package com.majordaftapps.sshpeaches.app.data.ssh

import com.hierynomus.sshj.key.KeyAlgorithm
import com.hierynomus.sshj.key.KeyAlgorithms
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicInteger
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.Message
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.OpenFailException
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.forwarded.AbstractForwardedChannel
import net.schmizz.sshj.connection.channel.forwarded.AbstractForwardedChannelOpener
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener

/**
 * SSH agent forwarding (OpenSSH's `ssh -A`): while a session is open, programs on the server can ask this phone to
 * sign with the user's keys, for example to log in to another server or to a Git host from there. The keys never
 * leave the phone; the server only ever gets signatures.
 *
 * The server asks by opening an `auth-agent@openssh.com` channel back to the client, which is served here with the
 * two requests of the agent protocol that matter: list the keys, and sign with one of them. Everything else (adding
 * and removing keys, locking) is refused. The channel type is only accepted while [attach]ed, so a server can't use
 * the keys of a host that doesn't have agent forwarding on.
 */
class ForwardedAgent(
    private val client: SSHClient,
    /** The keys to offer, loaded when the server first asks. */
    private val loadKeys: () -> List<Key>,
    /** Called with a key's label each time the server has it sign something. */
    private val onSigned: (String) -> Unit = {}
) {
    class Key(val label: String, val publicKey: PublicKey, val privateKey: PrivateKey) {
        /** Worked out here, so that a key this can't encode fails while loading and is left out on its own. */
        val blob: ByteArray = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
    }

    @Volatile
    private var loadedKeys: List<Key>? = null

    /** Loaded on first use and kept; a load that failed is tried again on the next request. */
    private val keys: List<Key>
        get() = loadedKeys ?: synchronized(this) {
            loadedKeys ?: runCatching(loadKeys).getOrNull()?.also { loadedKeys = it } ?: emptyList()
        }

    private val openChannels = AtomicInteger(0)

    private val opener = object : AbstractForwardedChannelOpener(CHANNEL_TYPE, client.connection) {
        override fun handleOpen(buf: SSHPacket) {
            val recipient = buf.readUInt32AsInt()
            // Each channel gets a thread and a buffer: a server opening them without end must not get that many.
            if (openChannels.incrementAndGet() > MAX_CHANNELS) {
                openChannels.decrementAndGet()
                conn.sendOpenFailure(recipient, OpenFailException.Reason.RESOURCE_SHORTAGE, "too many agent channels")
                return
            }
            try {
                val channel = AgentChannel(conn, recipient, buf.readUInt32(), buf.readUInt32())
                callListener(ConnectListener { opened -> serve(opened) }, channel)
            } catch (err: Throwable) {
                openChannels.decrementAndGet()
                throw err
            }
        }
    }

    private class AgentChannel(conn: Connection, recipient: Int, remoteWindow: Long, remoteMaxPacket: Long) :
        AbstractForwardedChannel(conn, CHANNEL_TYPE, recipient, remoteWindow, remoteMaxPacket, "", 0)

    /** Starts accepting the server's agent channels on this connection. */
    fun attach() {
        client.connection.attach(opener)
    }

    /** Stops accepting agent channels and lets go of the keys. */
    fun detach() {
        runCatching { client.connection.forget(opener) }
        loadedKeys = null
    }

    /**
     * Asks the server to offer this agent to what runs in [session] (before its shell or command starts). Like
     * OpenSSH's client, it doesn't wait for an answer: a server that refuses simply never opens an agent channel.
     */
    fun requestOn(session: Session) {
        client.transport.write(
            SSHPacket(Message.CHANNEL_REQUEST)
                .putUInt32(session.recipient.toLong())
                .putString(REQUEST_TYPE)
                .putBoolean(false)
        )
    }

    private fun serve(channel: Channel.Forwarded) {
        try {
            channel.confirm()
            val input = DataInputStream(channel.inputStream)
            val output = channel.outputStream
            while (true) {
                val length = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                }
                if (length !in 1..MAX_MESSAGE_BYTES) throw IOException("agent message of $length bytes")
                val message = ByteArray(length).also(input::readFully)
                val reply = runCatching { answer(message) }.getOrElse { FAILURE }
                output.write(Buffer.PlainBuffer().putUInt32(reply.size.toLong()).putRawBytes(reply).compactData)
                output.flush()
            }
        } catch (_: IOException) {
            // The program on the server went away, or the connection did.
        } finally {
            openChannels.decrementAndGet()
            runCatching { channel.close() }
        }
    }

    /** One agent request in, one reply out (both without the length in front). */
    internal fun answer(message: ByteArray): ByteArray {
        val request = Buffer.PlainBuffer(message)
        return when (request.readByte().toInt()) {
            REQUEST_IDENTITIES -> Buffer.PlainBuffer().putByte(IDENTITIES_ANSWER.toByte()).putUInt32(keys.size.toLong()).apply {
                keys.forEach { key -> putBytes(key.blob).putString(key.label) }
            }.compactData
            SIGN_REQUEST -> {
                val blob = request.readBytes()
                val data = request.readBytes()
                val flags = if (request.available() >= 4) request.readUInt32() else 0L
                val key = keys.firstOrNull { it.blob.contentEquals(blob) } ?: return FAILURE
                val algorithm = algorithmFor(key.publicKey, flags) ?: return FAILURE
                val signature = algorithm.newSignature().apply {
                    initSign(key.privateKey)
                    update(data)
                }
                val reply = Buffer.PlainBuffer().putByte(SIGN_RESPONSE.toByte())
                    .putSignature(algorithm.keyAlgorithm, signature.encode(signature.sign()))
                    .compactData
                runCatching { onSigned(key.label) }
                reply
            }
            else -> FAILURE
        }
    }

    /**
     * What to sign with: the key's own algorithm, or for RSA the SHA-2 one the request asks for. RSA with SHA-1
     * (no flag) is refused, as everywhere else in the app.
     */
    private fun algorithmFor(key: PublicKey, flags: Long): KeyAlgorithm? {
        val name = when (val type = KeyType.fromKey(key)) {
            KeyType.RSA -> when {
                flags and FLAG_RSA_SHA2_512 != 0L -> "rsa-sha2-512"
                flags and FLAG_RSA_SHA2_256 != 0L -> "rsa-sha2-256"
                else -> return null
            }
            KeyType.ED25519, KeyType.ECDSA256, KeyType.ECDSA384, KeyType.ECDSA521 -> type.toString()
            else -> return null
        }
        // The connection's own algorithms first: they are the ones that work with this device's key classes.
        client.transport.config.keyAlgorithms.firstOrNull { it.name == name }?.let { return it.create() }
        return when (name) {
            "rsa-sha2-512" -> KeyAlgorithms.RSASHA512()
            "rsa-sha2-256" -> KeyAlgorithms.RSASHA256()
            "ssh-ed25519" -> KeyAlgorithms.EdDSA25519()
            "ecdsa-sha2-nistp256" -> KeyAlgorithms.ECDSASHANistp256()
            "ecdsa-sha2-nistp384" -> KeyAlgorithms.ECDSASHANistp384()
            "ecdsa-sha2-nistp521" -> KeyAlgorithms.ECDSASHANistp521()
            else -> null
        }?.create()
    }

    companion object {
        const val CHANNEL_TYPE = "auth-agent@openssh.com"
        const val REQUEST_TYPE = "auth-agent-req@openssh.com"
        private const val MAX_MESSAGE_BYTES = 256 * 1024
        /** Agent channels open at once; ssh opens one per login or `ssh-add`, briefly. */
        internal const val MAX_CHANNELS = 8
        private const val REQUEST_IDENTITIES = 11
        private const val IDENTITIES_ANSWER = 12
        private const val SIGN_REQUEST = 13
        private const val SIGN_RESPONSE = 14
        private const val FLAG_RSA_SHA2_256 = 2L
        private const val FLAG_RSA_SHA2_512 = 4L
        private val FAILURE = byteArrayOf(5)
    }
}
