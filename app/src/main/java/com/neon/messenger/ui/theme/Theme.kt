package com.neon.messenger.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// NeoN Color Palette - Purple and Peach theme

// Dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8A2BE2),           // Акцентный фиолетовый
    onPrimary = Color.White,
    secondary = Color(0xFF9D4EDD),         // Светло-фиолетовый
    onSecondary = Color.White,
    tertiary = Color(0xFFFFA500),          // Персиковый/оранжевый
    onTertiary = Color.White,
    background = Color(0xFF121212),        // Тёмный фон
    onBackground = Color(0xFFFAFAFA),      // Светлый текст
    surface = Color(0xFF1E1E1E),           // Поверхность
    onSurface = Color(0xFFFAFAFA),         // Текст на поверхности
    surfaceVariant = Color(0xFF2D2D2D),    // Вариант поверхности
    onSurfaceVariant = Color(0xFFE0E0E0),  // Текст на варианте поверхности
    error = Color(0xFFF44336),             // Ошибка
    onError = Color.White,
    outline = Color(0xFF424242)            // Границы
)

// Light color scheme - основной светлый дизайн NeoN
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A2BE2),           // Акцентный фиолетовый
    onPrimary = Color.White,
    secondary = Color(0xFF9D4EDD),         // Светло-фиолетовый
    onSecondary = Color.White,
    tertiary = Color(0xFFFFA500),          // Персиковый/оранжевый для градиентов
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),        // Светло-серый фон (#fafafa)
    onBackground = Color(0xFF333333),      // Тёмно-серый текст (#333)
    surface = Color(0xFFFFFFFF),           // Белый фон поверхностей
    onSurface = Color(0xFF333333),         // Тёмно-серый текст
    surfaceVariant = Color(0xFFF5F5F7),    // Светло-серый вариант (#f5f5f7)
    onSurfaceVariant = Color(0xFF666666),  // Серый второстепенный текст (#666)
    error = Color(0xFFF44336),             // Ошибка
    onError = Color.White,
    outline = Color(0xFFE0E0E0)            // Границы
)

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
