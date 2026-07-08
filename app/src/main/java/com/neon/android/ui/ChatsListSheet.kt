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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.CampChatManager
import com.neon.android.mesh.GroupChatManager
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import com.neon.android.ui.theme.ChatColors
import com.neon.android.ui.theme.ChatAvatar

data class ChatListItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val type: ChatListItemType,
  val accentColor: androidx.compose.ui.graphics.Color,
  val unreadCount: Int = 0
)

enum class ChatListItemType {
  CAMP,
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val campUnread = campGeoKey?.let { unreadChannels[it] ?: 0 } ?: 0

  val chatItems = buildList {
    add(
      ChatListItem(
        id = "camp",
        title = campName,
        subtitle = "Общий чат лагеря · mesh",
        type = ChatListItemType.CAMP,
        accentColor = ChatColors.meshAccent,
        unreadCount = campUnread
      )
    )
    joinedChannels
      .filter { GroupChatManager.isGroupChannel(it) }
      .forEach { key ->
        val info = groupMeta[key]
        add(
          ChatListItem(
            id = key,
            title = info?.name ?: GroupChatManager.bareId(key),
            subtitle = "Группа · присоединение по QR · mesh",
            type = ChatListItemType.GROUP,
            accentColor = ChatColors.channelAccent,
            unreadCount = unreadChannels[key] ?: 0
          )
        )
      }
    joinedChannels
      .filter { !GroupChatManager.isGroupChannel(it) && it != CampChatManager.CAMP_MESH_CHANNEL }
      .forEach { channel ->
        add(
          ChatListItem(
            id = channel,
            title = channel.removePrefix("#"),
            subtitle = "Канал",
            type = ChatListItemType.CHANNEL,
            accentColor = ChatColors.channelAccent,
            unreadCount = unreadChannels[channel] ?: 0
          )
        )
      }
    privateChats.keys.sorted().forEach { peerId ->
      val title = peerNicknames[peerId] ?: peerId.take(12)
      val preview = privateChats[peerId]?.lastOrNull()?.content?.take(40) ?: "Личная переписка"
      add(
        ChatListItem(
          id = "private:$peerId",
          title = title,
          subtitle = preview,
          type = ChatListItemType.PRIVATE,
          accentColor = ChatColors.privateAccent,
          unreadCount = if (peerId in unreadPrivate) 1 else 0
        )
      )
    }
  }

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
      FilledTonalButton(
        onClick = onCreateGroup,
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Создать")
      }
      OutlinedButton(
        onClick = { onJoinGroupQr() },
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("По QR")
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = PaddingValues(bottom = 16.dp)
    ) {
      items(chatItems, key = { it.id }) { item ->
        val isActive = when (item.type) {
          ChatListItemType.CAMP ->
            currentChannel == null && CampChatManager.isCampChannel(context, selectedLocationChannel)
          ChatListItemType.GROUP, ChatListItemType.CHANNEL -> currentChannel == item.id
          ChatListItemType.PRIVATE -> false
        }
        ChatListRow(
          item = item,
          isActive = isActive,
          onClick = {
            when (item.type) {
              ChatListItemType.CAMP -> {
                onSelectCamp()
                onDismiss()
              }
              ChatListItemType.GROUP, ChatListItemType.CHANNEL -> {
                onSelectChannel(item.id)
                onDismiss()
              }
              ChatListItemType.PRIVATE -> {
                onSelectPrivate(item.id.removePrefix("private:"))
                onDismiss()
              }
            }
          }
        )
      }
      item(key = "advanced_location") {
        TextButton(
          onClick = {
            onAdvancedLocation()
            onDismiss()
          },
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
      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    },
    tonalElevation = if (isActive) 2.dp else 0.dp
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ChatAvatar(
        name = item.title,
        accentColor = item.accentColor,
        size = 46.dp
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
            modifier = Modifier.weight(1f)
          )
          if (item.unreadCount > 0) {
            Badge(containerColor = ChatColors.unreadBadge) {
              Text(item.unreadCount.toString())
            }
          }
        }
        Text(
          item.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
      }
    }
  }
}
