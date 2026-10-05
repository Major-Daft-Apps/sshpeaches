package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

fun resolveSessionTerminalProfileId(
    sessionId: String?,
    requestProfileId: String?,
    overrides: Map<String, String>
): String? {
    if (sessionId != null) {
        overrides[sessionId]?.let { return it }
    }
    return requestProfileId
}

/** The terminal top bar's ⋮ menu. "Change theme" swaps the menu to the profile list. */
@Composable
fun SessionOverflowMenu(
    profiles: List<TerminalProfile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit,
    arrowKeysEnabled: Boolean,
    onToggleArrowKeys: () -> Unit,
    onInsertPassword: () -> Unit,
    onFind: () -> Unit,
    onSnippets: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showingThemes by remember { mutableStateOf(false) }
    fun close() {
        menuOpen = false
        showingThemes = false
    }
    Box(modifier = modifier) {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.testTag(UiTestTags.CONNECTING_MENU_BUTTON)
        ) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Session options")
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { close() },
            modifier = Modifier.testTag(UiTestTags.CONNECTING_MENU)
        ) {
            if (showingThemes) {
                DropdownMenuItem(
                    text = { Text("Change theme") },
                    onClick = { showingThemes = false },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_THEME_BACK_BUTTON)
                )
                HorizontalDivider()
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            close()
                            onSelectProfile(profile.id)
                        },
                        trailingIcon = if (profile.id == selectedProfileId) {
                            { Icon(Icons.Default.Check, contentDescription = "Selected") }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag(UiTestTags.connectingThemeItem(profile.id))
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text("Swipe for arrow keys") },
                    onClick = {
                        close()
                        onToggleArrowKeys()
                    },
                    leadingIcon = { Icon(Icons.Default.OpenWith, contentDescription = null) },
                    trailingIcon = if (arrowKeysEnabled) {
                        { Icon(Icons.Default.Check, contentDescription = "On") }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_ARROW_KEYS_BUTTON)
                )
                DropdownMenuItem(
                    text = { Text("Insert password") },
                    onClick = {
                        close()
                        onInsertPassword()
                    },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_INSERT_PASSWORD_BUTTON)
                )
                DropdownMenuItem(
                    text = { Text("Change theme") },
                    onClick = { showingThemes = true },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingIcon = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_THEME_BUTTON)
                )
                DropdownMenuItem(
                    text = { Text("Find") },
                    onClick = {
                        close()
                        onFind()
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_FIND_BUTTON)
                )
                DropdownMenuItem(
                    text = { Text("Snippets") },
                    onClick = {
                        close()
                        onSnippets()
                    },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SNIPPETS_BUTTON)
                )
                DropdownMenuItem(
                    text = { Text("Reset") },
                    onClick = {
                        close()
                        onReset()
                    },
                    leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_RESET_BUTTON)
                )
            }
        }
    }
}
