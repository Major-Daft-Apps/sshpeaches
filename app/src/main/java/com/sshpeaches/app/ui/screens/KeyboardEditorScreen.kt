package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.R

@Composable
fun KeyboardEditorScreen() {
    val slotCount = 10
    val keysState = remember { mutableStateOf(List(slotCount) { "" }) }
    val dialogIndex = remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyboard Editor", style = MaterialTheme.typography.headlineSmall)
        Text("Tap a slot to add, replace, or remove a special key. One row, uniform tap targets.")

        // Single row (0%–40% vertical)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keysState.value.forEachIndexed { index, key ->
                KeySlot(
                    label = key,
                    onClick = { dialogIndex.value = index },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Keyboard image centered horizontally, middle band (about 40%–80%)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.keyboard),
                    contentDescription = "Keyboard illustration",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight(0.4f)
                )
            }
        }
    }

    dialogIndex.value?.let { idx ->
        KeySlotDialog(
            current = keysState.value[idx],
            onSelect = { newKey ->
                keysState.value = keysState.value.toMutableList().also { it[idx] = newKey }
                dialogIndex.value = null
            },
            onRemove = {
                keysState.value = keysState.value.toMutableList().also { it[idx] = "" }
                dialogIndex.value = null
            },
            onDismiss = { dialogIndex.value = null }
        )
    }
}

@Composable
private fun KeySlot(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isEmpty = label.isBlank()
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(if (isEmpty) "+" else label)
    }
}

@Composable
private fun KeySlotDialog(
    current: String,
    onSelect: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf(
        "Navigation" to listOf("Esc", "Tab", "Home", "End", "PgUp", "PgDn"),
        "Letters" to ('A'..'Z').map { it.toString() },
        "Numbers" to (0..9).map { it.toString() },
        "Symbols" to listOf("/", "-", "_", "|", "~", "@", "#", "%", "&", "+", "=")
    )
    val metaKeys = listOf("Ctrl", "Alt", "Shift", "Super")
    val currentCategory = remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current.isBlank()) "Add key" else "Edit key") },
        text = {
            if (currentCategory.value == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Meta keys shown directly
                    Text("Meta keys", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        metaKeys.forEach { key ->
                            TextButton(onClick = { onSelect(key) }) { Text(key) }
                        }
                    }
                    // Category rows with arrows
                    categories.forEach { (cat, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, style = MaterialTheme.typography.bodyLarge)
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Open $cat",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Transparent)
                                    .padding(2.dp)
                                    .let {
                                        Modifier
                                    }
                                    .clickable {
                                        currentCategory.value = cat
                                    }
                            )
                        }
                    }
                }
            } else {
                val items = categories.firstOrNull { it.first == currentCategory.value }?.second.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { currentCategory.value = null }) { Text("← Back") }
                    Text(currentCategory.value ?: "", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.chunked(4).forEach { chunk ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunk.forEach { key ->
                                    TextButton(onClick = { onSelect(key) }) { Text(key) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (current.isNotBlank()) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
