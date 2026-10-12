package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.telemetry.TelemetrySanitizer
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Embeds identifying values (addresses, host names, user@host, URLs, paths, named secrets) in
 * random error-like text and checks [TelemetrySanitizer.scrub] removes every one, and that it stays
 * fast on long hostile input (it runs on the main thread when a problem report is opened).
 */
class TelemetrySanitizerFuzzTest {

    private val filler = listOf(
        "failed", "to", "connect", "error", "Connection", "refused", "timeout", "after", "10000ms", "port", "22",
        "ECONNREFUSED", "Exception:", "caused", "by", "while", "reading", "status", "-1", "SFTP", "OK", "at", "12:34:56"
    )
    private val separators = listOf(" ", ", ", " (", ") ", " '", "' ", " \"", "\" ", "=", ": ", " [", "] ", "\n", "\t", " /", "; ")

    private data class Leak(val category: String, val secret: String, val input: String, val output: String)

    private val forbidden = (filler + listOf("<user>@<host>", "<ip>", "<url>", "<path>", "<redacted>", "<fingerprint>", "home", "My")).joinToString(" ")

    /** A random identifier that cannot collide with filler words or scrub placeholders. */
    private fun label(random: Random, min: Int = 3, max: Int = 10): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val first = "abcdefghijklmnopqrstuvwxyz"
        while (true) {
            val candidate = buildString {
                append(first[random.nextInt(first.length)])
                repeat(random.nextInt(min - 1, max)) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            if (!forbidden.contains(candidate, ignoreCase = true)) return candidate
        }
    }

    private fun ipv4(random: Random) = List(4) { random.nextInt(1, 255) }.joinToString(".")
    private fun hostName(random: Random) =
        List(random.nextInt(1, 4)) { label(random) }.joinToString(".") + "." + listOf("com", "net", "lan", "local", "internal", "io", "co.uk").random(random)
    private fun ipv6WithLetters(random: Random) =
        List(8) { Integer.toHexString(random.nextInt(0xa000, 0x10000)) }.joinToString(":")
    private fun ipv6Digits(random: Random) =
        listOf("2001", "2600", "2607") .random(random) + ":" + List(7) { random.nextInt(1, 9999).toString() }.joinToString(":")
    private fun ipv6Compressed(random: Random) =
        "2a01:" + Integer.toHexString(random.nextInt(0x100, 0xffff)) + "::" + random.nextInt(1, 9999)
    private fun absolutePath(random: Random) = "/" + List(random.nextInt(1, 5)) { label(random) }.joinToString("/")
    private fun homePath(random: Random) = "~/" + List(random.nextInt(1, 3)) { label(random) }.joinToString("/") + ".txt"
    private fun relativePath(random: Random) = List(random.nextInt(2, 4)) { label(random) }.joinToString("/") + ".conf"
    private fun spacedPath(random: Random) = "/home/" + label(random) + "/My " + label(random) + "/" + label(random) + ".pdf"

    /** Generates one input per call; the injected value must not appear in the output. */
    private fun runCategory(
        name: String,
        iterations: Int,
        seed: Long,
        secretsFor: (String) -> List<String> = { emptyList() },
        generate: (Random) -> Pair<String, List<String>>
    ): List<Leak> {
        val random = Random(seed)
        val leaks = mutableListOf<Leak>()
        repeat(iterations) {
            val (value, mustNotAppear) = generate(random)
            val input = buildString {
                repeat(random.nextInt(0, 4)) { append(filler.random(random)).append(' ') }
                append(separators.random(random).trimStart())
                append(value)
                append(separators.random(random))
                repeat(random.nextInt(0, 4)) { append(filler.random(random)).append(' ') }
            }
            val output = TelemetrySanitizer.scrub(input, secretsFor(value))
            mustNotAppear.firstOrNull { output.contains(it, ignoreCase = true) }?.let {
                leaks += Leak(name, it, input, output)
            }
        }
        return leaks
    }

    private fun assertNoLeaks(leaks: List<Leak>) {
        if (leaks.isEmpty()) return
        val sample = leaks.take(4).joinToString("\n") { "  leaked '${it.secret}'\n    in:  ${it.input.esc()}\n    out: ${it.output.esc()}" }
        fail("${leaks.size} leak(s) in ${leaks.first().category}:\n$sample")
    }

    private fun String.esc() = replace("\n", "\\n").replace("\t", "\\t")

    @Test
    fun ipv4AddressesAndHostNamesAndUserAtHostNeverLeak() {
        assertNoLeaks(runCategory("ipv4", 1500, Fuzz.SEED) { r -> ipv4(r).let { it to listOf(it) } })
        assertNoLeaks(runCategory("hostname", 1500, Fuzz.SEED + 1) { r -> hostName(r).let { it to listOf(it) } })
        assertNoLeaks(
            runCategory("user@host", 1500, Fuzz.SEED + 2) { r ->
                val user = label(r)
                val host = if (r.nextBoolean()) hostName(r) else ipv4(r)
                "$user@$host" to listOf(user, host)
            }
        )
        assertNoLeaks(
            runCategory("url", 800, Fuzz.SEED + 3) { r ->
                val host = hostName(r)
                "${listOf("https", "ssh", "sftp").random(r)}://$host/${label(r)}" to listOf(host)
            }
        )
        assertNoLeaks(runCategory("ipv6-hex", 800, Fuzz.SEED + 4) { r -> ipv6WithLetters(r).let { it to listOf(it) } })
        assertNoLeaks(runCategory("ipv6-compressed", 800, Fuzz.SEED + 5) { r -> ipv6Compressed(r).let { it to listOf(it) } })
    }

    @Test
    fun absoluteAndHomePathsNeverLeak() {
        assertNoLeaks(
            runCategory("absolute-path", 1500, Fuzz.SEED + 6) { r ->
                val path = absolutePath(r)
                path to path.split('/').filter { it.length >= 3 }
            }
        )
        assertNoLeaks(
            runCategory("home-path", 1500, Fuzz.SEED + 7) { r ->
                val path = homePath(r)
                path to path.removePrefix("~/").split('/').map { it.removeSuffix(".txt") }.filter { it.length >= 3 }
            }
        )
    }

    @Test
    fun namedSecretsNeverLeakWhateverSurroundsThem() {
        assertNoLeaks(
            runCategory("named-secret", 2000, Fuzz.SEED + 8, secretsFor = { listOf(it) }) { r ->
                val secret = when (r.nextInt(4)) {
                    0 -> label(r)
                    1 -> label(r).uppercase()
                    2 -> "${label(r)}-${label(r)}"
                    else -> label(r) + r.nextInt(10, 99)
                }
                secret to listOf(secret)
            }
        )
    }

    /** All-decimal IPv6 groups (no a-f letters, no "::") are mistaken for a time of day and kept. */
    @Test
    fun ipv6AddressesWithoutHexLettersNeverLeak() {
        assertNoLeaks(runCategory("ipv6-decimal-groups", 400, Fuzz.SEED + 9) { r -> ipv6Digits(r).let { it to listOf(it) } })
    }

    /** IPv4 glued to a word character (underscore, letter) has no \b before it and is kept. */
    @Test
    fun ipv4GluedToAWordNeverLeaks() {
        assertNoLeaks(
            runCategory("ipv4-glued", 400, Fuzz.SEED + 10) { r ->
                val ip = ipv4(r)
                "${listOf("peer_", "addr_", "host", "ip_").random(r)}$ip" to listOf(ip)
            }
        )
    }

    /** Paths without a leading / or ~ are kept (only the last "name.ext" is caught, as a host name). */
    @Test
    fun relativePathsNeverLeak() {
        assertNoLeaks(
            runCategory("relative-path", 400, Fuzz.SEED + 11) { r ->
                val path = relativePath(r)
                path to path.removeSuffix(".conf").split('/').filter { it.length >= 3 }
            }
        )
    }

    /** Path components after a space are kept. */
    @Test
    fun pathsWithSpacesNeverLeak() {
        assertNoLeaks(
            runCategory("spaced-path", 400, Fuzz.SEED + 12) { r ->
                val path = spacedPath(r)
                path to path.split('/', ' ').map { it.removeSuffix(".pdf") }.filter { it.length >= 3 && it != "home" && it != "My" }
            }
        )
    }

    @Test
    fun randomInputNeverThrows() {
        val random = Random(Fuzz.SEED + 13)
        repeat(3_000) {
            val text = Fuzz.string(random, 200)
            val secrets = List(random.nextInt(0, 3)) { Fuzz.string(random, 12) as String? } + listOf(null)
            Fuzz.withinMillis(2_000, "scrub") { TelemetrySanitizer.scrub(text, secrets) }
        }
    }

    /**
     * userAtHost (`[A-Za-z0-9._%+-]+@...`) and hostName have no left anchor, so a long run of
     * those characters without a match is scanned from every start position: quadratic time.
     */
    @Test
    fun longHostileMessagesAreScrubbedQuickly() {
        val inputs = mapOf(
            "letters" to "a".repeat(8 * 1024),
            "dotted-labels" to "a.".repeat(4 * 1024),
            "slashes" to "/a".repeat(4 * 1024),
            "digits" to "1".repeat(8 * 1024)
        )
        val slow = mutableListOf<String>()
        inputs.forEach { (name, text) ->
            val elapsed = Fuzz.elapsedMillis {
                runCatching { Fuzz.withinMillis(60_000, "scrub-$name") { TelemetrySanitizer.scrub(text) } }
                    .exceptionOrNull()?.let { slow += "$name: ${it.javaClass.simpleName} ${it.message?.take(120)}" }
            }
            if (elapsed > 500) slow += "$name (${text.length} chars): ${elapsed}ms"
        }
        assertTrue("slow or failing scrub: $slow", slow.isEmpty())
    }
}
