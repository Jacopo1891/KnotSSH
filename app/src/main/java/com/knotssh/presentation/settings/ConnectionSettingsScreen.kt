package com.knotssh.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.Defaults
import com.knotssh.presentation.common.rememberNotificationPermissionRequester

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
        item {
            SwitchRow(
                title = "Chiudi il terminale a fine sessione",
                description = "Dopo un exit torna da solo all'elenco delle connessioni",
                checked = connection.closeOnExit,
                onCheckedChange = viewModel::setCloseOnExit
            )
        }
        item {
            SliderRow(
                title = "Attesa prima di chiudere",
                description = "Tempo per leggere il messaggio di chiusura",
                value = connection.closeOnExitSeconds,
                range = 1..Defaults.CLOSE_ON_EXIT_SECONDS_MAX,
                enabled = connection.closeOnExit,
                valueLabel = { "$it s" },
                onValueChange = viewModel::setCloseOnExitSeconds
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
            SliderRow(
                title = "Sessioni contemporanee",
                description = "Quante connessioni puoi tenere aperte insieme. Ognuna occupa un thread e il proprio scrollback.",
                value = connection.maxSessions,
                range = 1..Defaults.MAX_SESSIONS_LIMIT,
                onValueChange = viewModel::setMaxSessions
            )
        }
        item {
            SwitchRow(
                title = "Sessioni in background",
                description = "Permette di sospendere una sessione e rientrarci dalla notifica persistente",
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
