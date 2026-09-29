package com.knotssh.presentation.credentials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.SshKeyType
import com.knotssh.domain.repository.CredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CredentialsUiState(
    val credentials: List<Credential> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CredentialsViewModel @Inject constructor(
    private val repository: CredentialRepository
) : ViewModel() {

    val uiState: StateFlow<CredentialsUiState> = repository.getAllCredentials()
        .map { CredentialsUiState(credentials = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CredentialsUiState())

    fun deleteCredential(id: Long) {
        viewModelScope.launch { repository.deleteCredential(id) }
    }
}

data class EditCredentialUiState(
    val alias: String = "",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val secret: String = "",
    val passphrase: String = "",
    val publicKey: String? = null,
    val keyType: SshKeyType? = null,
    val maskSecrets: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val aliasError: String? = null,
    val usernameError: String? = null,
    val secretError: String? = null
)

@HiltViewModel
class EditCredentialViewModel @Inject constructor(
    private val repository: CredentialRepository,
    appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val credentialId: Long? = savedStateHandle.get<Long>("credentialId")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(EditCredentialUiState())
    val state: StateFlow<EditCredentialUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.security.collect { security ->
                _state.update { it.copy(maskSecrets = security.maskSecretsInUi) }
            }
        }
        credentialId?.let { id ->
            viewModelScope.launch {
                repository.getCredentialById(id)?.let { credential ->
                    _state.update {
                        it.copy(
                            alias = credential.alias,
                            username = credential.username,
                            authType = credential.authType,
                            secret = repository.decryptSecret(credential),
                            passphrase = repository.decryptPassphrase(credential).orEmpty(),
                            publicKey = credential.publicKey,
                            keyType = credential.keyType
                        )
                    }
                }
            }
        }
    }

    fun onAliasChanged(value: String) = _state.update { it.copy(alias = value, aliasError = null) }
    fun onUsernameChanged(value: String) = _state.update { it.copy(username = value, usernameError = null) }
    fun onAuthTypeChanged(value: AuthType) = _state.update { it.copy(authType = value, secretError = null) }
    fun onSecretChanged(value: String) = _state.update { it.copy(secret = value, secretError = null) }
    fun onPassphraseChanged(value: String) = _state.update { it.copy(passphrase = value) }

    fun save() {
        val current = _state.value
        var hasError = false
        if (current.alias.isBlank()) {
            _state.update { it.copy(aliasError = "Campo obbligatorio") }
            hasError = true
        }
        if (current.username.isBlank()) {
            _state.update { it.copy(usernameError = "Campo obbligatorio") }
            hasError = true
        }
        if (current.secret.isBlank()) {
            _state.update { it.copy(secretError = "Campo obbligatorio") }
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.save(
                credential = Credential(
                    id = credentialId ?: 0L,
                    alias = current.alias.trim(),
                    username = current.username.trim(),
                    authType = current.authType,
                    publicKey = current.publicKey,
                    keyType = current.keyType
                ),
                plainSecret = current.secret,
                plainPassphrase = current.passphrase.takeIf { current.authType == AuthType.SSH_KEY }
            )
            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
