package com.majordaftapps.sshpeaches.app.fuzz.net

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.fail

/** Shared, deterministic helpers for the network-input fuzz harnesses. */
internal object Fuzz {
    /** Every harness uses fixed seeds so a failure reproduces exactly. */
    const val SEED = 0x5EED_CAFEL

    private val nasty = listOf(
        "", ".", "..", "...", "/", "//", "./", "../", "/..", "../..", "a/../..", "../../../../etc/passwd",
        "/etc/passwd", "~", "~root", "\u0000", "a\u0000b", "\n", "\r\n", "a\nb", "\t", " ", "  leading", "trailing  ",
        "\u001b[31mred\u001b[0m", "\u202Eevil\u202C", "\uFEFF", "\uFFFD", "\uD800", "\uDFFF", "\uD83C\uDF51",
        "CON", "NUL", "aux.txt", "a\\b", "..\\..\\x", "C:\\Windows", "%00", "%2e%2e%2f", "\u2215", "\u2024\u2024",
        "*", "?", "\"", "'", "`", "$(id)", ";rm -rf /", "|", "<", ">", "&", "@", "#", ":", "a:b"
    )

    fun string(random: Random, maxLength: Int = 64): String {
        return when (random.nextInt(6)) {
            0 -> nasty[random.nextInt(nasty.size)]
            1 -> buildString { repeat(random.nextInt(4) + 1) { append(nasty[random.nextInt(nasty.size)]) } }
            2 -> buildString {
                repeat(random.nextInt(maxLength + 1)) { append(random.nextInt(0, 0x80).toChar()) }
            }
            3 -> buildString {
                repeat(random.nextInt(maxLength + 1)) { append(random.nextInt(0, 0x10000).toChar()) }
            }
            4 -> buildString {
                val alphabet = "aAbB.-_/ 09~"
                repeat(random.nextInt(maxLength + 1)) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            else -> "x".repeat(random.nextInt(maxLength * 4 + 1))
        }
    }

    /**
     * Runs [block] on its own thread and fails if it does not finish within [timeoutMs]. The thread
     * is a daemon so a genuine hang cannot keep the test JVM alive.
     */
    fun <T> withinMillis(timeoutMs: Long, label: String, block: () -> T): T {
        var result: Result<T>? = null
        val worker = Thread({ result = runCatching(block) }, "fuzz-$label").apply {
            isDaemon = true
            start()
        }
        worker.join(timeoutMs)
        if (worker.isAlive) {
            val trace = worker.stackTrace.take(12).joinToString("\n    at ")
            worker.interrupt()
            fail("$label did not finish within ${timeoutMs}ms; worker is at:\n    at $trace")
        }
        return result!!.getOrThrow()
    }

    fun elapsedMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }
}
