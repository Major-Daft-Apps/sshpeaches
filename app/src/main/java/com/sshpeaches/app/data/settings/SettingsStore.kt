package com.sshpeaches.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import com.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

object SettingsStore {
    private lateinit var appContext: Context

    private val allowBackgroundSessionsKey = booleanPreferencesKey("allow_background_sessions")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val keyboardLayoutKey = stringPreferencesKey("keyboard_layout")

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val allowBackgroundSessions: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[allowBackgroundSessionsKey] ?: true
        }
    }

    val biometricLockEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[biometricLockKey] ?: false
        }
    }

    val keyboardLayout: Flow<List<String>> by lazy {
        dataStore.data.map { prefs ->
            prefs[keyboardLayoutKey]?.let { decodeKeyboardSlots(it) } ?: KeyboardLayoutDefaults.DEFAULT_SLOTS
        }
    }

    suspend fun setAllowBackgroundSessions(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[allowBackgroundSessionsKey] = enabled
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[biometricLockKey] = enabled
        }
    }

    suspend fun setKeyboardLayout(slots: List<String>) {
        val array = JSONArray()
        slots.forEach { array.put(it) }
        dataStore.edit { prefs ->
            prefs[keyboardLayoutKey] = array.toString()
        }
    }

    private val dataStore: DataStore<Preferences>
        get() {
            check(::appContext.isInitialized) { "SettingsStore not initialized" }
            return appContext.settingsDataStore
        }

    private fun decodeKeyboardSlots(serialized: String): List<String> =
        runCatching {
            val array = JSONArray(serialized)
            List(KeyboardLayoutDefaults.SLOT_COUNT) { index ->
                if (index < array.length()) array.optString(index) ?: "" else ""
            }
        }.getOrElse { KeyboardLayoutDefaults.DEFAULT_SLOTS }
}
