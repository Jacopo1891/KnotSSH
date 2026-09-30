package com.knotssh.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.R
import com.knotssh.data.backup.BackupCodec
import com.knotssh.data.backup.BackupException
import com.knotssh.data.backup.BackupManager
import com.knotssh.data.backup.BackupPayload
import com.knotssh.data.backup.BackupSummary
import com.knotssh.data.backup.ImportOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A decoded file waiting for the user to confirm what to do with it. */
data class PendingImport(val payload: BackupPayload, val summary: BackupSummary)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    private val _exported = MutableStateFlow(false)
    val exported: StateFlow<Boolean> = _exported.asStateFlow()

    private val _passwordRequired = MutableStateFlow(false)
    val passwordRequired: StateFlow<Boolean> = _passwordRequired.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _outcome = MutableStateFlow<ImportOutcome?>(null)
    val outcome: StateFlow<ImportOutcome?> = _outcome.asStateFlow()

    /** Held between reading the file and learning its password. Dropped as soon as it is decoded. */
    private var encryptedFile: ByteArray? = null

    fun export(uri: Uri, password: CharArray?, includeSettings: Boolean) {
        run(R.string.backup_error_write) {
            val payload = backupManager.export(includeSettings)
            val bytes = BackupCodec.encode(payload, password)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw BackupException.Corrupted("Cannot open the destination")
            }
            password?.fill('\u0000')
            _exported.value = true
        }
    }

    fun beginImport(uri: Uri) {
        run(R.string.backup_error_read) {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw BackupException.Corrupted("Cannot open the file")
            }
            if (BackupCodec.inspect(bytes).encrypted) {
                encryptedFile = bytes
                _passwordRequired.value = true
            } else {
                decode(bytes, null)
            }
        }
    }

    fun submitImportPassword(password: CharArray) {
        val bytes = encryptedFile ?: return
        run(R.string.backup_error_read) {
            decode(bytes, password)
            password.fill('\u0000')
        }
    }

    fun confirmImport(restoreSettings: Boolean) {
        val pending = _pendingImport.value ?: return
        run(R.string.backup_error_import) {
            _pendingImport.value = null
            _outcome.value = backupManager.import(pending.payload, restoreSettings)
        }
    }

    fun cancelImport() {
        encryptedFile = null
        _passwordRequired.value = false
        _pendingImport.value = null
    }

    fun dismissMessage() {
        _error.value = null
        _exported.value = false
        _outcome.value = null
    }

    private suspend fun decode(bytes: ByteArray, password: CharArray?) {
        val payload = withContext(Dispatchers.Default) { BackupCodec.decode(bytes, password) }
        encryptedFile = null
        _passwordRequired.value = false
        _pendingImport.value = PendingImport(payload, backupManager.summarize(payload))
    }

    private fun run(@StringRes fallbackError: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: BackupException) {
                _error.value = e.messageRes()
            } catch (e: Exception) {
                _error.value = fallbackError
            } finally {
                _busy.value = false
            }
        }
    }

    private fun BackupException.messageRes(): Int = when (this) {
        is BackupException.NotABackup -> R.string.backup_error_not_a_backup
        is BackupException.UnsupportedVersion -> R.string.backup_error_version
        is BackupException.PasswordRequired -> R.string.backup_error_password_required
        is BackupException.WrongPassword -> R.string.backup_error_wrong_password
        is BackupException.Corrupted -> R.string.backup_error_corrupted
    }
}
