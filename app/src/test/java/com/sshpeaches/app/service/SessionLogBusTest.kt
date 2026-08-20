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
        assertFalse(logger.isDebugEnabled)
        assertFalse(logger.isTraceEnabled)

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
                .collect { transcript += it.message }
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
            assertFalse(transcript.any { it.startsWith("Encoding packet #") })
            assertFalse(transcript.any { it.startsWith("Received packet #") })
            assertFalse(transcript.any { it.contains(" IN #") })
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
            collector.cancelAndJoin()
        }
    }

    private object ReleaseLikeLoggerFactory : LoggerFactory {
        private val delegate = NOPLoggerFactory()

        override fun getLogger(name: String): Logger = delegate.getLogger(name)

        override fun getLogger(clazz: Class<*>?): Logger =
            getLogger(clazz?.name ?: "unknown")
    }
}
