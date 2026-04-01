package com.majordaftapps.sshpeaches.app.security

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
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
