package com.knotssh.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.presentation.terminal.toAnnotatedString
import com.knotssh.presentation.terminal.toFontFamily
import com.knotssh.terminal.TerminalEmulator

/**
 * Live preview of the terminal appearance.
 *
 * Renders through a real [TerminalEmulator] and the same annotation pipeline as the terminal
 * itself, so the preview cannot drift from what the user will actually see.
 */
@Composable
fun TerminalAppearancePreview(
    settings: TerminalSettings,
    modifier: Modifier = Modifier
) {
    val lines = remember {
        val emulator = TerminalEmulator(PREVIEW_COLUMNS, PREVIEW_ROWS, scrollbackLimit = 0)
        emulator.writeLocal(PREVIEW_SCRIPT)
        emulator.snapshot().screen
    }

    val palette = remember(settings.theme) { settings.theme.palette }
    val foreground = remember(palette) { Color(palette.foreground) }
    val background = remember(palette) { Color(palette.background) }
    val linkColor = remember(palette) { Color(palette.color(12)) }
    val fontFamily = remember(settings.font) { settings.font.toFontFamily() }

    val textStyle = remember(settings.fontSize, fontFamily) {
        TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * 1.3f).sp,
            fontFamily = fontFamily
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(background)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            lines.forEach { line ->
                Text(
                    text = line.toAnnotatedString(
                        colorsEnabled = settings.ansiColorsEnabled,
                        palette = palette,
                        defaultForeground = foreground,
                        defaultBackground = background,
                        cursorColumn = null,
                        // Links are drawn but inert here: tapping a preview must not open a browser.
                        linkifyUrls = settings.clickableUrls,
                        linkColor = linkColor
                    ),
                    style = textStyle,
                    color = foreground,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}

private const val PREVIEW_COLUMNS = 44
private const val PREVIEW_ROWS = 7

/** Exercises bold, the 16 base colours, a 256-colour index, dim text and a URL. */
private val PREVIEW_SCRIPT = buildString {
    append("\u001B[1;32muser@server\u001B[0m:\u001B[1;34m~/srv\u001B[0m$ ls\r\n")
    append("\u001B[34mconfig\u001B[0m  \u001B[32mdeploy.sh\u001B[0m  README.md\r\n")
    append("\u001B[1;32muser@server\u001B[0m:\u001B[1;34m~/srv\u001B[0m$ systemctl status\r\n")
    append("\u001B[32m●\u001B[0m nginx.service \u2014 \u001B[1mactive (running)\u001B[0m\r\n")
    append("\u001B[33mwarning:\u001B[0m disk at 87%  \u001B[31merror:\u001B[0m 2 units failed\r\n")
    append("\u001B[38;5;208mdocs:\u001B[0m https://example.com/guide\r\n")
    append("\u001B[1;32muser@server\u001B[0m:\u001B[1;34m~/srv\u001B[0m$ \u001B[7m \u001B[0m")
}
