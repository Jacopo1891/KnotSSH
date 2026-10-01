package com.knotssh.presentation.server

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.R
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.domain.model.*import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.FolderRepository
import com.knotssh.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditServerUiState(
    val alias: String = "",
    val hostname: String = "",
    val port: String = "22",
    val selectedCredentialId: Long? = null,
    val keepAliveSeconds: String = "30",
    val connectTimeoutSeconds: String = "30",
    val portForwardRules: List<PortForwardRule> = emptyList(),
    val credentials: List<Credential> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val foldersEnabled: Boolean = true,
    val folderId: Long? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    // Validation errors, as string resources so they follow the app language
    @StringRes val aliasError: Int? = null,
    @StringRes val hostnameError: Int? = null,
    @StringRes val portError: Int? = null,
    @StringRes val credentialError: Int? = null,
    val testConnectionResult: TestResult? = null
)

sealed class TestResult {
    data object Testing : TestResult()
    data class Success(val latencyMs: Long) : TestResult()
    data class Failure(val message: String) : TestResult()
}

@HiltViewModel
class EditServerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    private val folderRepository: FolderRepository,
    preferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Long? = savedStateHandle.get<Long>("serverId")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(EditServerUiState())
    val state: StateFlow<EditServerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.foldersEnabled.collect { enabled ->
                _state.update { it.copy(foldersEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            folderRepository.getAll().collect { folders ->
                _state.update { it.copy(folders = folders) }
            }
        }
        viewModelScope.launch {
            var knownIds: Set<Long>? = null
            credentialRepository.getAllCredentials().collect { creds ->
                val previousIds = knownIds
                knownIds = creds.mapTo(mutableSetOf()) { it.id }
                // A credential created while this form is open was almost certainly created
                // for this server, so select it instead of making the user pick it again.
                val created = previousIds?.let { old ->
                    creds.filter { it.id !in old }.maxByOrNull { it.id }
                }
                _state.update { state ->
                    state.copy(
                        credentials = creds,
                        selectedCredentialId = created?.id ?: state.selectedCredentialId,
                        credentialError = if (created != null) null else state.credentialError
                    )
                }
            }
        }
        serverId?.let { id ->
            viewModelScope.launch {
                serverRepository.getServerById(id)?.let { server ->
                    _state.update {
                        it.copy(
                            alias = server.alias,
                            hostname = server.hostname,
                            port = server.port.toString(),
                            selectedCredentialId = server.credentialId,
                            keepAliveSeconds = server.keepAliveSeconds.toString(),
                            connectTimeoutSeconds = server.connectTimeoutSeconds.toString(),
                            portForwardRules = server.portForwardRules,
                            folderId = server.folderId
                        )
                    }
                }
            }
        }
    }

    fun onAliasChanged(v: String) = _state.update { it.copy(alias = v, aliasError = null) }
    fun onHostnameChanged(v: String) = _state.update { it.copy(hostname = v, hostnameError = null) }
    fun onPortChanged(v: String) = _state.update { it.copy(port = v, portError = null) }
    fun onCredentialSelected(id: Long) = _state.update { it.copy(selectedCredentialId = id, credentialError = null) }
    fun onKeepAliveChanged(v: String) = _state.update { it.copy(keepAliveSeconds = v) }
    fun onTimeoutChanged(v: String) = _state.update { it.copy(connectTimeoutSeconds = v) }
    fun onFolderSelected(id: Long?) = _state.update { it.copy(folderId = id) }

    fun addPortForwardRule(rule: PortForwardRule) = _state.update {
        it.copy(portForwardRules = it.portForwardRules + rule)
    }

    fun removePortForwardRule(rule: PortForwardRule) = _state.update {
        it.copy(portForwardRules = it.portForwardRules - rule)
    }

    fun save() {
        val s = _state.value
        var hasError = false

        if (s.alias.isBlank()) {
            _state.update { it.copy(aliasError = R.string.error_required) }; hasError = true
        }
        if (s.hostname.isBlank()) {
            _state.update { it.copy(hostnameError = R.string.error_required) }; hasError = true
        }
        val portInt = s.port.toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            _state.update { it.copy(portError = R.string.error_invalid_port) }; hasError = true
        }
        if (s.selectedCredentialId == null) {
            _state.update { it.copy(credentialError = R.string.error_pick_credential) }; hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val server = Server(
                id = serverId ?: 0L,
                alias = s.alias.trim(),
                hostname = s.hostname.trim(),
                port = portInt!!,
                credentialId = s.selectedCredentialId!!,
                keepAliveSeconds = s.keepAliveSeconds.toIntOrNull() ?: 30,
                connectTimeoutSeconds = s.connectTimeoutSeconds.toIntOrNull() ?: 30,
                portForwardRules = s.portForwardRules,
                folderId = s.folderId
            )
            if (serverId != null) serverRepository.updateServer(server)
            else serverRepository.insertServer(server)
            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
