package com.knotssh.ssh

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.knotssh.R
import com.knotssh.data.diagnostics.DiagnosticsLog
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.BellMode
import com.knotssh.data.local.preferences.ConnectionSettings
import com.knotssh.data.local.preferences.SecuritySettings
import com.knotssh.data.local.preferences.TerminalSettings
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.ServerRepository
import com.knotssh.terminal.TerminalEmulator
import com.knotssh.terminal.TerminalSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

sealed interface TerminalStatus {
    data object Connecting : TerminalStatus
    data object Connected : TerminalStatus
    data class Reconnecting(val attempt: Int, val of: Int) : TerminalStatus
    data class Disconnected(val reason: String?) : TerminalStatus

    /** The remote shell exited on its own, e.g. the user typed `exit`. Never auto-reconnected. */
    data class Ended(val exitStatus: Int) : TerminalStatus
}

/** Asks the user for a secret the credential deliberately does not store. */
data class SecretPrompt(val username: String, val passphrase: Boolean)

/** Dismissing the secret prompt is a deliberate abort, so it must not trigger a retry. */
class AuthCancelledException(message: String) : Exception(message)

/**
 * One live SSH session, owned by [SessionRegistry] rather than by a screen.
 *
 * Keeping the connection, the emulator and the reader here is what lets a session survive
 * navigating away: the terminal screen only attaches to it and detaches again.
 */
class SshSession(
    val serverId: Long,
    private val context: Context,
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    private val appPreferences: AppPreferences,
    private val sshManager: SshManager,
    private val diagnostics: DiagnosticsLog,
    parentScope: CoroutineScope,
    private val onFinished: (Long) -> Unit
) {
    /**
     * Child of the application scope: every coroutine this session starts — reader, settings
     * collectors, snapshot sharing — dies with [close] instead of outliving the connection.
     */
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

    private val _status = MutableStateFlow<TerminalStatus>(TerminalStatus.Connecting)
    val status: StateFlow<TerminalStatus> = _status.asStateFlow()

    private val _title = MutableStateFlow(context.getString(R.string.terminal_default_title))
    val title: StateFlow<String> = _title.asStateFlow()

    private val _hostKeyPrompt = MutableStateFlow<HostKeyVerdict?>(null)
    val hostKeyPrompt: StateFlow<HostKeyVerdict?> = _hostKeyPrompt.asStateFlow()

    private val _secretPrompt = MutableStateFlow<SecretPrompt?>(null)
    val secretPrompt: StateFlow<SecretPrompt?> = _secretPrompt.asStateFlow()

    private val _ctrlArmed = MutableStateFlow(false)
    val ctrlArmed: StateFlow<Boolean> = _ctrlArmed.asStateFlow()

    private val _altArmed = MutableStateFlow(false)
    val altArmed: StateFlow<Boolean> = _altArmed.asStateFlow()

    /** `user@alias`, used to label the session in notifications. */
    @Volatile
    var label: String = ""
        private set

    /**
     * Guards the emulator, which is mutated by the reader and read while snapshotting.
     *
     * Intentionally a plain monitor rather than a coroutine `Mutex`: suspending inside the read
     * loop would let the reader resume on a different pooled thread, which is exactly what the
     * JSch pipe cannot tolerate (see [readerDispatcher]).
     */
    private val emulatorLock = Any()

    /**
     * JSch hands channel data to a [java.io.PipedInputStream] from its own session thread, and
     * `PipedInputStream.receive()` throws "Read end dead" as soon as the thread that last read
     * has terminated — JSch answers that by tearing the channel down. A pooled dispatcher
     * recycles its threads between reads, so the reader needs dedicated ones. Two slots so a
     * reconnect is not blocked behind a reader still unwinding from the previous attempt.
     */
    private val readerDispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "KnotSSH-reader-$serverId").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val emulator = TerminalEmulator(
        columns = TerminalSettings().fallbackColumns,
        rows = TerminalSettings().fallbackRows,
        scrollbackLimit = TerminalSettings().scrollbackLines,
        onResponse = { bytes -> sendBytes(bytes) },
        onBell = { ringBell() },
        onTitle = { if (it.isNotBlank()) _title.value = it }
    )

    /**
     * Polling stops shortly after the terminal screen detaches, so sessions parked in the
     * background cost nothing to render while their reader keeps draining the channel.
     */
    val snapshot: StateFlow<TerminalSnapshot> = flow {
        var lastRevision = -1L
        while (true) {
            val fresh = synchronized(emulatorLock) {
                if (emulator.currentRevision == lastRevision) null else emulator.snapshot()
            }
            if (fresh != null) {
                lastRevision = fresh.revision
                emit(fresh)
            }
            delay(RENDER_INTERVAL_MS)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(1_000), TerminalSnapshot.EMPTY)

    private var connection: SshConnection? = null
    private var connectJob: Job? = null
    private var readerJob: Job? = null
    private var resizeJob: Job? = null
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null
    private var secretDecision: CompletableDeferred<String?>? = null

    /** Typed secret for a prompt-every-time credential; in memory only, never persisted. */
    @Volatile
    private var promptedSecret: String? = null
    private var closedByUser = false
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
        observeScrollbackSetting()
        connect()
    }

    val isLive: Boolean
        get() = _status.value.let { it is TerminalStatus.Connected || it is TerminalStatus.Connecting || it is TerminalStatus.Reconnecting }

    private fun observeScrollbackSetting() {
        scope.launch {
            var last = -1
            appPreferences.terminal.collect { settings ->
                if (settings.scrollbackLines != last) {
                    last = settings.scrollbackLines
                    synchronized(emulatorLock) { emulator.setScrollbackLimit(settings.scrollbackLines) }
                }
            }
        }
    }

    fun onViewportMeasured(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        scope.launch {
            val settings = appPreferences.terminal.first()
            val targetColumns = if (settings.autoSizePty) columns else settings.fallbackColumns
            val targetRows = if (settings.autoSizePty) rows else settings.fallbackRows
            if (targetColumns == measuredColumns && targetRows == measuredRows) return@launch

            measuredColumns = targetColumns
            measuredRows = targetRows
            resizeJob?.cancel()
            // setPtySize writes to the socket and can block: never on the main thread.
            resizeJob = scope.launch(Dispatchers.IO) {
                // The keyboard animates open, so the viewport reports a stream of intermediate
                // sizes; only the final one deserves a window-change and a shell redraw.
                delay(RESIZE_DEBOUNCE_MS)
                synchronized(emulatorLock) { emulator.resize(targetColumns, targetRows) }
                sshManager.resizePty(targetColumns, targetRows, widthPx, heightPx)
            }
        }
    }

    // --- Connection --------------------------------------------------------------------------

    fun reconnect() {
        closedByUser = false
        consecutiveQuickDrops = 0
        connect()
    }

    private fun connect() {
        readerJob?.cancel()
        connectJob?.cancel()
        connectJob = scope.launch {
            val connectionSettings = appPreferences.connection.first()
            val security = appPreferences.security.first()
            var attempt = 0

            while (true) {
                _status.value =
                    if (attempt == 0) TerminalStatus.Connecting
                    else TerminalStatus.Reconnecting(attempt, connectionSettings.autoReconnectAttempts)
                diagnostics.verbose(
                    "ssh",
                    "server#$serverId connect attempt $attempt " +
                        "(timeout=${connectionSettings.connectTimeoutSeconds}s, " +
                        "keepAlive=${connectionSettings.keepAliveSeconds}s, " +
                        "compression=${connectionSettings.compression})"
                )

                val failure = attemptConnect(connectionSettings, security)
                if (failure == null) return@launch

                // A host key problem is never retried: retrying would only re-expose credentials.
                // Neither is a rejected secret: it cannot fix itself, and hammering the server
                // trips fail2ban or locks the account.
                val retryable = failure !is HostKeyException &&
                        failure !is AuthFailedException &&
                        failure !is AuthCancelledException &&
                        connectionSettings.autoReconnect &&
                        !closedByUser &&
                        attempt < connectionSettings.autoReconnectAttempts

                if (!retryable) {
                    _status.value = TerminalStatus.Disconnected(failure.message)
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
                ?: return IllegalStateException(context.getString(R.string.ssh_server_not_found))
            _title.value = server.alias

            val credential = credentialRepository.getCredentialById(server.credentialId)
                ?: return IllegalStateException(context.getString(R.string.ssh_credential_not_found))
            label = "${credential.username}@${server.alias}"

            var secret = credentialRepository.decryptSecret(credential)
            var passphrase = credentialRepository.decryptPassphrase(credential)
            var typed: String? = null

            if (credential.askEachTime) {
                typed = promptedSecret ?: requestSecret(credential)
                    ?: return AuthCancelledException(
                        context.getString(R.string.ssh_auth_cancelled)
                    )
                if (credential.authType == AuthType.PASSWORD) secret = typed else passphrase = typed
            }

            synchronized(emulatorLock) { emulator.beginSession() }
            writeLocal(
                "\u001B[36m[" +
                    context.getString(
                        R.string.ssh_connecting_to,
                        server.alias,
                        server.hostname,
                        server.port
                    ) +
                    "]\u001B[0m\r\n"
            )

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
            diagnostics.info(
                "ssh",
                "server#$serverId connected (auth=${credential.authType}, " +
                    "policy=${security.hostKeyPolicy})"
            )
            // Held only now that it is known to work: a wrong password must prompt again, a
            // dropped connection must not.
            promptedSecret = typed
            serverRepository.updateLastConnected(server.id, System.currentTimeMillis())

            sshManager.warnings.forEach {
                writeLocal("\r\n\u001B[33m[$it]\u001B[0m\r\n")
            }

            startReader(established, generation)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The handshake failed, so a typed secret may simply be wrong: ask again next time.
            promptedSecret = null
            diagnostics.error("ssh", "server#$serverId connection failed", e)
            val reason = e.message ?: context.getString(R.string.ssh_connection_error)
            writeLocal("\r\n\u001B[31m[$reason]\u001B[0m\r\n")
            e
        }
    }

    private fun startReader(established: SshConnection, generation: Int) {
        readerJob = scope.launch(readerDispatcher) {
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
        if (closedByUser || generation != sessionGeneration) return

        val exitStatus = awaitExitStatus()
        val liveness = sshManager.livenessReport()
        Log.w(
            TAG,
            "Session $serverId/$generation ended: cause=${cause?.javaClass?.simpleName}:${cause?.message} " +
                    "exitStatus=$exitStatus $liveness",
            cause
        )

        // An exit status only arrives when the remote command terminated by itself, typically
        // because the user typed `exit`. A dropped link never reports one, so this is what
        // separates a deliberate logout from a failure worth retrying.
        if (exitStatus >= 0) {
            onRemoteExit(exitStatus)
            return
        }

        val detail = cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: liveness
        writeLocal(
            "\r\n\u001B[31m[" +
                context.getString(R.string.ssh_connection_terminated, detail) +
                "]\u001B[0m\r\n"
        )

        val settings = appPreferences.connection.first()

        // A session that dies immediately is a server-side refusal, not a flaky network, so
        // retrying in a tight loop would only hammer the host.
        val lasted = System.currentTimeMillis() - sessionStartedAt
        consecutiveQuickDrops = if (lasted < STABLE_SESSION_MS) consecutiveQuickDrops + 1 else 0

        _status.value = TerminalStatus.Disconnected(detail)
        if (settings.autoReconnect && consecutiveQuickDrops <= settings.autoReconnectAttempts) {
            connect()
        } else {
            onFinished(serverId)
        }
    }

    private fun onRemoteExit(exitStatus: Int) {
        writeLocal(
            "\r\n\u001B[33m[" +
                context.getString(R.string.ssh_session_closed, exitStatus) +
                "]\u001B[0m\r\n"
        )
        consecutiveQuickDrops = 0
        sshManager.disconnect()
        connection = null
        _status.value = TerminalStatus.Ended(exitStatus)
        onFinished(serverId)
    }

    /**
     * JSch can deliver `exit-status` a moment after the stream ends, so a single read would
     * sometimes miss it and mistake a logout for a dropped connection.
     */
    private suspend fun awaitExitStatus(): Int {
        repeat(EXIT_STATUS_POLLS) {
            val status = sshManager.exitStatus()
            if (status >= 0) return status
            delay(EXIT_STATUS_POLL_MS)
        }
        return sshManager.exitStatus()
    }

    // --- Host key ----------------------------------------------------------------------------

    private suspend fun requestHostKeyDecision(verdict: HostKeyVerdict): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        hostKeyDecision = deferred
        _hostKeyPrompt.value = verdict
        diagnostics.verbose(
            "ssh",
            "server#$serverId host key prompt: ${verdict::class.simpleName}"
        )
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

    // --- Interactive credentials -------------------------------------------------------------

    /** Returns the typed secret, or null if the user dismissed the prompt. */
    private suspend fun requestSecret(credential: Credential): String? {
        val deferred = CompletableDeferred<String?>()
        secretDecision = deferred
        _secretPrompt.value = SecretPrompt(
            username = credential.username,
            passphrase = credential.authType != AuthType.PASSWORD
        )
        return try {
            deferred.await()
        } finally {
            _secretPrompt.value = null
            secretDecision = null
        }
    }

    fun resolveSecretPrompt(secret: String?) {
        secretDecision?.complete(secret)
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

    private fun ringBell() {
        scope.launch {
            when (appPreferences.terminal.first().bellMode) {
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
    }

    private fun vibrator(): Vibrator? = runCatching {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    }.getOrNull()

    /** Terminates the session for good and releases its threads. */
    fun close() {
        closedByUser = true
        diagnostics.verbose("ssh", "server#$serverId session closed by user")
        sessionGeneration++
        hostKeyDecision?.complete(false)
        secretDecision?.complete(null)
        promptedSecret = null
        connectJob?.cancel()
        connectJob = null
        readerJob?.cancel()
        readerJob = null
        sshManager.disconnect()
        connection = null
        // A remote logout already reported its exit status; don't downgrade it to a plain drop.
        if (_status.value !is TerminalStatus.Ended) {
            _status.value = TerminalStatus.Disconnected(null)
        }
        scope.cancel()
        readerDispatcher.close()
    }

    private companion object {
        const val TAG = "KnotSSH_Session"
        const val ESCAPE = '\u001B'
        const val READ_BUFFER_SIZE = 8192
        const val RENDER_INTERVAL_MS = 16L
        const val RESIZE_DEBOUNCE_MS = 150L
        const val RECONNECT_BASE_DELAY_MS = 1_500L
        const val STABLE_SESSION_MS = 5_000L
        const val EXIT_STATUS_POLLS = 10
        const val EXIT_STATUS_POLL_MS = 30L
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
