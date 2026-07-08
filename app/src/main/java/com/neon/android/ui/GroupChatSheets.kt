package com.neon.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neon.android.mesh.GroupChatInfo
import com.neon.android.mesh.GroupQrCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.neon.android.core.ui.component.sheet.BitchatBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        com.neon.android.core.ui.component.sheet.BitchatSheetTopBar(
            onClose = onDismiss,
            title = { com.neon.android.core.ui.component.sheet.BitchatSheetTitle("Новый чат") }
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Создайте группу и покажите QR участникам. Сообщения идут по Bluetooth mesh через других людей.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Название чата") },
                placeholder = { Text("Отряд 3, Кухня, Волейбол…") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        error = "Минимум 2 символа"
                        return@Button
                    }
                    onCreate(name.trim())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать чат")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupQrDisplaySheet(
    isPresented: Boolean,
    group: GroupChatInfo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented || group == null) return
    val payload = remember(group) { GroupQrCodec.encode(group.id, group.name) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.neon.android.core.ui.component.sheet.BitchatBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        com.neon.android.core.ui.component.sheet.BitchatSheetTopBar(
            onClose = onDismiss,
            title = { com.neon.android.core.ui.component.sheet.BitchatSheetTitle("QR · ${group.name}") }
        )
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Участники: Чаты → Присоединиться по QR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                RoleQrCodeImage(data = payload, size = 240.dp, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupQrJoinScannerSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onScanRaw: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return
    var scanError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.neon.android.core.ui.component.sheet.BitchatBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        com.neon.android.core.ui.component.sheet.BitchatSheetTopBar(
            onClose = onDismiss,
            title = { com.neon.android.core.ui.component.sheet.BitchatSheetTitle("Сканировать QR чата") }
        )
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                QrCameraScannerBox(
                    modifier = Modifier.fillMaxSize(),
                    onScan = { raw ->
                        if (onScanRaw(raw)) {
                            scanError = null
                            onDismiss()
                        } else {
                            scanError = "Неверный QR чата"
                        }
                    }
                )
            }
            scanError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
