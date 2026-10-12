package com.majordaftapps.sshpeaches.app.fuzz.input

import android.view.KeyEvent
import com.majordaftapps.sshpeaches.app.ui.keyboard.InAppKey
import com.majordaftapps.sshpeaches.app.ui.keyboard.InAppKeyboardLayouts
import com.majordaftapps.sshpeaches.app.ui.keyboard.InAppKeyboardPage
import com.majordaftapps.sshpeaches.app.ui.keyboard.InAppShiftState
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.keyboard.displayText
import com.majordaftapps.sshpeaches.app.ui.keyboard.nextShiftState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Built-in keyboard: the shift/caps state machine under random tap sequences, what every key types
 * in every shift state, and the slot-layout parsers (legacy strings, combination tokens, slot lists).
 */
class KeyboardFuzzTest {

    /** Reference model of the documented shift behaviour, driven by a virtual clock. */
    private class ShiftModel {
        var state = InAppShiftState.OFF
        var lastShiftTap = 0L

        fun tapShift(now: Long) {
            state = when (state) {
                InAppShiftState.OFF -> InAppShiftState.ONCE
                InAppShiftState.ONCE -> if (now - lastShiftTap <= 400) InAppShiftState.LOCKED else InAppShiftState.OFF
                InAppShiftState.LOCKED -> InAppShiftState.OFF
            }
            lastShiftTap = now
        }

        fun typed(key: InAppKey.Char): String {
            val out = if (state != InAppShiftState.OFF && key.text.length == 1 && key.text[0].isLetter()) {
                key.text.uppercase()
            } else {
                key.text
            }
            if (state == InAppShiftState.ONCE) state = InAppShiftState.OFF
            return out
        }
    }

    @Test
    fun shiftStateMachine_randomTapSequences_matchModel() {
        val keys = InAppKeyboardPage.entries.flatMap { InAppKeyboardLayouts.rows(it).flatten() }
            .filterIsInstance<InAppKey.Char>()
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            val model = ShiftModel()
            var state = InAppShiftState.OFF
            var lastTap = 0L
            var now = 10_000L
            repeat(20_000) { i ->
                now += if (r.nextInt(4) == 0) r.nextLong(0, 2_000) else r.nextLong(0, 450)
                if (r.nextInt(3) == 0) {
                    state = nextShiftState(state, now - lastTap)
                    lastTap = now
                    model.tapShift(now)
                    assertEquals("seed=$seed i=$i", model.state, state)
                } else {
                    val key = keys[r.nextInt(keys.size)]
                    val shown = displayText(key, state)
                    if (state == InAppShiftState.ONCE) state = InAppShiftState.OFF
                    assertEquals("seed=$seed i=$i key=${key.text}", model.typed(key), shown)
                    assertEquals(model.state, state)
                }
            }
        }
    }

    @Test
    fun everyKeyTypesExactlyOneVisibleCharacterInEveryShiftState() {
        InAppKeyboardPage.entries.forEach { page ->
            val keys = InAppKeyboardLayouts.rows(page).flatten()
            val ids = keys.filter { it !is InAppKey.Gap }.map { it.id }
            assertEquals("$page duplicate key ids (test tags collide): $ids", ids.size, ids.toSet().size)
            keys.filterIsInstance<InAppKey.Char>().forEach { key ->
                InAppShiftState.entries.forEach { shift ->
                    val text = displayText(key, shift)
                    assertEquals("$page '${key.text}' $shift -> ${Fuzz.show(text)}", 1, text.codePointCount(0, text.length))
                    assertTrue("$page '${key.text}' types a control char", text.none { it.isISOControl() })
                }
                key.longPress?.let { assertEquals("$page long-press of ${key.text}", 1, it.length) }
            }
        }
    }

    // ------------------------------------------------------------- KeyboardLayoutDefaults parsers

    @Test
    fun legacyStringToAction_randomStrings_neverThrow_andCtrlLettersAreControlBytes() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(20_000) {
                val s = when (r.nextInt(4)) {
                    0 -> "ctrl-" + Fuzz.text(r, 2)
                    1 -> "c-" + Fuzz.text(r, 2)
                    2 -> "f" + r.nextInt(-3, 40)
                    else -> Fuzz.text(r, 10)
                }
                val action = try {
                    KeyboardLayoutDefaults.legacyStringToAction(s)
                } catch (t: Throwable) {
                    throw AssertionError("legacyStringToAction(${Fuzz.show(s)}) threw $t", t)
                }
                val lower = s.trim().lowercase()
                val m = Regex("(?:ctrl-|c-)([a-z])").matchEntire(lower)
                if (m != null) {
                    val letter = m.groupValues[1][0]
                    assertEquals(Fuzz.show(s), KeyboardActionType.KEY, action.type)
                    assertTrue(Fuzz.show(s), action.ctrl)
                    assertEquals(Fuzz.show(s), KeyEvent.KEYCODE_A + (letter - 'a'), action.keyCode)
                    assertEquals(Fuzz.show(s), (letter.code and 0x1f).toChar().toString(), action.sequence)
                }
                val fn = Regex("f(\\d+)").matchEntire(lower)?.groupValues?.get(1)?.toIntOrNull()
                if (fn != null && fn in 1..12) {
                    assertEquals(Fuzz.show(s), KeyEvent.KEYCODE_F1 + fn - 1, action.keyCode)
                }
            }
        }
    }

    @Test
    fun combinationAction_tokensRoundTripThroughKeyToken() {
        val tokens = ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } +
            (1..12).map { "F$it" } +
            listOf("ESC", "TAB", "ENTER", "BACKSPACE", "SPACE", "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN")
        tokens.forEach { token ->
            listOf(false, true).forEach { ctrl ->
                listOf(false, true).forEach { alt ->
                    listOf(false, true).forEach { shift ->
                        listOf(token, token.lowercase(), " $token ").forEach { spelled ->
                            val action = KeyboardLayoutDefaults.combinationAction(spelled, ctrl, alt, shift)
                            assertNotNull("combinationAction(${Fuzz.show(spelled)})", action)
                            action!!
                            assertEquals(token, KeyboardLayoutDefaults.keyTokenForAction(action))
                            val again = KeyboardLayoutDefaults.combinationAction(
                                KeyboardLayoutDefaults.keyTokenForAction(action), ctrl, alt, shift
                            )
                            assertEquals(action, again)
                            if (ctrl && !alt && token.length == 1 && token[0] in 'A'..'Z') {
                                assertEquals((token[0].code and 0x1f).toChar().toString(), action.sequence)
                            }
                        }
                    }
                }
            }
        }
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(10_000) {
                val t = Fuzz.text(r, 6)
                try {
                    KeyboardLayoutDefaults.combinationAction(t, r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), Fuzz.text(r, 3))
                } catch (e: Throwable) {
                    throw AssertionError("combinationAction(${Fuzz.show(t)}) threw $e", e)
                }
            }
        }
    }

    private fun randomSlot(r: Random): KeyboardSlotAction = when (r.nextInt(7)) {
        0 -> KeyboardLayoutDefaults.textAction(Fuzz.text(r, 8), Fuzz.text(r, 8))
        1 -> KeyboardLayoutDefaults.keyAction(Fuzz.text(r, 4), r.nextInt(-2, 300), Fuzz.text(r, 3), r.nextBoolean(), r.nextBoolean(), r.nextBoolean())
        2 -> KeyboardLayoutDefaults.modifierAction(KeyboardModifier.entries.random(r), Fuzz.text(r, 4))
        3 -> KeyboardLayoutDefaults.sequenceAction(Fuzz.text(r, 4), Fuzz.text(r, 4))
        4 -> KeyboardLayoutDefaults.fnKeyAction(r.nextBoolean())
        5 -> KeyboardLayoutDefaults.legacyStringToAction(Fuzz.text(r, 6))
        else -> KeyboardLayoutDefaults.textAction("", "").copy(iconId = listOf("", "fn", "swipe_nav", "search", "zzz").random(r))
    }

    @Test
    fun slotNormalisation_randomLayouts_haveFixedSizeAndNeverThrow() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(3_000) {
                val slots = List(r.nextInt(0, 40)) { randomSlot(r) }
                val normalized = KeyboardLayoutDefaults.normalizeSlots(slots)
                assertEquals(KeyboardLayoutDefaults.SLOT_COUNT, normalized.size)
                val migrated = KeyboardLayoutDefaults.replaceSwipeNavWithFn(slots)
                assertEquals(slots.size, migrated.size)
                assertTrue(migrated.none { it.iconId == "swipe_nav" } || slots.count { it.iconId == "swipe_nav" } > 1)
                assertEquals(KeyboardLayoutDefaults.SLOT_COUNT, KeyboardLayoutDefaults.builtInCompactLayout(migrated).size)
            }
        }
    }

    /** Compact key labels are cut to 6 UTF-16 units, which can split an emoji into a lone surrogate. */
    @Test
    fun compactLabel_neverSplitsASurrogatePair() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(5_000) {
                val action = randomSlot(r)
                val label = KeyboardLayoutDefaults.compactLabel(action, "fb")
                assertTrue("label ${Fuzz.show(label)} too long", label.length <= KeyboardLayoutDefaults.COMPACT_KEY_LABEL_MAX_CHARS)
                assertTrue("label ${Fuzz.show(label)} contains a newline", '\n' !in label)
                val lone = label.indices.any { i ->
                    val c = label[i]
                    (c.isHighSurrogate() && (i + 1 >= label.length || !label[i + 1].isLowSurrogate())) ||
                        (c.isLowSurrogate() && (i == 0 || !label[i - 1].isHighSurrogate()))
                }
                val source = action.label.ifBlank { action.text }
                val sourceLone = source.indices.any { i ->
                    val c = source[i]
                    (c.isHighSurrogate() && (i + 1 >= source.length || !source[i + 1].isLowSurrogate())) ||
                        (c.isLowSurrogate() && (i == 0 || !source[i - 1].isHighSurrogate()))
                }
                if (!sourceLone) {
                    assertTrue("compactLabel(${Fuzz.show(source)}) = ${Fuzz.show(label)} splits a surrogate pair", !lone)
                }
            }
        }
    }

    @Test
    fun iconPack_lookupsNeverThrow() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(5_000) { KeyboardIconPack.byId(if (r.nextInt(10) == 0) null else Fuzz.text(r, 8)) }
        }
    }
}
