package com.neon.android.mesh

/**
 * Wire format for named mesh channels (e.g. #преподы).
 * Format: `[CHN:#channel]:text`
 */
object ChannelWireCodec {

    const val WIRE_PREFIX = "[CHN:"

    fun wrap(channel: String, content: String): String {
        val tag = if (channel.startsWith("#")) channel else "#$channel"
        return "$WIRE_PREFIX$tag]:$content"
    }

    fun parse(raw: String): Pair<String, String>? {
        if (!raw.startsWith(WIRE_PREFIX)) return null
        val end = raw.indexOf("]:")
        if (end <= WIRE_PREFIX.length) return null
        val channel = raw.substring(WIRE_PREFIX.length, end)
        val text = raw.substring(end + 2)
        if (channel.isBlank()) return null
        return channel to text
    }
}
