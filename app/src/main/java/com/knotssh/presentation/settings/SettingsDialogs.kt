package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.KeyModifier
import com.knotssh.domain.model.KnownHost
import com.knotssh.domain.model.QuickCommand

@Composable
internal fun KnownHostsDialog(
    hosts: List<KnownHost>,
    onForget: (Long) -> Unit,
    onForgetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.known_hosts_title)) },
        text = {
            if (hosts.isEmpty()) {
                Text(stringResource(R.string.known_hosts_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(hosts.size, key = { hosts[it].id }) { index ->
                        val host = hosts[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${host.host}:${host.port}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${host.keyType} · ${host.fingerprintSha256}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onForget(host.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_forget),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            if (hosts.isNotEmpty()) {
                TextButton(onClick = onForgetAll) {
                    Text(
                        text = stringResource(R.string.action_forget_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
internal fun QuickCommandsDialog(
    commands: List<QuickCommand>,
    onAdd: (label: String, command: String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    val canAdd = label.isNotBlank() && command.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_commands_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.quick_commands_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.field_command)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onAdd(label, command)
                        label = ""
                        command = ""
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_add)) }

                if (commands.isNotEmpty()) {
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(commands.size, key = { commands[it].id }) { index ->
                            val item = commands[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        item.command,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
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
internal fun CustomKeysDialog(
    keys: List<CustomKey>,
    onAdd: (label: String, modifier: KeyModifier, base: String) -> Unit,
    onDelete: (Long) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var modifier by remember { mutableStateOf(KeyModifier.CTRL) }

    val preview = buildKeySequence(modifier, base)
    val canAdd = preview != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_keys_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.custom_keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChoiceRow(
                    title = stringResource(R.string.field_modifier),
                    options = listOf(
                        KeyModifier.CTRL to "Ctrl",
                        KeyModifier.ALT to "Alt",
                        KeyModifier.CTRL_ALT to "Ctrl+Alt",
                        KeyModifier.NONE to stringResource(R.string.modifier_none)
                    ),
                    selected = modifier,
                    onSelected = { modifier = it }
                )
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it.takeLast(1) },
                    label = { Text(stringResource(R.string.field_key)) },
                    supportingText = { Text(stringResource(R.string.field_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.field_label_optional)) },
                    placeholder = { Text(if (canAdd) defaultLabel(modifier, base) else "") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onAdd(label, modifier, base)
                        label = ""
                        base = ""
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_add)) }

                if (keys.isNotEmpty()) {
                    HorizontalDivider()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(keys.size, key = { keys[it].id }) { index ->
                            val key = keys[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = key.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDelete(key.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = onRestoreDefaults) {
                Text(stringResource(R.string.action_restore_defaults))
            }
        }
    )
}
