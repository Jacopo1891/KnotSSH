package com.knotssh.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.knotssh.R

/** Top-level destinations that share the bottom navigation bar. Settings lives in the top bar. */
enum class MainDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    SERVERS(Screen.Home.route, R.string.nav_servers, Icons.Outlined.Dns, Icons.Filled.Dns),
    ACCOUNTS(Screen.Credentials.route, R.string.nav_accounts, Icons.Outlined.Key, Icons.Filled.Key);

    companion object {
        fun isTopLevel(route: String?) = entries.any { it.route == route }
    }
}

@Composable
fun MainNavigationBar(
    current: MainDestination,
    onSelect: (MainDestination) -> Unit
) {
    NavigationBar {
        MainDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.label)) }
            )
        }
    }
}
