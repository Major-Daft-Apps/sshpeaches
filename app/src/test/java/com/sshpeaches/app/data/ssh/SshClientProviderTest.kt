package com.majordaftapps.sshpeaches.app.data.ssh

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.util.IdentityKeyAlgorithm
import com.majordaftapps.sshpeaches.app.util.IdentityKeyGenerationSpec
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import java.security.Key
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshClientProviderTest {

    @get:Rule
    val temp = TemporaryFolder()

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

    private companion object {
        const val TEST_USERNAME = "tester"
        const val TEST_PASSWORD = "peaches-password"
    }
}
