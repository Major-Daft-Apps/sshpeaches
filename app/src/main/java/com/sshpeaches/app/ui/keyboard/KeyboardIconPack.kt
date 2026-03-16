package com.majordaftapps.sshpeaches.app.ui.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector

data class KeyboardIconSpec(
    val id: String,
    val label: String,
    val icon: ImageVector
)

object KeyboardIconPack {
    val icons: List<KeyboardIconSpec> = listOf(
        KeyboardIconSpec("terminal", "Terminal", Icons.Default.Terminal),
        KeyboardIconSpec("keyboard", "Keyboard", Icons.Default.Keyboard),
        KeyboardIconSpec("code", "Snippets", Icons.Default.Code),
        KeyboardIconSpec("snippet_picker", "Snippets", Icons.Default.Code),
        KeyboardIconSpec("swipe_nav", "Swipe Arrows", Icons.Default.OpenWith),
        KeyboardIconSpec("build", "Settings", Icons.Default.Build),
        KeyboardIconSpec("settings", "Settings", Icons.Default.Build),
        KeyboardIconSpec("reset", "Reset", Icons.Default.CleaningServices),
        KeyboardIconSpec("folder", "Folder", Icons.Default.Folder),
        KeyboardIconSpec("key", "Password", Icons.Default.VpnKey),
        KeyboardIconSpec("home", "Home", Icons.Default.Home),
        KeyboardIconSpec("up", "Up", Icons.Default.ArrowUpward),
        KeyboardIconSpec("down", "Down", Icons.Default.ArrowDownward),
        KeyboardIconSpec("left", "Left", Icons.AutoMirrored.Filled.ArrowBack),
        KeyboardIconSpec("right", "Right", Icons.AutoMirrored.Filled.ArrowForward)
    )

    fun byId(id: String?): KeyboardIconSpec? =
        icons.firstOrNull { it.id == id }
}
