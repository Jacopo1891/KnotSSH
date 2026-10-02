package com.knotssh.ssh

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import com.knotssh.BuildConfig
import com.knotssh.R
import com.knotssh.data.local.preferences.ConnectionSettings
import com.knotssh.data.local.preferences.HostKeyPolicy
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.ForwardType
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.Server
import com.knotssh.domain.repository.KnownHostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/** Raised when the remote host could not be authenticated. Never retried automatically. */
class HostKeyException(val rejection: HostKeyRejection, message: String) : Exception(message)

/**
 * Raised when the server rejected our credentials. Never retried automatically: the secret will
 * not become correct on its own, and repeated failures trip fail2ban or lock the account out.
 */
class AuthFailedException(message: String) : Exception(message)

/** JSch reports authentication failures only through the exception text. */
private fun isAuthFailure(e: Throwable): Boolean {
    val message = e.message?.lowercase() ?: return false
    return AUTH_FAILURE_MARKERS.any { it in message }
}

private val AUTH_FAILURE_MARKERS = listOf(
    "auth fail",
    "auth cancel",
    "userauth fail",
    "too many authentication failures"
)

class SshConnection(
    val input: InputStream,
    private val output: OutputStream,
    private val session: Session,
    private val channel: ChannelShell,
    private val onClose: () -> Unit
) {
    /**
     * JSch pumps everything we write through a [java.io.PipedInputStream]. Its `read()` throws
     * "Write end dead" as soon as the thread that last wrote has terminated, and JSch answers
     * that by sending channel EOF — which makes the remote shell exit and the session drop.
     * Writes must therefore always come from the same long-lived thread, never from a pooled
     * dispatcher whose threads are recycled.
     */
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KnotSSH-writer").apply { isDaemon = true }
    }

    @Volatile
    private var closed = false

    val isConnected: Boolean get() = session.isConnected && channel.isConnected

    val sessionAlive: Boolean get() = session.isConnected

    val channelAlive: Boolean get() = channel.isConnected

    /** -1 while the remote command is still running. */
    val exitStatus: Int get() = channel.exitStatus

    fun send(bytes: ByteArray, onError: (Throwable) -> Unit) {
        if (closed) return
        runCatching {
            writer.execute {
                if (closed) return@execute
                try {
                    output.write(bytes)
                    output.flush()
                } catch (t: Throwable) {
                    if (!closed) onError(t)
                }
            }
        }
    }

    fun resizePty(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        runCatching { channel.setPtySize(columns, rows, widthPx, heightPx) }
    }

    fun close() {
        closed = true
        // Deliberately not closing [output]: that would signal EOF to the remote shell.
        writer.shutdownNow()
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
        onClose()
    }
}

class SshManager @Inject constructor(
    private val knownHosts: KnownHostRepository
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var connection: SshConnection? = null

    private val _warnings = mutableListOf<String>()

    /** Non-fatal setup issues, e.g. forwarding rules that could not be applied. */
    val warnings: List<String> get() = _warnings.toList()

    suspend fun connect(
        context: Context,
        server: Server,
        credential: Credential,
        plainSecret: String,
        passphrase: String?,
        settings: ConnectionSettings,
        hostKeyPolicy: HostKeyPolicy,
        verboseLogging: Boolean,
        columns: Int,
        rows: Int,
        onHostKeyPrompt: suspend (HostKeyVerdict) -> Boolean
    ): SshConnection = withContext(Dispatchers.IO) {
        disconnect()
        _warnings.clear()

        configureLogging(verboseLogging)
        if (settings.wakeLock) acquireWakeLock(context, settings.wakeLockMinutes)

        val jsch = JSch()
        jsch.removeAllIdentity()

        val gate = HostKeyGate(
            host = server.hostname,
            port = server.port,
            policy = hostKeyPolicy,
            knownHosts = knownHosts,
            onPrompt = onHostKeyPrompt
        )
        jsch.hostKeyRepository = gate

        if (credential.authType == AuthType.SSH_KEY) {
            val keyBytes = plainSecret.toByteArray(Charsets.UTF_8)
            val passphraseBytes = passphrase?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            try {
                jsch.addIdentity(
                    credential.alias.ifBlank { "knotssh_key" },
                    keyBytes,
                    null,
                    passphraseBytes
                )
            } catch (e: JSchException) {
                releaseWakeLock()
                throw IllegalArgumentException(
                    context.getString(R.string.ssh_bad_private_key, e.message.orEmpty()), e
                )
            } finally {
                keyBytes.fill(0)
                passphraseBytes?.fill(0)
            }
        }

        val session = jsch.getSession(credential.username, server.hostname, server.port)
        session.userInfo = buildUserInfo(credential, plainSecret, passphrase)
        session.setConfig("StrictHostKeyChecking", "ask")

        val keepAlive = server.keepAliveSeconds.takeIf { it > 0 } ?: settings.keepAliveSeconds
        if (keepAlive > 0) {
            session.setServerAliveInterval(keepAlive * 1000)
            session.serverAliveCountMax = 3
        }

        if (settings.compression) {
            session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
            session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
            session.setConfig("compression_level", "6")
        } else {
            session.setConfig("compression.s2c", "none")
            session.setConfig("compression.c2s", "none")
        }

        when (credential.authType) {
            AuthType.PASSWORD -> {
                session.setPassword(plainSecret)
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            }
            AuthType.SSH_KEY ->
                session.setConfig("PreferredAuthentications", "publickey")
        }

        val timeout = server.connectTimeoutSeconds.takeIf { it > 0 } ?: settings.connectTimeoutSeconds
        try {
            session.connect(timeout * 1000)
        } catch (e: CancellationException) {
            releaseWakeLock()
            throw e
        } catch (e: Exception) {
            releaseWakeLock()
            gate.rejection?.let { throw HostKeyException(it, describe(context, it, server)) }
            if (isAuthFailure(e)) {
                throw AuthFailedException(
                    context.getString(R.string.ssh_auth_failed, credential.username)
                )
            }
            throw e
        }

        applyPortForwarding(context, session, server)

        val channel = session.openChannel("shell") as ChannelShell
        channel.setPty(true)
        channel.setPtyType(PTY_TYPE)
        channel.setPtySize(columns, rows, columns * 8, rows * 16)

        val input = channel.inputStream
        val output = channel.outputStream
        // Deliberately no timeout argument: a non-zero channel connectTimeout makes JSch set
        // want_reply on every later channel request, including window-change, which RFC 4254
        // forbids and which then busy-waits for a reply the server need not send.
        channel.connect()

        SshConnection(input, output, session, channel) { releaseWakeLock() }
            .also { connection = it }
    }

    private fun applyPortForwarding(context: Context, session: Session, server: Server) {
        server.portForwardRules.forEach { rule ->
            try {
                when (rule.type) {
                    ForwardType.LOCAL -> session.setPortForwardingL(
                        rule.localPort,
                        rule.remoteHost.ifBlank { "127.0.0.1" },
                        rule.remotePort
                    )
                    ForwardType.REMOTE -> session.setPortForwardingR(
                        rule.remotePort,
                        rule.remoteHost.ifBlank { "127.0.0.1" },
                        rule.localPort
                    )
                    // JSch ships no SOCKS server, so this rule cannot be honoured.
                    ForwardType.DYNAMIC -> _warnings +=
                        context.getString(R.string.ssh_dynamic_forward_unsupported, rule.localPort)
                }
            } catch (e: JSchException) {
                _warnings += context.getString(
                    R.string.ssh_forward_rule_failed,
                    rule.type.name,
                    rule.localPort,
                    e.message.orEmpty()
                )
            }
        }
    }

    private fun buildUserInfo(
        credential: Credential,
        plainSecret: String,
        passphrase: String?
    ): UserInfo = object : UserInfo, UIKeyboardInteractive {
        private var passwordAttempted = false

        override fun getPassphrase(): String? = passphrase

        override fun getPassword(): String? =
            plainSecret.takeIf { credential.authType == AuthType.PASSWORD }

        override fun promptPassword(message: String?): Boolean {
            if (credential.authType != AuthType.PASSWORD) return false
            if (passwordAttempted) return false
            passwordAttempted = true
            return true
        }

        override fun promptPassphrase(message: String?): Boolean = passphrase != null

        /** Reached only after [HostKeyGate] already refused, so never blanket-accept here. */
        override fun promptYesNo(message: String?): Boolean = false

        override fun showMessage(message: String?) = Unit

        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<out String>?,
            echo: BooleanArray?
        ): Array<String>? {
            if (credential.authType != AuthType.PASSWORD) return null
            if (passwordAttempted) return null
            passwordAttempted = true
            return Array(prompt?.size ?: 1) { plainSecret }
        }
    }

    private fun describe(
        context: Context,
        rejection: HostKeyRejection,
        server: Server
    ): String = when (rejection) {
        is HostKeyRejection.UnknownHost -> context.getString(
            R.string.ssh_unknown_host,
            server.hostname,
            server.port,
            rejection.fingerprint
        )
        is HostKeyRejection.KeyChanged -> context.getString(
            R.string.ssh_host_key_changed,
            server.hostname,
            server.port,
            rejection.expected,
            rejection.actual
        )
        HostKeyRejection.UserDeclined -> context.getString(R.string.ssh_host_key_declined)
    }

    private fun configureLogging(verbose: Boolean) {
        val enabled = verbose && BuildConfig.DEBUG
        JSch.setLogger(object : com.jcraft.jsch.Logger {
            override fun isEnabled(level: Int): Boolean = enabled
            override fun log(level: Int, message: String) {
                if (enabled) Log.d(TAG, message)
            }
        })
    }

    private fun acquireWakeLock(context: Context, minutes: Int) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KnotSSH:SshPartialWakeLock")
            .apply { acquire(minutes * 60_000L) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    fun resizePty(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {
        connection?.resizePty(columns, rows, widthPx, heightPx)
    }

    fun send(bytes: ByteArray, onError: (Throwable) -> Unit) {
        connection?.send(bytes, onError)
    }

    /** -1 while the remote command is still running or when there is no session. */
    fun exitStatus(): Int = connection?.exitStatus ?: -1

    /** Distinguishes "the shell closed" from "the transport went down". */
    fun livenessReport(): String {
        val current = connection ?: return "no session"
        return "session=${current.sessionAlive} channel=${current.channelAlive}"
    }

    fun disconnect() {
        connection?.close()
        connection = null
        releaseWakeLock()
    }

    fun isConnected(): Boolean = connection?.isConnected == true

    companion object {
        private const val TAG = "KnotSSH_Ssh"
        private const val PTY_TYPE = "xterm-256color"
    }
}
