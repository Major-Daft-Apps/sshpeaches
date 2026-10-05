package com.majordaftapps.sshpeaches.app.transfer

import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LanTransferTest {

    private val loopback = InetAddress.getLoopbackAddress()
    private val allowLoopback: (InetAddress) -> Boolean = { it.isLoopbackAddress }

    @Test
    fun receiverGetsThePayloadWithTheRightCode() {
        val payload = "{\"hosts\":[{\"name\":\"web\"}],\"emoji\":\"🍑\"}"
        LanTransfer.Sender(payload, loopback, allowPeer = allowLoopback).use { sender ->
            val pool = Executors.newSingleThreadExecutor()
            val sent = pool.submit<InetAddress> { sender.awaitTransfer(10_000) }
            val received = LanTransfer.receive(sender.host, sender.port, sender.code, allowPeer = allowLoopback)
            assertEquals(payload, received)
            assertTrue(sent.get(10, TimeUnit.SECONDS).isLoopbackAddress)
            pool.shutdown()
        }
    }

    @Test
    fun aWrongCodeIsReportedAsSuch() {
        LanTransfer.Sender("secret hosts", loopback, allowPeer = allowLoopback).use { sender ->
            val pool = Executors.newSingleThreadExecutor()
            pool.submit { runCatching { sender.awaitTransfer(10_000) } }
            val wrong = generateSequence { LanTransfer.newCode() }.first { it != sender.code }
            try {
                LanTransfer.receive(sender.host, sender.port, wrong, allowPeer = allowLoopback)
                fail("wrong code decrypted")
            } catch (_: LanTransfer.WrongCodeException) {
            }
            pool.shutdownNow()
        }
    }

    @Test
    fun receiverRefusesAddressesOutsideTheLocalNetwork() {
        try {
            LanTransfer.receive("8.8.8.8", 4444, LanTransfer.newCode())
            fail("connected to a public address")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("local network"))
        }
    }

    @Test
    fun onlyPrivateAndLinkLocalAddressesCountAsLocal() {
        listOf("192.168.1.20", "10.0.0.5", "172.20.1.1", "169.254.3.4", "fe80::1").forEach {
            assertTrue(it, LanTransfer.isLocalNetworkAddress(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "100.100.1.1", "127.0.0.1", "2001:4860::8888").forEach {
            assertFalse(it, LanTransfer.isLocalNetworkAddress(InetAddress.getByName(it)))
        }
    }

    @Test
    fun codesNormalizeAndPairingUrisRoundTrip() {
        val code = LanTransfer.newCode()
        assertEquals(12, code.length)
        assertEquals(code, LanTransfer.normalizeCode(LanTransfer.formatCode(code).lowercase()))
        assertEquals("0123456789AB", LanTransfer.normalizeCode("o123-4567-89ab"))
        assertEquals("1111ABCDEFGH", LanTransfer.normalizeCode("ILl1 abcd efgh"))
        assertNull(LanTransfer.normalizeCode("too-short"))
        assertNull(LanTransfer.normalizeCode("UUUUUUUUUUUU"))

        val uri = LanTransfer.pairingUri("192.168.1.20", 40123, code)
        assertEquals(LanTransfer.Pairing("192.168.1.20", 40123, code), LanTransfer.parsePairingUri(uri))
        assertNull(LanTransfer.parsePairingUri("https://example.com"))
        assertNull(LanTransfer.parsePairingUri("sshpeaches-lan://192.168.1.20:99999/$code"))
    }
}
