package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.HostKeyPolicy
import com.knotssh.data.local.preferences.TerminalFont
import com.knotssh.data.local.preferences.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHostList.collectAsStateWithLifecycle()
    val customKeys by viewModel.customKeyList.collectAsStateWithLifecycle()
    val quickCommands by viewModel.quickCommandList.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Impostazioni", onBack = onBack) {
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Palette,
                title = "Aspetto",
                summary = listOf(
                    appearance.themeMode.label(),
                    terminal.theme.displayName,
                    "${terminal.font.label()} ${terminal.fontSize}sp"
                ).joinToString(" · "),
                onClick = onOpenAppearance
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Terminal,
                title = "Terminale",
                summary = listOf(
                    "${terminal.scrollbackLines} righe",
                    if (terminal.autoSizePty) "auto-dimensione"
                    else "${terminal.fallbackColumns}×${terminal.fallbackRows}",
                    terminal.bellMode.label()
                ).joinToString(" · "),
                onClick = onOpenTerminal
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Keyboard,
                title = "Tastiera",
                summary = listOf(
                    if (terminal.showAccessoryBar) "barra tasti attiva" else "barra tasti nascosta",
                    "${customKeys.size} tasti rapidi",
                    "${quickCommands.size} comandi"
                ).joinToString(" · "),
                onClick = onOpenKeyboard
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Wifi,
                title = "Connessione",
                summary = listOf(
                    if (connection.keepAliveSeconds == 0) "keep-alive off"
                    else "keep-alive ${connection.keepAliveSeconds}s",
                    if (connection.autoReconnect) "riconnessione automatica" else "nessuna riconnessione",
                    if (connection.compression) "compressa" else "non compressa"
                ).joinToString(" · "),
                onClick = onOpenConnection
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Lock,
                title = "Sicurezza",
                summary = listOf(
                    security.hostKeyPolicy.label(),
                    "${knownHosts.size} host noti",
                    if (security.biometricEnabled) "sblocco biometrico" else "nessuno sblocco"
                ).joinToString(" · "),
                onClick = onOpenSecurity
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.SettingsBackupRestore,
                title = "Backup e ripristino",
                summary = "Sincronizzazione cloud e valori predefiniti",
                onClick = onOpenBackup
            )
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "Sistema"
    ThemeMode.LIGHT -> "Chiaro"
    ThemeMode.DARK -> "Scuro"
}

private fun TerminalFont.label() = when (this) {
    TerminalFont.MONOSPACE -> "Mono"
    TerminalFont.SANS_SERIF -> "Sans"
    TerminalFont.SERIF -> "Serif"
}

private fun BellMode.label() = when (this) {
    BellMode.OFF -> "campanella off"
    BellMode.VIBRATE -> "campanella vibra"
    BellMode.SOUND -> "campanella sonora"
}

private fun HostKeyPolicy.label() = when (this) {
    HostKeyPolicy.STRICT -> "verifica rigida"
    HostKeyPolicy.PROMPT -> "chiede conferma"
    HostKeyPolicy.TRUST_ON_FIRST_USE -> "TOFU"
    HostKeyPolicy.ACCEPT_ANY -> "⚠ nessuna verifica"
}
