package com.neon.android.ui

/**
 * UI constants/utilities for nickname rendering.
 */
fun truncateNickname(name: String, maxLen: Int = com.neon.android.util.AppConstants.UI.MAX_NICKNAME_LENGTH): String {
    return if (name.length <= maxLen) name else name.take(maxLen)
}

