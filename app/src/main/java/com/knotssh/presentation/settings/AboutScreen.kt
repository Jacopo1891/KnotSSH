package com.knotssh.presentation.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.BuildConfig
import com.knotssh.R

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val verbose by viewModel.verbose.collectAsStateWithLifecycle()

    var reportPreview by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(REPORT_MIME)
    ) { uri ->
        uri?.let {
            viewModel.export(it) { ok ->
                message = context.getString(
                    if (ok) R.string.about_export_done else R.string.about_export_failed
                )
            }
        }
    }

    reportPreview?.let { report ->
        ReportPreviewDialog(report = report, onDismiss = { reportPreview = null })
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    SettingsScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
        item { AppHeader() }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.Update,
                title = stringResource(R.string.about_updates),
                summary = GITHUB_LABEL,
                onClick = {
                    if (!context.openUrl(GITHUB_RELEASES_URL)) {
                        message = context.getString(R.string.about_no_browser_app)
                    }
                }
            )
        }

        item { SettingsSection(stringResource(R.string.about_section_support)) }
        item {
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.Send,
                title = stringResource(R.string.about_report),
                summary = stringResource(R.string.about_report_desc),
                onClick = {
                    context.copyReport(viewModel.report())
                    val url = newIssueUrl(
                        title = context.getString(
                            R.string.about_report_subject,
                            BuildConfig.VERSION_NAME
                        ),
                        body = context.getString(
                            R.string.about_report_issue_body,
                            Build.MODEL,
                            Build.VERSION.RELEASE
                        )
                    )
                    if (!context.openUrl(url)) {
                        message = context.getString(R.string.about_no_browser_app)
                    }
                }
            )
        }

        item { SettingsSection(stringResource(R.string.about_section_diagnostics)) }
        item {
            SwitchRow(
                title = stringResource(R.string.about_verbose),
                description = stringResource(R.string.about_verbose_desc),
                checked = verbose,
                onCheckedChange = viewModel::setVerbose
            )
        }
        item {
            Text(
                text = stringResource(R.string.about_diagnostics_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { reportPreview = viewModel.report() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.about_view_log, entries.size))
                }
                OutlinedButton(
                    onClick = { exportLog.launch(reportFileName()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.about_export_log))
                }
            }
        }
        item {
            TextButton(
                onClick = {
                    viewModel.clear()
                    message = context.getString(R.string.about_log_cleared)
                },
                enabled = entries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.about_clear_log),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.ic_launcher_background))
            ) {
                // The artwork already carries the adaptive-icon safe zone, so it fills the tile.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Shows the report verbatim, so what the user reviews is exactly what leaves the device. */
@Composable
private fun ReportPreviewDialog(report: String, onDismiss: () -> Unit) {
    val lines = remember(report) { report.lines() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_log_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(lines.size) { index ->
                    Text(
                        text = lines[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

private const val GITHUB_URL = "https://github.com/Jacopo1891/KnotSSH"
private const val GITHUB_RELEASES_URL = "$GITHUB_URL/releases"
private const val GITHUB_LABEL = "github.com/Jacopo1891/KnotSSH"
private const val REPORT_MIME = "text/plain"

private fun reportFileName() = "knotssh-report-${System.currentTimeMillis()}.txt"

private fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** GitHub has no URL parameter for attachments, so the log reaches the issue via the clipboard. */
private fun Context.copyReport(report: String) {
    ContextCompat.getSystemService(this, ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), report))
}

private fun newIssueUrl(title: String, body: String): String =
    "$GITHUB_URL/issues/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
