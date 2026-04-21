package com.majordaftapps.sshpeaches.app.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass

enum class ShellLayoutMode {
    COMPACT,
    WIDE,
    SESSION
}

@Composable
fun rememberShellLayoutMode(
    sessionRoute: Boolean
): ShellLayoutMode {
    if (sessionRoute) return ShellLayoutMode.SESSION
    val adaptiveInfo = currentWindowAdaptiveInfo()
    return if (adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
        ShellLayoutMode.WIDE
    } else {
        ShellLayoutMode.COMPACT
    }
}

@Composable
fun AdaptivePaneScaffold(
    shellLayoutMode: ShellLayoutMode,
    modifier: Modifier = Modifier,
    secondaryPaneVisible: Boolean,
    primaryPane: @Composable () -> Unit,
    secondaryPane: @Composable () -> Unit
) {
    if (shellLayoutMode != ShellLayoutMode.WIDE) {
        Box(modifier = modifier.fillMaxSize()) {
            primaryPane()
        }
        return
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            primaryPane()
        }
        AnimatedVisibility(
            visible = secondaryPaneVisible,
            enter = slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .width(WIDE_SECONDARY_PANE_WIDTH)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    secondaryPane()
                }
            }
        }
    }
}

@Composable
fun WideSidebarScaffold(
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .width(WIDE_SIDEBAR_WIDTH)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            sidebar()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

val WIDE_SIDEBAR_WIDTH = 248.dp
val WIDE_SECONDARY_PANE_WIDTH = 440.dp
