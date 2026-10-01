package com.knotssh.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.diagnostics.DiagnosticsLog
import com.knotssh.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val log: DiagnosticsLog
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val entries: StateFlow<List<String>> = log.entriesText

    val verbose: StateFlow<Boolean> =
        prefs.diagnosticsVerbose.stateIn(viewModelScope, started, false)

    fun setVerbose(value: Boolean) = viewModelScope.launch { prefs.setDiagnosticsVerbose(value) }

    fun clear() = log.clear()

    fun report(): String = log.report()

    fun export(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(log.report().toByteArray())
                    } ?: error("no stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }
}
