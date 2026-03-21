package com.majordaftapps.sshpeaches.app.util

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortForwardsTest {

    @Test
    fun normalizeAssociatedHostIds_keepsOnlyFirstDistinctHost() {
        val normalized = normalizeAssociatedHostIds(listOf("", "host-1", "host-1", "host-2"))

        assertEquals(listOf("host-1"), normalized)
    }

    @Test
    fun selectedHostId_returnsNullWhenNoHostIsAssociated() {
        val forward = sampleForward(associatedHosts = emptyList())

        assertNull(forward.selectedHostId())
    }

    @Test
    fun inferredDestinationHost_prefersSelectedHostAddress() {
        val host = sampleHost(id = "host-1", address = "prod.internal")
        val forward = sampleForward(destinationHost = "stale.internal", associatedHosts = listOf(host.id))

        assertEquals("prod.internal", forward.inferredDestinationHost(host))
    }

    @Test
    fun inferredDestinationHost_fallsBackToStoredAddressThenLoopback() {
        val forwardWithStoredHost = sampleForward(destinationHost = "stored.internal")
        val forwardWithoutStoredHost = sampleForward(destinationHost = "")

        assertEquals("stored.internal", forwardWithStoredHost.inferredDestinationHost())
        assertEquals("127.0.0.1", forwardWithoutStoredHost.inferredDestinationHost())
    }

    private fun sampleForward(
        destinationHost: String = "docs.internal",
        associatedHosts: List<String> = listOf("host-1")
    ): PortForward =
        PortForward(
            id = "forward-1",
            label = "Docs Tunnel",
            type = PortForwardType.LOCAL,
            sourceHost = "127.0.0.1",
            sourcePort = 8080,
            destinationHost = destinationHost,
            destinationPort = 443,
            associatedHosts = associatedHosts
        )

    private fun sampleHost(id: String, address: String): HostConnection =
        HostConnection(
            id = id,
            name = "Prod Host",
            host = address,
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
}
