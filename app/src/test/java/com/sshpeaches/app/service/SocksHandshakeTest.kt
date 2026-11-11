package com.majordaftapps.sshpeaches.app.service

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocksHandshakeTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun read(input: ByteArray, output: ByteArrayOutputStream = ByteArrayOutputStream()): SocksRequest =
        SocksHandshake.readRequest(ByteArrayInputStream(input), output)

    @Test
    fun socks5_hostNameIsPassedOnUnresolved() {
        val output = ByteArrayOutputStream()
        val name = "example.com".toByteArray()
        val request = read(
            bytes(5, 2, 2, 0) + bytes(5, 1, 0, 3, name.size) + name + bytes(0x01, 0xBB),
            output
        )

        assertEquals(5, request.version)
        assertEquals("example.com", request.host)
        assertEquals(443, request.port)
        assertArrayEquals("no-auth chosen", bytes(5, 0), output.toByteArray())

        request.reply(SocksOutcome.CONNECTED)
        assertArrayEquals(bytes(5, 0, 5, 0, 0, 1, 0, 0, 0, 0, 0, 0), output.toByteArray())
    }

    @Test
    fun socks5_ipv4AndIpv6Addresses() {
        val v4 = read(bytes(5, 1, 0) + bytes(5, 1, 0, 1, 10, 0, 0, 7, 0, 22))
        assertEquals("10.0.0.7", v4.host)
        assertEquals(22, v4.port)

        val v6 = read(bytes(5, 1, 0) + bytes(5, 1, 0, 4) + ByteArray(15) + bytes(1) + bytes(0x1F, 0x90))
        assertEquals("0:0:0:0:0:0:0:1", v6.host)
        assertEquals(8080, v6.port)
    }

    @Test
    fun socks5_clientThatRequiresAuthenticationIsRefused() {
        val output = ByteArrayOutputStream()

        assertThrows(SocksException::class.java) { read(bytes(5, 1, 2), output) }
        assertArrayEquals(bytes(5, 0xFF), output.toByteArray())
    }

    @Test
    fun socks5_bindAndUdpAreRefusedWithCommandNotSupported() {
        for (command in listOf(2, 3)) {
            val output = ByteArrayOutputStream()

            assertThrows(SocksException::class.java) {
                read(bytes(5, 1, 0) + bytes(5, command, 0, 1, 1, 2, 3, 4, 0, 80), output)
            }
            assertArrayEquals(bytes(5, 0) + bytes(5, 7, 0, 1, 0, 0, 0, 0, 0, 0), output.toByteArray())
        }
    }

    @Test
    fun socks5_unknownAddressTypeIsRefused() {
        val output = ByteArrayOutputStream()

        assertThrows(SocksException::class.java) { read(bytes(5, 1, 0) + bytes(5, 1, 0, 9, 1, 2), output) }
        assertArrayEquals(bytes(5, 0) + bytes(5, 8, 0, 1, 0, 0, 0, 0, 0, 0), output.toByteArray())
    }

    @Test
    fun socks5_namesWithControlOrShellCharactersAreRefused() {
        for (bad in listOf("a b", "a\nb", "a;b", "h\u0000x", "é.example")) {
            val name = bad.toByteArray(Charsets.ISO_8859_1)
            val output = ByteArrayOutputStream()

            assertThrows(bad, SocksException::class.java) {
                read(bytes(5, 1, 0) + bytes(5, 1, 0, 3, name.size) + name + bytes(0, 80), output)
            }
            assertArrayEquals(bytes(5, 0) + bytes(5, 5, 0, 1, 0, 0, 0, 0, 0, 0), output.toByteArray())
        }
    }

    @Test
    fun socks5_emptyNameAndPortZeroAreRefused() {
        assertThrows(SocksException::class.java) { read(bytes(5, 1, 0) + bytes(5, 1, 0, 3, 0, 0, 80)) }
        assertThrows(SocksException::class.java) { read(bytes(5, 1, 0) + bytes(5, 1, 0, 1, 1, 2, 3, 4, 0, 0)) }
    }

    @Test
    fun socks4_address() {
        val output = ByteArrayOutputStream()
        val request = read(bytes(4, 1, 0, 80, 192, 168, 1, 5) + "user".toByteArray() + bytes(0), output)

        assertEquals(4, request.version)
        assertEquals("192.168.1.5", request.host)
        assertEquals(80, request.port)
        assertEquals("nothing is sent before the outcome", 0, output.size())

        request.reply(SocksOutcome.CONNECTED)
        assertArrayEquals(bytes(0, 0x5A, 0, 0, 0, 0, 0, 0), output.toByteArray())
    }

    @Test
    fun socks4a_hostName() {
        val output = ByteArrayOutputStream()
        val request = read(
            bytes(4, 1, 0x01, 0xBB, 0, 0, 0, 1) + bytes(0) + "example.org".toByteArray() + bytes(0),
            output
        )

        assertEquals("example.org", request.host)
        assertEquals(443, request.port)

        request.reply(SocksOutcome.REFUSED)
        assertArrayEquals(bytes(0, 0x5B, 0, 0, 0, 0, 0, 0), output.toByteArray())
    }

    @Test
    fun socks4_bindIsRefused() {
        val output = ByteArrayOutputStream()

        assertThrows(SocksException::class.java) { read(bytes(4, 2, 0, 80, 1, 2, 3, 4, 0), output) }
        assertArrayEquals(bytes(0, 0x5B, 0, 0, 0, 0, 0, 0), output.toByteArray())
    }

    @Test
    fun socks4_overlongFieldsAreRefused() {
        val long = ByteArray(300) { 'a'.code.toByte() }

        assertThrows(SocksException::class.java) { read(bytes(4, 1, 0, 80, 1, 2, 3, 4) + long + bytes(0)) }
        assertThrows(SocksException::class.java) {
            read(bytes(4, 1, 0, 80, 0, 0, 0, 1) + bytes(0) + long + bytes(0))
        }
    }

    @Test
    fun otherProtocolsAndTruncatedRequests() {
        assertThrows(SocksException::class.java) { read("GET / HTTP/1.1\r\n".toByteArray()) }
        assertThrows(EOFException::class.java) { read(bytes()) }
        assertThrows(EOFException::class.java) { read(bytes(5, 1, 0, 5, 1, 0, 3, 20, 'a'.code)) }
        assertThrows(EOFException::class.java) { read(bytes(4, 1, 0, 80, 1, 2, 3, 4, 'u'.code)) }
    }

    @Test
    fun everyOutcomeHasItsSocks5Code() {
        val expected = mapOf(
            SocksOutcome.CONNECTED to 0,
            SocksOutcome.FAILED to 1,
            SocksOutcome.NOT_ALLOWED to 2,
            SocksOutcome.REFUSED to 5,
            SocksOutcome.COMMAND_NOT_SUPPORTED to 7,
            SocksOutcome.ADDRESS_NOT_SUPPORTED to 8
        )
        for ((outcome, code) in expected) {
            val output = ByteArrayOutputStream()
            SocksRequest(version = 5, host = "h", port = 1, output = output).reply(outcome)
            assertEquals(code, output.toByteArray()[1].toInt())
        }
    }
}
