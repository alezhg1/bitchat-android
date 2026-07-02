package com.bitchat.android.services

import android.content.Context
import android.util.Log
import com.bitchat.android.model.BitchatMessage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * Persists all chat messages locally so conversations survive app restarts.
 */
class MessagePersistenceService private constructor(private val context: Context) {

  companion object {
    private const val TAG = "MessagePersistence"
    private const val DIR_NAME = "chat_messages"

    @Volatile
    private var INSTANCE: MessagePersistenceService? = null

    fun getInstance(context: Context): MessagePersistenceService {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: MessagePersistenceService(context.applicationContext).also { INSTANCE = it }
      }
    }
  }

  private val gson: Gson = GsonBuilder()
    .registerTypeAdapter(Date::class.java, com.google.gson.JsonSerializer<Date> { src, _, _ ->
      com.google.gson.JsonPrimitive(src.time)
    })
    .registerTypeAdapter(Date::class.java, com.google.gson.JsonDeserializer { json, _, _ ->
      Date(json.asLong)
    })
    .create()

  private val storageDir: File = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

  suspend fun savePublicMessage(message: BitchatMessage) = saveMessage("public", message)

  suspend fun savePrivateMessage(peerId: String, message: BitchatMessage) =
    saveMessage("private_$peerId", message)

  suspend fun saveChannelMessage(channel: String, message: BitchatMessage) {
    val safeKey = channel.replace("[^a-zA-Z0-9_:-]".toRegex(), "_")
    saveMessage("channel_$safeKey", message)
  }

  private suspend fun saveMessage(fileKey: String, message: BitchatMessage) = withContext(Dispatchers.IO) {
    try {
      val file = File(storageDir, "$fileKey.json")
      val existing = loadMessagesFromFile(file).toMutableList()
      if (existing.any { it.id == message.id }) return@withContext
      existing.add(message)
      existing.sortBy { it.timestamp.time }
      file.writeText(gson.toJson(existing))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save message to $fileKey", e)
    }
  }

  suspend fun loadAllIntoAppState() = withContext(Dispatchers.IO) {
    try {
      storageDir.listFiles()?.forEach { file ->
        if (!file.name.endsWith(".json")) return@forEach
        val messages = loadMessagesFromFile(file)
        val key = file.name.removeSuffix(".json")
        when {
          key == "public" -> messages.forEach { AppStateStore.addPublicMessage(it) }
          key.startsWith("private_") -> {
            val peerId = key.removePrefix("private_")
            messages.forEach { AppStateStore.addPrivateMessage(peerId, it) }
          }
          key.startsWith("channel_") -> {
            val channel = key.removePrefix("channel_")
            messages.forEach { AppStateStore.addChannelMessage(channel, it) }
          }
        }
      }
      Log.d(TAG, "Loaded persisted messages into AppStateStore")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load persisted messages", e)
    }
  }

  private fun loadMessagesFromFile(file: File): List<BitchatMessage> {
    if (!file.exists()) return emptyList()
    return try {
      val type = object : TypeToken<List<BitchatMessage>>() {}.type
      gson.fromJson(file.readText(), type) ?: emptyList()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse ${file.name}", e)
      emptyList()
    }
  }
}
