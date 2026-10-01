package com.knotssh.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.knotssh.presentation.credentials.CredentialsScreen
import com.knotssh.presentation.credentials.EditCredentialScreen
import com.knotssh.presentation.home.HomeScreen
import com.knotssh.presentation.server.EditServerScreen
import com.knotssh.presentation.settings.AboutScreen
import com.knotssh.presentation.settings.AppearanceSettingsScreen
import com.knotssh.presentation.settings.BackupSettingsScreen
import com.knotssh.presentation.settings.ConnectionSettingsScreen
import com.knotssh.presentation.settings.FaqScreen
import com.knotssh.presentation.settings.KeyboardSettingsScreen
import com.knotssh.presentation.settings.LanguageSettingsScreen
import com.knotssh.presentation.settings.SecuritySettingsScreen
import com.knotssh.presentation.settings.SettingsScreen
import com.knotssh.presentation.settings.TerminalSettingsScreen
import com.knotssh.presentation.terminal.TerminalScreen

private const val ANIM_DURATION = 300

@Composable
fun NavGraph(
    navController: NavHostController,
    terminalRequest: Long? = null,
    onTerminalRequestHandled: () -> Unit = {}
) {
    LaunchedEffect(terminalRequest) {
        terminalRequest?.let {
            // Replace whatever terminal is showing instead of stacking one entry per notification
            // tap: a stale entry would reconnect its server as soon as the user pressed back.
            navController.navigate(Screen.Terminal.createRoute(it)) {
                popUpTo(Screen.Home.route)
                launchSingleTop = true
            }
            onTerminalRequestHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(ANIM_DURATION)
            ) + fadeOut(tween(ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(ANIM_DURATION)
            ) + fadeOut(tween(ANIM_DURATION))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddServer = { navController.navigate(Screen.AddServer.route) },
                onEditServer = { id -> navController.navigate(Screen.EditServer.createRoute(id)) },
                onConnect = { id -> navController.navigate(Screen.Terminal.createRoute(id)) },
                onNavigateCredentials = { navController.navigate(Screen.Credentials.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AddServer.route) {
            EditServerScreen(
                serverId = null,
                onBack = { navController.popBackStack() },
                onNavigateToAddCredential = { navController.navigate(Screen.AddCredential.route) }
            )
        }

        composable(
            route = Screen.EditServer.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) { backStack ->
            EditServerScreen(
                serverId = backStack.arguments?.getLong("serverId"),
                onBack = { navController.popBackStack() },
                onNavigateToAddCredential = { navController.navigate(Screen.AddCredential.route) }
            )
        }

        composable(Screen.Credentials.route) {
            CredentialsScreen(
                onBack = { navController.popBackStack() },
                onAddCredential = { navController.navigate(Screen.AddCredential.route) },
                onEditCredential = { id -> navController.navigate(Screen.EditCredential.createRoute(id)) }
            )
        }

        composable(Screen.AddCredential.route) {
            EditCredentialScreen(
                credentialId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditCredential.route,
            arguments = listOf(navArgument("credentialId") { type = NavType.LongType })
        ) { backStack ->
            EditCredentialScreen(
                credentialId = backStack.arguments?.getLong("credentialId"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Screen.AppearanceSettings.route) },
                onOpenTerminal = { navController.navigate(Screen.TerminalSettings.route) },
                onOpenKeyboard = { navController.navigate(Screen.KeyboardSettings.route) },
                onOpenConnection = { navController.navigate(Screen.ConnectionSettings.route) },
                onOpenSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                onOpenLanguage = { navController.navigate(Screen.LanguageSettings.route) },
                onOpenBackup = { navController.navigate(Screen.BackupSettings.route) },
                onOpenFaq = { navController.navigate(Screen.Faq.route) },
                onOpenAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.TerminalSettings.route) {
            TerminalSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.KeyboardSettings.route) {
            KeyboardSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.ConnectionSettings.route) {
            ConnectionSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SecuritySettings.route) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.LanguageSettings.route) {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.BackupSettings.route) {
            BackupSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Faq.route) {
            FaqScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) {
            TerminalScreen(onBack = { navController.popBackStack() })
        }
    }
}
