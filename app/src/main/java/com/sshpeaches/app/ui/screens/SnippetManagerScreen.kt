package com.sshpeaches.app.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.Snippet
import com.sshpeaches.app.ui.components.EmptyState
import java.util.UUID

private const val TAG = "CW/SnippetManagerScreen"

@Composable
fun SnippetManagerScreen(
    snippets: List<Snippet>,
    onAdd: (title: String, description: String, command: String) -> Unit = { _, _, _ -> },
    onUpdate: (id: String, title: String, description: String, command: String) -> Unit = { _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    addRequest: Boolean = false,
    onAddConsumed: () -> Unit = {}
) {
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val titleState = remember { mutableStateOf("") }
    val descriptionState = remember { mutableStateOf("") }
    val commandState = remember { mutableStateOf("") }

    fun openDialog(snippet: Snippet?) {
        editingId.value = snippet?.id
        titleState.value = snippet?.title ?: ""
        descriptionState.value = snippet?.description ?: ""
        commandState.value = snippet?.command ?: ""
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
    }

    LaunchedEffect(addRequest) {
        if (addRequest) {
            openDialog(null)
            onAddConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (snippets.isEmpty()) {
            item { EmptyState(itemLabel = "snippet") }
        } else {
            items(snippets, key = { it.id }) { snippet ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                    if (snippet.description.isNotBlank()) {
                        Text(snippet.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = snippet.command,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    RowActions(
                        onRun = { 
                            Log.i(TAG, "UI snippet_run_click id=${snippet.id}")
                            /* TODO run snippet */ 
                        },
                        onEdit = { 
                            Log.i(TAG, "UI snippet_edit_click id=${snippet.id}")
                            openDialog(snippet) 
                        },
                        onDelete = { 
                            Log.i(TAG, "UI snippet_delete_click id=${snippet.id}")
                            onDelete(snippet.id) 
                        }
                    )
                }
            }
        }
        }
    }

    if (showDialog.value) {
        val isEdit = editingId.value != null
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit snippet" else "Add snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleState.value,
                        onValueChange = { titleState.value = it },
                        label = { Text("Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = descriptionState.value,
                        onValueChange = { descriptionState.value = it },
                        label = { Text("Description") }
                    )
                    OutlinedTextField(
                        value = commandState.value,
                        onValueChange = { commandState.value = it },
                        label = { Text("Command") },
                        singleLine = false,
                        minLines = 4,
                        maxLines = 12,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            titleState.value.ifBlank { "Snippet" },
                            descriptionState.value,
                            commandState.value
                        )
                    } else {
                        onAdd(
                            titleState.value.ifBlank { "Snippet ${UUID.randomUUID()}" },
                            descriptionState.value,
                            commandState.value
                        )
                    }
                    closeDialog()
                }) { Text(if (isEdit) "Save" else "Add") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEdit) {
                        TextButton(onClick = {
                            onDelete(editingId.value!!)
                            closeDialog()
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { closeDialog() }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun RowActions(onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onRun) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Run")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
