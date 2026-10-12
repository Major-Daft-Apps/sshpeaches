package com.majordaftapps.sshpeaches.app.fuzz.input

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.random.Random

/** Shared helpers for the seeded, deterministic fuzz harnesses in this package. */
internal object Fuzz {
    /** Fixed seeds: every run explores the same inputs, so a failure always reproduces. */
    val SEEDS = longArrayOf(0x5EED_0001L, 0x5EED_0002L, 0x5EED_0003L)

    private val interesting = listOf(
        "a", "Z", "0", "9", " ", "\t", "\n", "\r", "\r\n", "\u0000", "\u0001", "\u001B", "\u007F",
        "\u0085", "\u009B", "\u009F", "é", "é", "İ", "ı", "ß", "ẞ", "ǅ", "ﬀ", "Σ", "ς",
        "😀", "👍🏽", "👨‍👩‍👧", "\uD83D", "\uDE00", "﻿", "​", " ", "中", "한",
        "\\", "'", "\"", "$", "`", "(", ")", "[", "]", "{", "}", "*", "?", ".", "+", "^", "|",
        "$(id)", "`id`", "; rm -rf ~", "&&", "||", "\\n", "%s", "%n", "@", "~", "_", "/", "-"
    )

    fun text(r: Random, maxLen: Int = 24): String {
        val n = r.nextInt(maxLen + 1)
        val sb = StringBuilder()
        repeat(n) {
            when (r.nextInt(5)) {
                0 -> sb.append(interesting[r.nextInt(interesting.size)])
                1 -> sb.append((0x20 + r.nextInt(0x5F)).toChar())
                2 -> sb.append(r.nextInt(0x20).toChar())
                3 -> sb.appendCodePoint(randomCodePoint(r))
                else -> sb.append(('a' + r.nextInt(26)))
            }
        }
        return sb.toString()
    }

    fun randomCodePoint(r: Random): Int = when (r.nextInt(6)) {
        0 -> 0x20 + r.nextInt(0x5F)
        1 -> 0xA0 + r.nextInt(0x700)
        2 -> 0x4E00 + r.nextInt(0x500)
        3 -> 0x1F300 + r.nextInt(0x300)
        4 -> 0x300 + r.nextInt(0x70) // combining marks
        else -> r.nextInt(0x110000).let { if (it in 0xD800..0xDFFF) 0xFFFD else it }
    }

    fun asciiPrintable(r: Random, maxLen: Int): String =
        String(CharArray(r.nextInt(maxLen + 1)) { (0x20 + r.nextInt(0x5F)).toChar() })

    /** Runs [block] and fails if it takes longer than [limitMs] (catches hangs and blow-ups). */
    inline fun <T> timed(label: String, limitMs: Long, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (elapsed > limitMs) throw AssertionError("$label took ${elapsed}ms (limit ${limitMs}ms)")
        return result
    }

    fun show(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            if (c.code < 0x20 || c.code in 0x7F..0x9F || c.isSurrogate() || c.code > 0x7E) {
                append("\\u%04X".format(c.code))
            } else {
                append(c)
            }
        }
        append('"')
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun emulator(columns: Int = 80, rows: Int = 24, transcriptRows: Int = 200): TerminalEmulator =
        TerminalEmulator(
            object : TerminalOutput() {
                override fun write(data: ByteArray, offset: Int, count: Int) = Unit
                override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
                override fun onCopyTextToClipboard(text: String?) = Unit
                override fun onPasteTextFromClipboard() = Unit
                override fun onBell() = Unit
                override fun onColorsChanged() = Unit
            },
            columns,
            rows,
            0,
            0,
            transcriptRows,
            object : TerminalSessionClient {
                override fun onTextChanged(changedSession: TerminalSession) = Unit
                override fun onTitleChanged(changedSession: TerminalSession) = Unit
                override fun onSessionFinished(finishedSession: TerminalSession) = Unit
                override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
                override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
                override fun onBell(session: TerminalSession) = Unit
                override fun onColorsChanged(session: TerminalSession) = Unit
                override fun onTerminalCursorStateChange(state: Boolean) = Unit
            }
        )

    fun TerminalEmulator.feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        append(bytes, bytes.size)
    }
}

/** Reflection access to file-private top-level functions, without touching production code. */
internal class PrivateFns(className: String) {
    val cls: Class<*> = Class.forName(className)

    fun method(name: String, paramCount: Int): Method =
        cls.declaredMethods.first { it.name == name && it.parameterCount == paramCount }
            .apply { isAccessible = true }

    /** Unwraps InvocationTargetException so the real production exception surfaces. */
    fun call(m: Method, vararg args: Any?): Any? = try {
        m.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

internal fun Any.prop(name: String): Any? {
    val getter = javaClass.declaredMethods.first {
        it.parameterCount == 0 && (it.name == "get" + name.replaceFirstChar { c -> c.uppercase() })
    }
    getter.isAccessible = true
    return getter.invoke(this)
}
