package com.majordaftapps.sshpeaches.app.ui.theme

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
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode

private val DarkColors = darkColorScheme(
    primary = PeachyOrange,
    onPrimary = Color.Black,
    primaryContainer = DarkOrangeContainer,
    onPrimaryContainer = LightOrangeContent,
    inversePrimary = BurntOrange,
    secondary = PeachyOrange,
    onSecondary = Color.Black,
    secondaryContainer = DarkOrangeContainer,
    onSecondaryContainer = LightOrangeContent,
    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF3F2700),
    tertiaryContainer = Color(0xFF5C4000),
    onTertiaryContainer = Color(0xFFFFE1A6),
    background = HardBlack,
    onBackground = VanillaCream,
    surface = CarbonBlack,
    onSurface = VanillaCream,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmBeige,
    surfaceTint = PeachyOrange,
    inverseSurface = WarmCream,
    inverseOnSurface = WarmCharcoal,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = WarmOutline,
    outlineVariant = WarmSurfaceVariant,
    scrim = Color.Black,
    surfaceBright = Color(0xFF3E3833),
    surfaceContainer = Color(0xFF211E1B),
    surfaceContainerHigh = Color(0xFF2B2723),
    surfaceContainerHighest = Color(0xFF36302B),
    surfaceContainerLow = Color(0xFF171513),
    surfaceContainerLowest = Color(0xFF0F0E0C),
    surfaceDim = Color(0xFF151311)
)

private val LightColors = lightColorScheme(
    primary = BurntOrange,
    onPrimary = Color.White,
    primaryContainer = LightOrangeContainer,
    onPrimaryContainer = Color(0xFF321A00),
    inversePrimary = Color(0xFFFFB66A),
    secondary = BurntOrange,
    onSecondary = Color.White,
    secondaryContainer = LightOrangeContainer,
    onSecondaryContainer = Color(0xFF321A00),
    tertiary = Color(0xFF765A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color.White,
    onBackground = CarbonBlack,
    surface = VanillaCream,
    onSurface = CarbonBlack,
    surfaceVariant = WarmLightSurfaceVariant,
    onSurfaceVariant = WarmLightOnSurfaceVariant,
    surfaceTint = BurntOrange,
    inverseSurface = WarmSurface,
    inverseOnSurface = WarmCream,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = WarmLightOutline,
    outlineVariant = WarmLightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = WarmCream,
    surfaceContainer = Color(0xFFF8EEE6),
    surfaceContainerHigh = Color(0xFFF2E7DE),
    surfaceContainerHighest = Color(0xFFECE0D6),
    surfaceContainerLow = Color(0xFFFCF4EE),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFDED4CC)
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            val systemBarColor = scheme.surface.toArgb()
            window.statusBarColor = systemBarColor
            window.navigationBarColor = systemBarColor
            val insetsController = WindowCompat.getInsetsController(window, view)
            val lightIcons = !useDark
            insetsController.isAppearanceLightStatusBars = lightIcons
            insetsController.isAppearanceLightNavigationBars = lightIcons
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
