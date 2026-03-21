package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
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
}
