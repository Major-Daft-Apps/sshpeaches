package com.majordaftapps.sshpeaches.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {
    private const val PREF_NAME = "secure_store"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PASSWORD_PREFIX = "pwd_"
    private const val KEY_IDENTITY_PREFIX = "ident_"
    private const val KEY_IDENTITY_PUBLIC_PREFIX = "ident_pub_"
    private const val KEY_IDENTITY_PASSPHRASE_PREFIX = "ident_pass_"
    private const val INIT_AWAIT_TIMEOUT_SECONDS = 20L
    private const val CURRENT_EXPORT_KDF_ITERATIONS = 210_000
    const val MIN_SECRET_PASSPHRASE_LENGTH = 12

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var initError: Throwable? = null
    @Volatile
    private var initStarted = false

    private val initLock = Any()
    private val initCompleteLatch = CountDownLatch(1)
    private val lockState = MutableStateFlow(false)
    private val pinConfiguredState = MutableStateFlow(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        startInitializationIfNeeded(async = true)
    }

    fun isInitialized() = prefs != null

    fun isPinSet(): Boolean = pinConfiguredState.value

    fun pinConfiguredState(): StateFlow<Boolean> = pinConfiguredState.asStateFlow()

    fun lockState(): StateFlow<Boolean> = lockState.asStateFlow()

    fun isLocked(): Boolean = lockState.value

    fun lock() {
        lockState.value = isPinSet()
    }

    fun unlock() {
        lockState.value = false
    }

    fun setPin(pin: String) {
        val securePrefs = awaitPrefs()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        securePrefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        pinConfiguredState.value = true
        // Keep the current session unlocked after creating/changing a PIN.
        unlock()
    }

    fun clearPin() {
        val securePrefs = awaitPrefs()
        securePrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
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
        if (success) {
            unlock()
        }
        return success
    }

    fun storeHostPassword(hostId: String, password: String) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("store password")
        securePrefs.edit()
            .putString(KEY_PASSWORD_PREFIX + hostId, password)
            .apply()
    }

    fun getHostPassword(hostId: String): String? {
        val securePrefs = awaitPrefs()
        ensureUnlocked("access password")
        return securePrefs.getString(KEY_PASSWORD_PREFIX + hostId, null)
    }

    fun clearHostPassword(hostId: String) {
        awaitPrefs().edit().remove(KEY_PASSWORD_PREFIX + hostId).apply()
    }

    fun storeIdentityKey(identityId: String, key: String) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("store identity key")
        securePrefs.edit()
            .putString(KEY_IDENTITY_PREFIX + identityId, key)
            .apply()
    }

    fun getIdentityKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        ensureUnlocked("access identity key")
        return securePrefs.getString(KEY_IDENTITY_PREFIX + identityId, null)
    }

    fun clearIdentityKey(identityId: String) {
        awaitPrefs().edit()
            .remove(KEY_IDENTITY_PREFIX + identityId)
            .remove(KEY_IDENTITY_PUBLIC_PREFIX + identityId)
            .remove(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId)
            .apply()
    }

    fun storeIdentityPublicKey(identityId: String, publicKey: String) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("store identity public key")
        securePrefs.edit()
            .putString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, publicKey)
            .apply()
    }

    fun getIdentityPublicKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        ensureUnlocked("access identity public key")
        return securePrefs.getString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, null)
    }

    fun clearIdentityPublicKey(identityId: String) {
        awaitPrefs().edit().remove(KEY_IDENTITY_PUBLIC_PREFIX + identityId).apply()
    }

    fun storeIdentityKeyPassphrase(identityId: String, passphrase: String?) {
        val securePrefs = awaitPrefs()
        ensureUnlocked("store identity key passphrase")
        val editor = securePrefs.edit()
        if (passphrase.isNullOrBlank()) {
            editor.remove(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId)
        } else {
            editor.putString(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId, passphrase)
        }
        editor.apply()
    }

    fun getIdentityKeyPassphrase(identityId: String): String? {
        val securePrefs = awaitPrefs()
        ensureUnlocked("access identity key passphrase")
        return securePrefs.getString(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId, null)
    }

    fun clearIdentityKeyPassphrase(identityId: String) {
        awaitPrefs().edit().remove(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId).apply()
    }

    fun exportIdentityPublicKey(identityId: String): String? {
        val securePrefs = awaitPrefs()
        return securePrefs.getString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, null)
    }

    fun importIdentityPublicKey(identityId: String, publicKey: String) {
        val securePrefs = awaitPrefs()
        securePrefs.edit()
            .putString(KEY_IDENTITY_PUBLIC_PREFIX + identityId, publicKey)
            .apply()
    }

    fun exportHostPasswordPayload(hostId: String, passphrase: String): String? {
        if (lockState.value) return null
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val password = securePrefs.getString(KEY_PASSWORD_PREFIX + hostId, null) ?: return null
        return encryptTransferPayload(password, passphrase)
    }

    fun importHostPasswordPayload(hostId: String, payload: String, passphrase: String) {
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        securePrefs.edit()
            .putString(KEY_PASSWORD_PREFIX + hostId, plaintext)
            .apply()
    }

    fun exportIdentityKeyPayload(identityId: String, passphrase: String): String? {
        if (lockState.value) return null
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val keyValue = securePrefs.getString(KEY_IDENTITY_PREFIX + identityId, null) ?: return null
        return encryptTransferPayload(keyValue, passphrase)
    }

    fun importIdentityKeyPayload(identityId: String, payload: String, passphrase: String) {
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        securePrefs.edit()
            .putString(KEY_IDENTITY_PREFIX + identityId, plaintext)
            .apply()
    }

    fun exportIdentityKeyPassphrasePayload(identityId: String, passphrase: String): String? {
        if (lockState.value) return null
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val keyPassphrase = securePrefs.getString(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId, null) ?: return null
        return encryptTransferPayload(keyPassphrase, passphrase)
    }

    fun importIdentityKeyPassphrasePayload(identityId: String, payload: String, passphrase: String) {
        val securePrefs = awaitPrefs()
        requireValidSecretPassphrase(passphrase)
        val plaintext = decryptTransferPayload(payload, passphrase)
        securePrefs.edit()
            .putString(KEY_IDENTITY_PASSPHRASE_PREFIX + identityId, plaintext)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    private fun ensureUnlocked(action: String) {
        check(!lockState.value) { "Cannot $action while locked" }
    }

    private fun requireValidSecretPassphrase(passphrase: String) {
        require(passphrase.length >= MIN_SECRET_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_SECRET_PASSPHRASE_LENGTH characters."
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return factory.generateSecret(spec).encoded
    }

    private fun encryptTransferPayload(value: String, passphrase: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, CURRENT_EXPORT_KDF_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iter", CURRENT_EXPORT_KDF_ITERATIONS)
            put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decryptTransferPayload(payload: String, passphrase: String): String {
        val decoded = String(Base64.decode(payload, Base64.NO_WRAP))
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
        return cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
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
}
