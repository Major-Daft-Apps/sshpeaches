package com.majordaftapps.sshpeaches.app.security

import android.content.SharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityManagerVaultTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    @Test
    fun legacySecretsRemainReadableUntilPinSet() {
        val host = hostFixture("legacy-host")

        AppStateSeeder.seedHost(host, password = "hunter2")

        val prefs = securePrefs()
        assertFalse(prefs.contains("storage_version"))
        assertNotNull(prefs.getString("pwd_${host.id}", null))
        assertNull(prefs.getString("vault_pwd_${host.id}", null))
        assertEquals("hunter2", SecurityManager.getHostPassword(host.id))
    }

    @Test
    fun setPinLeavesAppUnlockedAndVaultReadable() {
        val host = hostFixture("unlocked-after-pin-host")
        AppStateSeeder.seedHost(host, password = "keep-me")

        SecurityManager.setPin("2468")

        assertTrue(SecurityManager.isPinSet())
        assertFalse(SecurityManager.isLocked())
        assertEquals("keep-me", SecurityManager.getHostPassword(host.id))
        assertTrue(SecurityManager.verifyPin("2468"))
        assertFalse(SecurityManager.isLocked())
    }

    @Test
    fun pinWorksWhileTheBcProviderIsBeingSwapped() {
        // The first SSH client replaces "BC" with the bundled Bouncy Castle; between the remove and the add there's
        // no "BC" at all, and PIN checks at startup failed with "PBKDF2WithHmacSHA256 SecretKeyFactory not available".
        val original = java.security.Security.getProvider("BC")
        java.security.Security.removeProvider("BC")
        try {
            SecurityManager.setPin("2468")
            SecurityManager.lock()
            assertTrue(SecurityManager.verifyPin("2468"))
        } finally {
            if (java.security.Security.getProvider("BC") == null && original != null) {
                java.security.Security.addProvider(original)
            }
        }
    }

    @Test
    fun aNewPinIsBoundToTheKeystore() {
        val host = hostFixture("bound-pin-host")
        AppStateSeeder.seedHost(host, password = "bound-secret")
        SecurityManager.setPin("246813")

        assertTrue(SecurityManager.isPinBoundToKeystore())
        SecurityManager.lock()
        assertFalse(SecurityManager.verifyPin("135792"))
        assertTrue(SecurityManager.verifyPin("246813"))
        assertEquals("bound-secret", SecurityManager.getHostPassword(host.id))
    }

    @Test
    fun withoutTheKeystoreKeyTheRightPinOpensNothing() {
        // What a copy of the app's files on another device amounts to: the PIN alone can't unwrap the vault.
        SecurityManager.setPin("246813")
        SecurityManager.lock()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("sshpeaches_vault_pin_binding")

        assertFalse(runCatching { SecurityManager.verifyPin("246813") }.getOrDefault(false))
        assertTrue(SecurityManager.isLocked())
    }

    @Test
    fun aVaultFromBeforeTheKeystoreBindingIsUpgradedOnTheNextUnlock() {
        val host = hostFixture("upgraded-pin-host")
        AppStateSeeder.seedHost(host, password = "kept-secret")
        SecurityManager.setPin("2468")
        SecurityManager.rewrapPinWithoutKeystoreForTests("2468")
        assertFalse(SecurityManager.isPinBoundToKeystore())
        SecurityManager.lock()

        assertTrue(SecurityManager.verifyPin("2468"))
        assertTrue(SecurityManager.isPinBoundToKeystore())
        assertEquals("kept-secret", SecurityManager.getHostPassword(host.id))
        SecurityManager.lock()
        assertTrue("the upgraded vault opens again", SecurityManager.verifyPin("2468"))
    }

    private fun deviceIsSecure(): Boolean =
        (InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.content.Context.KEYGUARD_SERVICE)
            as android.app.KeyguardManager).isDeviceSecure

    @Test
    fun theScreenLockSealNeedsAScreenLockOnThePhone() {
        org.junit.Assume.assumeFalse("the device has a screen lock", deviceIsSecure())
        SecurityManager.setPin("246813")
        assertFalse(SecurityManager.canRequireScreenLock())
        assertTrue(runCatching { SecurityManager.setScreenLockRequired(true) }.isFailure)
        assertFalse(SecurityManager.screenLockRequired().value)
        SecurityManager.lock()
        assertTrue("the PIN still opens the vault", SecurityManager.verifyPin("246813"))
    }

    @Test
    fun withTheScreenLockSealThePinOpensTheVaultOnlyThroughItsKey() {
        // Needs a screen lock confirmed in the last few minutes (the test lane unlocks the emulator with its PIN).
        org.junit.Assume.assumeTrue("the device has no screen lock", deviceIsSecure())
        val host = hostFixture("sealed-host")
        AppStateSeeder.seedHost(host, password = "sealed-secret")
        SecurityManager.setPin("246813")
        SecurityManager.setScreenLockRequired(true)
        assertTrue(SecurityManager.screenLockRequired().value)
        SecurityManager.lock()

        assertFalse(SecurityManager.verifyPin("135792"))
        assertTrue(SecurityManager.verifyPin("246813"))
        assertEquals("sealed-secret", SecurityManager.getHostPassword(host.id))

        // Removing the phone's screen lock destroys this key: the right PIN then opens nothing.
        SecurityManager.lock()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("sshpeaches_vault_screen_lock")
        val lost = runCatching { SecurityManager.verifyPin("246813") }.exceptionOrNull()
        assertTrue("$lost", lost is SecurityManager.ScreenLockKeyLostException)
        assertTrue(SecurityManager.isLocked())
    }

    @Test
    fun changingThePinTurnsTheScreenLockSealOff() {
        org.junit.Assume.assumeTrue("the device has no screen lock", deviceIsSecure())
        SecurityManager.setPin("246813")
        SecurityManager.setScreenLockRequired(true)
        SecurityManager.setPin("975310")
        assertFalse(SecurityManager.screenLockRequired().value)
        SecurityManager.lock()
        assertTrue(SecurityManager.verifyPin("975310"))
    }

    @Test
    fun setPinStoresNoFastPinHash() {
        SecurityManager.setPin("2468")

        // The PIN-wrapped vault key is the only verifier; a plain salted hash was an offline oracle.
        assertFalse(securePrefs().contains("pin_hash"))
        assertFalse(securePrefs().contains("pin_salt"))
        SecurityManager.lock()
        assertTrue(SecurityManager.verifyPin("2468"))
    }

    @Test
    fun repeatedWrongPinsLockOutEvenTheCorrectPin() {
        SecurityManager.setPin("2468")
        SecurityManager.lock()

        repeat(5) { assertFalse(SecurityManager.verifyPin("0000")) }

        assertTrue(SecurityManager.pinLockoutRemainingMillis() > 0L)
        assertFalse(SecurityManager.verifyPin("2468"))
        assertTrue(SecurityManager.isLocked())
    }

    @Test
    fun backgroundTimeoutLocksOnceElapsed() {
        SecurityManager.setPin("2468")

        SecurityManager.markBackgrounded(lockAfterMs = 0L)

        assertTrue(SecurityManager.lockIfBackgroundTimeoutElapsed())
        assertTrue(SecurityManager.isLocked())
    }

    @Test
    fun backgroundTimeoutDoesNotLockEarly() {
        SecurityManager.setPin("2468")

        SecurityManager.markBackgrounded(lockAfterMs = 60_000L)

        assertFalse(SecurityManager.lockIfBackgroundTimeoutElapsed())
        assertFalse(SecurityManager.isLocked())
    }

    @Test
    fun undecryptableSecureStoreIsResetSoPinCanBeSet() {
        val host = hostFixture("orphaned-store-host")
        AppStateSeeder.seedHost(host, password = "unrecoverable")
        securePrefs()

        // Simulate the Keystore master key being lost while secure_store.xml survives, which
        // previously left SecurityManager uninitialized and made Set PIN silently do nothing.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        SecurityManager.resetForTesting()
        SecurityManager.init(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

        SecurityManager.setPin("2468")

        assertTrue(SecurityManager.isPinSet())
        assertFalse(SecurityManager.isLocked())
        assertTrue(SecurityManager.secureStorageResetState().value)
        assertNull(SecurityManager.getHostPassword(host.id))
        SecurityManager.lock()
        assertTrue(SecurityManager.verifyPin("2468"))
    }

    @Test
    fun setPinMigratesLegacySecretsAndVerifyPinUnlocksVault() {
        val host = hostFixture("migrated-host")
        val identity = identityFixture("migrated-identity")

        AppStateSeeder.seedHost(host, password = "open-sesame")
        AppStateSeeder.seedIdentity(
            identity = identity,
            privateKey = "PRIVATE-KEY-MATERIAL",
            publicKey = "PUBLIC-KEY-MATERIAL",
            keyPassphrase = "identity-passphrase"
        )

        SecurityManager.setPin("2468")

        val prefs = securePrefs()
        assertEquals(1, prefs.getInt("storage_version", 0))
        assertNull(prefs.getString("pwd_${host.id}", null))
        assertNull(prefs.getString("ident_${identity.id}", null))
        assertNull(prefs.getString("ident_pass_${identity.id}", null))
        assertNotNull(prefs.getString("vault_pwd_${host.id}", null))
        assertNotNull(prefs.getString("vault_ident_${identity.id}", null))
        assertNotNull(prefs.getString("vault_ident_pass_${identity.id}", null))

        SecurityManager.lock()
        assertFalse(SecurityManager.verifyPin("0000"))
        assertTrue(SecurityManager.verifyPin("2468"))
        assertEquals("open-sesame", SecurityManager.getHostPassword(host.id))
        assertEquals("PRIVATE-KEY-MATERIAL", SecurityManager.getIdentityKey(identity.id))
        assertEquals("identity-passphrase", SecurityManager.getIdentityKeyPassphrase(identity.id))
        assertEquals("PUBLIC-KEY-MATERIAL", SecurityManager.getIdentityPublicKey(identity.id))
    }

    @Test
    fun clearPinRevertsVaultSecretsToLegacyStorage() {
        val host = hostFixture("reverted-host")
        val identity = identityFixture("reverted-identity")

        AppStateSeeder.seedHost(host, password = "pa55")
        AppStateSeeder.seedIdentity(
            identity = identity,
            privateKey = "LEGACY-PRIVATE-KEY",
            publicKey = "LEGACY-PUBLIC-KEY",
            keyPassphrase = "vault-pass"
        )
        SecurityManager.setPin("2468")

        SecurityManager.clearPin()

        val prefs = securePrefs()
        assertFalse(prefs.contains("storage_version"))
        assertNotNull(prefs.getString("pwd_${host.id}", null))
        assertNotNull(prefs.getString("ident_${identity.id}", null))
        assertNotNull(prefs.getString("ident_pass_${identity.id}", null))
        assertNull(prefs.getString("vault_pwd_${host.id}", null))
        assertNull(prefs.getString("vault_ident_${identity.id}", null))
        assertNull(prefs.getString("vault_ident_pass_${identity.id}", null))
        assertEquals("pa55", SecurityManager.getHostPassword(host.id))
        assertEquals("LEGACY-PRIVATE-KEY", SecurityManager.getIdentityKey(identity.id))
        assertEquals("vault-pass", SecurityManager.getIdentityKeyPassphrase(identity.id))
        assertEquals("LEGACY-PUBLIC-KEY", SecurityManager.getIdentityPublicKey(identity.id))
    }

    @Test
    fun exportImportProtectedSecretsWorkWhileVaultBacked() {
        val sourceHost = hostFixture("source-host")
        val sourceIdentity = identityFixture("source-identity")
        val importedHost = hostFixture("imported-host")
        val importedIdentity = identityFixture("imported-identity")

        AppStateSeeder.seedHost(sourceHost, password = "vault-host-password")
        AppStateSeeder.seedIdentity(
            identity = sourceIdentity,
            privateKey = "VAULT-PRIVATE-KEY",
            publicKey = "VAULT-PUBLIC-KEY",
            keyPassphrase = "vault-identity-passphrase"
        )

        SecurityManager.setPin("2468")
        SecurityManager.lock()
        assertTrue(SecurityManager.verifyPin("2468"))

        val hostPayload = SecurityManager.exportHostPasswordPayload(sourceHost.id, "export-passphrase")
        val keyPayload = SecurityManager.exportIdentityKeyPayload(sourceIdentity.id, "export-passphrase")
        val keyPassphrasePayload = SecurityManager.exportIdentityKeyPassphrasePayload(sourceIdentity.id, "export-passphrase")

        assertNotNull(hostPayload)
        assertNotNull(keyPayload)
        assertNotNull(keyPassphrasePayload)

        SecurityManager.importHostPasswordPayload(importedHost.id, hostPayload!!, "export-passphrase")
        SecurityManager.importIdentityKeyPayload(importedIdentity.id, keyPayload!!, "export-passphrase")
        SecurityManager.importIdentityPublicKey(importedIdentity.id, "IMPORTED-PUBLIC-KEY")
        SecurityManager.importIdentityKeyPassphrasePayload(importedIdentity.id, keyPassphrasePayload!!, "export-passphrase")

        assertEquals("vault-host-password", SecurityManager.getHostPassword(importedHost.id))
        assertEquals("VAULT-PRIVATE-KEY", SecurityManager.getIdentityKey(importedIdentity.id))
        assertEquals("vault-identity-passphrase", SecurityManager.getIdentityKeyPassphrase(importedIdentity.id))
        assertEquals("IMPORTED-PUBLIC-KEY", SecurityManager.getIdentityPublicKey(importedIdentity.id))
    }

    @Test
    fun exportsDeriveTheirKeyOnceAndAcceptOnlyKnownIterationCounts() {
        val sourceHost = hostFixture("kdf-source-host")
        val sourceIdentity = identityFixture("kdf-source-identity")
        val importedHost = hostFixture("kdf-imported-host")
        AppStateSeeder.seedHost(sourceHost, password = "kdf-host-password")
        AppStateSeeder.seedIdentity(sourceIdentity, privateKey = "KDF-PRIVATE-KEY", publicKey = "KDF-PUBLIC-KEY")

        val hostPayload = SecurityManager.exportHostPasswordPayload(sourceHost.id, "export-passphrase")!!
        val keyPayload = SecurityManager.exportIdentityKeyPayload(sourceIdentity.id, "export-passphrase")!!
        val host = decodeTransferPayload(hostPayload)
        val key = decodeTransferPayload(keyPayload)
        assertEquals(600_000, host.getInt("iter"))
        // One export: one salt (one PBKDF2 run), a fresh IV per secret.
        assertEquals(host.getString("salt"), key.getString("salt"))
        assertFalse(host.getString("iv") == key.getString("iv"))

        // The wrong passphrase still fails while the right one's key is cached.
        assertTrue(runCatching { SecurityManager.importHostPasswordPayload(importedHost.id, hostPayload, "wrong-passphrase") }.isFailure)

        // Exports made before 0.14.1 (210,000 iterations) still import; any other count is refused.
        SecurityManager.importHostPasswordPayload(importedHost.id, transferPayload("from-0.14.0", "export-passphrase", 210_000), "export-passphrase")
        assertEquals("from-0.14.0", SecurityManager.getHostPassword(importedHost.id))
        assertTrue(
            runCatching {
                SecurityManager.importHostPasswordPayload(importedHost.id, transferPayload("forged", "export-passphrase", 1), "export-passphrase")
            }.isFailure
        )
        assertEquals("from-0.14.0", SecurityManager.getHostPassword(importedHost.id))
    }

    private fun decodeTransferPayload(payload: String) =
        org.json.JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.NO_WRAP), Charsets.UTF_8))

    /** A payload in the export format with [iterations] of its own, as an older (or forged) export would carry. */
    private fun transferPayload(value: String, passphrase: String, iterations: Int): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        }
        val b64 = { bytes: ByteArray -> android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) }
        val json = org.json.JSONObject()
            .put("v", 1).put("iv", b64(iv)).put("salt", b64(salt)).put("iter", iterations)
            .put("cipher", b64(cipher.doFinal(value.toByteArray(Charsets.UTF_8))))
        return b64(json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun securePrefs(): SharedPreferences {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        SecurityManager.init(appContext)
        SecurityManager.exportIdentityPublicKey("__probe__")
        val field = SecurityManager::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        return field.get(null) as SharedPreferences
    }

    private fun hostFixture(id: String): HostConnection {
        return HostConnection(
            id = id,
            name = id,
            host = "example.com",
            username = "tester",
            preferredAuth = AuthMethod.PASSWORD
        )
    }

    private fun identityFixture(id: String): Identity {
        return Identity(
            id = id,
            label = id,
            fingerprint = "SHA256:$id",
            createdEpochMillis = System.currentTimeMillis(),
            updatedEpochMillis = System.currentTimeMillis(),
            hasPrivateKey = true
        )
    }
}
