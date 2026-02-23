package com.sshpeaches.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sshpeaches.app.ui.state.LockTimeout
import com.sshpeaches.app.ui.state.ThemeMode
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
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val lockTimeoutKey = stringPreferencesKey("lock_timeout")
    private val customLockTimeoutMinutesKey = intPreferencesKey("custom_lock_timeout_minutes")
    private val crashReportsKey = booleanPreferencesKey("crash_reports")
    private val analyticsKey = booleanPreferencesKey("analytics")
    private val diagnosticsKey = booleanPreferencesKey("diagnostics")
    private val includeIdentitiesKey = booleanPreferencesKey("include_identities")
    private val includeSettingsKey = booleanPreferencesKey("include_settings")
    private val autoStartForwardsKey = booleanPreferencesKey("auto_start_forwards")
    private val hostKeyPromptKey = booleanPreferencesKey("host_key_prompt")
    private val autoTrustHostKey = booleanPreferencesKey("auto_trust_host_key")
    private val usageReportsKey = booleanPreferencesKey("usage_reports")

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

    val themeMode: Flow<ThemeMode> by lazy {
        dataStore.data.map { prefs ->
            runCatching {
                ThemeMode.valueOf(prefs[themeModeKey] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM)
        }
    }

    val lockTimeout: Flow<LockTimeout> by lazy {
        dataStore.data.map { prefs ->
            runCatching {
                LockTimeout.valueOf(prefs[lockTimeoutKey] ?: LockTimeout.FIVE_MIN.name)
            }.getOrDefault(LockTimeout.FIVE_MIN)
        }
    }

    val customLockTimeoutMinutes: Flow<Int> by lazy {
        dataStore.data.map { prefs ->
            (prefs[customLockTimeoutMinutesKey] ?: 30).coerceIn(1, 720)
        }
    }

    val crashReportsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[crashReportsKey] ?: false }
    }

    val analyticsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[analyticsKey] ?: false }
    }

    val diagnosticsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[diagnosticsKey] ?: false }
    }

    val includeIdentities: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[includeIdentitiesKey] ?: true }
    }

    val includeSettings: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[includeSettingsKey] ?: true }
    }

    val autoStartForwards: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[autoStartForwardsKey] ?: true }
    }

    val hostKeyPromptEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[hostKeyPromptKey] ?: true }
    }

    val autoTrustHostKeyEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[autoTrustHostKey] ?: true }
    }

    val usageReportsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[usageReportsKey] ?: false }
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

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setLockTimeout(timeout: LockTimeout) {
        dataStore.edit { prefs ->
            prefs[lockTimeoutKey] = timeout.name
        }
    }

    suspend fun setCustomLockTimeoutMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[customLockTimeoutMinutesKey] = minutes.coerceIn(1, 720)
        }
    }

    suspend fun setCrashReportsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[crashReportsKey] = enabled
        }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[analyticsKey] = enabled
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[diagnosticsKey] = enabled
        }
    }

    suspend fun setIncludeIdentities(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[includeIdentitiesKey] = enabled
        }
    }

    suspend fun setIncludeSettings(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[includeSettingsKey] = enabled
        }
    }

    suspend fun setAutoStartForwards(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[autoStartForwardsKey] = enabled
        }
    }

    suspend fun setHostKeyPromptEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[hostKeyPromptKey] = enabled
        }
    }

    suspend fun setAutoTrustHostKeyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[autoTrustHostKey] = enabled
        }
    }

    suspend fun setUsageReportsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[usageReportsKey] = enabled
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
