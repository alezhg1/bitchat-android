package com.neon.android.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Verifies secret role keys and stores the verified elevated role in encrypted storage.
 *
 * The messenger is fully offline, so the reference secrets ship inside the app as SHA-256
 * hashes (never the plaintext keys). A user who types the correct key for a role is granted
 * that role; the grant itself is persisted encrypted so it survives restarts without keeping
 * the plaintext key anywhere on the device.
 */
class RoleKeyManager private constructor(context: Context) {

    companion object {
        private const val TAG = "RoleKeyManager"
        private const val PREFS_NAME = "role_grants"
        private const val KEY_VERIFIED_ROLE = "verified_role"

        // SHA-256 hashes of the long secret keys. Plaintext keys are handed to the operator
        // out-of-band and are intentionally NOT stored anywhere in the app.
        private const val ADMIN_KEY_HASH =
            "bdd66a2380dda0ef9f406b8fb79c112986dec8c98272ff93e907d0f1c8d14c7f"
        private const val TEACHER_KEY_HASH =
            "c1e4d07ba632c4730f21695f63d8b1fc295cca871d8fa9918cc0aa1587e5835a"

        @Volatile
        private var INSTANCE: RoleKeyManager? = null

        fun getInstance(context: Context): RoleKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoleKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.trim().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the role unlocked by [key], or null if the key matches no elevated role. */
    fun roleForKey(key: String): UserRole? {
        val hash = try {
            sha256Hex(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash provided key", e)
            return null
        }
        return when (hash) {
            ADMIN_KEY_HASH -> UserRole.ADMIN
            TEACHER_KEY_HASH -> UserRole.TEACHER
            else -> null
        }
    }

    /** Verifies [key] for the [requestedRole]; on success persists the grant encrypted. */
    fun verifyAndGrant(requestedRole: UserRole, key: String): Boolean {
        val unlocked = roleForKey(key) ?: return false
        if (unlocked != requestedRole) return false
        prefs.edit().putString(KEY_VERIFIED_ROLE, requestedRole.name).apply()
        return true
    }

    /** Role previously granted via a valid key, or STUDENT if none. */
    fun grantedRole(): UserRole =
        UserRole.fromString(prefs.getString(KEY_VERIFIED_ROLE, null))

    fun clearGrant() {
        prefs.edit().remove(KEY_VERIFIED_ROLE).apply()
    }
}
