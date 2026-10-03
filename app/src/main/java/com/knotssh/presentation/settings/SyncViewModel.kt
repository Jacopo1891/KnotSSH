package com.knotssh.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.SyncSettings
import com.knotssh.data.sync.SyncEngine
import com.knotssh.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    preferences: AppPreferences
) : ViewModel() {

    val settings: StateFlow<SyncSettings> = preferences.sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettings())

    val status: StateFlow<SyncStatus> = engine.status

    fun configure(uri: Uri, passphrase: String) {
        viewModelScope.launch { engine.configure(uri, passphrase) }
    }

    fun syncNow() {
        viewModelScope.launch { engine.syncNow() }
    }

    fun restoreNow() {
        viewModelScope.launch { engine.pullIfRemoteIsNewer(force = true) }
    }

    fun disable() {
        viewModelScope.launch { engine.disable() }
    }

    /** Present only after Android restored the app's data onto a new device. */
    fun systemBackupUri(): Uri? = engine.systemBackupFile()?.let(Uri::fromFile)
}
