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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val fnIcon: ImageVector = createFnIcon(includeShiftedStar = false)
private val fnActiveIcon: ImageVector = createFnIcon(includeShiftedStar = true)

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
        KeyboardIconSpec("right", "Right", Icons.AutoMirrored.Filled.ArrowForward),
        KeyboardIconSpec("fn", "Fn", fnIcon),
        KeyboardIconSpec("fn_active", "Fn*", fnActiveIcon)
    )

    fun byId(id: String?): KeyboardIconSpec? =
        icons.firstOrNull { it.id == id }
}

private fun createFnIcon(includeShiftedStar: Boolean): ImageVector = ImageVector.Builder(
    name = if (includeShiftedStar) "fn_active" else "fn",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        // F stem
        moveTo(4.5f, 2.2f)
        lineTo(4.5f, 21.8f)
        lineTo(6.9f, 21.8f)
        lineTo(6.9f, 13.9f)
        lineTo(10.5f, 13.9f)
        lineTo(10.5f, 11.5f)
        lineTo(6.9f, 11.5f)
        lineTo(6.9f, 2.2f)
        close()
        // F upper arm
        moveTo(6.9f, 2.2f)
        lineTo(11.8f, 2.2f)
        lineTo(11.8f, 4.8f)
        lineTo(6.9f, 4.8f)
        close()
        // F middle arm
        moveTo(6.9f, 11.0f)
        lineTo(11.1f, 11.0f)
        lineTo(11.1f, 13.6f)
        lineTo(6.9f, 13.6f)
        close()

        // n left stem
        moveTo(13.2f, 9.0f)
        lineTo(13.2f, 21.8f)
        lineTo(15.4f, 21.8f)
        lineTo(15.4f, 9.0f)
        close()
        // n top bar
        moveTo(13.2f, 9.0f)
        lineTo(20.8f, 9.0f)
        lineTo(20.8f, 11.6f)
        lineTo(13.2f, 11.6f)
        close()
        // n hump
        moveTo(17.0f, 11.6f)
        lineTo(20.4f, 11.6f)
        lineTo(20.4f, 15.6f)
        lineTo(17.0f, 15.6f)
        close()
        // n baseline
        moveTo(13.2f, 18.2f)
        lineTo(20.8f, 18.2f)
        lineTo(20.8f, 20.8f)
        lineTo(13.2f, 20.8f)
        close()
    }
    if (includeShiftedStar) {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(18.0f, 4.1f)
            lineTo(18.6f, 5.8f)
            lineTo(20.4f, 6.0f)
            lineTo(18.8f, 7.2f)
            lineTo(19.3f, 8.9f)
            lineTo(18.0f, 8.0f)
            lineTo(16.7f, 8.9f)
            lineTo(17.2f, 7.2f)
            lineTo(15.6f, 6.0f)
            lineTo(17.4f, 5.8f)
            close()
        }
    }
}.build()
