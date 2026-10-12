package com.majordaftapps.sshpeaches.app.fuzz

import java.util.Base64
import kotlin.random.Random

/**
 * Small helpers shared by the fuzz harnesses. Everything is seeded so a failure reproduces
 * exactly; print the seed/iteration together with the input when reporting a failure.
 */
internal object Fuzz {
    const val DEFAULT_SEED = 0x5EED_F00DL

    /** Per-call budget. Generous so slow CI hosts do not flake, tight enough to catch a hang. */
    const val CALL_BUDGET_MS = 2_000L

    private val interestingChars: List<Char> = listOf(
        ' ', '\t', '\r', '\n', '=', '"', '\'', '#', '%', '*', '?', '!', ':', '[', ']', '/', '\\',
        ',', '{', '}', '\u0000', ' ', ' ', '‮', '﻿', 'é', '中', '\uD83D', '\uDE00',
        '-', '+', '.', '@', '$', '`'
    )

    fun randomString(random: Random, maxLength: Int = 24, alphabet: String? = null): String {
        val length = random.nextInt(0, maxLength + 1)
        return buildString(length) {
            repeat(length) {
                append(
                    when {
                        alphabet != null -> alphabet[random.nextInt(alphabet.length)]
                        random.nextInt(4) == 0 -> interestingChars[random.nextInt(interestingChars.size)]
                        else -> ('a' + random.nextInt(26))
                    }
                )
            }
        }
    }

    /** Random "realistic" unicode string that survives UTF-8 round trips (no lone surrogates). */
    fun randomValidUnicode(random: Random, maxLength: Int = 24): String {
        val length = random.nextInt(0, maxLength + 1)
        return buildString {
            repeat(length) {
                when (random.nextInt(8)) {
                    0 -> append("😀")
                    1 -> append('中')
                    2 -> append('é')
                    3 -> append(listOf(' ', '\t', '\n', '"', '\\', '/', ',', ':', '#', '=', '%').random(random))
                    4 -> append('\u0001')
                    else -> append('a' + random.nextInt(26))
                }
            }
        }
    }

    fun mutateBytes(random: Random, input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(random.nextInt(0, 8)) { random.nextInt().toByte() }
        val bytes = input.copyOf()
        return when (random.nextInt(7)) {
            0 -> { // bit flips
                repeat(random.nextInt(1, 4)) {
                    val i = random.nextInt(bytes.size)
                    bytes[i] = (bytes[i].toInt() xor (1 shl random.nextInt(8))).toByte()
                }
                bytes
            }
            1 -> bytes.copyOf(random.nextInt(0, bytes.size)) // truncation
            2 -> { // duplicate a slice
                val from = random.nextInt(bytes.size)
                val to = random.nextInt(from, bytes.size + 1)
                bytes.copyOfRange(0, to) + bytes.copyOfRange(from, bytes.size)
            }
            3 -> { // random byte overwrite
                repeat(random.nextInt(1, 6)) { bytes[random.nextInt(bytes.size)] = random.nextInt().toByte() }
                bytes
            }
            4 -> { // delete a slice
                val from = random.nextInt(bytes.size)
                val to = random.nextInt(from, bytes.size + 1)
                bytes.copyOfRange(0, from) + bytes.copyOfRange(to, bytes.size)
            }
            5 -> { // insert junk
                val at = random.nextInt(bytes.size + 1)
                bytes.copyOfRange(0, at) + ByteArray(random.nextInt(1, 16)) { random.nextInt().toByte() } +
                    bytes.copyOfRange(at, bytes.size)
            }
            else -> { // set an "interesting" big-endian u32 (huge length fields)
                if (bytes.size < 4) return bytes
                val at = random.nextInt(bytes.size - 3)
                val value = listOf(0, 1, 0x7FFFFFFF, -1, 0x80000000.toInt(), bytes.size, bytes.size + 1).random(random)
                bytes[at] = (value ushr 24).toByte()
                bytes[at + 1] = (value ushr 16).toByte()
                bytes[at + 2] = (value ushr 8).toByte()
                bytes[at + 3] = value.toByte()
                bytes
            }
        }
    }

    fun mutateString(random: Random, input: String): String = when (random.nextInt(5)) {
        0 -> String(mutateBytes(random, input.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
        1 -> if (input.isEmpty()) input else input.substring(0, random.nextInt(input.length))
        2 -> {
            val at = random.nextInt(input.length + 1)
            input.substring(0, at) + randomString(random, 6) + input.substring(at)
        }
        3 -> {
            if (input.isEmpty()) input else {
                val at = random.nextInt(input.length)
                input.substring(0, at) + input.substring(at).replaceFirstChar { randomString(random, 1).ifEmpty { "x" } }
            }
        }
        else -> input + input.takeLast(random.nextInt(0, input.length + 1))
    }

    fun mutateBase64(random: Random, encoded: String): String {
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
        return if (decoded != null && random.nextBoolean()) {
            Base64.getEncoder().encodeToString(mutateBytes(random, decoded))
        } else {
            mutateString(random, encoded)
        }
    }

    /**
     * Runs [block], failing with [describe] if it throws or exceeds [budgetMs]. Returns the result
     * so callers can check properties on it.
     */
    inline fun <T> guarded(
        label: String,
        input: Any?,
        budgetMs: Long = CALL_BUDGET_MS,
        block: () -> T
    ): T {
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (t: Throwable) {
            throw AssertionError("$label threw ${t.javaClass.name}: ${t.message}\ninput=${describe(input)}", t)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        if (elapsedMs > budgetMs) {
            throw AssertionError("$label took ${elapsedMs}ms (> ${budgetMs}ms)\ninput=${describe(input)}")
        }
        return result
    }

    fun describe(input: Any?): String {
        val text = when (input) {
            is ByteArray -> Base64.getEncoder().encodeToString(input)
            else -> input.toString()
        }
        val escaped = buildString {
            for (ch in text) {
                when {
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    ch.code < 0x20 || ch.code in 0x7F..0x9F || ch.isSurrogate() ->
                        append("\\u%04x".format(ch.code))
                    else -> append(ch)
                }
            }
        }
        return if (escaped.length > 600) escaped.take(600) + "...(${escaped.length} chars)" else escaped
    }
}
