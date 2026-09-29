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
import androidx.navigation.compose.rememberNavController
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.data.local.preferences.AppearanceSettings
import com.knotssh.data.local.preferences.SecuritySettings
import com.knotssh.data.local.preferences.ThemeMode
import com.knotssh.presentation.navigation.NavGraph
import com.knotssh.presentation.security.BiometricGate
import com.knotssh.presentation.theme.KnotSshTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** AppCompat is required because BiometricPrompt attaches to a FragmentActivity. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    /** Terminal requested from a session notification, consumed once by the nav graph. */
    private val pendingTerminal = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingTerminal.value = intent?.terminalServerId()

        setContent {
            val appearance by appPreferences.appearance.collectAsState(initial = AppearanceSettings())
            val security by appPreferences.security.collectAsState(initial = SecuritySettings())
            val isSystemDark = isSystemInDarkTheme()

            val isDark = when (appearance.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            // Screen capture blocking is app-wide so credential screens are covered too.
            DisposableEffect(security.allowScreenshot) {
                if (security.allowScreenshot) {
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
                BiometricGate(
                    enabled = security.biometricEnabled,
                    graceSeconds = security.biometricGraceSeconds
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

    /** singleTask delivers notification taps here when the activity is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.terminalServerId()?.let { pendingTerminal.value = it }
    }

    private fun Intent.terminalServerId(): Long? =
        getLongExtra(EXTRA_TERMINAL_SERVER_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_TERMINAL_SERVER_ID = "navigate_to_terminal_server_id"
    }
}
