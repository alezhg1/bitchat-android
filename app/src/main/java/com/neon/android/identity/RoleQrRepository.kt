package com.neon.android.identity

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Loads pre-built QR payload strings from encrypted asset [ASSET_NAME].
 * Used by admins to display the teacher grant QR without typing keys manually.
 *
 * Regenerate with `tools/generate_role_keys.ps1`.
 */
object RoleQrRepository {

    private const val TAG = "RoleQrRepository"
    private const val ASSET_NAME = "role_qr.enc"

    private fun deriveKey(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest("com.neon.android.offline.roleqr.v1".toByteArray(Charsets.UTF_8))

    fun teacherQrPayload(context: Context): String? =
        loadPayloads(context)?.get(UserRole.TEACHER.name.lowercase())

    fun adminQrPayload(context: Context): String? =
        loadPayloads(context)?.get(UserRole.ADMIN.name.lowercase())

    private fun loadPayloads(context: Context): Map<String, String>? {
        readAsset(context)?.let { payload ->
            parsePayload(payload)?.let { return it }
        }
        return null
    }

    private fun readAsset(context: Context): ByteArray? = try {
        context.assets.open(ASSET_NAME).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun parsePayload(payload: ByteArray): Map<String, String>? = try {
        val json = JSONObject(decrypt(payload))
        buildMap {
            if (json.has("admin")) put("admin", json.getString("admin"))
            if (json.has("teacher")) put("teacher", json.getString("teacher"))
        }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse role QR payload", e)
        null
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.size > 28) { "Invalid role QR payload" }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveKey(), "AES"),
            GCMParameterSpec(128, iv)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
