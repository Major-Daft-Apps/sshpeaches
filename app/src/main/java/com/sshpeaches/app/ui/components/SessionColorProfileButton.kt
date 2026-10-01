package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun SessionColorProfileButton(
    profiles: List<TerminalProfile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuOpen = remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { menuOpen.value = true },
            modifier = Modifier.testTag(UiTestTags.CONNECTING_THEME_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Color profile"
            )
        }
        DropdownMenu(
            expanded = menuOpen.value,
            onDismissRequest = { menuOpen.value = false },
            modifier = Modifier.testTag(UiTestTags.CONNECTING_THEME_MENU)
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        menuOpen.value = false
                        onSelectProfile(profile.id)
                    },
                    trailingIcon = if (profile.id == selectedProfileId) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected"
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(UiTestTags.connectingThemeItem(profile.id))
                )
            }
        }
    }
}
