package com.majordaftapps.sshpeaches.app.qr

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.majordaftapps.sshpeaches.app.ui.components.encodeHostPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeIdentityPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodePortForwardPayload
import com.majordaftapps.sshpeaches.app.ui.components.encodeSnippetPayload
import com.majordaftapps.sshpeaches.app.ui.components.processHostQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processIdentityQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processPortForwardQrImport
import com.majordaftapps.sshpeaches.app.ui.components.processSnippetQrImport
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrRoundTripTest {

    @Test
    fun hostPayloadRoundTripPreservesConnectionFields() {
        val host = HostConnection(
            id = "host-qr",
            name = "QR Host",
            host = "192.168.1.50",
            port = 2222,
            username = "qr-user",
            preferredAuth = AuthMethod.IDENTITY,
            defaultMode = ConnectionMode.SFTP,
            group = "Lab",
            notes = "round-trip",
            hasPassword = true,
            useMosh = true,
            preferredIdentityId = "identity-1",
            preferredForwardId = "forward-1",
            startupScript = "echo ready",
            backgroundBehavior = BackgroundBehavior.ALWAYS_STOP,
            terminalProfileId = "profile-1"
        )

        val encoded = encodeHostPayload(host, encryptedPasswordPayload = "encrypted-pwd")
        val imported = processHostQrImport(
            contents = encoded,
            existingHosts = emptyList()
        ) { "generated-host" }

        check(imported is HostQrImportResult.Ready) {
            "Expected host QR import to succeed"
        }
        check(imported.data.targetId == "host-qr")
        check(imported.data.encryptedPasswordPayload == "encrypted-pwd")
        check(imported.data.host.name == host.name)
        check(imported.data.host.host == host.host)
        check(imported.data.host.port == host.port)
        check(imported.data.host.username == host.username)
        check(imported.data.host.preferredAuth == host.preferredAuth)
        check(imported.data.host.defaultMode == host.defaultMode)
        check(imported.data.host.group == host.group)
        check(imported.data.host.notes == host.notes)
        check(imported.data.host.useMosh == host.useMosh)
        check(imported.data.host.preferredIdentityId == host.preferredIdentityId)
        check(imported.data.host.preferredForwardId == host.preferredForwardId)
        check(imported.data.host.startupScript == host.startupScript)
        check(imported.data.host.backgroundBehavior == host.backgroundBehavior)
        check(imported.data.host.terminalProfileId == host.terminalProfileId)
    }

    @Test
    fun identityPayloadRoundTripRequestsOverwriteForExistingFingerprint() {
        val existing = Identity(
            id = "existing-id",
            label = "Existing",
            fingerprint = "SHA256:roundtrip",
            username = "old-user",
            createdEpochMillis = 1L,
            hasPrivateKey = false
        )
        val incoming = Identity(
            id = "incoming-id",
            label = "Imported Identity",
            fingerprint = existing.fingerprint,
            username = "new-user",
            createdEpochMillis = 2L,
            hasPrivateKey = true
        )

        val encoded = encodeIdentityPayload(incoming, encryptedKeyPayload = "encrypted-key")
        val imported = processIdentityQrImport(
            contents = encoded,
            existingIdentities = listOf(existing)
        )

        check(imported is IdentityQrImportResult.NeedsOverwrite) {
            "Expected identity QR import to request overwrite"
        }
        check(imported.overwrite.targetId == existing.id)
        check(imported.overwrite.label == incoming.label)
        check(imported.overwrite.username == incoming.username)
        check(imported.overwrite.encryptedKeyPayload == "encrypted-key")
    }

    @Test
    fun portForwardPayloadRoundTripProducesImportableLocalForward() {
        val forward = PortForward(
            id = "forward-qr",
            label = "Docs Tunnel",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 8080,
            destinationHost = "docs.internal",
            destinationPort = 443
        )

        val encoded = encodePortForwardPayload(forward)
        val imported = processPortForwardQrImport(encoded)

        check(imported is PortForwardQrImportResult.Ready) {
            "Expected port forward QR import to succeed"
        }
        check(imported.forward.label == forward.label)
        check(imported.forward.type == forward.type)
        check(imported.forward.sourceHost == forward.sourceHost)
        check(imported.forward.sourcePort == forward.sourcePort)
        check(imported.forward.destinationHost == forward.destinationHost)
        check(imported.forward.destinationPort == forward.destinationPort)
    }

    @Test
    fun snippetPayloadRoundTripProducesRunnableSnippet() {
        val snippet = Snippet(
            id = "snippet-qr",
            title = "Disk Usage",
            description = "Check home usage",
            command = "du -sh ~"
        )

        val encoded = encodeSnippetPayload(snippet)
        val imported = processSnippetQrImport(encoded)

        check(imported is SnippetQrImportResult.Ready) {
            "Expected snippet QR import to succeed"
        }
        check(imported.data.title == snippet.title)
        check(imported.data.description == snippet.description)
        check(imported.data.command == snippet.command)
    }
}
