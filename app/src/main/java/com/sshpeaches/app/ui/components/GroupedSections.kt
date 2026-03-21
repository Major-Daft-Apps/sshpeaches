package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import java.util.Locale

const val UNGROUPED_LABEL = "Ungrouped"

data class GroupedSection<T>(
    val key: String,
    val label: String,
    val items: List<T>
)

fun normalizedGroupLabel(group: String?): String =
    group?.trim()?.takeIf { it.isNotEmpty() } ?: UNGROUPED_LABEL

fun normalizedGroupKey(group: String?): String =
    normalizedGroupLabel(group).lowercase(Locale.ROOT)

fun <T> buildGroupedSections(
    items: List<T>,
    groupSelector: (T) -> String?,
    itemComparator: Comparator<in T>
): List<GroupedSection<T>> =
    items
        .groupBy { normalizedGroupLabel(groupSelector(it)) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (label, groupedItems) ->
            GroupedSection(
                key = normalizedGroupKey(label),
                label = label,
                items = groupedItems.sortedWith(itemComparator)
            )
        }

@Composable
fun GroupSectionHeader(
    vertical: String,
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UiTestTags.groupHeader(vertical, label)),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (count == 1) "1 item" else "$count items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.testTag(UiTestTags.groupToggle(vertical, label))
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse group" else "Expand group"
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UiTestTags.DELETE_CONFIRM_DIALOG),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(UiTestTags.DELETE_CONFIRM_BUTTON)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(UiTestTags.DELETE_CANCEL_BUTTON)
            ) {
                Text("Cancel")
            }
        }
    )
}
