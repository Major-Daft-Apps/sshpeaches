package com.majordaftapps.sshpeaches.app.data.ssh

import android.content.Context
import android.util.Log
import com.hierynomus.sshj.key.BaseKeyAlgorithm
import com.hierynomus.sshj.key.KeyAlgorithm
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.Signature as JcaSignature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.signature.Signature as SshjSignature
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.kex.KeyExchange
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Minimal SSHJ provider. Callers are responsible for threading/coroutine dispatch.
 */
object SshClientProvider {

    private const val TAG = "SshClientProvider"
    private const val BOUNCY_CASTLE_PROVIDER_NAME = "BC"
    private const val ED25519_OID = "1.3.101.112"
    private const val ED25519_KEY_SIZE = 32
    private val X509_ED25519_PUBLIC_KEY_HEADER = Base64.getDecoder().decode("MCowBQYDK2VwAyEA")
    private val knownHostsWriteLock = Any()
    private val providerInstallLock = Any()
    private const val KEEPALIVE_INTERVAL_SECONDS = 30
    private const val SSH_CHANNEL_WINDOW_SIZE_BYTES = 16L * 1024L * 1024L
    // SSHJ rejects transport packets above 256 KiB. A channel-data payload at
    // that same size becomes larger after SSH/SFTP framing (for example,
    // 262,172 bytes) and tears down the transport during large transfers.
    // Keep the large window for throughput, but advertise a packet size that
    // leaves ample room for protocol framing.
    private const val SSH_CHANNEL_MAX_PACKET_SIZE_BYTES = 32 * 1024
    @Volatile
    private var testingUnavailableKeyExchangeAlgorithms: Set<String> = emptySet()
    @Volatile
    private var hostKeyProviderChecked = false

    data class HostKeyPrompt(
        val host: String,
        val port: Int,
        val fingerprint: String,
        val keyChanged: Boolean
    )

    /**
     * Create a configured SSHClient for the given host.
     *
     * [autoTrustUnknownHostKey] controls unknown-host TOFU behavior.
     * Host-key changes always invoke [onHostKeyPrompt] when available.
     */
    fun createClient(
        context: Context,
        host: HostConnection,
        loggerFactory: LoggerFactory? = null,
        autoTrustUnknownHostKey: Boolean = true,
        onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)? = null
    ): SSHClient {
        // Keep known_hosts pinning enforceable and host-key changes classified correctly.
        val knownHostsFile = File(context.filesDir, "known_hosts")
        return createConfiguredClient(
            knownHostsFile = knownHostsFile,
            host = host,
            loggerFactory = loggerFactory,
            autoTrustUnknownHostKey = autoTrustUnknownHostKey,
            onHostKeyPrompt = onHostKeyPrompt,
            keyExchangeAvailability = testJcaAlgorithmAvailability(testingUnavailableKeyExchangeAlgorithms)
        )
    }

    internal fun setTestingUnavailableKeyExchangeAlgorithms(
        algorithms: Set<String>
    ) {
        testingUnavailableKeyExchangeAlgorithms = algorithms
            .map { it.lowercase() }
            .toSet()
    }

    internal fun clearTestingUnavailableKeyExchangeAlgorithms() {
        testingUnavailableKeyExchangeAlgorithms = emptySet()
    }

    internal fun withTestingUnavailableKeyExchangeAlgorithms(
        algorithms: Set<String>,
        action: () -> Unit
    ) {
        val previous = testingUnavailableKeyExchangeAlgorithms
        setTestingUnavailableKeyExchangeAlgorithms(algorithms)
        try {
            action()
        } finally {
            testingUnavailableKeyExchangeAlgorithms = previous
        }
    }

    internal fun ensureCompatibleHostKeyAlgorithmsAvailableForTesting() {
        ensureCompatibleHostKeyAlgorithmsAvailable()
    }

    internal fun ed25519SignatureForTesting(): SshjSignature {
        return AndroidEd25519Signature(KeyType.ED25519.toString())
    }

    internal fun compatibleKeyExchangeNamesForTesting(
        unavailableAlgorithms: Set<String> = emptySet()
    ): List<String> {
        val availability = testJcaAlgorithmAvailability(unavailableAlgorithms)
        return androidCompatibleKeyExchangeFactories(DefaultConfig().keyExchangeFactories, availability)
            .map { it.name }
    }

    internal fun createClientForTesting(
        knownHostsFile: File,
        host: HostConnection,
        loggerFactory: LoggerFactory? = null,
        autoTrustUnknownHostKey: Boolean = true,
        onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)? = null,
        unavailableKeyExchangeAlgorithms: Set<String> = emptySet()
    ): SSHClient {
        return createConfiguredClient(
            knownHostsFile = knownHostsFile,
            host = host,
            loggerFactory = loggerFactory,
            autoTrustUnknownHostKey = autoTrustUnknownHostKey,
            onHostKeyPrompt = onHostKeyPrompt,
            keyExchangeAvailability = testJcaAlgorithmAvailability(unavailableKeyExchangeAlgorithms)
        )
    }

    internal fun verifyHostKeyForTesting(
        knownHostsFile: File,
        host: String,
        port: Int,
        key: PublicKey,
        autoTrustUnknownHostKey: Boolean = true,
        onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)? = null
    ): Boolean {
        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return InteractiveKnownHosts(
            file = knownHostsFile,
            host = host,
            port = port,
            autoTrustUnknownHostKey = autoTrustUnknownHostKey,
            onHostKeyPrompt = onHostKeyPrompt
        ).verify(host, port, key)
    }

    private fun createConfiguredClient(
        knownHostsFile: File,
        host: HostConnection,
        loggerFactory: LoggerFactory?,
        autoTrustUnknownHostKey: Boolean,
        onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)?,
        keyExchangeAvailability: JcaAlgorithmAvailability = RealJcaAlgorithmAvailability
    ): SSHClient {
        // Keep SSHJ on Android's default provider. bcprov is bundled for key generation,
        // but letting SSHJ auto-register BC breaks transport digests on Android.
        SecurityUtils.setRegisterBouncyCastle(false)
        SecurityUtils.setSecurityProvider(null)
        ensureBundledBouncyCastleProviderInstalled()
        ensureCompatibleHostKeyAlgorithmsAvailable()
        val config = DefaultConfig()
        loggerFactory?.let { config.setLoggerFactory(it) }
        val compatibleKex = androidCompatibleKeyExchangeFactories(config.keyExchangeFactories, keyExchangeAvailability)
        if (compatibleKex.isNotEmpty()) {
            config.keyExchangeFactories = compatibleKex
        }
        val compatibleHostKeys = config.keyAlgorithms
            .map { factory ->
                androidCompatibleEcdsaHostKeyFactory(factory.name)
                    ?: androidCompatibleEd25519KeyFactory(factory.name)
                    ?: factory
            }
            .sortedBy { factory -> androidHostKeyPriority(factory.name) }
        if (compatibleHostKeys.isNotEmpty()) {
            config.keyAlgorithms = compatibleHostKeys
        }
        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
        return SSHClient(config).apply {
            // A larger receive window keeps file transfers from becoming
            // round-trip-bound on fast, higher-latency connections. The packet
            // allowance stays safely below SSHJ's transport packet ceiling.
            connection.setWindowSize(SSH_CHANNEL_WINDOW_SIZE_BYTES)
            connection.setMaxPacketSize(SSH_CHANNEL_MAX_PACKET_SIZE_BYTES)
            addHostKeyVerifier(
                InteractiveKnownHosts(
                    file = knownHostsFile,
                    host = host.host,
                    port = host.port,
                    autoTrustUnknownHostKey = autoTrustUnknownHostKey,
                    onHostKeyPrompt = onHostKeyPrompt
                )
            )
            connectTimeout = 10_000
            timeout = 20_000
            connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SECONDS
        }
    }

    private fun androidCompatibleKeyExchangeFactories(
        factories: List<Factory.Named<KeyExchange>>,
        availability: JcaAlgorithmAvailability = RealJcaAlgorithmAvailability
    ): List<Factory.Named<KeyExchange>> {
        val compatibleFactories = factories.filter { factory ->
            keyExchangeFactoryAvailable(factory.name, availability)
        }
        return compatibleFactories.sortedWith(
            compareBy<Factory.Named<KeyExchange>> { factory -> androidKeyExchangePriority(factory.name) }
                .thenBy { factory -> compatibleFactories.indexOf(factory) }
        )
    }

    private fun keyExchangeFactoryAvailable(
        name: String,
        availability: JcaAlgorithmAvailability
    ): Boolean {
        val lower = name.lowercase()
        return when {
            lower.startsWith("ext-info") -> true
            lower.contains("curve25519") -> availability.supportsKeyExchange(
                keyPairAlgorithm = "X25519",
                keyAgreementAlgorithm = "X25519",
                keyFactoryAlgorithm = "X25519"
            )
            lower.startsWith("ecdh-") -> availability.supportsKeyExchange(
                keyPairAlgorithm = "EC",
                keyAgreementAlgorithm = "ECDH",
                keyFactoryAlgorithm = "EC"
            )
            lower.contains("diffie-hellman") -> availability.supportsKeyExchange(
                keyPairAlgorithm = "DH",
                keyAgreementAlgorithm = "DH",
                keyFactoryAlgorithm = "DH"
            )
            else -> true
        }
    }

    private fun androidKeyExchangePriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.contains("curve25519") -> 0
            lower == "ecdh-sha2-nistp256" -> 1
            lower == "ecdh-sha2-nistp384" -> 2
            lower == "ecdh-sha2-nistp521" -> 3
            lower == "diffie-hellman-group14-sha256" -> 4
            lower == "diffie-hellman-group16-sha512" -> 5
            lower == "diffie-hellman-group18-sha512" -> 6
            lower.contains("diffie-hellman") -> 7
            lower.startsWith("ext-info") -> 99
            else -> 8
        }
    }

    private fun JcaAlgorithmAvailability.supportsKeyExchange(
        keyPairAlgorithm: String,
        keyAgreementAlgorithm: String,
        keyFactoryAlgorithm: String
    ): Boolean {
        return hasKeyPairGenerator(keyPairAlgorithm) &&
            hasKeyAgreement(keyAgreementAlgorithm) &&
            hasKeyFactory(keyFactoryAlgorithm)
    }

    private interface JcaAlgorithmAvailability {
        fun hasKeyPairGenerator(algorithm: String): Boolean
        fun hasKeyAgreement(algorithm: String): Boolean
        fun hasKeyFactory(algorithm: String): Boolean
    }

    private object RealJcaAlgorithmAvailability : JcaAlgorithmAvailability {
        override fun hasKeyPairGenerator(algorithm: String): Boolean =
            canCreateKeyPairGenerator(algorithm)

        override fun hasKeyAgreement(algorithm: String): Boolean =
            canCreateKeyAgreement(algorithm)

        override fun hasKeyFactory(algorithm: String): Boolean =
            canCreateKeyFactory(algorithm)
    }

    private fun testJcaAlgorithmAvailability(unavailableAlgorithms: Set<String>): JcaAlgorithmAvailability {
        val unavailable = unavailableAlgorithms.map { it.lowercase() }.toSet()
        return object : JcaAlgorithmAvailability {
            override fun hasKeyPairGenerator(algorithm: String): Boolean {
                val normalized = algorithm.lowercase()
                return normalized !in unavailable && RealJcaAlgorithmAvailability.hasKeyPairGenerator(algorithm)
            }

            override fun hasKeyAgreement(algorithm: String): Boolean {
                val normalized = algorithm.lowercase()
                return normalized !in unavailable && RealJcaAlgorithmAvailability.hasKeyAgreement(algorithm)
            }

            override fun hasKeyFactory(algorithm: String): Boolean {
                val normalized = algorithm.lowercase()
                return normalized !in unavailable && RealJcaAlgorithmAvailability.hasKeyFactory(algorithm)
            }
        }
    }

    private fun ensureCompatibleHostKeyAlgorithmsAvailable() {
        if (hostKeyProviderChecked && hostKeyAlgorithmsAvailable()) return
        synchronized(providerInstallLock) {
            if (hostKeyAlgorithmsAvailable()) {
                hostKeyProviderChecked = true
                return
            }

            hostKeyProviderChecked = true
            if (!hostKeyAlgorithmsAvailable()) {
                Log.w(
                    TAG,
                    "ECDSA/Ed25519 host-key algorithms are unavailable; SSH negotiation may fall back to RSA or fail."
                )
            }
        }
    }

    private fun hostKeyAlgorithmsAvailable(): Boolean {
        return canCreateKeyFactory("ECDSA") &&
            canCreateKeyFactory("Ed25519") &&
            canCreateSignature("Ed25519")
    }

    private fun canCreateKeyFactory(algorithm: String): Boolean {
        return canCreateWithAnyProvider { provider ->
            KeyFactory.getInstance(algorithm, provider)
        }
    }

    private fun canCreateSignature(algorithm: String): Boolean {
        return canCreateWithAnyProvider { provider ->
            Signature.getInstance(algorithm, provider)
        }
    }

    private fun canCreateKeyPairGenerator(algorithm: String): Boolean {
        return canCreateWithAnyProvider { provider ->
            KeyPairGenerator.getInstance(algorithm, provider)
        }
    }

    private fun canCreateKeyAgreement(algorithm: String): Boolean {
        return canCreateWithAnyProvider { provider ->
            KeyAgreement.getInstance(algorithm, provider)
        }
    }

    private fun canCreateWithAnyProvider(factory: (Provider) -> Any): Boolean {
        val providers = Security.getProviders() ?: return false
        if (providers.isEmpty()) {
            return false
        }
        return providers.any { provider ->
            runCatching { factory(provider) }.isSuccess
        }
    }

    private fun ensureBundledBouncyCastleProviderInstalled() {
        synchronized(providerInstallLock) {
            runCatching {
                val existing = Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME)
                if (existing?.javaClass?.name != BouncyCastleProvider::class.java.name) {
                    if (existing != null) {
                        Security.removeProvider(existing.name)
                    }
                    Security.addProvider(BouncyCastleProvider())
                }
            }.onFailure { error ->
                Log.w(TAG, "Unable to install bundled Bouncy Castle host-key provider", error)
            }
        }
    }

    private fun androidCompatibleEcdsaHostKeyFactory(name: String): Factory.Named<KeyAlgorithm>? {
        return when (name) {
            KeyType.ECDSA256.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA256withECDSA", KeyType.ECDSA256)
            KeyType.ECDSA256_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA256withECDSA", KeyType.ECDSA256_CERT)
            KeyType.ECDSA384.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA384withECDSA", KeyType.ECDSA384)
            KeyType.ECDSA384_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA384withECDSA", KeyType.ECDSA384_CERT)
            KeyType.ECDSA521.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA512withECDSA", KeyType.ECDSA521)
            KeyType.ECDSA521_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA512withECDSA", KeyType.ECDSA521_CERT)
            else -> null
        }
    }

    private fun androidCompatibleEd25519KeyFactory(name: String): Factory.Named<KeyAlgorithm>? {
        return when (name) {
            KeyType.ED25519.toString() -> AndroidEd25519KeyAlgorithmFactory(name, KeyType.ED25519)
            else -> null
        }
    }

    private fun androidHostKeyPriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower == "rsa-sha2-512" -> 0
            lower == "rsa-sha2-256" -> 1
            lower.contains("rsa") -> 2
            lower.contains("ecdsa") -> 3
            lower.contains("ed25519") || lower.contains("eddsa") -> 4
            else -> 5
        }
    }

    private class AndroidEcdsaKeyAlgorithmFactory(
        private val name: String,
        private val jcaAlgorithm: String,
        private val keyType: KeyType
    ) : Factory.Named<KeyAlgorithm> {
        override fun getName(): String = name

        override fun create(): KeyAlgorithm {
            return BaseKeyAlgorithm(
                name,
                AndroidEcdsaSignatureFactory(name, jcaAlgorithm),
                keyType
            )
        }
    }

    private class AndroidEd25519KeyAlgorithmFactory(
        private val name: String,
        private val keyType: KeyType
    ) : Factory.Named<KeyAlgorithm> {
        override fun getName(): String = name

        override fun create(): KeyAlgorithm {
            return BaseKeyAlgorithm(
                name,
                AndroidEd25519SignatureFactory(name),
                keyType
            )
        }
    }

    private class AndroidEd25519SignatureFactory(
        private val name: String
    ) : Factory.Named<SshjSignature> {
        override fun getName(): String = name

        override fun create(): SshjSignature = AndroidEd25519Signature(name)
    }

    private class AndroidEd25519Signature(
        private val signatureName: String
    ) : SshjSignature {
        private var signer: Ed25519Signer? = null

        override fun getSignatureName(): String = signatureName

        override fun initVerify(publicKey: PublicKey) {
            val rawPublicKey = runCatching { extractEd25519PublicKey(publicKey) }
                .getOrElse { throw SSHRuntimeException(it) }
            signer = Ed25519Signer().apply {
                init(false, Ed25519PublicKeyParameters(rawPublicKey, 0))
            }
        }

        override fun initSign(privateKey: PrivateKey) {
            val seed = runCatching { extractEd25519PrivateSeed(privateKey) }
                .getOrElse { throw SSHRuntimeException(it) }
            signer = Ed25519Signer().apply {
                init(true, Ed25519PrivateKeyParameters(seed, 0))
            }
        }

        override fun update(data: ByteArray) {
            update(data, 0, data.size)
        }

        override fun update(data: ByteArray, offset: Int, length: Int) {
            runCatching {
                signer?.update(data, offset, length) ?: error("Ed25519 signature is not initialized.")
            }.getOrElse { throw SSHRuntimeException(it) }
        }

        override fun sign(): ByteArray {
            return runCatching {
                signer?.generateSignature() ?: error("Ed25519 signature is not initialized.")
            }.getOrElse { throw SSHRuntimeException(it) }
        }

        override fun encode(signature: ByteArray): ByteArray = signature

        override fun verify(signature: ByteArray): Boolean {
            val rawSignature = runCatching { extractEd25519Signature(signature, signatureName) }
                .getOrElse { throw SSHRuntimeException(it) }
            return runCatching {
                signer?.verifySignature(rawSignature) ?: error("Ed25519 signature is not initialized.")
            }.getOrElse { throw SSHRuntimeException(it) }
        }

        private fun extractEd25519PrivateSeed(privateKey: PrivateKey): ByteArray {
            if (privateKey is RawEd25519PrivateKey) return privateKey.rawSeed()
            val info = PrivateKeyInfo.getInstance(privateKey.encoded)
            check(info.privateKeyAlgorithm.algorithm.id == ED25519_OID) { "Private key is not Ed25519." }
            val octets = ASN1OctetString.getInstance(info.parsePrivateKey()).octets
            check(octets.size == ED25519_KEY_SIZE) { "Invalid Ed25519 private key length." }
            return octets
        }

        private fun extractEd25519PublicKey(publicKey: PublicKey): ByteArray {
            return extractRawEd25519PublicKey(publicKey)
                ?: error("Unable to extract Ed25519 public key material.")
        }

        private fun extractEd25519Signature(signature: ByteArray, expectedName: String): ByteArray {
            if (signature.size == 64) return signature
            val buffer = Buffer.PlainBuffer(signature)
            val actualName = buffer.readString()
            if (actualName != expectedName) {
                error("Expected '$expectedName' key algorithm, but got: $actualName")
            }
            val rawSignature = buffer.readBytes()
            check(rawSignature.size == 64) { "Invalid Ed25519 signature length." }
            return rawSignature
        }
    }

    private class AndroidEcdsaSignatureFactory(
        private val name: String,
        private val jcaAlgorithm: String
    ) : Factory.Named<SshjSignature> {
        override fun getName(): String = name

        override fun create(): SshjSignature = AndroidEcdsaSignature(name, jcaAlgorithm)
    }

    private class AndroidEcdsaSignature(
        private val signatureName: String,
        private val jcaAlgorithm: String
    ) : SshjSignature {
        private val signature = createSignature()

        override fun getSignatureName(): String = signatureName

        override fun initVerify(publicKey: PublicKey) {
            runCatching { signature.initVerify(publicKey) }
                .getOrElse { throw SSHRuntimeException(it) }
        }

        override fun initSign(privateKey: PrivateKey) {
            runCatching { signature.initSign(privateKey) }
                .getOrElse { throw SSHRuntimeException(it) }
        }

        override fun update(data: ByteArray) {
            update(data, 0, data.size)
        }

        override fun update(data: ByteArray, offset: Int, length: Int) {
            runCatching { signature.update(data, offset, length) }
                .getOrElse { throw SSHRuntimeException(it) }
        }

        override fun sign(): ByteArray {
            return runCatching { signature.sign() }
                .getOrElse { throw SSHRuntimeException(it) }
        }

        override fun encode(signature: ByteArray): ByteArray {
            val (r, s) = runCatching { derDecodeEcdsaSignature(signature) }
                .getOrElse { throw SSHRuntimeException(it) }
            return Buffer.PlainBuffer()
                .putMPInt(r)
                .putMPInt(s)
                .compactData
        }

        override fun verify(signature: ByteArray): Boolean {
            val derSignature = runCatching { sshEcdsaSignatureToDer(signature, signatureName) }
                .getOrElse { throw SSHRuntimeException(it) }
            return runCatching { this.signature.verify(derSignature) }
                .getOrElse { throw SSHRuntimeException(it) }
        }

        private fun createSignature(): JcaSignature {
            return runCatching {
                val provider = Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME)
                if (provider == null) {
                    JcaSignature.getInstance(jcaAlgorithm)
                } else {
                    JcaSignature.getInstance(jcaAlgorithm, provider)
                }
            }.getOrElse { throw SSHRuntimeException(it) }
        }

        private fun sshEcdsaSignatureToDer(signature: ByteArray, expectedName: String): ByteArray {
            val outer = Buffer.PlainBuffer(signature)
            val actualName = outer.readString()
            if (actualName != expectedName) {
                error("Expected '$expectedName' key algorithm, but got: $actualName")
            }
            val inner = Buffer.PlainBuffer(outer.readBytes())
            return derEncodeEcdsaSignature(
                r = inner.readMPInt(),
                s = inner.readMPInt()
            )
        }

        private fun derEncodeEcdsaSignature(r: BigInteger, s: BigInteger): ByteArray {
            val encodedR = derEncodeInteger(r)
            val encodedS = derEncodeInteger(s)
            val length = encodedR.size + encodedS.size
            return byteArrayOf(0x30) + derEncodeLength(length) + encodedR + encodedS
        }

        private fun derEncodeInteger(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            return byteArrayOf(0x02) + derEncodeLength(bytes.size) + bytes
        }

        private fun derDecodeEcdsaSignature(signature: ByteArray): Pair<BigInteger, BigInteger> {
            val reader = DerReader(signature)
            reader.expect(0x30)
            val sequenceLength = reader.readLength()
            val sequenceEnd = reader.position + sequenceLength
            val r = reader.readInteger()
            val s = reader.readInteger()
            check(reader.position == sequenceEnd) { "Unexpected trailing ECDSA signature data." }
            return r to s
        }

        private fun derEncodeLength(length: Int): ByteArray {
            if (length < 0x80) return byteArrayOf(length.toByte())
            val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
            return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
        }

        private class DerReader(private val data: ByteArray) {
            var position: Int = 0
                private set

            fun expect(expected: Int) {
                val actual = readByte()
                check(actual == expected) {
                    "Expected DER tag 0x${expected.toString(16)}, got 0x${actual.toString(16)}."
                }
            }

            fun readLength(): Int {
                val first = readByte()
                if ((first and 0x80) == 0) return first
                val byteCount = first and 0x7F
                check(byteCount in 1..4) { "Unsupported DER length." }
                var length = 0
                repeat(byteCount) {
                    length = (length shl 8) or readByte()
                }
                check(length >= 0 && position + length <= data.size) { "Invalid DER length." }
                return length
            }

            fun readInteger(): BigInteger {
                expect(0x02)
                val length = readLength()
                check(length > 0 && position + length <= data.size) { "Invalid DER integer length." }
                val bytes = data.copyOfRange(position, position + length)
                position += length
                return BigInteger(bytes)
            }

            private fun readByte(): Int {
                check(position < data.size) { "Unexpected end of DER data." }
                return data[position++].toInt() and 0xFF
            }
        }
    }

    /**
     * Removes stored known-host entries for [host]:[port].
     * Returns true when the operation succeeds (including when no matching entry exists).
     */
    fun clearKnownHostEntry(
        context: Context,
        host: String,
        port: Int
    ): Boolean {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) return false
        val knownHostsFile = File(context.filesDir, "known_hosts")
        if (!knownHostsFile.exists()) return true
        return runCatching {
            synchronized(knownHostsWriteLock) {
                val adjustedHost = adjustKnownHostName(normalizedHost, port)
                val hostCandidates = setOf(normalizedHost, adjustedHost)
                val filtered = knownHostsFile.readLines(StandardCharsets.UTF_8)
                    .filterNot { line ->
                        val entry = parseKnownHostEntry(line) ?: return@filterNot false
                        hostCandidates.any(entry::appliesTo)
                    }
                knownHostsFile.writeText(
                    filtered.joinToString(System.lineSeparator()).let { text ->
                        if (text.isBlank()) "" else text + System.lineSeparator()
                    },
                    StandardCharsets.UTF_8
                )
            }
            true
        }.getOrElse { false }
    }

    private class InteractiveKnownHosts(
        private val file: File,
        private val host: String,
        private val port: Int,
        private val autoTrustUnknownHostKey: Boolean,
        private val onHostKeyPrompt: ((HostKeyPrompt) -> Boolean)?
    ) : HostKeyVerifier {

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val normalizedKey = normalizeKnownHostKey(key)
            val keyType = resolveKnownHostKeyType(normalizedKey)
            if (keyType == KeyType.UNKNOWN) {
                logUnableToResolveKeyType(hostname, normalizedKey)
                return false
            }
            val keyBlob = sshPublicKeyBlob(keyType, normalizedKey) ?: run {
                logUnableToResolveKeyType(hostname, normalizedKey)
                return false
            }
            val adjustedHost = adjustKnownHostName(hostname, port)
            var matchingTypeSeen = false
            readKnownHostEntries().forEach { entry ->
                if (entry.keyType == keyType && entry.appliesTo(adjustedHost)) {
                    matchingTypeSeen = true
                    if (entry.keyBlob.contentEquals(keyBlob)) return true
                }
            }
            return if (matchingTypeSeen) {
                hostKeyChangedAction(adjustedHost, normalizedKey)
            } else {
                hostKeyUnverifiableAction(adjustedHost, normalizedKey)
            }
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
            val adjustedHost = adjustKnownHostName(hostname, port)
            return readKnownHostEntries()
                .filter { entry -> entry.appliesTo(adjustedHost) }
                .map { entry -> entry.keyType.toString() }
                .distinct()
        }

        private fun hostKeyUnverifiableAction(hostname: String, key: PublicKey): Boolean {
            if (autoTrustUnknownHostKey) {
                return rememberAcceptedHostKey(hostname, key, replaceExisting = false)
            }
            val prompt = HostKeyPrompt(
                host = host,
                port = port,
                fingerprint = fingerprintSha256(key),
                keyChanged = false
            )
            val trusted = onHostKeyPrompt?.invoke(prompt) ?: false
            if (!trusted) return false
            return rememberAcceptedHostKey(hostname, key, replaceExisting = false)
        }

        private fun hostKeyChangedAction(hostname: String, key: PublicKey): Boolean {
            val prompt = HostKeyPrompt(
                host = host,
                port = port,
                fingerprint = fingerprintSha256(key),
                keyChanged = true
            )
            val trusted = onHostKeyPrompt?.invoke(prompt) ?: false
            if (!trusted) return false
            return rememberAcceptedHostKey(hostname, key, replaceExisting = true)
        }

        private fun rememberAcceptedHostKey(
            hostname: String,
            key: PublicKey,
            replaceExisting: Boolean
        ): Boolean {
            return runCatching {
                synchronized(knownHostsWriteLock) {
                    val normalizedKey = normalizeKnownHostKey(key)
                    val keyType = resolveKnownHostKeyType(normalizedKey)
                    if (keyType == KeyType.UNKNOWN) {
                        logUnableToResolveKeyType(hostname, normalizedKey)
                        return false
                    }
                    val keyBlob = sshPublicKeyBlob(keyType, normalizedKey) ?: run {
                        logUnableToResolveKeyType(hostname, normalizedKey)
                        return false
                    }
                    val line = knownHostLine(hostname, keyType, keyBlob)
                    if (replaceExisting) {
                        replaceHostKeyLine(hostname, keyType, line)
                    } else {
                        appendHostKeyLine(line)
                    }
                }
                true
            }.getOrElse { error ->
                Log.w(TAG, "Failed to persist accepted host key for $hostname", error)
                true
            }
        }

        private fun normalizeKnownHostKey(key: PublicKey): PublicKey {
            if (isEd25519Key(key)) {
                extractRawEd25519PublicKey(key)?.let { rawKey ->
                    return StableEd25519PublicKey(rawKey)
                }
            }
            val encoded = runCatching { key.encoded }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return key
            val candidates = buildList {
                val algorithm = key.algorithm.trim()
                val lower = algorithm.lowercase()
                when {
                    lower.contains("ecdsa") || lower.contains("ec") -> add("EC")
                    lower.contains("rsa") -> add("RSA")
                }
                if (algorithm.isNotBlank()) add(algorithm)
                add("EC")
                add("RSA")
            }.distinct()
            candidates.forEach { algorithm ->
                val normalized = generateKnownHostPublicKey(algorithm, encoded)
                if (normalized != null && resolveKnownHostKeyType(normalized) != KeyType.UNKNOWN) {
                    return normalized
                }
            }
            return key
        }

        private fun readKnownHostEntries(): List<KnownHostEntry> {
            return runCatching {
                if (!file.exists()) return emptyList()
                file.readLines(StandardCharsets.UTF_8).mapNotNull(::parseKnownHostEntry)
            }.getOrDefault(emptyList())
        }

        private fun appendHostKeyLine(line: String) {
            file.parentFile?.mkdirs()
            file.appendText(line + System.lineSeparator(), StandardCharsets.UTF_8)
        }

        private fun replaceHostKeyLine(hostname: String, keyType: KeyType, line: String) {
            file.parentFile?.mkdirs()
            val existing = if (file.exists()) file.readLines(StandardCharsets.UTF_8) else emptyList()
            val filtered = existing.filterNot { existingLine ->
                val entry = parseKnownHostEntry(existingLine) ?: return@filterNot false
                entry.keyType == keyType && entry.appliesTo(hostname)
            }
            file.writeText(
                (filtered + line).joinToString(System.lineSeparator()) + System.lineSeparator(),
                StandardCharsets.UTF_8
            )
        }

        private fun logUnableToResolveKeyType(hostname: String, key: PublicKey) {
            Log.w(
                TAG,
                "Unable to resolve host key type for $hostname using algorithm ${key.algorithm} (${key.javaClass.name})"
            )
        }

        private fun resolveKnownHostKeyType(key: PublicKey): KeyType {
            val direct = runCatching { KeyType.fromKey(key) }.getOrDefault(KeyType.UNKNOWN)
            if (direct != KeyType.UNKNOWN) return direct
            val algorithm = key.algorithm.trim().lowercase()
            return when {
                algorithm.contains("ed25519") || algorithm.contains("eddsa") -> KeyType.ED25519
                algorithm.contains("rsa") -> KeyType.RSA
                algorithm.contains("dsa") -> KeyType.DSA
                else -> KeyType.UNKNOWN
            }
        }

        private fun generateKnownHostPublicKey(algorithm: String, encoded: ByteArray): PublicKey? {
            return runCatching {
                KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(encoded))
            }.getOrNull()
        }
    }

    private fun fingerprintSha256(key: PublicKey): String {
        val normalizedKey = if (isEd25519Key(key)) {
            extractRawEd25519PublicKey(key)?.let(::StableEd25519PublicKey) ?: key
        } else {
            key
        }
        val keyType = runCatching { KeyType.fromKey(normalizedKey) }.getOrDefault(KeyType.UNKNOWN)
        val material = sshPublicKeyBlob(keyType, normalizedKey)
            ?: runCatching { normalizedKey.encoded }.getOrNull()
            ?: return "SHA256:unavailable"
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        val value = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$value"
    }

    private data class KnownHostEntry(
        val hostPart: String,
        val keyType: KeyType,
        val keyBlob: ByteArray
    ) {
        fun appliesTo(hostname: String): Boolean {
            return hostPart.split(',').any { candidate ->
                candidate.equals(hostname, ignoreCase = true)
            }
        }
    }

    private fun parseKnownHostEntry(line: String): KnownHostEntry? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"))
        val offset = if (parts.firstOrNull()?.startsWith("@") == true) 1 else 0
        if (parts.size <= offset + 2) return null
        val hostPart = parts[offset]
        val keyType = KeyType.fromString(parts[offset + 1]).takeIf { it != KeyType.UNKNOWN } ?: return null
        val keyBlob = runCatching { Base64.getDecoder().decode(parts[offset + 2]) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return KnownHostEntry(hostPart, keyType, keyBlob)
    }

    private fun adjustKnownHostName(hostname: String, port: Int): String {
        val normalized = hostname.trim().lowercase()
        return if (port == 22) normalized else "[$normalized]:$port"
    }

    private fun knownHostLine(hostname: String, keyType: KeyType, keyBlob: ByteArray): String {
        val encodedKey = Base64.getEncoder().encodeToString(keyBlob)
        return "$hostname ${keyType} $encodedKey"
    }

    private fun sshPublicKeyBlob(keyType: KeyType, key: PublicKey): ByteArray? {
        if (keyType == KeyType.ED25519) {
            val rawKey = extractRawEd25519PublicKey(key) ?: return null
            return Buffer.PlainBuffer()
                .putString(KeyType.ED25519.toString())
                .putBytes(rawKey)
                .compactData
        }
        return runCatching {
            Buffer.PlainBuffer()
                .putPublicKey(key)
                .compactData
        }.getOrNull()
    }

    private fun isEd25519Key(key: PublicKey): Boolean {
        val algorithm = key.algorithm.trim().lowercase()
        return algorithm.contains("ed25519") || algorithm.contains("eddsa") || algorithm == ED25519_OID
    }

    private fun extractRawEd25519PublicKey(publicKey: PublicKey): ByteArray? {
        if (publicKey is RawEd25519PublicKey) return publicKey.rawPublicKey()
        encodedEd25519PublicKey(publicKey)?.let { return it }
        return ed25519PublicKeyFromPoint(publicKey)
    }

    private fun encodedEd25519PublicKey(publicKey: PublicKey): ByteArray? {
        val encoded = runCatching { publicKey.encoded }.getOrNull()
            ?.takeIf { it.size >= ED25519_KEY_SIZE }
            ?: return null
        if (
            encoded.size == X509_ED25519_PUBLIC_KEY_HEADER.size + ED25519_KEY_SIZE &&
            encoded.copyOfRange(0, X509_ED25519_PUBLIC_KEY_HEADER.size)
                .contentEquals(X509_ED25519_PUBLIC_KEY_HEADER)
        ) {
            return encoded.copyOfRange(X509_ED25519_PUBLIC_KEY_HEADER.size, encoded.size)
        }
        return encoded.copyOfRange(encoded.size - ED25519_KEY_SIZE, encoded.size)
    }

    private fun ed25519PublicKeyFromPoint(publicKey: PublicKey): ByteArray? {
        return runCatching {
            val edPublicKeyType = Class.forName("java.security.interfaces.EdECPublicKey")
            if (!edPublicKeyType.isInstance(publicKey)) return null
            val point = edPublicKeyType.getMethod("getPoint").invoke(publicKey) ?: return null
            val y = point.javaClass.getMethod("getY").invoke(point) as BigInteger
            val xOdd = point.javaClass.getMethod("isXOdd").invoke(point) as Boolean
            encodeEd25519Point(y, xOdd)
        }.getOrNull()
    }

    private fun encodeEd25519Point(y: BigInteger, xOdd: Boolean): ByteArray {
        val raw = ByteArray(ED25519_KEY_SIZE)
        val bigEndianY = y.toByteArray()
            .let { bytes ->
                if (bytes.size > ED25519_KEY_SIZE) {
                    bytes.copyOfRange(bytes.size - ED25519_KEY_SIZE, bytes.size)
                } else {
                    bytes
                }
            }
        bigEndianY.indices.forEach { index ->
            raw[index] = bigEndianY[bigEndianY.lastIndex - index]
        }
        raw[ED25519_KEY_SIZE - 1] = if (xOdd) {
            (raw[ED25519_KEY_SIZE - 1].toInt() or 0x80).toByte()
        } else {
            (raw[ED25519_KEY_SIZE - 1].toInt() and 0x7F).toByte()
        }
        return raw
    }

    private class StableEd25519PublicKey(
        private val publicKey: ByteArray
    ) : PublicKey, RawEd25519PublicKey {
        override fun getAlgorithm(): String = "Ed25519"

        override fun getFormat(): String = "X.509"

        override fun getEncoded(): ByteArray = X509_ED25519_PUBLIC_KEY_HEADER + publicKey.copyOf()

        override fun rawPublicKey(): ByteArray = publicKey.copyOf()
    }
}
