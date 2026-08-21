package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutDefaultsTest {

    @Test
    fun defaultLayout_keepsOnlyFormerBottomRowsAndReplacesAltWithFn() {
        val labels = KeyboardLayoutDefaults.DEFAULT_SLOTS.map { it.label }

        assertEquals(14, labels.size)
        assertEquals(
            listOf(
                "Esc", "Fn", "Home", "Up", "End", "PgUp", "Swipe Nav",
                "Tab", "Ctrl", "Left", "Down", "Right", "PgDn", "Keyboard"
            ),
            labels
        )
        assertEquals("fn", KeyboardLayoutDefaults.DEFAULT_SLOTS[1].iconId)
        assertFalse(KeyboardLayoutDefaults.DEFAULT_SLOTS.any { it.modifier == KeyboardModifier.ALT })
    }

    @Test
    fun comboPreset_containsCtrlAandCtrlB() {
        val labels = KeyboardLayoutDefaults.comboPresets.map { it.label }
        assertTrue(labels.contains("Ctrl-A"))
        assertTrue(labels.contains("Ctrl-B"))
    }

    @Test
    fun builtInCompactLayout_usesTheSameTwoRemappableRows() {
        val compact = KeyboardLayoutDefaults.builtInCompactLayout(KeyboardLayoutDefaults.DEFAULT_SLOTS)

        assertEquals(KeyboardLayoutDefaults.BUILTIN_SLOT_COUNT, compact.size)
        assertEquals(KeyboardLayoutDefaults.DEFAULT_SLOTS, compact)
        assertEquals("Fn", compact[1].label)
        assertTrue(compact.any { it.modifier == KeyboardModifier.CTRL })
    }

    @Test
    fun normalizeSlots_assignsFnIconIdWhenMissing() {
        val legacyFnSlot = KeyboardLayoutDefaults.textAction(text = "", label = "Fn")
        val compact = KeyboardLayoutDefaults.normalizeSlots(listOf(legacyFnSlot))

        assertEquals("Fn", compact[0].label)
        assertEquals("fn", compact[0].iconId)
    }

    @Test
    fun normalizeSlots_assignsFnActiveIconIdWhenMissing() {
        val legacyFnSlot = KeyboardLayoutDefaults.textAction(text = "", label = "Fn*")
        val compact = KeyboardLayoutDefaults.normalizeSlots(listOf(legacyFnSlot))

        assertEquals("Fn*", compact[0].label)
        assertEquals("fn_active", compact[0].iconId)
    }

    @Test
    fun normalizeSlots_migratesOldTwentyEightSlotDefaultToNewDefault() {
        val oldBottomRows = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.ALT, "Alt")
        }
        val oldTopRows = List(14) { index ->
            KeyboardLayoutDefaults.textAction("legacy-$index", "Legacy")
        }

        val compact = KeyboardLayoutDefaults.normalizeSlots(oldTopRows + oldBottomRows)

        assertEquals(KeyboardLayoutDefaults.DEFAULT_SLOTS, compact)
    }

    @Test
    fun normalizeSlots_keepsCustomizedOldBottomRows() {
        val oldBottomRows = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.ALT, "Alt")
            this[2] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }
        val oldTopRows = List(14) { KeyboardLayoutDefaults.emptyAction() }

        val compact = KeyboardLayoutDefaults.normalizeSlots(oldTopRows + oldBottomRows)

        assertEquals(14, compact.size)
        assertEquals("Alt", compact[1].label)
        assertEquals("Custom", compact[2].label)
    }

    @Test
    fun fnLayout_isTwoFixedRowsOrderedBackShiftThenF1ThroughF12() {
        val fnLayout = KeyboardLayoutDefaults.builtInFnLayout()

        assertEquals(14, fnLayout.size)
        assertEquals(
            listOf(
                "Back", "Shift", "F1", "F2", "F3", "F4", "F5",
                "F6", "F7", "F8", "F9", "F10", "F11", "F12"
            ),
            fnLayout.map { it.label }
        )
        assertEquals("fn_back", fnLayout[0].iconId)
        assertEquals(KeyboardActionType.MODIFIER, fnLayout[1].type)
        assertEquals(KeyboardModifier.SHIFT, fnLayout[1].modifier)
    }

    @Test
    fun fnLayout_ignoresCustomizedMainRows() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[13] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }

        val builtInFn = KeyboardLayoutDefaults.builtInFnLayout(custom)
        val customFn = KeyboardLayoutDefaults.customFnLayout(custom)

        assertEquals(builtInFn, customFn)
        assertFalse(customFn.any { it.label == "Custom" })
        assertEquals("F12", customFn.last().label)
    }

    @Test
    fun fnAction_usesTextLabelAndInternalRoutingId() {
        val fn = KeyboardLayoutDefaults.fnKeyAction()

        assertEquals("Fn", fn.label)
        assertEquals("fn", fn.iconId)
        assertFalse(KeyboardLayoutDefaults.iconAliasPresets.any { it.iconId == "fn" })
    }
}