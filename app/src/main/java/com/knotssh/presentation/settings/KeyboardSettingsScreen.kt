package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R

@Composable
fun KeyboardSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    val quickCommands by viewModel.quickCommandList.collectAsStateWithLifecycle()
    val customKeys by viewModel.customKeyList.collectAsStateWithLifecycle()

    var showQuickCommands by remember { mutableStateOf(false) }
    var showCustomKeys by remember { mutableStateOf(false) }

    if (showQuickCommands) {
        QuickCommandsDialog(
            commands = quickCommands,
            onAdd = viewModel::addQuickCommand,
            onDelete = viewModel::deleteQuickCommand,
            onDismiss = { showQuickCommands = false }
        )
    }

    if (showCustomKeys) {
        CustomKeysDialog(
            keys = customKeys,
            onAdd = viewModel::addCustomKey,
            onDelete = viewModel::deleteCustomKey,
            onRestoreDefaults = viewModel::restoreDefaultCustomKeys,
            onDismiss = { showCustomKeys = false }
        )
    }

    SettingsScaffold(title = stringResource(R.string.settings_keyboard), onBack = onBack) {
        item { SettingsSection(stringResource(R.string.section_key_bar)) }
        item {
            SwitchRow(
                title = stringResource(R.string.keyboard_show_bar),
                description = stringResource(R.string.keyboard_show_bar_desc),
                checked = terminal.showAccessoryBar,
                onCheckedChange = viewModel::setShowAccessoryBar
            )
        }
        item {
            OutlinedButton(
                onClick = { showCustomKeys = true },
                enabled = terminal.showAccessoryBar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.keyboard_manage_keys, customKeys.size))
            }
        }

        item { SettingsSection(stringResource(R.string.section_quick_commands)) }
        item {
            SwitchRow(
                title = stringResource(R.string.keyboard_show_bar),
                description = stringResource(R.string.keyboard_quick_bar_desc),
                checked = terminal.showQuickCommandsBar,
                onCheckedChange = viewModel::setShowQuickCommandsBar
            )
        }
        item {
            OutlinedButton(
                onClick = { showQuickCommands = true },
                enabled = terminal.showQuickCommandsBar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.keyboard_manage_commands, quickCommands.size))
            }
        }

        item { SettingsSection(stringResource(R.string.section_input)) }
        item {
            SwitchRow(
                title = stringResource(R.string.keyboard_auto_show),
                description = stringResource(R.string.keyboard_auto_show_desc),
                checked = terminal.autoShowKeyboard,
                onCheckedChange = viewModel::setAutoShowKeyboard
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.keyboard_swipe_scroll),
                description = stringResource(R.string.keyboard_swipe_scroll_desc),
                checked = terminal.swipeScrollsFullScreenApps,
                onCheckedChange = viewModel::setSwipeScrollsFullScreenApps
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.keyboard_haptics),
                description = stringResource(R.string.keyboard_haptics_desc),
                checked = terminal.hapticFeedback,
                onCheckedChange = viewModel::setHapticFeedback
            )
        }
    }
}
