package com.knotssh.presentation.server

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knotssh.R
import com.knotssh.presentation.components.SearchPill
/** Bottom sheet listing every [ServerIcon] by group. Picking the default icon clears the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerIconPickerSheet(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = ServerIcon.fromKey(selectedKey)
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    // Matches the visible name, the stored key (so "pihole" finds "Pi-hole") and the group name.
    val visible = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            ServerIcon.ALL
        } else {
            ServerIcon.ALL.filter { icon ->
                val name = icon.name ?: context.getString(icon.nameRes)
                name.contains(needle, ignoreCase = true) ||
                    icon.key.contains(needle, ignoreCase = true) ||
                    context.getString(icon.group.label).contains(needle, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.imePadding()) {
            Text(
                text = stringResource(R.string.server_icon_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            SearchPill(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.server_icon_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.server_icon_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .weight(1f, fill = false)
            ) {
                ServerIconGroup.entries.forEach { group ->
                    val inGroup = visible.filter { it.group == group }
                    if (inGroup.isEmpty()) return@forEach
                    item(key = "group_${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(group.label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(inGroup, key = { it.key }) { icon ->
                        IconCell(
                            icon = icon,
                            selected = icon.key == selected.key,
                            onClick = {
                                onSelect(icon.key.takeUnless { icon == ServerIcon.DEFAULT })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCell(icon: ServerIcon, selected: Boolean, onClick: () -> Unit) {
    val name = icon.name ?: stringResource(icon.nameRes)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = name
            }
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                ServerIconImage(
                    icon = icon,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    size = 28.dp
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Two lines, so longer translated names ("Macchina virtuale") wrap instead of being cut.
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}
