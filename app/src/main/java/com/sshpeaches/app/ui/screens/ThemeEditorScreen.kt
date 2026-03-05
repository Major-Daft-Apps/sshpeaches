package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    onDefaultTerminalProfileChange: (String) -> Unit,
    onSaveTerminalProfile: (TerminalProfile) -> Unit,
    onDeleteTerminalProfile: (String) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val appThemeExpanded = remember { mutableStateOf(false) }
    val defaultTerminalProfileExpanded = remember { mutableStateOf(false) }
    val showProfileEditorDialog = remember { mutableStateOf(false) }
    val showDeleteProfileDialog = remember { mutableStateOf<String?>(null) }
    val editingProfile = remember { mutableStateOf<TerminalProfile?>(null) }
    val profileNameState = remember { mutableStateOf("") }
    val profileFontSizeState = remember { mutableStateOf("12") }
    val profileForegroundState = remember { mutableStateOf("#E6E6E6") }
    val profileBackgroundState = remember { mutableStateOf("#101010") }
    val profileCursorState = remember { mutableStateOf("#FFB74D") }
    val profileCursorStyleExpanded = remember { mutableStateOf(false) }
    val profileCursorStyleState = remember { mutableStateOf(TerminalCursorStyle.BLOCK) }
    val profileCursorBlinkState = remember { mutableStateOf(true) }
    val profileEditorError = remember { mutableStateOf<String?>(null) }
    val builtInProfileIds = remember { TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet() }
    val themeOptions = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )

    fun openProfileEditor(profile: TerminalProfile?) {
        editingProfile.value = profile
        val source = profile ?: TerminalProfileDefaults.customTemplate(name = "Custom Theme")
        profileNameState.value = source.name
        profileFontSizeState.value = source.fontSizeSp.toString()
        profileForegroundState.value = source.foregroundHex
        profileBackgroundState.value = source.backgroundHex
        profileCursorState.value = source.cursorHex
        profileCursorStyleState.value = source.cursorStyle
        profileCursorBlinkState.value = source.cursorBlink
        profileEditorError.value = null
        showProfileEditorDialog.value = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Theme Editor", style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App Theme", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(
                    expanded = appThemeExpanded.value,
                    onExpandedChange = { appThemeExpanded.value = !appThemeExpanded.value }
                ) {
                    TextField(
                        value = themeOptions.first { it.first == currentTheme }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appThemeExpanded.value) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = appThemeExpanded.value,
                        onDismissRequest = { appThemeExpanded.value = false }
                    ) {
                        themeOptions.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    appThemeExpanded.value = false
                                    onThemeChange(mode)
                                }
                            )
                        }
                    }
                }
            }
        }
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
                                }
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
                            Text(
                                "Font ${profile.fontSizeSp}sp  ${profile.foregroundHex}/${profile.backgroundHex}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (builtInProfileIds.contains(profile.id)) {
                                Text("Read-only stock theme", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (builtInProfileIds.contains(profile.id)) {
                            TextButton(
                                onClick = {
                                    openProfileEditor(
                                        profile.copy(
                                            id = "custom-${UUID.randomUUID()}",
                                            name = "${profile.name} Copy"
                                        )
                                    )
                                }
                            ) {
                                Text("Duplicate")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { openProfileEditor(profile) }) {
                                    Text("Edit")
                                }
                                TextButton(onClick = { showDeleteProfileDialog.value = profile.id }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { openProfileEditor(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Custom Theme")
                }
                Text(
                    "Stock themes are read-only. Duplicate them to customize fonts, colors, and cursor behavior.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showProfileEditorDialog.value) {
        AlertDialog(
            onDismissRequest = { showProfileEditorDialog.value = false },
            title = {
                Text(if (editingProfile.value == null) "Add terminal theme" else "Edit terminal theme")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = profileNameState.value,
                        onValueChange = {
                            profileNameState.value = it
                            profileEditorError.value = null
                        },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileFontSizeState.value,
                        onValueChange = {
                            profileFontSizeState.value = it.filter { ch -> ch.isDigit() }.take(2)
                            profileEditorError.value = null
                        },
                        label = { Text("Font size (sp)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileForegroundState.value,
                        onValueChange = {
                            profileForegroundState.value = it
                            profileEditorError.value = null
                        },
                        label = { Text("Foreground color (#RRGGBB)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileBackgroundState.value,
                        onValueChange = {
                            profileBackgroundState.value = it
                            profileEditorError.value = null
                        },
                        label = { Text("Background color (#RRGGBB)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileCursorState.value,
                        onValueChange = {
                            profileCursorState.value = it
                            profileEditorError.value = null
                        },
                        label = { Text("Cursor color (#RRGGBB)") },
                        singleLine = true
                    )
                    ExposedDropdownMenuBox(
                        expanded = profileCursorStyleExpanded.value,
                        onExpandedChange = { profileCursorStyleExpanded.value = !profileCursorStyleExpanded.value }
                    ) {
                        TextField(
                            value = profileCursorStyleState.value.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cursor style") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileCursorStyleExpanded.value)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = profileCursorStyleExpanded.value,
                            onDismissRequest = { profileCursorStyleExpanded.value = false }
                        ) {
                            TerminalCursorStyle.values().forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.label) },
                                    onClick = {
                                        profileCursorStyleState.value = style
                                        profileCursorStyleExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cursor blink")
                        Switch(
                            checked = profileCursorBlinkState.value,
                            onCheckedChange = { profileCursorBlinkState.value = it }
                        )
                    }
                    profileEditorError.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fontSize = profileFontSizeState.value.toIntOrNull()?.coerceIn(8, 28)
                    when {
                        profileNameState.value.isBlank() -> {
                            profileEditorError.value = "Theme name is required."
                            return@TextButton
                        }
                        fontSize == null -> {
                            profileEditorError.value = "Font size must be between 8 and 28."
                            return@TextButton
                        }
                        !isValidHexColor(profileForegroundState.value) -> {
                            profileEditorError.value = "Foreground must be #RRGGBB."
                            return@TextButton
                        }
                        !isValidHexColor(profileBackgroundState.value) -> {
                            profileEditorError.value = "Background must be #RRGGBB."
                            return@TextButton
                        }
                        !isValidHexColor(profileCursorState.value) -> {
                            profileEditorError.value = "Cursor must be #RRGGBB."
                            return@TextButton
                        }
                    }
                    val safeFontSize = fontSize ?: return@TextButton
                    val existingId = editingProfile.value?.id
                    val profile = TerminalProfile(
                        id = existingId ?: "custom-${UUID.randomUUID()}",
                        name = profileNameState.value.trim(),
                        fontSizeSp = safeFontSize,
                        foregroundHex = profileForegroundState.value.trim().uppercase(),
                        backgroundHex = profileBackgroundState.value.trim().uppercase(),
                        cursorHex = profileCursorState.value.trim().uppercase(),
                        cursorStyle = profileCursorStyleState.value,
                        cursorBlink = profileCursorBlinkState.value
                    )
                    onSaveTerminalProfile(profile)
                    showProfileEditorDialog.value = false
                    onShowMessage("Terminal theme saved.")
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditorDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteProfileDialog.value?.let { profileId ->
        val profileName = terminalProfiles.firstOrNull { it.id == profileId }?.name ?: "this theme"
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog.value = null },
            title = { Text("Delete terminal theme?") },
            text = { Text("Delete $profileName? Hosts using it will fall back to app default.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTerminalProfile(profileId)
                    showDeleteProfileDialog.value = null
                    onShowMessage("Terminal theme deleted.")
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProfileDialog.value = null }) { Text("Cancel") }
            }
        )
    }
}

private fun isValidHexColor(value: String): Boolean =
    Regex("^#[0-9A-Fa-f]{6}$").matches(value.trim())
