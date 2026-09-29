package com.knotssh.presentation.navigation

sealed class Screen(val route: String) {
    // Main destinations
    data object Home : Screen("home")
    data object Credentials : Screen("credentials")
    data object Settings : Screen("settings")

    // Server flow
    data object AddServer : Screen("server/add")
    data object EditServer : Screen("server/edit/{serverId}") {
        fun createRoute(serverId: Long) = "server/edit/$serverId"
    }

    // Credential flow
    data object AddCredential : Screen("credential/add")
    data object EditCredential : Screen("credential/edit/{credentialId}") {
        fun createRoute(credentialId: Long) = "credential/edit/$credentialId"
    }

    // Terminal
    data object Terminal : Screen("terminal/{serverId}") {
        fun createRoute(serverId: Long) = "terminal/$serverId"
    }

    // Settings categories
    data object AppearanceSettings : Screen("settings/appearance")
    data object TerminalSettings : Screen("settings/terminal")
    data object KeyboardSettings : Screen("settings/keyboard")
    data object ConnectionSettings : Screen("settings/connection")
    data object SecuritySettings : Screen("settings/security")
    data object BackupSettings : Screen("settings/backup")
}
