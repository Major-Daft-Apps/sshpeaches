package com.majordaftapps.sshpeaches.app.transfer

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Moves an export payload between two phones on the same local network.
 *
 * The sender serves the payload once, on its LAN address only, and refuses peers that aren't on a
 * private network. The payload travels AES-256-GCM encrypted with a key derived (PBKDF2) from a
 * 12-character pairing code that is shown on the sender and typed or scanned on the receiver; the
 * code itself never crosses the network.
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

    fun decrypt(frame: ByteArray, code: String): String {
        if (frame.size < MAGIC.size + SALT_BYTES + IV_BYTES + 16 || !frame.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("That device didn't send an SSHPeaches transfer.")
        }
        var offset = MAGIC.size
        val salt = frame.copyOfRange(offset, offset + SALT_BYTES).also { offset += SALT_BYTES }
        val iv = frame.copyOfRange(offset, offset + IV_BYTES).also { offset += IV_BYTES }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(code, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val plain = try {
            cipher.doFinal(frame, offset, frame.size - offset)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw WrongCodeException()
        }
        return String(plain, Charsets.UTF_8)
    }

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
        private val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(bindAddress, 0), 1)
        }
        val host: String = bindAddress.hostAddress.orEmpty()
        val port: Int get() = server.localPort

        /** @return the address that received the payload */
        fun awaitTransfer(timeoutMs: Int): InetAddress {
            server.soTimeout = timeoutMs
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) throw SocketTimeoutException("Nobody connected in time.")
                server.soTimeout = remaining
                val socket = server.accept()
                socket.use {
                    val peer = it.inetAddress
                    if (!allowPeer(peer)) return@use
                    it.soTimeout = 30_000
                    DataOutputStream(it.getOutputStream()).apply {
                        writeInt(frame.size)
                        write(frame)
                        flush()
                    }
                    // Wait for the receiver to finish reading (it closes), so the payload isn't cut off.
                    runCatching { it.getInputStream().read() }
                    return peer
                }
            }
        }

        override fun close() {
            runCatching { server.close() }
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
            socket.soTimeout = timeoutMs
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            if (size <= 0 || size > MAX_FRAME_BYTES) throw IOException("That device sent an invalid transfer.")
            val frame = ByteArray(size)
            input.readFully(frame)
            return decrypt(frame, code)
        }
    }
}
