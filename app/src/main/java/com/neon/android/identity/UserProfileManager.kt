package com.neon.android.identity

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user profile: editable FIO (display name) and immutable static ID.
 * Static ID is derived from the Noise identity fingerprint and cannot be changed.
 */
class UserProfileManager private constructor(context: Context) {

  companion object {
    private const val PREFS_NAME = "user_profile"
    private const val KEY_FIO = "fio"
    private const val KEY_STATIC_ID = "static_id"
    private const val KEY_ROLE = "role"
    private const val KEY_PROFILE_SETUP_DONE = "profile_setup_done"

    @Volatile
    private var INSTANCE: UserProfileManager? = null

    fun getInstance(context: Context): UserProfileManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: UserProfileManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getStaticId(): String? = prefs.getString(KEY_STATIC_ID, null)

  fun ensureStaticId(peerId: String) {
    if (prefs.getString(KEY_STATIC_ID, null) == null) {
      prefs.edit().putString(KEY_STATIC_ID, peerId).apply()
    }
  }

  fun getFio(): String {
    return prefs.getString(KEY_FIO, null) ?: "Пользователь"
  }

  fun setFio(fio: String) {
    prefs.edit().putString(KEY_FIO, fio.trim()).apply()
  }

  fun getRole(): UserRole = UserRole.fromString(prefs.getString(KEY_ROLE, null))

  fun setRole(role: UserRole) {
    prefs.edit().putString(KEY_ROLE, role.name).apply()
  }

  fun isProfileSetupDone(): Boolean = prefs.getBoolean(KEY_PROFILE_SETUP_DONE, false)

  fun markProfileSetupDone() {
    prefs.edit().putBoolean(KEY_PROFILE_SETUP_DONE, true).apply()
  }

  /** Display name used in chat — FIO if set, otherwise nickname fallback. */
  fun getDisplayName(nicknameFallback: String): String {
    val fio = prefs.getString(KEY_FIO, null)
    return if (!fio.isNullOrBlank()) fio else nicknameFallback
  }
}
