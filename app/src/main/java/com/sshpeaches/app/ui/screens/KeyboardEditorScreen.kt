package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction

@Composable
fun KeyboardEditorScreen(
    slots: List<KeyboardSlotAction>,
    onSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onReset: () -> Unit
) {
    val dialogIndex = remember { mutableStateOf<Int?>(null) }
    val normalizedSlots = remember(slots) { KeyboardLayoutDefaults.normalizeSlots(slots) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyboard Editor", style = MaterialTheme.typography.headlineSmall)
        Text("Tap a slot to assign text, navigation keys, modifiers, function keys, combos, or sequences.")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFA992A), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            normalizedSlots
                .chunked(KeyboardLayoutDefaults.SLOT_COLUMNS)
                .forEachIndexed { rowIndex, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEachIndexed { columnIndex, action ->
                            val index = rowIndex * KeyboardLayoutDefaults.SLOT_COLUMNS + columnIndex
                            KeySlot(
                                label = KeyboardLayoutDefaults.compactLabel(action, fallback = "+"),
                                active = !action.isEmpty(),
                                onClick = { dialogIndex.value = index }
                            )
                        }
                    }
                }
        }

        Text(
            "One-shot modifiers apply to the next key or typed text, then clear automatically.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Tip: add F1-F12 from the Function section for tmux, htop, and remote tooling.",
            style = MaterialTheme.typography.bodySmall
        )

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

        TextButton(onClick = {
            onReset()
        }) {
            Text("Reset layout")
        }
    }

    val handleSlotChange: (Int, KeyboardSlotAction) -> Unit = { idx, value ->
        onSlotChange(idx, value)
    }

    dialogIndex.value?.let { idx ->
        KeySlotDialog(
            current = normalizedSlots.getOrNull(idx) ?: KeyboardLayoutDefaults.emptyAction(),
            onSelect = { newKey ->
                handleSlotChange(idx, newKey)
                dialogIndex.value = null
            },
            onRemove = {
                handleSlotChange(idx, KeyboardLayoutDefaults.emptyAction())
                dialogIndex.value = null
            },
            onDismiss = { dialogIndex.value = null }
        )
    }
}

@Composable
private fun RowScope.KeySlot(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF474747), RoundedCornerShape(5.dp))
            .background(if (active) Color(0xFF121212) else Color(0xFF0A0A0A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
            fontSize = KeyboardLayoutDefaults.COMPACT_KEY_FONT_SP.sp,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySlotDialog(
    current: KeyboardSlotAction,
    onSelect: (KeyboardSlotAction) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val textDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.TEXT) current.text else "")
    }
    val sequenceDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.SEQUENCE) current.sequence else "")
    }
    val comboKeyDraft = remember(current) {
        mutableStateOf(
            if (current.type == KeyboardActionType.KEY) {
                KeyboardLayoutDefaults.keyTokenForAction(current)
            } else {
                ""
            }
        )
    }
    val comboLabelDraft = remember(current) {
        mutableStateOf(
            if (current.type == KeyboardActionType.KEY && (current.ctrl || current.alt || current.shift)) {
                current.label
            } else {
                ""
            }
        )
    }
    val comboCtrl = remember(current) {
        mutableStateOf(current.type == KeyboardActionType.KEY && current.ctrl)
    }
    val comboAlt = remember(current) {
        mutableStateOf(current.type == KeyboardActionType.KEY && current.alt)
    }
    val comboShift = remember(current) {
        mutableStateOf(current.type == KeyboardActionType.KEY && current.shift)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current.isEmpty()) "Add key action" else "Edit key action") },
        text = {
            val outerScroll = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(outerScroll)
                    .sizeIn(maxHeight = 460.dp)
            ) {
                Text(
                    "Current: ${KeyboardLayoutDefaults.compactLabel(current, fallback = "Empty")}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("Text", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = textDraft.value,
                    onValueChange = { textDraft.value = it },
                    label = { Text("Text payload") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = textDraft.value.isNotBlank(),
                    onClick = { onSelect(KeyboardLayoutDefaults.textAction(textDraft.value)) }
                ) {
                    Text("Use Text")
                }

                Text("Modifiers", style = MaterialTheme.typography.labelLarge)
                PresetRow(KeyboardLayoutDefaults.modifierPresets, onSelect)

                Text("Navigation", style = MaterialTheme.typography.labelLarge)
                PresetRow(KeyboardLayoutDefaults.navigationPresets, onSelect)

                Text("Function", style = MaterialTheme.typography.labelLarge)
                PresetRow(KeyboardLayoutDefaults.functionPresets, onSelect)

                Text("Combinations", style = MaterialTheme.typography.labelLarge)
                PresetRow(KeyboardLayoutDefaults.comboPresets, onSelect)

                OutlinedTextField(
                    value = comboKeyDraft.value,
                    onValueChange = { comboKeyDraft.value = it },
                    label = { Text("Combo base key (A, TAB, ESC, F1...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModifierToggle(
                        label = "Ctrl",
                        active = comboCtrl.value,
                        onToggle = { comboCtrl.value = !comboCtrl.value }
                    )
                    ModifierToggle(
                        label = "Alt",
                        active = comboAlt.value,
                        onToggle = { comboAlt.value = !comboAlt.value }
                    )
                    ModifierToggle(
                        label = "Shift",
                        active = comboShift.value,
                        onToggle = { comboShift.value = !comboShift.value }
                    )
                }
                OutlinedTextField(
                    value = comboLabelDraft.value,
                    onValueChange = { comboLabelDraft.value = it },
                    label = { Text("Custom combo label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val comboAction = KeyboardLayoutDefaults.combinationAction(
                    keyToken = comboKeyDraft.value,
                    ctrl = comboCtrl.value,
                    alt = comboAlt.value,
                    shift = comboShift.value,
                    customLabel = comboLabelDraft.value
                )
                TextButton(
                    enabled = comboAction != null && (comboCtrl.value || comboAlt.value || comboShift.value),
                    onClick = { comboAction?.let(onSelect) }
                ) {
                    Text("Use Combo")
                }

                Text("Sequences", style = MaterialTheme.typography.labelLarge)
                PresetRow(KeyboardLayoutDefaults.sequencePresets, onSelect)

                OutlinedTextField(
                    value = sequenceDraft.value,
                    onValueChange = { sequenceDraft.value = it },
                    label = { Text("Custom sequence") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = sequenceDraft.value.isNotBlank(),
                    onClick = {
                        onSelect(KeyboardLayoutDefaults.sequenceAction("Seq", sequenceDraft.value))
                    }
                ) {
                    Text("Use Sequence")
                }
            }
        },
        confirmButton = {
            if (!current.isEmpty()) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModifierToggle(
    label: String,
    active: Boolean,
    onToggle: () -> Unit
) {
    TextButton(onClick = onToggle) {
        Text(if (active) "[x] $label" else "[ ] $label")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    presets: List<KeyboardSlotAction>,
    onSelect: (KeyboardSlotAction) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { action ->
            TextButton(onClick = { onSelect(action) }) {
                Text(action.label)
            }
        }
    }
}
