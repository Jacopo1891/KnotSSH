package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.data.local.preferences.HostKeyPolicy

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val security by viewModel.security.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHostList.collectAsStateWithLifecycle()
    var showKnownHosts by remember { mutableStateOf(false) }

    if (showKnownHosts) {
        KnownHostsDialog(
            hosts = knownHosts,
            onForget = viewModel::forgetKnownHost,
            onForgetAll = viewModel::forgetAllKnownHosts,
            onDismiss = { showKnownHosts = false }
        )
    }

    SettingsScaffold(title = "Sicurezza", onBack = onBack) {
        item { SettingsSection("Chiavi host") }
        item {
            ChoiceRow(
                title = "Verifica chiave host",
                description = when (security.hostKeyPolicy) {
                    HostKeyPolicy.STRICT -> "Solo host già memorizzati. Massima sicurezza."
                    HostKeyPolicy.PROMPT -> "Chiede conferma mostrando l'impronta al primo accesso."
                    HostKeyPolicy.TRUST_ON_FIRST_USE -> "Memorizza in automatico al primo accesso, blocca se la chiave cambia."
                    HostKeyPolicy.ACCEPT_ANY -> "⚠ Nessuna protezione contro attacchi man-in-the-middle."
                },
                options = listOf(
                    HostKeyPolicy.STRICT to "Rigida",
                    HostKeyPolicy.PROMPT to "Chiedi",
                    HostKeyPolicy.TRUST_ON_FIRST_USE to "TOFU",
                    HostKeyPolicy.ACCEPT_ANY to "Nessuna"
                ),
                selected = security.hostKeyPolicy,
                onSelected = viewModel::setHostKeyPolicy
            )
        }
        item {
            OutlinedButton(
                onClick = { showKnownHosts = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Host conosciuti (${knownHosts.size})")
            }
        }

        item { SettingsSection("Accesso all'app") }
        item {
            SwitchRow(
                title = "Sblocco biometrico",
                description = "Richiede impronta, volto o PIN all'apertura dell'app",
                checked = security.biometricEnabled,
                onCheckedChange = viewModel::setBiometricEnabled
            )
        }
        item {
            SliderRow(
                title = "Tolleranza di riblocco",
                description = "Tempo in background prima di richiedere di nuovo l'autenticazione",
                value = security.biometricGraceSeconds,
                range = 0..600,
                step = 30,
                enabled = security.biometricEnabled,
                valueLabel = { if (it == 0) "Subito" else "$it s" },
                onValueChange = viewModel::setBiometricGraceSeconds
            )
        }

        item { SettingsSection("Riservatezza") }
        item {
            SwitchRow(
                title = "Consenti screenshot",
                description = "Se disattivo, l'intera app blocca cattura schermo e anteprima nei recenti",
                checked = security.allowScreenshot,
                onCheckedChange = viewModel::setAllowScreenshot
            )
        }
        item {
            SwitchRow(
                title = "Nascondi segreti nei moduli",
                description = "Maschera password e chiavi private nella schermata utenze",
                checked = security.maskSecretsInUi,
                onCheckedChange = viewModel::setMaskSecretsInUi
            )
        }
        item {
            SwitchRow(
                title = "Log SSH dettagliati",
                description = "Solo build di debug. Scrive la negoziazione SSH in Logcat.",
                checked = security.verboseSshLogging,
                onCheckedChange = viewModel::setVerboseSshLogging
            )
        }
    }
}
