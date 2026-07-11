package com.neon.android.identity.vault

import android.content.Context
import com.neon.android.identity.UserRole

/**
 * Resolves role key hashes through encrypted assets + scattered derivation.
 */
object RoleKeyVault {

    fun resolveHashes(context: Context): Map<UserRole, String> =
        com.neon.android.identity.RoleKeyAsset.loadHashes(context)

    fun curiousNotesAsset(): String = "offline/notes_for_curious_minds.txt"
}
