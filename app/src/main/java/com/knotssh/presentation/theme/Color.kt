package com.knotssh.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// === Status colors, shared by every palette ===
val SshGreen = Color(0xFF34A853)
val SshAmber = Color(0xFFFBBC04)

// Terminal-specific colors
val TerminalBackground = Color(0xFF0D1117)
val TerminalText = Color(0xFFE6EDF3)
val TerminalGreen = Color(0xFF3FB950)
val TerminalRed = Color(0xFFF85149)
val TerminalYellow = Color(0xFFD29922)
val TerminalBlue = Color(0xFF58A6FF)
val TerminalCyan = Color(0xFF39C5CF)
val TerminalMagenta = Color(0xFFBC8CFF)

// === KnotSSH red: a softened take on the launcher icon's deep red ===
internal val KnotRedLight = lightColorScheme(
    primary = Color(0xFF9C4143),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF3E0408),
    secondary = Color(0xFF775655),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD8),
    onSecondaryContainer = Color(0xFF2C1514),
    tertiary = Color(0xFF755A2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF281900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDB),
    onSurfaceVariant = Color(0xFF534342),
    surfaceTint = Color(0xFF9C4143),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    inverseSurface = Color(0xFF392E2D),
    inverseOnSurface = Color(0xFFFFEDEB),
    inversePrimary = Color(0xFFFFB3B1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceDim = Color(0xFFE8D6D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EF),
    surfaceContainer = Color(0xFFFCEAE8),
    surfaceContainerHigh = Color(0xFFF6E4E2),
    surfaceContainerHighest = Color(0xFFF0DEDC)
)

internal val KnotRedDark = darkColorScheme(
    primary = Color(0xFFFFB3B1),
    onPrimary = Color(0xFF5F1315),
    primaryContainer = Color(0xFF7D2A2B),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFE7BDBB),
    onSecondary = Color(0xFF442929),
    secondaryContainer = Color(0xFF5D3F3E),
    onSecondaryContainer = Color(0xFFFFDAD8),
    tertiary = Color(0xFFE5C18D),
    onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5B421A),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1111),
    onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1111),
    onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceTint = Color(0xFFFFB3B1),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534342),
    inverseSurface = Color(0xFFF1DEDC),
    inverseOnSurface = Color(0xFF392E2D),
    inversePrimary = Color(0xFF9C4143),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413736),
    surfaceDim = Color(0xFF1A1111),
    surfaceContainerLowest = Color(0xFF140C0C),
    surfaceContainerLow = Color(0xFF231919),
    surfaceContainer = Color(0xFF271D1D),
    surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3231)
)

// === Google blue: the palette the app shipped with ===
internal val GoogleBlueLight = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF7B5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F7FF),
    onBackground = Color(0xFF1A1D2E),
    surface = Color(0xFFF5F7FF),
    onSurface = Color(0xFF1A1D2E),
    surfaceVariant = Color(0xFFE8EBF8),
    onSurfaceVariant = Color(0xFF4A4E6A),
    surfaceTint = Color(0xFF1A73E8),
    outline = Color(0xFFB0B5CF),
    outlineVariant = Color(0xFFD0D4E8),
    inverseSurface = Color(0xFF1A1D2E),
    inverseOnSurface = Color(0xFFF0F0FF),
    inversePrimary = Color(0xFF8AB4F8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDFE2F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2FC),
    surfaceContainer = Color(0xFFEAEDF9),
    surfaceContainerHigh = Color(0xFFE4E7F5),
    surfaceContainerHighest = Color(0xFFDEE1F0)
)

internal val GoogleBlueDark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002878),
    primaryContainer = Color(0xFF1557B0),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFBBC04),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFEA4335),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE3E5F0),
    surface = Color(0xFF0F1117),
    onSurface = Color(0xFFE3E5F0),
    surfaceVariant = Color(0xFF252836),
    onSurfaceVariant = Color(0xFFA0A4B8),
    surfaceTint = Color(0xFF8AB4F8),
    outline = Color(0xFF3E4257),
    outlineVariant = Color(0xFF2A2E45),
    inverseSurface = Color(0xFFE3E5F0),
    inverseOnSurface = Color(0xFF1A1D2E),
    inversePrimary = Color(0xFF1A73E8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E3245),
    surfaceDim = Color(0xFF0F1117),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF161922),
    surfaceContainer = Color(0xFF1A1D27),
    surfaceContainerHigh = Color(0xFF222636),
    surfaceContainerHighest = Color(0xFF2C3043)
)

/**
 * Pure-black variant for OLED panels. Only the neutral surfaces change; accents stay as they are,
 * and the containers keep a faint lift so cards and list groups remain distinguishable.
 */
internal fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF242424),
    surfaceBright = Color(0xFF2E2E2E)
)
