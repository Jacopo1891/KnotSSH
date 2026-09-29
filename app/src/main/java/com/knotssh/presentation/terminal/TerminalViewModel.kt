package com.knotssh.presentation.terminal

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.ConnectionSettings
import com.knotssh.data.local.preferences.SecuritySettings
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.QuickCommand
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.domain.repository.ServerRepository
import com.knotssh.ssh.HostKeyException
import com.knotssh.ssh.SshConnection
import com.knotssh.ssh.SshManager
import com.knotssh.ssh.SshSessionService
import com.knotssh.terminal.TerminalEmulator
import com.knotssh.terminal.TerminalSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject

sealed interface TerminalStatus {
    data object Connecting : TerminalStatus
    data object Connected : TerminalStatus
    data class Reconnecting(val attempt: Int, val of: Int) : TerminalStatus
    data class Disconnected(val reason: String?) : TerminalStatus
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    quickCommandRepository: QuickCommandRepository,
    private val customKeyRepository: CustomKeyRepository,
    private val appPreferences: AppPreferences,
    private val sshManager: SshManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Long = checkNotNull(savedStateHandle["serverId"])

    val terminalSettings: StateFlow<TerminalSettings> =
        appPreferences.terminal.stateIn(viewModelScope, SharingStarted.Eagerly, TerminalSettings())

    val quickCommands: StateFlow<List<QuickCommand>> = quickCommandRepository.getAllCommands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customKeys: StateFlow<List<CustomKey>> = customKeyRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snapshot = MutableStateFlow(TerminalSnapshot.EMPTY)
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<TerminalStatus>(TerminalStatus.Connecting)
    val status: StateFlow<TerminalStatus> = _status.asStateFlow()

    private val _title = MutableStateFlow("Terminale SSH")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _hostKeyPrompt = MutableStateFlow<HostKeyVerdict?>(null)
    val hostKeyPrompt: StateFlow<HostKeyVerdict?> = _hostKeyPrompt.asStateFlow()

    private val _ctrlArmed = MutableStateFlow(false)
    val ctrlArmed: StateFlow<Boolean> = _ctrlArmed.asStateFlow()

    private val _altArmed = MutableStateFlow(false)
    val altArmed: StateFlow<Boolean> = _altArmed.asStateFlow()

    /**
     * Guards the emulator, which is mutated by the reader and read by the render ticker.
     *
     * Intentionally a plain monitor rather than a coroutine `Mutex`: suspending inside the read
     * loop would let the reader resume on a different pooled thread, which is exactly what the
     * JSch pipe cannot tolerate (see [readerDispatcher]). Critical sections are microseconds.
     */
    private val emulatorLock = Any()

    /**
     * JSch hands channel data to a [java.io.PipedInputStream] from its own session thread, and
     * `PipedInputStream.receive()` throws "Read end dead" as soon as the thread that last read
     * has terminated — JSch answers that by tearing the channel down. A pooled dispatcher
     * recycles its threads between reads, so the reader gets dedicated ones that stay alive for
     * the whole ViewModel. Two slots so a reconnect is not blocked behind a reader that is still
     * unwinding from the previous session.
     */
    private val readerDispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "KnotSSH-reader").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val emulator = TerminalEmulator(
        columns = TerminalSettings().fallbackColumns,
        rows = TerminalSettings().fallbackRows,
        scrollbackLimit = TerminalSettings().scrollbackLines,
        onResponse = { bytes -> sendBytes(bytes) },
        onBell = { ringBell() },
        onTitle = { if (it.isNotBlank()) _title.value = it }
    )

    private var connection: SshConnection? = null
    private var connectJob: Job? = null
    private var readerJob: Job? = null
    private var resizeJob: Job? = null
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null
    private var userInitiatedDisconnect = false
    private var measuredColumns = TerminalSettings().fallbackColumns
    private var measuredRows = TerminalSettings().fallbackRows
    private var sessionStartedAt = 0L
    private var consecutiveQuickDrops = 0

    /**
     * Incremented for every connection attempt. Tearing down a session unblocks its reader with
     * an IO error; without this guard that stale reader would report a drop for a session the
     * user has already replaced, and trigger a spurious reconnect.
     */
    private var sessionGeneration = 0

    init {
        viewModelScope.launch { customKeyRepository.seedDefaultsIfEmpty() }
        startRenderTicker()
        observeScrollbackSetting()
        connect()
    }

    // --- Rendering ---------------------------------------------------------------------------

    /**
     * Decouples throughput from the UI: the emulator absorbs output at full speed while the
     * screen refreshes at a fixed cadence, which is what keeps `cat` on a large file responsive.
     */
    private fun startRenderTicker() {
        viewModelScope.launch(Dispatchers.Default) {
            var lastRevision = -1L
            while (isActive) {
                val fresh = synchronized(emulatorLock) {
                    // Probing the revision first avoids rebuilding the buffer on idle frames.
                    if (emulator.currentRevision == lastRevision) null else emulator.snapshot()
                }
                if (fresh != null) {
                    lastRevision = fresh.revision
                    _snapshot.value = fresh
                }
                delay(RENDER_INTERVAL_MS)
            }
        }
    }

    private fun observeScrollbackSetting() {
        viewModelScope.launch {
            var last = -1
            terminalSettings.collect { settings ->
                if (settings.scrollbackLines != last) {
                    last = settings.scrollbackLines
                    synchronized(emulatorLock) { emulator.setScrollbackLimit(settings.scrollbackLines) }
                }
            }
        }
    }

    /** Called by the UI once it knows how many cells fit on screen. */
    fun onViewportMeasured(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        val settings = terminalSettings.value
        val targetColumns = if (settings.autoSizePty) columns else settings.fallbackColumns
        val targetRows = if (settings.autoSizePty) rows else settings.fallbackRows
        if (targetColumns == measuredColumns && targetRows == measuredRows) return

        measuredColumns = targetColumns
        measuredRows = targetRows
        resizeJob?.cancel()
        // setPtySize writes to the socket and can block: never on the main thread.
        resizeJob = viewModelScope.launch(Dispatchers.IO) {
            // The keyboard animates open, so the viewport reports a stream of intermediate
            // sizes; only the final one deserves a window-change and a shell redraw.
            delay(RESIZE_DEBOUNCE_MS)
            synchronized(emulatorLock) { emulator.resize(targetColumns, targetRows) }
            sshManager.resizePty(targetColumns, targetRows, widthPx, heightPx)
        }
    }

    // --- Connection --------------------------------------------------------------------------

    fun reconnect() {
        userInitiatedDisconnect = false
        consecutiveQuickDrops = 0
        connect()
    }

    private fun connect() {
        readerJob?.cancel()
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val connectionSettings = appPreferences.connection.first()
            val security = appPreferences.security.first()
            var attempt = 0

            while (true) {
                _status.value =
                    if (attempt == 0) TerminalStatus.Connecting
                    else TerminalStatus.Reconnecting(attempt, connectionSettings.autoReconnectAttempts)

                val failure = attemptConnect(connectionSettings, security)
                if (failure == null) return@launch

                // A host key problem is never retried: retrying would only re-expose credentials.
                val retryable = failure !is HostKeyException &&
                        connectionSettings.autoReconnect &&
                        !userInitiatedDisconnect &&
                        attempt < connectionSettings.autoReconnectAttempts

                if (!retryable) {
                    _status.value = TerminalStatus.Disconnected(failure.message)
                    if (connectionSettings.foregroundNotification) {
                        SshSessionService.updateStatus(context, isConnected = false)
                    }
                    return@launch
                }
                attempt++
                delay(RECONNECT_BASE_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun attemptConnect(
        connectionSettings: ConnectionSettings,
        security: SecuritySettings
    ): Exception? {
        // Claimed before the handshake: tearing down the previous session unblocks its reader,
        // which must already see itself as stale by then.
        val generation = ++sessionGeneration
        return try {
            val server = serverRepository.getServerById(serverId)
                ?: return IllegalStateException("Server non trovato")
            _title.value = server.alias

            val credential = credentialRepository.getCredentialById(server.credentialId)
                ?: return IllegalStateException("Credenziale associata non trovata")

            val secret = credentialRepository.decryptSecret(credential)
            val passphrase = credentialRepository.decryptPassphrase(credential)

            synchronized(emulatorLock) { emulator.beginSession() }
            writeLocal("\u001B[36m[Connessione a ${server.alias} (${server.hostname}:${server.port})…]\u001B[0m\r\n")

            if (connectionSettings.foregroundNotification) {
                SshSessionService.startService(context, serverId, server.alias)
            }

            val established = sshManager.connect(
                context = context,
                server = server,
                credential = credential,
                plainSecret = secret,
                passphrase = passphrase,
                settings = connectionSettings,
                hostKeyPolicy = security.hostKeyPolicy,
                verboseLogging = security.verboseSshLogging,
                columns = measuredColumns,
                rows = measuredRows,
                onHostKeyPrompt = ::requestHostKeyDecision
            )

            connection = established
            sessionStartedAt = System.currentTimeMillis()
            _status.value = TerminalStatus.Connected
            serverRepository.updateLastConnected(server.id, System.currentTimeMillis())

            sshManager.warnings.forEach {
                writeLocal("\r\n\u001B[33m[$it]\u001B[0m\r\n")
            }

            startReader(established, generation)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            writeLocal("\r\n\u001B[31m[${e.message ?: "Errore di connessione"}]\u001B[0m\r\n")
            if (connectionSettings.foregroundNotification) {
                SshSessionService.updateStatus(context, isConnected = false)
            }
            e
        }
    }

    private fun startReader(established: SshConnection, generation: Int) {
        readerJob = viewModelScope.launch(readerDispatcher) {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (isActive) {
                    // Nothing may be held while touching the pipe: JSch's session thread needs
                    // it to hand over data.
                    val read = established.input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    synchronized(emulatorLock) { emulator.write(buffer, read) }
                }
                onStreamEnded(null, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStreamEnded(e, generation)
            }
        }
    }

    private suspend fun onStreamEnded(cause: Throwable?, generation: Int) {
        if (userInitiatedDisconnect || generation != sessionGeneration) return

        val exitStatus = sshManager.exitStatus()
        val liveness = sshManager.livenessReport()
        Log.w(
            TAG,
            "Session $generation ended: cause=${cause?.javaClass?.simpleName}:${cause?.message} " +
                    "exitStatus=$exitStatus $liveness thread=${Thread.currentThread().name}",
            cause
        )

        // A clean EOF carries no exception; the shell's exit code and the transport state are
        // then the only clues about who hung up.
        val detail = cause?.let { "${it.javaClass.simpleName}: ${it.message}" }
            ?: exitStatus.takeIf { it >= 0 }?.let { "shell terminata (codice $it)" }
            ?: liveness
        writeLocal("\r\n\u001B[31m[Connessione terminata${detail?.let { ": $it" } ?: ""}]\u001B[0m\r\n")

        val settings = appPreferences.connection.first()
        if (settings.foregroundNotification) {
            SshSessionService.updateStatus(context, isConnected = false)
        }

        // A session that dies immediately is a server-side refusal, not a flaky network, so
        // retrying in a tight loop would only hammer the host.
        val lasted = System.currentTimeMillis() - sessionStartedAt
        consecutiveQuickDrops = if (lasted < STABLE_SESSION_MS) consecutiveQuickDrops + 1 else 0

        withContext(Dispatchers.Main) {
            _status.value = TerminalStatus.Disconnected(detail)
            if (settings.autoReconnect && consecutiveQuickDrops <= settings.autoReconnectAttempts) {
                connect()
            }
        }
    }

    // --- Host key ----------------------------------------------------------------------------

    private suspend fun requestHostKeyDecision(verdict: HostKeyVerdict): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        hostKeyDecision = deferred
        _hostKeyPrompt.value = verdict
        return try {
            deferred.await()
        } finally {
            _hostKeyPrompt.value = null
            hostKeyDecision = null
        }
    }

    fun resolveHostKeyPrompt(accepted: Boolean) {
        hostKeyDecision?.complete(accepted)
    }

    // --- Input -------------------------------------------------------------------------------

    /**
     * Sends [text], applying any armed sticky modifier to its first character and then
     * disarming it, the way a physical Ctrl or Alt key behaves.
     */
    fun sendText(text: String) {
        if (text.isEmpty()) return
        val ctrl = _ctrlArmed.value
        val alt = _altArmed.value
        if (!ctrl && !alt) {
            sendBytes(text.toByteArray(Charsets.UTF_8))
            return
        }

        val payload = buildString {
            if (alt) append(ESCAPE)
            append(if (ctrl) text[0].toControlChar() else text[0])
            append(text, 1, text.length)
        }
        disarmModifiers()
        sendBytes(payload.toByteArray(Charsets.UTF_8))
    }

    /** Sends a key sequence verbatim: escape sequences must not be re-encoded by modifiers. */
    fun sendSequence(sequence: String) {
        if (sequence.isNotEmpty()) sendBytes(sequence.toByteArray(Charsets.UTF_8))
    }

    /**
     * Pastes clipboard text. Line breaks become CR because that is what a shell expects from
     * Enter, and the payload is bracketed when the remote application asked for it so editors
     * can tell a paste from typing and skip auto-indent.
     */
    fun paste(text: String) {
        if (text.isEmpty()) return
        val normalised = text.replace("\r\n", "\r").replace('\n', '\r')
        val payload = if (emulator.bracketedPaste) {
            "\u001B[200~$normalised\u001B[201~"
        } else {
            normalised
        }
        sendSequence(payload)
    }

    fun sendCommand(command: String) = sendSequence(command + "\r")

    /**
     * Arrow keys switch between CSI and SS3 form depending on DECCKM, and take the xterm
     * modifier parameter when Ctrl or Alt is armed.
     */
    fun sendArrow(direction: Char) {
        val ctrl = _ctrlArmed.value
        val alt = _altArmed.value
        if (ctrl || alt) {
            val modifier = 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
            disarmModifiers()
            sendSequence("\u001B[1;$modifier$direction")
            return
        }
        val prefix = if (emulator.applicationCursorKeys) "\u001BO" else "\u001B["
        sendSequence(prefix + direction)
    }

    /** Home and End follow DECCKM exactly like the arrows do. */
    fun sendHomeEnd(end: Boolean) {
        val prefix = if (emulator.applicationCursorKeys) "\u001BO" else "\u001B["
        sendSequence(prefix + if (end) "F" else "H")
    }

    fun toggleCtrl() {
        _ctrlArmed.value = !_ctrlArmed.value
    }

    fun toggleAlt() {
        _altArmed.value = !_altArmed.value
    }

    private fun disarmModifiers() {
        _ctrlArmed.value = false
        _altArmed.value = false
    }

    private fun sendBytes(bytes: ByteArray) {
        sshManager.send(bytes) { error ->
            writeLocal("\r\n\u001B[31m[Invio fallito: ${error.message}]\u001B[0m\r\n")
        }
    }

    fun clearScrollback() {
        synchronized(emulatorLock) { emulator.clearScrollback() }
    }

    fun resetTerminal() {
        synchronized(emulatorLock) { emulator.reset() }
    }

    fun bufferAsText(): String = synchronized(emulatorLock) { emulator.plainText() }

    private fun writeLocal(text: String) {
        synchronized(emulatorLock) { emulator.writeLocal(text) }
    }

    // --- Misc --------------------------------------------------------------------------------

    private fun ringBell() {
        when (terminalSettings.value.bellMode) {
            BellMode.OFF -> Unit
            BellMode.VIBRATE -> vibrator()?.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
            BellMode.SOUND -> runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                    .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            }
        }
    }

    private fun vibrator(): Vibrator? = runCatching {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    }.getOrNull()

    fun disconnect() {
        userInitiatedDisconnect = true
        sessionGeneration++
        hostKeyDecision?.complete(false)
        connectJob?.cancel()
        connectJob = null
        readerJob?.cancel()
        readerJob = null
        sshManager.disconnect()
        connection = null
        _status.value = TerminalStatus.Disconnected(null)
        SshSessionService.stopService(context)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        readerDispatcher.close()
    }

    private companion object {
        const val TAG = "KnotSSH_Terminal"
        const val ESCAPE = '\u001B'
        const val READ_BUFFER_SIZE = 8192
        const val RENDER_INTERVAL_MS = 16L
        const val RESIZE_DEBOUNCE_MS = 150L
        const val RECONNECT_BASE_DELAY_MS = 1_500L
        const val STABLE_SESSION_MS = 5_000L
    }
}

/**
 * Maps a character to the control code a terminal expects for Ctrl+<key>: `@` through `_`
 * collapse to 0x00-0x1F, with the two conventional aliases for DEL and NUL.
 */
fun Char.toControlChar(): Char = when (val upper = uppercaseChar()) {
    in '@'..'_' -> (upper.code - 0x40).toChar()
    '?' -> '\u007F'
    ' ' -> '\u0000'
    else -> this
}
