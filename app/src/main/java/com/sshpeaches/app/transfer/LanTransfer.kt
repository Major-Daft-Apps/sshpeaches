package com.majordaftapps.sshpeaches.app.transfer

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Moves an export payload between two phones on the same local network.
 *
 * The sender serves the payload on its LAN address only, and refuses peers that aren't on a
 * private network. The payload travels AES-256-GCM encrypted with a key derived (PBKDF2) from a
 * 12-character pairing code that is shown on the sender and typed or scanned on the receiver; the
 * code itself never crosses the network. A receiver that decrypted the payload answers with a receipt
 * only the code's holder can make; the sender keeps serving until it gets one, so another device on
 * the network can't end the transfer by connecting first.
 */
object LanTransfer {
    const val SERVICE_TYPE = "_sshpeaches._tcp."
    const val URI_SCHEME = "sshpeaches-lan"
    const val CODE_LENGTH = 12
    private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford base32
    private val MAGIC = "SSHPEACHES-LAN1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_ITERATIONS = 150_000
    private const val MAX_FRAME_BYTES = 32 * 1024 * 1024
    private const val RECEIPT_BYTES = 32
    private const val RECEIPT_TIMEOUT_MS = 10_000
    private val RECEIPT_LABEL = "SSHPEACHES-LAN1 receipt\n".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    class WrongCodeException : IOException("The code doesn't match the one shown on the other phone.")

    fun newCode(): String = buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
    }

    /** "K7QM2X9DHT4P" -> "K7QM-2X9D-HT4P" */
    fun formatCode(code: String): String = code.chunked(4).joinToString("-")

    /** Accepts dashes, spaces, lower case, and the usual misreadings (O for 0, I/L for 1). */
    fun normalizeCode(input: String): String? {
        val code = input.uppercase()
            .filter { it.isLetterOrDigit() }
            .map { c -> when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c } }
            .joinToString("")
        return code.takeIf { it.length == CODE_LENGTH && it.all { c -> c in CODE_ALPHABET } }
    }

    fun pairingUri(host: String, port: Int, code: String): String = "$URI_SCHEME://$host:$port/$code"

    data class Pairing(val host: String, val port: Int, val code: String)

    fun parsePairingUri(text: String): Pairing? {
        val match = Regex("^$URI_SCHEME://([^/:]+):(\\d{1,5})/([A-Za-z0-9-]+)$").find(text.trim()) ?: return null
        val (host, port, code) = match.destructured
        return Pairing(host, port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null, normalizeCode(code) ?: return null)
    }

    /** Private (RFC 1918) and link-local addresses: the "same network" a transfer may use. */
    fun isLocalNetworkAddress(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLinkLocalAddress

    /** This device's IPv4 address on Wi-Fi or Ethernet, if it has a private one. */
    fun localNetworkAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { iface -> if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrNull()

    fun encrypt(payload: String, code: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(code, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + sealed
    }

    fun decrypt(frame: ByteArray, code: String): String = decryptWithKey(frame, code).first

    /** The payload and the key it was sealed with (for the receipt). */
    private fun decryptWithKey(frame: ByteArray, code: String): Pair<String, SecretKeySpec> {
        if (frame.size < MAGIC.size + SALT_BYTES + IV_BYTES + 16 || !frame.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("That device didn't send an SSHPeaches transfer.")
        }
        var offset = MAGIC.size
        val salt = frame.copyOfRange(offset, offset + SALT_BYTES).also { offset += SALT_BYTES }
        val iv = frame.copyOfRange(offset, offset + IV_BYTES).also { offset += IV_BYTES }
        val key = deriveKey(code, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val plain = try {
            cipher.doFinal(frame, offset, frame.size - offset)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw WrongCodeException()
        }
        return String(plain, Charsets.UTF_8) to key
    }

    /** Proof that a receiver decrypted [frame]: an HMAC over it with the frame's key. */
    private fun receipt(frame: ByteArray, key: SecretKeySpec): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.encoded, "HmacSHA256"))
        mac.update(RECEIPT_LABEL)
        return mac.doFinal(frame)
    }

    private fun frameKey(frame: ByteArray, code: String): SecretKeySpec =
        deriveKey(code, frame.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES))

    private fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, KEY_ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Serves one encrypted payload on [bindAddress]. [awaitTransfer] blocks until a local peer has
     * received it. [allowPeer] guards who may connect (local network only, by default).
     */
    class Sender(
        payload: String,
        bindAddress: InetAddress,
        val code: String = newCode(),
        private val allowPeer: (InetAddress) -> Boolean = ::isLocalNetworkAddress
    ) : Closeable {
        private val frame = encrypt(payload, code)
        private val expectedReceipt = receipt(frame, frameKey(frame, code))
        private val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(bindAddress, 0), 1)
        }
        val host: String = bindAddress.hostAddress.orEmpty()
        val port: Int get() = server.localPort

        // Closed by close() as well: a peer that connects and never reads leaves write() blocked, and a socket
        // timeout does not cover writes.
        @Volatile
        private var activePeer: Socket? = null

        /** @return the address that received (and decrypted) the payload */
        fun awaitTransfer(timeoutMs: Int): InetAddress {
            server.soTimeout = timeoutMs
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) throw SocketTimeoutException("Nobody connected in time.")
                server.soTimeout = remaining
                val socket = server.accept()
                activePeer = socket
                socket.use {
                    val peer = it.inetAddress
                    if (!allowPeer(peer)) return@use
                    it.soTimeout = 30_000
                    DataOutputStream(it.getOutputStream()).apply {
                        writeInt(frame.size)
                        write(frame)
                        flush()
                    }
                    // Only a receiver that decrypted the payload can answer with the receipt. Anyone else (a
                    // scanner, a device that guessed wrong, an older app that just closes) gets ciphertext, and the
                    // payload stays on offer for the real receiver.
                    // Decrypting takes the receiver a second or two; don't let a silent peer hold the sender long.
                    it.soTimeout = RECEIPT_TIMEOUT_MS
                    val answer = runCatching { readUpTo(it.getInputStream(), RECEIPT_BYTES) }.getOrNull()
                    if (answer != null && java.security.MessageDigest.isEqual(answer, expectedReceipt)) return peer
                }
            }
        }

        override fun close() {
            runCatching { server.close() }
            runCatching { activePeer?.close() }
        }
    }

    /** Fetches and decrypts a payload from a sender on the local network. */
    fun receive(
        host: String,
        port: Int,
        code: String,
        timeoutMs: Int = 15_000,
        allowPeer: (InetAddress) -> Boolean = ::isLocalNetworkAddress
    ): String {
        val address = InetAddress.getByName(host)
        if (!allowPeer(address)) throw IOException("$host isn't on your local network.")
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            // One deadline for the whole frame: the socket timeout alone applies per read, so a sender dripping a
            // byte at a time could hold the receive (and block new ones) indefinitely.
            val deadline = System.currentTimeMillis() + 2L * timeoutMs
            val input = socket.getInputStream()
            val header = ByteArray(4)
            readFullyBefore(socket, input, header, deadline)
            val size = DataInputStream(header.inputStream()).readInt()
            if (size <= 0 || size > MAX_FRAME_BYTES) throw IOException("That device sent an invalid transfer.")
            val frame = ByteArray(size)
            readFullyBefore(socket, input, frame, deadline)
            val (payload, key) = decryptWithKey(frame, code)
            runCatching { socket.getOutputStream().apply { write(receipt(frame, key)); flush() } }
            return payload
        }
    }

    /** Up to [count] bytes, fewer if the peer closes first. */
    private fun readUpTo(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) break
            offset += read
        }
        return buffer.copyOf(offset)
    }

    private fun readFullyBefore(socket: Socket, input: InputStream, buffer: ByteArray, deadline: Long) {
        var offset = 0
        while (offset < buffer.size) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw SocketTimeoutException("The transfer took too long.")
            socket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw EOFException("The other device closed the connection.")
            offset += read
        }
    }
}
