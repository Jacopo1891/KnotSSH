package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.Defaults

@Composable
fun TerminalSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    // valueLabel is a plain lambda, so the formatted string is resolved outside composition.
    val resources = LocalContext.current.resources

    SettingsScaffold(title = stringResource(R.string.settings_terminal), onBack = onBack) {
        item { SettingsSection(stringResource(R.string.section_buffer)) }
        item {
            SliderRow(
                title = stringResource(R.string.terminal_scrollback),
                description = stringResource(R.string.terminal_scrollback_desc),
                value = terminal.scrollbackLines,
                range = Defaults.SCROLLBACK_MIN..Defaults.SCROLLBACK_MAX,
                step = 500,
                valueLabel = { resources.getQuantityString(R.plurals.lines, it, it) },
                onValueChange = viewModel::setScrollbackLines
            )
        }

        item { SettingsSection(stringResource(R.string.section_size)) }
        item {
            SwitchRow(
                title = stringResource(R.string.terminal_auto_size),
                description = stringResource(R.string.terminal_auto_size_desc),
                checked = terminal.autoSizePty,
                onCheckedChange = viewModel::setAutoSizePty
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.terminal_fixed_columns),
                value = terminal.fallbackColumns,
                range = 40..200,
                step = 10,
                enabled = !terminal.autoSizePty,
                onValueChange = viewModel::setFallbackColumns
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.terminal_fixed_rows),
                value = terminal.fallbackRows,
                range = 10..80,
                step = 5,
                enabled = !terminal.autoSizePty,
                onValueChange = viewModel::setFallbackRows
            )
        }

        item { SettingsSection(stringResource(R.string.section_behaviour)) }
        item {
            SwitchRow(
                title = stringResource(R.string.terminal_clickable_urls),
                description = stringResource(R.string.terminal_clickable_urls_desc),
                checked = terminal.clickableUrls,
                onCheckedChange = viewModel::setClickableUrls
            )
        }
        item {
            ChoiceRow(
                title = stringResource(R.string.terminal_bell),
                description = stringResource(R.string.terminal_bell_desc),
                options = listOf(
                    BellMode.OFF to stringResource(R.string.bell_none),
                    BellMode.VIBRATE to stringResource(R.string.bell_vibrate),
                    BellMode.SOUND to stringResource(R.string.bell_sound)
                ),
                selected = terminal.bellMode,
                onSelected = viewModel::setBellMode
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.terminal_keep_screen_on),
                description = stringResource(R.string.terminal_keep_screen_on_desc),
                checked = terminal.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn
            )
        }
    }
}
