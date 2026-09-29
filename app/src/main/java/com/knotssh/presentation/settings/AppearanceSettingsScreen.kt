package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
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
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        header = { TerminalAppearancePreview(terminal) }
    ) {
        item { SettingsSection(stringResource(R.string.section_application)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.appearance_theme),
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_mode_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_mode_light),
                    ThemeMode.DARK to stringResource(R.string.theme_mode_dark)
                ),
                selected = appearance.themeMode,
                onSelected = viewModel::setThemeMode
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.appearance_dynamic_color),
                description = stringResource(R.string.appearance_dynamic_color_desc),
                checked = appearance.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor
            )
        }

        item { SettingsSection(stringResource(R.string.settings_terminal)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.appearance_terminal_theme),
                description = stringResource(R.string.appearance_terminal_theme_desc),
                options = TerminalTheme.entries.map { it to stringResource(it.labelRes) },
                selected = terminal.theme,
                onSelected = viewModel::setTerminalTheme
            )
        }
        item {
            ChoiceRow(
                title = stringResource(R.string.appearance_font),
                options = listOf(
                    TerminalFont.MONOSPACE to stringResource(R.string.font_mono),
                    TerminalFont.SANS_SERIF to stringResource(R.string.font_sans),
                    TerminalFont.SERIF to stringResource(R.string.font_serif)
                ),
                selected = terminal.font,
                onSelected = viewModel::setTerminalFont
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.appearance_font_size),
                value = terminal.fontSize,
                range = Defaults.FONT_SIZE_MIN..Defaults.FONT_SIZE_MAX,
                valueLabel = { "$it sp" },
                onValueChange = viewModel::setTerminalFontSize
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.appearance_ansi_colors),
                description = stringResource(R.string.appearance_ansi_colors_desc),
                checked = terminal.ansiColorsEnabled,
                onCheckedChange = viewModel::setAnsiColors
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.appearance_cursor_blink),
                checked = terminal.cursorBlink,
                onCheckedChange = viewModel::setCursorBlink
            )
        }
    }
}
