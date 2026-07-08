package com.neon.android.mesh

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neon.android.ui.ChatState
import com.neon.android.ui.DataManager
import com.neon.android.ui.MessageManager
import java.io.File
import java.util.UUID

data class GroupChatInfo(
    val id: String,
    val name: String,
    val creatorPeerId: String,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val storageKey: String get() = GroupChatManager.storageKey(id)
}

/**
 * User-created mesh groups. Messages use wire prefix [GRP:{id}]:text and relay multi-hop via BLE.
 */
object GroupChatManager {

    private const val TAG = "GroupChatManager"
    const val WIRE_PREFIX = "[GRP:"
    private const val META_FILE = "group_chats.json"

    fun storageKey(groupId: String): String {
        val bare = groupId.removePrefix("grp:")
        return "grp:$bare"
    }

    fun bareId(storageKey: String): String = storageKey.removePrefix("grp:")

    fun wrapForMesh(groupId: String, content: String): String {
        val id = bareId(groupId)
        return "$WIRE_PREFIX$id]:$content"
    }

    fun parseMeshPayload(raw: String): Pair<String, String>? {
        if (!raw.startsWith(WIRE_PREFIX)) return null
        val end = raw.indexOf("]:")
        if (end <= WIRE_PREFIX.length) return null
        val id = raw.substring(WIRE_PREFIX.length, end)
        val text = raw.substring(end + 2)
        if (id.isBlank()) return null
        return storageKey(id) to text
    }

    fun isGroupChannel(channel: String?): Boolean =
        channel?.startsWith("grp:") == true

    fun loadGroups(context: Context): Map<String, GroupChatInfo> {
        val file = File(context.filesDir, META_FILE)
        if (!file.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<List<GroupChatInfo>>() {}.type
            val list: List<GroupChatInfo> = Gson().fromJson(file.readText(), type) ?: emptyList()
            list.associateBy { it.storageKey }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load groups", e)
            emptyMap()
        }
    }

    private fun saveGroups(context: Context, groups: Map<String, GroupChatInfo>) {
        try {
            File(context.filesDir, META_FILE).writeText(Gson().toJson(groups.values.toList()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save groups", e)
        }
    }

    fun getGroupInfo(context: Context, storageKey: String): GroupChatInfo? =
        loadGroups(context)[storageKey]

    fun createGroup(
        context: Context,
        name: String,
        creatorPeerId: String,
        state: ChatState,
        dataManager: DataManager
    ): GroupChatInfo {
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val info = GroupChatInfo(id = id, name = name.trim(), creatorPeerId = creatorPeerId)
        val key = info.storageKey

        val groups = loadGroups(context).toMutableMap()
        groups[key] = info
        saveGroups(context, groups)

        val joined = state.getJoinedChannelsValue().toMutableSet()
        joined.add(key)
        state.setJoinedChannels(joined)

        dataManager.addChannelCreator(key, creatorPeerId)
        dataManager.addChannelMember(key, creatorPeerId)

        if (!state.getChannelMessagesValue().containsKey(key)) {
            val msgs = state.getChannelMessagesValue().toMutableMap()
            msgs[key] = emptyList()
            state.setChannelMessages(msgs)
        }

        dataManager.saveChannelData(joined, state.getPasswordProtectedChannelsValue())
        Log.i(TAG, "Created group $key ($name)")
        return info
    }

    fun joinGroup(
        context: Context,
        groupId: String,
        name: String,
        myPeerId: String,
        state: ChatState,
        dataManager: DataManager
    ): GroupChatInfo {
        val key = storageKey(groupId)
        val groups = loadGroups(context).toMutableMap()
        val info = groups[key] ?: GroupChatInfo(
            id = bareId(key),
            name = name,
            creatorPeerId = "unknown"
        ).also { groups[key] = it }
        saveGroups(context, groups)

        val joined = state.getJoinedChannelsValue().toMutableSet()
        joined.add(key)
        state.setJoinedChannels(joined)
        dataManager.addChannelMember(key, myPeerId)

        if (!state.getChannelMessagesValue().containsKey(key)) {
            val msgs = state.getChannelMessagesValue().toMutableMap()
            msgs[key] = emptyList()
            state.setChannelMessages(msgs)
        }

        dataManager.saveChannelData(joined, state.getPasswordProtectedChannelsValue())
        Log.i(TAG, "Joined group $key")
        return info
    }

    fun joinFromQr(
        context: Context,
        raw: String,
        myPeerId: String,
        state: ChatState,
        dataManager: DataManager
    ): GroupChatInfo? {
        val (id, name) = GroupQrCodec.decode(raw) ?: return null
        return joinGroup(context, id, name, myPeerId, state, dataManager)
    }

    fun displayName(context: Context, storageKey: String): String =
        getGroupInfo(context, storageKey)?.name ?: bareId(storageKey).take(8)
}
