package com.majordaftapps.sshpeaches.app.fuzz.input

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * Drives the terminal's IME bridge (the file-private TerminalImeBridgeEditText in ConnectingScreen.kt,
 * reached by reflection) with random InputConnection call sequences, as Gboard/SwiftKey/Samsung would,
 * and checks the bytes that reach the shell against a model of the document the IME is editing:
 * server line + local composing text must always equal what the IME believes the text is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TerminalImeBridgeFuzzTest {

    private val normalizeImeChunk = PrivateFns("com.majordaftapps.sshpeaches.app.ui.screens.ConnectingScreenKt")
        .let { fns -> fns.method("normalizeImeChunk", 1).let { m -> { s: String -> fns.call(m, s) as String } } }

    /** The shell's line editor: backspace removes one code point, CR submits the line. */
    private class Shell {
        val line = StringBuilder()
        val submitted = mutableListOf<String>()

        fun type(text: String) {
            text.forEach { c -> if (c == '\r') { submitted += line.toString(); line.setLength(0) } else line.append(c) }
        }

        fun backspace() {
            if (line.isEmpty()) return
            val cp = line.codePointBefore(line.length)
            line.setLength(line.length - Character.charCount(cp))
        }
    }

    private inner class Bridge {
        val shell = Shell()
        val view: View
        val ic: InputConnection

        init {
            val cls = Class.forName("com.majordaftapps.sshpeaches.app.ui.screens.TerminalImeBridgeEditText")
            val context: Context = RuntimeEnvironment.getApplication()
            view = cls.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
                .newInstance(context) as View
            fun set(name: String, value: Any) {
                cls.declaredMethods.first { it.name == name }.apply { isAccessible = true }.invoke(view, value)
            }
            set("setOnCommittedText", { s: String -> shell.type(normalizeImeChunk(s)) })
            set("setOnImeBackspace", { shell.backspace() })
            set("setOnImeForwardDelete", { Unit })
            set("setOnImeEnter", { shell.type("\r") })
            set("setOnImeKeyEvent", { e: KeyEvent ->
                when {
                    e.action != KeyEvent.ACTION_DOWN -> false
                    e.keyCode == KeyEvent.KEYCODE_DEL -> { shell.backspace(); true }
                    e.keyCode == KeyEvent.KEYCODE_ENTER -> { shell.type("\r"); true }
                    else -> false
                }
            })
            ic = view.onCreateInputConnection(EditorInfo())!!
        }

        fun shadow(): String = ic.getExtractedText(null, 0)?.text?.toString().orEmpty()
    }

    /** What the IME thinks the document is: everything typed so far on the current line. */
    private class Model {
        val doc = StringBuilder()
        var composingLen = 0
        val submitted = mutableListOf<String>()

        fun settle() {
            val normalized = doc.toString().replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "")
            val parts = normalized.split('\n')
            submitted += parts.dropLast(1)
            doc.setLength(0)
            doc.append(parts.last())
            composingLen = 0
        }

        fun replaceComposing(t: String) {
            doc.setLength(doc.length - composingLen)
            doc.append(t)
        }

        fun deleteBeforeComposingUnits(n: Int) {
            val end = doc.length - composingLen
            val start = (end - n).coerceAtLeast(0)
            doc.delete(start, end)
        }

        fun deleteBeforeComposingCodePoints(n: Int) {
            val end = doc.length - composingLen
            var start = end
            repeat(n) { if (start > 0) start -= Character.charCount(doc.codePointBefore(start)) }
            doc.delete(start, end)
        }

        fun deleteLastCodePoint() {
            if (doc.isNotEmpty()) doc.setLength(doc.length - Character.charCount(doc.codePointBefore(doc.length)))
        }
    }

    private fun word(r: Random, bmpOnly: Boolean): String {
        val n = r.nextInt(0, 6)
        return buildString {
            repeat(n) {
                when (r.nextInt(12)) {
                    0 -> append(if (bmpOnly) "é" else "😀")
                    1 -> append(' ')
                    2 -> if (r.nextInt(4) == 0) append('\n')
                    3 -> append("中")
                    else -> append('a' + r.nextInt(26))
                }
            }
        }
    }

    private fun run(seed: Long, bmpOnly: Boolean, steps: Int) {
        val r = Random(seed)
        val b = Bridge()
        val m = Model()
        val log = mutableListOf<String>()
        repeat(steps) { step ->
            val op: String
            when (r.nextInt(9)) {
                0, 1 -> {
                    val t = word(r, bmpOnly); op = "setComposingText(${Fuzz.show(t)})"
                    b.ic.setComposingText(t, 1); m.replaceComposing(t); m.composingLen = t.length
                }
                2, 3 -> {
                    val t = word(r, bmpOnly); op = "commitText(${Fuzz.show(t)})"
                    b.ic.commitText(t, 1); m.replaceComposing(t); m.settle()
                }
                4 -> { op = "finishComposingText()"; b.ic.finishComposingText(); m.settle() }
                5 -> {
                    val n = r.nextInt(0, 4); op = "deleteSurroundingTextInCodePoints($n, 0)"
                    b.ic.deleteSurroundingTextInCodePoints(n, 0); m.deleteBeforeComposingCodePoints(n)
                }
                6 -> {
                    if (bmpOnly) {
                        val n = r.nextInt(0, 4); op = "deleteSurroundingText($n, 0)"
                        b.ic.deleteSurroundingText(n, 0); m.deleteBeforeComposingUnits(n)
                    } else {
                        op = "noop"
                    }
                }
                7 -> {
                    op = "DEL key"
                    b.ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    b.ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    m.settle(); m.deleteLastCodePoint()
                }
                else -> {
                    if (r.nextBoolean()) {
                        op = "ENTER key"
                        b.ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        b.ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    } else {
                        op = "performEditorAction"
                        b.ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                    }
                    m.settle(); m.doc.append('\n'); m.settle()
                }
            }
            log += op
            val actual = b.shell.line.toString() + b.shadow()
            val trace = log.takeLast(12).joinToString("\n  ")
            assertEquals("seed=$seed step=$step document after:\n  $trace", m.doc.toString(), actual)
            assertEquals("seed=$seed step=$step submitted lines after:\n  $trace", m.submitted, b.shell.submitted)
        }
    }

    @Test
    fun randomImeSessions_bmpText_keepShellInSyncWithIme() {
        Fuzz.SEEDS.forEach { run(it, bmpOnly = true, steps = 1_500) }
    }

    @Test
    fun randomImeSessions_withEmoji_codePointDeletes_keepShellInSyncWithIme() {
        Fuzz.SEEDS.forEach { run(it xor 0x77L, bmpOnly = false, steps = 1_500) }
    }

    /**
     * deleteSurroundingText counts UTF-16 units. When the IME deletes an emoji that was already sent
     * (nothing left locally), the bridge sends one backspace per unit, i.e. two for one emoji, and
     * the shell deletes the character before the emoji too.
     */
    @Test
    fun deleteSurroundingText_overSentEmoji_sendsOneBackspace() {
        val b = Bridge()
        b.ic.commitText("ab😀", 1)
        b.ic.deleteSurroundingText(2, 0)
        assertEquals("ab", b.shell.line.toString())
    }
}
