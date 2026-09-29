package com.knotssh.terminal

import androidx.annotation.StringRes
import com.knotssh.R

/**
 * A resolved colour scheme for the terminal: background, default foreground and the full
 * 256-entry ANSI table.
 *
 * Only the first 16 entries differ between themes; the 6x6x6 colour cube and the greyscale ramp
 * are fixed by the xterm specification, so applications asking for index 200 get the same colour
 * everywhere.
 */
class TerminalPalette(
    val background: Int,
    val foreground: Int,
    private val colors: IntArray
) {
    fun color(index: Int): Int = colors[index.coerceIn(0, 255)]
}

enum class TerminalTheme(@StringRes val labelRes: Int) {
    DARK(R.string.terminal_theme_dark),
    LIGHT(R.string.terminal_theme_light),
    HACKER(R.string.terminal_theme_hacker),
    MOLOKAI(R.string.terminal_theme_molokai),
    SOLARIZED_DARK(R.string.terminal_theme_solarized_dark),
    SOLARIZED_LIGHT(R.string.terminal_theme_solarized_light);

    val palette: TerminalPalette by lazy { AnsiPalette.build(background, foreground, base16) }

    private val background: Int
        get() = when (this) {
            DARK -> 0xFF0D1117.toInt()
            LIGHT -> 0xFFFFFFFF.toInt()
            HACKER -> 0xFF000000.toInt()
            MOLOKAI -> 0xFF1B1D1E.toInt()
            SOLARIZED_DARK -> 0xFF002B36.toInt()
            SOLARIZED_LIGHT -> 0xFFFDF6E3.toInt()
        }

    private val foreground: Int
        get() = when (this) {
            DARK -> 0xFFE6EDF3.toInt()
            LIGHT -> 0xFF24292F.toInt()
            HACKER -> 0xFF33FF33.toInt()
            MOLOKAI -> 0xFFF8F8F2.toInt()
            SOLARIZED_DARK -> 0xFF839496.toInt()
            SOLARIZED_LIGHT -> 0xFF657B83.toInt()
        }

    private val base16: IntArray
        get() = when (this) {
            DARK -> intArrayOf(
                0xFF1C2128.toInt(), 0xFFF85149.toInt(), 0xFF3FB950.toInt(), 0xFFD29922.toInt(),
                0xFF58A6FF.toInt(), 0xFFBC8CFF.toInt(), 0xFF39C5CF.toInt(), 0xFFB1BAC4.toInt(),
                0xFF6E7681.toInt(), 0xFFFF7B72.toInt(), 0xFF56D364.toInt(), 0xFFE3B341.toInt(),
                0xFF79C0FF.toInt(), 0xFFD2A8FF.toInt(), 0xFF56D4DD.toInt(), 0xFFF0F6FC.toInt()
            )

            LIGHT -> intArrayOf(
                0xFF24292F.toInt(), 0xFFCF222E.toInt(), 0xFF116329.toInt(), 0xFF9A6700.toInt(),
                0xFF0969DA.toInt(), 0xFF8250DF.toInt(), 0xFF1B7C83.toInt(), 0xFF6E7781.toInt(),
                0xFF57606A.toInt(), 0xFFA40E26.toInt(), 0xFF1A7F37.toInt(), 0xFF7D4E00.toInt(),
                0xFF218BFF.toInt(), 0xFFA475F9.toInt(), 0xFF3192AA.toInt(), 0xFF8C959F.toInt()
            )

            // Monochrome green phosphor: differentiation comes from brightness, not hue.
            HACKER -> intArrayOf(
                0xFF002200.toInt(), 0xFF00CC00.toInt(), 0xFF00FF00.toInt(), 0xFF66FF66.toInt(),
                0xFF00AA00.toInt(), 0xFF33FF33.toInt(), 0xFF00DD77.toInt(), 0xFF33FF33.toInt(),
                0xFF006600.toInt(), 0xFF44FF44.toInt(), 0xFF88FF88.toInt(), 0xFFAAFFAA.toInt(),
                0xFF22DD22.toInt(), 0xFF77FF77.toInt(), 0xFF55FFAA.toInt(), 0xFFCCFFCC.toInt()
            )

            MOLOKAI -> intArrayOf(
                0xFF1B1D1E.toInt(), 0xFFF92672.toInt(), 0xFFA6E22E.toInt(), 0xFFE6DB74.toInt(),
                0xFF66D9EF.toInt(), 0xFFAE81FF.toInt(), 0xFFA1EFE4.toInt(), 0xFFF8F8F2.toInt(),
                0xFF75715E.toInt(), 0xFFFF669D.toInt(), 0xFFBEED5F.toInt(), 0xFFE6DB74.toInt(),
                0xFF66D9EF.toInt(), 0xFF9E6FFE.toInt(), 0xFFA3BABF.toInt(), 0xFFF9F8F5.toInt()
            )

            // Solarized defines one palette shared by both variants; only base tones swap.
            SOLARIZED_DARK, SOLARIZED_LIGHT -> intArrayOf(
                0xFF073642.toInt(), 0xFFDC322F.toInt(), 0xFF859900.toInt(), 0xFFB58900.toInt(),
                0xFF268BD2.toInt(), 0xFFD33682.toInt(), 0xFF2AA198.toInt(), 0xFFEEE8D5.toInt(),
                0xFF002B36.toInt(), 0xFFCB4B16.toInt(), 0xFF586E75.toInt(), 0xFF657B83.toInt(),
                0xFF839496.toInt(), 0xFF6C71C4.toInt(), 0xFF93A1A1.toInt(), 0xFFFDF6E3.toInt()
            )
        }
}
