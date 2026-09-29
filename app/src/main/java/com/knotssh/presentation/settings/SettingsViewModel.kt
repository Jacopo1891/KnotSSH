package com.knotssh.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.AppearanceSettings
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.ConnectionSettings
import com.knotssh.data.local.preferences.HostKeyPolicy
import com.knotssh.data.local.preferences.SecuritySettings
import com.knotssh.data.local.preferences.TerminalFont
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.data.local.preferences.ThemeMode
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.KeyModifier
import com.knotssh.domain.model.KnownHost
import com.knotssh.domain.model.QuickCommand
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.KnownHostRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.ssh.toControlChar
import com.knotssh.terminal.TerminalTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val knownHosts: KnownHostRepository,
    private val quickCommands: QuickCommandRepository,
    private val customKeys: CustomKeyRepository
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val appearance: StateFlow<AppearanceSettings> =
        prefs.appearance.stateIn(viewModelScope, started, AppearanceSettings())

    val terminal: StateFlow<TerminalSettings> =
        prefs.terminal.stateIn(viewModelScope, started, TerminalSettings())

    val connection: StateFlow<ConnectionSettings> =
        prefs.connection.stateIn(viewModelScope, started, ConnectionSettings())

    val security: StateFlow<SecuritySettings> =
        prefs.security.stateIn(viewModelScope, started, SecuritySettings())

    val googleDriveSync: StateFlow<Boolean> =
        prefs.googleDriveSync.stateIn(viewModelScope, started, false)

    val knownHostList: StateFlow<List<KnownHost>> =
        knownHosts.getAll().stateIn(viewModelScope, started, emptyList())

    val quickCommandList: StateFlow<List<QuickCommand>> =
        quickCommands.getAllCommands().stateIn(viewModelScope, started, emptyList())

    val customKeyList: StateFlow<List<CustomKey>> =
        customKeys.getAll().stateIn(viewModelScope, started, emptyList())

    // Appearance
    fun setThemeMode(value: ThemeMode) = update { prefs.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = update { prefs.setDynamicColor(value) }

    // Terminal
    fun setTerminalFontSize(value: Int) = update { prefs.setTerminalFontSize(value) }
    fun setTerminalFont(value: TerminalFont) = update { prefs.setTerminalFont(value) }
    fun setTerminalTheme(value: TerminalTheme) = update { prefs.setTerminalTheme(value) }
    fun setAnsiColors(value: Boolean) = update { prefs.setAnsiColorsEnabled(value) }
    fun setClickableUrls(value: Boolean) = update { prefs.setClickableUrls(value) }
    fun setScrollbackLines(value: Int) = update { prefs.setScrollbackLines(value) }
    fun setAutoSizePty(value: Boolean) = update { prefs.setAutoSizePty(value) }
    fun setFallbackColumns(value: Int) = update { prefs.setFallbackColumns(value) }
    fun setFallbackRows(value: Int) = update { prefs.setFallbackRows(value) }
    fun setCursorBlink(value: Boolean) = update { prefs.setCursorBlink(value) }
    fun setShowQuickCommandsBar(value: Boolean) = update { prefs.setShowQuickCommandsBar(value) }
    fun setShowAccessoryBar(value: Boolean) = update { prefs.setShowAccessoryBar(value) }
    fun setHapticFeedback(value: Boolean) = update { prefs.setHapticFeedback(value) }
    fun setBellMode(value: BellMode) = update { prefs.setBellMode(value) }
    fun setKeepScreenOn(value: Boolean) = update { prefs.setKeepScreenOn(value) }
    fun setAutoShowKeyboard(value: Boolean) = update { prefs.setAutoShowKeyboard(value) }

    // Connection
    fun setKeepAliveSeconds(value: Int) = update { prefs.setKeepAliveSeconds(value) }
    fun setConnectTimeoutSeconds(value: Int) = update { prefs.setConnectTimeoutSeconds(value) }
    fun setAutoReconnect(value: Boolean) = update { prefs.setAutoReconnect(value) }
    fun setAutoReconnectAttempts(value: Int) = update { prefs.setAutoReconnectAttempts(value) }
    fun setCompression(value: Boolean) = update { prefs.setCompression(value) }
    fun setWakeLock(value: Boolean) = update { prefs.setWakeLock(value) }
    fun setWakeLockMinutes(value: Int) = update { prefs.setWakeLockMinutes(value) }
    fun setForegroundNotification(value: Boolean) = update { prefs.setForegroundNotification(value) }
    fun setMaxSessions(value: Int) = update { prefs.setMaxSessions(value) }
    fun setCloseOnExit(value: Boolean) = update { prefs.setCloseOnExit(value) }
    fun setCloseOnExitSeconds(value: Int) = update { prefs.setCloseOnExitSeconds(value) }

    // Security
    fun setAllowScreenshot(value: Boolean) = update { prefs.setAllowScreenshot(value) }
    fun setBiometricEnabled(value: Boolean) = update { prefs.setBiometricEnabled(value) }
    fun setBiometricGraceSeconds(value: Int) = update { prefs.setBiometricGraceSeconds(value) }
    fun setHostKeyPolicy(value: HostKeyPolicy) = update { prefs.setHostKeyPolicy(value) }
    fun setMaskSecretsInUi(value: Boolean) = update { prefs.setMaskSecretsInUi(value) }
    fun setVerboseSshLogging(value: Boolean) = update { prefs.setVerboseSshLogging(value) }

    fun setGoogleDriveSync(value: Boolean) = update { prefs.setGoogleDriveSync(value) }

    fun forgetKnownHost(id: Long) = update { knownHosts.forget(id) }
    fun forgetAllKnownHosts() = update { knownHosts.forgetAll() }
    fun resetToDefaults() = update { prefs.resetToDefaults() }

    fun addQuickCommand(label: String, command: String) = update {
        quickCommands.insertCommand(
            QuickCommand(
                label = label.trim(),
                command = command.trim(),
                sortOrder = quickCommandList.value.size
            )
        )
    }

    fun deleteQuickCommand(id: Long) = update { quickCommands.deleteCommand(id) }

    fun addCustomKey(label: String, modifier: KeyModifier, base: String) = update {
        val sequence = buildKeySequence(modifier, base) ?: return@update
        customKeys.save(
            CustomKey(
                label = label.trim().ifBlank { defaultLabel(modifier, base) },
                sequence = sequence,
                sortOrder = customKeyList.value.size
            )
        )
    }

    fun deleteCustomKey(id: Long) = update { customKeys.delete(id) }

    fun restoreDefaultCustomKeys() = update { customKeys.restoreDefaults() }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Builds the byte sequence a key must emit, or null when [base] is not a single character. */
fun buildKeySequence(modifier: KeyModifier, base: String): String? {
    val character = base.singleOrNull() ?: return null
    val control = modifier == KeyModifier.CTRL || modifier == KeyModifier.CTRL_ALT
    val alt = modifier == KeyModifier.ALT || modifier == KeyModifier.CTRL_ALT
    return buildString {
        if (alt) append('\u001B')
        append(if (control) character.toControlChar() else character)
    }
}

fun defaultLabel(modifier: KeyModifier, base: String): String {
    val character = base.singleOrNull()?.uppercaseChar() ?: return base
    return when (modifier) {
        KeyModifier.NONE -> character.toString()
        KeyModifier.CTRL -> "^$character"
        KeyModifier.ALT -> "⎇$character"
        KeyModifier.CTRL_ALT -> "^⎇$character"
    }
}
