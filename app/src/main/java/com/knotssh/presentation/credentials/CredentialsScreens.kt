package com.knotssh.presentation.credentials

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.presentation.components.SwipeToDeleteContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    onAddCredential: () -> Unit,
    onEditCredential: (Long) -> Unit,
    onNavigateSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: CredentialsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var credentialToDelete by remember { mutableStateOf<Credential?>(null) }

    credentialToDelete?.let { credential ->
        val affected = uiState.serversPerCredential[credential.id] ?: 0
        AlertDialog(
            onDismissRequest = { credentialToDelete = null },
            title = { Text(stringResource(R.string.credential_delete_title)) },
            text = {
                Text(
                    if (affected == 0) {
                        stringResource(R.string.credential_delete_message, credential.alias)
                    } else {
                        stringResource(R.string.credential_delete_message, credential.alias) +
                            "\n\n" +
                            pluralStringResource(
                                R.plurals.credential_delete_orphans,
                                affected,
                                affected
                            )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCredential(credential.id)
                    credentialToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { credentialToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.credentials_title)) },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCredential,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.credential_new_title)) }
            )
        }
    ) { padding ->
        if (uiState.credentials.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.credentials_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                itemsIndexed(uiState.credentials, key = { _, it -> it.id }) { index, cred ->
                    Column(modifier = Modifier.animateItem()) {
                        SwipeToDeleteContainer(
                            onDelete = { credentialToDelete = cred; false },
                            shape = RectangleShape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { onEditCredential(cred.id) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (cred.authType == AuthType.PASSWORD) Icons.Default.VpnKey else Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cred.alias, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.credential_user, cred.username),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.credential_type, cred.authType.name) +
                                            if (cred.askEachTime) {
                                                " · " + stringResource(R.string.credential_prompts)
                                            } else {
                                                ""
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < uiState.credentials.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCredentialScreen(
    credentialId: Long?,
    onBack: () -> Unit,
    viewModel: EditCredentialViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSecret by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (credentialId == null) R.string.credential_new_title
                            else R.string.credential_edit_title
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
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.alias,
                onValueChange = viewModel::onAliasChanged,
                label = { Text(stringResource(R.string.credential_alias_label)) },
                isError = state.aliasError != null,
                supportingText = state.aliasError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text(stringResource(R.string.credential_username_label)) },
                isError = state.usernameError != null,
                supportingText = state.usernameError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.authType == AuthType.PASSWORD,
                    onClick = { viewModel.onAuthTypeChanged(AuthType.PASSWORD) },
                    label = { Text(stringResource(R.string.auth_password)) }
                )
                FilterChip(
                    selected = state.authType == AuthType.SSH_KEY,
                    onClick = { viewModel.onAuthTypeChanged(AuthType.SSH_KEY) },
                    label = { Text(stringResource(R.string.auth_ssh_key)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.credential_ask_each_time),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(
                            if (state.authType == AuthType.PASSWORD) {
                                R.string.credential_ask_each_time_password_desc
                            } else {
                                R.string.credential_ask_each_time_key_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.askEachTime,
                    onCheckedChange = viewModel::onAskEachTimeChanged
                )
            }

            // With a prompt-every-time password there is nothing left to type here.
            if (!(state.askEachTime && state.authType == AuthType.PASSWORD)) {
                OutlinedTextField(
                    value = state.secret,
                    onValueChange = viewModel::onSecretChanged,
                    label = {
                        Text(
                            stringResource(
                                if (state.authType == AuthType.PASSWORD) R.string.auth_password
                                else R.string.credential_pem_label
                            )
                        )
                    },
                    isError = state.secretError != null,
                    supportingText = state.secretError?.let { { Text(stringResource(it)) } },
                    visualTransformation = if (showSecret || !state.maskSecrets) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showSecret) R.string.action_hide else R.string.action_show
                                )
                            )
                        }
                    },
                    maxLines = if (state.authType == AuthType.SSH_KEY) 8 else 1,
                    singleLine = state.authType == AuthType.PASSWORD,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.authType == AuthType.SSH_KEY && !state.askEachTime) {
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::onPassphraseChanged,
                    label = { Text(stringResource(R.string.credential_passphrase_label)) },
                    supportingText = { Text(stringResource(R.string.credential_passphrase_hint)) },
                    visualTransformation = if (showPassphrase || !state.maskSecrets) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (showPassphrase) R.string.action_hide else R.string.action_show
                                )
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
