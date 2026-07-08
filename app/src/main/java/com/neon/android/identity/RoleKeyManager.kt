package com.neon.android.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Verifies secret role keys and stores the verified elevated role in encrypted storage.
 */
class RoleKeyManager private constructor(context: Context) {

    companion object {
        private const val TAG = "RoleKeyManager"
        private const val PREFS_NAME = "role_grants"
        private const val KEY_VERIFIED_ROLE = "verified_role"

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

    private val appContext = context.applicationContext
    private val roleHashes: Map<UserRole, String> by lazy { RoleKeyAsset.loadHashes(appContext) }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun roleForKey(key: String): UserRole? {
        val hash = try {
            sha256Hex(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash provided key", e)
            return null
        }
        return roleHashes.entries.firstOrNull { it.value == hash }?.key
    }

    fun verifyAndGrant(requestedRole: UserRole, key: String): Boolean {
        val unlocked = roleForKey(key) ?: return false
        if (unlocked != requestedRole) return false
        prefs.edit().putString(KEY_VERIFIED_ROLE, requestedRole.name).apply()
        return true
    }

    fun grantedRole(): UserRole =
        UserRole.fromString(prefs.getString(KEY_VERIFIED_ROLE, null))

    fun clearGrant() {
        prefs.edit().remove(KEY_VERIFIED_ROLE).apply()
    }

    /** Clamp persisted role to what was cryptographically granted. */
    fun enforceStoredRole(profileManager: UserProfileManager) {
        val stored = profileManager.getRole()
        if (stored == UserRole.STUDENT) return
        val granted = grantedRole()
        if (stored != granted) {
            profileManager.setRole(if (granted != UserRole.STUDENT) granted else UserRole.STUDENT)
        }
    }
}
