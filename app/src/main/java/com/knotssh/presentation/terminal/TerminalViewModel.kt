package com.knotssh.presentation.terminal

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.R
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.ConnectionSettings
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.QuickCommand
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.ssh.SessionLimitReached
import com.knotssh.ssh.SessionRegistry
import com.knotssh.ssh.SecretPrompt
import com.knotssh.ssh.SshSession
import com.knotssh.ssh.SshSessionService
import com.knotssh.ssh.TerminalStatus
import com.knotssh.terminal.TerminalSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin adapter over an [SshSession] owned by [SessionRegistry].
 *
 * The session deliberately outlives this ViewModel: leaving the screen must not disconnect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SessionRegistry,
    quickCommandRepository: QuickCommandRepository,
    private val customKeyRepository: CustomKeyRepository,
    appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val serverId: Long = checkNotNull(savedStateHandle["serverId"])

    private val _session = MutableStateFlow<SshSession?>(null)

    private val defaultTitle = context.getString(R.string.terminal_default_title)

    private val _limitReached = MutableStateFlow<Int?>(null)
    val limitReached: StateFlow<Int?> = _limitReached.asStateFlow()

    val terminalSettings: StateFlow<TerminalSettings> =
        appPreferences.terminal.stateIn(viewModelScope, SharingStarted.Eagerly, TerminalSettings())

    val connectionSettings: StateFlow<ConnectionSettings> =
        appPreferences.connection.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionSettings())

    /** A suspended session needs the foreground service, so the two share one switch. */
    val backgroundSessionsEnabled: StateFlow<Boolean> = connectionSettings
        .map { it.foregroundNotification }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val quickCommands: StateFlow<List<QuickCommand>> = quickCommandRepository.getAllCommands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customKeys: StateFlow<List<CustomKey>> = customKeyRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val snapshot: StateFlow<TerminalSnapshot> = _session
        .flatMapLatest { it?.snapshot ?: flowOf(TerminalSnapshot.EMPTY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), TerminalSnapshot.EMPTY)

    val status: StateFlow<TerminalStatus> = _session
        .flatMapLatest { it?.status ?: flowOf(TerminalStatus.Connecting) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TerminalStatus.Connecting)

    val title: StateFlow<String> = _session
        .flatMapLatest { it?.title ?: flowOf(defaultTitle) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultTitle)

    val hostKeyPrompt: StateFlow<HostKeyVerdict?> = _session
        .flatMapLatest { it?.hostKeyPrompt ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ctrlArmed: StateFlow<Boolean> = _session
        .flatMapLatest { it?.ctrlArmed ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val altArmed: StateFlow<Boolean> = _session
        .flatMapLatest { it?.altArmed ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val secretPrompt: StateFlow<SecretPrompt?> = _session
        .flatMapLatest { it?.secretPrompt ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val session: SshSession? get() = _session.value

    init {
        viewModelScope.launch {
            customKeyRepository.seedDefaultsIfEmpty()
            registry.open(serverId, registry.sessionLimit())
                .onSuccess {
                    _session.value = it
                    SshSessionService.sync(context)
                }
                .onFailure { error ->
                    _limitReached.value = (error as? SessionLimitReached)?.limit
                }
        }
    }

    fun onViewportMeasured(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        session?.onViewportMeasured(columns, rows, widthPx, heightPx)
    }

    fun reconnect() {
        session?.reconnect()
    }

    fun sendText(text: String) {
        session?.sendText(text)
    }

    fun sendSequence(sequence: String) {
        session?.sendSequence(sequence)
    }

    fun sendCommand(command: String) {
        session?.sendCommand(command)
    }

    fun sendArrow(direction: Char) {
        session?.sendArrow(direction)
    }

    fun sendHomeEnd(end: Boolean) {
        session?.sendHomeEnd(end)
    }

    fun paste(text: String) {
        session?.paste(text)
    }

    fun toggleCtrl() {
        session?.toggleCtrl()
    }

    fun toggleAlt() {
        session?.toggleAlt()
    }

    fun clearScrollback() {
        session?.clearScrollback()
    }

    fun resetTerminal() {
        session?.resetTerminal()
    }

    fun bufferAsText(): String = session?.bufferAsText().orEmpty()

    fun resolveHostKeyPrompt(accepted: Boolean) {
        session?.resolveHostKeyPrompt(accepted)
    }

    fun resolveSecretPrompt(secret: String?) {
        session?.resolveSecretPrompt(secret)
    }

    /** Ends the session for good. Leaving the screen without calling this keeps it running. */
    fun terminate() {
        registry.close(serverId)
        SshSessionService.sync(context)
    }

    /** Leaves the session running in the background and refreshes its notification. */
    fun detach() {
        if (!backgroundSessionsEnabled.value) {
            terminate()
            return
        }
        SshSessionService.sync(context)
    }
}
