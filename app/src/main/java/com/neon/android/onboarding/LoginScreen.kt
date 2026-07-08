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
    var fioError by remember { mutableStateOf<String?>(null) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var qrUnlockedRoles by remember { mutableStateOf(setOf(UserRole.STUDENT)) }

    val needsQrGrant = selectedRole != UserRole.STUDENT
    val hasQrGrant = selectedRole in qrUnlockedRoles

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
            text = "Укажите ФИО и роль. Админ и преподаватель входят через QR-код.",
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
                    Text("✓ QR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (needsQrGrant) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    qrError = null
                    showQrScanner = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasQrGrant) "QR подтверждён · сканировать снова"
                    else when (selectedRole) {
                        UserRole.ADMIN -> "Сканировать QR администратора"
                        UserRole.TEACHER -> "Сканировать QR от администратора"
                        else -> "Сканировать QR"
                    }
                )
            }
            if (!hasQrGrant) {
                Text(
                    when (selectedRole) {
                        UserRole.TEACHER -> "Преподавателем можно стать только через QR, который показывает администратор."
                        UserRole.ADMIN -> "QR администратора выдаёт оператор лагеря (распечатка или экран)."
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
                    qrError = "Сначала отсканируйте QR для роли «${selectedRole.displayNameRu}»"
                    return@Button
                }
                if (needsQrGrant && roleKeyManager.grantedRole() != selectedRole) {
                    qrError = "QR не подтверждён для этой роли"
                    return@Button
                }
                onLogin(fio.trim(), selectedRole)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = fio.isNotBlank() && (!needsQrGrant || hasQrGrant)
        ) {
            Text("Войти")
        }
    }
}
