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
