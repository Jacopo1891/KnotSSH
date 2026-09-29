package com.knotssh.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.Server
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.ServerRepository
import com.knotssh.ssh.SessionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val servers: List<Server> = emptyList(),
    val credentials: Map<Long, Credential> = emptyMap(),
    val activeSessions: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    sessionRegistry: SessionRegistry
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        serverRepository.getRecentServers(50),
        credentialRepository.getAllCredentials(),
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        sessionRegistry.active
    ) { servers, credentialList, query, sessions ->
        val credentials = credentialList.associateBy { it.id }
        val filtered = if (query.isBlank()) servers
        else servers.filter { server ->
            server.alias.contains(query, ignoreCase = true) ||
                    server.hostname.contains(query, ignoreCase = true) ||
                    credentials[server.credentialId]?.username?.contains(query, ignoreCase = true) == true
        }
        HomeUiState(
            servers = filtered,
            credentials = credentials,
            activeSessions = sessions.map { it.serverId }.toSet(),
            isLoading = false,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverRepository.deleteServer(serverId)
        }
    }

    fun toggleFavorite(serverId: Long, current: Boolean) {
        viewModelScope.launch {
            serverRepository.toggleFavorite(serverId, !current)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
