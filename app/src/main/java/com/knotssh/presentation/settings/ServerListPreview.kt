package com.knotssh.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knotssh.R
import com.knotssh.data.local.preferences.ServerListStyle
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.Server
import com.knotssh.presentation.home.ServerListItem
import com.knotssh.presentation.home.dividerInset
import java.util.concurrent.TimeUnit

/**
 * Two sample servers drawn with the very same row the Home list uses, so changing the style
 * shows its effect immediately. The sample data is invented and never stored.
 */
@Composable
fun ServerListPreview(style: ServerListStyle, modifier: Modifier = Modifier) {
    val samples = remember {
        val now = System.currentTimeMillis()
        listOf(
            Server(
                id = 1,
                alias = "Production",
                hostname = "prod.example.com",
                credentialId = 1,
                lastConnectedMs = now - TimeUnit.HOURS.toMillis(2),
                isFavorite = true,
                iconKey = "ubuntu"
            ) to Credential(id = 1, alias = "deploy", username = "deploy", authType = AuthType.SSH_KEY),
            Server(
                id = 2,
                alias = "Home NAS",
                hostname = "192.168.1.20",
                credentialId = 2,
                lastConnectedMs = now - TimeUnit.DAYS.toMillis(3),
                iconKey = "synology"
            ) to Credential(id = 2, alias = "admin", username = "admin", authType = AuthType.PASSWORD)
        )
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.appearance_preview),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                samples.forEachIndexed { index, (server, credential) ->
                    ServerListItem(
                        server = server,
                        credential = credential,
                        // The first row also shows how a live session is marked.
                        hasActiveSession = index == 0,
                        style = style,
                        onClick = {},
                        onLongClick = null,
                        onToggleFavorite = {}
                    )
                    if (index < samples.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = style.dividerInset(), end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
