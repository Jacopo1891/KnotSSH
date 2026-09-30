package com.knotssh.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.data.backup.ImportOutcome
import com.knotssh.data.backup.BackupSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val driveSync by viewModel.googleDriveSync.collectAsStateWithLifecycle()
    val busy by backupViewModel.busy.collectAsStateWithLifecycle()
    val error by backupViewModel.error.collectAsStateWithLifecycle()
    val exported by backupViewModel.exported.collectAsStateWithLifecycle()
    val passwordRequired by backupViewModel.passwordRequired.collectAsStateWithLifecycle()
    val pendingImport by backupViewModel.pendingImport.collectAsStateWithLifecycle()
    val outcome by backupViewModel.outcome.collectAsStateWithLifecycle()

    var showResetConfirm by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportIncludesSettings by remember { mutableStateOf(true) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri ->
        if (uri == null) {
            exportPassword = ""
        } else {
            backupViewModel.export(
                uri = uri,
                password = exportPassword.takeIf { it.isNotEmpty() }?.toCharArray(),
                includeSettings = exportIncludesSettings
            )
            exportPassword = ""
        }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(backupViewModel::beginImport) }

    if (showExportOptions) {
        ExportOptionsDialog(
            password = exportPassword,
            onPasswordChange = { exportPassword = it },
            includeSettings = exportIncludesSettings,
            onIncludeSettingsChange = { exportIncludesSettings = it },
            onConfirm = {
                showExportOptions = false
                createFile.launch(suggestedFileName())
            },
            onDismiss = {
                showExportOptions = false
                exportPassword = ""
            }
        )
    }

    if (passwordRequired) {
        ImportPasswordDialog(
            onSubmit = { backupViewModel.submitImportPassword(it.toCharArray()) },
            onDismiss = backupViewModel::cancelImport
        )
    }

    pendingImport?.let { pending ->
        ImportPreviewDialog(
            summary = pending.summary,
            onConfirm = backupViewModel::confirmImport,
            onDismiss = backupViewModel::cancelImport
        )
    }

    outcome?.let { ImportResultDialog(it, backupViewModel::dismissMessage) }

    if (exported) {
        InfoDialog(
            title = stringResource(R.string.backup_export_done_title),
            message = stringResource(R.string.backup_export_done_message),
            onDismiss = backupViewModel::dismissMessage
        )
    }

    error?.let { messageRes ->
        InfoDialog(
            title = stringResource(R.string.backup_error_title),
            message = stringResource(messageRes),
            onDismiss = backupViewModel::dismissMessage
        )
    }

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
        item { SettingsSection(stringResource(R.string.section_backup_file)) }
        item {
            Text(
                text = stringResource(R.string.backup_file_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (busy) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        item {
            OutlinedButton(
                onClick = { showExportOptions = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_export_button))
            }
        }
        item {
            OutlinedButton(
                // Some providers report the custom extension as octet-stream, others as generic.
                onClick = { openFile.launch(arrayOf(BACKUP_MIME, "*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_import_button))
            }
        }

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

@Composable
private fun ExportOptionsDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    includeSettings: Boolean,
    onIncludeSettingsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val weak = password.isNotEmpty() && password.length < RECOMMENDED_PASSWORD_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    isError = weak,
                    supportingText = {
                        Text(
                            text = when {
                                password.isEmpty() ->
                                    stringResource(R.string.backup_password_empty_warning)
                                weak -> stringResource(R.string.backup_password_weak)
                                else -> stringResource(R.string.backup_password_hint)
                            },
                            color = if (password.isEmpty() || weak) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (visible) R.string.action_hide else R.string.action_show
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                CheckboxRow(
                    checked = includeSettings,
                    onCheckedChange = onIncludeSettingsChange,
                    label = stringResource(R.string.backup_include_settings)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_export_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ImportPasswordDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_required_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.backup_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) {
                Text(stringResource(R.string.backup_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ImportPreviewDialog(
    summary: BackupSummary,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var restoreSettings by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.backup_import_preview_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.backup_count_credentials, summary.credentials))
                Text(stringResource(R.string.backup_count_servers, summary.servers))
                Text(stringResource(R.string.backup_count_known_hosts, summary.knownHosts))
                Text(stringResource(R.string.backup_count_commands, summary.quickCommands))
                Text(stringResource(R.string.backup_count_keys, summary.customKeys))
                if (summary.hasSettings) {
                    CheckboxRow(
                        checked = restoreSettings,
                        onCheckedChange = { restoreSettings = it },
                        label = stringResource(R.string.backup_restore_settings)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(restoreSettings) }) {
                Text(stringResource(R.string.backup_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ImportResultDialog(outcome: ImportOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.backup_import_added_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(stringResource(R.string.backup_count_credentials, outcome.added.credentials))
                Text(stringResource(R.string.backup_count_servers, outcome.added.servers))
                Text(stringResource(R.string.backup_count_known_hosts, outcome.added.knownHosts))
                Text(stringResource(R.string.backup_count_commands, outcome.added.quickCommands))
                Text(stringResource(R.string.backup_count_keys, outcome.added.customKeys))
                Text(
                    stringResource(
                        R.string.backup_import_skipped,
                        outcome.skipped.credentials + outcome.skipped.servers +
                            outcome.skipped.knownHosts + outcome.skipped.quickCommands +
                            outcome.skipped.customKeys
                    )
                )
                if (outcome.renamed > 0) {
                    Text(stringResource(R.string.backup_import_renamed, outcome.renamed))
                }
                if (outcome.conflictingHostKeys > 0) {
                    Text(
                        text = stringResource(
                            R.string.backup_import_host_conflicts,
                            outcome.conflictingHostKeys
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (outcome.settingsRestored) {
                    Text(stringResource(R.string.backup_import_settings_restored))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
        }
    )
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
        }
    )
}

@Composable
private fun CheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun suggestedFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "knotssh-$stamp.knotssh"
}

private const val BACKUP_MIME = "application/octet-stream"
private const val RECOMMENDED_PASSWORD_LENGTH = 12
