package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.components.DeleteConfirmationDialog
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.GroupSectionHeader
import com.majordaftapps.sshpeaches.app.ui.components.SnippetQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.buildGroupedSections
import com.majordaftapps.sshpeaches.app.ui.components.processSnippetQrImport
import com.majordaftapps.sshpeaches.app.ui.adaptive.AdaptivePaneScaffold
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import com.majordaftapps.sshpeaches.app.ui.adaptive.desktopHoverable
import com.majordaftapps.sshpeaches.app.ui.adaptive.rememberDesktopHoverState
import com.majordaftapps.sshpeaches.app.ui.adaptive.secondaryClickToOpen
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun SnippetManagerScreen(
    snippets: List<Snippet>,
    shellLayoutMode: ShellLayoutMode = ShellLayoutMode.COMPACT,
    addRequestKey: Int = 0,
    editRequestKey: Int = 0,
    editRequestId: String? = null,
    importRequestKey: Int = 0,
    onAdd: (title: String, group: String?, description: String, command: String) -> Unit = { _, _, _, _ -> },
    onUpdate: (id: String, title: String, group: String?, description: String, command: String) -> Unit = { _, _, _, _, _ -> },
    onEditSnippet: (snippetId: String) -> Unit = {},
    onDelete: (id: String) -> Unit = {},
    onRun: (snippet: Snippet) -> Unit = {},
    onImportFromQr: () -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val search = rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    val pendingDeleteSnippet = remember { mutableStateOf<Snippet?>(null) }
    val overflowSnippetId = remember { mutableStateOf<String?>(null) }
    val handledAddRequestKey = rememberSaveable { mutableStateOf(0) }
    val handledEditRequestKey = rememberSaveable { mutableStateOf(0) }
    var paneDetailSnippetId by rememberSaveable { mutableStateOf<String?>(null) }
    var paneEditorSnippetId by rememberSaveable { mutableStateOf<String?>(null) }
    var paneEditorDirty by remember { mutableStateOf(false) }
    var pendingPaneDetailId by remember { mutableStateOf<String?>(null) }
    var pendingPaneEditorId by remember { mutableStateOf<String?>(null) }
    var pendingPaneClose by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun openSnippetDetails(snippetId: String) {
        if (paneEditorDirty) {
            pendingPaneDetailId = snippetId
            pendingPaneEditorId = null
            pendingPaneClose = false
            showDiscardDialog = true
            return
        }
        paneEditorSnippetId = null
        paneDetailSnippetId = snippetId
    }

    fun openSnippetEditor(snippetId: String?) {
        if (shellLayoutMode != ShellLayoutMode.WIDE) {
            snippetId?.let(onEditSnippet)
            return
        }
        if (paneEditorDirty) {
            pendingPaneDetailId = null
            pendingPaneEditorId = snippetId
            pendingPaneClose = false
            showDiscardDialog = true
            return
        }
        paneDetailSnippetId = null
        paneEditorSnippetId = snippetId ?: ""
    }

    fun closePane() {
        if (paneEditorDirty) {
            pendingPaneDetailId = null
            pendingPaneEditorId = null
            pendingPaneClose = true
            showDiscardDialog = true
            return
        }
        paneDetailSnippetId = null
        paneEditorSnippetId = null
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        when (val processed = processSnippetQrImport(contents)) {
            is SnippetQrImportResult.Error -> {
                Toast.makeText(context, processed.message, Toast.LENGTH_SHORT).show()
            }
            is SnippetQrImportResult.Ready -> {
                onAdd(
                    processed.data.title,
                    processed.data.group,
                    processed.data.description,
                    processed.data.command
                )
                onImportFromQr()
                Toast.makeText(context, "Snippet imported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(importRequestKey) {
        if (importRequestKey > 0) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan snippet QR")
                setBeepEnabled(false)
                setCaptureActivity(com.majordaftapps.sshpeaches.app.ui.qr.PortraitCaptureActivity::class.java)
                setOrientationLocked(true)
            }
            scanLauncher.launch(options)
        }
    }

    LaunchedEffect(addRequestKey) {
        if (shellLayoutMode == ShellLayoutMode.WIDE && addRequestKey > handledAddRequestKey.value) {
            handledAddRequestKey.value = addRequestKey
            openSnippetEditor(null)
        }
    }

    LaunchedEffect(editRequestKey, editRequestId) {
        if (shellLayoutMode == ShellLayoutMode.WIDE && editRequestKey > handledEditRequestKey.value) {
            handledEditRequestKey.value = editRequestKey
            openSnippetEditor(editRequestId)
        }
    }

    val filteredSnippets = snippets.filter {
        val query = search.value.trim()
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.group?.contains(query, ignoreCase = true) == true ||
            it.description.contains(query, ignoreCase = true) ||
            it.command.contains(query, ignoreCase = true)
    }
    val groupedSnippets = buildGroupedSections(
        items = filteredSnippets,
        groupSelector = { it.group },
        itemComparator = compareBy<Snippet> { it.title.lowercase() }
    )
    val showEmptyState = snippets.isEmpty() || filteredSnippets.isEmpty()
    LaunchedEffect(showEmptyState) {
        onEmptyStateVisibleChanged(showEmptyState)
    }

    AdaptivePaneScaffold(
        shellLayoutMode = shellLayoutMode,
        secondaryPaneVisible = shellLayoutMode == ShellLayoutMode.WIDE &&
            (paneDetailSnippetId != null || paneEditorSnippetId != null),
        primaryPane = {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SNIPPET_SEARCH_INPUT),
                        value = search.value,
                        onValueChange = { search.value = it },
                        placeholder = { Text("Search snippets") },
                        trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (snippets.isEmpty()) {
                        item { EmptyState(itemLabel = "snippet") }
                    } else if (filteredSnippets.isEmpty()) {
                        item { EmptyState(itemLabel = "result") }
                    } else {
                        groupedSnippets.forEach { section ->
                            item(key = "snippet_header_${section.key}") {
                                GroupSectionHeader(
                                    vertical = "snippets",
                                    label = section.label,
                                    count = section.items.size,
                                    expanded = if (search.value.isNotBlank()) true else expandedSections[section.key] ?: true,
                                    onToggle = {
                                        val current = expandedSections[section.key] ?: true
                                        expandedSections[section.key] = !current
                                    }
                                )
                            }
                            if (search.value.isNotBlank() || (expandedSections[section.key] ?: true)) {
                                items(section.items, key = { it.id }) { snippet ->
                                    val (cardInteractionSource, cardHovered) = rememberDesktopHoverState()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .desktopHoverable(
                                                enabled = true,
                                                interactionSource = cardInteractionSource
                                            )
                                            .secondaryClickToOpen {
                                                overflowSnippetId.value = snippet.id
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (cardHovered) 6.dp else 0.dp
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                                                    if (snippet.description.isNotBlank()) {
                                                        Text(snippet.description, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    FilledTonalButton(
                                                        onClick = { onRun(snippet) },
                                                        modifier = Modifier.testTag(UiTestTags.snippetRun(snippet.id))
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                        Text("Run")
                                                    }
                                                    IconButton(
                                                        onClick = { overflowSnippetId.value = snippet.id },
                                                        modifier = Modifier.testTag(UiTestTags.snippetOverflowButton(snippet.id))
                                                    ) {
                                                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                                    }
                                                    DropdownMenu(
                                                        expanded = overflowSnippetId.value == snippet.id,
                                                        onDismissRequest = { overflowSnippetId.value = null }
                                                    ) {
                                                        if (shellLayoutMode == ShellLayoutMode.WIDE) {
                                                            DropdownMenuItem(
                                                                text = { Text("Details") },
                                                                onClick = {
                                                                    overflowSnippetId.value = null
                                                                    openSnippetDetails(snippet.id)
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("Edit") },
                                                            onClick = {
                                                                overflowSnippetId.value = null
                                                                if (shellLayoutMode == ShellLayoutMode.WIDE) {
                                                                    openSnippetEditor(snippet.id)
                                                                } else {
                                                                    onEditSnippet(snippet.id)
                                                                }
                                                            },
                                                            modifier = Modifier.testTag(UiTestTags.snippetOverflowAction(snippet.id, "edit"))
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Delete") },
                                                            onClick = {
                                                                overflowSnippetId.value = null
                                                                pendingDeleteSnippet.value = snippet
                                                            },
                                                            modifier = Modifier.testTag(UiTestTags.snippetOverflowAction(snippet.id, "delete"))
                                                        )
                                                    }
                                                }
                                            }
                                            Text(snippet.command, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        secondaryPane = {
            val editingSnippet = paneEditorSnippetId?.takeIf { it.isNotBlank() }?.let { paneId ->
                snippets.firstOrNull { it.id == paneId }
            }
            when {
                paneEditorSnippetId != null -> {
                    SnippetEditorScreen(
                        initialSnippet = editingSnippet,
                        onSave = { title, group, description, command ->
                            if (editingSnippet == null) {
                                onAdd(title, group, description, command)
                            } else {
                                onUpdate(editingSnippet.id, title, group, description, command)
                            }
                            paneEditorDirty = false
                            paneEditorSnippetId = null
                        },
                        onNavigateBack = {
                            paneEditorDirty = false
                            paneEditorSnippetId = null
                        },
                        onDirtyStateChange = { paneEditorDirty = it },
                        onShowMessage = {}
                    )
                }

                paneDetailSnippetId != null -> {
                    val detailSnippet = snippets.firstOrNull { it.id == paneDetailSnippetId }
                    if (detailSnippet != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(detailSnippet.title, style = MaterialTheme.typography.headlineSmall)
                                OutlinedButton(onClick = ::closePane) {
                                    Text("Close")
                                }
                            }
                            detailSnippet.group?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelLarge)
                            }
                            if (detailSnippet.description.isNotBlank()) {
                                Text(detailSnippet.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Command", style = MaterialTheme.typography.labelLarge)
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(
                                    detailSnippet.command,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRun(detailSnippet) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("Run")
                                }
                                OutlinedButton(onClick = { openSnippetEditor(detailSnippet.id) }) {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    pendingDeleteSnippet.value?.let { snippet ->
        DeleteConfirmationDialog(
            title = "Delete snippet?",
            message = "Delete ${snippet.title}?",
            onConfirm = {
                onDelete(snippet.id)
                pendingDeleteSnippet.value = null
            },
            onDismiss = { pendingDeleteSnippet.value = null }
        )
    }

    if (showDiscardDialog) {
        DeleteConfirmationDialog(
            title = "Discard changes?",
            message = "You have unsaved snippet changes.",
            onConfirm = {
                paneEditorDirty = false
                showDiscardDialog = false
                if (pendingPaneClose) {
                    paneDetailSnippetId = null
                    paneEditorSnippetId = null
                } else if (pendingPaneEditorId != null || pendingPaneEditorId == "") {
                    paneDetailSnippetId = null
                    paneEditorSnippetId = pendingPaneEditorId
                } else if (pendingPaneDetailId != null) {
                    paneEditorSnippetId = null
                    paneDetailSnippetId = pendingPaneDetailId
                }
                pendingPaneClose = false
                pendingPaneDetailId = null
                pendingPaneEditorId = null
            },
            onDismiss = {
                showDiscardDialog = false
                pendingPaneClose = false
                pendingPaneDetailId = null
                pendingPaneEditorId = null
            }
        )
    }
}
