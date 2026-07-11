package com.neon.android.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.neon.android.identity.vault.LegacyNoiseCompat
import com.neon.android.identity.vault.RoleKeyVault
import java.security.MessageDigest

/**
 * Verifies secret role keys and stores the verified elevated role in encrypted storage.
 */
class RoleKeyManager private constructor(context: Context) {

    companion object {
        private const val TAG = "RoleKeyManager"
        private const val PREFS_NAME = "role_grants"
        private const val KEY_VERIFIED_ROLE = "verified_role"

        // Touch decoy so R8 does not strip the bait package.
        @Suppress("UNUSED_VARIABLE")
        private val decoyBoot = LegacyNoiseCompat.bootstrap()

        @Volatile
        private var INSTANCE: RoleKeyManager? = null

        fun getInstance(context: Context): RoleKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoleKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun normalizeKey(key: String): String =
            key.trim().replace("\u200B", "").replace("\uFEFF", "")
    }

    private val roleHashes: Map<UserRole, String> =
        RoleKeyVault.resolveHashes(context.applicationContext)

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun roleForKey(key: String): UserRole? {
        val hash = try {
            sha256Hex(normalizeKey(key))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash provided key", e)
            return null
        }
        return roleHashes.entries.firstOrNull { it.value == hash }?.key
    }

    fun matchesRoleKey(role: UserRole, key: String): Boolean {
        if (role == UserRole.STUDENT) return true
        val hash = sha256Hex(normalizeKey(key))
        return roleHashes[role] == hash
    }

    fun verifyAndGrant(requestedRole: UserRole, key: String): Boolean {
        if (!matchesRoleKey(requestedRole, key)) return false
        return try {
            prefs.edit().putString(KEY_VERIFIED_ROLE, requestedRole.name).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist role grant", e)
            // Key was valid even if encrypted prefs failed — allow login for this session.
            true
        }
    }

    /** Parse a scanned role QR and persist the grant if valid. */
    fun grantFromQrPayload(raw: String): UserRole? {
        val (role, key) = RoleQrCodec.decode(raw) ?: return null
        return if (verifyAndGrant(role, key)) role else null
    }

    fun grantedRole(): UserRole =
        UserRole.fromString(prefs.getString(KEY_VERIFIED_ROLE, null))

    fun clearGrant() {
        try {
            prefs.edit().remove(KEY_VERIFIED_ROLE).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear role grant", e)
        }
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
