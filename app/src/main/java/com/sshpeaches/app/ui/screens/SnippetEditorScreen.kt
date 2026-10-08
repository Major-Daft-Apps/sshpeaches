package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.majordaftapps.sshpeaches.app.ui.code.CodeEditorPane
import com.majordaftapps.sshpeaches.app.ui.code.CodeLanguage
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var title by rememberSaveable(initialSnippet?.id) { mutableStateOf(initialSnippet?.title.orEmpty()) }
    var group by rememberSaveable(initialSnippet?.id) { mutableStateOf(initialSnippet?.group.orEmpty()) }
    var description by rememberSaveable(initialSnippet?.id) { mutableStateOf(initialSnippet?.description.orEmpty()) }
    var command by rememberSaveable(initialSnippet?.id) { mutableStateOf(initialSnippet?.command.orEmpty()) }
    var editorError by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
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
                CodeEditorPane(
                    text = command,
                    onTextChange = { command = it },
                    language = CodeLanguage.SHELL,
                    editorTestTag = UiTestTags.SNIPPET_EDITOR_COMMAND_INPUT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
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
            fun saveSnippet() {
                if (command.isBlank()) {
                    editorError = "Command is required."
                    return
                }
                onSave(title.ifBlank { "Snippet" }, group.ifBlank { null }, description, command)
                onShowMessage("Snippet saved.")
                onNavigateBack()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = ::saveSnippet)
                    .testTag(UiTestTags.SNIPPET_EDITOR_SAVE_BUTTON)
            ) {
                Button(
                    onClick = ::saveSnippet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
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
