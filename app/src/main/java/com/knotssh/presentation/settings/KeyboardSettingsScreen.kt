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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    SettingsScaffold(title = "Tastiera", onBack = onBack) {
        item { SettingsSection("Barra tasti speciali") }
        item {
            SwitchRow(
                title = "Mostra la barra",
                description = "ESC, TAB, frecce, CTRL/ALT e tasti personalizzati",
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
                Text("Gestisci tasti rapidi (${customKeys.size})")
            }
        }

        item { SettingsSection("Comandi rapidi") }
        item {
            SwitchRow(
                title = "Mostra la barra",
                description = "Chip che inviano un comando completo con a capo",
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
                Text("Gestisci comandi rapidi (${quickCommands.size})")
            }
        }

        item { SettingsSection("Input") }
        item {
            SwitchRow(
                title = "Apri tastiera automaticamente",
                description = "Alla connessione. Un tocco sul buffer la apre comunque.",
                checked = terminal.autoShowKeyboard,
                onCheckedChange = viewModel::setAutoShowKeyboard
            )
        }
        item {
            SwitchRow(
                title = "Feedback aptico",
                description = "Vibrazione breve sui tasti della barra",
                checked = terminal.hapticFeedback,
                onCheckedChange = viewModel::setHapticFeedback
            )
        }
    }
}
