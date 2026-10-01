package com.knotssh.presentation.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knotssh.R

private val FAQ_ENTRIES = listOf(
    R.string.faq_q_storage to R.string.faq_a_storage,
    R.string.faq_q_host_key to R.string.faq_a_host_key,
    R.string.faq_q_policies to R.string.faq_a_policies,
    R.string.faq_q_strict_add to R.string.faq_a_strict_add,
    R.string.faq_q_ssh_key to R.string.faq_a_ssh_key,
    R.string.faq_q_background to R.string.faq_a_background,
    R.string.faq_q_sessions to R.string.faq_a_sessions,
    R.string.faq_q_port_forward to R.string.faq_a_port_forward,
    R.string.faq_q_migrate to R.string.faq_a_migrate,
    R.string.faq_q_bug to R.string.faq_a_bug
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    // Only one answer stays open at a time, so the list never turns into a wall of text.
    var expandedIndex by remember { mutableIntStateOf(-1) }

    SettingsScaffold(title = stringResource(R.string.settings_faq), onBack = onBack) {
        items(FAQ_ENTRIES.size) { index ->
            val (question, answer) = FAQ_ENTRIES[index]
            FaqItem(
                question = stringResource(question),
                answer = stringResource(answer),
                expanded = expandedIndex == index,
                onToggle = { expandedIndex = if (expandedIndex == index) -1 else index }
            )
        }
    }
}

@Composable
private fun FaqItem(
    question: String,
    answer: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
