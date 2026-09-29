package com.knotssh.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knotssh.R

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    // Changing the locale recreates the activity, so the value is read back on every entry.
    var selected by remember { mutableStateOf(LocaleManager.current()) }

    SettingsScaffold(title = stringResource(R.string.settings_language), onBack = onBack) {
        item {
            Text(
                text = stringResource(R.string.language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(AppLanguage.entries.size) { index ->
            val language = AppLanguage.entries[index]
            LanguageRow(
                label = if (language == AppLanguage.SYSTEM) {
                    stringResource(R.string.language_system)
                } else {
                    language.displayName
                },
                selected = language == selected,
                onClick = {
                    selected = language
                    LocaleManager.apply(language)
                }
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
