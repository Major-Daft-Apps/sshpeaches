package com.majordaftapps.sshpeaches.app.ui.adaptive

import android.view.MotionEvent
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi

@Composable
fun rememberDesktopHoverState(
    enabled: Boolean = true
): Pair<MutableInteractionSource, Boolean> {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    return interactionSource to (enabled && isHovered)
}

fun Modifier.desktopHoverable(
    enabled: Boolean,
    interactionSource: MutableInteractionSource
): Modifier {
    if (!enabled) return this
    return this
        .hoverable(interactionSource = interactionSource)
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.secondaryClickToOpen(
    enabled: Boolean = true,
    onSecondaryClick: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInteropFilter { event ->
        val isSecondary = (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
        if (isSecondary && event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            onSecondaryClick()
            true
        } else {
            false
        }
    }
}
