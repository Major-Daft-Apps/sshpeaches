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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val slotCount = 12
    val keysState = remember { mutableStateOf(List(slotCount) { "" }) }
    val dialogIndex = remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyboard Editor", style = MaterialTheme.typography.headlineSmall)
        Text("Tap a slot to add, replace, or remove a special key.")
        Text("The keys will be resized to fit on the screen.")

        // Force a single horizontal row; allow scrolling if it overflows
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .border(1.dp, Color(0xFFB8B8B8), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keysState.value.forEachIndexed { index, key ->
                KeySlot(
                    label = key,
                    onClick = { dialogIndex.value = index }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.keyboard),
                    contentDescription = "Keyboard illustration",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
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
private fun KeySlot(label: String, onClick: () -> Unit) {
    val isEmpty = label.isBlank()
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .sizeIn(minWidth = 56.dp)
            .clip(RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFA992A)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFFA992A)
        )
    ) {
        Text(if (isEmpty) "+" else label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySlotDialog(
    current: String,
    onSelect: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf(
        "Uppercase" to ('A'..'Z').map { it.toString() },
        "Lowercase" to ('a'..'z').map { it.toString() },
        "Numbers" to (0..9).map { it.toString() },
        "Symbols" to listOf("/", "-", "_", "|", "~", "@", "#", "%", "&", "+", "=")
    )
    val metaKeys = listOf("Ctrl", "Alt", "Shift", "Super")
    val navigationKeys = listOf("Esc", "Tab", "Home", "End", "PgUp", "PgDn")
    val currentCategory = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current.isBlank()) "Add key" else "Edit key") },
        text = {
            val outerScroll = rememberScrollState()
            if (currentCategory.value == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .verticalScroll(outerScroll)
                        .sizeIn(maxHeight = 420.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            metaKeys.forEach { key ->
                                TextButton(onClick = { onSelect(key) }) { Text(key) }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            navigationKeys.forEach { key ->
                                TextButton(onClick = { onSelect(key) }) { Text(key) }
                            }
                        }
                    }
                    categories.forEach { (cat, _) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, style = MaterialTheme.typography.bodyLarge)
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open $cat",
                                modifier = Modifier
                                    .clickable { currentCategory.value = cat }
                            )
                        }
                    }
                }
            } else {
                val items = categories.firstOrNull { it.first == currentCategory.value }?.second.orEmpty()
                val scroll = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .verticalScroll(scroll)
                        .sizeIn(maxHeight = 420.dp)
                ) {
                    TextButton(onClick = { currentCategory.value = null }) { Text("<- Back") }
                    Text(currentCategory.value ?: "", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { key ->
                            TextButton(onClick = { onSelect(key) }) { Text(key) }
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
