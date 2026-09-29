package com.knotssh.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConnectionSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val notificationPermission = rememberNotificationPermissionRequester()

    SettingsScaffold(title = "Connessione", onBack = onBack) {
        item { SettingsSection("Sessione") }
        item {
            SliderRow(
                title = "Keep-alive",
                description = "Intervallo dei pacchetti di mantenimento. 0 disattiva.",
                value = connection.keepAliveSeconds,
                range = 0..300,
                step = 15,
                valueLabel = { if (it == 0) "Off" else "$it s" },
                onValueChange = viewModel::setKeepAliveSeconds
            )
        }
        item {
            SliderRow(
                title = "Timeout di connessione",
                value = connection.connectTimeoutSeconds,
                range = 5..120,
                step = 5,
                valueLabel = { "$it s" },
                onValueChange = viewModel::setConnectTimeoutSeconds
            )
        }
        item {
            SwitchRow(
                title = "Compressione",
                description = "Utile su reti lente, aumenta il carico CPU",
                checked = connection.compression,
                onCheckedChange = viewModel::setCompression
            )
        }

        item { SettingsSection("Riconnessione") }
        item {
            SwitchRow(
                title = "Riconnessione automatica",
                description = "Ritenta quando la sessione cade per motivi di rete",
                checked = connection.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect
            )
        }
        item {
            SliderRow(
                title = "Tentativi di riconnessione",
                value = connection.autoReconnectAttempts,
                range = 1..10,
                enabled = connection.autoReconnect,
                onValueChange = viewModel::setAutoReconnectAttempts
            )
        }

        item { SettingsSection("In background") }
        item {
            SwitchRow(
                title = "Notifica persistente",
                description = "Servizio in primo piano che mantiene viva la sessione in background",
                checked = connection.foregroundNotification,
                onCheckedChange = { enabled ->
                    if (enabled) notificationPermission()
                    viewModel.setForegroundNotification(enabled)
                }
            )
        }
        item {
            SwitchRow(
                title = "Wake lock",
                description = "Impedisce alla CPU di sospendersi durante la sessione",
                checked = connection.wakeLock,
                onCheckedChange = viewModel::setWakeLock
            )
        }
        item {
            SliderRow(
                title = "Durata massima wake lock",
                value = connection.wakeLockMinutes,
                range = 5..240,
                step = 5,
                enabled = connection.wakeLock,
                valueLabel = { "$it min" },
                onValueChange = viewModel::setWakeLockMinutes
            )
        }
    }
}

/**
 * Notifications are only needed when the persistent session card is enabled, so the prompt is
 * tied to that switch rather than fired at app start.
 */
@Composable
private fun rememberNotificationPermissionRequester(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    return remember(launcher) {
        { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
