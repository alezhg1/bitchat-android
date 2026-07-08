package com.neon.android.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    var secretKey by remember { mutableStateOf("") }
    var fioError by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }

    val needsSecretKey = selectedRole != UserRole.STUDENT

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
            text = "Укажите ФИО и роль для входа в мессенджер",
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
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
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
                    Text(
                        staticIdPreview,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        keyError = null
                        if (role == UserRole.STUDENT) secretKey = ""
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(role.displayNameRu)
            }
        }

        if (needsSecretKey) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secretKey,
                onValueChange = { secretKey = it; keyError = null },
                label = { Text("Секретный ключ") },
                placeholder = { Text("Ключ для роли «${selectedRole.displayNameRu}»") },
                singleLine = true,
                isError = keyError != null,
                supportingText = keyError?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (fio.trim().length < 2) {
                    fioError = "Введите ФИО (минимум 2 символа)"
                    return@Button
                }
                if (needsSecretKey) {
                    if (secretKey.isBlank()) {
                        keyError = "Введите секретный ключ"
                        return@Button
                    }
                    if (!roleKeyManager.matchesRoleKey(selectedRole, secretKey)) {
                        keyError = "Неверный ключ"
                        return@Button
                    }
                    roleKeyManager.verifyAndGrant(selectedRole, secretKey)
                }
                onLogin(fio.trim(), selectedRole)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = fio.isNotBlank()
        ) {
            Text("Войти")
        }
    }
}
