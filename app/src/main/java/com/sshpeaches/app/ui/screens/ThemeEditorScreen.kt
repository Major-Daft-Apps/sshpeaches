package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.ui.adaptive.AdaptivePaneScaffold
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.terminal.resolveTerminalTypeface
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    shellLayoutMode: ShellLayoutMode = ShellLayoutMode.COMPACT,
    onDefaultTerminalProfileChange: (String) -> Unit,
    onDeleteTerminalProfile: (String) -> Unit,
    onCreateTheme: () -> Unit,
    onSaveTheme: (TerminalProfile) -> Unit = {},
    onEditTheme: (String) -> Unit,
    onDuplicateTheme: (String) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val defaultTerminalProfileExpanded = remember { mutableStateOf(false) }
    val showDeleteProfileDialog = remember { mutableStateOf<String?>(null) }
    val builtInProfileIds = remember { TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet() }
    var paneProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var paneDuplicateSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var paneDirty by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingPaneProfileId by remember { mutableStateOf<String?>(null) }
    var pendingPaneDuplicateId by remember { mutableStateOf<String?>(null) }
    var pendingPaneClose by remember { mutableStateOf(false) }

    fun requestPaneNavigation(profileId: String?, duplicateSourceId: String?) {
        if (paneDirty) {
            pendingPaneProfileId = profileId
            pendingPaneDuplicateId = duplicateSourceId
            pendingPaneClose = false
            showDiscardDialog = true
            return
        }
        paneProfileId = profileId
        paneDuplicateSourceId = duplicateSourceId
    }

    fun closePane() {
        if (paneDirty) {
            pendingPaneClose = true
            pendingPaneProfileId = null
            pendingPaneDuplicateId = null
            showDiscardDialog = true
            return
        }
        paneProfileId = null
        paneDuplicateSourceId = null
    }

    AdaptivePaneScaffold(
        shellLayoutMode = shellLayoutMode,
        secondaryPaneVisible = shellLayoutMode == ShellLayoutMode.WIDE &&
            (paneProfileId != null || paneDuplicateSourceId != null),
        primaryPane = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.SCREEN_THEME_EDITOR)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Terminal Themes", style = MaterialTheme.typography.titleMedium)
                        ExposedDropdownMenuBox(
                            expanded = defaultTerminalProfileExpanded.value,
                            onExpandedChange = {
                                defaultTerminalProfileExpanded.value = !defaultTerminalProfileExpanded.value
                            }
                        ) {
                            TextField(
                                value = terminalProfiles.firstOrNull { it.id == defaultTerminalProfileId }?.name
                                    ?: terminalProfiles.firstOrNull()?.name.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Default theme") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = defaultTerminalProfileExpanded.value)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag(UiTestTags.THEME_DEFAULT_FIELD)
                            )
                            ExposedDropdownMenu(
                                expanded = defaultTerminalProfileExpanded.value,
                                onDismissRequest = { defaultTerminalProfileExpanded.value = false }
                            ) {
                                terminalProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name) },
                                        onClick = {
                                            defaultTerminalProfileExpanded.value = false
                                            onDefaultTerminalProfileChange(profile.id)
                                        },
                                        modifier = Modifier.testTag(UiTestTags.themeDefaultOption(profile.name))
                                    )
                                }
                            }
                        }
                        terminalProfiles.forEach { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(profile.name)
                                    ThemeListPreview(
                                        profile = profile,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                }
                                if (builtInProfileIds.contains(profile.id)) {
                                    TextButton(
                                        onClick = {
                                            if (shellLayoutMode == ShellLayoutMode.WIDE) {
                                                requestPaneNavigation(profileId = null, duplicateSourceId = profile.id)
                                            } else {
                                                onDuplicateTheme(profile.id)
                                            }
                                        },
                                        modifier = Modifier.testTag(UiTestTags.themeDuplicate(profile.id))
                                    ) {
                                        Text("Duplicate")
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                if (shellLayoutMode == ShellLayoutMode.WIDE) {
                                                    requestPaneNavigation(profileId = profile.id, duplicateSourceId = null)
                                                } else {
                                                    onEditTheme(profile.id)
                                                }
                                            },
                                            modifier = Modifier.testTag(UiTestTags.themeEdit(profile.id))
                                        ) {
                                            Text("Edit")
                                        }
                                        TextButton(
                                            onClick = { showDeleteProfileDialog.value = profile.id },
                                            modifier = Modifier.testTag(UiTestTags.themeDelete(profile.id))
                                        ) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (shellLayoutMode == ShellLayoutMode.WIDE) {
                                    requestPaneNavigation(profileId = null, duplicateSourceId = null)
                                } else {
                                    onCreateTheme()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.THEME_CREATE_BUTTON)
                        ) {
                            Text("New Terminal Theme")
                        }
                        Text(
                            "Open a theme to edit name, font, text size, and colors using dedicated picker modals.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        secondaryPane = {
            val selected = terminalProfiles.firstOrNull { it.id == paneProfileId }
            val initialProfile = remember(paneProfileId, paneDuplicateSourceId, terminalProfiles) {
                when {
                    paneDuplicateSourceId != null -> {
                        val source = terminalProfiles.firstOrNull { it.id == paneDuplicateSourceId }
                        if (source == null) {
                            TerminalProfileDefaults.customTemplate(name = "Custom Theme")
                        } else {
                            source.copy(
                                id = "custom-${UUID.randomUUID()}",
                                name = "${source.name} Copy"
                            )
                        }
                    }

                    selected != null -> selected
                    else -> TerminalProfileDefaults.customTemplate(name = "Custom Theme")
                }
            }
            val isEditingExisting = selected != null && paneDuplicateSourceId == null && !builtInProfileIds.contains(selected.id)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isEditingExisting) "Edit Terminal Theme" else "Terminal Theme",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = ::closePane) {
                        Text("Close")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ThemeProfileEditorScreen(
                    initialProfile = initialProfile,
                    existingProfiles = terminalProfiles,
                    isEditingExisting = isEditingExisting,
                    onSaveTheme = {
                        onSaveTheme(it)
                        paneDirty = false
                        paneProfileId = null
                        paneDuplicateSourceId = null
                    },
                    onNavigateBack = {
                        paneDirty = false
                        paneProfileId = null
                        paneDuplicateSourceId = null
                    },
                    onDirtyStateChange = { paneDirty = it },
                    onShowMessage = onShowMessage
                )
            }
        }
    )

    showDeleteProfileDialog.value?.let { profileId ->
        val profileName = terminalProfiles.firstOrNull { it.id == profileId }?.name ?: "this theme"
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog.value = null },
            title = { Text("Delete terminal theme?") },
            text = { Text("Delete $profileName? Hosts using it will fall back to app default.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTerminalProfile(profileId)
                        showDeleteProfileDialog.value = null
                        onShowMessage("Terminal theme deleted.")
                    },
                    modifier = Modifier.testTag(UiTestTags.THEME_DELETE_CONFIRM)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteProfileDialog.value = null },
                    modifier = Modifier.testTag(UiTestTags.THEME_DELETE_CANCEL)
                ) { Text("Cancel") }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved terminal theme changes.") },
            confirmButton = {
                TextButton(onClick = {
                    paneDirty = false
                    showDiscardDialog = false
                    if (pendingPaneClose) {
                        paneProfileId = null
                        paneDuplicateSourceId = null
                    } else {
                        paneProfileId = pendingPaneProfileId
                        paneDuplicateSourceId = pendingPaneDuplicateId
                    }
                    pendingPaneProfileId = null
                    pendingPaneDuplicateId = null
                    pendingPaneClose = false
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    pendingPaneProfileId = null
                    pendingPaneDuplicateId = null
                    pendingPaneClose = false
                }) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun ThemeListPreview(
    profile: TerminalProfile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val foreground = remember(profile.foregroundHex) {
        parseHexColorOrDefault(profile.foregroundHex, Color(0xFFE6E6E6))
    }
    val background = remember(profile.backgroundHex) {
        parseHexColorOrDefault(profile.backgroundHex, Color(0xFF101010))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(background, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            ThemeListPreviewText(
                context = context,
                text = "ssh> AaBb 0Oo1Il [] {}",
                fontSizePt = profile.fontSizeSp.toFloat(),
                textColor = foreground,
                profile = profile,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "${profile.font.label}  ${profile.fontSizeSp} pt",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ThemeListPreviewText(
    context: android.content.Context,
    text: String,
    fontSizePt: Float,
    textColor: Color,
    profile: TerminalProfile,
    modifier: Modifier = Modifier
) {
    val typeface = remember(context, profile.font) { resolveTerminalTypeface(context, profile.font) }

    key(profile.font) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                TextView(viewContext).apply {
                    isSingleLine = true
                    setTypeface(typeface)
                }
            },
            update = { view ->
                view.text = text
                view.typeface = typeface
                view.textSize = fontSizePt
                view.setTextColor(textColor.toArgb())
            }
        )
    }
}

private fun parseHexColorOrDefault(hex: String, fallback: Color): Color =
    runCatching {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return fallback
        Color(
            red = cleaned.substring(0, 2).toInt(16),
            green = cleaned.substring(2, 4).toInt(16),
            blue = cleaned.substring(4, 6).toInt(16)
        )
    }.getOrDefault(fallback)
