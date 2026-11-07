package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import com.majordaftapps.sshpeaches.app.service.connectionFailureKind
import net.schmizz.sshj.SSHClient
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** SHA-1 era algorithms are offered only to hosts that allow them; RSA host keys keep working through rsa-sha2. */
class LegacyAlgorithmsSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var server: SshServer? = null
    private val clients = mutableListOf<SSHClient>()

    @After
    fun tearDown() {
        clients.forEach { runCatching { it.disconnect() } }
        runCatching { server?.stop(true) }
    }

    @Test
    fun legacyAlgorithmsAreOnlyOfferedWhenTheHostAllowsThem() {
        assertTrue(SshClientProvider.isLegacyAlgorithm("ssh-rsa"))
        assertTrue(SshClientProvider.isLegacyAlgorithm("ssh-dss"))
        assertTrue(SshClientProvider.isLegacyAlgorithm("diffie-hellman-group14-sha1"))
        assertTrue(SshClientProvider.isLegacyAlgorithm("diffie-hellman-group-exchange-sha1"))
        assertTrue(SshClientProvider.isLegacyAlgorithm("aes128-cbc"))
        listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-ed25519", "ecdh-sha2-nistp256", "curve25519-sha256",
            "diffie-hellman-group14-sha256", "aes128-ctr", "aes256-gcm@openssh.com", "hmac-sha1", "hmac-sha2-256"
        ).forEach { assertFalse(it, SshClientProvider.isLegacyAlgorithm(it)) }

        val modern = SshClientProvider.createClientForTesting(temp.newFile(), host(22, legacy = false)).config()
        val legacy = SshClientProvider.createClientForTesting(temp.newFile(), host(22, legacy = true)).config()
        listOf("ssh-rsa", "diffie-hellman-group14-sha1", "aes128-cbc").forEach {
            assertFalse("$it offered by default: $modern", it in modern)
            assertTrue("$it not offered with legacy on: $legacy", it in legacy)
        }
        assertTrue("rsa-sha2-512" in modern)
    }

    @Test
    fun anRsaHostKeyStillConnectsWithSha2Signatures() {
        startServer(rsaHostKey = true) {}
        connect(legacy = false)
    }

    @Test
    fun aSha1OnlyKeyExchangeNeedsLegacyAlgorithms() {
        startServer {
            keyExchangeFactories = NamedFactory.setUpTransformedFactories(
                false, listOf(BuiltinDHFactories.dhg14), ServerBuilder.DH2KEX
            )
        }
        assertNoCommonAlgorithms()
        connect(legacy = true)
    }

    @Test
    fun anSshRsaOnlyHostKeyNeedsLegacyAlgorithms() {
        startServer(rsaHostKey = true) {
            signatureFactories = listOf(BuiltinSignatures.rsa)
        }
        assertNoCommonAlgorithms()
        connect(legacy = true)
    }

    private fun assertNoCommonAlgorithms() {
        try {
            connect(legacy = false)
            fail("connected without legacy algorithms")
        } catch (e: Exception) {
            assertEquals(e.toString(), ConnectionFailureKind.NO_COMMON_ALGORITHMS, e.connectionFailureKind())
        }
    }

    private fun SSHClient.config(): List<String> = transport.config.let { config ->
        (config.keyAlgorithms + config.keyExchangeFactories + config.cipherFactories).map { it.name }
    }.also { runCatching { close() } }

    private fun host(port: Int, legacy: Boolean) = HostConnection(
        id = "legacy-$legacy",
        name = "Legacy test",
        host = "127.0.0.1",
        port = port,
        username = "tester",
        preferredAuth = AuthMethod.PASSWORD,
        allowLegacyAlgorithms = legacy
    )

    private fun startServer(rsaHostKey: Boolean = false, configure: SshServer.() -> Unit) {
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("hostkey").toPath()).apply {
                if (rsaHostKey) {
                    algorithm = "RSA"
                    keySize = 2048
                }
            }
            configure()
            start()
        }
    }

    private fun connect(legacy: Boolean) {
        val host = host(server!!.port, legacy)
        SshClientProvider.createClientForTesting(knownHostsFile = temp.newFile(), host = host).also {
            clients += it
            it.connect(host.host, host.port)
            assertTrue(it.isConnected)
        }
    }
}
