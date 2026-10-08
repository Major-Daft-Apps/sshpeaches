package com.majordaftapps.sshpeaches.app.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppKeyboardTest {

    @Test
    fun shiftTapsCycleThroughOnceAndCapsLock() {
        assertEquals(InAppShiftState.ONCE, nextShiftState(InAppShiftState.OFF, 5_000))
        assertEquals(InAppShiftState.LOCKED, nextShiftState(InAppShiftState.ONCE, 200))
        assertEquals(InAppShiftState.OFF, nextShiftState(InAppShiftState.ONCE, 1_000))
        assertEquals(InAppShiftState.OFF, nextShiftState(InAppShiftState.LOCKED, 100))
    }

    @Test
    fun shiftOnlyChangesLetters() {
        assertEquals("Q", displayText(InAppKey.Char("q"), InAppShiftState.ONCE))
        assertEquals("q", displayText(InAppKey.Char("q"), InAppShiftState.OFF))
        assertEquals("/", displayText(InAppKey.Char("/"), InAppShiftState.LOCKED))
    }

    @Test
    fun everyPageHasTheKeysATerminalNeeds() {
        InAppKeyboardPage.entries.forEach { page ->
            val keys = InAppKeyboardLayouts.rows(page).flatten()
            val actions = keys.filterIsInstance<InAppKey.Action>().map { it.action }.toSet()
            assertTrue("$page needs Enter, Space and Backspace", actions.containsAll(
                setOf(InAppKeyAction.ENTER, InAppKeyAction.SPACE, InAppKeyAction.BACKSPACE)
            ))
            val ids = keys.filterNot { it is InAppKey.Gap }.map { it.id }
            assertEquals("$page has duplicate key ids", ids.size, ids.toSet().size)
        }
        val typed = InAppKeyboardPage.entries
            .flatMap { InAppKeyboardLayouts.rows(it).flatten() }
            .filterIsInstance<InAppKey.Char>()
            .map { it.text }
            .toSet()
        "abcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?".forEach { c ->
            assertTrue("missing $c", c.toString() in typed)
        }
    }
}
