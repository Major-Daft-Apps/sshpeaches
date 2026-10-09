package com.majordaftapps.sshpeaches.app.ui.keyboard

import android.view.KeyEvent

enum class KeyboardActionType {
    TEXT,
    KEY,
    MODIFIER,
    SEQUENCE,
    PASSWORD_INJECT,
    SNIPPET_PICKER
}

enum class KeyboardModifier {
    CTRL,
    ALT,
    SHIFT
}

data class KeyboardSlotAction(
    val type: KeyboardActionType,
    val label: String,
    val text: String = "",
    val keyCode: Int? = null,
    val modifier: KeyboardModifier? = null,
    val sequence: String = "",
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val repeatable: Boolean = false,
    val iconId: String = ""
) {
    fun isEmpty(): Boolean =
        type == KeyboardActionType.TEXT && text.isBlank() && label.isBlank() && iconId.isBlank()
}

object KeyboardLayoutDefaults {
    const val SLOT_COLUMNS = 7
    const val SLOT_ROWS = 2
    const val SLOT_COUNT = SLOT_COLUMNS * SLOT_ROWS
    const val BUILTIN_SLOT_ROWS = SLOT_ROWS
    const val BUILTIN_SLOT_COUNT = SLOT_COLUMNS * BUILTIN_SLOT_ROWS
    const val MAX_PERSISTED_SLOT_COUNT = SLOT_COLUMNS * 4
    const val COMPACT_KEY_LABEL_MAX_CHARS = 6
    const val COMPACT_KEY_HEIGHT_DP = 30
    const val COMPACT_KEY_FONT_SP = 10

    // Swipe Nav (the arrows toggle) lives in the terminal's overflow menu; Fn takes its slot.
    val DEFAULT_SLOTS: List<KeyboardSlotAction> = listOf(
        keyAction("Esc", KeyEvent.KEYCODE_ESCAPE),
        modifierAction(KeyboardModifier.ALT, "Alt"),
        keyAction("Home", KeyEvent.KEYCODE_MOVE_HOME, repeatable = true),
        keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true).copy(iconId = "up"),
        keyAction("End", KeyEvent.KEYCODE_MOVE_END, repeatable = true),
        keyAction("PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
        fnKeyAction(),
        keyAction("Tab", KeyEvent.KEYCODE_TAB),
        modifierAction(KeyboardModifier.CTRL, "Ctrl"),
        keyAction("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true).copy(iconId = "left"),
        keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true).copy(iconId = "down"),
        keyAction("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true).copy(iconId = "right"),
        keyAction("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true),
        textAction(label = "Keyboard", text = "").copy(iconId = "keyboard")
    )

    // Fn layer: Back (return to the main rows), F1-F12, then the keyboard toggle.
    private val FIXED_FN_SLOTS: List<KeyboardSlotAction> = listOf(fnBackAction()) +
        (1..12).map(::functionKeyAction) +
        textAction(label = "Keyboard", text = "").copy(iconId = "keyboard")

    // The keyboard editor's choices, in its groups. Letters and digits (the phone keyboard has
    // them), the numpad, and lock/system keys that send nothing to a terminal are not offered;
    // layouts that already use them keep working.
    val modifierPresets: List<KeyboardSlotAction> = listOf(
        modifierAction(KeyboardModifier.CTRL, "Ctrl"),
        modifierAction(KeyboardModifier.ALT, "Alt"),
        modifierAction(KeyboardModifier.SHIFT, "Shift")
    )

    val keyPresets: List<KeyboardSlotAction> = listOf(
        keyAction("Esc", KeyEvent.KEYCODE_ESCAPE),
        keyAction("Tab", KeyEvent.KEYCODE_TAB),
        keyAction("⇧Tab", KeyEvent.KEYCODE_TAB, shift = true),
        keyAction("Enter", KeyEvent.KEYCODE_ENTER),
        keyAction("Bksp", KeyEvent.KEYCODE_DEL, repeatable = true),
        keyAction("Delete", KeyEvent.KEYCODE_FORWARD_DEL, repeatable = true),
        keyAction("Insert", KeyEvent.KEYCODE_INSERT),
        keyAction("Space", KeyEvent.KEYCODE_SPACE),
        keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true).copy(iconId = "up"),
        keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true).copy(iconId = "down"),
        keyAction("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true).copy(iconId = "left"),
        keyAction("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true).copy(iconId = "right"),
        keyAction("Home", KeyEvent.KEYCODE_MOVE_HOME, repeatable = true),
        keyAction("End", KeyEvent.KEYCODE_MOVE_END, repeatable = true),
        keyAction("PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
        keyAction("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true)
    ) + (1..12).map(::functionKeyAction)

    val shortcutPresets: List<KeyboardSlotAction> = listOfNotNull(
        combinationAction("C", ctrl = true),
        combinationAction("D", ctrl = true),
        combinationAction("Z", ctrl = true),
        combinationAction("L", ctrl = true),
        combinationAction("R", ctrl = true),
        combinationAction("A", ctrl = true),
        combinationAction("B", ctrl = true),
        combinationAction("E", ctrl = true),
        combinationAction("K", ctrl = true),
        combinationAction("U", ctrl = true),
        combinationAction("W", ctrl = true),
        keyAction("Ctrl-\\", KeyEvent.KEYCODE_BACKSLASH, sequence = "\u001C", ctrl = true),
        keyAction("Ctrl-←", KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, repeatable = true),
        keyAction("Ctrl-→", KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true, repeatable = true),
        combinationAction("B", alt = true),
        combinationAction("F", alt = true),
        keyAction("Alt-.", KeyEvent.KEYCODE_PERIOD, alt = true),
        keyAction("Alt-⌫", KeyEvent.KEYCODE_DEL, alt = true, repeatable = true)
    )

    val symbolPresets: List<KeyboardSlotAction> =
        listOf("|", "&", ";", "$", "*", "!", "#", "~", "`", "/", "\\", "-", "_", "=", "+",
            "{", "}", "[", "]", "(", ")", "<", ">", "'", "\"", ":", "?", "@", "%", "^")
            .map { textAction(it) }

    val actionPresets: List<KeyboardSlotAction> = listOf(
        textAction(label = "Keyboard", text = "").copy(iconId = "keyboard"),
        textAction(label = "Paste", text = "").copy(iconId = "paste"),
        snippetPickerAction(label = "Snippets", iconId = "code"),
        passwordInjectAction(label = "Password", iconId = "key"),
        textAction(label = "Swipe arrows", text = "").copy(iconId = "swipe_nav"),
        textAction(label = "Find", text = "").copy(iconId = "search"),
        textAction(label = "Settings", text = "").copy(iconId = "build")
    )

    /** A custom-text key; [pressEnter] sends Enter after the text, e.g. for `git status`. */
    fun customTextAction(text: String, pressEnter: Boolean): KeyboardSlotAction =
        textAction(text = if (pressEnter) "$text\r" else text, label = text)

    fun emptyAction(): KeyboardSlotAction = textAction("")

    fun compactLabel(action: KeyboardSlotAction, fallback: String = ""): String {
        val base = if (action.label.isNotBlank()) action.label else action.text
        val compact = base.trim().replace("\n", " ").take(COMPACT_KEY_LABEL_MAX_CHARS)
        return if (compact.isBlank()) fallback else compact
    }

    fun normalizeSlots(slots: List<KeyboardSlotAction>): List<KeyboardSlotAction> {
        if (slots.isEmpty()) return DEFAULT_SLOTS
        val visibleSlots = if (slots.size > SLOT_COUNT) slots.takeLast(SLOT_COUNT) else slots
        return List(SLOT_COUNT) { index ->
            val action = visibleSlots.getOrNull(index) ?: DEFAULT_SLOTS.getOrNull(index) ?: emptyAction()
            applyLegacyIconAlias(action)
        }
    }

    /**
     * One-time move for layouts saved before 0.11.3: swipe arrows are toggled from the session ⋮
     * menu now, so the Swipe Nav key becomes Fn. A Fn key elsewhere becomes Alt when the layout has
     * no Alt, which turns both earlier defaults into DEFAULT_SLOTS; other keys are left alone.
     */
    fun replaceSwipeNavWithFn(slots: List<KeyboardSlotAction>): List<KeyboardSlotAction> {
        val swipeIndex = slots.indexOfFirst { it.iconId == "swipe_nav" }
        if (swipeIndex < 0) return slots
        val hasAlt = slots.any { it.type == KeyboardActionType.MODIFIER && it.modifier == KeyboardModifier.ALT }
        return slots.mapIndexed { index, slot ->
            when {
                index == swipeIndex -> fnKeyAction()
                !hasAlt && slot.iconId in setOf("fn", "fn_active") -> modifierAction(KeyboardModifier.ALT, "Alt")
                else -> slot
            }
        }
    }

    fun builtInCompactLayout(slots: List<KeyboardSlotAction>): List<KeyboardSlotAction> =
        normalizeSlots(slots)

    @Suppress("UNUSED_PARAMETER")
    fun builtInFnLayout(slots: List<KeyboardSlotAction> = DEFAULT_SLOTS): List<KeyboardSlotAction> =
        FIXED_FN_SLOTS

    @Suppress("UNUSED_PARAMETER")
    fun customFnLayout(slots: List<KeyboardSlotAction>): List<KeyboardSlotAction> = FIXED_FN_SLOTS

    fun fnKeyAction(active: Boolean = false): KeyboardSlotAction = textAction(
        text = "",
        label = if (active) "Fn*" else "Fn"
    ).copy(iconId = if (active) "fn_active" else "fn")

    fun fnBackAction(): KeyboardSlotAction = textAction(text = "", label = "Back")
        .copy(iconId = "fn_back")

    fun textAction(text: String, label: String = text): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.TEXT,
        label = label,
        text = text
    )

    fun keyAction(
        label: String,
        keyCode: Int,
        sequence: String = "",
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        repeatable: Boolean = false
    ): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.KEY,
        label = label,
        keyCode = keyCode,
        sequence = sequence,
        ctrl = ctrl,
        alt = alt,
        shift = shift,
        repeatable = repeatable
    )

    fun modifierAction(modifier: KeyboardModifier, label: String): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.MODIFIER,
        label = label,
        modifier = modifier
    )

    fun sequenceAction(label: String, sequence: String): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.SEQUENCE,
        label = label,
        sequence = sequence
    )

    fun snippetPickerAction(
        label: String = "Snippets",
        iconId: String = "code"
    ): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.SNIPPET_PICKER,
        label = label,
        iconId = iconId
    )

    fun passwordInjectAction(
        label: String = "Password",
        iconId: String = "key"
    ): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.PASSWORD_INJECT,
        label = label,
        iconId = iconId
    )

    fun combinationAction(
        keyToken: String,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        customLabel: String = ""
    ): KeyboardSlotAction? {
        val spec = parseCombinationKeyToken(keyToken) ?: return null
        val label = customLabel.takeIf { it.isNotBlank() }
            ?: buildCombinationLabel(spec.label, ctrl = ctrl, alt = alt, shift = shift)
        val fallback = if (ctrl && !alt) spec.ctrlFallback.orEmpty() else ""
        return keyAction(
            label = label,
            keyCode = spec.keyCode,
            sequence = fallback,
            ctrl = ctrl,
            alt = alt,
            shift = shift
        )
    }

    fun keyTokenForAction(action: KeyboardSlotAction): String {
        if (action.type != KeyboardActionType.KEY) return ""
        val keyCode = action.keyCode ?: return ""
        return keyTokenForCode(keyCode)
    }

    fun legacyStringToAction(value: String): KeyboardSlotAction {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyAction()
        val lower = trimmed.lowercase()
        val ctrlCombo = parseLegacyCtrlLetterCombo(lower)
        if (ctrlCombo != null) return ctrlCombo
        return when (lower) {
            "ctrl" -> modifierAction(KeyboardModifier.CTRL, "Ctrl")
            "alt" -> modifierAction(KeyboardModifier.ALT, "Alt")
            "shift" -> modifierAction(KeyboardModifier.SHIFT, "Shift")
            "esc" -> keyAction("Esc", KeyEvent.KEYCODE_ESCAPE)
            "tab" -> keyAction("Tab", KeyEvent.KEYCODE_TAB)
            "ent", "enter" -> keyAction("Enter", KeyEvent.KEYCODE_ENTER)
            "bk", "bsp", "backspace", "del" -> keyAction("Bksp", KeyEvent.KEYCODE_DEL, repeatable = true)
            "home" -> keyAction("Home", KeyEvent.KEYCODE_MOVE_HOME)
            "end" -> keyAction("End", KeyEvent.KEYCODE_MOVE_END)
            "pgup" -> keyAction("PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true)
            "pgdn", "pgdown" -> keyAction("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true)
            "up" -> keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true).copy(iconId = "up")
            "dn", "down" -> keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true).copy(iconId = "down")
            "lt", "left" -> keyAction("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true).copy(iconId = "left")
            "rt", "right" -> keyAction("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true).copy(iconId = "right")
            "c-c" -> sequenceAction("C-C", "\u0003")
            "c-d" -> sequenceAction("C-D", "\u0004")
            "c-z" -> sequenceAction("C-Z", "\u001A")
            "snippet", "snippets" -> snippetPickerAction()
            "password", "pwd", "pass" -> passwordInjectAction()
            "swipe", "swipenav", "swipe-nav" -> textAction(label = "Swipe Nav", text = "").copy(iconId = "swipe_nav")
            "keyboard" -> textAction(label = "Keyboard", text = "").copy(iconId = "keyboard")
            "fn" -> fnKeyAction()
            "reset", "clr", "clear" -> textAction(label = "reset+Enter", text = "").copy(iconId = "reset")
            "settings", "wrench", "tools", "tool" -> textAction(label = "Settings", text = "").copy(iconId = "build")
            else -> {
                val fn = parseFunctionKey(lower)
                if (fn != null) functionKeyAction(fn) else textAction(trimmed)
            }
        }
    }

    private fun functionKeyAction(index: Int): KeyboardSlotAction {
        val keyCode = when (index) {
            1 -> KeyEvent.KEYCODE_F1
            2 -> KeyEvent.KEYCODE_F2
            3 -> KeyEvent.KEYCODE_F3
            4 -> KeyEvent.KEYCODE_F4
            5 -> KeyEvent.KEYCODE_F5
            6 -> KeyEvent.KEYCODE_F6
            7 -> KeyEvent.KEYCODE_F7
            8 -> KeyEvent.KEYCODE_F8
            9 -> KeyEvent.KEYCODE_F9
            10 -> KeyEvent.KEYCODE_F10
            11 -> KeyEvent.KEYCODE_F11
            12 -> KeyEvent.KEYCODE_F12
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
        val fallback = when (index) {
            1 -> "\u001BOP"
            2 -> "\u001BOQ"
            3 -> "\u001BOR"
            4 -> "\u001BOS"
            5 -> "\u001B[15~"
            6 -> "\u001B[17~"
            7 -> "\u001B[18~"
            8 -> "\u001B[19~"
            9 -> "\u001B[20~"
            10 -> "\u001B[21~"
            11 -> "\u001B[23~"
            12 -> "\u001B[24~"
            else -> ""
        }
        return keyAction(
            label = "F$index",
            keyCode = keyCode,
            sequence = fallback
        )
    }

    private fun parseFunctionKey(value: String): Int? {
        if (!value.startsWith("f")) return null
        val number = value.removePrefix("f").toIntOrNull() ?: return null
        return number.takeIf { it in 1..12 }
    }

    private fun parseLegacyCtrlLetterCombo(value: String): KeyboardSlotAction? {
        val letter = when {
            value.startsWith("ctrl-") && value.length == 6 -> value.last()
            value.startsWith("c-") && value.length == 3 -> value.last()
            else -> null
        } ?: return null
        if (!letter.isLetter()) return null
        return combinationAction(letter.uppercaseChar().toString(), ctrl = true)
    }

    private fun buildCombinationLabel(base: String, ctrl: Boolean, alt: Boolean, shift: Boolean): String {
        val modifiers = mutableListOf<String>()
        if (ctrl) modifiers += "Ctrl"
        if (alt) modifiers += "Alt"
        if (shift) modifiers += "Shift"
        return if (modifiers.isEmpty()) {
            base
        } else {
            "${modifiers.joinToString("+")}-$base"
        }
    }

    private fun parseCombinationKeyToken(token: String): CombinationKeySpec? {
        val normalized = token.trim().uppercase()
        if (normalized.isBlank()) return null
        if (normalized.length == 1) {
            val single = normalized[0]
            if (single in 'A'..'Z') {
                val keyCode = KeyEvent.KEYCODE_A + (single.code - 'A'.code)
                val controlValue = single.code - 'A'.code + 1
                return CombinationKeySpec(
                    keyCode = keyCode,
                    label = single.toString(),
                    ctrlFallback = String(charArrayOf(controlValue.toChar()))
                )
            }
            if (single in '0'..'9') {
                val keyCode = KeyEvent.KEYCODE_0 + (single.code - '0'.code)
                return CombinationKeySpec(keyCode = keyCode, label = single.toString())
            }
        }

        val fn = parseFunctionKey(normalized.lowercase())
        if (fn != null) {
            val action = functionKeyAction(fn)
            return CombinationKeySpec(
                keyCode = action.keyCode ?: return null,
                label = action.label,
                ctrlFallback = action.sequence.takeIf { it.isNotBlank() }
            )
        }

        return when (normalized) {
            "ESC", "ESCAPE" -> CombinationKeySpec(KeyEvent.KEYCODE_ESCAPE, "Esc")
            "TAB" -> CombinationKeySpec(KeyEvent.KEYCODE_TAB, "Tab")
            "ENTER", "RETURN" -> CombinationKeySpec(KeyEvent.KEYCODE_ENTER, "Enter")
            "BACKSPACE", "BKSP", "DEL", "DELETE" -> CombinationKeySpec(KeyEvent.KEYCODE_DEL, "Bksp")
            "SPACE" -> CombinationKeySpec(
                keyCode = KeyEvent.KEYCODE_SPACE,
                label = "Space",
                ctrlFallback = String(charArrayOf(0.toChar()))
            )
            "UP" -> CombinationKeySpec(KeyEvent.KEYCODE_DPAD_UP, "Up")
            "DOWN" -> CombinationKeySpec(KeyEvent.KEYCODE_DPAD_DOWN, "Down")
            "LEFT" -> CombinationKeySpec(KeyEvent.KEYCODE_DPAD_LEFT, "Left")
            "RIGHT" -> CombinationKeySpec(KeyEvent.KEYCODE_DPAD_RIGHT, "Right")
            "HOME" -> CombinationKeySpec(KeyEvent.KEYCODE_MOVE_HOME, "Home")
            "END" -> CombinationKeySpec(KeyEvent.KEYCODE_MOVE_END, "End")
            "PGUP", "PAGEUP" -> CombinationKeySpec(KeyEvent.KEYCODE_PAGE_UP, "PgUp")
            "PGDN", "PAGEDOWN" -> CombinationKeySpec(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn")
            else -> null
        }
    }

    private fun keyTokenForCode(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> "ESC"
        KeyEvent.KEYCODE_TAB -> "TAB"
        KeyEvent.KEYCODE_ENTER -> "ENTER"
        KeyEvent.KEYCODE_DEL -> "BACKSPACE"
        KeyEvent.KEYCODE_SPACE -> "SPACE"
        KeyEvent.KEYCODE_DPAD_UP -> "UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        KeyEvent.KEYCODE_MOVE_HOME -> "HOME"
        KeyEvent.KEYCODE_MOVE_END -> "END"
        KeyEvent.KEYCODE_PAGE_UP -> "PGUP"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PGDN"
        KeyEvent.KEYCODE_F1 -> "F1"
        KeyEvent.KEYCODE_F2 -> "F2"
        KeyEvent.KEYCODE_F3 -> "F3"
        KeyEvent.KEYCODE_F4 -> "F4"
        KeyEvent.KEYCODE_F5 -> "F5"
        KeyEvent.KEYCODE_F6 -> "F6"
        KeyEvent.KEYCODE_F7 -> "F7"
        KeyEvent.KEYCODE_F8 -> "F8"
        KeyEvent.KEYCODE_F9 -> "F9"
        KeyEvent.KEYCODE_F10 -> "F10"
        KeyEvent.KEYCODE_F11 -> "F11"
        KeyEvent.KEYCODE_F12 -> "F12"
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
            ('A'.code + (keyCode - KeyEvent.KEYCODE_A)).toChar().toString()
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            ('0'.code + (keyCode - KeyEvent.KEYCODE_0)).toChar().toString()
        else -> ""
    }

    private fun applyLegacyIconAlias(action: KeyboardSlotAction): KeyboardSlotAction {
        val withDirectionAlias = applyDirectionalIconAlias(action)
        return applyFnIconAlias(withDirectionAlias)
    }

    private fun applyDirectionalIconAlias(action: KeyboardSlotAction): KeyboardSlotAction {
        if (action.type != KeyboardActionType.KEY) return action
        if (action.iconId.isNotBlank()) return action
        if (action.ctrl || action.alt || action.shift) return action
        val keyCode = action.keyCode ?: return action
        val iconId = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> return action
        }
        return action.copy(iconId = iconId)
    }

    private fun applyFnIconAlias(action: KeyboardSlotAction): KeyboardSlotAction {
        if (action.iconId.isNotBlank()) return action
        if (action.type != KeyboardActionType.TEXT) return action
        if (action.text.isNotBlank()) return action
        return when (action.label.trim().lowercase()) {
            "fn" -> action.copy(iconId = "fn")
            "fn*" -> action.copy(iconId = "fn_active")
            else -> action
        }
    }

    private data class CombinationKeySpec(
        val keyCode: Int,
        val label: String,
        val ctrlFallback: String? = null
    )
}
