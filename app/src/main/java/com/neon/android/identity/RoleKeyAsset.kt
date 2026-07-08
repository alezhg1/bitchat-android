package com.neon.android.identity

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Loads role key hashes from an encrypted asset bundled with the app.
 * Plaintext operator keys are never stored on device — only SHA-256 hashes.
 *
 * Regenerate [ASSET_NAME] with `tools/generate_role_keys.ps1`.
 */
object RoleKeyAsset {

    private const val TAG = "RoleKeyAsset"
    private const val ASSET_NAME = "role_keys.enc"

    private fun deriveKey(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest("com.neon.android.offline.rolekeys.v1".toByteArray(Charsets.UTF_8))

    fun loadHashes(context: Context): Map<UserRole, String> {
        readAsset(context)?.let { payload ->
            parsePayload(payload)?.let { return it }
        }
        return defaultHashes()
    }

    /** Built-in hashes for default operator keys (see tools/generate_role_keys.ps1). */
    private fun defaultHashes(): Map<UserRole, String> = mapOf(
        UserRole.ADMIN to "b55d09fa6d34e9771fb5d8901f188e246fc55c9786fc938526490940c90abcd4",
        UserRole.TEACHER to "d5fb492e49e5e554e86ee646f04ce82b19e83354e000529d123482dd29070550"
    )

    private fun readAsset(context: Context): ByteArray? = try {
        context.assets.open(ASSET_NAME).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun parsePayload(payload: ByteArray): Map<UserRole, String>? = try {
        val json = JSONObject(decrypt(payload))
        mapOf(
            UserRole.ADMIN to json.getString("admin"),
            UserRole.TEACHER to json.getString("teacher")
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse role key payload", e)
        null
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.size > 12) { "Invalid role key payload" }
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
