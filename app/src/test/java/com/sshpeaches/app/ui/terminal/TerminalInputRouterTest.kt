package com.majordaftapps.sshpeaches.app.ui.terminal

import android.view.KeyEvent
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputRouterTest {

    @Test
    fun isPasteShortcut_acceptsCtrlShiftV() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "git status\n" }
        )

        assertTrue(
            router.isPasteShortcut(
                keyCode = KeyEvent.KEYCODE_V,
                metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
            )
        )
    }

    @Test
    fun isPasteShortcut_rejectsPlainCtrlV() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "ignored" }
        )

        assertFalse(
            router.isPasteShortcut(
                keyCode = KeyEvent.KEYCODE_V,
                metaState = KeyEvent.META_CTRL_ON
            )
        )
    }

    @Test
    fun pasteFromClipboard_returnsFalseWhenClipboardIsEmpty() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "" }
        )

        assertFalse(router.pasteFromClipboard())
        assertTrue(writes.isEmpty())
    }

    @Test
    fun pasteFromClipboard_writesBracketedPasteFriendlyTerminalSequence() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "git status\n" }
        )

        assertTrue(router.pasteFromClipboard())
        assertEquals(1, writes.size)
        assertArrayEquals("git status\r".encodeToByteArray(), writes.single())
    }

    private fun testEmulator(writes: MutableList<ByteArray>): TerminalEmulator =
        TerminalEmulator(
            object : TerminalOutput() {
                override fun write(data: ByteArray, offset: Int, count: Int) {
                    writes += data.copyOfRange(offset, offset + count)
                }

                override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

                override fun onCopyTextToClipboard(text: String?) = Unit

                override fun onPasteTextFromClipboard() = Unit

                override fun onBell() = Unit

                override fun onColorsChanged() = Unit
            },
            80,
            24,
            0,
            0,
            100,
            object : TerminalSessionClient {
                override fun onTextChanged(changedSession: com.termux.terminal.TerminalSession) = Unit
                override fun onTitleChanged(changedSession: com.termux.terminal.TerminalSession) = Unit
                override fun onSessionFinished(finishedSession: com.termux.terminal.TerminalSession) = Unit
                override fun onCopyTextToClipboard(session: com.termux.terminal.TerminalSession, text: String?) = Unit
                override fun onPasteTextFromClipboard(session: com.termux.terminal.TerminalSession?) = Unit
                override fun onBell(session: com.termux.terminal.TerminalSession) = Unit
                override fun onColorsChanged(session: com.termux.terminal.TerminalSession) = Unit
                override fun onTerminalCursorStateChange(state: Boolean) = Unit
            }
        )
}
