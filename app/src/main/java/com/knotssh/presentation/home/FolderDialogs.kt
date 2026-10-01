package com.knotssh.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knotssh.R
import com.knotssh.domain.model.Folder
import com.knotssh.domain.model.Server

@Composable
internal fun FolderManagerDialog(
    folders: List<Folder>,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Folder?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_manage_folders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.folder_name_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.folder_create))
                    }
                }

                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(folders, key = { _, folder -> folder.id }) { index, folder ->
                            FolderManagerRow(
                                folder = folder,
                                isFirst = index == 0,
                                isLast = index == folders.lastIndex,
                                onMoveUp = { onMove(index, index - 1) },
                                onMoveDown = { onMove(index, index + 1) },
                                onRename = { editing = folder },
                                onDelete = { onDelete(folder.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )

    editing?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onConfirm = { name ->
                onRename(folder.id, name)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
internal fun RenameFolderDialog(
    folder: Folder,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FolderManagerRow(
    folder: Folder,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.ArrowUpward,
                stringResource(R.string.folder_move_up),
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.ArrowDownward,
                stringResource(R.string.folder_move_down),
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Edit,
                stringResource(R.string.folder_rename),
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun FolderContentsDialog(
    folder: Folder,
    servers: List<Server>,
    onToggle: (Server) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = {
            if (servers.isEmpty()) {
                Text(stringResource(R.string.folder_no_servers_yet))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.folder_pick_servers_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        itemsIndexed(servers, key = { _, server -> server.id }) { _, server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(server) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = server.folderId == folder.id,
                                    onCheckedChange = { onToggle(server) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(server.alias, style = MaterialTheme.typography.bodyMedium)
                                    val other = server.folderId != null &&
                                        server.folderId != folder.id
                                    if (other) {
                                        Text(
                                            text = stringResource(R.string.folder_in_other_folder),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
internal fun MoveToFolderDialog(
    server: Server,
    folders: List<Folder>,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_move_to_folder)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                item {
                    FolderChoiceRow(
                        label = stringResource(R.string.home_no_folder),
                        icon = Icons.Default.Dns,
                        selected = server.folderId == null,
                        onClick = { onSelect(null) }
                    )
                }
                itemsIndexed(folders, key = { _, folder -> folder.id }) { _, folder ->
                    FolderChoiceRow(
                        label = folder.name,
                        icon = Icons.Default.Folder,
                        selected = server.folderId == folder.id,
                        onClick = { onSelect(folder.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FolderChoiceRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        trailingContent = {
            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
