package com.majordaftapps.sshpeaches.app.fuzz

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.ui.components.HostQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.IdentityQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.PortForwardQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.SnippetQrImportResult
import com.majordaftapps.sshpeaches.app.ui.components.decodeForwardFromQr
import com.majordaftapps.sshpeaches.app.ui.components.decodeHostFromQr
import com.majordaftapps.sshpeaches.app.ui.components.decodeIdentityFromQr
import com.majordaftapps.sshpeaches.app.ui.components.decodeSnippetFromQr
import com.majordaftapps.sshpeaches.app.ui.components.encodeHostPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeIdentityPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodePortForwardPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeSnippetPayload
import com.majordaftapps.sshpeaches.app.ui.components.processHostQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processIdentityQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processPortForwardQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processSnippetQrImport
import com.majordaftapps.sshpeaches.app.util.parsePort
import java.util.Base64
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip and garbage-decoding fuzz for the per-item share QR codes. Decoders document `null`
 * (and the process* helpers an `Error`) as the invalid-input path.
 */
class QrPayloadFuzzTest {

    private fun s(random: Random, max: Int = 20) = Fuzz.randomValidUnicode(random, max)
    private fun optS(random: Random) = if (random.nextBoolean()) null else s(random, 12).ifBlank { null }
    private fun optL(random: Random) = if (random.nextBoolean()) null else random.nextLong()

    private fun randomHost(random: Random) = HostConnection(
        id = s(random, 36),
        name = s(random),
        host = s(random),
        port = random.nextInt(),
        username = s(random),
        preferredAuth = AuthMethod.entries.random(random),
        group = optS(random),
        createdEpochMillis = optL(random),
        updatedEpochMillis = optL(random),
        lastUsedEpochMillis = optL(random),
        notes = s(random, 200),
        defaultMode = ConnectionMode.entries.random(random),
        hasPassword = random.nextBoolean(),
        useMosh = random.nextBoolean(),
        preferredIdentityId = optS(random),
        preferredForwardId = optS(random),
        startupScript = s(random, 100),
        backgroundBehavior = BackgroundBehavior.entries.random(random),
        terminalProfileId = optS(random)
    )

    @Test
    fun host_roundTrip_isIdentityOnEncodedFields() {
        val random = Random(Fuzz.DEFAULT_SEED + 20)
        repeat(2_000) {
            val host = randomHost(random)
            val pwd = if (random.nextBoolean()) s(random, 40).ifBlank { null } else null
            val payload = encodeHostPayload(host, pwd)
            val decoded = Fuzz.guarded("decodeHostFromQr", payload) { decodeHostFromQr(payload) }
            assertNotNull("decode failed for $host", decoded)
            assertEquals("host=$host", host, decoded!!.host)
            assertEquals(pwd, decoded.encryptedPasswordPayload)
        }
    }

    @Test
    fun identity_forward_snippet_roundTrip() {
        val random = Random(Fuzz.DEFAULT_SEED + 21)
        repeat(2_000) {
            val identity = Identity(
                id = s(random, 36), label = s(random), fingerprint = s(random, 50),
                username = optS(random), group = optS(random), createdEpochMillis = random.nextLong(),
                updatedEpochMillis = optL(random), lastUsedEpochMillis = optL(random), hasPrivateKey = random.nextBoolean()
            )
            val keyPayload = if (random.nextBoolean()) s(random, 40).ifBlank { null } else null
            val decodedIdentity = decodeIdentityFromQr(encodeIdentityPayload(identity, keyPayload))
            assertEquals(identity, decodedIdentity?.identity)
            assertEquals(keyPayload, decodedIdentity?.encryptedKeyPayload)

            val forward = PortForward(
                id = s(random, 36), label = s(random), group = optS(random), type = PortForwardType.LOCAL,
                sourceHost = s(random), sourcePort = random.nextInt(), destinationHost = s(random),
                destinationPort = random.nextInt(), createdEpochMillis = optL(random),
                updatedEpochMillis = optL(random), lastUsedEpochMillis = optL(random)
            )
            assertEquals(forward, decodeForwardFromQr(encodePortForwardPayload(forward)))

            val snippet = Snippet(
                id = s(random, 36), title = s(random), group = optS(random), description = s(random, 80),
                command = s(random, 200), createdEpochMillis = optL(random), updatedEpochMillis = optL(random),
                lastUsedEpochMillis = optL(random)
            )
            assertEquals(snippet, decodeSnippetFromQr(encodeSnippetPayload(snippet)))
        }
    }

    private val sampleHostPayload = encodeHostPayload(
        HostConnection(id = "h1", name = "prod", host = "prod.example.com", port = 2202, username = "root", preferredAuth = AuthMethod.PASSWORD),
        "pwd-blob"
    )

    @Test
    fun garbageAndMutatedPayloads_neverThrowOrHang() {
        val random = Random(Fuzz.DEFAULT_SEED + 22)
        val samples = listOf(
            sampleHostPayload,
            encodeIdentityPayload(Identity(id = "i", label = "l", fingerprint = "SHA256:abc", createdEpochMillis = 1), "k"),
            encodePortForwardPayload(PortForward(id = "f", label = "l", type = PortForwardType.LOCAL, sourcePort = 1, destinationHost = "d", destinationPort = 2)),
            encodeSnippetPayload(Snippet(id = "s", title = "t", command = "ls"))
        )
        val nested = Base64.getEncoder().encodeToString(("[".repeat(100_000)).toByteArray())
        val deepObject = Base64.getEncoder().encodeToString(("{\"a\":".repeat(50_000)).toByteArray())
        val extra = listOf("", " ", "====", "e30=", "bnVsbA==", "W10=", nested, deepObject, "\u0000", "ü")
        repeat(4_000) { iteration ->
            val input = when {
                iteration < extra.size -> extra[iteration]
                random.nextInt(4) == 0 -> Fuzz.randomString(random, 60)
                else -> Fuzz.mutateBase64(random, samples.random(random))
            }
            Fuzz.guarded("processHostQrImport", input) { processHostQrImport(input, emptyList()) { "gen" } }
            Fuzz.guarded("processIdentityQrImport", input) { processIdentityQrImport(input, emptyList(), { 0L }) { "gen" } }
            Fuzz.guarded("processPortForwardQrImport", input) { processPortForwardQrImport(input) }
            Fuzz.guarded("processSnippetQrImport", input) { processSnippetQrImport(input) { "gen" } }
        }
    }

    // ---------------- validator / parser agreement ----------------

    private fun hostQr(json: String) = Base64.getEncoder().encodeToString(json.toByteArray())

    @Test
    fun hostQr_readyResultHasAPortTheEditorWouldAccept() {
        listOf(0, -1, 70_000, Int.MAX_VALUE).forEach { port ->
            val result = processHostQrImport(hostQr("""{"name":"n","host":"h.example.com","user":"u","port":$port}"""), emptyList())
            if (result is HostQrImportResult.Ready) {
                assertNotNull("port $port imported by QR but rejected by the host editor", parsePort(result.data.host.port.toString()))
            }
        }
    }

    @Test
    fun hostQr_doesNotReuseAnExistingLocalHostId() {
        val local = HostConnection(id = "h1", name = "renamed-locally", host = "a.example.com", username = "u", preferredAuth = AuthMethod.PASSWORD)
        val result = processHostQrImport(sampleHostPayload, listOf(local)) { "fresh-id" }
        assertTrue(result is HostQrImportResult.Ready)
        assertTrue("targetId collides with existing host ${local.id}", (result as HostQrImportResult.Ready).data.targetId != local.id)
    }

    @Test
    fun identityQr_withoutFingerprint_isAnError() {
        listOf("{}", """{"label":"x"}""").forEach { json ->
            val result = processIdentityQrImport(hostQr(json), emptyList(), { 0L }) { "id" }
            assertTrue("$json -> $result", result is IdentityQrImportResult.Error)
        }
        val fromHostQr = processIdentityQrImport(sampleHostPayload, emptyList(), { 0L }) { "id" }
        assertTrue("host QR accepted as identity: $fromHostQr", fromHostQr is IdentityQrImportResult.Error)
    }

    @Test
    fun hostQr_roundTripsAttachTmux() {
        val host = HostConnection(id = "h", name = "n", host = "h", username = "u", preferredAuth = AuthMethod.PASSWORD, attachTmux = true)
        assertEquals(true, decodeHostFromQr(encodeHostPayload(host, null))?.host?.attachTmux)
    }

    @Test
    fun portForwardQr_isOnlyAPrefillForTheEditor() {
        // Documents behaviour: forward QR results open the editor, which validates; so 0 ports are fine here.
        val result = processPortForwardQrImport(hostQr("""{"srcPort":0}"""))
        assertTrue(result is PortForwardQrImportResult.Ready)
        val snippet = processSnippetQrImport(sampleHostPayload)
        assertTrue(snippet is SnippetQrImportResult.Error)
    }
}
