package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.SnippetQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.processSnippetQrImport
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun SnippetManagerScreen(
    snippets: List<Snippet>,
    onAdd: (title: String, description: String, command: String) -> Unit = { _, _, _ -> },
    onCreateSnippet: () -> Unit = {},
    onEditSnippet: (snippetId: String) -> Unit = {},
    onDelete: (id: String) -> Unit = {},
    onRun: (snippet: Snippet) -> Unit = {},
    onImportFromQr: () -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val search = remember { mutableStateOf("") }
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (val processed = processSnippetQrImport(contents)) {
            is SnippetQrImportResult.Error -> {
                Toast.makeText(context, processed.message, Toast.LENGTH_SHORT).show()
            }
            is SnippetQrImportResult.Ready -> {
                onAdd(
                    processed.data.title,
                    processed.data.description,
                    processed.data.command
                )
                onImportFromQr()
                Toast.makeText(context, "Snippet imported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredSnippets = snippets.filter {
        it.title.contains(search.value, ignoreCase = true) ||
            it.description.contains(search.value, ignoreCase = true) ||
            it.command.contains(search.value, ignoreCase = true)
    }
    val showEmptyState = snippets.isEmpty() || filteredSnippets.isEmpty()
    LaunchedEffect(showEmptyState) {
        onEmptyStateVisibleChanged(showEmptyState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_SNIPPETS)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = search.value,
                onValueChange = { search.value = it },
                placeholder = { Text("Search snippets") },
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onCreateSnippet, modifier = Modifier.weight(1f)) {
                        Text("Add snippet")
                    }
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan snippet QR")
                                setBeepEnabled(false)
                                setCaptureActivity(com.majordaftapps.sshpeaches.app.ui.qr.PortraitCaptureActivity::class.java)
                                setOrientationLocked(true)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Import QR")
                    }
                }
            }
            if (snippets.isEmpty()) {
                item { EmptyState(itemLabel = "snippet") }
            } else if (filteredSnippets.isEmpty()) {
                item { EmptyState(itemLabel = "result") }
            } else {
                items(filteredSnippets, key = { it.id }) { snippet ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                        if (snippet.description.isNotBlank()) {
                            Text(snippet.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(snippet.command, style = MaterialTheme.typography.bodySmall)
                        RowActions(
                            onRun = { onRun(snippet) },
                            onEdit = { onEditSnippet(snippet.id) },
                            onDelete = { onDelete(snippet.id) }
                        )
                    }
                }
            }
            }
        }
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
