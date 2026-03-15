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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadRoundTripTest {

    @Test
    fun host_roundTrip_withEncryptedPasswordPayload() {
        val host = sampleHost(id = "h1")
        val payload = encodeHostPayload(
            host = host,
            encryptedPasswordPayload = "enc-pwd-blob"
        )

        val decoded = decodeHostFromQr(payload)

        assertNotNull(decoded)
        assertEquals(host.id, decoded!!.host.id)
        assertEquals(host.name, decoded.host.name)
        assertEquals(host.host, decoded.host.host)
        assertEquals(host.port, decoded.host.port)
        assertEquals(host.username, decoded.host.username)
        assertEquals(host.preferredAuth, decoded.host.preferredAuth)
        assertEquals(host.group, decoded.host.group)
        assertEquals(host.notes, decoded.host.notes)
        assertEquals(host.hasPassword, decoded.host.hasPassword)
        assertEquals(host.useMosh, decoded.host.useMosh)
        assertEquals(host.preferredIdentityId, decoded.host.preferredIdentityId)
        assertEquals(host.preferredForwardId, decoded.host.preferredForwardId)
        assertEquals(host.startupScript, decoded.host.startupScript)
        assertEquals(host.backgroundBehavior, decoded.host.backgroundBehavior)
        assertEquals(host.terminalProfileId, decoded.host.terminalProfileId)
        assertEquals("enc-pwd-blob", decoded.encryptedPasswordPayload)
    }

    @Test
    fun identity_roundTrip_withEncryptedKeyPayload() {
        val identity = Identity(
            id = "id-1",
            label = "Prod key",
            fingerprint = "SHA256:abc123",
            username = "ubuntu",
            createdEpochMillis = 100L,
            hasPrivateKey = true
        )
        val payload = encodeIdentityPayload(identity, "enc-key-blob")

        val decoded = decodeIdentityFromQr(payload)

        assertNotNull(decoded)
        assertEquals(identity.id, decoded!!.identity.id)
        assertEquals(identity.label, decoded.identity.label)
        assertEquals(identity.fingerprint, decoded.identity.fingerprint)
        assertEquals(identity.username, decoded.identity.username)
        assertEquals(identity.hasPrivateKey, decoded.identity.hasPrivateKey)
        assertEquals("enc-key-blob", decoded.encryptedKeyPayload)
    }

    @Test
    fun portForward_roundTrip() {
        val forward = PortForward(
            id = "pf-1",
            label = "DB tunnel",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 5432,
            destinationHost = "10.0.0.5",
            destinationPort = 5432
        )
        val payload = encodePortForwardPayload(forward)

        val decoded = decodeForwardFromQr(payload)

        assertNotNull(decoded)
        assertEquals(forward.id, decoded!!.id)
        assertEquals(forward.label, decoded.label)
        assertEquals(forward.type, decoded.type)
        assertEquals(forward.sourceHost, decoded.sourceHost)
        assertEquals(forward.sourcePort, decoded.sourcePort)
        assertEquals(forward.destinationHost, decoded.destinationHost)
        assertEquals(forward.destinationPort, decoded.destinationPort)
    }

    @Test
    fun snippet_roundTrip() {
        val snippet = Snippet(
            id = "sn-1",
            title = "Uptime",
            description = "Print load and uptime",
            command = "uptime"
        )
        val payload = encodeSnippetPayload(snippet)

        val decoded = decodeSnippetFromQr(payload)

        assertNotNull(decoded)
        assertEquals(snippet.id, decoded!!.id)
        assertEquals(snippet.title, decoded.title)
        assertEquals(snippet.description, decoded.description)
        assertEquals(snippet.command, decoded.command)
    }

    @Test
    fun decode_invalidPayload_returnsNull() {
        assertNull(decodeHostFromQr("not-base64"))
        assertNull(decodeIdentityFromQr("not-base64"))
        assertNull(decodeForwardFromQr("not-base64"))
        assertNull(decodeSnippetFromQr("not-base64"))
    }

    private fun sampleHost(id: String): HostConnection =
        HostConnection(
            id = id,
            name = "prod",
            host = "example.com",
            port = 2222,
            username = "root",
            preferredAuth = AuthMethod.PASSWORD_AND_IDENTITY,
            group = "critical",
            notes = "nightly",
            defaultMode = ConnectionMode.SSH,
            hasPassword = true,
            useMosh = true,
            preferredIdentityId = "identity-1",
            preferredForwardId = "forward-1",
            startupScript = "source ~/.profile",
            backgroundBehavior = BackgroundBehavior.ALWAYS_ALLOW,
            terminalProfileId = "profile-1"
        )
}
