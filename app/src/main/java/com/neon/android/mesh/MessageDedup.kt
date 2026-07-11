package com.neon.android.mesh

import com.neon.android.model.BitchatMessage
import com.neon.android.protocol.BitchatPacket
import com.neon.android.sync.PacketIdUtil

/**
 * Stable deduplication keys for mesh messages so relay paths do not duplicate chat lines.
 */
object MessageDedup {

    fun packetId(packet: BitchatPacket): String = PacketIdUtil.computeIdHex(packet)

    fun contentKey(message: BitchatMessage): String {
        val sender = message.senderPeerID ?: message.sender
        val channel = message.channel.orEmpty()
        val bucket = message.timestamp.time / 2000
        return "$sender|$channel|${message.content.hashCode()}|$bucket"
    }

    fun isGeolocPayload(raw: String): Boolean =
        raw.startsWith(com.neon.android.geohash.LocationSharingService.GEOLOC_PREFIX)
}
