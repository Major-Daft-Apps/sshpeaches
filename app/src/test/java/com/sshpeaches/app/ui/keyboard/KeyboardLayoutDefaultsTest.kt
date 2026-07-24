package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutDefaultsTest {

    @Test
    fun defaultLayout_matchesCurrentDefaultRows() {
        val labels = KeyboardLayoutDefaults.DEFAULT_SLOTS.map { it.label }
        assertEquals(28, labels.size)

        val expected = listOf(
            "F1", "F2", "F3", "F4", "F5", "F6", "F7",
            "F8", "F9", "F10", "F11", "F12", "Snippets", "Password",
            "Esc", "Alt", "Home", "Up", "End", "PgUp", "Swipe Nav",
            "Tab", "Ctrl", "Left", "Down", "Right", "PgDn", "Keyboard"
        )
        assertEquals(expected, labels)
    }

    @Test
    fun comboPreset_containsCtrlAandCtrlB() {
        val labels = KeyboardLayoutDefaults.comboPresets.map { it.label }
        assertTrue(labels.contains("Ctrl-A"))
        assertTrue(labels.contains("Ctrl-B"))
    }

    @Test
    fun builtInCompactLayout_replacesFunctionKeysAndKeepsAllModifiersReachable() {
        val compact = KeyboardLayoutDefaults.builtInCompactLayout(KeyboardLayoutDefaults.DEFAULT_SLOTS)
        assertEquals(KeyboardLayoutDefaults.BUILTIN_SLOT_COUNT, compact.size)

        assertEquals("Num0", compact[0].label)
        assertEquals("Num1", compact[1].label)
        assertEquals("Num2", compact[2].label)
        assertEquals("Num9", compact[9].label)
        assertEquals("Num-", compact[11].label)

        val fnAction = compact[13]
        assertEquals("Fn", fnAction.label)
        assertEquals("fn", fnAction.iconId)
        assertFalse(fnAction.isEmpty())

        assertTrue(compact.any { it.modifier == KeyboardModifier.CTRL })
        assertTrue(compact.any { it.modifier == KeyboardModifier.ALT })
        assertTrue(compact.any { it.modifier == KeyboardModifier.SHIFT })
    }

    @Test
    fun builtInCompactLayout_reservesFnAndShiftSlots() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[12] = KeyboardLayoutDefaults.modifierAction(KeyboardModifier.SHIFT, "Shift")
        }
        val compact = KeyboardLayoutDefaults.builtInCompactLayout(custom)
        assertEquals("Fn", compact[13].label)
        assertEquals("fn", compact[13].iconId)
        assertEquals(KeyboardModifier.SHIFT, compact[27].modifier)
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
    fun builtInFnLayout_containsFunctionRowAndShift() {
        val fnLayout = KeyboardLayoutDefaults.builtInFnLayout()
        assertEquals(KeyboardLayoutDefaults.BUILTIN_SLOT_COUNT, fnLayout.size)

        assertEquals("Fn*", fnLayout[0].label)
        assertEquals("fn_active", fnLayout[0].iconId)
        assertEquals("F1", fnLayout[1].label)
        assertEquals("F12", fnLayout[12].label)
        assertEquals("Shift", fnLayout[13].label)
        assertEquals(KeyboardActionType.MODIFIER, fnLayout[13].type)
        assertEquals(KeyboardModifier.SHIFT, fnLayout[13].modifier)
        assertTrue(fnLayout.drop(14).any { it.modifier == KeyboardModifier.CTRL })
        assertTrue(fnLayout.drop(14).any { it.modifier == KeyboardModifier.ALT })
    }

    @Test
    fun customFnLayout_replacesOnlyFirstLayerAndPreservesCustomControls() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[20] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }

        val fnLayout = KeyboardLayoutDefaults.customFnLayout(custom)

        assertEquals(KeyboardLayoutDefaults.SLOT_COUNT, fnLayout.size)
        assertEquals("Fn*", fnLayout[0].label)
        assertEquals("F1", fnLayout[1].label)
        assertEquals("F12", fnLayout[12].label)
        assertEquals("Custom", fnLayout[20].label)
    }

    @Test
    fun builtInFnLayout_preservesConfiguredSecondLayerControls() {
        val custom = KeyboardLayoutDefaults.DEFAULT_SLOTS.toMutableList().apply {
            this[20] = KeyboardLayoutDefaults.textAction("custom", "Custom")
        }

        val fnLayout = KeyboardLayoutDefaults.builtInFnLayout(custom)

        assertEquals("F1", fnLayout[1].label)
        assertEquals("Custom", fnLayout[20].label)
    }
}
