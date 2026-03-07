package com.majordaftapps.sshpeaches.app.ui.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
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
        KeyboardIconSpec("code", "Code", Icons.Default.Code),
        KeyboardIconSpec("search", "Search", Icons.Default.Search),
        KeyboardIconSpec("build", "Tools", Icons.Default.Build),
        KeyboardIconSpec("folder", "Folder", Icons.Default.Folder),
        KeyboardIconSpec("home", "Home", Icons.Default.Home),
        KeyboardIconSpec("up", "Up", Icons.Default.ArrowUpward),
        KeyboardIconSpec("down", "Down", Icons.Default.ArrowDownward),
        KeyboardIconSpec("left", "Left", Icons.Default.ArrowBack),
        KeyboardIconSpec("right", "Right", Icons.Default.ArrowForward)
    )

    fun byId(id: String?): KeyboardIconSpec? =
        icons.firstOrNull { it.id == id }
}
