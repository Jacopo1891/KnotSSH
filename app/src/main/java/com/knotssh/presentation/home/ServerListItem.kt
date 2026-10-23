package com.knotssh.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotssh.R
import com.knotssh.data.local.preferences.ServerListStyle
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.Server
import com.knotssh.presentation.server.ServerIcon
import com.knotssh.presentation.server.ServerIconImage
import com.knotssh.presentation.theme.SshAmber
import com.knotssh.presentation.theme.SshGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the text column starts, used to inset the dividers between rows. */
fun ServerListStyle.dividerInset() = when (this) {
    ServerListStyle.EXTENDED -> 72.dp
    ServerListStyle.COMPACT -> 16.dp
}

/**
 * One server row. Shared by the Home list and the preview in the appearance settings, so what
 * the user previews is exactly what the list shows.
 *
 * The whole row is the click target; [onLongClick] is `null` while dragging owns the gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerListItem(
    server: Server,
    credential: Credential?,
    hasActiveSession: Boolean,
    style: ServerListStyle,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The credential it referenced was deleted: connecting would only fail, so the caller sends
    // the user to the editor instead, and the row is tinted to say why.
    val orphaned = credential == null
    val extended = style == ServerListStyle.EXTENDED

    val account = if (credential != null) {
        credential.alias.ifBlank { credential.username }
    } else {
        stringResource(R.string.home_no_credential)
    }
    val subtitleColor = if (orphaned) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            // Opaque, so the swipe-to-delete background stays hidden until the row moves.
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = 16.dp,
                end = 4.dp,
                top = if (extended) 12.dp else 8.dp,
                bottom = if (extended) 12.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (extended) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                ServerIconImage(
                    icon = ServerIcon.fromKey(server.iconKey),
                    tint = if (orphaned) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    size = 28.dp
                )
                if (hasActiveSession) {
                    // Ringed with the surface colour so the dot reads over any icon.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        ActiveDot(10.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = server.alias,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Without an icon to carry the badge, the dot sits beside the name.
                if (!extended && hasActiveSession) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ActiveDot(8.dp)
                }
            }
            Text(
                text = "$account · ${server.hostname}:${server.port}",
                style = if (extended) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (extended) {
                val details = listOfNotNull(
                    server.lastConnectedMs?.let {
                        stringResource(R.string.home_last_connection, formatLastConnected(it))
                    },
                    server.portForwardRules.takeIf { it.isNotEmpty() }?.let {
                        pluralStringResource(R.plurals.port_forward_rules, it.size, it.size)
                    }
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (server.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                contentDescription = if (server.isFavorite) {
                    stringResource(R.string.action_unfavorite)
                } else {
                    stringResource(R.string.action_favorite)
                },
                tint = if (server.isFavorite) SshAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveDot(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(SshGreen)
    )
}

@Composable
fun formatLastConnected(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> stringResource(R.string.time_now)
        diff < 3_600_000 -> stringResource(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> stringResource(R.string.time_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> {
            val days = (diff / 86_400_000).toInt()
            pluralStringResource(R.plurals.days_ago, days, days)
        }
        else -> remember(ms) {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
        }
    }
}
