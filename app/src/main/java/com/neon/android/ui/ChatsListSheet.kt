package com.neon.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.CampChatManager
import com.neon.android.mesh.GroupChatManager
import com.neon.android.model.BitchatMessage
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import com.neon.android.ui.theme.ChatColors
import com.neon.android.ui.theme.ChatAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatListItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val type: ChatListItemType,
  val accentColor: androidx.compose.ui.graphics.Color,
  val unreadCount: Int = 0,
  val lastMessageTime: Long? = null
)

enum class ChatListItemType {
  CAMP,
  TEACHERS,
  GROUP,
  CHANNEL,
  PRIVATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListSheet(
  isPresented: Boolean,
  onDismiss: () -> Unit,
  viewModel: ChatViewModel,
  onSelectCamp: () -> Unit,
  onSelectChannel: (String) -> Unit,
  onSelectPrivate: (String) -> Unit = {},
  onAdvancedLocation: () -> Unit = {},
  onCreateGroup: () -> Unit = {},
  onJoinGroupQr: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  if (!isPresented) return

  val context = LocalContext.current
  val campName = remember { CampChatManager.getDisplayName(context) }
  val campGeoKey = CampChatManager.geoStorageKey(context)

  val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
  val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
  val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
  val groupMeta = remember(joinedChannels) { GroupChatManager.loadGroups(context) }
  val unreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
  val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
  val unreadPrivate by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
  val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  fun lastForChannel(key: String?): Pair<String, Long>? {
    if (key == null) return null
    val list = channelMessages[key] ?: return null
    val last = list.lastOrNull() ?: return null
    return previewText(last) to last.timestamp.time
  }

  fun lastForCamp(): Pair<String, Long>? {
    val geo = campGeoKey?.let { channelMessages[it] } ?: emptyList()
    val merged = CampChatManager.mergeCampTimeline(messages, geo)
    val last = merged.lastOrNull() ?: return null
    return previewText(last) to last.timestamp.time
  }

  val campUnread = campGeoKey?.let { unreadChannels[it] ?: 0 } ?: 0
  val campLast = lastForCamp()

  val mainChats = buildList {
    add(
      ChatListItem(
        id = "camp",
        title = campName,
        subtitle = campLast?.first ?: "Общий чат лагеря · mesh",
        type = ChatListItemType.CAMP,
        accentColor = ChatColors.meshAccent,
        unreadCount = campUnread,
        lastMessageTime = campLast?.second
      )
    )
    val teachersKey = CampChatManager.TEACHERS_CHANNEL
    val teachersLast = lastForChannel(teachersKey)
    add(
      ChatListItem(
        id = teachersKey,
        title = "Преподы",
        subtitle = teachersLast?.first ?: "Только преподаватели пишут · все читают",
        type = ChatListItemType.TEACHERS,
        accentColor = ChatColors.channelAccent,
        unreadCount = unreadChannels[teachersKey] ?: 0,
        lastMessageTime = teachersLast?.second
      )
    )
  }

  val groupChats = joinedChannels
    .filter { GroupChatManager.isGroupChannel(it) }
    .map { key ->
      val info = groupMeta[key]
      val last = lastForChannel(key)
      ChatListItem(
        id = key,
        title = info?.name ?: GroupChatManager.bareId(key),
        subtitle = last?.first ?: "Группа · присоединение по QR",
        type = ChatListItemType.GROUP,
        accentColor = ChatColors.channelAccent,
        unreadCount = unreadChannels[key] ?: 0,
        lastMessageTime = last?.second
      )
    }
    .sortedByDescending { it.lastMessageTime ?: 0L }

  val channelChats = joinedChannels
    .filter {
      !GroupChatManager.isGroupChannel(it) &&
        it != CampChatManager.CAMP_MESH_CHANNEL &&
        it != CampChatManager.TEACHERS_CHANNEL
    }
    .map { channel ->
      val last = lastForChannel(channel)
      ChatListItem(
        id = channel,
        title = channel.removePrefix("#"),
        subtitle = last?.first ?: "Канал",
        type = ChatListItemType.CHANNEL,
        accentColor = ChatColors.channelAccent,
        unreadCount = unreadChannels[channel] ?: 0,
        lastMessageTime = last?.second
      )
    }
    .sortedByDescending { it.lastMessageTime ?: 0L }

  val privateChatItems = privateChats.keys.sorted().map { peerId ->
    val title = peerNicknames[peerId] ?: peerId.take(12)
    val last = privateChats[peerId]?.lastOrNull()
    ChatListItem(
      id = "private:$peerId",
      title = title,
      subtitle = last?.let { previewText(it) } ?: "Личная переписка",
      type = ChatListItemType.PRIVATE,
      accentColor = ChatColors.privateAccent,
      unreadCount = if (peerId in unreadPrivate) 1 else 0,
      lastMessageTime = last?.timestamp?.time
    )
  }.sortedByDescending { it.lastMessageTime ?: 0L }

  BitchatBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    BitchatSheetTopBar(
      onClose = onDismiss,
      title = { BitchatSheetTitle("Чаты") }
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilledTonalButton(onClick = onCreateGroup, modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Создать")
      }
      OutlinedButton(onClick = { onJoinGroupQr() }, modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("По QR")
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      contentPadding = PaddingValues(bottom = 16.dp)
    ) {
      item(key = "section_main") {
        SectionHeader("Основные")
      }
      items(mainChats, key = { it.id }) { item ->
        ChatListRow(
          item = item,
          isActive = isChatActive(context, item, currentChannel, selectedLocationChannel),
          onClick = {
            when (item.type) {
              ChatListItemType.CAMP -> onSelectCamp()
              ChatListItemType.TEACHERS -> onSelectChannel(item.id)
              else -> Unit
            }
            onDismiss()
          }
        )
      }

      if (groupChats.isNotEmpty()) {
        item(key = "section_groups") { SectionHeader("Группы") }
        items(groupChats, key = { it.id }) { item ->
          ChatListRow(
            item = item,
            isActive = currentChannel == item.id,
            onClick = {
              onSelectChannel(item.id)
              onDismiss()
            }
          )
        }
      }

      if (channelChats.isNotEmpty()) {
        item(key = "section_channels") { SectionHeader("Каналы") }
        items(channelChats, key = { it.id }) { item ->
          ChatListRow(
            item = item,
            isActive = currentChannel == item.id,
            onClick = {
              onSelectChannel(item.id)
              onDismiss()
            }
          )
        }
      }

      if (privateChatItems.isNotEmpty()) {
        item(key = "section_private") { SectionHeader("Личные") }
        items(privateChatItems, key = { it.id }) { item ->
          ChatListRow(
            item = item,
            isActive = false,
            onClick = {
              onSelectPrivate(item.id.removePrefix("private:"))
              onDismiss()
            }
          )
        }
      }

      item(key = "advanced_location") {
        TextButton(
          onClick = { onAdvancedLocation(); onDismiss() },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            "Расширенные локационные каналы…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

private fun isChatActive(
  context: android.content.Context,
  item: ChatListItem,
  currentChannel: String?,
  selectedLocationChannel: com.neon.android.geohash.ChannelID?
): Boolean = when (item.type) {
  ChatListItemType.CAMP ->
    currentChannel == null && CampChatManager.isCampChannel(context, selectedLocationChannel)
  ChatListItemType.TEACHERS, ChatListItemType.GROUP, ChatListItemType.CHANNEL ->
    currentChannel == item.id
  ChatListItemType.PRIVATE -> false
}

private fun previewText(message: BitchatMessage): String {
  val text = message.content.trim()
  val prefix = "${message.sender}: "
  return prefix + if (text.length > 48) text.take(48) + "…" else text
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
  )
}

@Composable
private fun ChatListRow(
  item: ChatListItem,
  isActive: Boolean,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = MaterialTheme.shapes.large,
    color = if (isActive) {
      item.accentColor.copy(alpha = 0.12f)
    } else {
      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    },
    tonalElevation = if (isActive) 2.dp else 0.dp
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ChatAvatar(
        name = item.title,
        accentColor = item.accentColor,
        size = 52.dp
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            item.title,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          item.lastMessageTime?.let { formatChatTime(it) }?.let { time ->
            Text(
              time,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Spacer(Modifier.height(2.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          if (item.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Badge(containerColor = ChatColors.unreadBadge) {
              Text(item.unreadCount.toString())
            }
          }
        }
      }
    }
  }
}

private fun formatChatTime(epochMs: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - epochMs
  return when {
    diff < 60_000 -> "сейчас"
    diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(epochMs))
  }
}
