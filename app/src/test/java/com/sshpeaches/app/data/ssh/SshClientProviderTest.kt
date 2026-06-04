package com.majordaftapps.sshpeaches.app.data.ssh

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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshClientProviderTest {

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
}
