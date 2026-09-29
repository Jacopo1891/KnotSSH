package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.Defaults

@Composable
fun TerminalSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Terminale", onBack = onBack) {
        item { SettingsSection("Buffer") }
        item {
            SliderRow(
                title = "Righe di scrollback",
                description = "Storico mantenuto in memoria. Valori alti consumano più RAM.",
                value = terminal.scrollbackLines,
                range = Defaults.SCROLLBACK_MIN..Defaults.SCROLLBACK_MAX,
                step = 500,
                valueLabel = { "$it righe" },
                onValueChange = viewModel::setScrollbackLines
            )
        }

        item { SettingsSection("Dimensioni") }
        item {
            SwitchRow(
                title = "Adatta dimensione al display",
                description = "Comunica al server righe e colonne reali dello schermo",
                checked = terminal.autoSizePty,
                onCheckedChange = viewModel::setAutoSizePty
            )
        }
        item {
            SliderRow(
                title = "Colonne fisse",
                value = terminal.fallbackColumns,
                range = 40..200,
                step = 10,
                enabled = !terminal.autoSizePty,
                onValueChange = viewModel::setFallbackColumns
            )
        }
        item {
            SliderRow(
                title = "Righe fisse",
                value = terminal.fallbackRows,
                range = 10..80,
                step = 5,
                enabled = !terminal.autoSizePty,
                onValueChange = viewModel::setFallbackRows
            )
        }

        item { SettingsSection("Comportamento") }
        item {
            SwitchRow(
                title = "URL cliccabili",
                description = "Riconosce gli indirizzi http/https nell'output e li apre nel browser",
                checked = terminal.clickableUrls,
                onCheckedChange = viewModel::setClickableUrls
            )
        }
        item {
            ChoiceRow(
                title = "Campanella (BEL)",
                description = "Comportamento quando il server invia \\a",
                options = listOf(
                    BellMode.OFF to "Nessuno",
                    BellMode.VIBRATE to "Vibrazione",
                    BellMode.SOUND to "Suono"
                ),
                selected = terminal.bellMode,
                onSelected = viewModel::setBellMode
            )
        }
        item {
            SwitchRow(
                title = "Mantieni schermo acceso",
                description = "Solo durante una sessione SSH attiva",
                checked = terminal.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn
            )
        }
    }
}
