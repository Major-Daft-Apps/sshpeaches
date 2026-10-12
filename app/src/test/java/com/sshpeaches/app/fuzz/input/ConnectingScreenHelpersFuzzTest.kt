package com.majordaftapps.sshpeaches.app.fuzz.input

import android.view.KeyEvent
import com.majordaftapps.sshpeaches.app.fuzz.input.Fuzz.feed
import com.termux.terminal.TerminalEmulator
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fuzzes the file-private pure helpers of ConnectingScreen.kt through reflection:
 * normalizeImeChunk, computeTerminalFindMatches (with and without a real emulator) and
 * resolveSwipeKeyCode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ConnectingScreenHelpersFuzzTest {

    private val fns = PrivateFns("com.majordaftapps.sshpeaches.app.ui.screens.ConnectingScreenKt")
    private val normalizeImeChunk = fns.method("normalizeImeChunk", 1)
    private val findMatches = fns.method("computeTerminalFindMatches", 5)
    private val swipe = fns.method("resolveSwipeKeyCode", 6)
    private val swipeStartCtor = Class.forName(
        "com.majordaftapps.sshpeaches.app.ui.screens.SwipeGestureStart"
    ).declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }

    private fun ime(s: String) = fns.call(normalizeImeChunk, s) as String

    @Suppress("UNCHECKED_CAST")
    private fun find(
        text: String,
        query: String,
        caseSensitive: Boolean,
        emulator: TerminalEmulator? = null,
        join: Boolean = false
    ): List<Any> = fns.call(findMatches, text, query, caseSensitive, emulator, join) as List<Any>

    // ---------------------------------------------------------------- normalizeImeChunk

    @Test
    fun normalizeImeChunk_matchesOracle() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(3_000) {
                val input = Fuzz.text(r, 30)
                val out = ime(input)
                val expected = buildString {
                    val noNulAfterCr = input.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "")
                    noNulAfterCr.forEach { append(if (it == '\n') '\r' else it) }
                }
                assertEquals("seed=$seed in=${Fuzz.show(input)}", expected, out)
                assertTrue(out.none { it == '\n' || it == '\u0000' })
            }
        }
    }

    // ---------------------------------------------------------------- computeTerminalFindMatches

    /** Every reported match must point inside the searched text at a real occurrence of the query. */
    private fun checkPlainMatches(text: String, query: String, caseSensitive: Boolean, label: String) {
        val matches = Fuzz.timed(label, 2_000) { find(text, query, caseSensitive) }
        val needle = query.trim()
        if (needle.isBlank() || text.isBlank()) {
            assertTrue("$label expected no matches", matches.isEmpty())
            return
        }
        matches.forEach { m ->
            val index = m.prop("index") as Int
            val line = m.prop("line") as Int
            assertTrue("$label index $index out of bounds (len ${text.length})", index in 0..text.length - needle.length)
            val found = text.substring(index, index + needle.length)
            if (caseSensitive) {
                assertEquals("$label wrong match text at $index", needle, found)
            } else {
                // Find compares char by char ignoring case (Kotlin's ignoreCase), so dotless ı may match i.
                assertTrue("$label wrong match text at $index: $found", found.equals(needle, ignoreCase = true))
            }
            assertEquals("$label line", text.substring(0, index).count { it == '\n' } + 1, line)
        }
        if (caseSensitive) {
            // Non-overlapping left-to-right scan oracle.
            var count = 0
            var at = 0
            while (count < 200) {
                val hit = text.indexOf(needle, at)
                if (hit < 0) break
                count++
                at = hit + needle.length
            }
            assertEquals("$label match count", count, matches.size)
        }
    }

    @Test
    fun findMatches_plainText_caseSensitive_randomInputs() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(1_500) { i ->
                val text = Fuzz.text(r, 60)
                val query = if (r.nextBoolean() && text.length > 2) {
                    val a = r.nextInt(text.length - 1)
                    text.substring(a, (a + 1 + r.nextInt(4)).coerceAtMost(text.length))
                } else {
                    Fuzz.text(r, 3)
                }
                checkPlainMatches(text, query, true, "seed=$seed i=$i text=${Fuzz.show(text)} q=${Fuzz.show(query)}")
            }
        }
    }

    @Test
    fun findMatches_plainText_caseInsensitive_lengthPreservingInputs() {
        // Inputs whose lowercase() keeps the UTF-16 length (no U+0130).
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(1_500) { i ->
                val text = Fuzz.text(r, 60).replace("İ", "I")
                val query = Fuzz.text(r, 3).replace("İ", "I")
                checkPlainMatches(text, query, false, "seed=$seed i=$i text=${Fuzz.show(text)} q=${Fuzz.show(query)}")
            }
        }
    }

    @Test
    fun findMatches_regexAndEmptyQueries_doNotThrow() {
        val text = "a.b*c+d?e(f)g[h]i{j}k\\l^m\$n|o\n.*\n(a|b)\n"
        listOf("", " ", "\t", ".", ".*", "(", "[", "\\", "^", "$", "|", "(a|b)", "{", "\n", " . ").forEach { q ->
            checkPlainMatches(text, q, true, "q=${Fuzz.show(q)}")
            checkPlainMatches(text, q, false, "q=${Fuzz.show(q)}")
        }
    }

    @Test
    fun findMatches_overlappingQueries_areNonOverlapping() {
        val matches = find("aaaa", "aa", caseSensitive = true)
        assertEquals(listOf(0, 2), matches.map { it.prop("index") })
    }

    /**
     * BUG reproducer: case-insensitive search lowercases the transcript, and "İ".lowercase() is two
     * chars ("i̇"), so indices found in the lowercased copy run past the end of the original text.
     */
    @Test
    fun findMatches_caseInsensitive_dottedCapitalI_staysInBounds() {
        val text = "İİİİ x"
        val matches = try {
            find(text, "x", caseSensitive = false)
        } catch (e: StringIndexOutOfBoundsException) {
            fail("computeTerminalFindMatches threw for text=${Fuzz.show(text)} q=\"x\": $e")
            return
        }
        assertEquals(1, matches.size)
        assertEquals("index of x", 5, matches.single().prop("index"))
    }

    @Test
    fun findMatches_caseInsensitive_dottedCapitalI_pointsAtTheMatch() {
        // No exception here (one İ), but the reported index is shifted past the real match.
        val text = "İx"
        val matches = find(text, "x", caseSensitive = false)
        assertEquals(1, matches.size)
        assertEquals("index of x", 1, matches.single().prop("index"))
    }

    @Test
    fun findMatches_withEmulator_randomScreens_rowsAndColumnsInBounds() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(60) { i ->
                val cols = 5 + r.nextInt(40)
                val rows = 2 + r.nextInt(10)
                val emulator = Fuzz.emulator(cols, rows, 50)
                val sb = StringBuilder()
                repeat(r.nextInt(40)) {
                    val chunk = Fuzz.text(r, 30).replace("\u001B", "").replace("İ", "I")
                    sb.append(chunk)
                    if (r.nextInt(3) == 0) sb.append("\r\n")
                }
                emulator.feed(sb.toString())
                val transcript = emulator.screen.transcriptText
                val queries = listOf("a", "e", "😀", "中", " a", "ab", Fuzz.text(r, 2).replace("İ", "I"))
                queries.forEach { q ->
                    listOf(true, false).forEach { cs ->
                        listOf(true, false).forEach { join ->
                            val label = "seed=$seed i=$i cols=$cols rows=$rows q=${Fuzz.show(q)} cs=$cs join=$join"
                            val matches = Fuzz.timed(label, 2_000) { find(transcript, q, cs, emulator, join) }
                            val minRow = -emulator.screen.activeTranscriptRows
                            matches.forEach { m ->
                                val rowStart = m.prop("rowStart") as Int?
                                val rowEnd = m.prop("rowEnd") as Int?
                                val colStart = m.prop("columnStart") as Int?
                                val colEnd = m.prop("columnEndExclusive") as Int?
                                assertTrue("$label rowStart=$rowStart", rowStart != null && rowStart in minRow until rows)
                                assertTrue("$label rowEnd=$rowEnd", rowEnd != null && rowEnd in rowStart!!..(rows - 1))
                                assertTrue("$label colStart=$colStart", colStart != null && colStart in 0 until cols)
                                assertTrue("$label colEnd=$colEnd", colEnd != null && colEnd in (colStart!! + 1)..cols)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun findMatches_withEmulator_dottedCapitalI_doesNotThrow() {
        val emulator = Fuzz.emulator(20, 4, 10)
        emulator.feed("İİİİİİ x")
        val transcript = emulator.screen.transcriptText
        try {
            find(transcript, "x", caseSensitive = false, emulator = emulator, join = true)
        } catch (e: StringIndexOutOfBoundsException) {
            fail("computeTerminalFindMatches (emulator) threw: $e")
        }
    }

    @Test
    fun findMatches_hugeTranscript_isFast() {
        val r = Random(Fuzz.SEEDS[0])
        val text = buildString { repeat(200_000) { append(('a' + r.nextInt(3))) ; if (it % 97 == 0) append('\n') } }
        Fuzz.timed("200k chars, frequent query", 3_000) { find(text, "ab", caseSensitive = false) }
        Fuzz.timed("200k chars, absent query", 3_000) { find(text, "zzz", caseSensitive = false) }
    }

    // ---------------------------------------------------------------- resolveSwipeKeyCode

    @Test
    fun resolveSwipeKeyCode_matchesDirectionalOracle() {
        val specials = floatArrayOf(0f, -0f, 1e-3f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f)
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(5_000) {
                fun coord() = if (r.nextInt(20) == 0) specials[r.nextInt(specials.size)] else (r.nextFloat() - 0.5f) * 2000f
                val sx = coord(); val sy = coord(); val ex = coord(); val ey = coord()
                val t0 = r.nextLong(0, 1_000_000)
                val t1 = t0 + r.nextLong(-100, 2_000)
                val min = if (r.nextInt(10) == 0) 0f else r.nextFloat() * 200f
                val maxDur: Long? = if (r.nextBoolean()) null else r.nextLong(0, 1_500)
                val start = swipeStartCtor.newInstance(sx, sy, t0)
                val result = fns.call(swipe, start, ex, ey, t1, min, maxDur) as Int?
                val label = "start=($sx,$sy,$t0) end=($ex,$ey,$t1) min=$min max=$maxDur -> $result"
                if (maxDur != null && t1 - t0 > maxDur) {
                    assertEquals(label, null, result)
                    return@repeat
                }
                val dx = ex - sx
                val dy = ey - sy
                if (result == null) return@repeat
                assertTrue(label, abs(dx) >= min || abs(dy) >= min)
                when (result) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> assertTrue(label, dx > 0 && abs(dx) > abs(dy) * 1.2f)
                    KeyEvent.KEYCODE_DPAD_LEFT -> assertTrue(label, dx < 0 && abs(dx) > abs(dy) * 1.2f)
                    KeyEvent.KEYCODE_DPAD_DOWN -> assertTrue(label, dy > 0 && abs(dy) > abs(dx) * 1.2f)
                    KeyEvent.KEYCODE_DPAD_UP -> assertTrue(label, dy < 0 && abs(dy) > abs(dx) * 1.2f)
                    else -> fail("unexpected key $label")
                }
            }
        }
    }
}
