package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun KeyboardEditorScreen() {
    val rows = remember { mutableStateListOf(sampleRow()) }
    val compactMode = remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyboard Editor", style = MaterialTheme.typography.headlineSmall)
        Text("Drag items from the palette to customize up to three rows. Swipe-to-arrow and swipe-to-scroll toggles stay available by default.")
        KeyPalette()
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Compact Keys")
                    Switch(checked = compactMode.value, onCheckedChange = { compactMode.value = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        if (rows.size < 3) rows.add(sampleRow(rows.size + 1))
                    }) {
                        Text("Add Row")
                    }
                    OutlinedButton(onClick = { if (rows.size > 1) rows.removeLast() }) {
                        Text("Remove Row")
                    }
                }
            }
        }
        rows.forEachIndexed { index, row ->
            KeyRowCard(index = index + 1, keys = row)
        }
    }
}

@Composable
private fun KeyPalette() {
    val groups = listOf(
        "Fn" to listOf("F1", "F2", "F3", "F4"),
        "a" to listOf("a", "b", "c"),
        "A" to listOf("A", "B", "C"),
        "1" to listOf("1", "2", "3"),
        "#" to listOf("#", "@", "&")
    )
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Palette", style = MaterialTheme.typography.titleMedium)
            groups.forEach { (label, keys) ->
                Text(label, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        AssistChip(
                            onClick = {},
                            label = { Text(key) },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyRowCard(index: Int, keys: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Row $index", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    Text(
                        key,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun sampleRow(index: Int = 1): List<String> = when (index) {
    1 -> listOf("ESC", "TAB", "CTRL", "HOME", "END", "/", "-", "_", "Swipe→Arrow", "Swipe→Scroll", "Editor")
    2 -> listOf("F1", "F2", "F3", "F4", "Alt", "Shift")
    else -> listOf("Custom")
}
