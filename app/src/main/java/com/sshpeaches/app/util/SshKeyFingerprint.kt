package com.majordaftapps.sshpeaches.app.util

import java.security.MessageDigest
import java.util.Base64

fun computeSshPublicKeyFingerprint(publicKeyText: String): String? {
    val line = publicKeyText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
        ?: return null

    val parts = line.split(Regex("\\s+"))
    val base64Blob = when {
        parts.size >= 2 && isLikelySshKeyType(parts[0]) -> parts[1]
        parts.size == 1 -> parts[0]
        else -> return null
    }

    val keyBlob = runCatching { Base64.getDecoder().decode(base64Blob) }.getOrNull() ?: return null
    if (keyBlob.isEmpty()) return null

    val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
    val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
    return "SHA256:$encoded"
}

private fun isLikelySshKeyType(value: String): Boolean {
    return value.startsWith("ssh-") ||
        value.startsWith("ecdsa-") ||
        value.startsWith("sk-")
}

/**
 * The fingerprint the identity editor gives a key pasted without its public half: a hash of the key text itself.
 */
fun sshKeyTextFingerprint(keyText: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(keyText.toByteArray())
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

/**
 * Whether an imported private key belongs to an identity with [expectedFingerprint]: its public half
 * ([derivedPublicKey]) has that fingerprint, or the key text does (identities created from a pasted private key).
 * A key that can't be checked either way (an encrypted key whose public half can't be derived) is only accepted
 * for an identity without a key, so an import can never swap out a key you already have.
 */
fun importedKeyMatchesFingerprint(
    privateKeyText: String,
    derivedPublicKey: String,
    expectedFingerprint: String,
    identityHasKey: Boolean
): Boolean {
    val expected = expectedFingerprint.trim()
    if (expected.isEmpty()) return !identityHasKey
    if (derivedPublicKey.isNotBlank()) {
        if (computeSshPublicKeyFingerprint(derivedPublicKey) == expected) return true
    }
    if (sshKeyTextFingerprint(privateKeyText) == expected || sshKeyTextFingerprint(privateKeyText.trim()) == expected) {
        return true
    }
    return derivedPublicKey.isBlank() && !identityHasKey
}
