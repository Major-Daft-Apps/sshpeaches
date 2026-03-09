package com.majordaftapps.sshpeaches.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay

private const val PASSWORD_TAIL_REVEAL_TIMEOUT_MS = 700L

class TailRevealPasswordVisualTransformation(
    private val revealedIndex: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = buildString(text.length) {
            text.text.forEachIndexed { index, char ->
                append(if (index == revealedIndex) char else '\u2022')
            }
        }
        return TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
    }
}

fun calculatePasswordRevealIndex(previous: String, next: String): Int =
    if (next.length > previous.length) next.lastIndex else -1

fun updatePasswordStateWithReveal(
    state: MutableState<String>,
    revealIndexState: MutableIntState,
    next: String
) {
    val previous = state.value
    state.value = next
    revealIndexState.intValue = calculatePasswordRevealIndex(previous, next)
}

@Composable
fun AutoHidePasswordReveal(
    revealIndexState: MutableIntState
) {
    LaunchedEffect(revealIndexState.intValue) {
        if (revealIndexState.intValue < 0) return@LaunchedEffect
        delay(PASSWORD_TAIL_REVEAL_TIMEOUT_MS)
        revealIndexState.intValue = -1
    }
}

fun newPasswordRevealState(): MutableIntState = mutableIntStateOf(-1)
