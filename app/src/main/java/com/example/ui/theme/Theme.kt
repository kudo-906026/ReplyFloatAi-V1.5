package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color(0xFFFFEAEA),
    secondary = CoralGlow,
    onSecondary = Color.Black,
    secondaryContainer = DarkCardElevated,
    onSecondaryContainer = Color(0xFFFFD1D1),
    tertiary = EmeraldSuccess,
    background = DarkBg,
    onBackground = Color(0xFFF5EBE6),
    surface = DarkCard,
    onSurface = Color(0xFFF5EBE6),
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = TextMuted,
    outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E5),
    onPrimaryContainer = CrimsonDark,
    secondary = Color(0xFF991B1B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD5D5),
    onSecondaryContainer = Color(0xFF500707),
    tertiary = EmeraldSuccess,
    background = LightSurface,
    onBackground = Color(0xFF1F1212),
    surface = LightCard,
    onSurface = Color(0xFF1F1212),
    surfaceVariant = Color(0xFFF8EFEF),
    onSurfaceVariant = Color(0xFF6B5555),
    outline = Color(0xFFD8C4C4)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

