package com.majordaftapps.sshpeaches.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object SecurityManager {
    private const val PREF_NAME = "secure_store"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_STORAGE_VERSION = "storage_version"
    private const val KEY_VAULT_PIN_SALT = "vault_pin_salt"
    private const val KEY_VAULT_PIN_ITERATIONS = "vault_pin_iter"
    private const val KEY_VAULT_PIN_WRAPPED = "vault_pin_wrapped"
    private const val KEY_VAULT_BIOMETRIC_WRAPPED = "vault_biometric_wrapped"
    private const val KEY_PASSWORD_PREFIX = "pwd_"
    private const val KEY_IDENTITY_PREFIX = "ident_"
    private const val KEY_IDENTITY_PUBLIC_PREFIX = "ident_pub_"
    private const val KEY_IDENTITY_PASSPHRASE_PREFIX = "ident_pass_"
    private const val KEY_VAULT_PASSWORD_PREFIX = "vault_pwd_"
    private const val KEY_VAULT_IDENTITY_PREFIX = "vault_ident_"
    private const val KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX = "vault_ident_pass_"
    private const val KEYSTORE_NAME = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "sshpeaches_biometric_vault_key"
    private const val BIOMETRIC_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val INIT_AWAIT_TIMEOUT_SECONDS = 20L
    private const val STORAGE_VERSION_LEGACY = 0
    private const val STORAGE_VERSION_VAULT = 1
    private const val CURRENT_EXPORT_KDF_ITERATIONS = 210_000
    private const val CURRENT_PIN_KDF_ITERATIONS = 210_000
    const val MIN_SECRET_PASSPHRASE_LENGTH = 12

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var initError: Throwable? = null
    @Volatile
    private var initStarted = false
    @Volatile
    private var unlockedVaultKey: ByteArray? = null

    private val initLock = Any()
    private val vaultKeyLock = Any()
    @Volatile
    private var initCompleteLatch = CountDownLatch(1)
    private val lockState = MutableStateFlow(false)
    private val pinConfiguredState = MutableStateFlow(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        startInitializationIfNeeded(async = true)
    }

    fun resetForTesting() {
        synchronized(initLock) {
            prefs = null
            initError = null
            initStarted = false
            initCompleteLatch = CountDownLatch(1)
            pinConfiguredState.value = false
            lockState.value = false
            clearUnlockedVaultKey()
        }
    }

    fun isInitialized() = prefs != null

    fun isPinSet(): Boolean = pinConfiguredState.value

    fun pinConfiguredState(): StateFlow<Boolean> = pinConfiguredState.asStateFlow()

    fun lockState(): StateFlow<Boolean> = lockState.asStateFlow()

    fun isLocked(): Boolean = lockState.value

    fun lock() {
        clearUnlockedVaultKey()
        lockState.value = isPinSet()
    }

    fun unlock() {
        check(!isPinSet() || !isVaultBackedStorage() || currentUnlockedVaultKey() != null) {
            "Cannot unlock vault-backed storage without restoring the vault key."
        }
        lockState.value = false
    }

    fun setPin(pin: String) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("set PIN")
        val pinSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val pinHash = hashPin(pin, pinSalt)
        val vaultKey = if (isVaultBackedStorage(securePrefs)) {
            requireUnlockedVaultKey("change PIN")
        } else {
            migrateLegacySecretsToVault(securePrefs, pin)
        }
        writePinMetadata(
            securePrefs = securePrefs,
            pin = pin,
            pinSalt = pinSalt,
            pinHash = pinHash,
            vaultKey = vaultKey
        )
        storeUnlockedVaultKey(vaultKey)
        pinConfiguredState.value = true
        unlock()
    }

    fun clearPin() {
        val securePrefs = awaitPrefs()
        ensureUnlocked("clear PIN")
        if (isVaultBackedStorage(securePrefs)) {
            revertVaultSecretsToLegacy(securePrefs)
        }
        removePinAndVaultMetadata(securePrefs)
        deleteBiometricKey()
        clearUnlockedVaultKey()
        pinConfiguredState.value = false
        lockState.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val securePrefs = awaitPrefs()
        val saltEncoded = securePrefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashEncoded = securePrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
        val expected = Base64.decode(hashEncoded, Base64.NO_WRAP)
        val actual = hashPin(pin, salt)
        val success = expected.contentEquals(actual)
        if (!success) return false

        val vaultKey = if (isVaultBackedStorage(securePrefs)) {
            unwrapVaultKeyWithPin(securePrefs, pin)
        } else {
            migrateLegacySecretsToVault(securePrefs, pin)
        }
        storeUnlockedVaultKey(vaultKey)
        ensureBiometricWrappedVaultKey(securePrefs, vaultKey)
        unlock()
        return true
    }

    fun prepareBiometricUnlockCipher(): Cipher? {
        val securePrefs = awaitPrefs()
        if (!isPinSet() || !isVaultBackedStorage(securePrefs)) return null
        if (securePrefs.getString(KEY_VAULT_BIOMETRIC_WRAPPED, null).isNullOrBlank()) return null
        return runCatching {
            val keyStore = loadKeyStore()
            val privateKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) ?: return null
            Cipher.getInstance(BIOMETRIC_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, privateKey)
            }
        }.onFailure {
            handleBiometricWrapFailure(securePrefs)
        }.getOrNull()
    }

    fun unlockWithBiometric(cipher: Cipher?): Boolean {
        if (cipher == null) return false
        val securePrefs = awaitPrefs()
        if (!isPinSet() || !isVaultBackedStorage(securePrefs)) return false
        val wrapped = securePrefs.getString(KEY_VAULT_BIOMETRIC_WRAPPED, null) ?: return false
        val vaultKey = runCatching {
            cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
        }.onFailure {
            handleBiometricWrapFailure(securePrefs)
        }.getOrNull() ?: return false
        storeUnlockedVaultKey(vaultKey)
        unlock()
        return true
    }

    fun storeHostPassword(hostId: String, password: String) {
        val securePrefs = awaitPrefs()
        storeProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_PASSWORD_PREFIX + hostId,
            vaultKeyName = KEY_VAULT_PASSWORD_PREFIX + hostId,
            value = password,
            action = "store password"
        )
    }

    fun getHostPassword(hostId: String): String? {
        val securePrefs = awaitPrefs()
        return getProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_PASSWORD_PREFIX + hostId,
            vaultKeyName = KEY_VAULT_PASSWORD_PREFIX + hostId,
            action = "access password"
        )
    }

    fun clearHostPassword(hostId: String) {
        awaitPrefs().edit {
            remove(KEY_PASSWORD_PREFIX + hostId)
            remove(KEY_VAULT_PASSWORD_PREFIX + hostId)
        }
    }

    fun storeIdentityKey(identityId: String, key: String) {
        val securePrefs = awaitPrefs()
        storeProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_IDENTITY_PREFIX + identityId,
            vaultKeyName = KEY_VAULT_IDENTITY_PREFIX + identityId,
            value = key,
            action = "store identity key"
        )
    }

    fun getIdentityKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        return getProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_IDENTITY_PREFIX + identityId,
            vaultKeyName = KEY_VAULT_IDENTITY_PREFIX + identityId,
            action = "access identity key"
        )
    }

    fun clearIdentityKey(identityId: String) {
        awaitPrefs().edit {
            remove(KEY_IDENTITY_PREFIX + identityId)
            remove(KEY_VAULT_IDENTITY_PREFIX + identityId)
            remove(KEY_IDENTITY_PUBLIC_PREFIX + identityId)
            remove(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId)
            remove(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX + identityId)
        }
    }

    fun storeIdentityPublicKey(identityId: String, publicKey: String) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("store identity public key")
        securePrefs.edit {
            putString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, publicKey)
        }
    }

    fun getIdentityPublicKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        ensureUnlocked("access identity public key")
        return securePrefs.getString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, null)
    }

    fun clearIdentityPublicKey(identityId: String) {
        awaitPrefs().edit {
            remove(KEY_IDENTITY_PUBLIC_PREFIX + identityId)
        }
    }

    fun storeIdentityKeyPassphrase(identityId: String, passphrase: String?) {
        val securePrefs = awaitPrefs()
        if (passphrase.isNullOrBlank()) {
            clearIdentityKeyPassphrase(identityId)
            return
        }
        storeProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_IDENTITY_PASSPHRASE_PREFIX + identityId,
            vaultKeyName = KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX + identityId,
            value = passphrase,
            action = "store identity key passphrase"
        )
    }

    fun getIdentityKeyPassphrase(identityId: String): String? {
        val securePrefs = awaitPrefs()
        return getProtectedSecret(
            securePrefs = securePrefs,
            legacyKey = KEY_IDENTITY_PASSPHRASE_PREFIX + identityId,
            vaultKeyName = KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX + identityId,
            action = "access identity key passphrase"
        )
    }

    fun clearIdentityKeyPassphrase(identityId: String) {
        awaitPrefs().edit {
            remove(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId)
            remove(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX + identityId)
        }
    }

    fun exportIdentityPublicKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        return securePrefs.getString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, null)
    }

    fun importIdentityPublicKey(identityId: String, publicKey: String) {
        val securePrefs = awaitPrefs()
        securePrefs.edit {
            putString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, publicKey)
        }
    }

    fun exportHostPasswordPayload(hostId: String, passphrase: String): String? {
        if (lockState.value) return null
        requireValidSecretPassphrase(passphrase)
        val password = getHostPassword(hostId) ?: return null
        return encryptTransferPayload(password, passphrase)
    }

    fun importHostPasswordPayload(hostId: String, payload: String, passphrase: String) {
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        storeHostPassword(hostId, plaintext)
    }

    fun exportIdentityKeyPayload(identityId: String, passphrase: String): String? {
        if (lockState.value) return null
        requireValidSecretPassphrase(passphrase)
        val keyValue = getIdentityKey(identityId) ?: return null
        return encryptTransferPayload(keyValue, passphrase)
    }

    fun importIdentityKeyPayload(identityId: String, payload: String, passphrase: String) {
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        storeIdentityKey(identityId, plaintext)
    }

    fun exportIdentityKeyPassphrasePayload(identityId: String, passphrase: String): String? {
        if (lockState.value) return null
        requireValidSecretPassphrase(passphrase)
        val keyPassphrase = getIdentityKeyPassphrase(identityId) ?: return null
        return encryptTransferPayload(keyPassphrase, passphrase)
    }

    fun importIdentityKeyPassphrasePayload(identityId: String, payload: String, passphrase: String) {
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        storeIdentityKeyPassphrase(identityId, plaintext)
    }

    private fun storeProtectedSecret(
        securePrefs: SharedPreferences,
        legacyKey: String,
        vaultKeyName: String,
        value: String,
        action: String
    ) {
        ensureUnlocked(action)
        if (isVaultBackedStorage(securePrefs)) {
            val encrypted = encryptVaultPayload(value, requireUnlockedVaultKey(action))
            securePrefs.edit {
                putString(vaultKeyName, encrypted)
                remove(legacyKey)
            }
        } else {
            securePrefs.edit {
                putString(legacyKey, value)
            }
        }
    }

    private fun getProtectedSecret(
        securePrefs: SharedPreferences,
        legacyKey: String,
        vaultKeyName: String,
        action: String
    ): String? {
        ensureUnlocked(action)
        return if (isVaultBackedStorage(securePrefs)) {
            securePrefs.getString(vaultKeyName, null)?.let { decryptVaultPayload(it, requireUnlockedVaultKey(action)) }
        } else {
            securePrefs.getString(legacyKey, null)
        }
    }

    private fun migrateLegacySecretsToVault(securePrefs: SharedPreferences, pin: String): ByteArray {
        val legacySecrets = collectLegacySecrets(securePrefs)
        val vaultKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pinSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val pinHash = hashPin(pin, pinSalt)
        val wrappedWithPin = wrapVaultKeyWithPin(vaultKey, pin)
        val wrappedWithBiometric = wrapVaultKeyWithBiometric(vaultKey)
        val encryptedSecrets = legacySecrets.mapValues { (_, value) ->
            encryptVaultPayload(value, vaultKey)
        }
        encryptedSecrets.forEach { (_, payload) ->
            decryptVaultPayload(payload, vaultKey)
        }
        securePrefs.edit(commit = true) {
            encryptedSecrets.forEach { (key, payload) ->
                putString(key, payload)
            }
            putString(KEY_PIN_SALT, Base64.encodeToString(pinSalt, Base64.NO_WRAP))
            putString(KEY_PIN_HASH, Base64.encodeToString(pinHash, Base64.NO_WRAP))
            putString(KEY_VAULT_PIN_SALT, wrappedWithPin.salt)
            putInt(KEY_VAULT_PIN_ITERATIONS, wrappedWithPin.iterations)
            putString(KEY_VAULT_PIN_WRAPPED, wrappedWithPin.payload)
            if (wrappedWithBiometric != null) {
                putString(KEY_VAULT_BIOMETRIC_WRAPPED, wrappedWithBiometric)
            } else {
                remove(KEY_VAULT_BIOMETRIC_WRAPPED)
            }
        }
        securePrefs.edit(commit = true) {
            putInt(KEY_STORAGE_VERSION, STORAGE_VERSION_VAULT)
        }
        securePrefs.edit(commit = true) {
            legacySecrets.keys.forEach { legacyKey ->
                remove(toLegacySecretKey(legacyKey))
            }
        }
        return vaultKey
    }

    private fun revertVaultSecretsToLegacy(securePrefs: SharedPreferences) {
        val vaultKey = requireUnlockedVaultKey("clear PIN")
        val decryptedPasswords = collectVaultSecrets(securePrefs, KEY_VAULT_PASSWORD_PREFIX, vaultKey)
            .mapKeys { (key, _) -> key.removePrefix(KEY_VAULT_PASSWORD_PREFIX) }
        val decryptedIdentityKeys = collectVaultSecrets(securePrefs, KEY_VAULT_IDENTITY_PREFIX, vaultKey)
            .mapKeys { (key, _) -> key.removePrefix(KEY_VAULT_IDENTITY_PREFIX) }
        val decryptedIdentityPassphrases = collectVaultSecrets(securePrefs, KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX, vaultKey)
            .mapKeys { (key, _) -> key.removePrefix(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX) }
        securePrefs.edit(commit = true) {
            decryptedPasswords.forEach { (suffix, value) ->
                putString(KEY_PASSWORD_PREFIX + suffix, value)
            }
            decryptedIdentityKeys.forEach { (suffix, value) ->
                putString(KEY_IDENTITY_PREFIX + suffix, value)
            }
            decryptedIdentityPassphrases.forEach { (suffix, value) ->
                putString(KEY_IDENTITY_PASSPHRASE_PREFIX + suffix, value)
            }
        }
        securePrefs.edit(commit = true) {
            securePrefs.all.keys
                .filter { key ->
                    key.startsWith(KEY_VAULT_PASSWORD_PREFIX) ||
                        key.startsWith(KEY_VAULT_IDENTITY_PREFIX) ||
                        key.startsWith(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX)
                }
                .forEach { remove(it) }
            remove(KEY_STORAGE_VERSION)
            remove(KEY_VAULT_PIN_SALT)
            remove(KEY_VAULT_PIN_ITERATIONS)
            remove(KEY_VAULT_PIN_WRAPPED)
            remove(KEY_VAULT_BIOMETRIC_WRAPPED)
        }
    }

    private fun removePinAndVaultMetadata(securePrefs: SharedPreferences) {
        securePrefs.edit(commit = true) {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_STORAGE_VERSION)
            remove(KEY_VAULT_PIN_SALT)
            remove(KEY_VAULT_PIN_ITERATIONS)
            remove(KEY_VAULT_PIN_WRAPPED)
            remove(KEY_VAULT_BIOMETRIC_WRAPPED)
        }
    }

    private fun writePinMetadata(
        securePrefs: SharedPreferences,
        pin: String,
        pinSalt: ByteArray,
        pinHash: ByteArray,
        vaultKey: ByteArray
    ) {
        val wrappedWithPin = wrapVaultKeyWithPin(vaultKey, pin)
        val wrappedWithBiometric = wrapVaultKeyWithBiometric(vaultKey)
        securePrefs.edit(commit = true) {
            putString(KEY_PIN_SALT, Base64.encodeToString(pinSalt, Base64.NO_WRAP))
            putString(KEY_PIN_HASH, Base64.encodeToString(pinHash, Base64.NO_WRAP))
            putInt(KEY_STORAGE_VERSION, STORAGE_VERSION_VAULT)
            putString(KEY_VAULT_PIN_SALT, wrappedWithPin.salt)
            putInt(KEY_VAULT_PIN_ITERATIONS, wrappedWithPin.iterations)
            putString(KEY_VAULT_PIN_WRAPPED, wrappedWithPin.payload)
            if (wrappedWithBiometric != null) {
                putString(KEY_VAULT_BIOMETRIC_WRAPPED, wrappedWithBiometric)
            } else {
                remove(KEY_VAULT_BIOMETRIC_WRAPPED)
            }
        }
    }

    private fun ensureBiometricWrappedVaultKey(securePrefs: SharedPreferences, vaultKey: ByteArray) {
        if (!securePrefs.getString(KEY_VAULT_BIOMETRIC_WRAPPED, null).isNullOrBlank()) return
        val wrapped = wrapVaultKeyWithBiometric(vaultKey) ?: return
        securePrefs.edit {
            putString(KEY_VAULT_BIOMETRIC_WRAPPED, wrapped)
        }
    }

    private fun collectLegacySecrets(securePrefs: SharedPreferences): Map<String, String> {
        val all = securePrefs.all
        return buildMap {
            all.forEach { (key, value) ->
                if (value !is String) return@forEach
                if (isLegacyProtectedSecretKey(key)) {
                    put(toVaultSecretKey(key), value)
                }
            }
        }
    }

    private fun collectVaultSecrets(
        securePrefs: SharedPreferences,
        prefix: String,
        vaultKey: ByteArray
    ): Map<String, String> {
        val all = securePrefs.all
        return buildMap {
            all.forEach { (key, value) ->
                if (value !is String || !matchesVaultPrefix(key, prefix)) return@forEach
                put(key, decryptVaultPayload(value, vaultKey))
            }
        }
    }

    private fun isLegacyProtectedSecretKey(key: String): Boolean {
        return key.startsWith(KEY_PASSWORD_PREFIX) ||
            key.startsWith(KEY_IDENTITY_PASSPHRASE_PREFIX) ||
            (key.startsWith(KEY_IDENTITY_PREFIX) &&
                !key.startsWith(KEY_IDENTITY_PUBLIC_PREFIX) &&
                !key.startsWith(KEY_IDENTITY_PASSPHRASE_PREFIX))
    }

    private fun matchesVaultPrefix(key: String, prefix: String): Boolean {
        return when (prefix) {
            KEY_VAULT_PASSWORD_PREFIX -> key.startsWith(KEY_VAULT_PASSWORD_PREFIX)
            KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX -> key.startsWith(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX)
            KEY_VAULT_IDENTITY_PREFIX ->
                key.startsWith(KEY_VAULT_IDENTITY_PREFIX) &&
                    !key.startsWith(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX)
            else -> key.startsWith(prefix)
        }
    }

    private fun toVaultSecretKey(legacyKey: String): String {
        return when {
            legacyKey.startsWith(KEY_PASSWORD_PREFIX) ->
                KEY_VAULT_PASSWORD_PREFIX + legacyKey.removePrefix(KEY_PASSWORD_PREFIX)
            legacyKey.startsWith(KEY_IDENTITY_PASSPHRASE_PREFIX) ->
                KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX + legacyKey.removePrefix(KEY_IDENTITY_PASSPHRASE_PREFIX)
            legacyKey.startsWith(KEY_IDENTITY_PREFIX) ->
                KEY_VAULT_IDENTITY_PREFIX + legacyKey.removePrefix(KEY_IDENTITY_PREFIX)
            else -> error("Unsupported legacy secret key: $legacyKey")
        }
    }

    private fun toLegacySecretKey(vaultKeyName: String): String {
        return when {
            vaultKeyName.startsWith(KEY_VAULT_PASSWORD_PREFIX) ->
                KEY_PASSWORD_PREFIX + vaultKeyName.removePrefix(KEY_VAULT_PASSWORD_PREFIX)
            vaultKeyName.startsWith(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX) ->
                KEY_IDENTITY_PASSPHRASE_PREFIX + vaultKeyName.removePrefix(KEY_VAULT_IDENTITY_PASSPHRASE_PREFIX)
            vaultKeyName.startsWith(KEY_VAULT_IDENTITY_PREFIX) ->
                KEY_IDENTITY_PREFIX + vaultKeyName.removePrefix(KEY_VAULT_IDENTITY_PREFIX)
            else -> error("Unsupported vault secret key: $vaultKeyName")
        }
    }

    private fun wrapVaultKeyWithPin(vaultKey: ByteArray, pin: String): PinWrappedVaultKey {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(deriveKey(pin, salt, CURRENT_PIN_KDF_ITERATIONS), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(vaultKey)
        return PinWrappedVaultKey(
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iterations = CURRENT_PIN_KDF_ITERATIONS,
            payload = Base64.encodeToString(
                JSONObject().apply {
                    put("v", 1)
                    put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                }.toString().toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
        )
    }

    private fun unwrapVaultKeyWithPin(securePrefs: SharedPreferences, pin: String): ByteArray {
        val salt = Base64.decode(
            securePrefs.getString(KEY_VAULT_PIN_SALT, null)
                ?: error("Missing vault PIN salt."),
            Base64.NO_WRAP
        )
        val iterations = securePrefs.getInt(KEY_VAULT_PIN_ITERATIONS, 0)
        require(iterations == CURRENT_PIN_KDF_ITERATIONS) { "Unsupported vault PIN format." }
        val payload = String(
            Base64.decode(
                securePrefs.getString(KEY_VAULT_PIN_WRAPPED, null)
                    ?: error("Missing PIN-wrapped vault key."),
                Base64.NO_WRAP
            ),
            StandardCharsets.UTF_8
        )
        val json = JSONObject(payload)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(json.getString("cipher"), Base64.NO_WRAP)
        val key = SecretKeySpec(deriveKey(pin, salt, iterations), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun wrapVaultKeyWithBiometric(vaultKey: ByteArray): String? {
        return runCatching {
            val publicKey = getOrCreateBiometricPublicKey()
            val cipher = Cipher.getInstance(BIOMETRIC_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.encodeToString(cipher.doFinal(vaultKey), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun getOrCreateBiometricPublicKey(): PublicKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getCertificate(BIOMETRIC_KEY_ALIAS)?.publicKey
        if (existing != null) return existing
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_NAME)
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair().public
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(KEYSTORE_NAME).apply { load(null) }
    }

    private fun deleteBiometricKey() {
        runCatching {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    private fun handleBiometricWrapFailure(securePrefs: SharedPreferences) {
        deleteBiometricKey()
        securePrefs.edit {
            remove(KEY_VAULT_BIOMETRIC_WRAPPED)
        }
    }

    private fun encryptVaultPayload(value: String, vaultKey: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(vaultKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
        return Base64.encodeToString(payload.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decryptVaultPayload(payload: String, vaultKey: ByteArray): String {
        val decoded = String(Base64.decode(payload, Base64.NO_WRAP), StandardCharsets.UTF_8)
        val json = JSONObject(decoded)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(json.getString("cipher"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(vaultKey, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(StandardCharsets.UTF_8))
        return digest.digest()
    }

    private fun ensureUnlocked(action: String) {
        check(!lockState.value) { "Cannot $action while locked" }
    }

    private fun requireUnlockedVaultKey(action: String): ByteArray {
        ensureUnlocked(action)
        return currentUnlockedVaultKey()?.copyOf()
            ?: error("Vault key is not available while attempting to $action.")
    }

    private fun currentUnlockedVaultKey(): ByteArray? =
        synchronized(vaultKeyLock) { unlockedVaultKey?.copyOf() }

    private fun storeUnlockedVaultKey(vaultKey: ByteArray) {
        synchronized(vaultKeyLock) {
            unlockedVaultKey?.fill(0)
            unlockedVaultKey = vaultKey.copyOf()
        }
    }

    private fun clearUnlockedVaultKey() {
        synchronized(vaultKeyLock) {
            unlockedVaultKey?.fill(0)
            unlockedVaultKey = null
        }
    }

    private fun requireValidSecretPassphrase(passphrase: String) {
        require(passphrase.length >= MIN_SECRET_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_SECRET_PASSPHRASE_LENGTH characters."
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return factory.generateSecret(spec).encoded
    }

    private fun encryptTransferPayload(value: String, passphrase: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, CURRENT_EXPORT_KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iter", CURRENT_EXPORT_KDF_ITERATIONS)
            put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
        return Base64.encodeToString(payload.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decryptTransferPayload(payload: String, passphrase: String): String {
        val decoded = String(Base64.decode(payload, Base64.NO_WRAP), StandardCharsets.UTF_8)
        val json = JSONObject(decoded)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val iterations = json.optInt("iter", 0)
        require(iterations == CURRENT_EXPORT_KDF_ITERATIONS) {
            "Unsupported secret export format."
        }
        val cipherBytes = Base64.decode(json.getString("cipher"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(deriveKey(passphrase, salt, iterations), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }

    private fun isVaultBackedStorage(securePrefs: SharedPreferences = awaitPrefs()): Boolean {
        return securePrefs.getInt(KEY_STORAGE_VERSION, STORAGE_VERSION_LEGACY) >= STORAGE_VERSION_VAULT
    }

    private fun startInitializationIfNeeded(async: Boolean) {
        if (prefs != null) return
        synchronized(initLock) {
            if (prefs != null || initStarted) return
            initStarted = true
            val initializer = Runnable {
                try {
                    val context = appContext
                        ?: error("SecurityManager.init must be called with context before use")
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    val securePrefs = EncryptedSharedPreferences.create(
                        context,
                        PREF_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    prefs = securePrefs
                    val configured = securePrefs.contains(KEY_PIN_HASH)
                    pinConfiguredState.value = configured
                    lockState.value = configured
                    clearUnlockedVaultKey()
                } catch (t: Throwable) {
                    initError = t
                } finally {
                    initCompleteLatch.countDown()
                }
            }
            if (async) {
                Thread(initializer, "SSHPeaches-SecurityInit").apply {
                    isDaemon = true
                }.start()
            } else {
                initializer.run()
            }
        }
    }

    private fun awaitPrefs(): SharedPreferences {
        prefs?.let { return it }
        startInitializationIfNeeded(async = false)
        if (prefs == null) {
            initCompleteLatch.await(INIT_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        return prefs ?: throw IllegalStateException(
            "SecurityManager failed to initialize encrypted storage",
            initError
        )
    }

    private data class PinWrappedVaultKey(
        val salt: String,
        val iterations: Int,
        val payload: String
    )
}
