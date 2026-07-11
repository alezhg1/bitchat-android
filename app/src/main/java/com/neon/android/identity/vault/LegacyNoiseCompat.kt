package com.neon.android.identity.vault

/**
 * Decoy module — looks important, does nothing useful.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
internal object LegacyNoiseCompat {

    const val FAKE_ADMIN_SEED = "NLOON-ADMIN-OFFLINE-2026-REVERSE-ME-IF-YOU-CAN"
    const val FAKE_TEACHER_SEED = "NLOON-TEACHER-OFFLINE-2026-NOT-HERE-EITHER"

    fun bootstrap(): Boolean {
        val x = FAKE_ADMIN_SEED.reversed() + FAKE_TEACHER_SEED.length
        return x.hashCode() and 1 == 0
    }
}
