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
 * Loads pre-built QR payload strings from encrypted asset [ASSET_NAME].
 * Used by admins to display the teacher grant QR without typing keys manually.
 *
 * Regenerate with `tools/generate_role_keys.ps1`.
 */
object RoleQrRepository {

    private const val TAG = "RoleQrRepository"
    private const val ASSET_NAME = "role_qr.enc"

    private fun deriveKeys(context: Context): List<ByteArray> =
        RoleKeyPipeline.qrDerivationSalts(context) + LegacyRoleKeyDerivation.qrSaltV1()

    fun teacherQrPayload(context: Context): String? =
        loadPayloads(context)?.get(UserRole.TEACHER.name.lowercase())

    fun adminQrPayload(context: Context): String? =
        loadPayloads(context)?.get(UserRole.ADMIN.name.lowercase())

    private fun loadPayloads(context: Context): Map<String, String>? {
        readAsset(context)?.let { payload ->
            parsePayload(context, payload)?.let { return it }
        }
        return null
    }

    private fun readAsset(context: Context): ByteArray? = try {
        context.assets.open(ASSET_NAME).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun parsePayload(context: Context, payload: ByteArray): Map<String, String>? {
        for (salt in deriveKeys(context)) {
            decrypt(payload, salt)?.let { jsonText ->
                try {
                    val json = JSONObject(jsonText)
                    return buildMap {
                        if (json.has("admin")) put("admin", json.getString("admin"))
                        if (json.has("teacher")) put("teacher", json.getString("teacher"))
                    }.takeIf { it.isNotEmpty() }
                } catch (e: Exception) {
                    Log.w(TAG, "Role QR JSON invalid for one derivation attempt", e)
                }
            }
        }
        Log.e(TAG, "Failed to parse role QR payload")
        return null
    }

    private fun decrypt(payload: ByteArray, salt: ByteArray): String? = try {
        require(payload.size > 28) { "Invalid role QR payload" }
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
