package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyState(
    itemLabel: String,
    modifier: Modifier = Modifier,
    showCreateHint: Boolean = true
) {
    val pluralLabel = when {
        itemLabel.endsWith("y", ignoreCase = true) && itemLabel.length > 1 -> {
            val stem = itemLabel.dropLast(1)
            val prev = itemLabel[itemLabel.length - 2].lowercaseChar()
            if (prev in listOf('a', 'e', 'i', 'o', 'u')) "${itemLabel}s" else "${stem}ies"
        }
        else -> "${itemLabel}s"
    }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = Color.Gray.copy(alpha = 0.4f)
        )
        Text(
            text = if (showCreateHint) {
                "You don't have any $pluralLabel. Tap \"New\" to create one."
            } else {
                "You don't have any $pluralLabel."
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
        )
    }
}
