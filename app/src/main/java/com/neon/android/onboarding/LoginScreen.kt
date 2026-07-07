package com.neon.android.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLogin: (String) -> Unit
) {
    var nickname by remember { mutableStateFlowOf("") }
    val isError = nickname.isBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Добро пожаловать",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Введите ваше ФИО или никнейм для входа",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("ФИО / Логин") },
            placeholder = { Text("Иван Иванов") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError && nickname.isNotEmpty(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { if (nickname.isNotBlank()) onLogin(nickname) },
            modifier = Modifier.fillMaxWidth(),
            enabled = nickname.isNotBlank()
        ) {
            Text("Войти")
        }
    }
}

private fun <T> mutableStateFlowOf(value: T) = mutableStateOf(value)
