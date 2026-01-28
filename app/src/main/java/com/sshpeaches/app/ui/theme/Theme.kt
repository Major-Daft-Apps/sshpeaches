package com.sshpeaches.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BlazingFlame,
    onPrimary = Color.Black,
    secondary = VanillaCream,
    onSecondary = Color.Black,
    background = HardBlack,
    surface = CarbonBlack,
    onSurface = VanillaCream,
    onBackground = VanillaCream
)

private val LightColors = lightColorScheme(
    primary = BlazingFlame,
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
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
