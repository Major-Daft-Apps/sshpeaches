package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction

@Composable
fun KeyboardEditorScreen(
    slots: List<KeyboardSlotAction>,
    onSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onReset: () -> Unit
) {
    val editorIndex = remember { mutableStateOf<Int?>(null) }
    val normalizedSlots = remember(slots) { KeyboardLayoutDefaults.normalizeSlots(slots) }
    val activeEditorIndex = editorIndex.value

    if (activeEditorIndex != null) {
        val current = normalizedSlots.getOrNull(activeEditorIndex) ?: KeyboardLayoutDefaults.emptyAction()
        KeyActionEditorVertical(
            slotIndex = activeEditorIndex,
            current = current,
            onApply = { action ->
                onSlotChange(activeEditorIndex, action)
                editorIndex.value = null
            },
            onRemove = {
                onSlotChange(activeEditorIndex, KeyboardLayoutDefaults.emptyAction())
                editorIndex.value = null
            },
            onCancel = { editorIndex.value = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyboard Editor", style = MaterialTheme.typography.headlineSmall)
        Text("Tap a slot to open the full key-action editor vertical.")

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
                                action = action,
                                active = !action.isEmpty(),
                                onClick = { editorIndex.value = index }
                            )
                        }
                    }
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

        Text(
            "Modifier keys and combination toggles co-exist: modifiers send plain Ctrl/Alt/Shift; combination toggles apply to the next selected base key.",
            style = MaterialTheme.typography.bodySmall
        )

        TextButton(onClick = onReset) {
            Text("Reset layout")
        }
    }
}

@Composable
private fun RowScope.KeySlot(
    action: KeyboardSlotAction,
    active: Boolean,
    onClick: () -> Unit
) {
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
        val icon = KeyboardIconPack.byId(action.iconId)
        if (icon != null) {
            Icon(
                imageVector = icon.icon,
                contentDescription = icon.label,
                tint = if (active) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = KeyboardLayoutDefaults.compactLabel(action, fallback = "+"),
                color = if (active) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
                fontSize = KeyboardLayoutDefaults.COMPACT_KEY_FONT_SP.sp,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyActionEditorVertical(
    slotIndex: Int,
    current: KeyboardSlotAction,
    onApply: (KeyboardSlotAction) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    val scrollState = rememberScrollState()
    val comboCtrl = remember(current) { mutableStateOf(current.type == KeyboardActionType.KEY && current.ctrl) }
    val comboAlt = remember(current) { mutableStateOf(current.type == KeyboardActionType.KEY && current.alt) }
    val comboShift = remember(current) { mutableStateOf(current.type == KeyboardActionType.KEY && current.shift) }
    val selectedIconId = remember(current) { mutableStateOf(current.iconId) }
    val textDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.TEXT) current.text else "")
    }
    val sequenceDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.SEQUENCE) current.sequence else "")
    }
    val advancedExpanded = remember { mutableStateOf(false) }

    val applyKeyAction: (KeyboardSlotAction) -> Unit = { base ->
        onApply(
            withCombinationAndIcon(
                base = base,
                ctrl = comboCtrl.value,
                alt = comboAlt.value,
                shift = comboShift.value,
                iconId = selectedIconId.value
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Edit Key Action", style = MaterialTheme.typography.headlineSmall)
            }
            Text("Slot ${slotIndex + 1}", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "Current: ${fullActionLabel(current)}",
            style = MaterialTheme.typography.bodySmall
        )

        SectionTitle("Modifiers")
        PresetRow(KeyboardLayoutDefaults.modifierPresets) { preset ->
            onApply(preset.copy(iconId = selectedIconId.value))
        }

        SectionTitle("Combination")
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
        Text(
            "Pick any base key below. Active combination toggles are applied to that key.",
            style = MaterialTheme.typography.bodySmall
        )

        SectionTitle("Letters")
        PresetRow(KeyboardLayoutDefaults.letterPresets, applyKeyAction)

        SectionTitle("Digits")
        PresetRow(KeyboardLayoutDefaults.digitPresets, applyKeyAction)

        SectionTitle("Special Characters")
        PresetRow(KeyboardLayoutDefaults.punctuationPresets, applyKeyAction)

        SectionTitle("Whitespace/Editing")
        PresetRow(KeyboardLayoutDefaults.whitespaceEditingPresets, applyKeyAction)

        SectionTitle("Navigation")
        PresetRow(KeyboardLayoutDefaults.navigationPresets, applyKeyAction)

        SectionTitle("Function Keys")
        PresetRow(KeyboardLayoutDefaults.functionPresets, applyKeyAction)

        SectionTitle("Numpad")
        PresetRow(KeyboardLayoutDefaults.numpadPresets, applyKeyAction)

        SectionTitle("Lock/System")
        PresetRow(KeyboardLayoutDefaults.lockSystemPresets, applyKeyAction)

        SectionTitle("Text")
        OutlinedTextField(
            value = textDraft.value,
            onValueChange = { textDraft.value = it },
            label = { Text("Text payload") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            enabled = textDraft.value.isNotBlank(),
            onClick = {
                onApply(
                    KeyboardLayoutDefaults.textAction(textDraft.value).copy(iconId = selectedIconId.value)
                )
            }
        ) {
            Text("Use Text")
        }

        SectionTitle("Icon")
        IconPresetRow(
            selectedIconId = selectedIconId.value,
            onSelect = { selectedIconId.value = it }
        )

        TextButton(onClick = { advancedExpanded.value = !advancedExpanded.value }) {
            Icon(
                imageVector = if (advancedExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Text(if (advancedExpanded.value) "Advanced (hide)" else "Advanced")
        }

        if (advancedExpanded.value) {
            SectionTitle("VT100/xterm Sequences")
            PresetRow(KeyboardLayoutDefaults.advancedSequencePresets) { preset ->
                onApply(preset.copy(iconId = selectedIconId.value))
            }

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
                    onApply(
                        KeyboardLayoutDefaults.sequenceAction("Seq", sequenceDraft.value)
                            .copy(iconId = selectedIconId.value)
                    )
                }
            ) {
                Text("Use Custom Sequence")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            if (!current.isEmpty()) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

private fun withCombinationAndIcon(
    base: KeyboardSlotAction,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    iconId: String
): KeyboardSlotAction {
    val withIcon = base.copy(iconId = iconId)
    if (withIcon.type != KeyboardActionType.KEY) return withIcon
    if (!ctrl && !alt && !shift) return withIcon

    val modifiers = mutableListOf<String>()
    if (ctrl) modifiers += "Ctrl"
    if (alt) modifiers += "Alt"
    if (shift) modifiers += "Shift"
    val baseLabel = withIcon.label.ifBlank {
        KeyboardLayoutDefaults.keyTokenForAction(withIcon).ifBlank { "Key" }
    }
    val combinedLabel = "${modifiers.joinToString("+")}-$baseLabel"
    return withIcon.copy(
        label = combinedLabel,
        ctrl = withIcon.ctrl || ctrl,
        alt = withIcon.alt || alt,
        shift = withIcon.shift || shift
    )
}

private fun fullActionLabel(action: KeyboardSlotAction): String {
    if (action.isEmpty()) return "Empty"
    val label = action.label.trim()
    if (label.isNotBlank()) return label
    return when (action.type) {
        KeyboardActionType.TEXT -> action.text.trim().ifBlank { "Empty" }
        KeyboardActionType.KEY -> KeyboardLayoutDefaults.keyTokenForAction(action).ifBlank { "Key" }
        KeyboardActionType.MODIFIER -> "Modifier"
        KeyboardActionType.SEQUENCE -> action.sequence.trim().ifBlank { "Sequence" }
    }
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
                val icon = KeyboardIconPack.byId(action.iconId)
                if (icon != null) {
                    Icon(icon.icon, contentDescription = icon.label, modifier = Modifier.size(16.dp))
                } else {
                    Text(action.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPresetRow(
    selectedIconId: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = { onSelect("") }) {
            Text(if (selectedIconId.isBlank()) "[x] Text" else "Text")
        }
        KeyboardIconPack.icons.forEach { spec ->
            TextButton(onClick = { onSelect(spec.id) }) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.label,
                    modifier = Modifier.size(16.dp)
                )
                Text(if (selectedIconId == spec.id) " [x]" else "")
            }
        }
    }
}
