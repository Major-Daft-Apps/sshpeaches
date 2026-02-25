package com.sshpeaches.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
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

    private lateinit var prefs: SharedPreferences
    private val lockState = MutableStateFlow(false)

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        lockState.value = isPinSet()
    }

    fun isInitialized() = this::prefs.isInitialized

    fun isPinSet(): Boolean {
        if (!this::prefs.isInitialized) return false
        return prefs.contains(KEY_PIN_HASH)
    }

    fun lockState(): StateFlow<Boolean> = lockState.asStateFlow()

    fun isLocked(): Boolean = lockState.value

    fun lock() {
        lockState.value = isPinSet()
    }

    fun unlock() {
        lockState.value = false
    }

    fun setPin(pin: String) {
        ensureInit()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        lock()
    }

    fun clearPin() {
        ensureInit()
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
        lockState.value = false
    }

    fun verifyPin(pin: String): Boolean {
        ensureInit()
        val saltEncoded = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashEncoded = prefs.getString(KEY_PIN_HASH, null) ?: return false
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
        ensureInit()
        ensureUnlocked("store password")
        prefs.edit()
            .putString(KEY_PASSWORD_PREFIX + hostId, password)
            .apply()
    }

    fun getHostPassword(hostId: String): String? {
        ensureInit()
        ensureUnlocked("access password")
        return prefs.getString(KEY_PASSWORD_PREFIX + hostId, null)
    }

    fun clearHostPassword(hostId: String) {
        ensureInit()
        prefs.edit().remove(KEY_PASSWORD_PREFIX + hostId).apply()
    }

    fun storeIdentityKey(identityId: String, key: String) {
        ensureInit()
        ensureUnlocked("store identity key")
        prefs.edit()
            .putString(KEY_IDENTITY_PREFIX + identityId, key)
            .apply()
    }

    fun getIdentityKey(identityId: String): String? {
        ensureInit()
        ensureUnlocked("access identity key")
        return prefs.getString(KEY_IDENTITY_PREFIX + identityId, null)
    }

    fun clearIdentityKey(identityId: String) {
        ensureInit()
        prefs.edit().remove(KEY_IDENTITY_PREFIX + identityId).apply()
    }

    fun exportHostPasswordPayload(hostId: String, passphrase: String): String? {
        ensureInit()
        ensureUnlocked("export password")
        if (passphrase.isBlank()) return null
        val password = prefs.getString(KEY_PASSWORD_PREFIX + hostId, null) ?: return null
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val payload = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun importHostPasswordPayload(hostId: String, payload: String, passphrase: String) {
        ensureInit()
        ensureUnlocked("import password")
        if (passphrase.isBlank()) return
        val decoded = String(Base64.decode(payload, Base64.NO_WRAP))
        val json = JSONObject(decoded)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(json.getString("cipher"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(deriveKey(passphrase, salt), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        prefs.edit()
            .putString(KEY_PASSWORD_PREFIX + hostId, plaintext)
            .apply()
    }

    fun exportIdentityKeyPayload(identityId: String, passphrase: String): String? {
        ensureInit()
        ensureUnlocked("export identity key")
        if (passphrase.isBlank()) return null
        val keyValue = prefs.getString(KEY_IDENTITY_PREFIX + identityId, null) ?: return null
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(keyValue.toByteArray(Charsets.UTF_8))
        val payload = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun importIdentityKeyPayload(identityId: String, payload: String, passphrase: String) {
        ensureInit()
        ensureUnlocked("import identity key")
        if (passphrase.isBlank()) return
        val decoded = String(Base64.decode(payload, Base64.NO_WRAP))
        val json = JSONObject(decoded)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(json.getString("cipher"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(deriveKey(passphrase, salt), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        prefs.edit()
            .putString(KEY_IDENTITY_PREFIX + identityId, plaintext)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    private fun ensureInit() {
        check(this::prefs.isInitialized) { "SecurityManager not initialized" }
    }

    private fun ensureUnlocked(action: String) {
        check(!lockState.value) { "Cannot $action while locked" }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, 12000, 256)
        return factory.generateSecret(spec).encoded
    }
}
