package com.majordaftapps.sshpeaches.app.fuzz.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.majordaftapps.sshpeaches.app.fuzz.input.Fuzz.feed
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalInputRouter
import com.termux.terminal.TerminalEmulator
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Seeded fuzzing of TerminalInputRouter: text with modifier combinations, virtual keys in every
 * cursor/keypad mode, hardware key events with random meta states, and paste sanitising.
 * Expected bytes come from an independent xterm-style oracle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TerminalInputRouterFuzzTest {

    private class Rig(paste: String? = null) {
        val out = ByteArrayOutputStream()
        val emulator: TerminalEmulator = Fuzz.emulator()
        val router = TerminalInputRouter(
            emulatorProvider = { emulator },
            onWriteToRemote = { out.write(it) },
            onRequestPasteText = { paste }
        )

        fun take(): ByteArray = out.toByteArray().also { out.reset() }
    }

    private fun utf8(cp: Int) = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)

    /** US-layout shift as the router documents it: ASCII only. */
    private fun shifted(cp: Int): Int {
        val lower = "`1234567890-=[]\\;',./"
        val upper = "~!@#$%^&*()_+{}|:\"<>?"
        return when {
            cp in 'a'.code..'z'.code -> cp - 32
            cp < 0x80 && lower.indexOf(cp.toChar()) >= 0 -> upper[lower.indexOf(cp.toChar())].code
            else -> cp
        }
    }

    /** xterm/VT220 control mapping: @..~ -> & 0x1f, ?/8 -> DEL, 2/space -> NUL, 3..7 -> ESC..US. */
    private fun xtermCtrl(cp: Int): Int = when (cp) {
        in 'a'.code..'z'.code, in '@'.code..'_'.code -> cp and 0x1f
        ' '.code, '2'.code -> 0
        '3'.code -> 27
        '4'.code -> 28
        '5'.code -> 29
        '6'.code -> 30
        '7'.code, '/'.code -> 31
        '8'.code, '?'.code -> 127
        else -> cp
    }

    private fun expectedText(text: String, ctrl: Boolean, alt: Boolean, shift: Boolean, ctrlMap: (Int) -> Int): ByteArray {
        if (!ctrl && !alt && !shift) return text.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            var cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (shift) cp = shifted(cp)
            if (ctrl) cp = ctrlMap(cp)
            if (alt) bos.write(0x1B)
            bos.write(utf8(cp))
        }
        return bos.toByteArray()
    }

    @Test
    fun sendText_randomTextAndModifiers_matchOracle() {
        // The router now matches xterm, including Ctrl+@ (NUL) and Ctrl+? (DEL).
        val termuxCtrl: (Int) -> Int = ::xtermCtrl
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            val rig = Rig()
            repeat(4_000) { i ->
                val text = Fuzz.text(r, 12)
                val ctrl = r.nextInt(3) == 0
                val alt = r.nextInt(3) == 0
                val shift = r.nextInt(3) == 0
                rig.router.sendText(text, ctrl, alt, shift)
                val got = rig.take()
                val want = expectedText(text, ctrl, alt, shift, termuxCtrl)
                assertArrayEquals(
                    "seed=$seed i=$i text=${Fuzz.show(text)} ctrl=$ctrl alt=$alt shift=$shift got=${Fuzz.hex(got)} want=${Fuzz.hex(want)}",
                    want,
                    got
                )
            }
        }
    }

    @Test
    fun sendText_ctrlLetterOracle_exhaustive() {
        val rig = Rig()
        for (c in ('a'..'z') + ('A'..'Z')) {
            rig.router.sendText(c.toString(), ctrlDown = true)
            assertArrayEquals("Ctrl+$c", byteArrayOf((c.code and 0x1f).toByte()), rig.take())
            rig.router.sendText(c.toString(), ctrlDown = true, altDown = true)
            assertArrayEquals("Ctrl+Alt+$c", byteArrayOf(0x1B, (c.code and 0x1f).toByte()), rig.take())
        }
    }

    /** Ctrl+@ is NUL (^@) in xterm, VT220 and every terminal. */
    @Test
    fun sendText_ctrlAt_isNul() {
        val rig = Rig()
        rig.router.sendText("@", ctrlDown = true)
        assertArrayEquals("Ctrl+@ got ${Fuzz.hex(rig.out.toByteArray())}", byteArrayOf(0), rig.take())
        // Same key reached as latched Ctrl+Shift on the '2' key.
        rig.router.sendText("2", ctrlDown = true, shiftDown = true)
        assertArrayEquals("Ctrl+Shift+2 got ${Fuzz.hex(rig.out.toByteArray())}", byteArrayOf(0), rig.take())
    }

    /** Ctrl+? is DEL (^?) in xterm. */
    @Test
    fun sendText_ctrlQuestionMark_isDel() {
        val rig = Rig()
        rig.router.sendText("?", ctrlDown = true)
        assertArrayEquals("Ctrl+? got ${Fuzz.hex(rig.out.toByteArray())}", byteArrayOf(0x7F), rig.take())
    }

    private fun xtermMod(ctrl: Boolean, alt: Boolean, shift: Boolean) =
        1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)

    @Test
    fun sendVirtualKey_randomKeysModesAndModifiers_neverThrow_andArrowsMatchOracle() {
        val arrows = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to 'A', KeyEvent.KEYCODE_DPAD_DOWN to 'B',
            KeyEvent.KEYCODE_DPAD_RIGHT to 'C', KeyEvent.KEYCODE_DPAD_LEFT to 'D',
            KeyEvent.KEYCODE_MOVE_HOME to 'H', KeyEvent.KEYCODE_MOVE_END to 'F'
        )
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            val rig = Rig()
            var cursorApp = false
            var keypadApp = false
            repeat(6_000) { i ->
                when (r.nextInt(10)) {
                    0 -> { cursorApp = !cursorApp; rig.emulator.feed(if (cursorApp) "\u001B[?1h" else "\u001B[?1l") }
                    1 -> { keypadApp = !keypadApp; rig.emulator.feed(if (keypadApp) "\u001B=" else "\u001B>") }
                }
                assertEquals(cursorApp, rig.emulator.isCursorKeysApplicationMode)
                val keyCode = if (r.nextInt(4) == 0) arrows.keys.random(r) else r.nextInt(-5, 320)
                val ctrl = r.nextInt(3) == 0
                val alt = r.nextInt(3) == 0
                val shift = r.nextInt(3) == 0
                val fallback = if (r.nextInt(5) == 0) Fuzz.text(r, 4) else null
                val label = "seed=$seed i=$i key=$keyCode ctrl=$ctrl alt=$alt shift=$shift cApp=$cursorApp kApp=$keypadApp fb=${fallback?.let(Fuzz::show)}"
                val sent = try {
                    rig.router.sendVirtualKey(keyCode, ctrl, alt, shift, fallback)
                } catch (t: Throwable) {
                    throw AssertionError("$label threw $t", t)
                }
                val got = rig.take()
                assertEquals("$label returned $sent but wrote ${got.size} bytes", sent, got.isNotEmpty())
                val final = arrows[keyCode]
                if (final != null) {
                    val mod = xtermMod(ctrl, alt, shift)
                    val want = if (mod == 1) {
                        if (cursorApp) "\u001BO$final" else "\u001B[$final"
                    } else {
                        "\u001B[1;$mod$final"
                    }
                    assertEquals(label, want, String(got, Charsets.UTF_8))
                }
                if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && fallback.isNullOrEmpty()) {
                    var c = 'a'.code + keyCode - KeyEvent.KEYCODE_A
                    if (shift) c -= 32
                    if (ctrl) c = c and 0x1f
                    val want = (if (alt) byteArrayOf(0x1B) else ByteArray(0)) + byteArrayOf(c.toByte())
                    assertArrayEquals(label, want, got)
                }
            }
        }
    }

    @Test
    fun onAndroidKeyDown_randomEventsAndMetaStates_neverThrow_andLettersMatchOracle() {
        val metaBits = intArrayOf(
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON,
            KeyEvent.META_META_ON, KeyEvent.META_FUNCTION_ON, KeyEvent.META_CAPS_LOCK_ON,
            KeyEvent.META_NUM_LOCK_ON, KeyEvent.META_SYM_ON
        )
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            val rig = Rig(paste = "p\u001B[201~x\n")
            repeat(6_000) { i ->
                var meta = 0
                metaBits.forEach { if (r.nextInt(5) == 0) meta = meta or it }
                val keyCode = r.nextInt(0, 300)
                val action = if (r.nextInt(8) == 0) KeyEvent.ACTION_MULTIPLE else KeyEvent.ACTION_DOWN
                val repeat = if (action == KeyEvent.ACTION_MULTIPLE) r.nextInt(0, 4) else 0
                val event = KeyEvent(0L, 0L, action, keyCode, repeat, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
                val ctrl = r.nextInt(4) == 0
                val alt = r.nextInt(4) == 0
                val shift = r.nextInt(4) == 0
                val label = "seed=$seed i=$i key=$keyCode action=$action repeat=$repeat meta=0x${meta.toString(16)} ext(c=$ctrl a=$alt s=$shift)"
                val handled = try {
                    rig.router.onAndroidKeyDown(event, ctrl, alt, shift)
                } catch (t: Throwable) {
                    throw AssertionError("$label threw $t", t)
                }
                val got = rig.take()
                if (!handled) assertEquals("$label unhandled but wrote ${Fuzz.hex(got)}", 0, got.size)
                val ctrlAny = ctrl || (meta and KeyEvent.META_CTRL_ON) != 0
                val altAny = alt || (meta and KeyEvent.META_ALT_ON) != 0
                val shiftAny = shift || (meta and KeyEvent.META_SHIFT_ON) != 0
                val isPaste = keyCode == KeyEvent.KEYCODE_V && ctrlAny && shiftAny && !altAny
                if (action == KeyEvent.ACTION_DOWN && keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z &&
                    ctrlAny && !isPaste &&
                    (meta and (KeyEvent.META_ALT_RIGHT_ON or KeyEvent.META_FUNCTION_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON)) == 0
                ) {
                    val letter = 'a'.code + keyCode - KeyEvent.KEYCODE_A
                    val want = (if (altAny) byteArrayOf(0x1B) else ByteArray(0)) + byteArrayOf((letter and 0x1f).toByte())
                    assertArrayEquals("$label got=${Fuzz.hex(got)}", want, got)
                }
                if (isPaste && (meta and (KeyEvent.META_ALT_RIGHT_ON)) == 0 && action == KeyEvent.ACTION_DOWN) {
                    assertEquals("$label paste", "p[201~x\r", String(got, Charsets.UTF_8))
                }
            }
        }
    }

    @Test
    fun pasteText_randomText_isSanitised() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            val rig = Rig()
            var bracketed = false
            repeat(3_000) { i ->
                if (r.nextInt(6) == 0) {
                    bracketed = !bracketed
                    rig.emulator.feed(if (bracketed) "\u001B[?2004h" else "\u001B[?2004l")
                }
                val text = Fuzz.text(r, 30) + if (r.nextBoolean()) "\u001B[201~rm -rf /\n" else ""
                val ok = rig.router.pasteText(text, rig.emulator)
                val got = String(rig.take(), Charsets.UTF_8)
                val label = "seed=$seed i=$i bracketed=$bracketed text=${Fuzz.show(text)} got=${Fuzz.show(got)}"
                val body = if (bracketed && ok) {
                    assertTrue(label, got.startsWith("\u001B[200~") && got.endsWith("\u001B[201~"))
                    got.removePrefix("\u001B[200~").removeSuffix("\u001B[201~")
                } else {
                    got
                }
                assertTrue("$label: ESC/C1/LF leaked", body.none { it == '\u001B' || it == '\n' || it.code in 0x80..0x9F })
                val expectedBody = text.filter { it != '\u001B' && it.code !in 0x80..0x9F }.replace("\r\n", "\r").replace('\n', '\r')
                    .let { String(it.toByteArray(Charsets.UTF_8), Charsets.UTF_8) } // lone surrogates -> '?'
                assertEquals(label, expectedBody.isNotEmpty(), ok)
                if (ok) assertEquals(label, expectedBody, body)
            }
        }
    }

    /**
     * Dead keys on hardware layouts (e.g. ´ or ^ on German/French/US-International) come back from
     * getUnicodeChar with the COMBINING_ACCENT bit set. TerminalView composes them with the next key,
     * but the router claims the event (returns true) and writes nothing, so the accent is lost.
     */
    @Test
    fun onAndroidKeyDown_deadKey_isNotSwallowed() {
        val rig = Rig()
        val acute = KeyCharacterMap.COMBINING_ACCENT or 0x0301
        val deadKey = object : KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_APOSTROPHE, 0, 0) {
            override fun getUnicodeChar(metaState: Int): Int = acute
        }
        val handled = rig.router.onAndroidKeyDown(deadKey)
        val wrote = rig.take()
        // Either the router composes the accent itself, or it must leave the event to TerminalView.
        assertTrue(
            "dead key: handled=$handled wrote=${Fuzz.hex(wrote)} — accent dropped while the event was consumed",
            !handled || wrote.isNotEmpty()
        )
    }

    /**
     * AltGr (right Alt) composes characters on most non-US layouts: German AltGr+Q = '@', AltGr+8 = '[',
     * AltGr+7 = '{', AltGr++ = '~'. The router keeps AltGr for getUnicodeChar (correct) but then also
     * treats it as Meta and prefixes ESC, so the shell receives ESC '@' / ESC '[' instead of '@' / '['.
     * Termux's TerminalView only Meta-prefixes for *left* Alt.
     */
    @Test
    fun onAndroidKeyDown_altGrComposedCharacter_isNotEscPrefixed() {
        val rig = Rig()
        listOf(
            KeyEvent.KEYCODE_Q to '@', KeyEvent.KEYCODE_8 to '[', KeyEvent.KEYCODE_7 to '{',
            KeyEvent.KEYCODE_PLUS to '~', KeyEvent.KEYCODE_MINUS to '\\'
        ).forEach { (keyCode, composed) ->
            val altGr = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
            val event = object : KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, altGr) {
                override fun getUnicodeChar(metaState: Int): Int =
                    if ((metaState and KeyEvent.META_ALT_RIGHT_ON) != 0) composed.code else 'x'.code
            }
            assertTrue(rig.router.onAndroidKeyDown(event))
            val got = rig.take()
            assertArrayEquals("AltGr -> '$composed' got ${Fuzz.hex(got)}", byteArrayOf(composed.code.toByte()), got)
        }
    }
}
