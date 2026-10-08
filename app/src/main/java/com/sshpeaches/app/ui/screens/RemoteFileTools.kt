package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.majordaftapps.sshpeaches.app.ui.code.CodeEditorPane
import com.majordaftapps.sshpeaches.app.ui.code.CodeLanguage
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import kotlinx.coroutines.launch

/** Breadcrumb segments for an absolute remote path: ("/", "/"), ("home", "/home"), ... */
internal fun remoteBreadcrumbs(path: String): List<Pair<String, String>> {
    val trimmed = path.trim()
    if (!trimmed.startsWith("/")) return emptyList()
    val crumbs = mutableListOf("/" to "/")
    var current = ""
    trimmed.split('/').filter { it.isNotEmpty() }.forEach { part ->
        current += "/$part"
        crumbs += part to current
    }
    return crumbs
}

/** The mode bits (including setuid/setgid/sticky) from an `ls -l` style string such as "-rwsr-xr-x". */
internal fun permissionSummaryToMode(summary: String): Int? {
    val bits = summary.trim().takeLast(9)
    if (bits.length != 9) return null
    var mode = 0
    var special = 0
    for (group in 0 until 3) {
        val r = bits[group * 3]
        val w = bits[group * 3 + 1]
        val x = bits[group * 3 + 2]
        if (r !in "r-" || w !in "w-" || x !in "xsStT-") return null
        val shift = (2 - group) * 3
        if (r == 'r') mode = mode or (4 shl shift)
        if (w == 'w') mode = mode or (2 shl shift)
        if (x in "xst") mode = mode or (1 shl shift)
        if (x in "sStT") special = special or (1 shl (2 - group))
    }
    return (special shl 9) or mode
}

/** "644", or "4755" when setuid/setgid/sticky bits are set. */
internal fun formatMode(mode: Int): String {
    val special = (mode shr 9) and 7
    val base = Integer.toOctalString(mode and 0x1FF).padStart(3, '0')
    return if (special == 0) base else "$special$base"
}

internal fun parseMode(text: String): Int? {
    val value = text.trim()
    if (value.length !in 3..4 || value.any { it !in '0'..'7' }) return null
    return value.toInt(8)
}

@Composable
internal fun RemoteBreadcrumbBar(
    path: String,
    enabled: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val crumbs = remoteBreadcrumbs(path)
    if (crumbs.size < 2) return
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .testTag(UiTestTags.CONNECTING_SCP_BREADCRUMBS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, (label, target) ->
            if (index > 1) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val isCurrent = index == crumbs.lastIndex
            TextButton(
                onClick = { onNavigate(target) },
                enabled = enabled && !isCurrent,
                modifier = Modifier.testTag(UiTestTags.connectingScpBreadcrumb(index))
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun RemotePermissionsDialog(
    name: String,
    initialMode: Int,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var modeText by rememberSaveable(name) { mutableStateOf(formatMode(initialMode)) }
    val mode = parseMode(modeText)
    val who = listOf("Owner", "Group", "Others")
    val what = listOf("Read" to 4, "Write" to 2, "Execute" to 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_PERMISSIONS_DIALOG),
        title = { Text("Permissions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(72.dp))
                    what.forEach { (label, _) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                    }
                }
                who.forEachIndexed { group, groupLabel ->
                    val shift = (2 - group) * 3
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(groupLabel, modifier = Modifier.width(72.dp))
                        what.forEach { (label, bit) ->
                            val mask = bit shl shift
                            Box(Modifier.width(64.dp)) {
                                Checkbox(
                                    checked = mode != null && mode and mask != 0,
                                    enabled = mode != null,
                                    onCheckedChange = { checked ->
                                        val current = mode ?: return@Checkbox
                                        modeText = formatMode(if (checked) current or mask else current and mask.inv())
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.connectingScpPermission("${groupLabel.lowercase()}_${label.lowercase()}")
                                    )
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = modeText,
                    onValueChange = { modeText = it.filter { c -> c in '0'..'7' }.take(4) },
                    label = { Text("Mode (octal)") },
                    singleLine = true,
                    isError = mode == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CONNECTING_SCP_PERMISSIONS_MODE)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mode != null,
                onClick = { mode?.let { onApply(formatMode(it)) } },
                modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_PERMISSIONS_APPLY)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Full-screen editor for a small remote text file: loads it with [load], uploads with [save].
 * Closing with unsaved changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteTextEditorDialog(
    path: String,
    load: suspend () -> Result<String>,
    save: suspend (String) -> Result<Unit>,
    onClose: (saved: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var original by remember(path) { mutableStateOf<String?>(null) }
    var text by rememberSaveable(path) { mutableStateOf("") }
    var loadError by remember(path) { mutableStateOf<String?>(null) }
    var saving by remember(path) { mutableStateOf(false) }
    var status by remember(path) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember(path) { mutableStateOf(false) }
    var savedOnce by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        load().onSuccess { content ->
            original = content
            text = content
        }.onFailure { error ->
            loadError = error.message ?: "Couldn't open the file."
        }
    }
    val dirty = original != null && text != original
    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose(savedOnce)
    }
    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(UiTestTags.CONNECTING_SCP_EDITOR),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(path.substringAfterLast('/').ifBlank { path }, maxLines = 1)
                            Text(
                                status ?: if (dirty) "Unsaved changes" else path,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = ::requestClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close editor")
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = dirty && !saving,
                            onClick = {
                                saving = true
                                status = "Saving..."
                                scope.launch {
                                    val snapshot = text
                                    save(snapshot).onSuccess {
                                        original = snapshot
                                        savedOnce = true
                                        status = "Saved"
                                    }.onFailure { error ->
                                        status = "Save failed: ${error.message ?: "unknown error"}"
                                    }
                                    saving = false
                                }
                            },
                            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_EDITOR_SAVE)
                        ) { Text("Save") }
                    }
                )
                when {
                    loadError != null -> Text(
                        loadError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    original == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> CodeEditorPane(
                        text = text,
                        onTextChange = { text = it; status = null },
                        language = remember(path) { CodeLanguage.detect(path, original.orEmpty()) },
                        editorTestTag = UiTestTags.CONNECTING_SCP_EDITOR_TEXT,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this file haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose(savedOnce) }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }
}
