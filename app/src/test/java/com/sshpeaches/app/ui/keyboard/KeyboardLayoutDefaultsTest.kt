package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutDefaultsTest {

    @Test
    fun defaultLayout_matchesTermuxStyleRows() {
        val labels = KeyboardLayoutDefaults.DEFAULT_SLOTS.map { it.label }
        assertEquals(14, labels.size)

        val expected = listOf(
            "Esc", "/", "-", "Home", "Up", "End", "PgUp",
            "Tab", "Ctrl", "Alt", "Left", "Down", "Right", "PgDn"
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

