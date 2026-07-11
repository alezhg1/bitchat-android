package com.neon.android.identity.vault

/**
 * Scattered derivation material — not useful alone.
 */
internal object RoleKeyShards {

    private val shardA = intArrayOf(0x6e, 0x65, 0x6f, 0x6e, 0x2e, 0x63, 0x61, 0x6d, 0x70)
    private val shardB = intArrayOf(0x2e, 0x76, 0x32, 0x2e, 0x72, 0x6f, 0x6c, 0x65)
    private val shardC = intArrayOf(0x2e, 0x6b, 0x65, 0x79, 0x73, 0x2e, 0x6f, 0x66, 0x66)

    private val twist = byteArrayOf(
        0x4e, 0x4c, 0x4f, 0x4f, 0x4e, 0x2d, 0x32, 0x30,
        0x32, 0x36, 0x2d, 0x63, 0x61, 0x6d, 0x70, 0x2d
    )

    fun materialLabel(): String =
        buildString(shardA.size + shardB.size + shardC.size) {
            shardA.forEach { append(it.toChar()) }
            shardB.forEach { append(it.toChar()) }
            shardC.forEach { append(it.toChar()) }
        }

    fun twistBytes(): ByteArray = twist.copyOf()
}
