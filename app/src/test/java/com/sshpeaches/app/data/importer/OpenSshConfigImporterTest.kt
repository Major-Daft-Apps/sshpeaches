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
    fun parse_importsRemoteAndDynamicForwardsOnLoopbackAndSwitchedOff() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host prod
                  HostName prod.example.com
                  User root
                  RemoteForward *:9000 192.168.1.10:80
                  DynamicForward 0.0.0.0:1080
                  DynamicForward 1081 extra
                  RemoteForward 9001
            """.trimIndent(),
            nowProvider = { 200L },
            idProvider = sequentialIds()
        )

        val forwards = result.localForwards.map { it.forward }
        assertEquals(2, forwards.size)
        val remote = forwards.single { it.type == PortForwardType.REMOTE }
        assertEquals("127.0.0.1", remote.sourceHost)
        assertEquals(9000, remote.sourcePort)
        assertEquals("192.168.1.10", remote.destinationHost)
        assertEquals(80, remote.destinationPort)
        val dynamic = forwards.single { it.type == PortForwardType.DYNAMIC }
        assertEquals("127.0.0.1", dynamic.sourceHost)
        assertEquals(1080, dynamic.sourcePort)
        assertEquals("", dynamic.destinationHost)
        assertEquals(0, dynamic.destinationPort)
        assertTrue(forwards.none { it.enabled })
        assertTrue(result.warnings.any { it.contains("Skipped DynamicForward") })
        assertTrue(result.warnings.any { it.contains("Skipped RemoteForward") })
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

    @Test
    fun parse_proxyJumpBecomesTheJumpHost() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host bastion
                  HostName bastion.example.com
                  User gate
                Host db
                  HostName 10.0.0.5
                  User postgres
                  ProxyJump bastion
                Host direct
                  HostName direct.example.com
                  User me
                  ProxyJump none
            """.trimIndent(),
            idProvider = sequentialIds()
        )
        val byName = result.hosts.associate { it.alias to it.host }
        assertEquals(byName.getValue("bastion").id, byName.getValue("db").jumpHostId)
        assertEquals(null, byName.getValue("bastion").jumpHostId)
        assertEquals(null, byName.getValue("direct").jumpHostId)
        assertTrue(result.warnings.none { it.contains("ProxyJump") })
    }

    @Test
    fun parse_proxyJumpChainGoesThroughEachHop() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host outer
                  User a
                Host middle
                  User b
                Host inside
                  User c
                  ProxyJump outer,middle
            """.trimIndent(),
            idProvider = sequentialIds()
        )
        val byName = result.hosts.associate { it.alias to it.host }
        assertEquals(byName.getValue("middle").id, byName.getValue("inside").jumpHostId)
        assertEquals(byName.getValue("outer").id, byName.getValue("middle").jumpHostId)
        assertEquals(null, byName.getValue("outer").jumpHostId)
    }

    @Test
    fun parse_proxyJumpChainWhoseHopHasTheSameRouteOfItsOwnGivesNoWarning() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host inside
                  User c
                  ProxyJump outer,middle
                Host middle
                  User b
                  ProxyJump outer
                Host outer
                  User a
            """.trimIndent(),
            idProvider = sequentialIds()
        )
        val byName = result.hosts.associate { it.alias to it.host }
        assertEquals(byName.getValue("middle").id, byName.getValue("inside").jumpHostId)
        assertEquals(byName.getValue("outer").id, byName.getValue("middle").jumpHostId)
        assertTrue(result.warnings.toString(), result.warnings.none { it.contains("ProxyJump") })
    }

    @Test
    fun parse_proxyJumpChainThroughASavedHostSaysThatTheChainDiffers() {
        val saved = HostConnection(id = "saved-mid", name = "middle", host = "m.example", username = "g", preferredAuth = AuthMethod.PASSWORD)
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host outer
                  User a
                Host inside
                  User c
                  ProxyJump outer,middle
            """.trimIndent(),
            existingHosts = listOf(saved),
            idProvider = sequentialIds()
        )
        val byName = result.hosts.associate { it.alias to it.host }
        assertEquals("saved-mid", byName.getValue("inside").jumpHostId)
        assertTrue(result.warnings.toString(), result.warnings.any { it.contains("ProxyJump outer,middle for inside") && it.contains("differs") })
    }

    @Test
    fun parse_proxyJumpFindsASavedHostByName() {
        val saved = HostConnection(id = "saved-1", name = "Bastion", host = "b.example", username = "g", preferredAuth = AuthMethod.PASSWORD)
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host db
                  User postgres
                  ProxyJump bastion
            """.trimIndent(),
            existingHosts = listOf(saved),
            idProvider = sequentialIds()
        )
        assertEquals("saved-1", result.hosts.single().host.jumpHostId)
    }

    @Test
    fun parse_proxyJumpToAnAddressOrALoopIsLeftOutWithANote() {
        val result = OpenSshConfigImporter.parse(
            contents = """
                Host db
                  User postgres
                  ProxyJump gate@203.0.113.9:2222
                Host a
                  User x
                  ProxyJump b
                Host b
                  User y
                  ProxyJump a
            """.trimIndent(),
            idProvider = sequentialIds()
        )
        val byName = result.hosts.associate { it.alias to it.host }
        assertEquals(null, byName.getValue("db").jumpHostId)
        assertTrue(result.warnings.any { it.contains("ProxyJump gate@203.0.113.9:2222 for db was not imported") })
        // a -> b is taken; b -> a would close the loop and is refused.
        assertEquals(byName.getValue("b").id, byName.getValue("a").jumpHostId)
        assertEquals(null, byName.getValue("b").jumpHostId)
        assertTrue(result.warnings.any { it.contains("for b was not imported") })
    }

    private fun sequentialIds(): () -> String {
        var next = 1
        return { "id-${next++}" }
    }
}
