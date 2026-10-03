package com.knotssh.data.sync

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import com.knotssh.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the encrypted backup is kept.
 *
 * Deliberately narrow: read a blob, write a blob, tell when it last changed. Anything that can do
 * those three things can back the sync, which is what lets a Drive implementation be added later
 * without touching the engine.
 */
interface SyncTarget {
    /** Shown to the user so they can tell where their data is going. */
    suspend fun describe(): String?

    suspend fun write(bytes: ByteArray)

    /** `null` when the destination exists but holds nothing yet. */
    suspend fun read(): ByteArray?

    /** Remote modification time, used to decide whether there is anything worth pulling. */
    suspend fun lastModified(): Long?
}

/**
 * A single document the user picked through the system file picker, kept alive by a persisted
 * URI permission.
 *
 * One document rather than a folder on purpose: there is no child lookup to get wrong, and
 * writing is a single `openOutputStream`. Fewer moving parts is the whole point for something
 * that runs unattended.
 */
class DocumentSyncTarget(
    private val context: Context,
    private val uri: Uri
) : SyncTarget {

    override suspend fun describe(): String? = withContext(Dispatchers.IO) {
        queryColumn(DocumentsContract.Document.COLUMN_DISPLAY_NAME) { getString(it) }
    }

    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        // Cloud-backed providers do not all implement truncation, and one that rejects a mode
        // throws instead of degrading: ask for the strictest first, then settle for less.
        val stream = WRITE_MODES.firstNotNullOfOrNull { mode ->
            try {
                context.contentResolver.openOutputStream(uri, mode)
            } catch (e: SecurityException) {
                throw SyncException.PermissionLost()
            } catch (_: Exception) {
                null
            }
        } ?: throw SyncException.Unavailable()

        stream.use { it.write(bytes) }
    }

    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: SecurityException) {
            throw SyncException.PermissionLost()
        } catch (e: Exception) {
            throw SyncException.Unavailable()
        }
    }

    override suspend fun lastModified(): Long? = withContext(Dispatchers.IO) {
        queryColumn(DocumentsContract.Document.COLUMN_LAST_MODIFIED) { getLong(it) }
    }

    private fun <T> queryColumn(name: String, read: Cursor.(Int) -> T): T? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(name)
            if (column >= 0 && cursor.moveToFirst()) cursor.read(column) else null
        }
    }.getOrNull()

    private companion object {
        /** Truncating, truncating read-write, plain write. */
        val WRITE_MODES = listOf("wt", "rwt", "w")
    }
}

sealed class SyncException(@StringRes val messageRes: Int) : Exception() {
    /** The destination is gone: file deleted, provider uninstalled, storage unmounted. */
    class Unavailable : SyncException(R.string.sync_error_unavailable)

    /** The persisted permission was dropped, usually after the provider re-created the document. */
    class PermissionLost : SyncException(R.string.sync_error_permission)

    /** The provider accepted the write and then kept something else, or nothing at all. */
    class NotPersisted : SyncException(R.string.sync_error_not_persisted)

    class NotConfigured : SyncException(R.string.sync_error_not_configured)
}
