package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.security.spec.EdECPoint
import java.security.spec.NamedParameterSpec
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.userauth.UserAuthException
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshClientProviderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun configuredClientUsesTransferOptimizedChannelFlowControl() {
        val host = HostConnection(
            id = "flow-control-test",
            name = "Flow control test",
            host = "127.0.0.1",
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_flow_control"),
            host = host
        )

        try {
            assertEquals(16L * 1024L * 1024L, client.connection.windowSize)
            assertEquals(32 * 1024, client.connection.maxPacketSize)
        } finally {
            runCatching { client.close() }
        }
    }

    @Test
    fun regression_largeSftpUploadAndDownloadStayBelowSshjTransportPacketLimit() {
        val sandbox = temp.newFolder("large-sftp-sandbox")
        val server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("large-sftp-hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == TEST_USERNAME && password == TEST_PASSWORD
            }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        val payload = ByteArray(3 * 1024 * 1024) { index -> (index % 251).toByte() }
        val upload = temp.newFile("large-video.mp4").apply { writeBytes(payload) }
        val download = temp.newFile("large-video-downloaded.mp4")
        val host = HostConnection(
            id = "large-sftp-test",
            name = "Large SFTP test",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_large_sftp"),
            host = host
        )

        try {
            client.connect(host.host, host.port)
            client.authPassword(TEST_USERNAME, TEST_PASSWORD)
            client.newSFTPClient().use { sftp ->
                sftp.put(upload.absolutePath, "/large-video.mp4")
                sftp.get("/large-video.mp4", download.absolutePath)
            }
            assertTrue(Files.mismatch(upload.toPath(), download.toPath()) == -1L)
        } finally {
            runCatching { client.close() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun compatibleHostKeyProviderKeepsSshjDefaultProviderUnset() {
        val originalBcProvider = Security.getProvider("BC")
        try {
            SecurityUtils.setRegisterBouncyCastle(false)
            SecurityUtils.setSecurityProvider(null)
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }

            SshClientProvider.ensureCompatibleHostKeyAlgorithmsAvailableForTesting()

            assertNotNull(KeyFactory.getInstance("ECDSA"))
            assertNotNull(KeyFactory.getInstance("Ed25519"))
            assertNotNull(Signature.getInstance("Ed25519"))
            assertNull(SecurityUtils.getSecurityProvider())
        } finally {
            SecurityUtils.setSecurityProvider(null)
            if (originalBcProvider == null) {
                Security.removeProvider("BC")
            } else if (Security.getProvider("BC")?.javaClass?.name != originalBcProvider.javaClass.name) {
                Security.removeProvider("BC")
                Security.addProvider(originalBcProvider)
            }
        }
    }

    @Test
    fun compatibleKexRemovesDiffieHellmanWhenDhKeyPairGeneratorIsUnavailable() {
        val names = SshClientProvider.compatibleKeyExchangeNamesForTesting(
            unavailableAlgorithms = setOf("DH")
        )

        assertFalse(names.any { it.contains("diffie-hellman", ignoreCase = true) })
        assertTrue(names.any { it.startsWith("ecdh-", ignoreCase = true) })
    }

    @Test
    fun regression_cannotConnectWhenDhOnlyServerIsUnavailableForCaseInsensitiveInput() {
        val names = SshClientProvider.compatibleKeyExchangeNamesForTesting(
            unavailableAlgorithms = setOf("dH")
        )

        assertFalse(names.any { it.contains("diffie-hellman", ignoreCase = true) })
    }

    @Test
    fun regression_cannotConnectWhenDhOnlyServerIsUnavailableAndUnavailableToClient() {
        val server = startLocalSshServer(
            hostKeyAlgorithm = "EC",
            keyExchanges = listOf(BuiltinDHFactories.dhgex256)
        )
        val host = HostConnection(
            id = "localhost-dh-only-fallback-blocked",
            name = "Local DH-only server with DH unavailable",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_dh_only"),
            host = host,
            autoTrustUnknownHostKey = true,
            unavailableKeyExchangeAlgorithms = setOf("DH")
        )

        try {
            client.connect(host.host, host.port)
            fail("Expected DH-only key exchange negotiation to fail when DH is unavailable on client.")
        } catch (_: Exception) {
            assertFalse(client.isAuthenticated)
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun compatibleKexPrefersEcdhBeforeDiffieHellmanForOpenSshOverlap() {
        val names = SshClientProvider.compatibleKeyExchangeNamesForTesting(
            unavailableAlgorithms = setOf("X25519")
        )

        val ecdhIndex = names.indexOf("ecdh-sha2-nistp256")
        val dhIndex = names.indexOf("diffie-hellman-group-exchange-sha256")
        assertTrue("Expected ecdh-sha2-nistp256 in KEX list: $names", ecdhIndex >= 0)
        assertTrue("Expected diffie-hellman-group-exchange-sha256 in KEX list: $names", dhIndex >= 0)
        assertTrue("Expected ECDH before DH KEX: $names", ecdhIndex < dhIndex)
    }

    @Test
    fun connectsToOpenSshLikeEcdhServerWithEcdsaHostKey() {
        val server = startLocalSshServer(
            hostKeyAlgorithm = "EC",
            keyExchanges = listOf(BuiltinDHFactories.dhgex256, BuiltinDHFactories.ecdhp256)
        )
        val host = HostConnection(
            id = "openssh-like",
            name = "OpenSSH-like local",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts"),
            host = host,
            autoTrustUnknownHostKey = true
        )

        try {
            client.connect(host.host, host.port)
            client.authPassword(TEST_USERNAME, TEST_PASSWORD)

            assertTrue(client.isConnected)
            assertTrue(client.isAuthenticated)
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun connectsToLocalEcdhServerWhenDhKeyPairGeneratorIsUnavailable() {
        val server = startLocalSshServer(
            hostKeyAlgorithm = "EC",
            keyExchanges = listOf(BuiltinDHFactories.dhgex256, BuiltinDHFactories.ecdhp256)
        )
        val host = HostConnection(
            id = "localhost-dh-fallback",
            name = "Local DH filtered to ECDH",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_fallback"),
            host = host,
            autoTrustUnknownHostKey = true,
            unavailableKeyExchangeAlgorithms = setOf("DH")
        )

        try {
            client.connect(host.host, host.port)
            client.authPassword(TEST_USERNAME, TEST_PASSWORD)

            assertTrue(client.isConnected)
            assertTrue(client.isAuthenticated)
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun retriesPasswordAfterInitialFailureWhenDhKeyPairGeneratorIsUnavailable() {
        val server = startLocalSshServer(
            hostKeyAlgorithm = "EC",
            keyExchanges = listOf(BuiltinDHFactories.dhgex256, BuiltinDHFactories.ecdhp256)
        )
        val host = HostConnection(
            id = "localhost-dh-fallback-retry",
            name = "Local DH filtered to ECDH retry",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = temp.newFile("known_hosts_fallback_retry"),
            host = host,
            autoTrustUnknownHostKey = true,
            unavailableKeyExchangeAlgorithms = setOf("DH")
        )

        try {
            client.connect(host.host, host.port)

            runCatching {
                client.authPassword(TEST_USERNAME, "wrong-password")
            }.onSuccess {
                throw AssertionError("Authentication should fail with wrong password.")
            }

            client.authPassword(TEST_USERNAME, TEST_PASSWORD)
            assertTrue(client.isAuthenticated)
        } catch (ex: UserAuthException) {
            throw AssertionError("Expected second authentication attempt with correct password to succeed.", ex)
        } finally {
            runCatching { client.disconnect() }
            runCatching { server.stop(true) }
        }
    }

    @Test
    fun connectsToOpenSshServerWithEd25519HostKeyWhenAvailable() {
        val server = startOpenSshEd25519Server()
        val knownHostsFile = temp.newFile("known_hosts_openssh_ed25519")
        val host = HostConnection(
            id = "openssh-like-ed25519",
            name = "OpenSSH Ed25519 local",
            host = "127.0.0.1",
            port = server.port,
            username = TEST_USERNAME,
            preferredAuth = AuthMethod.PASSWORD
        )
        val client = SshClientProvider.createClientForTesting(
            knownHostsFile = knownHostsFile,
            host = host,
            autoTrustUnknownHostKey = true
        )

        try {
            client.connect(host.host, host.port)

            assertTrue(client.isConnected)
            assertTrue(knownHostsFile.readText().contains("ssh-ed25519"))
        } finally {
            runCatching { client.disconnect() }
            server.close()
        }
    }

    @Test
    fun ed25519Pkcs8IdentityLoadsDirectKeyProvider() {
        SecurityUtils.setRegisterBouncyCastle(false)
        SecurityUtils.setSecurityProvider(null)
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val generated = SshKeyGenerator.generate(
            IdentityKeyGenerationSpec(
                algorithm = IdentityKeyAlgorithm.ED25519,
                comment = "test-ed25519"
            )
        )
        val client = SSHClient(DefaultConfig())
        val provider = Ed25519IdentityKeyProvider.load(
            client = client,
            privateKeyMaterial = generated.privateKey,
            publicKeyMaterial = generated.publicKey,
            passphrase = null
        )

        assertNotNull(provider)
        val loaded = provider!!
        assertEquals(KeyType.ED25519, loaded.type)
        assertEquals(KeyType.ED25519, KeyType.fromKey(loaded.public))
        assertEquals(KeyType.ED25519, KeyType.fromKey(loaded.private))
        assertEquals("Ed25519", loaded.public.algorithm)
        assertEquals("Ed25519", loaded.private.algorithm)

        val publicKeyBlob = Buffer.PlainBuffer()
            .putPublicKey(loaded.public)
            .compactData
        assertTrue(publicKeyBlob.isNotEmpty())

        val payload = "sshpeaches-ed25519-auth".encodeToByteArray()
        val sshjSignature = SshClientProvider.ed25519SignatureForTesting()
        sshjSignature.initSign(loaded.private)
        sshjSignature.update(payload)
        val signed = sshjSignature.sign()
        assertEquals(64, signed.size)

        val verifier = SshClientProvider.ed25519SignatureForTesting()
        verifier.initVerify(loaded.public)
        verifier.update(payload)
        assertTrue(verifier.verify(signed))

        val wrappedSignature = Buffer.PlainBuffer()
            .putString(KeyType.ED25519.toString())
            .putString(signed)
            .compactData
        val wrappedVerifier = SshClientProvider.ed25519SignatureForTesting()
        wrappedVerifier.initVerify(loaded.public)
        wrappedVerifier.update(payload)
        assertTrue(wrappedVerifier.verify(wrappedSignature))

        assertTrue(loaded.private is Key)
        assertTrue(loaded.public is Key)
    }

    @Test
    fun ed25519SignatureVerifiesPublicKeyWhenEncodedThrows() {
        val loaded = generatedEd25519KeyProvider()
        val rawPublicKey = (loaded.public as RawEd25519PublicKey).rawPublicKey()
        val conscryptLikePublicKey = ThrowingEncodedEd25519PublicKey(rawPublicKey)
        val payload = "sshpeaches-conscrypt-ed25519-host-key".encodeToByteArray()

        val signer = SshClientProvider.ed25519SignatureForTesting()
        signer.initSign(loaded.private)
        signer.update(payload)
        val signature = signer.sign()

        val verifier = SshClientProvider.ed25519SignatureForTesting()
        verifier.initVerify(conscryptLikePublicKey)
        verifier.update(payload)

        assertTrue(verifier.verify(signature))
    }

    @Test
    fun knownHostsTrustsEd25519PublicKeyWhenEncodedThrows() {
        val loaded = generatedEd25519KeyProvider()
        val rawPublicKey = (loaded.public as RawEd25519PublicKey).rawPublicKey()
        val conscryptLikePublicKey = ThrowingEncodedEd25519PublicKey(rawPublicKey)
        val knownHostsFile = temp.newFile("known_hosts_conscrypt_ed25519")
        val prompts = mutableListOf<SshClientProvider.HostKeyPrompt>()

        val trusted = SshClientProvider.verifyHostKeyForTesting(
            knownHostsFile = knownHostsFile,
            host = "127.0.0.1",
            port = 2222,
            key = conscryptLikePublicKey,
            autoTrustUnknownHostKey = false,
            onHostKeyPrompt = { prompt ->
                prompts += prompt
                true
            }
        )

        assertTrue(trusted)
        assertEquals(1, prompts.size)
        assertFalse(prompts.single().keyChanged)
        assertTrue(prompts.single().fingerprint.startsWith("SHA256:"))
        assertTrue(knownHostsFile.readText().contains("ssh-ed25519"))

        assertTrue(
            SshClientProvider.verifyHostKeyForTesting(
                knownHostsFile = knownHostsFile,
                host = "127.0.0.1",
                port = 2222,
                key = conscryptLikePublicKey,
                autoTrustUnknownHostKey = false
            )
        )
    }

    private fun generatedEd25519KeyProvider(): net.schmizz.sshj.userauth.keyprovider.KeyProvider {
        SecurityUtils.setRegisterBouncyCastle(false)
        SecurityUtils.setSecurityProvider(null)
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val generated = SshKeyGenerator.generate(
            IdentityKeyGenerationSpec(
                algorithm = IdentityKeyAlgorithm.ED25519,
                comment = "test-ed25519"
            )
        )
        val provider = Ed25519IdentityKeyProvider.load(
            client = SSHClient(DefaultConfig()),
            privateKeyMaterial = generated.privateKey,
            publicKeyMaterial = generated.publicKey,
            passphrase = null
        )

        assertNotNull(provider)
        return provider!!
    }

    private fun startLocalSshServer(
        hostKeyAlgorithm: String,
        keyExchanges: List<BuiltinDHFactories>
    ): SshServer {
        return SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(
                temp.newFile("host-key-$hostKeyAlgorithm.ser").toPath()
            ).apply {
                setAlgorithm(hostKeyAlgorithm)
            }
            keyExchangeFactories = keyExchanges.map { ServerBuilder.DH2KEX.apply(it) }
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == TEST_USERNAME && password == TEST_PASSWORD
            }
            start()
        }
    }

    private fun startOpenSshEd25519Server(): OpenSshServer {
        val sshd = findExecutable(
            "/usr/sbin/sshd",
            "/usr/local/sbin/sshd",
            "/opt/homebrew/sbin/sshd"
        ) ?: skipTest("OpenSSH sshd is unavailable")
        val sshKeygen = findExecutable(
            "/usr/bin/ssh-keygen",
            "/usr/local/bin/ssh-keygen",
            "/opt/homebrew/bin/ssh-keygen"
        ) ?: skipTest("OpenSSH ssh-keygen is unavailable")
        val serverDir = temp.newFolder("openssh-ed25519-server")
        val hostKey = File(serverDir, "ssh_host_ed25519_key")
        val logFile = File(serverDir, "sshd.log")
        val port = reserveLocalPort()

        val keygen = ProcessBuilder(
            sshKeygen,
            "-q",
            "-t",
            "ed25519",
            "-N",
            "",
            "-f",
            hostKey.absolutePath
        )
            .redirectErrorStream(true)
            .start()
        assumeTrue("ssh-keygen could not create an Ed25519 host key", keygen.waitForSuccess())

        val configFile = File(serverDir, "sshd_config").apply {
            writeText(
                """
                HostKey ${hostKey.absolutePath}
                HostKeyAlgorithms ssh-ed25519
                Port $port
                ListenAddress 127.0.0.1
                PidFile ${File(serverDir, "sshd.pid").absolutePath}
                AuthorizedKeysFile none
                PubkeyAuthentication no
                PasswordAuthentication no
                UsePAM no
                PermitRootLogin no
                PrintMotd no
                LogLevel VERBOSE
                Subsystem sftp internal-sftp
                """.trimIndent()
            )
        }
        val configCheck = ProcessBuilder(
            sshd,
            "-t",
            "-f",
            configFile.absolutePath,
            "-E",
            logFile.absolutePath
        )
            .redirectErrorStream(true)
            .start()
        assumeTrue(
            "OpenSSH sshd rejected the temporary test config: ${logFile.readIfPresent()}",
            configCheck.waitForSuccess()
        )

        val process = ProcessBuilder(
            sshd,
            "-D",
            "-f",
            configFile.absolutePath,
            "-E",
            logFile.absolutePath
        ).start()
        assumeTrue(
            "OpenSSH sshd did not start on localhost: $port ${logFile.readIfPresent()}",
            waitForPort("127.0.0.1", port, process)
        )
        return OpenSshServer(port, process)
    }

    private fun findExecutable(vararg candidates: String): String? {
        return candidates.firstOrNull { candidate ->
            File(candidate).canExecute()
        }
    }

    private fun reserveLocalPort(): Int {
        return ServerSocket(0).use { socket ->
            socket.localPort
        }
    }

    private fun waitForPort(host: String, port: Int, process: Process): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) return false
            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 250)
                }
            }.isSuccess
            if (connected) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun Process.waitForSuccess(timeoutSeconds: Long = 10): Boolean {
        if (!waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            destroyForcibly()
            return false
        }
        return exitValue() == 0
    }

    private fun File.readIfPresent(): String {
        return takeIf { it.exists() }?.readText().orEmpty()
    }

    private fun skipTest(reason: String): Nothing {
        assumeTrue(reason, false)
        error(reason)
    }

    private class OpenSshServer(
        val port: Int,
        private val process: Process
    ) : AutoCloseable {
        override fun close() {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    private class ThrowingEncodedEd25519PublicKey(
        private val rawPublicKey: ByteArray
    ) : PublicKey, EdECPublicKey {
        override fun getAlgorithm(): String = "EdDSA"

        override fun getFormat(): String = "X.509"

        override fun getEncoded(): ByteArray {
            error("don't know how to encode keyL: com.android.org.conscrypt.OpenSSLEdDSAPublicKey@ad30ed04")
        }

        override fun getParams(): NamedParameterSpec = NamedParameterSpec.ED25519

        override fun getPoint(): EdECPoint {
            val yBytes = rawPublicKey.copyOf()
            val xOdd = (yBytes[31].toInt() and 0x80) != 0
            yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
            return EdECPoint(xOdd, BigInteger(1, yBytes.reversedArray()))
        }
    }

    private companion object {
        const val TEST_USERNAME = "tester"
        const val TEST_PASSWORD = "peaches-password"
    }
}
