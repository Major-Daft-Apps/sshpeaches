package com.majordaftapps.sshpeaches.app.fuzz

import com.majordaftapps.sshpeaches.app.data.importer.OpenSshConfigImportResult
import com.majordaftapps.sshpeaches.app.data.importer.OpenSshConfigImporter
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Fuzzes [OpenSshConfigImporter.parse]. The importer's documented "invalid input" path is
 * warnings in the result; it must never throw, hang, or produce out-of-range data.
 */
class OpenSshConfigImporterFuzzTest {

    private val keywords = listOf(
        "Host", "HostName", "User", "Port", "IdentityFile", "LocalForward", "Match", "Include",
        "ProxyJump", "ForwardAgent", "SendEnv", "RemoteForward", "DynamicForward", "IdentitiesOnly"
    )

    private fun parse(contents: String, existing: List<HostConnection> = emptyList()): OpenSshConfigImportResult {
        var counter = 0
        return Fuzz.guarded("OpenSshConfigImporter.parse", contents) {
            OpenSshConfigImporter.parse(
                contents = contents,
                existingHosts = existing,
                nowProvider = { 1L },
                idProvider = { "id-${counter++}" }
            )
        }
    }

    private fun checkInvariants(contents: String, result: OpenSshConfigImportResult, existing: List<HostConnection>) {
        val names = existing.map { it.name.lowercase() }.toMutableSet()
        val hostIds = mutableSetOf<String>()
        for (imported in result.hosts) {
            val host = imported.host
            val ctx = "input=${Fuzz.describe(contents)} host=$host"
            assertTrue("blank name: $ctx", host.name.isNotBlank())
            assertTrue("blank host: $ctx", host.host.isNotBlank())
            assertTrue("blank user: $ctx", host.username.isNotBlank())
            assertTrue("bad port: $ctx", host.port in 1..65_535)
            assertTrue("duplicate name: $ctx", names.add(host.name.lowercase()))
            assertTrue("pattern imported as host: $ctx", !host.name.contains('*') && !host.name.contains('?'))
            hostIds += host.id
        }
        for (forward in result.localForwards) {
            val f = forward.forward
            val ctx = "input=${Fuzz.describe(contents)} forward=$f"
            assertTrue("bad src port: $ctx", f.sourcePort in 1..65_535)
            assertTrue("bad dst port: $ctx", f.destinationPort in 1..65_535)
            assertTrue("blank dst host: $ctx", f.destinationHost.isNotBlank())
            assertTrue("dangling host id: $ctx", f.associatedHosts.all { it in hostIds })
        }
    }

    private fun randomLine(random: Random): String {
        val keyword = keywords.random(random).let { if (random.nextBoolean()) it.lowercase() else it }
        val separator = listOf(" ", "\t", "=", " = ", "  ", "\t=\t").random(random)
        val value = when (random.nextInt(6)) {
            0 -> random.nextInt(-5, 70_000).toString()
            1 -> "\"" + Fuzz.randomString(random, 12) + "\""
            2 -> "${random.nextInt(0, 70_000)} ${Fuzz.randomString(random, 8)}:${random.nextInt(0, 70_000)}"
            3 -> "[${Fuzz.randomString(random, 6, "0123456789abcdef:")}]:${random.nextInt(0, 70_000)}"
            4 -> listOf("*", "!*", "?", "%h.example.com", "%%", "%r@%h").random(random)
            else -> Fuzz.randomString(random, 16)
        }
        val indent = if (random.nextBoolean()) "  " else ""
        val comment = if (random.nextInt(5) == 0) " # ${Fuzz.randomString(random, 8)}" else ""
        return indent + keyword + separator + value + comment
    }

    private fun randomConfig(random: Random, lines: Int): String {
        val eol = listOf("\n", "\r\n", "\r").random(random)
        return (0 until lines).joinToString(eol) { randomLine(random) }
    }

    private val validSample = """
        # sample
        Host *
          User fallback
          IdentityFile ~/.ssh/id_ed25519
        Host prod staging "qa host"
          HostName %h.example.com
          Port 2202
          LocalForward 15432 db.internal:5432
          LocalForward [::1]:8080 [fe80::1]:80
          LocalForward *:9000 localhost:9000
        Match host foo
          User ignored
        Host bastion
          HostName=10.0.0.1
          User=admin
          Port = 2222
    """.trimIndent()

    @Test
    fun randomConfigs_neverThrowOrHang_andKeepInvariants() {
        val random = Random(Fuzz.DEFAULT_SEED)
        repeat(3_000) {
            val contents = randomConfig(random, random.nextInt(0, 30))
            val existing = if (random.nextBoolean()) {
                listOf(HostConnection(id = "e", name = "prod", host = "h", username = "u", preferredAuth = AuthMethod.PASSWORD))
            } else emptyList()
            checkInvariants(contents, parse(contents, existing), existing)
        }
    }

    @Test
    fun mutatedValidConfig_neverThrowOrHang_andKeepInvariants() {
        val random = Random(Fuzz.DEFAULT_SEED + 1)
        assertEquals(4, parse(validSample).hosts.size)
        repeat(3_000) {
            var contents = validSample
            repeat(random.nextInt(1, 4)) { contents = Fuzz.mutateString(random, contents) }
            checkInvariants(contents, parse(contents), emptyList())
        }
    }

    @Test
    fun hugeConfigs_parseWithinBudget() {
        // ~1.3 MB, 20k host blocks, lots of global options and wildcard blocks.
        val big = buildString {
            repeat(2_000) { append("SendEnv VAR$it\n") }
            repeat(200) { append("Host *\n  ForwardAgent no\n") }
            repeat(20_000) { append("Host h$it\n  HostName 10.0.${it / 256 % 256}.${it % 256}\n  User u\n  LocalForward ${1024 + it % 60_000} db:5432\n") }
        }
        val start = System.nanoTime()
        val result = Fuzz.guarded("parse huge", "huge config", budgetMs = 30_000) { OpenSshConfigImporter.parse(big) }
        val ms = (System.nanoTime() - start) / 1_000_000
        println("hugeConfigs_parseWithinBudget: ${big.length} chars, ${result.hosts.size} hosts in ${ms}ms")
        assertEquals(20_000, result.hosts.size)

        // A single enormous line and a line of only quotes.
        parse("Host " + "a".repeat(2_000_000))
        parse("\"".repeat(1_000_000))
        parse("Host x\n  User " + "'\"".repeat(500_000))
    }

    /** Generates configs from a model and checks the importer reads the model back (OpenSSH semantics). */
    private data class ModelHost(val alias: String, val hostName: String?, val user: String, val port: Int?)

    private fun renderModel(random: Random, hosts: List<ModelHost>, quoteValues: Boolean): String {
        val eol = if (random.nextBoolean()) "\n" else "\r\n"
        fun kw(word: String) = when (random.nextInt(3)) {
            0 -> word.lowercase()
            1 -> word.uppercase()
            else -> word
        }
        fun sep() = listOf(" ", "\t", "=", " = ", "\t ").random(random)
        fun v(value: String) = if (quoteValues) "\"$value\"" else value
        return buildString {
            append("# generated").append(eol)
            hosts.forEach { h ->
                append(kw("Host")).append(sep()).append(h.alias)
                if (random.nextInt(4) == 0) append(" # trailing comment")
                append(eol)
                val indent = listOf("", "  ", "\t").random(random)
                h.hostName?.let { append(indent).append(kw("HostName")).append(sep()).append(v(it)).append(eol) }
                append(indent).append(kw("User")).append(sep()).append(v(h.user)).append(eol)
                h.port?.let { append(indent).append(kw("Port")).append(sep()).append(v(it.toString())).append(eol) }
                if (random.nextBoolean()) append(eol)
            }
        }
    }

    private fun randomModel(random: Random): List<ModelHost> =
        (0 until random.nextInt(1, 6)).map { index ->
            ModelHost(
                alias = "h$index-" + Fuzz.randomString(random, 6, "abcdefghijklmnopqrstuvwxyz0123456789-_."),
                hostName = if (random.nextBoolean()) "srv$index.example.com" else null,
                user = Fuzz.randomString(random, 8, "abcdefghijklmnopqrstuvwxyz").ifEmpty { "u" },
                port = if (random.nextBoolean()) random.nextInt(1, 65_536) else null
            )
        }

    private fun assertModelRoundTrip(seed: Long, quoteValues: Boolean) {
        val random = Random(seed)
        repeat(500) {
            val model = randomModel(random)
            val contents = renderModel(random, model, quoteValues)
            val result = parse(contents)
            assertEquals("hosts for ${Fuzz.describe(contents)}", model.size, result.hosts.size)
            model.zip(result.hosts).forEach { (expected, actual) ->
                val ctx = "input=${Fuzz.describe(contents)}"
                assertEquals(ctx, expected.alias, actual.host.name)
                assertEquals(ctx, expected.hostName ?: expected.alias, actual.host.host)
                assertEquals(ctx, expected.user, actual.host.username)
                assertEquals(ctx, expected.port ?: 22, actual.host.port)
            }
        }
    }

    @Test
    fun modelRoundTrip_unquotedValues() = assertModelRoundTrip(Fuzz.DEFAULT_SEED + 2, quoteValues = false)

    // ---- Bugs found by fuzzing, now fixed (OpenSSH behaviour) ----

    @Test
    fun modelRoundTrip_quotedValues() = assertModelRoundTrip(Fuzz.DEFAULT_SEED + 3, quoteValues = true)

    @Test
    fun quotedPortAndUser_areUnquoted() {
        val host = parse("Host a\n  User \"bob\"\n  Port \"2222\"").hosts.single().host
        assertEquals("bob", host.username)
        assertEquals(2222, host.port)
    }

    @Test
    fun aliasInSeveralHostBlocks_combinesOptions() {
        val result = parse("Host prod staging\n  User deploy\nHost prod\n  HostName prod.example.com\n")
        val prod = result.hosts.single { it.alias == "prod" }.host
        // OpenSSH: ssh prod -> deploy@prod.example.com. Importer: deploy@prod (HostName dropped,
        // second block reported as "Skipped prod: missing User").
        assertEquals("prod.example.com", prod.host)
    }

    @Test
    fun firstValueWins() {
        val host = parse("Host a\n  User first\n  User second\n  Port 2201\n  Port 2202").hosts.single().host
        assertEquals("first", host.username)
        assertEquals(2201, host.port)
    }

    @Test
    fun invalidPort_fallsBackTo22_withAWarning() {
        val result = parse("Host a\n  User u\n  Port 65536")
        assertEquals(22, result.hosts.single().host.port)
        assertEquals(listOf("Port 65536 for a is not valid; using 22."), result.warnings)
    }
}
