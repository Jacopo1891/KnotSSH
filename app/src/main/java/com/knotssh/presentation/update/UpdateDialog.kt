package com.knotssh.presentation.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import kotlin.math.roundToInt

/**
 * Single rendering of every update state, so the startup check and the two manual buttons
 * cannot drift apart.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val current = state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> Shell(
            title = stringResource(R.string.update_title),
            body = { Text(stringResource(R.string.update_checking)) },
            confirm = null,
            onDismiss = viewModel::dismiss
        )

        UpdateState.UpToDate -> Shell(
            title = stringResource(R.string.update_title),
            body = {
                Text(stringResource(R.string.update_up_to_date, viewModel.currentVersion))
            },
            confirm = null,
            onDismiss = viewModel::dismiss
        )

        is UpdateState.Available -> Shell(
            title = stringResource(R.string.update_available, current.release.version),
            body = {
                if (current.release.notes.isNotBlank()) {
                    Text(
                        text = current.release.notes.take(NOTES_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 240.dp)
                    )
                }
            },
            confirmLabel = stringResource(R.string.update_download),
            confirm = { viewModel.download(current.release) },
            dismissLabel = stringResource(R.string.update_later),
            onDismiss = viewModel::dismiss
        )

        is UpdateState.Downloading -> Shell(
            title = stringResource(R.string.update_title),
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.update_downloading,
                            (current.progress * 100).roundToInt()
                        )
                    )
                    LinearProgressIndicator(
                        progress = { current.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirm = null,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::dismiss
        )

        is UpdateState.ReadyToInstall -> Shell(
            title = stringResource(R.string.update_title),
            body = { Text(stringResource(R.string.update_ready, current.release.version)) },
            confirmLabel = stringResource(R.string.update_install),
            confirm = {
                context.launch(viewModel.installIntent(current.apk))
                viewModel.dismiss()
            },
            dismissLabel = stringResource(R.string.update_later),
            onDismiss = viewModel::dismiss
        )

        UpdateState.PermissionRequired -> Shell(
            title = stringResource(R.string.update_permission_title),
            body = { Text(stringResource(R.string.update_permission_body)) },
            confirmLabel = stringResource(R.string.update_open_settings),
            confirm = {
                context.launch(viewModel.unknownSourcesIntent())
                viewModel.dismiss()
            },
            onDismiss = viewModel::dismiss
        )

        UpdateState.Failed -> Shell(
            title = stringResource(R.string.update_title),
            body = { Text(stringResource(R.string.update_failed)) },
            confirm = null,
            onDismiss = viewModel::dismiss
        )
    }
}

@Composable
private fun Shell(
    title: String,
    body: @Composable () -> Unit,
    confirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    confirmLabel: String = "",
    dismissLabel: String = stringResource(R.string.action_close)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = confirm) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

private fun Context.launch(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { if (it !is ActivityNotFoundException) throw it }
}

private const val NOTES_LIMIT = 1200
