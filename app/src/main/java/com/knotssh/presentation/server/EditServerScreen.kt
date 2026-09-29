package com.knotssh.presentation.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.domain.model.ForwardType
import com.knotssh.domain.model.PortForwardRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditServerScreen(
    serverId: Long?,
    onBack: () -> Unit,
    onNavigateToAddCredential: () -> Unit,
    viewModel: EditServerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddRuleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (serverId == null) R.string.server_new_title
                            else R.string.server_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.action_save)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // General Info Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.server_general_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = state.alias,
                            onValueChange = viewModel::onAliasChanged,
                            label = { Text(stringResource(R.string.server_alias_label)) },
                            isError = state.aliasError != null,
                            supportingText = state.aliasError?.let { { Text(stringResource(it)) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = state.hostname,
                                onValueChange = viewModel::onHostnameChanged,
                                label = { Text(stringResource(R.string.server_host_label)) },
                                isError = state.hostnameError != null,
                                supportingText = state.hostnameError?.let { { Text(stringResource(it)) } },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f)
                            )
                            OutlinedTextField(
                                value = state.port,
                                onValueChange = viewModel::onPortChanged,
                                label = { Text(stringResource(R.string.server_port_label)) },
                                isError = state.portError != null,
                                supportingText = state.portError?.let { { Text(stringResource(it)) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.3f)
                            )
                        }
                    }
                }
            }

            // Credential Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.server_auth_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = onNavigateToAddCredential) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.action_new))
                            }
                        }

                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val selectedCred = state.credentials.find { it.id == state.selectedCredentialId }

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCred?.let { "${it.alias} (${it.username})" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.server_credential_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                isError = state.credentialError != null,
                                supportingText = state.credentialError?.let { { Text(stringResource(it)) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                state.credentials.forEach { cred ->
                                    DropdownMenuItem(
                                        text = { Text("${cred.alias} (${cred.username})") },
                                        onClick = {
                                            viewModel.onCredentialSelected(cred.id)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Advanced Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.server_advanced_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.keepAliveSeconds,
                                onValueChange = viewModel::onKeepAliveChanged,
                                label = { Text(stringResource(R.string.server_keepalive_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.5f)
                            )
                            OutlinedTextField(
                                value = state.connectTimeoutSeconds,
                                onValueChange = viewModel::onTimeoutChanged,
                                label = { Text(stringResource(R.string.server_timeout_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.5f)
                            )
                        }
                    }
                }
            }

            // Port Forwarding Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.server_port_forwarding),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showAddRuleDialog = true }) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = stringResource(R.string.server_add_rule)
                        )
                    }
                }
            }

            items(state.portForwardRules) { rule ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${rule.type}: :${rule.localPort} -> ${rule.remoteHost}:${rule.remotePort}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { viewModel.removePortForwardRule(rule) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_remove),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddPortForwardDialog(
            onDismiss = { showAddRuleDialog = false },
            onAdd = { rule ->
                viewModel.addPortForwardRule(rule)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun AddPortForwardDialog(
    onDismiss: () -> Unit,
    onAdd: (PortForwardRule) -> Unit
) {
    var type by remember { mutableStateOf(ForwardType.LOCAL) }
    var localPort by remember { mutableStateOf("8080") }
    var remoteHost by remember { mutableStateOf("localhost") }
    var remotePort by remember { mutableStateOf("80") }

    val localPortValue = localPort.toIntOrNull()
    val remotePortValue = remotePort.toIntOrNull()
    val localPortValid = localPortValue in 1..65535
    val remotePortValid = remotePortValue in 1..65535
    val isValid = localPortValid && remotePortValid && remoteHost.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forward_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ForwardType.LOCAL,
                        onClick = { type = ForwardType.LOCAL },
                        label = { Text(stringResource(R.string.forward_local)) }
                    )
                    FilterChip(
                        selected = type == ForwardType.REMOTE,
                        onClick = { type = ForwardType.REMOTE },
                        label = { Text(stringResource(R.string.forward_remote)) }
                    )
                }
                Text(
                    text = stringResource(
                        if (type == ForwardType.LOCAL) R.string.forward_local_desc
                        else R.string.forward_remote_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = localPort,
                    onValueChange = { localPort = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.forward_local_port)) },
                    isError = localPort.isNotEmpty() && !localPortValid,
                    supportingText = if (localPort.isNotEmpty() && !localPortValid) {
                        { Text(stringResource(R.string.port_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    label = { Text(stringResource(R.string.forward_target_host)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = remotePort,
                    onValueChange = { remotePort = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.forward_target_port)) },
                    isError = remotePort.isNotEmpty() && !remotePortValid,
                    supportingText = if (remotePort.isNotEmpty() && !remotePortValid) {
                        { Text(stringResource(R.string.port_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onAdd(
                        PortForwardRule(
                            type = type,
                            localPort = localPortValue!!,
                            remoteHost = remoteHost.trim(),
                            remotePort = remotePortValue!!
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
