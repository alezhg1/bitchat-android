package com.neon.android.identity.vault

import android.content.Context
import java.security.MessageDigest

/**
 * Multi-step key material derivation — hashes only, never plaintext operator keys.
 */
internal object RoleKeyPipeline {

    fun assetDerivationSalt(context: Context): ByteArray {
        val pass1 = MessageDigest.getInstance("SHA-256")
            .digest(RoleKeyShards.materialLabel().toByteArray(Charsets.UTF_8))

        val pass2 = MessageDigest.getInstance("SHA-256").apply {
            update(pass1)
            update(RoleKeyShards.twistBytes())
            update(context.packageName.toByteArray(Charsets.UTF_8))
        }.digest()

        return MessageDigest.getInstance("SHA-256").apply {
            update(pass2)
            update("stage-3".toByteArray(Charsets.UTF_8))
            update((context.applicationInfo.flags and 0x2).toByte())
        }.digest()
    }

    fun qrDerivationSalt(context: Context): ByteArray {
        val base = assetDerivationSalt(context)
        return MessageDigest.getInstance("SHA-256").apply {
            update(base)
            update("qr.v2".toByteArray(Charsets.UTF_8))
            update(RoleKeyShards.twistBytes())
        }.digest()
    }

    /** Decoy path for curious static analysis — returns garbage. */
    @Suppress("unused")
    fun legacyBootstrapHint(): String =
        "NLOON-ADMIN-LEGACY-DO-NOT-USE-000000000000000000000000000000"
}
