package com.knotssh.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.data.local.preferences.ServerSortMode
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.Folder
import com.knotssh.domain.model.Server
import com.knotssh.presentation.components.SwipeToDeleteContainer
import com.knotssh.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onConnect: (Long) -> Unit,
    onNavigateCredentials: () -> Unit,
    onNavigateSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<Server?>(null) }
    var serverToMove by remember { mutableStateOf<Server?>(null) }
    var folderToFill by remember { mutableStateOf<Folder?>(null) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }

    folderToRename?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onConfirm = { name ->
                viewModel.renameFolder(folder.id, name)
                folderToRename = null
            },
            onDismiss = { folderToRename = null }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.folder_delete_title)) },
            text = { Text(stringResource(R.string.folder_delete_message, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.id)
                    folderToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    folderToFill?.let { folder ->
        // Re-read it from the list so the checkmarks follow the edits instead of a stale copy.
        val current = uiState.folders.firstOrNull { it.id == folder.id }
        if (current == null) {
            folderToFill = null
        } else {
            FolderContentsDialog(
                folder = current,
                servers = uiState.allServers,
                onToggle = { server ->
                    val target = if (server.folderId == current.id) null else current.id
                    viewModel.moveServerToFolder(server.id, target)
                },
                onDismiss = { folderToFill = null }
            )
        }
    }

    if (showFolderManager) {
        FolderManagerDialog(
            folders = uiState.folders,
            onCreate = viewModel::createFolder,
            onRename = viewModel::renameFolder,
            onDelete = viewModel::deleteFolder,
            onMove = viewModel::moveFolder,
            onDismiss = { showFolderManager = false }
        )
    }

    serverToMove?.let { server ->
        MoveToFolderDialog(
            server = server,
            folders = uiState.folders,
            onSelect = { folderId ->
                viewModel.moveServerToFolder(server.id, folderId)
                serverToMove = null
            },
            onDismiss = { serverToMove = null }
        )
    }

    // Confirmation dialog for delete
    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.home_delete_title)) },
            text = { Text(stringResource(R.string.home_delete_message, server.alias)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteServer(server.id)
                    serverToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                searchExpanded = searchExpanded,
                searchQuery = uiState.searchQuery,
                sortMode = uiState.sortMode,
                foldersEnabled = uiState.foldersEnabled,
                onSearchExpandToggle = { searchExpanded = !searchExpanded },
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSortModeSelected = viewModel::setSortMode,
                onFoldersEnabledChange = viewModel::setFoldersEnabled,
                onManageFolders = { showFolderManager = true },
                onNavigateCredentials = onNavigateCredentials,
                onNavigateSettings = onNavigateSettings
            )
        },
        floatingActionButton = {
            // L'empty state mostra già un pulsante centrale per aggiungere il primo server.
            val showEmptyStateAddButton =
                !uiState.isLoading && uiState.totalServers == 0 && uiState.searchQuery.isBlank()
            if (!showEmptyStateAddButton) {
                ExtendedFloatingActionButton(
                    onClick = onAddServer,
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.home_add_server)
                        )
                    },
                    text = { Text(stringResource(R.string.home_new_server)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                !uiState.hasVisibleServers -> {
                    EmptyState(
                        hasSearch = uiState.isSearching,
                        onAddServer = onAddServer,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ServerList(
                        state = uiState,
                        onConnect = onConnect,
                        onEdit = onEditServer,
                        onDelete = { serverToDelete = it },
                        onToggleFavorite = { s -> viewModel.toggleFavorite(s.id, s.isFavorite) },
                        onToggleFolder = viewModel::toggleFolder,
                        onMoveToFolder = { serverToMove = it },
                        onEnableReorder = { viewModel.setSortMode(ServerSortMode.MANUAL) },
                        onEditFolderContents = { folderToFill = it },
                        onRenameFolder = { folderToRename = it },
                        onDeleteFolder = { folderToDelete = it },
                        onServerDragStart = viewModel::beginServerDrag,
                        onServerDragStep = viewModel::moveServerStep,
                        onServerDragFinish = { committed ->
                            if (committed) viewModel.endServerDrag() else viewModel.cancelServerDrag()
                        },
                        onFolderDragStep = viewModel::moveFolderStep
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    searchExpanded: Boolean,
    searchQuery: String,
    sortMode: ServerSortMode,
    foldersEnabled: Boolean,
    onSearchExpandToggle: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSortModeSelected: (ServerSortMode) -> Unit,
    onFoldersEnabledChange: (Boolean) -> Unit,
    onManageFolders: () -> Unit,
    onNavigateCredentials: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                AnimatedContent(
                    targetState = searchExpanded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "title_anim"
                ) { expanded ->
                    if (expanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text(stringResource(R.string.home_search_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_monochrome),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                // The knot only fills the adaptive-icon safe zone, so the box
                                // must be oversized for the glyph to read at title scale.
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "KnotSSH",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = {
                    onSearchExpandToggle()
                    if (searchExpanded) onSearchQueryChanged("")
                }) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchExpanded) {
                            stringResource(R.string.home_close_search)
                        } else {
                            stringResource(R.string.home_search)
                        }
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.home_sort)
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SORT_MODES.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = {
                                    if (mode == sortMode) Icon(Icons.Default.Check, null)
                                },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortModeSelected(mode)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_use_folders)) },
                            leadingIcon = {
                                Icon(
                                    if (foldersEnabled) Icons.Default.CheckBox
                                    else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                sortMenuExpanded = false
                                onFoldersEnabledChange(!foldersEnabled)
                            }
                        )
                        if (foldersEnabled) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_manage_folders)) },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onManageFolders()
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onNavigateCredentials) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = stringResource(R.string.credentials_title)
                    )
                }
                IconButton(onClick = onNavigateSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

private val SORT_MODES = listOf(
    ServerSortMode.LAST_USED to R.string.home_sort_last_used,
    ServerSortMode.NAME to R.string.home_sort_name,
    ServerSortMode.ADDED to R.string.home_sort_added,
    ServerSortMode.MANUAL to R.string.home_sort_manual
)

@Composable
private fun ServerList(
    state: HomeUiState,
    onConnect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Server) -> Unit,
    onToggleFavorite: (Server) -> Unit,
    onToggleFolder: (Folder) -> Unit,
    onMoveToFolder: (Server) -> Unit,
    onEnableReorder: () -> Unit,
    onEditFolderContents: (Folder) -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onServerDragStart: (Long) -> Unit,
    onServerDragStep: (Long, Int) -> Unit,
    onServerDragFinish: (Boolean) -> Unit,
    onFolderDragStep: (Long, Int) -> Unit
) {
    val reorderable = state.isReorderable

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (reorderable) {
            item(key = "reorder_hint") {
                Text(
                    text = stringResource(R.string.home_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Favourites are a shortcut, not a location: the same servers stay in their folder below.
        if (state.favorites.isNotEmpty() && !state.isSearching) {
            item(key = "header_favorites") {
                SectionHeader(
                    title = stringResource(R.string.home_favorites),
                    icon = Icons.Default.Star
                )
            }
            items(state.favorites, key = { "fav_${it.id}" }) { server ->
                ServerRow(
                    server = server,
                    state = state,
                    onConnect = onConnect,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onToggleFavorite = onToggleFavorite,
                    onMoveToFolder = onMoveToFolder,
                    onEnableReorder = onEnableReorder
                )
            }
        }

        state.visibleGroups.forEach { group ->
            val folder = group.folder

            item(key = "header_${group.key}") {
                when {
                    // With folders off there is a single section, so a header would say nothing.
                    !state.foldersEnabled -> Spacer(Modifier.height(0.dp))
                    folder == null -> SectionHeader(
                        title = stringResource(R.string.home_no_folder),
                        icon = Icons.Default.Dns,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    else -> FolderHeader(
                        folder = folder,
                        count = group.servers.size,
                        reorderable = reorderable,
                        onToggle = { onToggleFolder(folder) },
                        onEditContents = { onEditFolderContents(folder) },
                        onRename = { onRenameFolder(folder) },
                        onDelete = { onDeleteFolder(folder) },
                        onDragStep = { step -> onFolderDragStep(folder.id, step) }
                    )
                }
            }

            if (folder?.isExpanded == false && !state.isSearching) return@forEach

            if (group.servers.isEmpty()) {
                item(key = "empty_${group.key}") {
                    if (state.foldersEnabled) {
                        EmptyGroupHint(isFolder = folder != null, reorderable = reorderable)
                    }
                }
                return@forEach
            }

            // Keyed by server alone: a key that included the section would make Compose discard
            // and rebuild the row when it crosses a folder, killing the drag gesture mid-flight.
            items(group.servers, key = { "srv_${it.id}" }) { server ->
                ServerRow(
                    server = server,
                    state = state,
                    onConnect = onConnect,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onToggleFavorite = onToggleFavorite,
                    onMoveToFolder = onMoveToFolder,
                    onEnableReorder = onEnableReorder,
                    draggable = reorderable,
                    onDragStart = { onServerDragStart(server.id) },
                    onDragStep = { step -> onServerDragStep(server.id, step) },
                    onDragFinish = onServerDragFinish
                )
            }
        }

        // Bottom padding for FAB
        item(key = "fab_spacer") { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun EmptyGroupHint(isFolder: Boolean, reorderable: Boolean) {
    Text(
        text = stringResource(
            when {
                !isFolder -> R.string.home_no_folder_empty
                reorderable -> R.string.home_folder_empty_drag
                else -> R.string.home_folder_empty
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun LazyItemScope.ServerRow(
    server: Server,
    state: HomeUiState,
    onConnect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Server) -> Unit,
    onToggleFavorite: (Server) -> Unit,
    onMoveToFolder: (Server) -> Unit,
    onEnableReorder: () -> Unit = {},
    draggable: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragStep: (Int) -> Unit = {},
    onDragFinish: (Boolean) -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val drag = rememberDragToReorder(
        stepHeightPx = { rowHeight.toFloat() },
        onStart = onDragStart,
        onStep = onDragStep,
        onFinish = onDragFinish,
        onPressWithoutMove = { showContextMenu = true }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (drag.isDragging) Modifier.zIndex(1f) else Modifier.animateItem())
            .liftWhileDragging(drag)
            .onSizeChanged { rowHeight = it.height },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerSwipeCard(
            server = server,
            credential = state.credentials[server.credentialId],
            hasActiveSession = server.id in state.activeSessions,
            onConnect = { onConnect(server.id) },
            onEdit = { onEdit(server.id) },
            onDelete = { onDelete(server) },
            onToggleFavorite = { onToggleFavorite(server) },
            // While reordering the long press is the grab gesture, so it cannot also open a menu.
            onLongPress = if (draggable) null else ({ showContextMenu = true }),
            gestureModifier = if (draggable) drag.modifier else Modifier
        )
    }

    if (showContextMenu) {
        ServerContextMenu(
            server = server,
            foldersEnabled = state.foldersEnabled,
            canReorder = state.isReorderable,
            onDismiss = { showContextMenu = false },
            onConnect = { showContextMenu = false; onConnect(server.id) },
            onEdit = { showContextMenu = false; onEdit(server.id) },
            onDelete = { showContextMenu = false; onDelete(server) },
            onToggleFavorite = { showContextMenu = false; onToggleFavorite(server) },
            onMoveToFolder = { showContextMenu = false; onMoveToFolder(server) },
            onEnableReorder = { showContextMenu = false; onEnableReorder() }
        )
    }
}

/** Drag state shared by server rows and folder headers. */
@Stable
private class DragToReorder(
    val modifier: Modifier,
    isDragging: () -> Boolean,
    offset: () -> Float
) {
    val isDraggingProvider = isDragging
    val offsetProvider = offset
    val isDragging: Boolean get() = isDraggingProvider()
}

/**
 * Picks the item up on a long press and emits one reorder step every time the finger travels a
 * full slot. The leftover travel is kept as [DragToReorder.offsetProvider] so the row can follow
 * the finger: after a step the list has already moved the row by exactly one slot, which is what
 * makes the residual the correct visual offset.
 */
@Composable
private fun rememberDragToReorder(
    stepHeightPx: () -> Float,
    onStep: (Int) -> Unit,
    onStart: () -> Unit = {},
    onFinish: (committed: Boolean) -> Unit = {},
    onPressWithoutMove: () -> Unit = {}
): DragToReorder {
    val step by rememberUpdatedState(onStep)
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    val press by rememberUpdatedState(onPressWithoutMove)
    val height by rememberUpdatedState(stepHeightPx)
    val dragging = remember { mutableStateOf(false) }
    val offset = remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current

    val modifier = remember {
        Modifier.pointerInput(Unit) {
            var moved = false
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    moved = false
                    dragging.value = true
                    offset.floatValue = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    start()
                },
                onDragEnd = {
                    if (moved) finish(true) else press()
                    dragging.value = false
                    offset.floatValue = 0f
                },
                onDragCancel = {
                    if (moved) finish(false)
                    dragging.value = false
                    offset.floatValue = 0f
                }
            ) { change, amount ->
                change.consume()
                if (amount.y != 0f) moved = true
                val threshold = height().takeIf { it > 0f } ?: return@detectDragGesturesAfterLongPress
                offset.floatValue += amount.y
                while (offset.floatValue >= threshold) {
                    offset.floatValue -= threshold
                    step(1)
                }
                while (offset.floatValue <= -threshold) {
                    offset.floatValue += threshold
                    step(-1)
                }
            }
        }
    }

    return remember {
        DragToReorder(
            modifier = modifier,
            isDragging = { dragging.value },
            offset = { offset.floatValue }
        )
    }
}

/** Reads the offset at draw time, so following the finger never costs a recomposition. */
private fun Modifier.liftWhileDragging(drag: DragToReorder): Modifier = graphicsLayer {
    translationY = drag.offsetProvider()
    val lifted = drag.isDraggingProvider()
    val scale = if (lifted) 1.02f else 1f
    scaleX = scale
    scaleY = scale
    shadowElevation = if (lifted) 12.dp.toPx() else 0f
    shape = RoundedCornerShape(16.dp)
    clip = false
}

@Composable
private fun LazyItemScope.FolderHeader(
    folder: Folder,
    count: Int,
    reorderable: Boolean,
    onToggle: () -> Unit,
    onEditContents: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDragStep: (Int) -> Unit
) {
    var headerHeight by remember { mutableIntStateOf(0) }
    // A folder step is one folder, not one row: the threshold is this header's own height.
    val drag = rememberDragToReorder(
        stepHeightPx = { headerHeight.toFloat() },
        onStep = onDragStep
    )

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (drag.isDragging) Modifier.zIndex(1f) else Modifier.animateItem())
            .padding(top = 8.dp)
            .liftWhileDragging(drag)
            .onSizeChanged { headerHeight = it.height }
            .then(if (reorderable) drag.modifier else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (folder.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.folder_actions),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_pick_servers)) },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                        onClick = { menuExpanded = false; onEditContents() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.folder_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
            Icon(
                imageVector = if (folder.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ServerSwipeCard(
    server: Server,
    credential: Credential?,
    hasActiveSession: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: (() -> Unit)?,
    gestureModifier: Modifier
) {
    SwipeToDeleteContainer(onDelete = { onDelete(); false }) {
        ServerCard(
            server = server,
            credential = credential,
            hasActiveSession = hasActiveSession,
            onConnect = onConnect,
            onFixCredential = onEdit,
            onLongPress = onLongPress,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .then(gestureModifier)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    server: Server,
    credential: Credential?,
    hasActiveSession: Boolean,
    onConnect: () -> Unit,
    onFixCredential: () -> Unit,
    onLongPress: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The credential it referenced was deleted: connecting would only fail, so the card sends
    // the user to the editor instead.
    val orphaned = credential == null
    val accentColor = if (orphaned) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = if (orphaned) onFixCredential else onConnect,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent indicator strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.4f))
                        )
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Server icon
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (hasActiveSession) {
                    // Sits outside the icon's clip so the ring reads against any background.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(SshGreen)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.alias,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ServerChip(
                        icon = Icons.Default.Language,
                        text = "${server.hostname}:${server.port}"
                    )
                    if (credential != null) {
                        ServerChip(
                            icon = Icons.Default.Person,
                            text = credential.username
                        )
                    } else {
                        ServerChip(
                            icon = Icons.Default.PersonOff,
                            text = stringResource(R.string.home_no_credential),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                server.lastConnectedMs?.let { ms ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_last_connection, formatLastConnected(ms)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (server.portForwardRules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.port_forward_rules,
                            server.portForwardRules.size,
                            server.portForwardRules.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Actions column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (server.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (server.isFavorite) {
                            stringResource(R.string.action_unfavorite)
                        } else {
                            stringResource(R.string.action_favorite)
                        },
                        tint = if (server.isFavorite) SshAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Connect button
                FilledIconButton(
                    onClick = if (orphaned) onFixCredential else onConnect,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor
                    )
                ) {
                    Icon(
                        imageVector = if (orphaned) Icons.Default.Edit else Icons.Default.Terminal,
                        contentDescription = stringResource(
                            if (orphaned) R.string.action_edit else R.string.action_connect
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = content
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerContextMenu(
    server: Server,
    foldersEnabled: Boolean,
    canReorder: Boolean,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToFolder: () -> Unit,
    onEnableReorder: () -> Unit
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_connect)) },
            leadingIcon = { Icon(Icons.Default.Terminal, null) },
            onClick = onConnect
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (server.isFavorite) {
                        stringResource(R.string.action_unfavorite)
                    } else {
                        stringResource(R.string.action_favorite)
                    }
                )
            },
            leadingIcon = {
                Icon(
                    if (server.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                    null
                )
            },
            onClick = onToggleFavorite
        )
        if (foldersEnabled) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_move_to_folder)) },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                onClick = onMoveToFolder
            )
        }
        // Dragging is tied to custom ordering, so offer the switch where the user looks for it.
        if (!canReorder) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_enable_reorder)) },
                leadingIcon = { Icon(Icons.Default.SwapVert, null) },
                onClick = onEnableReorder
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = onDelete
        )
    }
}

@Composable
private fun EmptyState(
    hasSearch: Boolean,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Default.SearchOff else Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = if (hasSearch) {
                stringResource(R.string.home_empty_search_title)
            } else {
                stringResource(R.string.home_empty_title)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (hasSearch) {
                stringResource(R.string.home_empty_search_message)
            } else {
                stringResource(R.string.home_empty_message)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!hasSearch) {
            FilledTonalButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_server))
            }
        }
    }
}

@Composable
private fun formatLastConnected(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> stringResource(R.string.time_now)
        diff < 3_600_000 -> stringResource(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> stringResource(R.string.time_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> {
            val days = (diff / 86_400_000).toInt()
            pluralStringResource(R.plurals.days_ago, days, days)
        }
        else -> remember(ms) {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
        }
    }
}
