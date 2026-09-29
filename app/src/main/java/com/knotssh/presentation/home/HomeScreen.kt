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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            title = { Text("Elimina connessione") },
            text = { Text("Vuoi eliminare \"${server.alias}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteServer(server.id)
                    serverToDelete = null
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Annulla") }
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
            ExtendedFloatingActionButton(
                onClick = onAddServer,
                icon = { Icon(Icons.Default.Add, contentDescription = "Aggiungi server") },
                text = { Text("Nuovo server") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
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
                            placeholder = { Text("Cerca server…") },
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
                        Text(
                            text = "KnotSSH",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                        contentDescription = if (searchExpanded) "Chiudi ricerca" else "Cerca"
                    )
                }
                IconButton(onClick = onNavigateCredentials) {
                    Icon(Icons.Default.Key, contentDescription = "Credenziali")
                }
                IconButton(onClick = onNavigateSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
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
                SectionHeader(title = "Preferiti", icon = Icons.Default.Star)
            }
            items(favorites, key = { it.id }) { server ->
                ServerItemWithSwipe(
                    server = server,
                    credential = credentials[server.credentialId],
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
                    title = "Recenti",
                    icon = Icons.Default.History,
                    modifier = Modifier.padding(top = if (favorites.isNotEmpty()) 8.dp else 0.dp)
                )
            }
            items(recents, key = { it.id }) { server ->
                ServerItemWithSwipe(
                    server = server,
                    credential = credentials[server.credentialId],
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
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    SwipeToDeleteContainer(onDelete = onDelete) {
        ServerCard(
            server = server,
            credential = credential,
            onConnect = onConnect,
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
    onConnect: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onConnect,
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
                    }
                }
                server.lastConnectedMs?.let { ms ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ultima connessione: ${formatLastConnected(ms)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (server.portForwardRules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${server.portForwardRules.size} regola/e port forward",
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
                        contentDescription = if (server.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (server.isFavorite) SshAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Connect button
                FilledIconButton(
                    onClick = onConnect,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Connetti",
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
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text = { Text("Connetti") },
            leadingIcon = { Icon(Icons.Default.Terminal, null) },
            onClick = onConnect
        )
        DropdownMenuItem(
            text = { Text("Modifica") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = { Text(if (server.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") },
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
            text = { Text("Elimina", color = MaterialTheme.colorScheme.error) },
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
            text = if (hasSearch) "Nessun risultato" else "Nessuna connessione",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (hasSearch) "Prova con un termine diverso"
            else "Aggiungi il tuo primo server SSH\ncon il pulsante in basso",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!hasSearch) {
            FilledTonalButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi server")
            }
        }
    }
}

private fun formatLastConnected(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "ora"
        diff < 3_600_000 -> "${diff / 60_000} min fa"
        diff < 86_400_000 -> "${diff / 3_600_000} ore fa"
        diff < 604_800_000 -> "${diff / 86_400_000} giorni fa"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
    }
}
