package com.majordaftapps.sshpeaches.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedKeyFingerprintTest {

    private val yours = SshKeyGenerator.generate(IdentityKeyGenerationSpec(comment = "yours"))
    private val theirs = SshKeyGenerator.generate(IdentityKeyGenerationSpec(comment = "theirs"))

    private fun derived(privateKey: String) = SshKeyGenerator.derivePublicKeyFromPrivate(privateKey).orEmpty()

    @Test
    fun yourOwnKeyMatchesItsFingerprint() {
        assertTrue(importedKeyMatchesFingerprint(yours.privateKey, derived(yours.privateKey), yours.fingerprint, true))
    }

    @Test
    fun anotherKeyClaimingYourFingerprintIsRejected() {
        assertFalse(importedKeyMatchesFingerprint(theirs.privateKey, derived(theirs.privateKey), yours.fingerprint, true))
        assertFalse(importedKeyMatchesFingerprint(theirs.privateKey, derived(theirs.privateKey), yours.fingerprint, false))
    }

    @Test
    fun identitiesFingerprintedFromPastedKeyTextStillMatch() {
        val textFingerprint = sshKeyTextFingerprint(yours.privateKey)
        assertTrue(importedKeyMatchesFingerprint(yours.privateKey, derived(yours.privateKey), textFingerprint, true))
    }

    @Test
    fun aKeyThatCantBeCheckedOnlyFillsAnEmptyIdentity() {
        assertTrue(importedKeyMatchesFingerprint("opaque", "", yours.fingerprint, identityHasKey = false))
        assertFalse(importedKeyMatchesFingerprint("opaque", "", yours.fingerprint, identityHasKey = true))
    }
}
