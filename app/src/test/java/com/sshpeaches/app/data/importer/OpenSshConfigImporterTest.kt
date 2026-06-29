package com.majordaftapps.sshpeaches.app.data.importer

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSshConfigImporterTest {

    @Test
    fun parse_importsConcreteHostWithIdentityNote() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                # shared defaults
                Host *
                  User ubuntu
                  IdentityFile ~/.ssh/id_ed25519

                Host prod
                  HostName prod.example.com
                  Port 2202
            """.trimIndent(),
            nowProvider = { 100L },
            idProvider = sequentialIds()
        )

        assertEquals(1, result.hosts.size)
        val imported = result.hosts.single()
        assertEquals("prod", imported.host.name)
        assertEquals("prod.example.com", imported.host.host)
        assertEquals(2202, imported.host.port)
        assertEquals("ubuntu", imported.host.username)
        assertEquals(AuthMethod.PASSWORD, imported.host.preferredAuth)
        assertEquals("OpenSSH", imported.host.group)
        assertEquals(listOf("~/.ssh/id_ed25519"), imported.identityFiles)
        assertTrue(imported.host.notes.contains("IdentityFile"))
    }

    @Test
    fun parse_supportsEqualsAndQuotedAliases() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                User default-user
                Host "qa host"
                    HostName=qa.internal
            """.trimIndent(),
            idProvider = sequentialIds()
        )

        assertEquals(1, result.hosts.size)
        assertEquals("qa host", result.hosts.single().host.name)
        assertEquals("qa.internal", result.hosts.single().host.host)
        assertEquals("default-user", result.hosts.single().host.username)
    }

    @Test
    fun parse_skipsWildcardAndMissingUserEntries() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host *.internal
                  User deploy

                Host no-user
                  HostName no-user.example.com
            """.trimIndent(),
            idProvider = sequentialIds()
        )

        assertTrue(result.hosts.isEmpty())
        assertTrue(result.warnings.any { it.contains("missing User") })
    }

    @Test
    fun parse_importsLocalForwardAndAssociatesHost() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host prod
                  HostName prod.example.com
                  User root
                  LocalForward 127.0.0.1:15432 db.internal:5432
                  LocalForward 8080 localhost:80
            """.trimIndent(),
            nowProvider = { 200L },
            idProvider = sequentialIds()
        )

        assertEquals(1, result.hosts.size)
        assertEquals(2, result.localForwards.size)
        val hostId = result.hosts.single().host.id
        val first = result.localForwards.first().forward
        assertEquals("127.0.0.1", first.sourceHost)
        assertEquals(15432, first.sourcePort)
        assertEquals("db.internal", first.destinationHost)
        assertEquals(5432, first.destinationPort)
        assertEquals(listOf(hostId), first.associatedHosts)
        val second = result.localForwards[1].forward
        assertEquals("127.0.0.1", second.sourceHost)
        assertEquals(8080, second.sourcePort)
        assertEquals("localhost", second.destinationHost)
        assertEquals(80, second.destinationPort)
    }

    @Test
    fun parse_skipsExistingDuplicates() {
        val existingHost = HostConnection(
            id = "existing-host",
            name = "prod",
            host = "prod.example.com",
            username = "root",
            preferredAuth = AuthMethod.PASSWORD
        )
        val existingForward = PortForward(
            id = "existing-forward",
            label = "existing",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 8080,
            destinationHost = "localhost",
            destinationPort = 80
        )

        val result = OpenSshConfigImporter.parse(
            contents = """
                Host prod staging
                  User root
                  LocalForward 8080 localhost:80
            """.trimIndent(),
            existingHosts = listOf(existingHost),
            existingPortForwards = listOf(existingForward),
            idProvider = sequentialIds()
        )

        assertEquals(listOf("staging"), result.hosts.map { it.host.name })
        assertTrue(result.localForwards.isEmpty())
        assertTrue(result.warnings.any { it.contains("host name already exists") })
        assertTrue(result.warnings.any { it.contains("duplicate LocalForward") })
    }

    private fun sequentialIds(): () -> String {
        var next = 1
        return { "id-${next++}" }
    }
}
