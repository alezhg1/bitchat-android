package com.neon.android.identity

import android.content.Context
import android.util.Log
import com.neon.android.identity.vault.LegacyRoleKeyDerivation
import com.neon.android.identity.vault.RoleKeyPipeline
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
/**
 * Loads role key hashes from an encrypted asset bundled with the app.
 * Plaintext operator keys are never stored on device — only SHA-256 hashes.
 */
object RoleKeyAsset {

    private const val TAG = "RoleKeyAsset"
    private const val ASSET_NAME = "role_keys.enc"

    fun loadHashes(context: Context): Map<UserRole, String> {
        readAsset(context)?.let { payload ->
            parsePayload(context, payload)?.let { return it }
        }
        Log.e(TAG, "Role key asset missing or corrupt")
        return emptyMap()
    }

    private fun readAsset(context: Context): ByteArray? = try {
        context.assets.open(ASSET_NAME).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun parsePayload(context: Context, payload: ByteArray): Map<UserRole, String>? {
        for (salt in derivationSalts(context)) {
            decrypt(payload, salt)?.let { jsonText ->
                try {
                    val json = JSONObject(jsonText)
                    return mapOf(
                        UserRole.ADMIN to json.getString("admin"),
                        UserRole.TEACHER to json.getString("teacher")
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Role key JSON invalid for one derivation attempt", e)
                }
            }
        }
        Log.e(TAG, "Failed to parse role key payload")
        return null
    }

    private fun derivationSalts(context: Context): List<ByteArray> =
        RoleKeyPipeline.assetDerivationSalts(context) + LegacyRoleKeyDerivation.assetSaltV1()

    private fun decrypt(payload: ByteArray, salt: ByteArray): String? = try {
        require(payload.size > 12) { "Invalid role key payload" }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(salt, "AES"),
            GCMParameterSpec(128, iv)
        )
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
