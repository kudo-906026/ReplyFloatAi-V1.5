package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Control Panel & Futuristic Dark Palette
val DarkBg = Color(0xFF0D0F14)
val DarkSurfaceCard = Color(0xFF141822)
val DarkCard = Color(0xFF161B26)
val DarkCardElevated = Color(0xFF1C2230)
val DarkSurfaceVariant = Color(0xFF222938)
val DarkCardBorder = Color(0xFF2B3346)

// Accent Colors
val CrimsonPrimary = Color(0xFFFF3366)
val CrimsonLight = Color(0xFFFF6688)
val CrimsonDark = Color(0xFFCC1144)

val TechBlue = Color(0xFF38BDF8)
val TechGreen = Color(0xFF4ADE80)
val AccentGreen = Color(0xFF10B981)
val AccentGreenLight = Color(0xFF34D399)
val AccentPurple = Color(0xFFA855F7)
val AccentPurpleLight = Color(0xFFC084FC)
val AccentBlue = Color(0xFF00E5FF)
val AccentYellow = Color(0xFFFBBF24)

// Text Colors
val TextWhite = Color(0xFFF8FAFC)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color.White,
    secondary = TechBlue,
    onSecondary = Color.Black,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TechBlue,
    tertiary = AccentPurple,
    background = DarkBg,
    onBackground = TextWhite,
    surface = DarkSurfaceCard,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder
)

@Composable
fun ReplyFloatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
