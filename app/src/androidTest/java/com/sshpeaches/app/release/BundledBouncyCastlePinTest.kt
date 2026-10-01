package com.majordaftapps.sshpeaches.app.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import javax.crypto.SecretKeyFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-lane regression: creating an SSH client swaps Android's "BC" provider for the bundled
 * Bouncy Castle provider process-wide. With R8 stripping the provider's reflectively loaded
 * classes, "PBKDF2WithHmacSHA256" then disappeared and setting the PIN failed. Debug builds are
 * not shrunk, so only the minified release lane can catch this.
 */
@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class BundledBouncyCastlePinTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    @Test
    fun pinCanBeSetAndVerifiedAfterSshInstallsBundledBouncyCastle() {
        // The swap createClient() performs on the first SSH connection.
        SshClientProvider.installBundledBouncyCastleProvider()

        assertNotNull(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"))
        SecurityManager.setPin("2468")
        assertTrue(SecurityManager.isPinSet())
        SecurityManager.lock()
        assertTrue(SecurityManager.verifyPin("2468"))
    }
}
