package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.data.local.preferences.Defaults
import com.knotssh.presentation.common.rememberNotificationPermissionRequester

@Composable
fun ConnectionSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val notificationPermission = rememberNotificationPermissionRequester()
    // valueLabel is a plain lambda, so its strings are resolved outside composition.
    val resources = LocalContext.current.resources

    SettingsScaffold(title = stringResource(R.string.settings_connection), onBack = onBack) {
        item { SettingsSection(stringResource(R.string.section_session)) }
        item {
            SliderRow(
                title = stringResource(R.string.connection_keepalive),
                description = stringResource(R.string.connection_keepalive_desc),
                value = connection.keepAliveSeconds,
                range = 0..300,
                step = 15,
                valueLabel = {
                    if (it == 0) resources.getString(R.string.value_off)
                    else resources.getString(R.string.unit_seconds, it)
                },
                onValueChange = viewModel::setKeepAliveSeconds
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.connection_timeout),
                value = connection.connectTimeoutSeconds,
                range = 5..120,
                step = 5,
                valueLabel = { resources.getString(R.string.unit_seconds, it) },
                onValueChange = viewModel::setConnectTimeoutSeconds
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.connection_compression),
                description = stringResource(R.string.connection_compression_desc),
                checked = connection.compression,
                onCheckedChange = viewModel::setCompression
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.connection_close_on_exit),
                description = stringResource(R.string.connection_close_on_exit_desc),
                checked = connection.closeOnExit,
                onCheckedChange = viewModel::setCloseOnExit
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.connection_close_delay),
                description = stringResource(R.string.connection_close_delay_desc),
                value = connection.closeOnExitSeconds,
                range = 1..Defaults.CLOSE_ON_EXIT_SECONDS_MAX,
                enabled = connection.closeOnExit,
                valueLabel = { resources.getString(R.string.unit_seconds, it) },
                onValueChange = viewModel::setCloseOnExitSeconds
            )
        }

        item { SettingsSection(stringResource(R.string.section_reconnection)) }
        item {
            SwitchRow(
                title = stringResource(R.string.connection_auto_reconnect),
                description = stringResource(R.string.connection_auto_reconnect_desc),
                checked = connection.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.connection_reconnect_attempts),
                value = connection.autoReconnectAttempts,
                range = 1..10,
                enabled = connection.autoReconnect,
                onValueChange = viewModel::setAutoReconnectAttempts
            )
        }

        item { SettingsSection(stringResource(R.string.section_background)) }
        item {
            SliderRow(
                title = stringResource(R.string.connection_multi_session),
                description = stringResource(R.string.connection_multi_session_desc),
                value = connection.maxSessions,
                range = 1..Defaults.MAX_SESSIONS_LIMIT,
                onValueChange = viewModel::setMaxSessions
            )
        }
        item {
            // The persistent notification is what keeps the process alive, so an unprotected
            // setup is the more urgent warning of the two.
            val warning = when {
                connection.maxSessions > 1 && !connection.foregroundNotification ->
                    R.string.connection_sessions_unprotected
                connection.maxSessions > Defaults.MAX_SESSIONS_ADVISED ->
                    R.string.connection_sessions_costly
                else -> null
            }
            warning?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        item {
            SwitchRow(
                title = stringResource(R.string.connection_background_sessions),
                description = stringResource(R.string.connection_background_sessions_desc),
                checked = connection.foregroundNotification,
                onCheckedChange = { enabled ->
                    if (enabled) notificationPermission()
                    viewModel.setForegroundNotification(enabled)
                }
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.connection_wake_lock),
                description = stringResource(R.string.connection_wake_lock_desc),
                checked = connection.wakeLock,
                onCheckedChange = viewModel::setWakeLock
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.connection_wake_lock_duration),
                value = connection.wakeLockMinutes,
                range = 5..240,
                step = 5,
                enabled = connection.wakeLock,
                valueLabel = { resources.getString(R.string.unit_minutes, it) },
                onValueChange = viewModel::setWakeLockMinutes
            )
        }
    }
}
