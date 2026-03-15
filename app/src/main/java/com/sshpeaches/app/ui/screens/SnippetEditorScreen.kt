package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun SnippetEditorScreen(
    initialSnippet: Snippet?,
    onSave: (title: String, description: String, command: String) -> Unit,
    onDelete: (() -> Unit)?,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    var title by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.title.orEmpty()) }
    var description by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.description.orEmpty()) }
    var command by remember(initialSnippet?.id) { mutableStateOf(initialSnippet?.command.orEmpty()) }
    var editorError by remember { mutableStateOf<String?>(null) }
    val isEditingExisting = initialSnippet != null

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
                    onSave(title.ifBlank { "Snippet" }, description, command)
                    onShowMessage("Snippet saved.")
                    onNavigateBack()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }

        if (isEditingExisting && onDelete != null) {
            OutlinedButton(
                onClick = {
                    onDelete()
                    onShowMessage("Snippet deleted.")
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Snippet")
            }
        }
    }
    }
}

@Composable
private fun ScriptEditorWithLineNumbers(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val lineCount = remember(value) { (value.count { it == '\n' } + 1).coerceAtLeast(1) }
    val lineNumberText = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .heightIn(min = 220.dp, max = 520.dp)
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
            Text(
                text = lineNumberText,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        "Enter shell command script...",
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                        autoCorrect = false
                    ),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .testTag(UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT)
                )
            }
        }
    }
}
