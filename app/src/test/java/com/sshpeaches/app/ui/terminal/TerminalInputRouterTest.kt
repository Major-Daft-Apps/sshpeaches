package com.majordaftapps.sshpeaches.app.ui.terminal

import android.view.KeyEvent
import android.view.KeyCharacterMap
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
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

    @Test
    fun sendVirtualKey_printableKeysUseDeviceIndependentTextFallback() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_A))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_0))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_MINUS))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_SLASH, shiftDown = true))

        assertEquals("a0-?", writes.joinToByteArray().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun sendVirtualKey_printableFallbackAppliesCtrlAltAndShift() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_C, ctrlDown = true))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_B, altDown = true))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_A, shiftDown = true))

        assertArrayEquals(
            byteArrayOf(0x03, 0x1B) + "bA".encodeToByteArray(),
            writes.joinToByteArray()
        )
    }

    @Test
    fun sendVirtualKey_explicitFallbackPrecedesPrintableFallback() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(
            router.sendVirtualKey(
                keyCode = KeyEvent.KEYCODE_A,
                fallbackSequence = "fallback"
            )
        )

        assertEquals("fallback", writes.joinToByteArray().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun sendVirtualKey_virtualNumpadAlwaysEnablesNumLockSemantics() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9).forEach { keyCode ->
            assertTrue(router.sendVirtualKey(keyCode))
        }
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_DOT))

        assertEquals("0123456789.", writes.joinToByteArray().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun sendVirtualKey_virtualNumpadStaysLiteralInApplicationKeypadMode() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val enableApplicationKeypad = "\u001B=".encodeToByteArray()
        emulator.append(enableApplicationKeypad, enableApplicationKeypad.size)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_7, shiftDown = true))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_ADD))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_SUBTRACT))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_DOT))
        assertTrue(router.sendVirtualKey(KeyEvent.KEYCODE_NUMPAD_ENTER))

        assertEquals("7+-.\r", writes.joinToByteArray().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun sendText_shiftTransformsPrintableTextActions() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        router.sendText("a1-/", shiftDown = true)

        assertEquals("A!_?", writes.joinToByteArray().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun sendText_ctrlTransformsAIntoExactControlByte() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        router.sendText("a", ctrlDown = true)

        assertArrayEquals(byteArrayOf(0x01), writes.joinToByteArray())
    }

    @Test
    fun sendText_plainAWritesExactLiteralByte() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        router.sendText("a")

        assertArrayEquals(byteArrayOf(0x61), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_unmodifiedAWithExternalCtrlWritesExactControlByte() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_A), ctrlDown = true))

        assertArrayEquals(byteArrayOf(0x01), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_plainAWritesExactLiteralByte() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_A)))

        assertArrayEquals(byteArrayOf(0x61), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_ignoredRoutesReturnFalseWithoutWriting() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertFalse(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_BACK), ctrlDown = true))
        assertFalse(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_VOLUME_UP), ctrlDown = true))

        assertTrue(writes.isEmpty())
    }

    @Test
    fun onAndroidKeyDown_externalAltAndShiftApplyToPrintableInput() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_A), altDown = true))
        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_A), shiftDown = true))

        assertArrayEquals(byteArrayOf(0x1B) + "aA".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_navigationUsesKeyHandlerWithExternalModifiers() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_DPAD_RIGHT), altDown = true))

        assertArrayEquals("\u001B[D\u001B[1;3C".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_actionMultipleUnknownWritesCharacters() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(actionMultipleUnknown("ab")))

        assertArrayEquals("ab".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_actionMultipleUnknownWithoutCharactersReturnsFalse() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertFalse(router.onAndroidKeyDown(actionMultipleUnknown("")))

        assertTrue(writes.isEmpty())
    }

    @Test
    fun onAndroidKeyDown_actionMultipleKnownRepeatsKeyRouting() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertTrue(router.onAndroidKeyDown(keyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_A, repeatCount = 2)))

        assertArrayEquals("aa".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_ctrlShiftVPastesClipboard() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "git status\n" }
        )

        assertTrue(
            router.onAndroidKeyDown(
                keyDown(
                    keyCode = KeyEvent.KEYCODE_V,
                    metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
                )
            )
        )

        assertArrayEquals("git status\r".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_shiftInsertPastesClipboard() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "pasted" }
        )

        assertTrue(
            router.onAndroidKeyDown(
                keyDown(
                    keyCode = KeyEvent.KEYCODE_INSERT,
                    metaState = KeyEvent.META_SHIFT_ON
                )
            )
        )

        assertArrayEquals("pasted".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun onAndroidKeyDown_keycodePastePastesClipboard() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it },
            onRequestPasteText = { "pasted" }
        )

        assertTrue(router.onAndroidKeyDown(keyDown(KeyEvent.KEYCODE_PASTE)))

        assertArrayEquals("pasted".encodeToByteArray(), writes.joinToByteArray())
    }

    @Test
    fun sendVirtualKey_unknownKeyWithoutFallbackReturnsFalse() {
        val writes = mutableListOf<ByteArray>()
        val emulator = testEmulator(writes)
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { writes += it }
        )

        assertFalse(router.sendVirtualKey(KeyEvent.KEYCODE_UNKNOWN))
        assertTrue(writes.isEmpty())
    }

    private fun keyDown(
        keyCode: Int,
        metaState: Int = 0
    ): KeyEvent = keyEvent(KeyEvent.ACTION_DOWN, keyCode, metaState = metaState)

    private fun keyEvent(
        action: Int,
        keyCode: Int,
        repeatCount: Int = 0,
        metaState: Int = 0
    ): KeyEvent = KeyEvent(
        0L,
        0L,
        action,
        keyCode,
        repeatCount,
        metaState
    )

    private fun actionMultipleUnknown(characters: String): KeyEvent =
        KeyEvent(
            0L,
            characters,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0
        )

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

    private fun List<ByteArray>.joinToByteArray(): ByteArray =
        fold(ByteArray(0)) { combined, payload -> combined + payload }

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
