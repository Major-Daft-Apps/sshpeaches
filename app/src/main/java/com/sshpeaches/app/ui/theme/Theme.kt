package com.sshpeaches.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    val targetScheme = if (useDark) DarkColors else LightColors

    val animationSpec = androidx.compose.animation.core.tween<Color>(durationMillis = 300)

    val primary = androidx.compose.animation.animateColorAsState(targetScheme.primary, animationSpec, label = "primary")
    val onPrimary = androidx.compose.animation.animateColorAsState(targetScheme.onPrimary, animationSpec, label = "onPrimary")
    val secondary = androidx.compose.animation.animateColorAsState(targetScheme.secondary, animationSpec, label = "secondary")
    val onSecondary = androidx.compose.animation.animateColorAsState(targetScheme.onSecondary, animationSpec, label = "onSecondary")
    val background = androidx.compose.animation.animateColorAsState(targetScheme.background, animationSpec, label = "background")
    val surface = androidx.compose.animation.animateColorAsState(targetScheme.surface, animationSpec, label = "surface")
    val onSurface = androidx.compose.animation.animateColorAsState(targetScheme.onSurface, animationSpec, label = "onSurface")
    val onBackground = androidx.compose.animation.animateColorAsState(targetScheme.onBackground, animationSpec, label = "onBackground")

    val animatedScheme = targetScheme.copy(
        primary = primary.value,
        onPrimary = onPrimary.value,
        secondary = secondary.value,
        onSecondary = onSecondary.value,
        background = background.value,
        surface = surface.value,
        onSurface = onSurface.value,
        onBackground = onBackground.value
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        content = content
    )
}
