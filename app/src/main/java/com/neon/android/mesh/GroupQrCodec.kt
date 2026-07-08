package com.neon.android.mesh

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** QR payload to join a user-created mesh group: `nlogn://group/v1/{id}#{name}` */
object GroupQrCodec {

    const val PREFIX = "nlogn://group/v1/"

    fun encode(groupId: String, name: String): String {
        val id = groupId.removePrefix("grp:")
        val encodedName = URLEncoder.encode(name.trim(), StandardCharsets.UTF_8.name())
        return "$PREFIX$id#$encodedName"
    }

    fun decode(raw: String): Pair<String, String>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val rest = trimmed.removePrefix(PREFIX)
        val hashIdx = rest.indexOf('#')
        if (hashIdx <= 0) return null
        val id = rest.substring(0, hashIdx).trim()
        val name = rest.substring(hashIdx + 1).trim()
        if (id.isBlank() || name.isBlank()) return null
        return try {
            id to URLDecoder.decode(name, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }
}
