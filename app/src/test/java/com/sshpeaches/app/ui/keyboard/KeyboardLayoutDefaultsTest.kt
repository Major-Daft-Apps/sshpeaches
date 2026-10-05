package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutDefaultsTest {

    @Test
    fun defaultLayout_putsFnWhereSwipeNavWasAndAltInSlotTwo() {
        val labels = KeyboardLayoutDefaults.DEFAULT_SLOTS.map { it.label }

        assertEquals(14, labels.size)
        assertEquals(
            listOf(
                "Esc", "Alt", "Home", "Up", "End", "PgUp", "Fn",
                "Tab", "Ctrl", "Left", "Down", "Right", "PgDn", "Keyboard"
            ),
            labels
        )
        assertEquals(KeyboardModifier.ALT, KeyboardLayoutDefaults.DEFAULT_SLOTS[1].modifier)
        assertEquals("fn", KeyboardLayoutDefaults.DEFAULT_SLOTS[6].iconId)
        assertFalse(KeyboardLayoutDefaults.DEFAULT_SLOTS.any { it.iconId == "swipe_nav" })
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
        assertEquals("Fn", compact[6].label)
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
    fun replaceSwipeNavWithFn_turnsThePreviousDefaultIntoTheNewDefault() {
        val previousDefault = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.fnKeyAction()
            this[6] = swipeNavAction()
        }

        assertEquals(KeyboardLayoutDefaults.DEFAULT_SLOTS, KeyboardLayoutDefaults.replaceSwipeNavWithFn(previousDefault))
    }

    @Test
    fun replaceSwipeNavWithFn_turnsTheOriginalAltDefaultIntoTheNewDefault() {
        val originalDefault = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[6] = swipeNavAction()
        }

        assertEquals(KeyboardLayoutDefaults.DEFAULT_SLOTS, KeyboardLayoutDefaults.replaceSwipeNavWithFn(originalDefault))
    }

    @Test
    fun replaceSwipeNavWithFn_keepsOtherCustomKeys() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.fnKeyAction()
            this[2] = KeyboardLayoutDefaults.textAction("custom", "Custom")
            this[6] = swipeNavAction()
        }

        val migrated = KeyboardLayoutDefaults.replaceSwipeNavWithFn(custom)

        assertEquals("Alt", migrated[1].label)
        assertEquals("Custom", migrated[2].label)
        assertEquals("fn", migrated[6].iconId)
        assertEquals(custom.drop(7), migrated.drop(7))
    }

    @Test
    fun replaceSwipeNavWithFn_leavesLayoutsWithoutSwipeNavAlone() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[1] = KeyboardLayoutDefaults.fnKeyAction()
            this[6] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }

        assertEquals(custom, KeyboardLayoutDefaults.replaceSwipeNavWithFn(custom))
    }

    @Test
    fun normalizeSlots_keepsTheBottomRowsOfOldTwentyEightSlotLayouts() {
        val oldBottomRows = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[6] = swipeNavAction()
        }
        val oldTopRows = List(14) { index ->
            KeyboardLayoutDefaults.textAction("legacy-$index", "Legacy")
        }

        assertEquals(oldBottomRows, KeyboardLayoutDefaults.normalizeSlots(oldTopRows + oldBottomRows))
    }

    @Test
    fun fnLayout_isTwoFixedRowsOrderedBackThenF1ThroughF12ThenKeyboard() {
        val fnLayout = KeyboardLayoutDefaults.builtInFnLayout()

        assertEquals(14, fnLayout.size)
        assertEquals(
            listOf(
                "Back", "F1", "F2", "F3", "F4", "F5", "F6",
                "F7", "F8", "F9", "F10", "F11", "F12", "Keyboard"
            ),
            fnLayout.map { it.label }
        )
        assertEquals("fn_back", fnLayout[0].iconId)
        assertEquals("keyboard", fnLayout[13].iconId)
        assertFalse(fnLayout.any { it.type == KeyboardActionType.MODIFIER })
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
        assertEquals("Keyboard", customFn.last().label)
    }

    @Test
    fun fnAction_usesTextLabelAndInternalRoutingId() {
        val fn = KeyboardLayoutDefaults.fnKeyAction()

        assertEquals("Fn", fn.label)
        assertEquals("fn", fn.iconId)
        assertFalse(KeyboardLayoutDefaults.iconAliasPresets.any { it.iconId == "fn" })
    }

    private fun swipeNavAction() =
        KeyboardLayoutDefaults.textAction(label = "Swipe Nav", text = "").copy(iconId = "swipe_nav")
}
