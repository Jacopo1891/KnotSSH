package com.knotssh.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.knotssh.data.backup.BackupCodec
import com.knotssh.data.diagnostics.DiagnosticsLog
import com.knotssh.data.backup.BackupManager
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.SyncSettings
import com.knotssh.data.local.security.CryptoManager
import com.knotssh.di.ApplicationScope
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.FolderRepository
import com.knotssh.domain.repository.KnownHostRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.domain.repository.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val running: Boolean = false,
    val pendingChanges: Boolean = false,
    val location: String? = null
)

/**
 * Keeps an encrypted copy of the whole configuration on a destination the user chose.
 *
 * Writes are coalesced rather than immediate: encoding runs Argon2id over 47 MiB, so saving five
 * servers in a row has to cost one upload, not five. Anything still pending is flushed when the
 * app goes to the background, so nothing is lost by walking away.
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val backupManager: BackupManager,
    private val crypto: CryptoManager,
    private val diagnostics: DiagnosticsLog,
    serverRepository: ServerRepository,
    credentialRepository: CredentialRepository,
    folderRepository: FolderRepository,
    knownHostRepository: KnownHostRepository,
    quickCommandRepository: QuickCommandRepository,
    customKeyRepository: CustomKeyRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var debounceJob: Job? = null

    /** Every table that belongs in a backup; any emission means the local copy is stale. */
    private val dataChanges = merge(
        serverRepository.getAllServers().map { },
        credentialRepository.getAllCredentials().map { },
        folderRepository.getAll().map { },
        knownHostRepository.getAll().map { },
        quickCommandRepository.getAllCommands().map { },
        customKeyRepository.getAll().map { }
    )

    fun start() {
        scope.launch {
            // drop(6): the first emission of each flow is the current content, not a change.
            dataChanges.drop(INITIAL_EMISSIONS).collect { markDirty() }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                scope.launch { flush() }
            }
        })
        scope.launch { pullIfRemoteIsNewer() }
    }

    /** Grants the app lasting access to the document the user picked. */
    fun persistPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Points the app at [uri] and makes it the destination from now on.
     *
     * Anything already there is merged **before** the first upload: adopting a copy made on
     * another device must not let this one overwrite it with its own, possibly empty, state.
     * A passphrase that cannot open the existing file aborts without touching anything.
     *
     * Returns `true` when an existing copy was adopted.
     */
    suspend fun configure(uri: Uri, passphrase: String): Result<Boolean> {
        persistPermission(uri)
        val target = DocumentSyncTarget(context, uri)

        val existing = try {
            target.read()
        } catch (e: Exception) {
            diagnostics.error("sync", "cannot read the chosen destination", e)
            return Result.failure(e)
        }

        var adopted = false
        if (existing != null) {
            try {
                val payload = BackupCodec.decode(existing, passphrase.toCharArray())
                backupManager.import(payload, restoreSettings = false)
                adopted = true
            } catch (e: Exception) {
                diagnostics.error("sync", "cannot open the existing copy", e)
                return Result.failure(e)
            }
        }

        preferences.setSyncTarget(uri.toString(), crypto.encrypt(passphrase))
        preferences.setSyncEnabled(true)
        preferences.setSyncOutcome(0L, 0L, null)
        syncNow()
        return Result.success(adopted)
    }

    suspend fun disable() {
        preferences.setSyncEnabled(false)
        preferences.setSyncTarget(null, null)
        preferences.setSyncOutcome(0L, 0L, null)
        _status.value = SyncStatus()
    }

    private fun markDirty() {
        _status.value = _status.value.copy(pendingChanges = true)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            flush()
        }
    }

    private suspend fun flush() {
        if (!_status.value.pendingChanges) return
        syncNow()
    }

    suspend fun syncNow(): Result<Unit> = mutex.withLock {
        val settings = preferences.sync.first()
        if (!settings.enabled || !settings.isConfigured) {
            return Result.failure(SyncException.NotConfigured())
        }
        debounceJob?.cancel()
        _status.value = _status.value.copy(running = true)
        return try {
            upload(settings)
            _status.value = SyncStatus(running = false, pendingChanges = false, location = _status.value.location)
            Result.success(Unit)
        } catch (e: Exception) {
            diagnostics.error("sync", "upload failed", e)
            preferences.setSyncOutcome(settings.lastSyncMs, settings.remoteStampMs, e.describeForUi())
            _status.value = _status.value.copy(running = false)
            Result.failure(e)
        }
    }

    private suspend fun upload(settings: SyncSettings) {
        val target = targetOf(settings)
        val passphrase = crypto.decrypt(settings.encryptedPassphrase!!).toCharArray()
        val bytes = try {
            BackupCodec.encode(backupManager.export(includeSettings = true), passphrase)
        } finally {
            passphrase.fill('\u0000')
        }
        target.write(bytes)
        // Compared byte for byte rather than merely parsed: a provider that truncates, defers or
        // quietly drops the upload would otherwise leave the user believing they have a backup.
        val stored = target.read() ?: throw SyncException.NotPersisted()
        if (!stored.contentEquals(bytes)) throw SyncException.NotPersisted()
        writeSystemBackupMirror(bytes)

        val stamp = target.lastModified() ?: System.currentTimeMillis()
        preferences.setSyncOutcome(System.currentTimeMillis(), stamp, null)
        _status.value = _status.value.copy(location = target.describe())
        diagnostics.verbose("sync", "uploaded ${bytes.size} bytes")
    }

    /**
     * Keeps a copy where Android's own backup can pick it up. Already encrypted with the user's
     * passphrase, so handing it to the system backup leaks nothing, and it is the only thing in
     * the app's data that is portable: the database is sealed to this device by the Keystore.
     */
    private fun writeSystemBackupMirror(bytes: ByteArray) {
        runCatching {
            val dir = File(context.filesDir, SYSTEM_BACKUP_DIR).apply { mkdirs() }
            File(dir, SYSTEM_BACKUP_FILE).writeBytes(bytes)
        }
    }

    /** The copy left behind by a system restore, if there is one. */
    fun systemBackupFile(): File? =
        File(File(context.filesDir, SYSTEM_BACKUP_DIR), SYSTEM_BACKUP_FILE).takeIf { it.isFile }

    /** Merges a copy left by another device. Never destructive: the import only adds and renames. */
    suspend fun pullIfRemoteIsNewer(force: Boolean = false): Result<Boolean> = mutex.withLock {
        val settings = preferences.sync.first()
        if (!settings.enabled || !settings.isConfigured) {
            return Result.failure(SyncException.NotConfigured())
        }
        return try {
            val target = targetOf(settings)
            val stamp = target.lastModified()
            if (!force && (stamp == null || stamp <= settings.remoteStampMs)) {
                return Result.success(false)
            }
            val bytes = target.read() ?: return Result.success(false)
            val passphrase = crypto.decrypt(settings.encryptedPassphrase!!).toCharArray()
            val payload = try {
                BackupCodec.decode(bytes, passphrase)
            } finally {
                passphrase.fill('\u0000')
            }
            backupManager.import(payload, restoreSettings = false)
            preferences.setSyncOutcome(
                System.currentTimeMillis(),
                stamp ?: settings.remoteStampMs,
                null
            )
            diagnostics.info("sync", "merged a newer remote copy")
            Result.success(true)
        } catch (e: Exception) {
            diagnostics.error("sync", "pull failed", e)
            preferences.setSyncOutcome(settings.lastSyncMs, settings.remoteStampMs, e.describeForUi())
            Result.failure(e)
        }
    }

    private fun targetOf(settings: SyncSettings): SyncTarget =
        DocumentSyncTarget(context, Uri.parse(settings.targetUri!!))

    private fun Exception.describeForUi(): String = when (this) {
        is SyncException -> context.getString(messageRes)
        else -> this::class.java.simpleName
    }

    private companion object {
        const val DEBOUNCE_MS = 30_000L
        const val INITIAL_EMISSIONS = 6
        const val SYSTEM_BACKUP_DIR = "cloud"
        const val SYSTEM_BACKUP_FILE = "knotssh-sync.knotssh"
    }
}
