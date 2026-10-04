package com.majordaftapps.sshpeaches.app.ui.help

import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFailureHelpTest {

    private fun help(kind: ConnectionFailureKind?, host: String = "example.com", saved: Boolean = true) =
        connectionFailureHelp(kind = kind, host = host, port = 2222, username = "alice", isSavedHost = saved)

    @Test
    fun everyKindHasATitleAndConcreteTips() {
        (ConnectionFailureKind.entries + listOf(null)).forEach { kind ->
            val help = help(kind)
            assertTrue("$kind title", help.title.isNotBlank())
            assertTrue("$kind tips", help.tips.size in 2..4)
            assertTrue("$kind tips are short", help.tips.all { it.length <= 200 })
        }
    }

    @Test
    fun refusedNamesThePortAndSuggestsCheckingTheServer() {
        val help = help(ConnectionFailureKind.REFUSED)
        assertTrue(help.title.contains("2222"))
        assertTrue(help.tips.any { "systemctl" in it })
    }

    @Test
    fun unreachablePrivateAddressLeadsWithTheVpnExplanation() {
        val help = help(ConnectionFailureKind.UNREACHABLE, host = "192.168.1.20")
        assertTrue(help.tips.first().contains("private address"))
        assertFalse(help(ConnectionFailureKind.UNREACHABLE).tips.any { "private address" in it })
    }

    @Test
    fun loginFailuresOfferIdentitiesAndSavedHostsOfferEditing() {
        assertEquals(
            listOf(FailureHelpAction.EDIT_HOST, FailureHelpAction.OPEN_IDENTITIES),
            help(ConnectionFailureKind.AUTH_PASSWORD).actions
        )
        assertEquals(
            listOf(FailureHelpAction.OPEN_IDENTITIES),
            help(ConnectionFailureKind.AUTH_KEY, saved = false).actions
        )
        assertTrue(help(ConnectionFailureKind.AUTH_KEY).tips.any { "alice" in it })
    }

    @Test
    fun detectsPrivateAndLocalOnlyAddresses() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.10", "100.101.102.103", "169.254.1.1", "nas.local", "pi.lan")
            .forEach { assertTrue(it, isPrivateAddress(it)) }
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "example.com", "192.168.1", "300.1.1.1")
            .forEach { assertFalse(it, isPrivateAddress(it)) }
    }
}
