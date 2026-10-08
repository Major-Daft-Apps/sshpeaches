package com.majordaftapps.sshpeaches.app.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The keyboard SSHPeaches draws itself in built-in keyboard mode, so typing never depends on the
 * system keyboard (some vendor keyboards refuse to open for the terminal, or autocorrect commands).
 */
enum class InAppKeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS }

enum class InAppShiftState { OFF, ONCE, LOCKED }

internal sealed interface InAppKey {
    val id: String
    val weight: Float

    /** Types [text]; long-pressing types [longPress] instead when set. */
    data class Char(
        val text: String,
        val longPress: String? = null,
        override val weight: Float = 1f
    ) : InAppKey {
        override val id: String = text
        val isLetter: Boolean = text.length == 1 && text[0].isLetter()
    }

    data class Action(
        val action: InAppKeyAction,
        val label: String,
        override val weight: Float = 1.5f
    ) : InAppKey {
        override val id: String = action.name.lowercase()
    }

    /** Blank space used to indent a row. */
    data class Gap(override val weight: Float) : InAppKey {
        override val id: String = "gap"
    }
}

enum class InAppKeyAction { SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, MORE_SYMBOLS, LETTERS }

internal object InAppKeyboardLayouts {
    private val digits = "1234567890"

    private fun chars(row: String, longPress: String? = null): List<InAppKey> =
        row.mapIndexed { index, c -> InAppKey.Char(c.toString(), longPress?.getOrNull(index)?.toString()) }

    private val bottomRow = listOf(
        InAppKey.Char(","),
        InAppKey.Action(InAppKeyAction.SPACE, "space", weight = 5f),
        InAppKey.Char("."),
        InAppKey.Action(InAppKeyAction.ENTER, "⏎")
    )

    val letters: List<List<InAppKey>> = listOf(
        // Long-press the top row for digits, as on most phone keyboards.
        chars("qwertyuiop", longPress = digits),
        listOf(InAppKey.Gap(0.5f)) + chars("asdfghjkl") + InAppKey.Gap(0.5f),
        listOf(InAppKey.Action(InAppKeyAction.SHIFT, "⇧")) + chars("zxcvbnm") +
            InAppKey.Action(InAppKeyAction.BACKSPACE, "⌫"),
        listOf(
            InAppKey.Action(InAppKeyAction.SYMBOLS, "?123"),
            InAppKey.Char("/"),
            InAppKey.Char("-"),
            InAppKey.Action(InAppKeyAction.SPACE, "space", weight = 4f),
            InAppKey.Char("."),
            InAppKey.Action(InAppKeyAction.ENTER, "⏎")
        )
    )

    val symbols: List<List<InAppKey>> = listOf(
        chars(digits),
        chars("@#\$_&-+()/"),
        listOf(InAppKey.Action(InAppKeyAction.MORE_SYMBOLS, "=\\<")) + chars("*\"':;!?") +
            InAppKey.Action(InAppKeyAction.BACKSPACE, "⌫"),
        listOf(InAppKey.Action(InAppKeyAction.LETTERS, "ABC")) + bottomRow
    )

    val moreSymbols: List<List<InAppKey>> = listOf(
        chars("~`|\\{}[]<>"),
        chars("^%=+°€£¥§•"),
        listOf(InAppKey.Action(InAppKeyAction.SYMBOLS, "?123")) + chars("…¬¦«»¿¡") +
            InAppKey.Action(InAppKeyAction.BACKSPACE, "⌫"),
        listOf(InAppKey.Action(InAppKeyAction.LETTERS, "ABC")) + bottomRow
    )

    fun rows(page: InAppKeyboardPage): List<List<InAppKey>> = when (page) {
        InAppKeyboardPage.LETTERS -> letters
        InAppKeyboardPage.SYMBOLS -> symbols
        InAppKeyboardPage.MORE_SYMBOLS -> moreSymbols
    }
}

/** Shift goes OFF → ONCE on a tap; a second tap within [DOUBLE_TAP_MS] locks it; any other tap turns it off. */
internal fun nextShiftState(current: InAppShiftState, sinceLastShiftTapMs: Long): InAppShiftState =
    when (current) {
        InAppShiftState.OFF -> InAppShiftState.ONCE
        InAppShiftState.ONCE ->
            if (sinceLastShiftTapMs <= DOUBLE_TAP_MS) InAppShiftState.LOCKED else InAppShiftState.OFF
        InAppShiftState.LOCKED -> InAppShiftState.OFF
    }

internal fun displayText(key: InAppKey.Char, shift: InAppShiftState): String =
    if (key.isLetter && shift != InAppShiftState.OFF) key.text.uppercase() else key.text

private const val DOUBLE_TAP_MS = 400L
private const val KEY_REPEAT_INITIAL_DELAY_MS = 400L
private const val KEY_REPEAT_INTERVAL_MS = 50L
private const val KEY_HEIGHT_DP = 42

/**
 * @param onText typed characters, including space; the caller applies latched Ctrl/Alt/Shift.
 * @param onKey Enter and Backspace, as Android key codes.
 */
@Composable
fun InAppKeyboard(
    onText: (String) -> Unit,
    onKey: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(InAppKeyboardPage.LETTERS) }
    var shift by rememberSaveable { mutableStateOf(InAppShiftState.OFF) }
    var lastShiftTapAt by remember { mutableLongStateOf(0L) }
    val colors = inAppKeyboardColors()

    fun typeChar(key: InAppKey.Char, text: String = displayText(key, shift)) {
        onText(text)
        if (shift == InAppShiftState.ONCE) shift = InAppShiftState.OFF
    }

    fun press(key: InAppKey) {
        when (key) {
            is InAppKey.Char -> typeChar(key)
            is InAppKey.Gap -> Unit
            is InAppKey.Action -> when (key.action) {
                InAppKeyAction.SHIFT -> {
                    val now = System.currentTimeMillis()
                    shift = nextShiftState(shift, now - lastShiftTapAt)
                    lastShiftTapAt = now
                }
                InAppKeyAction.BACKSPACE -> onKey(KeyEvent.KEYCODE_DEL)
                InAppKeyAction.ENTER -> onKey(KeyEvent.KEYCODE_ENTER)
                InAppKeyAction.SPACE -> onText(" ")
                InAppKeyAction.SYMBOLS -> page = InAppKeyboardPage.SYMBOLS
                InAppKeyAction.MORE_SYMBOLS -> page = InAppKeyboardPage.MORE_SYMBOLS
                InAppKeyAction.LETTERS -> page = InAppKeyboardPage.LETTERS
            }
        }
    }

    Column(
        modifier = modifier
            .background(colors.panel)
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .testTag(UiTestTags.IN_APP_KEYBOARD),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        InAppKeyboardLayouts.rows(page).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        is InAppKey.Gap -> Spacer(Modifier.weight(key.weight))
                        is InAppKey.Char -> InAppKeyButton(
                            label = displayText(key, shift),
                            hint = key.longPress,
                            description = key.text,
                            testTag = UiTestTags.inAppKey(key.id),
                            weight = key.weight,
                            colors = colors,
                            active = false,
                            actionStyle = false,
                            repeatable = false,
                            onPress = { press(key) },
                            onLongPress = key.longPress?.let { alt -> { typeChar(key, alt) } }
                        )
                        is InAppKey.Action -> InAppKeyButton(
                            label = if (key.action == InAppKeyAction.SHIFT && shift == InAppShiftState.LOCKED) {
                                "⇪"
                            } else {
                                key.label
                            },
                            hint = null,
                            description = actionDescription(key.action, shift),
                            testTag = UiTestTags.inAppKey(key.id),
                            weight = key.weight,
                            colors = colors,
                            active = key.action == InAppKeyAction.SHIFT && shift != InAppShiftState.OFF,
                            actionStyle = key.action != InAppKeyAction.SPACE,
                            repeatable = key.action == InAppKeyAction.BACKSPACE,
                            onPress = { press(key) },
                            onLongPress = null
                        )
                    }
                }
            }
        }
    }
}

private fun actionDescription(action: InAppKeyAction, shift: InAppShiftState): String = when (action) {
    InAppKeyAction.SHIFT -> when (shift) {
        InAppShiftState.OFF -> "Shift"
        InAppShiftState.ONCE -> "Shift on"
        InAppShiftState.LOCKED -> "Caps lock on"
    }
    InAppKeyAction.BACKSPACE -> "Backspace"
    InAppKeyAction.ENTER -> "Enter"
    InAppKeyAction.SPACE -> "Space"
    InAppKeyAction.SYMBOLS -> "Numbers and symbols"
    InAppKeyAction.MORE_SYMBOLS -> "More symbols"
    InAppKeyAction.LETTERS -> "Letters"
}

private data class InAppKeyboardColors(
    val panel: Color,
    val key: Color,
    val actionKey: Color,
    val pressed: Color,
    val border: Color,
    val content: Color,
    val hint: Color
)

@Composable
private fun inAppKeyboardColors(): InAppKeyboardColors {
    val scheme = MaterialTheme.colorScheme
    // Same palette as the extra-key rows above it.
    return if (scheme.background.luminance() < 0.5f) {
        InAppKeyboardColors(
            panel = Color(0xFF050505),
            key = Color(0xFF1C1C1C),
            actionKey = Color(0xFF121212),
            pressed = Color(0xFF5B3A0F),
            border = Color(0xFF474747),
            content = Color(0xFFEDEDED),
            hint = Color(0xFF9A9A9A)
        )
    } else {
        InAppKeyboardColors(
            panel = scheme.surfaceVariant.copy(alpha = 0.6f),
            key = scheme.surface,
            actionKey = scheme.surfaceVariant,
            pressed = Color(0xFFF2B27A),
            border = scheme.outline.copy(alpha = 0.7f),
            content = scheme.onSurface,
            hint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowScope.InAppKeyButton(
    label: String,
    hint: String?,
    description: String,
    testTag: String,
    weight: Float,
    colors: InAppKeyboardColors,
    active: Boolean,
    actionStyle: Boolean,
    repeatable: Boolean,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val shape = RoundedCornerShape(6.dp)
    DisposableEffect(Unit) {
        onDispose { repeatJob?.cancel() }
    }
    val background = when {
        pressed || active -> colors.pressed
        actionStyle -> colors.actionKey
        else -> colors.key
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .height(KEY_HEIGHT_DP.dp)
            .testTag(testTag)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (active) selected = true
                onClick(label = description) {
                    currentOnPress()
                    true
                }
            }
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .background(background)
            .pointerInput(repeatable) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        if (repeatable) {
                            // Backspace acts on press and repeats while held.
                            currentOnPress()
                            repeatJob?.cancel()
                            repeatJob = scope.launch {
                                delay(KEY_REPEAT_INITIAL_DELAY_MS)
                                while (isActive) {
                                    currentOnPress()
                                    delay(KEY_REPEAT_INTERVAL_MS)
                                }
                            }
                            waitForUpOrCancellation()?.consume()
                            return@awaitEachGesture
                        }
                        val longPress = currentOnLongPress
                        if (longPress == null) {
                            waitForUpOrCancellation()?.let {
                                it.consume()
                                currentOnPress()
                            }
                            return@awaitEachGesture
                        }
                        var finished = false
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation().also { finished = true }
                        }
                        when {
                            !finished -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                longPress()
                                waitForUpOrCancellation()?.consume()
                            }
                            up != null -> {
                                up.consume()
                                currentOnPress()
                            }
                        }
                    } finally {
                        repeatJob?.cancel()
                        repeatJob = null
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.content,
            fontSize = if (label.length > 2) 13.sp else 19.sp,
            fontWeight = if (label.length > 2) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
        if (hint != null) {
            Text(
                text = hint,
                color = colors.hint,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}
