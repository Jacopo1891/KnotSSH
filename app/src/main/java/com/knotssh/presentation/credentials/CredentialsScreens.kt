package com.knotssh.presentation.credentials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.presentation.components.SwipeToDeleteContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    onBack: () -> Unit,
    onAddCredential: () -> Unit,
    onEditCredential: (Long) -> Unit,
    viewModel: CredentialsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Utenze") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCredential,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuova Utenza")
            }
        }
    ) { padding ->
        if (uiState.credentials.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nessuna utenza salvata", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(uiState.credentials, key = { it.id }) { cred ->
                    SwipeToDeleteContainer(onDelete = { viewModel.deleteCredential(cred.id) }) {
                        Card(
                            onClick = { onEditCredential(cred.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (cred.authType == AuthType.PASSWORD) Icons.Default.VpnKey else Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cred.alias, style = MaterialTheme.typography.titleMedium)
                                    Text("User: ${cred.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Tipo: ${cred.authType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
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
        topBar = {
            TopAppBar(
                title = { Text(if (credentialId == null) "Nuova Utenza" else "Modifica Utenza") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Check, contentDescription = "Salva")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.alias,
                onValueChange = viewModel::onAliasChanged,
                label = { Text("Alias Utenza (es. Admin Prod)") },
                isError = state.aliasError != null,
                supportingText = state.aliasError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                isError = state.usernameError != null,
                supportingText = state.usernameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.authType == AuthType.PASSWORD,
                    onClick = { viewModel.onAuthTypeChanged(AuthType.PASSWORD) },
                    label = { Text("Password") }
                )
                FilterChip(
                    selected = state.authType == AuthType.SSH_KEY,
                    onClick = { viewModel.onAuthTypeChanged(AuthType.SSH_KEY) },
                    label = { Text("Chiave SSH") }
                )
            }

            OutlinedTextField(
                value = state.secret,
                onValueChange = viewModel::onSecretChanged,
                label = { Text(if (state.authType == AuthType.PASSWORD) "Password" else "Chiave Privata PEM") },
                isError = state.secretError != null,
                supportingText = state.secretError?.let { { Text(it) } },
                visualTransformation = if (showSecret || !state.maskSecrets) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSecret) "Nascondi" else "Mostra"
                        )
                    }
                },
                maxLines = if (state.authType == AuthType.SSH_KEY) 8 else 1,
                singleLine = state.authType == AuthType.PASSWORD,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.authType == AuthType.SSH_KEY) {
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::onPassphraseChanged,
                    label = { Text("Passphrase della chiave (opzionale)") },
                    supportingText = { Text("Necessaria solo se la chiave privata è cifrata") },
                    visualTransformation = if (showPassphrase || !state.maskSecrets) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Nascondi" else "Mostra"
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
