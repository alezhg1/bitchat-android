package com.neon.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neon.android.identity.RoleKeyManager
import com.neon.android.identity.UserProfileManager
import com.neon.android.identity.UserRole

@Composable
fun ProfileSetupScreen(
  staticId: String,
  onComplete: (fio: String, role: UserRole) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val profileManager = remember { UserProfileManager.getInstance(context) }
  val roleKeyManager = remember { RoleKeyManager.getInstance(context) }

  var fio by remember { mutableStateOf(profileManager.getFio().takeIf { it != "Пользователь" } ?: "") }
  var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
  var fioError by remember { mutableStateOf<String?>(null) }

  // Secret-key dialog state for elevated roles
  var pendingRole by remember { mutableStateOf<UserRole?>(null) }
  var secretKey by remember { mutableStateOf("") }
  var keyError by remember { mutableStateOf<String?>(null) }
  // Roles already unlocked in this setup session (persisted encrypted on success)
  var unlockedRoles by remember { mutableStateOf(setOf(UserRole.STUDENT)) }

  fun selectRole(role: UserRole) {
    if (role == UserRole.STUDENT || role in unlockedRoles) {
      selectedRole = role
    } else {
      // Require secret key before granting the elevated role
      pendingRole = role
      secretKey = ""
      keyError = null
    }
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
      "Настройка профиля",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "Укажите ФИО и роль. Static ID назначается автоматически и не может быть изменён.",
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
      singleLine = true
    )

    Spacer(Modifier.height(16.dp))

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Static ID", style = MaterialTheme.typography.labelMedium)
        Text(
          staticId,
          style = MaterialTheme.typography.bodyLarge,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Medium
        )
        Text(
          "Неизменяемый идентификатор",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(Modifier.height(20.dp))

    Text("Роль", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    UserRole.entries.forEach { role ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        RadioButton(
          selected = selectedRole == role,
          onClick = { selectRole(role) }
        )
        Spacer(Modifier.width(8.dp))
        Text(role.displayNameRu)
        if (role != UserRole.STUDENT && role in unlockedRoles) {
          Spacer(Modifier.width(8.dp))
          Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(Modifier.height(32.dp))

    Button(
      onClick = {
        if (fio.trim().length < 2) {
          fioError = "Введите ФИО (минимум 2 символа)"
          return@Button
        }
        onComplete(fio.trim(), selectedRole)
      },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Продолжить")
    }
  }

  val requestedRole = pendingRole
  if (requestedRole != null) {
    AlertDialog(
      onDismissRequest = { pendingRole = null },
      title = { Text("Секретный ключ: ${requestedRole.displayNameRu}") },
      text = {
        Column {
          Text(
            "Введите секретный ключ роли «${requestedRole.displayNameRu}».",
            style = MaterialTheme.typography.bodyMedium
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = secretKey,
            onValueChange = { secretKey = it; keyError = null },
            label = { Text("Секретный ключ") },
            singleLine = true,
            isError = keyError != null,
            supportingText = keyError?.let { { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          if (roleKeyManager.verifyAndGrant(requestedRole, secretKey)) {
            unlockedRoles = unlockedRoles + requestedRole
            selectedRole = requestedRole
            pendingRole = null
          } else {
            keyError = "Неверный ключ"
          }
        }) {
          Text("Подтвердить")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingRole = null }) { Text("Отмена") }
      }
    )
  }
}
