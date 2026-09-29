package com.knotssh.presentation.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knotssh.R

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Survives configuration changes but dies with the process, so rotating the device does not
 * re-prompt while a cold start still does.
 */
class AppLockState : ViewModel() {
    var unlocked: Boolean = false
    var backgroundedAtMs: Long = 0L
}

/**
 * Blocks [content] behind biometric or device-credential authentication.
 *
 * Re-locks when the app has been in the background for longer than [graceSeconds], so the
 * protection survives task switching rather than only cold starts.
 */
@Composable
fun BiometricGate(
    enabled: Boolean,
    graceSeconds: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    if (!enabled || activity == null) {
        content()
        return
    }

    val lockState: AppLockState = viewModel()
    var unlocked by remember { mutableStateOf(lockState.unlocked) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentGrace by rememberUpdatedState(graceSeconds)

    fun setUnlocked(value: Boolean) {
        unlocked = value
        lockState.unlocked = value
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lockState.backgroundedAtMs = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val since = lockState.backgroundedAtMs
                    if (since > 0L && System.currentTimeMillis() - since > currentGrace * 1000L) {
                        setUnlocked(false)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun authenticate() {
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(ALLOWED_AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No enrolled authenticator: failing open here would silently disable the setting,
            // so surface the reason instead.
            error = context.getString(R.string.biometric_no_method)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    setUnlocked(true)
                    error = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_title))
                .setSubtitle(context.getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
        )
    }

    LaunchedEffect(unlocked) {
        if (!unlocked) authenticate()
    }

    if (unlocked) {
        content()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.biometric_locked),
                    style = MaterialTheme.typography.titleMedium
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Button(onClick = ::authenticate) { Text(stringResource(R.string.biometric_unlock)) }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
