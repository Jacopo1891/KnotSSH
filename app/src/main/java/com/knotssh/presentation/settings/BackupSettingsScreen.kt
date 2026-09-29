package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val driveSync by viewModel.googleDriveSync.collectAsStateWithLifecycle()
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Ripristina impostazioni") },
            text = { Text("Tutte le preferenze tornano ai valori predefiniti. Server, credenziali e host conosciuti non vengono toccati.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaults()
                    showResetConfirm = false
                }) { Text("Ripristina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Annulla") }
            }
        )
    }

    SettingsScaffold(title = "Backup e ripristino", onBack = onBack) {
        item { SettingsSection("Backup") }
        item {
            SwitchRow(
                title = "Sincronizzazione Google Drive",
                description = "Non ancora disponibile in questa versione",
                checked = driveSync,
                enabled = false,
                onCheckedChange = viewModel::setGoogleDriveSync
            )
        }

        item { SettingsSection("Ripristino") }
        item {
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ripristina impostazioni predefinite")
            }
        }
    }
}
