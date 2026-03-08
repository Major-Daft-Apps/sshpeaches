package com.majordaftapps.sshpeaches.app.util

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.DSAPrivateKeyParameters
import org.bouncycastle.crypto.params.DSAPublicKeyParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.math.ec.FixedPointCombMultiplier

enum class IdentityKeyAlgorithm {
    ED25519,
    RSA,
    ECDSA
}

enum class IdentityEcdsaCurve(val standardName: String) {
    P256("secp256r1"),
    P384("secp384r1"),
    P521("secp521r1")
}

data class IdentityKeyGenerationSpec(
    val algorithm: IdentityKeyAlgorithm = IdentityKeyAlgorithm.ED25519,
    val rsaBits: Int = 4096,
    val ecdsaCurve: IdentityEcdsaCurve = IdentityEcdsaCurve.P256,
    val comment: String = "",
    val keyPassphrase: String? = null
)

data class GeneratedIdentityKeyPair(
    val privateKey: String,
    val publicKey: String,
    val fingerprint: String
)

object SshKeyGenerator {
    fun generate(spec: IdentityKeyGenerationSpec): GeneratedIdentityKeyPair {
        val keyPair = when (spec.algorithm) {
            IdentityKeyAlgorithm.ED25519 -> {
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            }
            IdentityKeyAlgorithm.RSA -> {
                KeyPairGenerator.getInstance("RSA").apply {
                    initialize(spec.rsaBits.coerceIn(2048, 8192))
                }.generateKeyPair()
            }
            IdentityKeyAlgorithm.ECDSA -> {
                KeyPairGenerator.getInstance("EC").apply {
                    initialize(ECGenParameterSpec(spec.ecdsaCurve.standardName))
                }.generateKeyPair()
            }
        }
        val privatePem = if (spec.keyPassphrase.isNullOrBlank()) {
            toPkcs8Pem(keyPair.private.encoded)
        } else {
            toEncryptedPkcs8Pem(
                encodedKey = keyPair.private.encoded,
                passphrase = spec.keyPassphrase
            )
        }
        val publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(PublicKeyFactory.createKey(keyPair.public.encoded))
        val publicKey = toOpenSshPublicLine(publicBlob, spec.comment)
        val fingerprint = computeSshPublicKeyFingerprint(publicKey) ?: fallbackFingerprint(publicBlob)
        return GeneratedIdentityKeyPair(
            privateKey = privatePem,
            publicKey = publicKey,
            fingerprint = fingerprint
        )
    }

    fun derivePublicKeyFromPrivate(privateKeyMaterial: String, comment: String = ""): String? {
        val pem = parsePem(privateKeyMaterial) ?: return null
        val privateParams = runCatching {
            when (pem.type) {
                "PRIVATE KEY" -> PrivateKeyFactory.createKey(pem.body)
                "RSA PRIVATE KEY", "EC PRIVATE KEY", "DSA PRIVATE KEY", "OPENSSH PRIVATE KEY" ->
                    OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(pem.body)
                else -> null
            }
        }.getOrNull() ?: return null

        val publicParams = derivePublicParameters(privateParams) ?: return null
        val publicBlob = runCatching { OpenSSHPublicKeyUtil.encodePublicKey(publicParams) }.getOrNull() ?: return null
        return toOpenSshPublicLine(publicBlob, comment)
    }

    private fun derivePublicParameters(privateParams: AsymmetricKeyParameter): AsymmetricKeyParameter? = when (privateParams) {
        is RSAPrivateCrtKeyParameters -> {
            RSAKeyParameters(false, privateParams.modulus, privateParams.publicExponent)
        }
        is ECPrivateKeyParameters -> {
            val q = FixedPointCombMultiplier().multiply(privateParams.parameters.g, privateParams.d)
            ECPublicKeyParameters(q, privateParams.parameters)
        }
        is DSAPrivateKeyParameters -> {
            val y = privateParams.parameters.g.modPow(privateParams.x, privateParams.parameters.p)
            DSAPublicKeyParameters(y, privateParams.parameters)
        }
        is Ed25519PrivateKeyParameters -> privateParams.generatePublicKey()
        else -> null
    }

    private fun toPkcs8Pem(encodedKey: ByteArray): String {
        return toPem("PRIVATE KEY", encodedKey)
    }

    private fun toEncryptedPkcs8Pem(encodedKey: ByteArray, passphrase: String): String {
        val candidateAlgorithms = listOf(
            "PBEWithHmacSHA256AndAES_256",
            "PBEWithHmacSHA1AndAES_256",
            "PBEWithSHA1AndDESede"
        )
        candidateAlgorithms.forEach { algorithm ->
            runCatching {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val paramSpec = PBEParameterSpec(salt, 12_000)
                val secretKey = SecretKeyFactory.getInstance(algorithm).generateSecret(PBEKeySpec(passphrase.toCharArray()))
                val cipher = Cipher.getInstance(algorithm)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec)
                val encrypted = cipher.doFinal(encodedKey)
                val encryptedInfo = EncryptedPrivateKeyInfo(cipher.parameters, encrypted)
                return toPem("ENCRYPTED PRIVATE KEY", encryptedInfo.encoded)
            }
        }
        return toPkcs8Pem(encodedKey)
    }

    private fun toPem(type: String, body: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(body)
        val wrapped = base64.chunked(64).joinToString("\n")
        return buildString {
            appendLine("-----BEGIN $type-----")
            appendLine(wrapped)
            append("-----END $type-----")
        }
    }

    private fun toOpenSshPublicLine(blob: ByteArray, comment: String): String {
        val keyType = readSshString(blob) ?: "ssh-key"
        val base64 = Base64.getEncoder().encodeToString(blob)
        val suffix = comment.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "$keyType $base64$suffix"
    }

    private fun readSshString(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val length = ((blob[0].toInt() and 0xFF) shl 24) or
            ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or
            (blob[3].toInt() and 0xFF)
        if (length <= 0 || 4 + length > blob.size) return null
        return blob.copyOfRange(4, 4 + length).toString(Charsets.UTF_8)
    }

    private fun fallbackFingerprint(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
    }

    private fun parsePem(input: String): PemBlock? {
        val text = input.trim()
        val match = PEM_REGEX.find(text) ?: return null
        val type = match.groupValues[1].trim()
        val bodyText = match.groupValues[2].replace(Regex("\\s"), "")
        if (bodyText.isBlank()) return null
        val body = runCatching { Base64.getDecoder().decode(bodyText) }.getOrNull() ?: return null
        return PemBlock(type = type, body = body)
    }

    private data class PemBlock(
        val type: String,
        val body: ByteArray
    )

    private val PEM_REGEX = Regex(
        pattern = "-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
}
