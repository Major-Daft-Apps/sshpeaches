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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Theme Editor", style = MaterialTheme.typography.headlineSmall)
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
                                "Font ${profile.fontSizeSp} pt  ${profile.foregroundHex}/${profile.backgroundHex}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (builtInProfileIds.contains(profile.id)) {
                            TextButton(onClick = { onDuplicateTheme(profile.id) }) {
                                Text("Duplicate")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onEditTheme(profile.id) }) {
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
                    onClick = onCreateTheme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("New Terminal Theme")
                }
                Text(
                    "Open a theme to edit name, text size, and colors using dedicated picker modals.",
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
