package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.Defaults
import com.knotssh.data.local.preferences.TerminalFont
import com.knotssh.data.local.preferences.ThemeMode
import com.knotssh.terminal.TerminalTheme

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = "Aspetto",
        onBack = onBack,
        header = { TerminalAppearancePreview(terminal) }
    ) {
        item { SettingsSection("Applicazione") }
        item {
            ChoiceRow(
                title = "Tema",
                options = listOf(
                    ThemeMode.SYSTEM to "Sistema",
                    ThemeMode.LIGHT to "Chiaro",
                    ThemeMode.DARK to "Scuro"
                ),
                selected = appearance.themeMode,
                onSelected = viewModel::setThemeMode
            )
        }
        item {
            SwitchRow(
                title = "Dynamic Color",
                description = "Usa la palette Material You del sistema",
                checked = appearance.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor
            )
        }

        item { SettingsSection("Terminale") }
        item {
            ChoiceRow(
                title = "Tema del terminale",
                description = "Sfondo, testo e tavolozza ANSI",
                options = TerminalTheme.entries.map { it to it.displayName },
                selected = terminal.theme,
                onSelected = viewModel::setTerminalTheme
            )
        }
        item {
            ChoiceRow(
                title = "Font",
                options = listOf(
                    TerminalFont.MONOSPACE to "Mono",
                    TerminalFont.SANS_SERIF to "Sans",
                    TerminalFont.SERIF to "Serif"
                ),
                selected = terminal.font,
                onSelected = viewModel::setTerminalFont
            )
        }
        item {
            SliderRow(
                title = "Dimensione carattere",
                value = terminal.fontSize,
                range = Defaults.FONT_SIZE_MIN..Defaults.FONT_SIZE_MAX,
                valueLabel = { "$it sp" },
                onValueChange = viewModel::setTerminalFontSize
            )
        }
        item {
            SwitchRow(
                title = "Colori ANSI",
                description = "Interpreta le sequenze di colore inviate dal server",
                checked = terminal.ansiColorsEnabled,
                onCheckedChange = viewModel::setAnsiColors
            )
        }
        item {
            SwitchRow(
                title = "Cursore lampeggiante",
                checked = terminal.cursorBlink,
                onCheckedChange = viewModel::setCursorBlink
            )
        }
    }
}
