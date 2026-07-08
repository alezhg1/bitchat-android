package com.neon.android.identity

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Offline role grant payloads embedded in QR codes.
 *
 * Format: `nlogn://role/v1/{ROLE}#{url-encoded-key}`
 *
 * Scanned locally — never sent over mesh/Nostr. Teacher QR is shown only by admins in-app.
 */
object RoleQrCodec {

    const val PREFIX = "nlogn://role/v1/"

    fun encode(role: UserRole, key: String): String {
        require(role != UserRole.STUDENT) { "Cannot encode student role" }
        val normalized = RoleKeyManager.normalizeKey(key)
        val encodedKey = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
        return "$PREFIX${role.name}#$encodedKey"
    }

    fun decode(raw: String): Pair<UserRole, String>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = false)) return null
        val rest = trimmed.removePrefix(PREFIX)
        val hashIdx = rest.indexOf('#')
        if (hashIdx <= 0) return null
        val role = UserRole.fromString(rest.substring(0, hashIdx).uppercase())
        if (role == UserRole.STUDENT) return null
        val keyPart = rest.substring(hashIdx + 1)
        if (keyPart.isBlank()) return null
        return try {
            role to URLDecoder.decode(keyPart, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }
}
