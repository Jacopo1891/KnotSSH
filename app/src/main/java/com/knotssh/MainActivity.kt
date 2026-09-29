package com.knotssh

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.AppearanceSettings
import com.knotssh.data.local.preferences.SecuritySettings
import com.knotssh.data.local.preferences.ThemeMode
import com.knotssh.presentation.navigation.NavGraph
import com.knotssh.presentation.security.BiometricGate
import com.knotssh.presentation.theme.KnotSshTheme
import com.knotssh.ssh.SessionRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AppCompat is required because BiometricPrompt attaches to a FragmentActivity. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var sessionRegistry: SessionRegistry

    /**
     * Null until DataStore has answered. The UI stays behind the splash screen meanwhile:
     * composing with the defaults would show unlocked content before we know a lock is set.
     */
    private val security = MutableStateFlow<SecuritySettings?>(null)

    /** Terminal requested from a session notification, consumed once by the nav graph. */
    private val pendingTerminal = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { security.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            appPreferences.security.collect { security.value = it }
        }

        pendingTerminal.value = intent?.terminalServerId()

        setContent {
            val appearance by appPreferences.appearance.collectAsState(initial = AppearanceSettings())
            val securitySettings by security.collectAsState()
            val isSystemDark = isSystemInDarkTheme()

            val isDark = when (appearance.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            // Screen capture blocking is app-wide so credential screens are covered too, and
            // stays on until the stored preference says otherwise.
            DisposableEffect(securitySettings?.allowScreenshot) {
                if (securitySettings?.allowScreenshot == true) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
                onDispose { }
            }

            KnotSshTheme(darkTheme = isDark, dynamicColor = appearance.dynamicColor) {
                securitySettings?.let { settings ->
                    BiometricGate(
                        enabled = settings.biometricEnabled,
                        graceSeconds = settings.biometricGraceSeconds
                    ) {
                        val navController = rememberNavController()
                        val terminalRequest by pendingTerminal.collectAsState()
                        NavGraph(
                            navController = navController,
                            terminalRequest = terminalRequest,
                            onTerminalRequestHandled = { pendingTerminal.value = null }
                        )
                    }
                }
            }
        }
    }

    /** singleTask delivers notification taps here when the activity is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.terminalServerId()?.let { pendingTerminal.value = it }
    }

    /**
     * The launcher activity is exported, so this extra is attacker-controlled. Honouring it only
     * for a session that is already running keeps it a "bring me back" hint: another app cannot
     * use it to open a brand new connection to a stored server.
     */
    private fun Intent.terminalServerId(): Long? =
        getLongExtra(EXTRA_TERMINAL_SERVER_ID, -1L)
            .takeIf { it > 0 && sessionRegistry.find(it) != null }

    companion object {
        const val EXTRA_TERMINAL_SERVER_ID = "navigate_to_terminal_server_id"
    }
}
