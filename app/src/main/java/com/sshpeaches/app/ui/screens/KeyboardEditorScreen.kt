package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun KeyboardEditorScreen(
    slots: List<KeyboardSlotAction>,
    onSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onReset: () -> Unit
) {
    val editorIndex = remember { mutableStateOf<Int?>(null) }
    val normalizedSlots = remember(slots) { KeyboardLayoutDefaults.normalizeSlots(slots) }
    val keyBlockHeightPx = remember { mutableIntStateOf(0) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_KEYBOARD)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tap a slot to open the full key-action editor.")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFA992A), RoundedCornerShape(8.dp))
                    .onSizeChanged { keyBlockHeightPx.intValue = it.height }
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
                                    index = index,
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
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val density = LocalDensity.current
                    val keyBlockHeight = with(density) {
                        if (keyBlockHeightPx.intValue > 0) keyBlockHeightPx.intValue.toDp()
                        else KEYBOARD_ILLUSTRATION_FALLBACK_HEIGHT
                    }
                    val maxIllustrationHeight = keyBlockHeight * KEYBOARD_ILLUSTRATION_MAX_HEIGHT_MULTIPLIER
                    val illustrationWidth = maxWidth.coerceAtMost(KEYBOARD_ILLUSTRATION_MAX_WIDTH)
                    val naturalHeight = illustrationWidth / KEYBOARD_ILLUSTRATION_ASPECT_RATIO
                    val illustrationHeight = naturalHeight.coerceIn(
                        minimumValue = keyBlockHeight,
                        maximumValue = maxIllustrationHeight
                    )

                    Box(
                        modifier = Modifier
                            .width(illustrationWidth)
                            .height(illustrationHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.keyboard),
                            contentDescription = "Keyboard illustration",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            TextButton(
                onClick = onReset,
                modifier = Modifier.testTag(UiTestTags.KEYBOARD_RESET_BUTTON)
            ) {
                Text("Reset layout")
            }
        }
    }
}

@Composable
private fun RowScope.KeySlot(
    index: Int,
    action: KeyboardSlotAction,
    active: Boolean,
    onClick: () -> Unit
) {
    val slotLabel = KeyboardLayoutDefaults.compactLabel(action, fallback = "+")
    Box(
        modifier = Modifier
            .weight(1f)
            .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
            .testTag(UiTestTags.keyboardSlot(index))
            .semantics(mergeDescendants = true) {
                contentDescription = slotLabel
            }
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
                text = slotLabel,
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
    val textDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.TEXT) current.text else "")
    }
    val sequenceDraft = remember(current) {
        mutableStateOf(if (current.type == KeyboardActionType.SEQUENCE) current.sequence else "")
    }
    val advancedExpanded = remember { mutableStateOf(false) }

    val applyKeyAction: (KeyboardSlotAction) -> Unit = { base ->
        onApply(
            withCombination(
                base = base,
                ctrl = comboCtrl.value,
                alt = comboAlt.value,
                shift = comboShift.value
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_BACK_BUTTON)
                ) {
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

        SectionTitle("Icon Aliases")
        Text(
            "Icon entries are direct action aliases in the shell.",
            style = MaterialTheme.typography.bodySmall
        )
        PresetRow(KeyboardLayoutDefaults.iconAliasPresets, onApply)

        SectionTitle("Modifiers")
        PresetRow(KeyboardLayoutDefaults.modifierPresets) { preset ->
            onApply(preset)
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.KEYBOARD_EDITOR_TEXT_INPUT)
        )
        TextButton(
            enabled = textDraft.value.isNotBlank(),
            onClick = {
                onApply(KeyboardLayoutDefaults.textAction(textDraft.value))
            },
            modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_USE_TEXT_BUTTON)
        ) {
            Text("Use Text")
        }

        SectionTitle("Actions")
        TextButton(
            onClick = {
                onApply(KeyboardLayoutDefaults.snippetPickerAction(iconId = "code"))
            }
        ) {
            Text("Snippet Picker")
        }
        TextButton(
            onClick = {
                onApply(KeyboardLayoutDefaults.passwordInjectAction(iconId = "key"))
            }
        ) {
            Text("Inject Password")
        }

        TextButton(
            onClick = { advancedExpanded.value = !advancedExpanded.value },
            modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_ADVANCED_BUTTON)
        ) {
            Icon(
                imageVector = if (advancedExpanded.value) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Text(if (advancedExpanded.value) "Advanced (hide)" else "Advanced")
        }

        if (advancedExpanded.value) {
            SectionTitle("VT100/xterm Sequences")
            PresetRow(KeyboardLayoutDefaults.advancedSequencePresets) { preset ->
                onApply(preset)
            }

            OutlinedTextField(
                value = sequenceDraft.value,
                onValueChange = { sequenceDraft.value = it },
                label = { Text("Custom sequence") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.KEYBOARD_EDITOR_SEQUENCE_INPUT)
            )
            TextButton(
                enabled = sequenceDraft.value.isNotBlank(),
                onClick = {
                    onApply(KeyboardLayoutDefaults.sequenceAction("Seq", sequenceDraft.value))
                },
                modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_USE_SEQUENCE_BUTTON)
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

private fun withCombination(
    base: KeyboardSlotAction,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean
): KeyboardSlotAction {
    if (base.type != KeyboardActionType.KEY) return base
    if (!ctrl && !alt && !shift) return base

    val modifiers = mutableListOf<String>()
    if (ctrl) modifiers += "Ctrl"
    if (alt) modifiers += "Alt"
    if (shift) modifiers += "Shift"
    val baseLabel = base.label.ifBlank {
        KeyboardLayoutDefaults.keyTokenForAction(base).ifBlank { "Key" }
    }
    val combinedLabel = "${modifiers.joinToString("+")}-$baseLabel"
    return base.copy(
        label = combinedLabel,
        ctrl = base.ctrl || ctrl,
        alt = base.alt || alt,
        shift = base.shift || shift
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
        KeyboardActionType.PASSWORD_INJECT -> "Inject Password"
        KeyboardActionType.SNIPPET_PICKER -> "Snippet Picker"
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

private const val KEYBOARD_ILLUSTRATION_ASPECT_RATIO = 2160f / 1126f
private val KEYBOARD_ILLUSTRATION_FALLBACK_HEIGHT = 180.dp
private val KEYBOARD_ILLUSTRATION_MAX_WIDTH = 980.dp
private const val KEYBOARD_ILLUSTRATION_MAX_HEIGHT_MULTIPLIER = 2f
