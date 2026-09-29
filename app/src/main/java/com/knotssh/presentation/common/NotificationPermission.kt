package com.knotssh.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Returns a callback that asks for POST_NOTIFICATIONS, or a no-op below Android 13. */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

/**
 * Asks for POST_NOTIFICATIONS the first time a screen that relies on it appears.
 *
 * Without the grant a foreground service still runs, but its notification is hidden from the
 * drawer, so a suspended session would have no way back.
 */
@Composable
fun RequestNotificationPermissionOnce(enabled: Boolean = true) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val request = rememberNotificationPermissionRequester()
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) request()
    }
}
