package com.knotssh.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.update.AppRelease
import com.knotssh.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Only reached through the manual button: a silent startup check stays quiet. */
    data object UpToDate : UpdateState

    data class Available(val release: AppRelease) : UpdateState

    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState

    data class ReadyToInstall(val release: AppRelease, val apk: File) : UpdateState

    data object PermissionRequired : UpdateState

    data object Failed : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = repository.currentVersion

    val autoCheckEnabled = preferences.autoUpdateCheck

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdateCheck(enabled) }
    }

    /** Startup path: stays silent unless there is something to offer. */
    fun checkOnStartup() {
        viewModelScope.launch {
            if (!repository.isReleaseBuild) return@launch
            if (!preferences.autoUpdateCheck.first()) return@launch
            val last = preferences.lastUpdateCheck.first()
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return@launch
            runCheck(silent = true)
        }
    }

    fun checkNow() {
        viewModelScope.launch { runCheck(silent = false) }
    }

    private suspend fun runCheck(silent: Boolean) {
        if (!silent) _state.value = UpdateState.Checking
        val result = repository.fetchLatest()
        preferences.setLastUpdateCheck(System.currentTimeMillis())
        result.onSuccess { release ->
            _state.value = when {
                repository.isNewer(release.version) -> UpdateState.Available(release)
                silent -> UpdateState.Idle
                else -> UpdateState.UpToDate
            }
        }.onFailure {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed
        }
    }

    fun download(release: AppRelease) {
        if (!repository.canInstallPackages()) {
            _state.value = UpdateState.PermissionRequired
            return
        }
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            repository.download(release) { done, total ->
                val progress = if (total > 0) done.toFloat() / total else 0f
                _state.value = UpdateState.Downloading(release, progress.coerceIn(0f, 1f))
            }.onSuccess {
                _state.value = UpdateState.ReadyToInstall(release, it)
            }.onFailure {
                _state.value = UpdateState.Failed
            }
        }
    }

    fun installIntent(apk: File) = repository.installIntent(apk)

    fun unknownSourcesIntent() = repository.unknownSourcesSettingsIntent()

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private companion object {
        val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
    }
}
