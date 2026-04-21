package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun SnippetEditorScreen(
    initialSnippet: Snippet?,
    onSave: (title: String, group: String?, description: String, command: String) -> Unit,
    onNavigateBack: () -> Unit,
    onDirtyStateChange: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    var title by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.title.orEmpty()) }
    var group by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.group.orEmpty()) }
    var description by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.description.orEmpty()) }
    var command by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.command.orEmpty()) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isEditingExisting = initialSnippet != null
    val isDirty = title != initialSnippet?.title.orEmpty() ||
        group != initialSnippet?.group.orEmpty() ||
        description != initialSnippet?.description.orEmpty() ||
        command != initialSnippet?.command.orEmpty()

    fun requestClose() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = isDirty) {
        requestClose()
    }

    LaunchedEffect(isDirty) {
        onDirtyStateChange(isDirty)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_SNIPPET_EDITOR)
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 980.dp)
            .fillMaxSize()
            .align(Alignment.TopCenter)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isEditingExisting) "Edit Snippet" else "Add Snippet",
            style = MaterialTheme.typography.headlineSmall
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SNIPPET_EDITOR_TITLE_INPUT)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SNIPPET_EDITOR_DESCRIPTION_INPUT)
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Group (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SNIPPET_EDITOR_GROUP_INPUT)
                )
                Text("Command", style = MaterialTheme.typography.labelLarge)
                ScriptEditorWithLineNumbers(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        editorError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (command.isBlank()) {
                        editorError = "Command is required."
                        return@Button
                    }
                    onSave(title.ifBlank { "Snippet" }, group.ifBlank { null }, description, command)
                    onShowMessage("Snippet saved.")
                    onNavigateBack()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = ::requestClose,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved snippet changes.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun ScriptEditorWithLineNumbers(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val minEditorHeight = 200.dp
    val density = LocalDensity.current
    val fallbackLineHeightPx = with(density) { textStyle.lineHeight.toPx() }
    var textLayoutResult by remember(value) { mutableStateOf<TextLayoutResult?>(null) }
    val lineBlocks = remember(value, textLayoutResult, fallbackLineHeightPx) {
        buildSnippetLineBlocks(
            value = value,
            textLayoutResult = textLayoutResult,
            fallbackLineHeightPx = fallbackLineHeightPx
        )
    }
    val gutterWidth = remember(lineBlocks.size) {
        val digits = lineBlocks.size.coerceAtLeast(1).toString().length
        (digits * 10 + 20).dp
    }

    Box(
        modifier = modifier
            .heightIn(min = 220.dp, max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(surfaceColor, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .heightIn(min = minEditorHeight)
            ) {
                lineBlocks.forEachIndexed { index, block ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = with(density) { block.topPx.toDp() })
                            .height(with(density) { block.heightPx.toDp() }),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minEditorHeight)
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Enter shell command script...",
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                        autoCorrect = false
                    ),
                    singleLine = false,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minEditorHeight)
                        .testTag(UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT)
                )
            }
        }
    }
}

private data class SnippetLineBlock(
    val topPx: Float,
    val heightPx: Float
)

private fun buildSnippetLineBlocks(
    value: String,
    textLayoutResult: TextLayoutResult?,
    fallbackLineHeightPx: Float
): List<SnippetLineBlock> {
    val logicalLineCount = (value.count { it == '\n' } + 1).coerceAtLeast(1)
    if (textLayoutResult == null) {
        return List(logicalLineCount) { index ->
            SnippetLineBlock(
                topPx = index * fallbackLineHeightPx,
                heightPx = fallbackLineHeightPx
            )
        }
    }

    val blocks = mutableListOf<SnippetLineBlock>()
    var blockStartVisualLine = 0
    for (visualLine in 0 until textLayoutResult.lineCount) {
        val lineEndOffset = textLayoutResult.getLineEnd(visualLine, visibleEnd = false)
        val endsLogicalLine = visualLine == textLayoutResult.lineCount - 1 ||
            (lineEndOffset > 0 && value.getOrNull(lineEndOffset - 1) == '\n')
        if (!endsLogicalLine) continue

        val topPx = textLayoutResult.getLineTop(blockStartVisualLine)
        val bottomPx = textLayoutResult.getLineBottom(visualLine)
        blocks += SnippetLineBlock(
            topPx = topPx,
            heightPx = (bottomPx - topPx).coerceAtLeast(fallbackLineHeightPx)
        )
        blockStartVisualLine = visualLine + 1
    }

    if (blocks.size >= logicalLineCount) return blocks.take(logicalLineCount)

    val trailingTop = blocks.lastOrNull()?.let { it.topPx + it.heightPx } ?: 0f
    return buildList {
        addAll(blocks)
        repeat(logicalLineCount - blocks.size) { index ->
            add(
                SnippetLineBlock(
                    topPx = trailingTop + (index * fallbackLineHeightPx),
                    heightPx = fallbackLineHeightPx
                )
            )
        }
    }
}
