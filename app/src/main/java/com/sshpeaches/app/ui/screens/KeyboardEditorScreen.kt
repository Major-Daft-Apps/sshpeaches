package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.majordaftapps.sshpeaches.app.ui.adaptive.AdaptivePaneScaffold
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun KeyboardEditorScreen(
    slots: List<KeyboardSlotAction>,
    shellLayoutMode: ShellLayoutMode = ShellLayoutMode.COMPACT,
    onSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onReset: () -> Unit
) {
    val editorIndex = remember { mutableStateOf<Int?>(null) }
    val normalizedSlots = remember(slots) { KeyboardLayoutDefaults.normalizeSlots(slots) }
    val keyBlockHeightPx = remember { mutableIntStateOf(0) }
    val activeEditorIndex = editorIndex.value

    BackHandler(enabled = activeEditorIndex != null) {
        editorIndex.value = null
    }

    if (shellLayoutMode != ShellLayoutMode.WIDE && activeEditorIndex != null) {
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

    AdaptivePaneScaffold(
        shellLayoutMode = shellLayoutMode,
        secondaryPaneVisible = shellLayoutMode == ShellLayoutMode.WIDE && activeEditorIndex != null,
        primaryPane = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.SCREEN_KEYBOARD)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (shellLayoutMode == ShellLayoutMode.WIDE) 1400.dp else 980.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            Text("Tap a main-row slot to edit it. The Fn layer (Back, F1-F12, keyboard) is fixed.")

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFA992A), RoundedCornerShape(8.dp))
                    .onSizeChanged { keyBlockHeightPx.intValue = it.height }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val rows = remember(normalizedSlots) {
                    normalizedSlots.chunked(KeyboardLayoutDefaults.SLOT_COLUMNS)
                }
                val useWideLayout = maxWidth >= KEYBOARD_EDITOR_WIDE_LAYOUT_MIN_WIDTH
                if (useWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeyboardSlotRows(
                            rows = rows.take(2),
                            rowOffset = 0,
                            onSlotClick = { editorIndex.value = it },
                            modifier = Modifier.weight(1f)
                        )
                        KeyboardSlotRows(
                            rows = rows.drop(2),
                            rowOffset = 2,
                            onSlotClick = { editorIndex.value = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    KeyboardSlotRows(
                        rows = rows,
                        rowOffset = 0,
                        onSlotClick = { editorIndex.value = it },
                        modifier = Modifier.fillMaxWidth()
                    )
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
        },
        secondaryPane = {
            val current = normalizedSlots.getOrNull(activeEditorIndex ?: -1) ?: KeyboardLayoutDefaults.emptyAction()
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modify Key", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { editorIndex.value = null }) {
                        Text("Close")
                    }
                }
                KeyActionEditorVertical(
                    slotIndex = activeEditorIndex ?: 0,
                    current = current,
                    onApply = { action ->
                        val index = activeEditorIndex ?: return@KeyActionEditorVertical
                        onSlotChange(index, action)
                        editorIndex.value = null
                    },
                    onRemove = {
                        val index = activeEditorIndex ?: return@KeyActionEditorVertical
                        onSlotChange(index, KeyboardLayoutDefaults.emptyAction())
                        editorIndex.value = null
                    },
                    onCancel = { editorIndex.value = null }
                )
            }
        }
    )
}

@Composable
private fun KeyboardSlotRows(
    rows: List<List<KeyboardSlotAction>>,
    rowOffset: Int,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(KeyboardLayoutDefaults.SLOT_COLUMNS) { columnIndex ->
                    val action = row.getOrNull(columnIndex)
                    if (action == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
                        )
                        return@repeat
                    }
                    val index = (rowOffset + rowIndex) * KeyboardLayoutDefaults.SLOT_COLUMNS + columnIndex
                    KeySlot(
                        index = index,
                        action = action,
                        active = !action.isEmpty(),
                        onClick = { onSlotClick(index) }
                    )
                }
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
                contentDescription = null,
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
    val textDraft = remember(current) {
        mutableStateOf(
            if (current.type == KeyboardActionType.TEXT && current.iconId.isBlank()) {
                current.text.removeSuffix("\r")
            } else {
                ""
            }
        )
    }
    val pressEnterDraft = remember(current) {
        mutableStateOf(current.type == KeyboardActionType.TEXT && current.text.endsWith("\r"))
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
            Text("Edit Key Action", style = MaterialTheme.typography.headlineSmall)
            Text("Slot ${slotIndex + 1}", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "Current: ${fullActionLabel(current)}",
            style = MaterialTheme.typography.bodySmall
        )

        SectionTitle("Modifiers")
        Text(
            "Ctrl, Alt and Shift apply to the next key you press. Fn swaps the rows for Back, F1-F12 and the keyboard key, which stay fixed.",
            style = MaterialTheme.typography.bodySmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardLayoutDefaults.modifierPresets.forEach { preset ->
                TextButton(onClick = { onApply(preset) }) { Text(preset.label) }
            }
            TextButton(
                onClick = { onApply(KeyboardLayoutDefaults.fnKeyAction()) },
                modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_FN_BUTTON)
            ) {
                Text("Fn")
            }
        }

        SectionTitle("Keys")
        PresetRow(KeyboardLayoutDefaults.keyPresets, onApply)

        SectionTitle("Shortcuts")
        PresetRow(KeyboardLayoutDefaults.shortcutPresets, onApply)

        SectionTitle("Symbols")
        PresetRow(KeyboardLayoutDefaults.symbolPresets, onApply)

        SectionTitle("Actions")
        PresetRow(KeyboardLayoutDefaults.actionPresets, onApply, showLabels = true)

        SectionTitle("Custom text")
        Text(
            "Types the text when pressed, for example \"sudo \" or \"git status\".",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = textDraft.value,
            onValueChange = { textDraft.value = it },
            label = { Text("Text") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.KEYBOARD_EDITOR_TEXT_INPUT)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = pressEnterDraft.value,
                onCheckedChange = { pressEnterDraft.value = it },
                modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_PRESS_ENTER)
            )
            Text("Press Enter after", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(
            enabled = textDraft.value.isNotBlank(),
            onClick = {
                onApply(KeyboardLayoutDefaults.customTextAction(textDraft.value, pressEnterDraft.value))
            },
            modifier = Modifier.testTag(UiTestTags.KEYBOARD_EDITOR_USE_TEXT_BUTTON)
        ) {
            Text("Use Text")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    presets: List<KeyboardSlotAction>,
    onSelect: (KeyboardSlotAction) -> Unit,
    showLabels: Boolean = false
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
                }
                if (icon == null || showLabels) {
                    Text(
                        text = action.label,
                        modifier = if (icon != null) Modifier.padding(start = 6.dp) else Modifier
                    )
                }
            }
        }
    }
}

private const val KEYBOARD_ILLUSTRATION_ASPECT_RATIO = 2160f / 1126f
private val KEYBOARD_ILLUSTRATION_FALLBACK_HEIGHT = 180.dp
private val KEYBOARD_ILLUSTRATION_MAX_WIDTH = 980.dp
private val KEYBOARD_EDITOR_WIDE_LAYOUT_MIN_WIDTH = 600.dp
private const val KEYBOARD_ILLUSTRATION_MAX_HEIGHT_MULTIPLIER = 2f
