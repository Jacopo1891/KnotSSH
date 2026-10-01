package com.knotssh.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.domain.model.Credential
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
                onSearchExpandToggle = { searchExpanded = !searchExpanded },
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onNavigateCredentials = onNavigateCredentials,
                onNavigateSettings = onNavigateSettings
            )
        },
        floatingActionButton = {
            // L'empty state mostra già un pulsante centrale per aggiungere il primo server.
            val showEmptyStateAddButton =
                !uiState.isLoading && uiState.servers.isEmpty() && uiState.searchQuery.isBlank()
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
                uiState.servers.isEmpty() -> {
                    EmptyState(
                        hasSearch = uiState.searchQuery.isNotBlank(),
                        onAddServer = onAddServer,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ServerList(
                        servers = uiState.servers,
                        credentials = uiState.credentials,
                        activeSessions = uiState.activeSessions,
                        onConnect = onConnect,
                        onEdit = onEditServer,
                        onDelete = { serverToDelete = it },
                        onToggleFavorite = { s -> viewModel.toggleFavorite(s.id, s.isFavorite) }
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
    onSearchExpandToggle: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onNavigateCredentials: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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

@Composable
private fun ServerList(
    servers: List<Server>,
    credentials: Map<Long, Credential>,
    activeSessions: Set<Long>,
    onConnect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Server) -> Unit,
    onToggleFavorite: (Server) -> Unit
) {
    // Separate favorites and recents
    val favorites = servers.filter { it.isFavorite }
    val recents = servers.filter { !it.isFavorite }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (favorites.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_favorites), icon = Icons.Default.Star)
            }
            items(favorites, key = { it.id }) { server ->
                ServerItemWithSwipe(
                    server = server,
                    credential = credentials[server.credentialId],
                    hasActiveSession = server.id in activeSessions,
                    onConnect = { onConnect(server.id) },
                    onEdit = { onEdit(server.id) },
                    onDelete = { onDelete(server) },
                    onToggleFavorite = { onToggleFavorite(server) }
                )
            }
        }

        if (recents.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recents),
                    icon = Icons.Default.History,
                    modifier = Modifier.padding(top = if (favorites.isNotEmpty()) 8.dp else 0.dp)
                )
            }
            items(recents, key = { it.id }) { server ->
                ServerItemWithSwipe(
                    server = server,
                    credential = credentials[server.credentialId],
                    hasActiveSession = server.id in activeSessions,
                    onConnect = { onConnect(server.id) },
                    onEdit = { onEdit(server.id) },
                    onDelete = { onDelete(server) },
                    onToggleFavorite = { onToggleFavorite(server) }
                )
            }
        }

        // Bottom padding for FAB
        item { Spacer(modifier = Modifier.height(80.dp)) }
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
private fun ServerItemWithSwipe(
    server: Server,
    credential: Credential?,
    hasActiveSession: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    SwipeToDeleteContainer(onDelete = { onDelete(); false }) {
        ServerCard(
            server = server,
            credential = credential,
            hasActiveSession = hasActiveSession,
            onConnect = onConnect,
            onFixCredential = onEdit,
            onLongPress = { showContextMenu = true },
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showContextMenu) {
        ServerContextMenu(
            server = server,
            onDismiss = { showContextMenu = false },
            onConnect = { showContextMenu = false; onConnect() },
            onEdit = { showContextMenu = false; onEdit() },
            onDelete = { showContextMenu = false; onDelete() },
            onToggleFavorite = { showContextMenu = false; onToggleFavorite() }
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
    onLongPress: () -> Unit,
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
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
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
