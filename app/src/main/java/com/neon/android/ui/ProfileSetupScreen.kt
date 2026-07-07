package com.bitchat.android.ui

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
import androidx.compose.ui.unit.dp
import com.bitchat.android.identity.UserProfileManager
import com.bitchat.android.identity.UserRole

@Composable
fun ProfileSetupScreen(
  staticId: String,
  onComplete: (fio: String, role: UserRole) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val profileManager = remember { UserProfileManager.getInstance(context) }

  var fio by remember { mutableStateOf(profileManager.getFio().takeIf { it != "Пользователь" } ?: "") }
  var selectedRole by remember { mutableStateOf(profileManager.getRole()) }
  var fioError by remember { mutableStateOf<String?>(null) }

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
          onClick = { selectedRole = role }
        )
        Spacer(Modifier.width(8.dp))
        Text(role.displayNameRu)
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
}
