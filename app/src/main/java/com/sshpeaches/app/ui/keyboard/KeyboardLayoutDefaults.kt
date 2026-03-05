package com.majordaftapps.sshpeaches.app.ui.keyboard

import android.view.KeyEvent

enum class KeyboardActionType {
    TEXT,
    KEY,
    MODIFIER,
    SEQUENCE
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
    val repeatable: Boolean = false
) {
    fun isEmpty(): Boolean = type == KeyboardActionType.TEXT && text.isBlank() && label.isBlank()
}

object KeyboardLayoutDefaults {
    const val SLOT_COLUMNS = 7
    const val SLOT_ROWS = 1
    const val SLOT_COUNT = SLOT_COLUMNS * SLOT_ROWS
    const val COMPACT_KEY_LABEL_MAX_CHARS = 6
    const val COMPACT_KEY_HEIGHT_DP = 30
    const val COMPACT_KEY_FONT_SP = 10

    val DEFAULT_SLOTS: List<KeyboardSlotAction> = listOf(
        keyAction("Esc", KeyEvent.KEYCODE_ESCAPE),
        keyAction("Tab", KeyEvent.KEYCODE_TAB),
        modifierAction(KeyboardModifier.CTRL, "Ctrl"),
        modifierAction(KeyboardModifier.ALT, "Alt"),
        textAction("-", "-"),
        keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true),
        keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true)
    )

    val modifierPresets: List<KeyboardSlotAction> = listOf(
        modifierAction(KeyboardModifier.CTRL, "Ctrl"),
        modifierAction(KeyboardModifier.ALT, "Alt"),
        modifierAction(KeyboardModifier.SHIFT, "Shift")
    )

    val navigationPresets: List<KeyboardSlotAction> = listOf(
        keyAction("Esc", KeyEvent.KEYCODE_ESCAPE),
        keyAction("Tab", KeyEvent.KEYCODE_TAB),
        keyAction("Enter", KeyEvent.KEYCODE_ENTER),
        keyAction("Bksp", KeyEvent.KEYCODE_DEL, repeatable = true),
        keyAction("Home", KeyEvent.KEYCODE_MOVE_HOME),
        keyAction("End", KeyEvent.KEYCODE_MOVE_END),
        keyAction("PgUp", KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
        keyAction("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true),
        keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true),
        keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true),
        keyAction("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true),
        keyAction("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true)
    )

    val functionPresets: List<KeyboardSlotAction> = listOf(
        functionKeyAction(1),
        functionKeyAction(2),
        functionKeyAction(3),
        functionKeyAction(4),
        functionKeyAction(5),
        functionKeyAction(6),
        functionKeyAction(7),
        functionKeyAction(8),
        functionKeyAction(9),
        functionKeyAction(10),
        functionKeyAction(11),
        functionKeyAction(12)
    )

    val sequencePresets: List<KeyboardSlotAction> = listOf(
        sequenceAction("C-C", "\u0003"),
        sequenceAction("C-D", "\u0004"),
        sequenceAction("C-Z", "\u001A")
    )

    val comboPresets: List<KeyboardSlotAction> = listOfNotNull(
        combinationAction("A", ctrl = true),
        combinationAction("B", ctrl = true),
        combinationAction("C", ctrl = true),
        combinationAction("D", ctrl = true),
        combinationAction("E", ctrl = true),
        combinationAction("F", ctrl = true),
        combinationAction("K", ctrl = true),
        combinationAction("L", ctrl = true),
        combinationAction("R", ctrl = true),
        combinationAction("U", ctrl = true),
        combinationAction("W", ctrl = true),
        combinationAction("Z", ctrl = true)
    )

    fun emptyAction(): KeyboardSlotAction = textAction("")

    fun compactLabel(action: KeyboardSlotAction, fallback: String = ""): String {
        val base = if (action.label.isNotBlank()) action.label else action.text
        val compact = base.trim().replace("\n", " ").take(COMPACT_KEY_LABEL_MAX_CHARS)
        return if (compact.isBlank()) fallback else compact
    }

    fun normalizeSlots(slots: List<KeyboardSlotAction>): List<KeyboardSlotAction> =
        List(SLOT_COUNT) { index ->
            slots.getOrNull(index) ?: emptyAction()
        }

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
            "up" -> keyAction("Up", KeyEvent.KEYCODE_DPAD_UP, repeatable = true)
            "dn", "down" -> keyAction("Down", KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true)
            "lt", "left" -> keyAction("Left", KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true)
            "rt", "right" -> keyAction("Right", KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true)
            "c-c" -> sequenceAction("C-C", "\u0003")
            "c-d" -> sequenceAction("C-D", "\u0004")
            "c-z" -> sequenceAction("C-Z", "\u001A")
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
                val keyCode = KeyEvent.keyCodeFromString("KEYCODE_$single")
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    val controlValue = single.code - 'A'.code + 1
                    return CombinationKeySpec(
                        keyCode = keyCode,
                        label = single.toString(),
                        ctrlFallback = String(charArrayOf(controlValue.toChar()))
                    )
                }
            }
            if (single in '0'..'9') {
                val keyCode = KeyEvent.keyCodeFromString("KEYCODE_$single")
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    return CombinationKeySpec(keyCode = keyCode, label = single.toString())
                }
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

    private data class CombinationKeySpec(
        val keyCode: Int,
        val label: String,
        val ctrlFallback: String? = null
    )
}
