package com.neon.android.ui.theme

/**
 * App theme preference: System default, Light, or Dark.
 * NOTE: Theme selection has been removed. App now uses light theme only.
 */
enum class ThemePreference {
    Light;

    val isSystem : Boolean get() = false
    val isLight : Boolean get() = true
    val isDark : Boolean get() = false
}

/**
 * Simple SharedPreferences-backed manager for theme preference with a StateFlow.
 * NOTE: Theme selection has been removed. This now always returns Light theme.
 */
object ThemePreferenceManager {
    private const val PREFS_NAME = "bitchat_settings"
    
    private val _themeFlow = kotlinx.coroutines.flow.MutableStateFlow(ThemePreference.Light)
    val themeFlow: kotlinx.coroutines.flow.StateFlow<ThemePreference> = _themeFlow

    fun init(context: android.content.Context) {
        // No-op: theme selection removed, always using light theme
    }

    fun set(context: android.content.Context, preference: ThemePreference) {
        // No-op: theme selection removed, always using light theme
    }
}
