package com.majordaftapps.sshpeaches.app.service

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress

/**
 * The server side of a SOCKS handshake, for dynamic port forwards. Like OpenSSH's -D it speaks
 * SOCKS4, SOCKS4a and SOCKS5 without authentication and only accepts CONNECT.
 *
 * [readRequest] reads the client's request and answers anything it must refuse itself. The caller then
 * opens the connection through the SSH server and reports the outcome with [SocksRequest.reply].
 */
internal object SocksHandshake {
    private const val MAX_NAME_LENGTH = 255

    /** Reads one request. Throws [SocksException] after answering a request it refuses. */
    fun readRequest(input: InputStream, output: OutputStream): SocksRequest =
        when (val version = input.readByte()) {
            4 -> readSocks4(input, output)
            5 -> readSocks5(input, output)
            else -> throw SocksException("Not a SOCKS client (first byte $version)")
        }

    private fun readSocks4(input: InputStream, output: OutputStream): SocksRequest {
        val command = input.readByte()
        val port = input.readPort()
        val address = input.readExactly(4)
        input.readNullTerminated() // user id, unused
        // SOCKS4a: an address of 0.0.0.x (x not 0) means a host name follows.
        val host = if (address[0].toInt() == 0 && address[1].toInt() == 0 && address[2].toInt() == 0 &&
            address[3].toInt() != 0
        ) {
            input.readNullTerminated()
        } else {
            InetAddress.getByAddress(address).hostAddress.orEmpty()
        }
        val request = SocksRequest(version = 4, host = host, port = port, output = output)
        if (command != COMMAND_CONNECT) {
            request.reply(SocksOutcome.REFUSED)
            throw SocksException("Unsupported SOCKS4 command $command")
        }
        request.requireUsable()
        return request
    }

    private fun readSocks5(input: InputStream, output: OutputStream): SocksRequest {
        val methodCount = input.readByte()
        val methods = input.readExactly(methodCount)
        if (METHOD_NO_AUTH.toByte() !in methods) {
            output.write(byteArrayOf(5, 0xFF.toByte()))
            output.flush()
            throw SocksException("The SOCKS5 client requires authentication")
        }
        output.write(byteArrayOf(5, METHOD_NO_AUTH.toByte()))
        output.flush()

        val version = input.readByte()
        if (version != 5) throw SocksException("Bad SOCKS5 request (version $version)")
        val command = input.readByte()
        input.readByte() // reserved
        val addressType = input.readByte()
        val host = when (addressType) {
            ADDRESS_IPV4 -> InetAddress.getByAddress(input.readExactly(4)).hostAddress.orEmpty()
            ADDRESS_IPV6 -> InetAddress.getByAddress(input.readExactly(16)).hostAddress.orEmpty()
            ADDRESS_NAME -> String(input.readExactly(input.readByte()), Charsets.US_ASCII)
            else -> {
                SocksRequest(version = 5, host = "", port = 0, output = output).reply(SocksOutcome.ADDRESS_NOT_SUPPORTED)
                throw SocksException("Unsupported SOCKS5 address type $addressType")
            }
        }
        val port = input.readPort()
        val request = SocksRequest(version = 5, host = host, port = port, output = output)
        if (command != COMMAND_CONNECT) {
            request.reply(SocksOutcome.COMMAND_NOT_SUPPORTED)
            throw SocksException("Unsupported SOCKS5 command $command")
        }
        request.requireUsable()
        return request
    }

    private fun SocksRequest.requireUsable() {
        // The name goes to the SSH server as text: keep it to what a host name or address can contain.
        val valid = host.isNotEmpty() && host.length <= MAX_NAME_LENGTH && port in 1..65535 &&
            host.all { it.isLetterOrDigit() && it.code < 128 || it in ".-_:" }
        if (!valid) {
            reply(SocksOutcome.REFUSED)
            throw SocksException("Invalid SOCKS destination")
        }
    }

    private fun InputStream.readByte(): Int {
        val value = read()
        if (value < 0) throw EOFException("SOCKS client closed the connection")
        return value
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) throw EOFException("SOCKS client closed the connection")
            offset += read
        }
        return bytes
    }

    private fun InputStream.readPort(): Int = (readByte() shl 8) or readByte()

    private fun InputStream.readNullTerminated(): String {
        val builder = StringBuilder()
        while (true) {
            val value = readByte()
            if (value == 0) return builder.toString()
            if (builder.length >= MAX_NAME_LENGTH) throw SocksException("SOCKS4 field too long")
            builder.append(value.toChar())
        }
    }

    private const val COMMAND_CONNECT = 1
    private const val METHOD_NO_AUTH = 0
    private const val ADDRESS_IPV4 = 1
    private const val ADDRESS_NAME = 3
    private const val ADDRESS_IPV6 = 4
}

internal class SocksException(message: String) : IOException(message)

internal enum class SocksOutcome(val socks5Code: Int) {
    CONNECTED(0),
    FAILED(1),
    NOT_ALLOWED(2),
    REFUSED(5),
    COMMAND_NOT_SUPPORTED(7),
    ADDRESS_NOT_SUPPORTED(8)
}

/** A CONNECT request for [host]:[port]; [host] is a name or a literal address. */
internal class SocksRequest(
    val version: Int,
    val host: String,
    val port: Int,
    private val output: OutputStream
) {
    /** Tells the client how its request ended. Send exactly one reply per request. */
    fun reply(outcome: SocksOutcome) {
        val bytes = if (version == 4) {
            byteArrayOf(0, if (outcome == SocksOutcome.CONNECTED) 0x5A else 0x5B, 0, 0, 0, 0, 0, 0)
        } else {
            // The bound address is not known on this side of the tunnel; like OpenSSH, report 0.0.0.0:0.
            byteArrayOf(5, outcome.socks5Code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0)
        }
        output.write(bytes)
        output.flush()
    }
}
