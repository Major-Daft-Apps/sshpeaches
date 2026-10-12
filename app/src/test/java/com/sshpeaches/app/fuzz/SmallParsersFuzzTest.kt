package com.majordaftapps.sshpeaches.app.fuzz

import com.majordaftapps.sshpeaches.app.data.local.Converters
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.util.inferredDestinationHost
import com.majordaftapps.sshpeaches.app.util.isValidFingerprint
import com.majordaftapps.sshpeaches.app.util.isValidHostAddress
import com.majordaftapps.sshpeaches.app.util.isValidPort
import com.majordaftapps.sshpeaches.app.util.normalizeAssociatedHostIds
import com.majordaftapps.sshpeaches.app.util.parsePort
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.majordaftapps.sshpeaches.app.util.selectedHostId
import com.majordaftapps.sshpeaches.app.util.snippetCommandToTerminalPayload
import com.majordaftapps.sshpeaches.app.util.snippetReference
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room converters, validators, snippet references and port-forward helpers. */
class SmallParsersFuzzTest {

    @Test
    fun enumConverters_roundTrip_andNeverThrowOnGarbage() {
        val random = Random(Fuzz.DEFAULT_SEED + 30)
        AuthMethod.entries.forEach { assertEquals(it, Converters.toAuthMethod(Converters.fromAuthMethod(it))) }
        ConnectionMode.entries.forEach { assertEquals(it, Converters.toMode(Converters.fromMode(it))) }
        BackgroundBehavior.entries.forEach { assertEquals(it, Converters.toBackgroundBehavior(Converters.fromBackgroundBehavior(it))) }
        PortForwardType.entries.forEach { assertEquals(it, Converters.toPortType(Converters.fromPortType(it))) }
        repeat(5_000) {
            val garbage = if (random.nextInt(5) == 0) null else Fuzz.randomString(random, 20)
            Fuzz.guarded("enum converters", garbage) {
                Converters.toAuthMethod(garbage); Converters.toMode(garbage)
                Converters.toBackgroundBehavior(garbage); Converters.toPortType(garbage)
                Converters.toOsMetadata(garbage); Converters.toStringList(garbage)
            }
        }
    }

    private fun randomOs(random: Random): OsMetadata = when (random.nextInt(3)) {
        0 -> OsMetadata.Undetected
        1 -> OsMetadata.Known(OsFamily.entries.random(random), Fuzz.randomValidUnicode(random, 10).filter { it != ':' }.ifBlank { null })
        else -> OsMetadata.Custom(Fuzz.randomValidUnicode(random, 16))
    }

    @Test
    fun osMetadata_roundTrip_withoutColonsInVersion() {
        val random = Random(Fuzz.DEFAULT_SEED + 31)
        repeat(5_000) {
            val meta = randomOs(random)
            assertEquals(meta, Converters.toOsMetadata(Converters.fromOsMetadata(meta)))
        }
    }

    @Test
    fun osMetadata_roundTrip_versionWithColon() {
        val meta = OsMetadata.Known(OsFamily.UBUNTU, "24.04:LTS")
        assertEquals(meta, Converters.toOsMetadata(Converters.fromOsMetadata(meta)))
    }

    @Test
    fun stringList_roundTrip_forCommaFreeNonEmptyItems() {
        val random = Random(Fuzz.DEFAULT_SEED + 32)
        repeat(5_000) {
            val list = List(random.nextInt(0, 6)) { Fuzz.randomValidUnicode(random, 12).replace(",", "").ifBlank { "x" } }
            assertEquals(list, Converters.toStringList(Converters.fromStringList(list)))
        }
    }

    @Test
    fun stringList_roundTrip_itemWithComma() {
        val list = listOf("ps -eo pid,comm")
        assertEquals(list, Converters.toStringList(Converters.fromStringList(list)))
    }

    @Test
    fun stringList_randomListsRoundTrip_andRowsInTheOldFormatStillRead() {
        val random = Random(Fuzz.DEFAULT_SEED + 51)
        repeat(5_000) {
            // A single blank item can't be told apart from an empty list in either format; the app trims them away.
            val list = List(random.nextInt(0, 5)) { Fuzz.randomString(random, 12) + listOf("", ",", "\"", "[", "x").random(random) }
                .filter { it.isNotBlank() }
            assertEquals(list, Converters.toStringList(Converters.fromStringList(list)))
        }
        assertEquals(listOf("id-1", "id-2"), Converters.toStringList("id-1,id-2"))
        assertEquals("id-1,id-2", Converters.fromStringList(listOf("id-1", "id-2")))
    }

    @Test
    fun snippetReference_roundTrip() {
        val random = Random(Fuzz.DEFAULT_SEED + 33)
        repeat(5_000) {
            val id = Fuzz.randomString(random, 40)
            assertEquals(id, parseSnippetReference(snippetReference(id)))
            val other = Fuzz.randomString(random, 40)
            if (!other.startsWith("snippet:")) assertEquals(null, parseSnippetReference(other))
        }
    }

    @Test
    fun snippetPayload_neverContainsLoneLineFeeds_andIsIdempotentOnPayloadLines() {
        val random = Random(Fuzz.DEFAULT_SEED + 34)
        repeat(5_000) {
            val command = Fuzz.randomString(random, 60)
            val payload = Fuzz.guarded("snippetCommandToTerminalPayload", command) { snippetCommandToTerminalPayload(command) }
            assertFalse("LF in payload for ${Fuzz.describe(command)}", payload.contains('\n'))
            if (command.isNotBlank() && payload.isNotEmpty()) assertTrue(payload.endsWith("\r"))
        }
    }

    @Test
    fun parsePort_agreesWithIsValidPort() {
        val random = Random(Fuzz.DEFAULT_SEED + 35)
        repeat(20_000) {
            val n = when (random.nextInt(3)) {
                0 -> random.nextInt(-10, 70_000)
                1 -> random.nextInt()
                else -> listOf(0, 1, 65_535, 65_536, Int.MIN_VALUE, Int.MAX_VALUE).random(random)
            }
            assertEquals(if (isValidPort(n)) n else null, parsePort(n.toString()))
        }
        listOf("", " 22", "22 ", "+22", "0x16", "２２", "99999999999").forEach { Fuzz.guarded("parsePort", it) { parsePort(it) } }
    }

    @Test
    fun validators_neverThrowOrHang() {
        val random = Random(Fuzz.DEFAULT_SEED + 36)
        repeat(5_000) {
            val text = Fuzz.randomString(random, 300)
            Fuzz.guarded("isValidHostAddress", text) { isValidHostAddress(text) }
            Fuzz.guarded("isValidFingerprint", text) { isValidFingerprint(text) }
        }
        // Regex backtracking probes.
        val probes = listOf(
            "a".repeat(63) + ".".repeat(1) + "a-".repeat(5_000),
            ("a" + "-".repeat(60) + ".").repeat(4) + "1",
            "A".repeat(100_000) + "!",
            "ab:".repeat(30_000)
        )
        probes.forEach { probe ->
            Fuzz.guarded("isValidHostAddress probe", probe, budgetMs = 500) { isValidHostAddress(probe) }
            Fuzz.guarded("isValidFingerprint probe", probe, budgetMs = 500) { isValidFingerprint(probe) }
        }
    }

    @Test
    fun isValidHostAddress_acceptsAddressesTheImportersProduce() {
        // All of these come out of OpenSshConfigImporter (HostName / LocalForward) or a QR code and
        // then fail "Enter a valid hostname or IP address." / "Selected host has an invalid address."
        listOf("::1", "fe80::1", "2001:db8::10", "myserver", "db", "raspberrypi").forEach {
            assertTrue("rejected $it", isValidHostAddress(it))
        }
    }

    @Test
    fun portForwardHelpers_neverThrow() {
        val random = Random(Fuzz.DEFAULT_SEED + 37)
        repeat(5_000) {
            val ids = List(random.nextInt(0, 5)) { Fuzz.randomString(random, 8) }
            val normalized = normalizeAssociatedHostIds(ids)
            assertTrue(normalized.size <= 1)
            assertTrue(normalized.all { it.isNotBlank() && it == it.trim() })
            val forward = PortForward(
                id = "f", label = "l", type = PortForwardType.LOCAL, sourcePort = 1,
                destinationHost = Fuzz.randomString(random, 10), associatedHosts = ids
            )
            assertEquals(normalized.firstOrNull(), forward.selectedHostId())
            assertTrue(forward.inferredDestinationHost().isNotBlank())
        }
    }
}
