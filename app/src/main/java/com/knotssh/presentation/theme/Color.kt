package com.knotssh.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// === Brand Colors ===
val SshBlue = Color(0xFF1A73E8)
val SshBlueDark = Color(0xFF8AB4F8)
val SshBlueContainer = Color(0xFF1557B0)
val SshTeal = Color(0xFF00897B)
val SshTealDark = Color(0xFF4DB6AC)
val SshGreen = Color(0xFF34A853)
val SshRed = Color(0xFFEA4335)
val SshAmber = Color(0xFFFBBC04)

// === Dark scheme surfaces ===
val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF1A1D27)
val DarkSurfaceVariant = Color(0xFF252836)
val DarkSurfaceContainer = Color(0xFF1E2130)
val DarkSurfaceContainerHigh = Color(0xFF262A3D)
val DarkOutline = Color(0xFF3E4257)
val DarkOnSurface = Color(0xFFE3E5F0)
val DarkOnSurfaceVariant = Color(0xFFA0A4B8)

// === Light scheme surfaces ===
val LightBackground = Color(0xFFF5F7FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EBF8)
val LightSurfaceContainer = Color(0xFFEEF0FC)
val LightOutline = Color(0xFFB0B5CF)
val LightOnSurface = Color(0xFF1A1D2E)
val LightOnSurfaceVariant = Color(0xFF4A4E6A)

// Terminal-specific colors
val TerminalBackground = Color(0xFF0D1117)
val TerminalText = Color(0xFFE6EDF3)
val TerminalGreen = Color(0xFF3FB950)
val TerminalRed = Color(0xFFF85149)
val TerminalYellow = Color(0xFFD29922)
val TerminalBlue = Color(0xFF58A6FF)
val TerminalCyan = Color(0xFF39C5CF)
val TerminalMagenta = Color(0xFFBC8CFF)

val DarkColorScheme = darkColorScheme(
    primary = SshBlueDark,
    onPrimary = Color(0xFF002878),
    primaryContainer = SshBlueContainer,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = SshTealDark,
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = SshAmber,
    onTertiary = Color(0xFF3D2E00),
    error = SshRed,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2A2E45),
    inverseSurface = Color(0xFFE3E5F0),
    inverseOnSurface = Color(0xFF1A1D2E),
    inversePrimary = SshBlue,
    scrim = Color(0x99000000),
)

val LightColorScheme = lightColorScheme(
    primary = SshBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = SshTeal,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF7B5800),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    outline = LightOutline,
    outlineVariant = Color(0xFFD0D4E8),
    inverseSurface = Color(0xFF1A1D2E),
    inverseOnSurface = Color(0xFFF0F0FF),
    inversePrimary = SshBlueDark,
    scrim = Color(0x99000000),
)
