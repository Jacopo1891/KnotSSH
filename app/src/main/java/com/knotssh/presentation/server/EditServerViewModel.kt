package com.knotssh.presentation.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.domain.model.*
import com.knotssh.domain.repository.CredentialRepository
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
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    // Validation errors
    val aliasError: String? = null,
    val hostnameError: String? = null,
    val portError: String? = null,
    val credentialError: String? = null,
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: Long? = savedStateHandle.get<Long>("serverId")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(EditServerUiState())
    val state: StateFlow<EditServerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load credentials list
            credentialRepository.getAllCredentials().collect { creds ->
                _state.update { it.copy(credentials = creds) }
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
                            portForwardRules = server.portForwardRules
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

    fun addPortForwardRule(rule: PortForwardRule) = _state.update {
        it.copy(portForwardRules = it.portForwardRules + rule)
    }

    fun removePortForwardRule(rule: PortForwardRule) = _state.update {
        it.copy(portForwardRules = it.portForwardRules - rule)
    }

    fun save() {
        val s = _state.value
        var hasError = false

        if (s.alias.isBlank()) { _state.update { it.copy(aliasError = "Campo obbligatorio") }; hasError = true }
        if (s.hostname.isBlank()) { _state.update { it.copy(hostnameError = "Campo obbligatorio") }; hasError = true }
        val portInt = s.port.toIntOrNull()
        if (portInt == null || portInt !in 1..65535) { _state.update { it.copy(portError = "Porta non valida (1-65535)") }; hasError = true }
        if (s.selectedCredentialId == null) { _state.update { it.copy(credentialError = "Seleziona una credenziale") }; hasError = true }
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
                portForwardRules = s.portForwardRules
            )
            if (serverId != null) serverRepository.updateServer(server)
            else serverRepository.insertServer(server)
            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
