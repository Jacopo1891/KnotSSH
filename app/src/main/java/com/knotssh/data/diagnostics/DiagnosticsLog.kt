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
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticLevel(val marker: Char) { INFO('I'), WARN('W'), ERROR('E') }

/**
 * Rolling log of app-level events, kept so a user can send a meaningful bug report.
 *
 * Everything that goes in passes through [DiagnosticsRedactor] first, and terminal output,
 * credentials and hostnames are never recorded at all. The file lives in the app's private
 * storage and only leaves the device when the user explicitly exports or shares it.
 */
@Singleton
class DiagnosticsLog @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: AppPreferences
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    /** Routine events are opt-in; failures are always kept so a report is never empty. */
    @Volatile
    private var verboseEnabled = false

    init {
        synchronized(lock) {
            runCatching { if (file.exists()) lines.addAll(file.readLines().takeLast(MAX_LINES)) }
            _entries.value = lines.toList()
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
            lines.clear()
            runCatching { file.delete() }
            _entries.value = emptyList()
        }
    }

    /** Full text to export or attach to an email, environment header included. */
    fun report(): String {
        val body = synchronized(lock) { lines.toList() }
        return buildString {
            appendLine("KnotSSH diagnostic report")
            appendLine("Generated: ${SimpleDateFormat(REPORT_FORMAT, Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${BuildConfig.BUILD_TYPE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("Entries: ${body.size}")
            appendLine("Hostnames, accounts, keys and terminal output are never recorded.")
            appendLine("---")
            body.forEach(::appendLine)
        }
    }

    private fun append(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        redact: Boolean = true
    ) {
        val stamp = synchronized(timestampFormat) { timestampFormat.format(Date()) }
        val body = if (redact) DiagnosticsRedactor.redact(message) else message
        val line = "$stamp ${level.marker}/$tag: $body"
        synchronized(lock) {
            lines.addLast(line)
            val trimmed = lines.size > MAX_LINES
            while (lines.size > MAX_LINES) lines.removeFirst()
            runCatching {
                // A rewrite is only needed when the oldest lines were dropped.
                if (trimmed) file.writeText(lines.joinToString("\n", postfix = "\n"))
                else file.appendText("$line\n")
            }
            _entries.value = lines.toList()
        }
    }

    private companion object {
        const val FILE_NAME = "diagnostics.log"
        const val MAX_LINES = 500
        const val REPORT_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
}
