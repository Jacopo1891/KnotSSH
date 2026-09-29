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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R

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
            title = { Text(stringResource(R.string.backup_reset_title)) },
            text = { Text(stringResource(R.string.backup_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaults()
                    showResetConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.action_restore),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    SettingsScaffold(title = stringResource(R.string.settings_backup), onBack = onBack) {
        item { SettingsSection(stringResource(R.string.section_backup)) }
        item {
            SwitchRow(
                title = stringResource(R.string.backup_drive_sync),
                description = stringResource(R.string.backup_drive_sync_desc),
                checked = driveSync,
                enabled = false,
                onCheckedChange = viewModel::setGoogleDriveSync
            )
        }

        item { SettingsSection(stringResource(R.string.section_restore)) }
        item {
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_reset_button))
            }
        }
    }
}
