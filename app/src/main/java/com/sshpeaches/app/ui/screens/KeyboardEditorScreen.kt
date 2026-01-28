package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
        PhoneOutlineDecoration(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyPalette() {
    val dropdownGroups = listOf(
        "Fn" to listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"),
        "a" to ('a'..'z').map { it.toString() },
        "A" to ('A'..'Z').map { it.toString() },
        "1" to listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        "#" to listOf("#", "@", "&", "%", "!", "?", "+", "=")
    )
    val quickKeys = listOf("Esc", "Tab", "Ctrl", "Alt", "Shift", "Swipe→Arrow", "Swipe→Scroll", "Editor")
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Palette", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickKeys.forEach { key ->
                    AssistChip(
                        onClick = {},
                        label = { Text(key) },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dropdownGroups.forEach { (label, keys) ->
                    KeyDropdown(label = label, keys = keys)
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

@Composable
private fun KeyDropdown(label: String, keys: List<String>) {
    val expanded = remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded.value = true }) {
            Text(label)
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            keys.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { expanded.value = false }
                )
            }
        }
    }
}

@Composable
private fun PhoneOutlineDecoration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            val width = size.width * 0.8f
            val left = (size.width - width) / 2
            val right = left + width
            val top = size.height * 0.4f
            val bottom = size.height * 0.8f
            val color = Color(0xFFB8B8B8)
            val stroke = Stroke(width = 4f)

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f),
                style = stroke
            )

            val rows = 12
            val cols = 3
            val cellWidth = width / cols
            val cellHeight = (bottom - top) / rows

            for (row in 1 until rows) {
                val y = top + row * cellHeight
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(left, y),
                    end = androidx.compose.ui.geometry.Offset(right, y),
                    strokeWidth = 2f
                )
            }
            for (col in 1 until cols) {
                val x = left + col * cellWidth
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(x, top),
                    end = androidx.compose.ui.geometry.Offset(x, bottom),
                    strokeWidth = 2f
                )
            }
        }
    }
}

private fun sampleRow(index: Int = 1): List<String> = when (index) {
    1 -> listOf("ESC", "TAB", "CTRL", "HOME", "END", "/", "-", "_", "Swipe→Arrow", "Swipe→Scroll", "Editor")
    2 -> listOf("F1", "F2", "F3", "F4", "Alt", "Shift")
    else -> listOf("Custom")
}
