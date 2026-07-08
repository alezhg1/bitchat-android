package com.neon.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
 

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Intent
import android.net.Uri
import com.neon.android.model.BitchatMessage
import com.neon.android.model.DeliveryStatus
import com.neon.android.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.*
import com.neon.android.ui.media.VoiceNotePlayer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.neon.android.ui.theme.ChatColors
import com.neon.android.ui.media.FileMessageItem
import com.neon.android.model.BitchatMessageType
import com.neon.android.R
import androidx.compose.ui.res.stringResource


// VoiceNotePlayer moved to com.neon.android.ui.media.VoiceNotePlayer

/**
 * Message display components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun MessagesList(
    messages: List<BitchatMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: ((Boolean) -> Unit)? = null,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    
    // Track if this is the first time messages are being loaded
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    var followIncomingMessages by remember { mutableStateOf(true) }
    
    // Smart scroll: auto-scroll to bottom for initial load, then follow unless user scrolls away
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val isFirstLoad = !hasScrolledToInitialPosition
            if (isFirstLoad || followIncomingMessages) {
                listState.scrollToItem(0)
                if (isFirstLoad) {
                    hasScrolledToInitialPosition = true
                }
            }
        }
    }
    
    // Track whether user has scrolled away from the latest messages
    val isAtLatest by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            firstVisibleIndex <= 2
        }
    }
    LaunchedEffect(isAtLatest) {
        followIncomingMessages = isAtLatest
        onScrolledUpChanged?.invoke(!isAtLatest)
    }
    
    // Force scroll to bottom when requested (e.g., when user sends a message)
    LaunchedEffect(forceScrollToBottom) {
        if (messages.isNotEmpty()) {
            // With reverseLayout=true and reversed data, latest is at index 0
            followIncomingMessages = true
            listState.scrollToItem(0)
        }
    }
    
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.background(ChatColors.chatBackground),
        reverseLayout = true
    ) {
        items(
            items = messages.asReversed(),
            key = { it.id }
        ) { message ->
                MessageItem(
                    message = message,
                    messages = messages,
                    currentUserNickname = currentUserNickname,
                    meshService = meshService,
                    onNicknameClick = onNicknameClick,
                    onMessageLongPress = onMessageLongPress,
                    onCancelTransfer = onCancelTransfer,
                    onImageClick = onImageClick
                )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: BitchatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    messages: List<BitchatMessage> = emptyList(),
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    MessageTextWithClickableNicknames(
        message = message,
        messages = messages,
        currentUserNickname = currentUserNickname,
        meshService = meshService,
        colorScheme = colorScheme,
        timeFormatter = timeFormatter,
        onNicknameClick = onNicknameClick,
        onMessageLongPress = onMessageLongPress,
        onCancelTransfer = onCancelTransfer,
        onImageClick = onImageClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
    private fun MessageTextWithClickableNicknames(
        message: BitchatMessage,
        messages: List<BitchatMessage>,
        currentUserNickname: String,
        meshService: BluetoothMeshService,
        colorScheme: ColorScheme,
        timeFormatter: SimpleDateFormat,
        onNicknameClick: ((String) -> Unit)?,
        onMessageLongPress: ((BitchatMessage) -> Unit)?,
        onCancelTransfer: ((BitchatMessage) -> Unit)?,
        onImageClick: ((String, List<String>, Int) -> Unit)?,
        modifier: Modifier = Modifier
    ) {
    // Image special rendering
    if (message.type == BitchatMessageType.Image) {
        com.neon.android.ui.media.ImageMessageItem(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            onImageClick = onImageClick,
            modifier = modifier
        )
        return
    }

    // Voice note special rendering
    if (message.type == BitchatMessageType.Audio) {
        com.neon.android.ui.media.AudioMessageItem(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            modifier = modifier
        )
        return
    }

    // File special rendering
    if (message.type == BitchatMessageType.File) {
        val path = message.content.trim()
        // Derive sending progress if applicable
        val (overrideProgress, _) = when (val st = message.deliveryStatus) {
            is com.neon.android.model.DeliveryStatus.PartiallyDelivered -> {
                if (st.total > 0 && st.reached < st.total) {
                    (st.reached.toFloat() / st.total.toFloat()) to colorScheme.primary // Фиолетовый NeoN для статуса отправки
                } else null to null
            }
            else -> null to null
        }
        Column(modifier = modifier.fillMaxWidth()) {
            // Header: nickname + timestamp line above the file, identical styling to text messages
            val headerText = formatMessageHeaderAnnotatedString(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter
            )
            val haptic = LocalHapticFeedback.current
            var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headerText,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface,
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(onTap = { pos ->
                        val layout = headerLayout ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(pos)
                        val ann = headerText.getStringAnnotations("nickname_click", offset, offset)
                        if (ann.isNotEmpty() && onNicknameClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNicknameClick.invoke(ann.first().item)
                        }
                    }, onLongPress = { onMessageLongPress?.invoke(message) })
                },
                onTextLayout = { headerLayout = it }
            )

            // Try to load the file packet from the path
            val packet = try {
                val file = java.io.File(path)
                if (file.exists()) {
                    // Create a temporary BitchatFilePacket for display
                    // In a real implementation, this would be stored with the packet metadata
                    com.neon.android.model.BitchatFilePacket(
                        fileName = file.name,
                        fileSize = file.length(),
                        mimeType = com.neon.android.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                        content = file.readBytes()
                    )
                } else null
            } catch (e: Exception) {
                null
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (packet != null) {
                        if (overrideProgress != null) {
                            // Show sending animation while in-flight
                            com.neon.android.ui.media.FileSendingAnimation(
                                fileName = packet.fileName,
                                progress = overrideProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Static file display with open/save dialog
                            FileMessageItem(
                                packet = packet,
                                onFileClick = {
                                    // handled inside FileMessageItem via dialog
                                }
                            )
                        }

                        // Cancel button overlay during sending
                        val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
                        if (showCancel) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onCancelTransfer?.invoke(message) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.file_unavailable), fontFamily = FontFamily.Monospace, color = Color.Gray)
                    }
                }
            }
        }
        return
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)
    
    // If animation is needed, use the matrix animation component for content only
    if (shouldAnimate) {
        // Display message with matrix animation for content
        MessageWithMatrixAnimation(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onImageClick = onImageClick,
            modifier = modifier
        )
    } else {
        val isSelf = isMessageFromSelf(message, currentUserNickname, meshService)
        val isSystem = message.sender == "system"
        val showSenderName = !isSelf && !isSystem && !message.isPrivate

        if (isSystem) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        val bubbleContent = formatBubbleContentAnnotatedString(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            isSelf = isSelf
        )

        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
        ) {
            if (showSenderName) {
                Text(
                    text = formatBubbleSenderLabel(message),
                    style = MaterialTheme.typography.labelMedium,
                    color = ChatColors.senderName,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 2.dp, end = 6.dp)
                        .then(
                            if (onNicknameClick != null) {
                                Modifier.pointerInput(message.id) {
                                    detectTapGestures(onTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNicknameClick.invoke(message.originalSender ?: message.sender)
                                    })
                                }
                            } else Modifier
                        )
                )
            }

            Row(
                horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isSelf) 18.dp else 4.dp,
                        bottomEnd = if (isSelf) 4.dp else 18.dp
                    ),
                    color = if (isSelf) ChatColors.selfBubble else ChatColors.peerBubble,
                    shadowElevation = if (isSelf) 0.dp else 1.dp,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = bubbleContent,
                            modifier = Modifier.pointerInput(message) {
                                detectTapGestures(
                                    onTap = { position ->
                                        val layout = textLayoutResult ?: return@detectTapGestures
                                        val offset = layout.getOffsetForPosition(position)
                                        if (!isSelf && onNicknameClick != null) {
                                            val nicknameAnnotations = bubbleContent.getStringAnnotations(
                                                tag = "nickname_click",
                                                start = offset,
                                                end = offset
                                            )
                                            if (nicknameAnnotations.isNotEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onNicknameClick.invoke(nicknameAnnotations.first().item)
                                                return@detectTapGestures
                                            }
                                        }
                                        val geohashAnnotations = bubbleContent.getStringAnnotations(
                                            tag = "geohash_click",
                                            start = offset,
                                            end = offset
                                        )
                                        if (geohashAnnotations.isNotEmpty()) {
                                            val geohash = geohashAnnotations.first().item
                                            try {
                                                val locationManager =
                                                    com.neon.android.geohash.LocationChannelManager.getInstance(context)
                                                val level = when (geohash.length) {
                                                    in 0..2 -> com.neon.android.geohash.GeohashChannelLevel.REGION
                                                    in 3..4 -> com.neon.android.geohash.GeohashChannelLevel.PROVINCE
                                                    5 -> com.neon.android.geohash.GeohashChannelLevel.CITY
                                                    6 -> com.neon.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                                                    else -> com.neon.android.geohash.GeohashChannelLevel.BLOCK
                                                }
                                                val channel = com.neon.android.geohash.GeohashChannel(
                                                    level,
                                                    geohash.lowercase()
                                                )
                                                locationManager.setTeleported(true)
                                                locationManager.select(
                                                    com.neon.android.geohash.ChannelID.Location(channel)
                                                )
                                            } catch (_: Exception) { }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            return@detectTapGestures
                                        }
                                        val urlAnnotations = bubbleContent.getStringAnnotations(
                                            tag = "url_click",
                                            start = offset,
                                            end = offset
                                        )
                                        if (urlAnnotations.isNotEmpty()) {
                                            val raw = urlAnnotations.first().item
                                            val resolved = if (
                                                raw.startsWith("http://", ignoreCase = true) ||
                                                raw.startsWith("https://", ignoreCase = true)
                                            ) raw else "https://$raw"
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (_: Exception) { }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMessageLongPress?.invoke(message)
                                    }
                                )
                            },
                            softWrap = true,
                            overflow = TextOverflow.Visible,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isSelf) ChatColors.selfBubbleContent else ChatColors.peerBubbleContent
                            ),
                            onTextLayout = { result -> textLayoutResult = result }
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.powDifficulty?.let { bits ->
                                if (bits > 0) {
                                    Text(
                                        text = "⛨${bits}b",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelf) {
                                            ChatColors.selfBubbleContent.copy(alpha = 0.75f)
                                        } else {
                                            ChatColors.timestamp
                                        }
                                    )
                                }
                            }
                            Text(
                                text = timeFormatter.format(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelf) {
                                    ChatColors.selfBubbleContent.copy(alpha = 0.75f)
                                } else {
                                    ChatColors.timestamp
                                }
                            )
                            if (message.isPrivate && isSelf) {
                                message.deliveryStatus?.let { status ->
                                    DeliveryStatusIcon(status = status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val colorScheme = MaterialTheme.colorScheme
    
    when (status) {
        is DeliveryStatus.Sending -> {
            Text(
                text = stringResource(R.string.status_sending),
                style = MaterialTheme.typography.labelSmall,
                color = ChatColors.selfBubbleContent.copy(alpha = 0.7f)
            )
        }
        is DeliveryStatus.Sent -> {
            Text(
                text = stringResource(R.string.status_pending),
                style = MaterialTheme.typography.labelSmall,
                color = ChatColors.selfBubbleContent.copy(alpha = 0.7f)
            )
        }
        is DeliveryStatus.Delivered -> {
            // Single check for Delivered (matches iOS expectations)
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.Read -> {
            Text(
                text = stringResource(R.string.status_delivered),
                fontSize = 10.sp,
                color = colorScheme.primary, // Фиолетовый NeoN
                fontWeight = FontWeight.Bold
            )
        }
        is DeliveryStatus.Failed -> {
            Text(
                text = stringResource(R.string.status_failed),
                fontSize = 10.sp,
                color = Color.Red.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.PartiallyDelivered -> {
            // Show a single subdued check without numeric label
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
