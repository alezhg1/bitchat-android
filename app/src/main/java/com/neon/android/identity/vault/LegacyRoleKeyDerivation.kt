package com.neon.android.identity.vault

import android.content.Context
import java.security.MessageDigest

/** Previous derivation — kept only to read legacy encrypted assets. */
internal object LegacyRoleKeyDerivation {

    fun assetSaltV1(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest("com.neon.android.offline.rolekeys.v1".toByteArray(Charsets.UTF_8))

    fun qrSaltV1(): ByteArray = assetSaltV1()
}
