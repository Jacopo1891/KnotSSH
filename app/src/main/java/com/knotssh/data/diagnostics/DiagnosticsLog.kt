package com.knotssh.data.diagnostics

import android.content.Context
import android.os.Build
import com.knotssh.BuildConfig
import com.knotssh.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticLevel(val marker: Char) {
    INFO('I'),
    WARN('W'),
    ERROR('E');

    val isProblem: Boolean get() = this != INFO
}

private data class DiagnosticEntry(
    val timestampMs: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String,
    val count: Int = 1
) {
    fun sameEventAs(other: DiagnosticEntry) =
        level == other.level && tag == other.tag && message == other.message
}

/**
 * Rolling log of app-level events, kept so a user can send a meaningful bug report.
 *
 * Everything that goes in passes through [DiagnosticsRedactor] first, and terminal output,
 * credentials and hostnames are never recorded at all. The file lives in the app's private
 * storage and only leaves the device when the user explicitly exports or shares it.
 *
 * Retention is deliberately asymmetric. An install that never misbehaves produces only routine
 * events, which are worthless after a few days and are capped hard and expired quickly; failures
 * and crashes are what a report is made of, so they get a far larger budget and a far longer life.
 * Repeating the same event does not add a line, it bumps a counter, which is what keeps an
 * open-close-open-close session from filling the log.
 */
@Singleton
class DiagnosticsLog @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: AppPreferences
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private val entries = ArrayDeque<DiagnosticEntry>()
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Formatted, newest last. Exposed for the preview and for the "N entries" label. */
    val entriesText: StateFlow<List<String>> = _lines.asStateFlow()

    /** Routine events are opt-in; failures are always kept so a report is never empty. */
    @Volatile
    private var verboseEnabled = false

    init {
        synchronized(lock) {
            runCatching {
                if (file.exists()) file.readLines().mapNotNullTo(entries) { decode(it) }
            }
            prune(System.currentTimeMillis())
            publish()
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            preferences.diagnosticsVerbose.collect { verboseEnabled = it }
        }
    }

    fun info(tag: String, message: String) = append(DiagnosticLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = append(DiagnosticLevel.WARN, tag, message)

    fun error(tag: String, message: String, error: Throwable? = null) {
        if (error == null) {
            append(DiagnosticLevel.ERROR, tag, message)
            return
        }
        // describe() redacts as it walks the cause chain, so the result must not be run twice.
        val detail = "${DiagnosticsRedactor.redact(message)} — ${DiagnosticsRedactor.describe(error)}"
        append(DiagnosticLevel.ERROR, tag, detail, redact = false)
    }

    /** Recorded only while the user has detailed logging switched on. */
    fun verbose(tag: String, message: String) {
        if (verboseEnabled) append(DiagnosticLevel.INFO, tag, message)
    }

    fun crash(thread: String, error: Throwable) {
        // Already redacted piecewise: the stack frames must survive verbatim to be of any use.
        append(
            level = DiagnosticLevel.ERROR,
            tag = "crash",
            message = "on $thread: ${DiagnosticsRedactor.describe(error)}\n" +
                DiagnosticsRedactor.stackTrace(error),
            redact = false
        )
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            runCatching { file.delete() }
            publish()
        }
    }

    /** Full text to export or attach to an email, environment header included. */
    fun report(): String {
        val body = synchronized(lock) { entries.toList() }
        val problems = body.count { it.level.isProblem }
        return buildString {
            appendLine("KnotSSH diagnostic report")
            appendLine("Generated: ${SimpleDateFormat(REPORT_FORMAT, Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${BuildConfig.BUILD_TYPE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("Entries: ${body.size} ($problems with a problem)")
            appendLine("Hostnames, accounts, keys and terminal output are never recorded.")
            appendLine(
                "Routine events expire after $INFO_MAX_AGE_DAYS days, " +
                    "problems after $PROBLEM_MAX_AGE_DAYS."
            )
            appendLine("---")
            body.forEach { appendLine(it.format()) }
        }
    }

    private fun append(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        redact: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        val entry = DiagnosticEntry(
            timestampMs = now,
            level = level,
            tag = tag,
            message = if (redact) DiagnosticsRedactor.redact(message) else message
        )

        synchronized(lock) {
            val last = entries.lastOrNull()
            if (last != null && last.sameEventAs(entry)) {
                // Same event again: bump the counter instead of adding a line.
                entries.removeLast()
                entries.addLast(last.copy(timestampMs = now, count = last.count + 1))
            } else {
                entries.addLast(entry)
            }
            prune(now)
            runCatching { file.writeText(entries.joinToString("\n", postfix = "\n") { encode(it) }) }
            publish()
        }
    }

    /**
     * Drops what has stopped being useful. Each class is capped on its own so a burst of routine
     * events can never push an old crash out of the log.
     */
    private fun prune(now: Long) {
        entries.removeAll { entry ->
            val maxAge = if (entry.level.isProblem) PROBLEM_MAX_AGE_MS else INFO_MAX_AGE_MS
            now - entry.timestampMs > maxAge
        }
        trimOldest(wantProblems = false, cap = MAX_INFO)
        trimOldest(wantProblems = true, cap = MAX_PROBLEMS)
    }

    private fun trimOldest(wantProblems: Boolean, cap: Int) {
        var excess = entries.count { it.level.isProblem == wantProblems } - cap
        if (excess <= 0) return
        val iterator = entries.iterator()
        while (iterator.hasNext() && excess > 0) {
            if (iterator.next().level.isProblem == wantProblems) {
                iterator.remove()
                excess--
            }
        }
    }

    private fun publish() {
        _lines.value = entries.map { it.format() }
    }

    private fun DiagnosticEntry.format(): String {
        val stamp = synchronized(timestampFormat) { timestampFormat.format(Date(timestampMs)) }
        val repeats = if (count > 1) " (x$count)" else ""
        return "$stamp ${level.marker}/$tag: $message$repeats"
    }

    private fun encode(entry: DiagnosticEntry) = listOf(
        entry.timestampMs.toString(),
        entry.count.toString(),
        entry.level.name,
        entry.tag,
        entry.message.replace("\n", NEWLINE)
    ).joinToString(SEPARATOR)

    private fun decode(line: String): DiagnosticEntry? {
        val parts = line.split(SEPARATOR)
        if (parts.size != 5) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        val level = DiagnosticLevel.entries.firstOrNull { it.name == parts[2] } ?: return null
        return DiagnosticEntry(timestamp, level, parts[3], parts[4].replace(NEWLINE, "\n"), count)
    }

    private companion object {
        const val FILE_NAME = "diagnostics.log"
        const val REPORT_FORMAT = "yyyy-MM-dd HH:mm:ss"

        /** Unit and record separators: never produced by a redacted message. */
        const val SEPARATOR = "\u001F"
        const val NEWLINE = "\u001E"

        const val MAX_INFO = 60
        const val MAX_PROBLEMS = 200
        const val INFO_MAX_AGE_DAYS = 7L
        const val PROBLEM_MAX_AGE_DAYS = 90L
        val INFO_MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(INFO_MAX_AGE_DAYS)
        val PROBLEM_MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(PROBLEM_MAX_AGE_DAYS)
    }
}
