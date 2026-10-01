package com.majordaftapps.sshpeaches.app.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On Android the platform "Ed25519" KeyPairGenerator threw "Not initialized" until an SSH
 * connection had installed the bundled Bouncy Castle provider, so generating the default key type
 * failed in a fresh app session. JVM unit tests cannot see this; the JDK's own Ed25519 works.
 */
@RunWith(AndroidJUnit4::class)
class SshKeyGeneratorDeviceTest {

    @Test
    fun generatesEd25519KeyOnDevice() {
        val generated = SshKeyGenerator.generate(
            IdentityKeyGenerationSpec(algorithm = IdentityKeyAlgorithm.ED25519, comment = "device-test")
        )

        assertTrue(generated.publicKey.startsWith("ssh-ed25519 "))
        assertTrue(generated.privateKey.contains("PRIVATE KEY"))
    }
}
