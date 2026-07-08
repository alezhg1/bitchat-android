package com.neon.android.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
  var qrError by remember { mutableStateOf<String?>(null) }
  var showQrScanner by remember { mutableStateOf(false) }
  var qrUnlockedRoles by remember { mutableStateOf(setOf(UserRole.STUDENT)) }

  if (showQrScanner && selectedRole != UserRole.STUDENT) {
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
      "Настройка профиля",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "Укажите ФИО и роль. Админ и преподаватель — только через QR.",
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
          onClick = { selectedRole = role; qrError = null }
        )
        Spacer(Modifier.width(8.dp))
        Text(role.displayNameRu)
        if (role != UserRole.STUDENT && role in qrUnlockedRoles) {
          Spacer(Modifier.width(8.dp))
          Text("✓ QR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
      }
    }

    if (selectedRole != UserRole.STUDENT) {
      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = { showQrScanner = true },
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Сканировать QR · ${selectedRole.displayNameRu}")
      }
      qrError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    Spacer(Modifier.height(32.dp))

    Button(
      onClick = {
        if (fio.trim().length < 2) {
          fioError = "Введите ФИО (минимум 2 символа)"
          return@Button
        }
        if (selectedRole != UserRole.STUDENT && selectedRole !in qrUnlockedRoles) {
          qrError = "Сначала отсканируйте QR"
          return@Button
        }
        onComplete(fio.trim(), selectedRole)
      },
      modifier = Modifier.fillMaxWidth(),
      enabled = selectedRole == UserRole.STUDENT || selectedRole in qrUnlockedRoles
    ) {
      Text("Продолжить")
    }
  }
}
