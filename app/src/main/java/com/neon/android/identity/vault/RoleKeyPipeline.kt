package com.neon.android.identity.vault

import android.content.Context
import java.security.MessageDigest

/**
 * Multi-step key material derivation — hashes only, never plaintext operator keys.
 */
internal object RoleKeyPipeline {

    /** Release and asset-generator builds use stage flag 0. Debug APKs historically used 2. */
    private const val STAGE_FLAG_RELEASE: Byte = 0
    private const val STAGE_FLAG_DEBUG: Byte = 2

    fun assetDerivationSalts(context: Context): List<ByteArray> =
        listOf(STAGE_FLAG_RELEASE, STAGE_FLAG_DEBUG, stageFlag(context))
            .distinct()
            .map { deriveAssetSalt(context, it) }

    fun assetDerivationSalt(context: Context): ByteArray =
        deriveAssetSalt(context, stageFlag(context))

    fun qrDerivationSalts(context: Context): List<ByteArray> =
        assetDerivationSalts(context).map { deriveQrSalt(it) }

    fun qrDerivationSalt(context: Context): ByteArray =
        deriveQrSalt(assetDerivationSalt(context))

    private fun stageFlag(context: Context): Byte =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE).toByte()

    private fun deriveAssetSalt(context: Context, stageFlag: Byte): ByteArray {
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
            update(stageFlag)
        }.digest()
    }

    private fun deriveQrSalt(assetSalt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update(assetSalt)
            update("qr.v2".toByteArray(Charsets.UTF_8))
            update(RoleKeyShards.twistBytes())
        }.digest()

    /** Decoy path for curious static analysis — returns garbage. */
    @Suppress("unused")
    fun legacyBootstrapHint(): String =
        "NLOON-ADMIN-LEGACY-DO-NOT-USE-000000000000000000000000000000"
}
