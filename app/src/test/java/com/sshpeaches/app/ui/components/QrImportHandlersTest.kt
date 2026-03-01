package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImportHandlersTest {

    @Test
    fun hostImport_invalidPayload_returnsError() {
        val result = processHostQrImport("not-base64", emptyList())
        assertTrue(result is HostQrImportResult.Error)
        assertEquals("Invalid host QR", (result as HostQrImportResult.Error).message)
    }

    @Test
    fun hostImport_duplicateName_returnsError() {
        val payload = encodeHostPayload(sampleHost(id = "incoming"), encryptedPasswordPayload = null)
        val existing = listOf(sampleHost(id = "existing"))

        val result = processHostQrImport(payload, existing)

        assertTrue(result is HostQrImportResult.Error)
        assertEquals("Host already exists", (result as HostQrImportResult.Error).message)
    }

    @Test
    fun hostImport_missingId_assignsGeneratedTargetId() {
        val payload = encodeHostPayload(
            sampleHost(id = "", name = "new-host"),
            encryptedPasswordPayload = "enc-payload"
        )

        val result = processHostQrImport(
            contents = payload,
            existingHosts = emptyList(),
            idProvider = { "generated-id-1" }
        )

        assertTrue(result is HostQrImportResult.Ready)
        val ready = (result as HostQrImportResult.Ready).data
        assertEquals("generated-id-1", ready.targetId)
        assertEquals("enc-payload", ready.encryptedPasswordPayload)
        assertEquals("new-host", ready.host.name)
    }

    @Test
    fun identityImport_invalidPayload_returnsError() {
        val result = processIdentityQrImport("not-base64", emptyList())
        assertTrue(result is IdentityQrImportResult.Error)
        assertEquals("Invalid identity QR", (result as IdentityQrImportResult.Error).message)
    }

    @Test
    fun identityImport_matchingFingerprint_requestsOverwrite() {
        val existing = listOf(
            Identity(
                id = "existing-id",
                label = "Old",
                fingerprint = "SHA256:ABC",
                username = "root",
                createdEpochMillis = 10L,
                hasPrivateKey = true
            )
        )
        val incoming = Identity(
            id = "incoming-id",
            label = "",
            fingerprint = "sha256:abc",
            username = null,
            createdEpochMillis = 20L,
            hasPrivateKey = true
        )
        val payload = encodeIdentityPayload(incoming, "enc-key")

        val result = processIdentityQrImport(payload, existing)

        assertTrue(result is IdentityQrImportResult.NeedsOverwrite)
        val overwrite = (result as IdentityQrImportResult.NeedsOverwrite).overwrite
        assertEquals("existing-id", overwrite.targetId)
        assertEquals("Old", overwrite.label)
        assertEquals("root", overwrite.username)
        assertEquals("enc-key", overwrite.encryptedKeyPayload)
    }

    @Test
    fun identityImport_ready_setsFallbackLabelIdAndTimestamp() {
        val payload = encodeIdentityPayload(
            Identity(
                id = "",
                label = "",
                fingerprint = "SHA256:NEW",
                username = "ubuntu",
                createdEpochMillis = 1L
            ),
            encryptedKeyPayload = null
        )

        val result = processIdentityQrImport(
            contents = payload,
            existingIdentities = emptyList(),
            nowProvider = { 123456L },
            idProvider = { "identity-generated" }
        )

        assertTrue(result is IdentityQrImportResult.Ready)
        val ready = (result as IdentityQrImportResult.Ready).data
        assertEquals("identity-generated", ready.identity.id)
        assertEquals("Imported Identity", ready.identity.label)
        assertEquals(123456L, ready.identity.createdEpochMillis)
    }

    @Test
    fun portForwardImport_invalidPayload_returnsError() {
        val result = processPortForwardQrImport("not-base64")
        assertTrue(result is PortForwardQrImportResult.Error)
        assertEquals("Invalid port forward QR", (result as PortForwardQrImportResult.Error).message)
    }

    @Test
    fun portForwardImport_validPayload_returnsReady() {
        val payload = encodePortForwardPayload(
            PortForward(
                id = "pf-1",
                label = "PF",
                type = PortForwardType.LOCAL,
                sourceHost = "0.0.0.0",
                sourcePort = 2222,
                destinationHost = "127.0.0.1",
                destinationPort = 22
            )
        )

        val result = processPortForwardQrImport(payload)

        assertTrue(result is PortForwardQrImportResult.Ready)
        assertEquals("pf-1", (result as PortForwardQrImportResult.Ready).forward.id)
    }

    @Test
    fun portForwardImport_nonLocalPayload_returnsError() {
        val payload = encodePortForwardPayload(
            PortForward(
                id = "pf-2",
                label = "PF Remote",
                type = PortForwardType.REMOTE,
                sourceHost = "0.0.0.0",
                sourcePort = 2222,
                destinationHost = "127.0.0.1",
                destinationPort = 22
            )
        )

        val result = processPortForwardQrImport(payload)

        assertTrue(result is PortForwardQrImportResult.Error)
        assertEquals("Only Local forwarding is supported right now", (result as PortForwardQrImportResult.Error).message)
    }

    @Test
    fun snippetImport_blankCommand_returnsError() {
        val payload = encodeSnippetPayload(
            Snippet(
                id = "s1",
                title = "Bad",
                description = "",
                command = ""
            )
        )

        val result = processSnippetQrImport(payload)

        assertTrue(result is SnippetQrImportResult.Error)
        assertEquals("Invalid snippet QR", (result as SnippetQrImportResult.Error).message)
    }

    @Test
    fun snippetImport_blankTitle_usesFallback() {
        val payload = encodeSnippetPayload(
            Snippet(
                id = "s2",
                title = "",
                description = "desc",
                command = "uptime"
            )
        )

        val result = processSnippetQrImport(payload, idProvider = { "abc-123" })

        assertTrue(result is SnippetQrImportResult.Ready)
        val ready = (result as SnippetQrImportResult.Ready).data
        assertEquals("Snippet abc-123", ready.title)
        assertEquals("desc", ready.description)
        assertEquals("uptime", ready.command)
    }

    private fun sampleHost(id: String, name: String = "prod"): HostConnection =
        HostConnection(
            id = id,
            name = name,
            host = "example.com",
            port = 22,
            username = "root",
            preferredAuth = AuthMethod.PASSWORD,
            group = "g",
            notes = "n",
            defaultMode = ConnectionMode.SSH,
            hasPassword = true,
            useMosh = false,
            preferredIdentityId = "i1",
            preferredForwardId = "f1",
            startupScript = "echo hi",
            backgroundBehavior = BackgroundBehavior.INHERIT,
            terminalProfileId = "p1"
        )
}
