package com.neon.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.android.nostr.NostrProofOfWork
import com.neon.android.nostr.PoWPreferenceManager
import androidx.compose.ui.res.stringResource
import com.neon.android.R
import com.neon.android.core.ui.component.button.CloseButton
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.identity.RoleQrRepository
import com.neon.android.identity.UserProfileManager
import com.neon.android.identity.UserRole
import com.neon.android.net.TorPreferenceManager
import com.neon.android.net.ArtiTorManager

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    statusIndicator: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.primary.copy(alpha = if (enabled) 0.14f else 0.06f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f)
            )
            statusIndicator?.invoke()
        }

        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    nickname: String,
    staticId: String = "",
    onNicknameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowDebug: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showTeacherQr by remember { mutableStateOf(false) }
    val userRole = remember { UserProfileManager.getInstance(context).getRole() }
    val teacherQrPayload = remember { RoleQrRepository.teacherQrPayload(context) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val lazyListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.98f else 0f,
        label = "topBarAlpha"
    )

    val colorScheme = MaterialTheme.colorScheme

    if (isPresented) {
        BitchatBottomSheet(
            modifier = modifier,
            onDismissRequest = onDismiss,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "version") {
                        Text(
                            text = stringResource(R.string.version_prefix, versionName ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onBackground.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item(key = "nickname") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_nickname_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                OutlinedTextField(
                                    value = nickname,
                                    onValueChange = onNicknameChange,
                                    singleLine = true,
                                    placeholder = {
                                        Text(stringResource(R.string.settings_nickname_hint))
                                    },
                                    leadingIcon = {
                                        Text(
                                            text = stringResource(R.string.at_symbol),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.primary,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorScheme.primary,
                                        unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.4f)
                                    )
                                )
                                if (staticId.isNotBlank()) {
                                    Text(
                                        text = "Static ID: $staticId",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Static ID нельзя изменить",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "settings") {
                        LaunchedEffect(Unit) { PoWPreferenceManager.init(context) }
                        val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
                        val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()
                        var backgroundEnabled by remember {
                            mutableStateOf(com.neon.android.service.MeshServicePreferences.isBackgroundEnabled(true))
                        }
                        val torMode = remember { mutableStateOf(TorPreferenceManager.get(context)) }
                        val torProvider = remember { ArtiTorManager.getInstance() }
                        val torStatus by torProvider.statusFlow.collectAsState()
                        val torAvailable = remember { torProvider.isTorAvailable() }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column {
                                SettingsSwitchRow(
                                    icon = Icons.Filled.Bluetooth,
                                    title = stringResource(R.string.about_background_title),
                                    checked = backgroundEnabled,
                                    onCheckedChange = { enabled ->
                                        backgroundEnabled = enabled
                                        com.neon.android.service.MeshServicePreferences.setBackgroundEnabled(enabled)
                                        if (!enabled) {
                                            com.neon.android.service.MeshForegroundService.stop(context)
                                        } else {
                                            com.neon.android.service.MeshForegroundService.start(context)
                                        }
                                    }
                                )
                                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.15f))
                                SettingsSwitchRow(
                                    icon = Icons.Filled.Speed,
                                    title = stringResource(R.string.about_pow),
                                    checked = powEnabled,
                                    onCheckedChange = { PoWPreferenceManager.setPowEnabled(it) }
                                )
                                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.15f))
                                SettingsSwitchRow(
                                    icon = Icons.Filled.Security,
                                    title = stringResource(R.string.settings_tor_network),
                                    checked = torMode.value == TorMode.ON,
                                    onCheckedChange = { enabled ->
                                        if (torAvailable) {
                                            torMode.value = if (enabled) TorMode.ON else TorMode.OFF
                                            TorPreferenceManager.set(context, torMode.value)
                                        }
                                    },
                                    enabled = torAvailable,
                                    statusIndicator = if (torMode.value == TorMode.ON) {
                                        {
                                            val statusColor = when {
                                                torStatus.running && torStatus.bootstrapPercent >= 100 -> colorScheme.primary
                                                torStatus.running -> Color(0xFFFF9500)
                                                else -> Color(0xFFFF3B30)
                                            }
                                            Surface(
                                                color = statusColor,
                                                shape = CircleShape,
                                                modifier = Modifier.size(8.dp)
                                            ) {}
                                        }
                                    } else null
                                )
                            }
                        }

                        if (!torAvailable) {
                            Text(
                                text = stringResource(R.string.tor_not_available_in_this_build),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
                            )
                        }
                    }

                    item(key = "pow_slider") {
                        val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
                        val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()

                        if (powEnabled) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_pow_difficulty),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$powDifficulty bits • ${NostrProofOfWork.estimateMiningTime(powDifficulty)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    }
                                    Slider(
                                        value = powDifficulty.toFloat(),
                                        onValueChange = { PoWPreferenceManager.setPowDifficulty(it.toInt()) },
                                        valueRange = 0f..32f,
                                        steps = 31,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colorScheme.primary,
                                            activeTrackColor = colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item(key = "tor_status") {
                        val torMode = remember { mutableStateOf(TorPreferenceManager.get(context)) }
                        val torProvider = remember { ArtiTorManager.getInstance() }
                        val torStatus by torProvider.statusFlow.collectAsState()

                        if (torMode.value == TorMode.ON) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val statusColor = when {
                                        torStatus.running && torStatus.bootstrapPercent >= 100 -> colorScheme.primary
                                        torStatus.running -> Color(0xFFFF9500)
                                        else -> Color(0xFFFF3B30)
                                    }
                                    Surface(color = statusColor, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                                    Text(
                                        text = if (torStatus.running) {
                                            stringResource(R.string.settings_tor_connected, torStatus.bootstrapPercent)
                                        } else {
                                            stringResource(R.string.settings_tor_disconnected)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    item(key = "warning") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.about_emergency_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }

                    if (userRole == UserRole.ADMIN) {
                        item(key = "teacher_qr") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Роли · QR преподавателя",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Покажите QR будущему преподавателю при входе. Пароль вручную не нужен.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { showTeacherQr = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Показать QR преподавателя")
                                    }
                                }
                            }
                        }
                    }

                    if (onShowDebug != null) {
                        item(key = "debug") {
                            TextButton(
                                onClick = onShowDebug,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.about_debug_settings),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }

                    if (onLogout != null) {
                        item(key = "logout") {
                            TextButton(
                                onClick = { showLogoutConfirm = true },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "Выйти из аккаунта",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(colorScheme.background.copy(alpha = topBarAlpha))
                ) {
                    Text(
                        text = stringResource(R.string.settings_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                    )
                    CloseButton(
                        onClick = onDismiss,
                        modifier = modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    RoleQrDisplaySheet(
        isPresented = showTeacherQr,
        role = UserRole.TEACHER,
        qrPayload = teacherQrPayload,
        onDismiss = { showTeacherQr = false }
    )

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text("Будут удалены ФИО, роль и локальные переписки. Static ID устройства сохранится.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout?.invoke()
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Password prompt dialog for password-protected channels
 */
@Composable
fun PasswordPromptDialog(
    show: Boolean,
    channelName: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (show && channelName != null) {
        val colorScheme = MaterialTheme.colorScheme

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.pwd_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.pwd_prompt_message, channelName ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.pwd_label), style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(R.string.join),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
            },
            containerColor = colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}
