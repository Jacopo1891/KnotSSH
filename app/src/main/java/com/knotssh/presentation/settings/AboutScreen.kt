package com.knotssh.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MailOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.BuildConfig
import com.knotssh.R
import java.io.File

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

        item { SettingsSection(stringResource(R.string.about_section_support)) }
        item {
            SettingsCategoryRow(
                icon = Icons.Default.MailOutline,
                title = stringResource(R.string.about_contact),
                summary = stringResource(R.string.about_contact_desc, SUPPORT_EMAIL),
                onClick = {
                    val sent = context.sendSupportMail(
                        subject = context.getString(
                            R.string.about_mail_subject,
                            BuildConfig.VERSION_NAME
                        ),
                        body = context.getString(R.string.about_mail_body)
                    )
                    if (!sent) message = context.getString(R.string.about_no_mail_app)
                }
            )
        }
        item {
            SettingsCategoryRow(
                icon = Icons.AutoMirrored.Filled.Send,
                title = stringResource(R.string.about_report),
                summary = stringResource(R.string.about_report_desc),
                onClick = {
                    val sent = context.shareReport(
                        subject = context.getString(
                            R.string.about_report_subject,
                            BuildConfig.VERSION_NAME
                        ),
                        report = viewModel.report()
                    )
                    if (!sent) message = context.getString(R.string.about_no_mail_app)
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
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
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

private const val SUPPORT_EMAIL = "info@suitslabs.dev"
private const val REPORT_MIME = "text/plain"

private fun reportFileName() = "knotssh-report-${System.currentTimeMillis()}.txt"

private fun Context.sendSupportMail(subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return launchChooser(intent, subject)
}

/**
 * Writes the report to the shared cache directory and attaches it, so the user never has to find
 * and pick the file by hand.
 */
private fun Context.shareReport(subject: String, report: String): Boolean {
    val uri = runCatching {
        val dir = File(cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, reportFileName()).apply { writeText(report) }
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }.getOrNull() ?: return false

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = REPORT_MIME
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, getString(R.string.about_report_mail_body))
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return launchChooser(intent, subject)
}

private fun Context.launchChooser(intent: Intent, title: String): Boolean = try {
    startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
