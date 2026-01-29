package com.sshpeaches.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sshpeaches.app.ui.state.ThemeMode

private val DarkColors = darkColorScheme(
    primary = PeachyOrange,
    onPrimary = Color.Black,
    secondary = VanillaCream,
    onSecondary = Color.Black,
    background = HardBlack,
    surface = CarbonBlack,
    onSurface = VanillaCream,
    onBackground = VanillaCream
)

private val LightColors = lightColorScheme(
    primary = PeachyOrange,
    onPrimary = Color.White,
    secondary = CarbonBlack,
    onSecondary = Color.White,
    background = Color.White,
    surface = VanillaCream,
    onSurface = CarbonBlack,
    onBackground = CarbonBlack
)

@Composable
fun SSHPeachesTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (useDark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
