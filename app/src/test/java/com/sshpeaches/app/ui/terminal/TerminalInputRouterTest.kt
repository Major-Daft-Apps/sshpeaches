package com.majordaftapps.sshpeaches.app.ui.terminal

import android.view.KeyEvent
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    @Test
    fun pasteFromClipboard_wrapsPasteWhenBracketedPasteModeIsEnabled() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val enableBracketedPaste = "\u001B[?2004h".encodeToByteArray()
        emulator.append(enableBracketedPaste, enableBracketedPaste.size)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "git status\n" }
        )

        assertTrue(router.pasteFromClipboard())
        assertEquals(1, writes.size)
        assertArrayEquals("\u001B[200~git status\r\u001B[201~".encodeToByteArray(), writes.single())
    }

    @Test
    fun pasteFromClipboard_fakeEmulatorAndStandardSpeedMockSshServerReturnsBeforeDrain() {
        StandardSpeedMockSshServer(bytesPerSecond = 256).use { server ->
            val writes = mutableListOf<ByteArray>()
            val emulator = testEmulator(writes)
            val router = TerminalInputRouter(
                emulatorProvider = { emulator },
                onWriteToRemote = { server.enqueue(it) },
                onRequestPasteText = { "abc" }
            )

            val start = System.nanoTime()
            assertTrue(router.pasteFromClipboard())
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

            assertTrue("Paste path waited for mock SSH drain: ${elapsedMillis}ms", elapsedMillis < 100)
            assertEquals("abc", String(server.awaitBytes(3), StandardCharsets.UTF_8))
        }
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

    private class StandardSpeedMockSshServer(
        private val bytesPerSecond: Int
    ) : AutoCloseable {
        private val input = LinkedBlockingQueue<ByteArray>()
        private val received = LinkedBlockingQueue<Byte>()
        private val running = AtomicBoolean(true)
        private val worker = Thread {
            try {
                while (running.get()) {
                    val payload = input.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    for (byte in payload) {
                        if (!running.get()) break
                        Thread.sleep((1_000L / bytesPerSecond).coerceAtLeast(1L))
                        received.offer(byte)
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            name = "standard-speed-mock-ssh"
            isDaemon = true
            start()
        }

        fun enqueue(payload: ByteArray) {
            input.offer(payload.copyOf())
        }

        fun awaitBytes(count: Int): ByteArray {
            val out = ByteArray(count)
            for (index in 0 until count) {
                out[index] = received.poll(2, TimeUnit.SECONDS)
                    ?: error("Timed out waiting for mock SSH byte $index")
            }
            return out
        }

        override fun close() {
            running.set(false)
            worker.interrupt()
            worker.join(500)
        }
    }
}
