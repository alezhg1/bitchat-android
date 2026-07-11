package com.neon.android.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neon.android.identity.RoleKeyManager
import com.neon.android.identity.UserRole
import com.neon.android.ui.RoleQrScannerSheet

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    staticIdPreview: String = "",
    onLogin: (fio: String, role: UserRole) -> Unit
) {
    val context = LocalContext.current
    val roleKeyManager = remember { RoleKeyManager.getInstance(context) }

    var fio by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var roleCode by remember { mutableStateOf("") }
    var fioError by remember { mutableStateOf<String?>(null) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var qrUnlockedRoles by remember { mutableStateOf(setOf(UserRole.STUDENT)) }

    val needsQrGrant = selectedRole != UserRole.STUDENT
    val hasQrGrant = selectedRole in qrUnlockedRoles

    fun tryGrantFromCode(): Boolean {
        if (roleCode.isBlank()) return false
        return if (roleKeyManager.verifyAndGrant(selectedRole, roleCode)) {
            qrUnlockedRoles = qrUnlockedRoles + selectedRole
            qrError = null
            true
        } else {
            qrError = "Неверный код для роли «${selectedRole.displayNameRu}»"
            false
        }
    }

    if (showQrScanner && needsQrGrant) {
        RoleQrScannerSheet(
            isPresented = true,
            expectedRole = selectedRole,
            onDismiss = { showQrScanner = false },
            onScanned = { role ->
                qrUnlockedRoles = qrUnlockedRoles + role
                qrError = null
                showQrScanner = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Добро пожаловать",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Укажите ФИО и роль. Админ и преподаватель — по QR или коду доступа.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = fio,
            onValueChange = { fio = it; fioError = null },
            label = { Text("ФИО") },
            placeholder = { Text("Иванов Иван Иванович") },
            isError = fioError != null,
            supportingText = fioError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            )
        )

        if (staticIdPreview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Static ID", style = MaterialTheme.typography.labelMedium)
                    Text(staticIdPreview, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Неизменяемый идентификатор устройства",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Роль", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
        UserRole.entries.forEach { role ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedRole == role,
                    onClick = {
                        selectedRole = role
                        qrError = null
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(role.displayNameRu)
                if (role != UserRole.STUDENT && role in qrUnlockedRoles) {
                    Spacer(Modifier.width(8.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (needsQrGrant) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = roleCode,
                onValueChange = { roleCode = it; qrError = null },
                label = { Text("Код доступа") },
                placeholder = { Text("NLOON-ADMIN-OFFLINE-…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { tryGrantFromCode() },
                    modifier = Modifier.weight(1f),
                    enabled = roleCode.isNotBlank()
                ) {
                    Text("Проверить код")
                }
                OutlinedButton(
                    onClick = { qrError = null; showQrScanner = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("QR")
                }
            }
            if (!hasQrGrant) {
                Text(
                    when (selectedRole) {
                        UserRole.TEACHER -> "Преподаватель: QR от админа или код доступа."
                        UserRole.ADMIN -> "Администратор: QR или код, выданный оператором лагеря."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            qrError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (fio.trim().length < 2) {
                    fioError = "Введите ФИО (минимум 2 символа)"
                    return@Button
                }
                if (needsQrGrant && !hasQrGrant) {
                    if (!tryGrantFromCode()) {
                        qrError = "Подтвердите роль QR-кодом или кодом доступа"
                    }
                    if (selectedRole !in qrUnlockedRoles) return@Button
                }
                if (needsQrGrant && roleKeyManager.grantedRole() != selectedRole) {
                    qrError = "Роль не подтверждена"
                    return@Button
                }
                onLogin(fio.trim(), selectedRole)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = fio.isNotBlank() && (!needsQrGrant || hasQrGrant || roleCode.isNotBlank())
        ) {
            Text("Войти")
        }
    }
}
