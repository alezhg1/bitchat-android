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
        UserRole.ADMIN to "bdd66a2380dda0ef9f406b8fb79c112986dec8c98272ff93e907d0f1c8d14c7f",
        UserRole.TEACHER to "c1e4d07ba632c4730f21695f63d8b1fc295cca871d8fa9918cc0aa1587e5835a"
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
