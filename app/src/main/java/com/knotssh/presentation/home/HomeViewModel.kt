package com.knotssh.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.ServerSortMode
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.Folder
import com.knotssh.domain.model.Server
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.FolderRepository
import com.knotssh.domain.repository.ServerRepository
import com.knotssh.ssh.SessionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One section of the list: a folder, or the catch-all for servers that have none. */
data class ServerGroup(
    val folder: Folder?,
    val servers: List<Server>
) {
    val key: Long get() = folder?.id ?: NO_FOLDER_KEY

    companion object {
        const val NO_FOLDER_KEY = -1L
    }
}

data class HomeUiState(
    val favorites: List<Server> = emptyList(),
    val groups: List<ServerGroup> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val allServers: List<Server> = emptyList(),
    val credentials: Map<Long, Credential> = emptyMap(),
    val activeSessions: Set<Long> = emptySet(),
    val sortMode: ServerSortMode = ServerSortMode.LAST_USED,
    val foldersEnabled: Boolean = true,
    val draggingServerId: Long? = null,
    val totalServers: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = ""
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()

    /** Dragging is only offered when the order on screen is the user's own. */
    val isReorderable: Boolean get() = sortMode == ServerSortMode.MANUAL && !isSearching

    /**
     * Sections actually rendered, in list order. The drag logic walks this same list, so what the
     * user sees and what a drag step lands on can never disagree.
     */
    val visibleGroups: List<ServerGroup>
        get() = groups.filter { group ->
            when {
                isSearching -> group.servers.isNotEmpty()
                // Folders stay visible when empty, otherwise creating one would look broken.
                group.folder != null -> true
                // The catch-all is kept while dragging so a server can be pulled out of a folder.
                else -> group.servers.isNotEmpty() || isReorderable
            }
        }

    val hasVisibleServers: Boolean get() = groups.any { it.servers.isNotEmpty() }
}

/** Where the dragged server would land if the finger were lifted right now. */
private data class DragPlacement(
    val serverId: Long,
    val originKey: Long,
    val groupKey: Long,
    val index: Int
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    private val folderRepository: FolderRepository,
    private val preferences: AppPreferences,
    sessionRegistry: SessionRegistry
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _drag = MutableStateFlow<DragPlacement?>(null)

    private val listConfig = combine(
        preferences.serverSortMode,
        preferences.foldersEnabled,
        folderRepository.getAll()
    ) { mode, foldersEnabled, folders ->
        Triple(mode, foldersEnabled, if (foldersEnabled) folders else emptyList())
    }

    private val storedState: Flow<HomeUiState> = combine(
        serverRepository.getAllServers(),
        credentialRepository.getAllCredentials(),
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        sessionRegistry.active,
        listConfig
    ) { servers, credentialList, query, sessions, config ->
        val (sortMode, foldersEnabled, folders) = config
        val credentials = credentialList.associateBy { it.id }
        val matching = servers.filter { it.matches(query, credentials) }
        val knownFolderIds = folders.mapTo(mutableSetOf()) { it.id }

        val groups = folders.map { folder ->
            ServerGroup(folder, matching.filter { it.folderId == folder.id }.ordered(sortMode))
        } + ServerGroup(
            folder = null,
            // Also catches rows left pointing at a folder that no longer exists.
            servers = matching
                .filter { it.folderId == null || it.folderId !in knownFolderIds }
                .ordered(sortMode)
        )

        HomeUiState(
            favorites = matching.filter { it.isFavorite }.ordered(sortMode),
            groups = groups,
            folders = folders,
            allServers = servers.ordered(sortMode),
            credentials = credentials,
            activeSessions = sessions.map { it.serverId }.toSet(),
            sortMode = sortMode,
            foldersEnabled = foldersEnabled,
            totalServers = servers.size,
            isLoading = false,
            searchQuery = query
        )
    }

    // The drag is folded in last so the preview never reaches the database until the finger lifts.
    private val storedSnapshot: StateFlow<HomeUiState> =
        storedState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(storedSnapshot, _drag) { state, drag ->
        state.withPreview(drag)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Preview as of right now. Drag steps must not read [uiState]: it is emitted asynchronously,
     * so two pointer events in the same frame would both be computed against a stale list.
     */
    private fun previewNow(placement: DragPlacement?) = storedSnapshot.value.withPreview(placement)

    private fun HomeUiState.withPreview(drag: DragPlacement?): HomeUiState {
        if (drag == null) return this
        val dragged = groups.firstNotNullOfOrNull { group ->
            group.servers.firstOrNull { it.id == drag.serverId }
        } ?: return this

        val previewed = groups.map { group ->
            val without = group.servers.filterNot { it.id == drag.serverId }
            when (group.key) {
                drag.groupKey -> {
                    val servers = without.toMutableList()
                    servers.add(drag.index.coerceIn(0, servers.size), dragged)
                    // Opened so the insertion point is visible while the finger is still down.
                    group.copy(folder = group.folder?.copy(isExpanded = true), servers = servers)
                }
                else -> group.copy(servers = without)
            }
        }
        return copy(groups = previewed, draggingServerId = drag.serverId)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch { serverRepository.deleteServer(serverId) }
    }

    fun toggleFavorite(serverId: Long, current: Boolean) {
        viewModelScope.launch { serverRepository.toggleFavorite(serverId, !current) }
    }

    fun setSortMode(mode: ServerSortMode) {
        viewModelScope.launch { preferences.setServerSortMode(mode) }
    }

    fun toggleFolder(folder: Folder) {
        viewModelScope.launch { folderRepository.setExpanded(folder.id, !folder.isExpanded) }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { folderRepository.create(name) }
    }

    fun renameFolder(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { folderRepository.rename(id, name) }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch { folderRepository.delete(id) }
    }

    fun moveFolder(from: Int, to: Int) {
        val ordered = uiState.value.folders.map { it.id }.toMutableList()
        if (from !in ordered.indices || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        viewModelScope.launch { folderRepository.applyOrder(ordered) }
    }

    /** One drag step on a folder header: swap it with the neighbouring folder. */
    fun moveFolderStep(folderId: Long, direction: Int) {
        val ordered = uiState.value.folders.map { it.id }
        val from = ordered.indexOf(folderId)
        if (from < 0) return
        moveFolder(from, from + direction)
    }

    fun moveServerToFolder(serverId: Long, folderId: Long?) {
        viewModelScope.launch { serverRepository.setFolder(serverId, folderId) }
    }

    /**
     * One drag step on a server: swap with the next row of its own section, or cross the section
     * boundary into the neighbouring one. Nothing is written yet — a step only moves the preview,
     * so passing over a folder on the way somewhere else leaves no trace.
     */
    fun moveServerStep(serverId: Long, direction: Int) {
        val placement = _drag.value?.takeIf { it.serverId == serverId } ?: return
        val groups = previewNow(placement).visibleGroups
        val groupIndex = groups.indexOfFirst { it.key == placement.groupKey }
        if (groupIndex < 0) return

        val group = groups[groupIndex]
        val movingDown = direction > 0
        val staysInGroup =
            if (movingDown) placement.index < group.servers.lastIndex else placement.index > 0

        _drag.value = if (staysInGroup) {
            placement.copy(index = placement.index + direction)
        } else {
            val neighbour = groups.getOrNull(groupIndex + direction) ?: return
            placement.copy(
                groupKey = neighbour.key,
                // The preview already removed the server from this group, so its size is the end.
                index = if (movingDown) 0 else neighbour.servers.size
            )
        }
    }

    fun beginServerDrag(serverId: Long) {
        val state = storedSnapshot.value
        if (!state.isReorderable) return
        val group = state.visibleGroups
            .firstOrNull { group -> group.servers.any { it.id == serverId } } ?: return
        _drag.value = DragPlacement(
            serverId = serverId,
            originKey = group.key,
            groupKey = group.key,
            index = group.servers.indexOfFirst { it.id == serverId }
        )
    }

    /** Commits the preview. Only now does the move reach the database. */
    fun endServerDrag() {
        val placement = _drag.value ?: return
        val state = previewNow(placement)
        val target = state.groups.firstOrNull { it.key == placement.groupKey }
        if (target == null) {
            _drag.value = null
            return
        }
        val origin = state.groups.firstOrNull { it.key == placement.originKey }
        val targetFolder = state.folders.firstOrNull { it.id == placement.groupKey }

        viewModelScope.launch {
            if (placement.originKey != placement.groupKey) {
                serverRepository.setFolder(placement.serverId, target.folder?.id)
                origin?.let { serverRepository.applyOrder(it.servers.map { s -> s.id }) }
                // Dropping into a collapsed folder would otherwise look like the server vanished.
                targetFolder?.takeIf { !it.isExpanded }
                    ?.let { folderRepository.setExpanded(it.id, true) }
            }
            serverRepository.applyOrder(target.servers.map { it.id })
            // Cleared last, so the list never flashes back to the old order mid-write.
            _drag.value = null
        }
    }

    fun cancelServerDrag() {
        _drag.value = null
    }

    fun setFoldersEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setFoldersEnabled(enabled) }
    }

    private fun Server.matches(query: String, credentials: Map<Long, Credential>): Boolean {
        if (query.isBlank()) return true
        return alias.contains(query, ignoreCase = true) ||
            hostname.contains(query, ignoreCase = true) ||
            credentials[credentialId]?.username?.contains(query, ignoreCase = true) == true
    }

    private fun List<Server>.ordered(mode: ServerSortMode): List<Server> = when (mode) {
        ServerSortMode.LAST_USED -> sortedWith(
            compareByDescending<Server> { it.lastConnectedMs ?: Long.MIN_VALUE }
                .thenBy { it.alias.lowercase() }
        )
        ServerSortMode.NAME -> sortedBy { it.alias.lowercase() }
        ServerSortMode.ADDED -> sortedByDescending { it.id }
        ServerSortMode.MANUAL -> sortedWith(compareBy<Server> { it.sortOrder }.thenBy { it.id })
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
