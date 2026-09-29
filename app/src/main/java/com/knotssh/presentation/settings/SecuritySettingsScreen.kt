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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knotssh.R
import com.knotssh.data.local.preferences.HostKeyPolicy

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val security by viewModel.security.collectAsStateWithLifecycle()
    val knownHosts by viewModel.knownHostList.collectAsStateWithLifecycle()
    var showKnownHosts by remember { mutableStateOf(false) }
    // valueLabel is a plain lambda, so its strings are resolved outside composition.
    val resources = LocalContext.current.resources

    if (showKnownHosts) {
        KnownHostsDialog(
            hosts = knownHosts,
            onForget = viewModel::forgetKnownHost,
            onForgetAll = viewModel::forgetAllKnownHosts,
            onDismiss = { showKnownHosts = false }
        )
    }

    SettingsScaffold(title = stringResource(R.string.settings_security), onBack = onBack) {
        item { SettingsSection(stringResource(R.string.section_host_keys)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.security_host_key_check),
                description = stringResource(
                    when (security.hostKeyPolicy) {
                        HostKeyPolicy.STRICT -> R.string.security_host_key_strict_desc
                        HostKeyPolicy.PROMPT -> R.string.security_host_key_prompt_desc
                        HostKeyPolicy.TRUST_ON_FIRST_USE -> R.string.security_host_key_tofu_desc
                        HostKeyPolicy.ACCEPT_ANY -> R.string.security_host_key_any_desc
                    }
                ),
                options = listOf(
                    HostKeyPolicy.STRICT to stringResource(R.string.host_key_strict),
                    HostKeyPolicy.PROMPT to stringResource(R.string.host_key_prompt),
                    HostKeyPolicy.TRUST_ON_FIRST_USE to stringResource(R.string.host_key_tofu),
                    HostKeyPolicy.ACCEPT_ANY to stringResource(R.string.host_key_none)
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
                Text(stringResource(R.string.security_known_hosts_button, knownHosts.size))
            }
        }

        item { SettingsSection(stringResource(R.string.section_app_access)) }
        item {
            SwitchRow(
                title = stringResource(R.string.security_biometric),
                description = stringResource(R.string.security_biometric_desc),
                checked = security.biometricEnabled,
                onCheckedChange = viewModel::setBiometricEnabled
            )
        }
        item {
            SliderRow(
                title = stringResource(R.string.security_relock_grace),
                description = stringResource(R.string.security_relock_grace_desc),
                value = security.biometricGraceSeconds,
                range = 0..600,
                step = 30,
                enabled = security.biometricEnabled,
                valueLabel = {
                    if (it == 0) resources.getString(R.string.value_immediately)
                    else resources.getString(R.string.unit_seconds, it)
                },
                onValueChange = viewModel::setBiometricGraceSeconds
            )
        }

        item { SettingsSection(stringResource(R.string.section_privacy)) }
        item {
            SwitchRow(
                title = stringResource(R.string.security_allow_screenshot),
                description = stringResource(R.string.security_allow_screenshot_desc),
                checked = security.allowScreenshot,
                onCheckedChange = viewModel::setAllowScreenshot
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.security_mask_secrets),
                description = stringResource(R.string.security_mask_secrets_desc),
                checked = security.maskSecretsInUi,
                onCheckedChange = viewModel::setMaskSecretsInUi
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.security_verbose_logs),
                description = stringResource(R.string.security_verbose_logs_desc),
                checked = security.verboseSshLogging,
                onCheckedChange = viewModel::setVerboseSshLogging
            )
        }
    }
}
