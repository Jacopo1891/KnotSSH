package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
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
    onOpenLanguage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHostList.collectAsStateWithLifecycle()
    val customKeys by viewModel.customKeyList.collectAsStateWithLifecycle()
    val quickCommands by viewModel.quickCommandList.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_appearance),
                summary = listOf(
                    appearance.themeMode.label(),
                    stringResource(terminal.theme.labelRes),
                    stringResource(R.string.summary_font, terminal.font.label(), terminal.fontSize)
                ).joinToString(SUMMARY_SEPARATOR),
                onClick = onOpenAppearance
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Terminal,
                title = stringResource(R.string.settings_terminal),
                summary = listOf(
                    pluralStringResource(
                        R.plurals.lines,
                        terminal.scrollbackLines,
                        terminal.scrollbackLines
                    ),
                    if (terminal.autoSizePty) {
                        stringResource(R.string.summary_auto_size)
                    } else {
                        stringResource(
                            R.string.summary_fixed_size,
                            terminal.fallbackColumns,
                            terminal.fallbackRows
                        )
                    },
                    terminal.bellMode.label()
                ).joinToString(SUMMARY_SEPARATOR),
                onClick = onOpenTerminal
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Keyboard,
                title = stringResource(R.string.settings_keyboard),
                summary = listOf(
                    if (terminal.showAccessoryBar) {
                        stringResource(R.string.summary_key_bar_on)
                    } else {
                        stringResource(R.string.summary_key_bar_off)
                    },
                    pluralStringResource(
                        R.plurals.custom_keys_count,
                        customKeys.size,
                        customKeys.size
                    ),
                    pluralStringResource(
                        R.plurals.commands_count,
                        quickCommands.size,
                        quickCommands.size
                    )
                ).joinToString(SUMMARY_SEPARATOR),
                onClick = onOpenKeyboard
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Wifi,
                title = stringResource(R.string.settings_connection),
                summary = listOf(
                    if (connection.keepAliveSeconds == 0) {
                        stringResource(R.string.summary_keepalive_off)
                    } else {
                        stringResource(R.string.summary_keepalive, connection.keepAliveSeconds)
                    },
                    if (connection.autoReconnect) {
                        stringResource(R.string.summary_reconnect_on)
                    } else {
                        stringResource(R.string.summary_reconnect_off)
                    },
                    if (connection.compression) {
                        stringResource(R.string.summary_compression_on)
                    } else {
                        stringResource(R.string.summary_compression_off)
                    }
                ).joinToString(SUMMARY_SEPARATOR),
                onClick = onOpenConnection
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_security),
                summary = listOf(
                    security.hostKeyPolicy.label(),
                    pluralStringResource(
                        R.plurals.known_hosts_count,
                        knownHosts.size,
                        knownHosts.size
                    ),
                    if (security.biometricEnabled) {
                        stringResource(R.string.summary_biometric_on)
                    } else {
                        stringResource(R.string.summary_biometric_off)
                    }
                ).joinToString(SUMMARY_SEPARATOR),
                onClick = onOpenSecurity
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                summary = LocaleManager.current().let {
                    if (it == AppLanguage.SYSTEM) {
                        stringResource(R.string.language_system)
                    } else {
                        it.displayName
                    }
                },
                onClick = onOpenLanguage
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.SettingsBackupRestore,
                title = stringResource(R.string.settings_backup),
                summary = stringResource(R.string.settings_backup_summary),
                onClick = onOpenBackup
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.settings_faq),
                summary = stringResource(R.string.settings_faq_summary),
                onClick = onOpenFaq
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                onClick = onOpenAbout
            )
        }
    }
}

private const val SUMMARY_SEPARATOR = " · "

@Composable
private fun ThemeMode.label() = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
    }
)

@Composable
private fun TerminalFont.label() = stringResource(
    when (this) {
        TerminalFont.MONOSPACE -> R.string.font_mono
        TerminalFont.SANS_SERIF -> R.string.font_sans
        TerminalFont.SERIF -> R.string.font_serif
    }
)

@Composable
private fun BellMode.label() = stringResource(
    when (this) {
        BellMode.OFF -> R.string.summary_bell_off
        BellMode.VIBRATE -> R.string.summary_bell_vibrate
        BellMode.SOUND -> R.string.summary_bell_sound
    }
)

@Composable
private fun HostKeyPolicy.label() = stringResource(
    when (this) {
        HostKeyPolicy.STRICT -> R.string.summary_hostkey_strict
        HostKeyPolicy.PROMPT -> R.string.summary_hostkey_prompt
        HostKeyPolicy.TRUST_ON_FIRST_USE -> R.string.summary_hostkey_tofu
    }
)
