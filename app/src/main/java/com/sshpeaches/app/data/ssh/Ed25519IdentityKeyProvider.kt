package com.majordaftapps.sshpeaches.app.data.ssh

import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

internal interface RawEd25519PrivateKey {
    fun rawSeed(): ByteArray
}

internal interface RawEd25519PublicKey {
    fun rawPublicKey(): ByteArray
}

internal object Ed25519IdentityKeyProvider {
    private const val OPENSSH_ED25519 = "ssh-ed25519"
    private const val ED25519_OID = "1.3.101.112"
    private const val ED25519_KEY_SIZE = 32
    private val PKCS8_PRIVATE_KEY_HEADER = Base64.getDecoder().decode("MC4CAQEwBQYDK2VwBCIEIA")
    private val X509_PUBLIC_KEY_HEADER = Base64.getDecoder().decode("MCowBQYDK2VwAyEA")

    fun load(
        @Suppress("UNUSED_PARAMETER")
        client: SSHClient,
        privateKeyMaterial: String,
        publicKeyMaterial: String?,
        passphrase: String?
    ): KeyProvider? {
        val pem = parsePem(privateKeyMaterial) ?: return null
        val encodedPkcs8 = when (pem.type) {
            "PRIVATE KEY" -> PKCS8EncodedKeySpec(pem.body).encoded
            "ENCRYPTED PRIVATE KEY" -> decryptPkcs8PrivateKey(pem.body, passphrase)?.encoded
            else -> null
        } ?: return null
        val seed = extractEd25519Seed(encodedPkcs8) ?: return null
        val rawPublicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        // Parse the stored public key when present to validate its shape, but prefer the
        // derived public key so auth always pairs the exact private/public halves.
        parseOpenSshPublicKey(publicKeyMaterial)
        return Ed25519RawKeyProvider(
            publicKey = StableEd25519PublicKey(rawPublicKey),
            privateKey = StableEd25519PrivateKey(seed)
        )
    }

    private fun extractEd25519Seed(encodedPkcs8: ByteArray): ByteArray? {
        return runCatching {
            val info = PrivateKeyInfo.getInstance(encodedPkcs8)
            if (info.privateKeyAlgorithm.algorithm.id != ED25519_OID) return null
            val octets = ASN1OctetString.getInstance(info.parsePrivateKey()).octets
            octets.takeIf { it.size == ED25519_KEY_SIZE }
        }.getOrNull()
    }

    private fun parseOpenSshPublicKey(publicKeyMaterial: String?): ByteArray? {
        val parts = publicKeyMaterial
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$OPENSSH_ED25519 ") }
            ?.split(Regex("\\s+"), limit = 3)
            ?: return null
        if (parts.size < 2) return null
        return runCatching {
            val blob = Base64.getDecoder().decode(parts[1])
            val buffer = Buffer.PlainBuffer(blob)
            val keyType = buffer.readString()
            if (keyType != OPENSSH_ED25519) return null
            buffer.readBytes().takeIf { it.size == ED25519_KEY_SIZE }
        }.getOrNull()
    }

    private fun decryptPkcs8PrivateKey(body: ByteArray, passphrase: String?): PKCS8EncodedKeySpec? {
        if (passphrase.isNullOrBlank()) return null
        return runCatching {
            val encryptedInfo = EncryptedPrivateKeyInfo(body)
            val algorithm = encryptedInfo.algName
            val secretKey = SecretKeyFactory.getInstance(algorithm)
                .generateSecret(PBEKeySpec(passphrase.toCharArray()))
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedInfo.algParameters)
            encryptedInfo.getKeySpec(cipher)
        }.getOrNull()
    }

    private fun parsePem(input: String): PemBlock? {
        val match = PEM_REGEX.find(input.trim()) ?: return null
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

    private class Ed25519RawKeyProvider(
        private val publicKey: PublicKey,
        private val privateKey: PrivateKey
    ) : KeyProvider {
        override fun getPrivate(): PrivateKey = privateKey

        override fun getPublic(): PublicKey = publicKey

        override fun getType(): KeyType = KeyType.ED25519
    }

    private class StableEd25519PrivateKey(
        private val seed: ByteArray
    ) : PrivateKey, RawEd25519PrivateKey {
        override fun getAlgorithm(): String = "Ed25519"

        override fun getFormat(): String = "PKCS#8"

        override fun getEncoded(): ByteArray = PKCS8_PRIVATE_KEY_HEADER + seed.copyOf()

        override fun rawSeed(): ByteArray = seed.copyOf()
    }

    private class StableEd25519PublicKey(
        private val publicKey: ByteArray
    ) : PublicKey, RawEd25519PublicKey {
        override fun getAlgorithm(): String = "Ed25519"

        override fun getFormat(): String = "X.509"

        override fun getEncoded(): ByteArray = X509_PUBLIC_KEY_HEADER + publicKey.copyOf()

        override fun rawPublicKey(): ByteArray = publicKey.copyOf()
    }

    private val PEM_REGEX = Regex(
        pattern = "-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
}
