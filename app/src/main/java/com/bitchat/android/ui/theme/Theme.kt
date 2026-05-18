package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// NLOON NLogN 2026 Color Palette - Purple and Orange theme (Light only)

// Light color scheme - основной светлый дизайн NLOON
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
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
