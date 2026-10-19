package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.service.SessionLogBus.LogLevel
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import org.slf4j.helpers.NOPLoggerFactory

class SessionLogBusTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun debugLogsAreVisibleInConnectionTranscriptWithoutSessionDiagnostics() {
        assertTrue(
            shouldPublishSshLog(
                level = LogLevel.DEBUG,
                connectionTranscriptEnabled = true,
                diagnosticsEnabled = false
            )
        )
        assertFalse(
            shouldPublishSshLog(
                level = LogLevel.DEBUG,
                connectionTranscriptEnabled = false,
                diagnosticsEnabled = false
            )
        )
        assertTrue(
            shouldPublishSshLog(
                level = LogLevel.DEBUG,
                connectionTranscriptEnabled = false,
                diagnosticsEnabled = true
            )
        )
    }

    @Test
    fun highFrequencyTransportLogsArePublishedWithDiagnosticsEnabled() {
        listOf(
            "Received packet {}",
            "Received packet #{}: {}",
            "Consuming by {} down to {}",
            "Increasing by {} up to {}",
            "{} Sending after interval [{} seconds]"
        ).forEach { message ->
            assertTrue(
                message,
                shouldPublishSshLog(
                    level = LogLevel.DEBUG,
                    connectionTranscriptEnabled = false,
                    diagnosticsEnabled = true
                )
            )
        }
    }

    @Test
    fun userRelevantLevelsAreNeverSuppressed() {
        listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR).forEach { level ->
            assertTrue(
                shouldPublishSshLog(
                    level = level,
                    connectionTranscriptEnabled = false,
                    diagnosticsEnabled = false
                )
            )
        }
    }

    @Test
    fun highFrequencyTransportLogsAreSampledAcrossSessionLoggers() {
        var now = 1_000L
        val sampler = SshTransportLogSampler(
            intervalNanos = 100L,
            nanoTime = { now }
        )

        assertTrue(sampler.shouldPublish(LogLevel.DEBUG, "Received packet #1: SSH_MSG_CHANNEL_DATA"))
        now += 99L
        assertFalse(sampler.shouldPublish(LogLevel.DEBUG, "Consuming by 32 down to 128"))
        now += 1L
        assertTrue(sampler.shouldPublish(LogLevel.DEBUG, "Increasing by 64 up to 192"))
        assertTrue(sampler.shouldPublish(LogLevel.DEBUG, "Authentication method changed"))
        assertTrue(sampler.shouldPublish(LogLevel.INFO, "Received packet summary"))
    }

    @Test
    fun connectionTranscriptDoesNotSampleProtocolMessages() {
        val sampler = SshTransportLogSampler(intervalNanos = Long.MAX_VALUE)

        assertTrue(
            sampler.shouldPublish(
                level = LogLevel.DEBUG,
                message = "Received packet SSH_MSG_KEXINIT",
                connectionTranscriptEnabled = true
            )
        )
        assertTrue(
            sampler.shouldPublish(
                level = LogLevel.DEBUG,
                message = "Received packet SSH_MSG_NEWKEYS",
                connectionTranscriptEnabled = true
            )
        )
    }

    @Test
    fun releaseLikeLoggerPublishesSemanticConnectionEvents() = runBlocking {
        val hostId = "release-like-connection"
        val logger = SessionLoggerFactory(
            hostId = hostId,
            connectionTranscriptEnabled = { true },
            diagnosticsEnabled = { false },
            delegate = ReleaseLikeLoggerFactory,
            logcatEnabled = false
        ).getLogger("sshj.connection")
        // On for the connection transcript, so SSHJ's guarded packet dumps reach it (to be decoded).
        assertTrue(logger.isDebugEnabled)
        assertTrue(logger.isTraceEnabled)

        val entries = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) {
                SessionLogBus.entries
                    .filter { it.hostId == hostId }
                    .take(2)
                    .toList()
            }
        }
        logger.debug("Sending {}", "SSH_MSG_KEXINIT")
        logger.trace("Received packet {}", "SSH_MSG_KEXINIT")

        assertEquals(
            listOf("Sending SSH_MSG_KEXINIT", "Received packet SSH_MSG_KEXINIT"),
            entries.await().map { it.message }
        )
    }

    @Test
    fun packetDumpsAreOffOnceTheSessionIsOpen() {
        var transcript = true
        val logger = SessionLoggerFactory(
            hostId = "dump-gating",
            connectionTranscriptEnabled = { transcript },
            diagnosticsEnabled = { true },
            delegate = ReleaseLikeLoggerFactory,
            logcatEnabled = false
        ).getLogger("sshj.transport")
        assertTrue(logger.isTraceEnabled)
        transcript = false
        // Not even with diagnostics: an open session's packets carry everything typed.
        assertFalse(logger.isTraceEnabled)
        assertFalse(logger.isDebugEnabled)
    }

    @Test
    fun packetsAreDecodedAndSecretCarryingPacketsAreNotLogged() {
        val proposal = listOf(
            "curve25519-sha256,ext-info-c", "ssh-ed25519", "aes128-ctr", "aes128-ctr", "hmac-sha2-256",
            "hmac-sha2-256", "none", "none", "", ""
        )
        val kexinit = byteArrayOf(20) + ByteArray(16) + proposal.fold(ByteArray(0)) { acc, it -> acc + sshString(it) } +
            byteArrayOf(0) + ByteArray(4)
        assertEquals(
            listOf(
                "recv SSH_MSG_KEXINIT (${kexinit.size} bytes)",
                "  server proposal:",
                "    KEX algorithms: curve25519-sha256,ext-info-c",
                "    host key algorithms: ssh-ed25519",
                "    ciphers ctos: aes128-ctr",
                "    ciphers stoc: aes128-ctr",
                "    MACs ctos: hmac-sha2-256",
                "    MACs stoc: hmac-sha2-256",
                "    compression ctos: none",
                "    compression stoc: none"
            ),
            packetLogText("Received packet #{}: {}", arrayOf(3L, hexOf(kexinit)))!!.lines()
        )
        assertEquals(
            "send SSH_MSG_SERVICE_REQUEST (17 bytes)\n  service ssh-userauth",
            packetLogText("Encoding packet #{}: {}", arrayOf(4L, hexOf(byteArrayOf(5) + sshString("ssh-userauth"))))
        )
        val failure = byteArrayOf(51) + sshString("publickey,password") + byteArrayOf(0)
        assertEquals(
            "recv SSH_MSG_USERAUTH_FAILURE (24 bytes)\n  can continue: publickey,password",
            packetLogText("Received packet #{}: {}", arrayOf(6L, hexOf(failure)))
        )
        val hostKey = sshString("ssh-ed25519") + sshString("k".repeat(32))
        val reply = byteArrayOf(31) + sshBytes(hostKey) + sshString("q".repeat(32))
        val replyText = packetLogText("Received packet #{}: {}", arrayOf(5L, hexOf(reply)))!!
        assertTrue(replyText, replyText.endsWith("\n  host key type ssh-ed25519"))
        val request = byteArrayOf(98) + ByteArray(4) + sshString("pty-req") + byteArrayOf(1) + sshString("xterm")
        assertEquals(
            "send SSH_MSG_CHANNEL_REQUEST (${request.size} bytes)\n  channel 0, request pty-req, want reply true",
            packetLogText("Encoding packet #{}: {}", arrayOf(9L, hexOf(request)))
        )

        // Passwords, keyboard-interactive answers, terminal data and unknown messages: not even a line.
        val auth = hexOf(byteArrayOf(50) + "user ssh-connection password secret".toByteArray())
        assertEquals(SKIP_PACKET_LOG, packetLogText("Encoding packet #{}: {}", arrayOf(7L, auth)))
        listOf(50, 60, 61, 94, 95, 123).forEach { id ->
            val packet = hexOf(byteArrayOf(id.toByte(), 65, 66))
            assertEquals("$id", SKIP_PACKET_LOG, packetLogText("Encoding packet #{}: {}", arrayOf(1L, packet)))
            assertEquals("$id", SKIP_PACKET_LOG, packetLogText("Received packet #{}: {}", arrayOf(1L, packet)))
        }
        // The client never sends these; if it did, they wouldn't be ours to log.
        listOf(51, 52, 53).forEach { id ->
            val packet = hexOf(byteArrayOf(id.toByte(), 65, 66))
            assertEquals("$id", SKIP_PACKET_LOG, packetLogText("Encoding packet #{}: {}", arrayOf(1L, packet)))
        }
        assertEquals(SKIP_PACKET_LOG, packetLogText("IN #{}: {}", arrayOf(0, "41 42")))
        assertEquals(null, packetLogText("Received packet {}", arrayOf("SSH_MSG_KEXINIT")))
        // A dump under a log line this filter doesn't know (a renamed sshj message) is dropped, not printed.
        assertEquals(SKIP_PACKET_LOG, packetLogText("Sending packet #{}: {}", arrayOf(7L, auth)))
        assertEquals(null, packetLogText("Connected to {} {}", arrayOf("ab cd", "host")))
        // A malformed packet still gets its line, without details.
        assertEquals(
            "recv SSH_MSG_KEXINIT (3 bytes)",
            packetLogText("Received packet #{}: {}", arrayOf(1L, hexOf(byteArrayOf(20, 1, 2))))
        )
    }

    @Test
    fun realSshjHandshakeProducesDenseSafeConnectionTranscriptInReleaseLikeMode() = runBlocking {
        val hostId = "release-like-real-handshake"
        val username = "transcript-user"
        val password = "transcript-password"
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(
                temp.root.toPath().resolve("transcript-host-key.ser")
            ).apply {
                setAlgorithm("EC")
            }
            passwordAuthenticator = PasswordAuthenticator { candidate, secret, _ ->
                candidate == username && secret == password
            }
            start()
        }
        val transcript = CopyOnWriteArrayList<String>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            SessionLogBus.entries
                .filter { it.hostId == hostId }
                .collect {
                    transcript += it.message
                }
        }
        val config = DefaultConfig().apply {
            setLoggerFactory(
                SessionLoggerFactory(
                    hostId = hostId,
                    connectionTranscriptEnabled = { true },
                    diagnosticsEnabled = { false },
                    delegate = ReleaseLikeLoggerFactory,
                    logcatEnabled = false
                )
            )
        }
        val client = SSHClient(config).apply {
            addHostKeyVerifier(PromiscuousVerifier())
        }

        try {
            client.connect(server.host, server.port)
            client.authPassword(username, password)
            withTimeout(5_000L) {
                while (transcript.none { it.contains("auth successful") }) {
                    delay(10L)
                }
            }

            assertTrue("Expected a dense SSH connection transcript: $transcript", transcript.size >= 30)
            assertTrue(transcript.any { it == "Sending SSH_MSG_KEXINIT" })
            assertTrue(transcript.any { it == "Received SSH_MSG_KEXINIT" })
            assertTrue(transcript.any { it.startsWith("Negotiated algorithms:") })
            assertTrue(transcript.any { it.contains("Trying `password` auth") })
            assertTrue(transcript.any { it.contains("`password` auth successful") })
            assertFalse(transcript.any { it.contains(password) })
            // SSHJ's raw one-line dumps are always decoded.
            assertFalse(transcript.any { it.startsWith("Encoding packet #") })
            assertFalse(transcript.any { it.startsWith("Received packet #") })
            assertFalse(transcript.any { it.contains(" IN #") })
            // One decoded line per packet; the password packet isn't logged at all, and nothing is a hexdump.
            assertTrue(transcript.any { it.startsWith("send SSH_MSG_KEXINIT") && it.contains("\n  client proposal:") })
            assertTrue(transcript.any { it.startsWith("recv SSH_MSG_KEXINIT") && it.contains("KEX algorithms: ") })
            assertTrue(transcript.any { it.startsWith("send SSH_MSG_SERVICE_REQUEST") && it.endsWith("service ssh-userauth") })
            assertTrue(transcript.any { it.startsWith("recv SSH_MSG_USERAUTH_SUCCESS") })
            assertFalse(transcript.any { it.contains("SSH_MSG_USERAUTH_REQUEST") })
            assertFalse(transcript.any { it.contains(Regex("([0-9a-f]{2} ){8}")) })
            assertTrue("Expected a long, streaming transcript: ${transcript.size}", transcript.sumOf { it.lines().size } >= 60)
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
            collector.cancelAndJoin()
        }
    }

    private fun hexOf(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it) }

    private fun sshBytes(bytes: ByteArray): ByteArray =
        java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array() + bytes

    private fun sshString(text: String): ByteArray = sshBytes(text.toByteArray())

    private object ReleaseLikeLoggerFactory : LoggerFactory {
        private val delegate = NOPLoggerFactory()

        override fun getLogger(name: String): Logger = delegate.getLogger(name)

        override fun getLogger(clazz: Class<*>?): Logger =
            getLogger(clazz?.name ?: "unknown")
    }
}
