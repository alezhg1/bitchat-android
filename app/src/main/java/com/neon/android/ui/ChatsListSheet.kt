package com.neon.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.android.geohash.ChannelID
import com.neon.android.core.ui.component.sheet.BitchatBottomSheet
import com.neon.android.core.ui.component.sheet.BitchatSheetTopBar
import com.neon.android.core.ui.component.sheet.BitchatSheetTitle
import com.neon.android.ui.theme.ChatColors

data class ChatListItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val type: ChatListItemType,
  val accentColor: androidx.compose.ui.graphics.Color,
  val unreadCount: Int = 0
)

enum class ChatListItemType {
  MESH,
  LOCATION,
  CHANNEL,
  PRIVATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListSheet(
  isPresented: Boolean,
  onDismiss: () -> Unit,
  viewModel: ChatViewModel,
  onSelectMesh: () -> Unit,
  onSelectLocation: () -> Unit,
  onSelectChannel: (String) -> Unit,
  onSelectPrivate: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  if (!isPresented) return

  val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
  val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
  val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
  val unreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
  val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
  val unreadPrivate by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
  val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val chatItems = buildList {
    add(
      ChatListItem(
        id = "mesh",
        title = "Общий чат",
        subtitle = "Сообщения через mesh-сеть",
        type = ChatListItemType.MESH,
        accentColor = ChatColors.meshAccent
      )
    )
    when (val loc = selectedLocationChannel) {
      is ChannelID.Location -> {
        add(
          ChatListItem(
            id = "geo:${loc.channel.geohash}",
            title = "Локационный чат",
            subtitle = loc.channel.geohash,
            type = ChatListItemType.LOCATION,
            accentColor = ChatColors.locationAccent,
            unreadCount = unreadChannels["geo:${loc.channel.geohash}"] ?: 0
          )
        )
      }
      else -> {
        add(
          ChatListItem(
            id = "location_picker",
            title = "Локационные чаты",
            subtitle = "Выбрать чат по геолокации",
            type = ChatListItemType.LOCATION,
            accentColor = ChatColors.locationAccent
          )
        )
      }
    }
    joinedChannels.forEach { channel ->
      add(
        ChatListItem(
          id = channel,
          title = channel,
          subtitle = "Групповой чат",
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

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(chatItems, key = { it.id }) { item ->
        val isActive = when (item.type) {
          ChatListItemType.MESH -> currentChannel == null && selectedLocationChannel is ChannelID.Mesh
          ChatListItemType.LOCATION -> item.id.startsWith("geo:") &&
            selectedLocationChannel is ChannelID.Location &&
            currentChannel == null
          ChatListItemType.CHANNEL -> currentChannel == item.id
          ChatListItemType.PRIVATE -> false
          else -> false
        }
        ChatListRow(
          item = item,
          isActive = isActive,
          onClick = {
            when (item.type) {
              ChatListItemType.MESH -> {
                onSelectMesh()
                onDismiss()
              }
              ChatListItemType.LOCATION -> {
                if (item.id == "location_picker") onSelectLocation() else onDismiss()
              }
              ChatListItemType.CHANNEL -> {
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
    }
  }
}

@Composable
private fun ChatListRow(
  item: ChatListItem,
  isActive: Boolean,
  onClick: () -> Unit
) {
  val icon = when (item.type) {
    ChatListItemType.MESH -> Icons.Default.Chat
    ChatListItemType.LOCATION -> Icons.Default.LocationOn
    ChatListItemType.CHANNEL -> Icons.Default.Tag
    ChatListItemType.PRIVATE -> Icons.Default.Person
  }
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
      Icon(icon, contentDescription = null, tint = item.accentColor, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(item.title, fontWeight = FontWeight.SemiBold, color = item.accentColor)
        Text(
          item.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
      if (item.unreadCount > 0) {
        Badge(containerColor = ChatColors.unreadBadge) {
          Text(item.unreadCount.toString())
        }
      }
    }
  }
}
