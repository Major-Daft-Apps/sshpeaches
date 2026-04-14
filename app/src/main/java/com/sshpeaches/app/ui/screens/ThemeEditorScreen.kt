package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.terminal.resolveTerminalTypeface
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    onDefaultTerminalProfileChange: (String) -> Unit,
    onDeleteTerminalProfile: (String) -> Unit,
    onCreateTheme: () -> Unit,
    onEditTheme: (String) -> Unit,
    onDuplicateTheme: (String) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val defaultTerminalProfileExpanded = remember { mutableStateOf(false) }
    val showDeleteProfileDialog = remember { mutableStateOf<String?>(null) }
    val builtInProfileIds = remember { TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet() }

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
                                onClick = { onDuplicateTheme(profile.id) },
                                modifier = Modifier.testTag(UiTestTags.themeDuplicate(profile.id))
                            ) {
                                Text("Duplicate")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onEditTheme(profile.id) },
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
                    onClick = onCreateTheme,
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
