package com.neon.android.mesh

import com.neon.android.geohash.CampChatManager
import com.neon.android.model.BitchatMessage
import com.neon.android.protocol.BitchatPacket
import com.neon.android.sync.PacketIdUtil

/**
 * Stable deduplication keys for mesh messages so relay paths do not duplicate chat lines.
 */
object MessageDedup {

    fun packetId(packet: BitchatPacket): String = PacketIdUtil.computeIdHex(packet)

    fun packetSenderPeerId(packet: BitchatPacket): String =
        packet.senderID.joinToString("") { b -> "%02x".format(b) }

    fun contentKey(message: BitchatMessage): String {
        val sender = message.senderPeerID ?: message.sender
        val bucket = message.timestamp.time / 5000
        val channelKey = normalizeChannel(message.channel, message.content)
        return "$sender|$channelKey|${message.content.trim()}|$bucket"
    }

    /** Camp timeline merges mesh public + Nostr geo — dedupe by visible sender/content/time. */
    fun campContentKey(message: BitchatMessage): String {
        val sender = message.sender.trim().lowercase()
        val bucket = message.timestamp.time / 5000
        return "$sender|${message.content.trim()}|$bucket"
    }

    fun normalizeChannel(channel: String?, content: String): String {
        GroupChatManager.parseMeshPayload(content)?.first?.let { return it }
        ChannelWireCodec.parse(content)?.first?.let { return it }
        when {
            channel?.startsWith("grp:") == true -> return channel
            channel?.startsWith("#") == true -> return channel
            channel?.startsWith("geo:") == true -> return CampChatManager.CAMP_MESH_CHANNEL
            channel == CampChatManager.CAMP_MESH_CHANNEL -> return CampChatManager.CAMP_MESH_CHANNEL
            else -> return "__public__"
        }
    }

    fun isGeolocPayload(raw: String): Boolean =
        raw.startsWith(com.neon.android.geohash.LocationSharingService.GEOLOC_PREFIX)
}
