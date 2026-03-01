package com.majordaftapps.sshpeaches.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.ui.state.LockTimeout
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction

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
    private val terminalEmulationKey = stringPreferencesKey("terminal_emulation")
    private val terminalProfilesKey = stringPreferencesKey("terminal_profiles")
    private val defaultTerminalProfileIdKey = stringPreferencesKey("default_terminal_profile_id")
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

    val keyboardLayout: Flow<List<KeyboardSlotAction>> by lazy {
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

    val terminalEmulation: Flow<TerminalEmulation> by lazy {
        dataStore.data.map { prefs ->
            runCatching {
                TerminalEmulation.valueOf(
                    prefs[terminalEmulationKey] ?: TerminalEmulation.XTERM.name
                )
            }.getOrDefault(TerminalEmulation.XTERM)
        }
    }

    val terminalProfiles: Flow<List<TerminalProfile>> by lazy {
        dataStore.data.map { prefs ->
            mergedTerminalProfiles(prefs[terminalProfilesKey])
        }
    }

    val defaultTerminalProfileId: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            val profiles = mergedTerminalProfiles(prefs[terminalProfilesKey])
            val configured = prefs[defaultTerminalProfileIdKey] ?: TerminalProfileDefaults.DEFAULT_PROFILE_ID
            if (profiles.any { it.id == configured }) {
                configured
            } else {
                profiles.firstOrNull()?.id ?: TerminalProfileDefaults.DEFAULT_PROFILE_ID
            }
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

    suspend fun setKeyboardLayout(slots: List<KeyboardSlotAction>) {
        val array = JSONArray()
        KeyboardLayoutDefaults.normalizeSlots(slots).forEach { slot ->
            array.put(
                JSONObject().apply {
                    put("type", slot.type.name)
                    put("label", slot.label)
                    put("text", slot.text)
                    put("keyCode", slot.keyCode ?: JSONObject.NULL)
                    put("modifier", slot.modifier?.name ?: JSONObject.NULL)
                    put("sequence", slot.sequence)
                    put("ctrl", slot.ctrl)
                    put("alt", slot.alt)
                    put("shift", slot.shift)
                    put("repeatable", slot.repeatable)
                }
            )
        }
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

    suspend fun setTerminalEmulation(value: TerminalEmulation) {
        dataStore.edit { prefs ->
            prefs[terminalEmulationKey] = value.name
        }
    }

    suspend fun setTerminalProfiles(profiles: List<TerminalProfile>) {
        val builtInIds = TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet()
        val custom = profiles.filterNot { builtInIds.contains(it.id) }
        dataStore.edit { prefs ->
            prefs[terminalProfilesKey] = encodeTerminalProfiles(custom)
        }
    }

    suspend fun setDefaultTerminalProfileId(profileId: String) {
        dataStore.edit { prefs ->
            prefs[defaultTerminalProfileIdKey] = profileId
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

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.asMap().keys.toList().forEach { key ->
                @Suppress("UNCHECKED_CAST")
                prefs.remove(key as Preferences.Key<Any>)
            }
        }
    }

    private val dataStore: DataStore<Preferences>
        get() {
            check(::appContext.isInitialized) { "SettingsStore not initialized" }
            return appContext.settingsDataStore
        }

    private fun decodeKeyboardSlots(serialized: String): List<KeyboardSlotAction> =
        runCatching {
            val array = JSONArray(serialized)
            if (array.length() == 0) {
                KeyboardLayoutDefaults.DEFAULT_SLOTS
            } else {
                val first = array.opt(0)
                val parsed = when (first) {
                    is JSONObject -> {
                        List(minOf(array.length(), KeyboardLayoutDefaults.SLOT_COUNT)) { index ->
                            decodeKeyboardSlotObject(array.optJSONObject(index))
                        }
                    }
                    else -> {
                        List(minOf(array.length(), KeyboardLayoutDefaults.SLOT_COUNT)) { index ->
                            KeyboardLayoutDefaults.legacyStringToAction(array.optString(index, ""))
                        }
                    }
                }
                KeyboardLayoutDefaults.normalizeSlots(parsed)
            }
        }.getOrElse { KeyboardLayoutDefaults.DEFAULT_SLOTS }

    private fun decodeKeyboardSlotObject(item: JSONObject?): KeyboardSlotAction {
        if (item == null) return KeyboardLayoutDefaults.emptyAction()
        val type = runCatching {
            KeyboardActionType.valueOf(item.optString("type", KeyboardActionType.TEXT.name))
        }.getOrDefault(KeyboardActionType.TEXT)
        val label = item.optString("label", "")
        val text = item.optString("text", "")
        val keyCode = when {
            !item.has("keyCode") -> null
            item.isNull("keyCode") -> null
            else -> item.optInt("keyCode").takeIf { it != 0 }
        }
        val modifier = runCatching {
            item.optString("modifier")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { KeyboardModifier.valueOf(it) }
        }.getOrNull()
        val sequence = item.optString("sequence", "")
        val ctrl = item.optBoolean("ctrl", false)
        val alt = item.optBoolean("alt", false)
        val shift = item.optBoolean("shift", false)
        val repeatable = item.optBoolean("repeatable", false)
        return KeyboardSlotAction(
            type = type,
            label = label,
            text = text,
            keyCode = keyCode,
            modifier = modifier,
            sequence = sequence,
            ctrl = ctrl,
            alt = alt,
            shift = shift,
            repeatable = repeatable
        )
    }

    private fun mergedTerminalProfiles(serializedCustomProfiles: String?): List<TerminalProfile> {
        val builtIns = TerminalProfileDefaults.builtInProfiles
        val builtInIds = builtIns.map { it.id }.toSet()
        val customProfiles = serializedCustomProfiles
            ?.let(::decodeTerminalProfiles)
            .orEmpty()
            .filterNot { builtInIds.contains(it.id) }
        return builtIns + customProfiles
    }

    private fun encodeTerminalProfiles(profiles: List<TerminalProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("fontSizeSp", profile.fontSizeSp)
                    put("foregroundHex", profile.foregroundHex)
                    put("backgroundHex", profile.backgroundHex)
                    put("cursorHex", profile.cursorHex)
                    put("cursorStyle", profile.cursorStyle.name)
                    put("cursorBlink", profile.cursorBlink)
                }
            )
        }
        return array.toString()
    }

    private fun decodeTerminalProfiles(serialized: String): List<TerminalProfile> =
        runCatching {
            val array = JSONArray(serialized)
            val out = mutableListOf<TerminalProfile>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue
                val name = item.optString("name", "Profile").trim().ifBlank { "Profile" }
                out += TerminalProfile(
                    id = id,
                    name = name,
                    fontSizeSp = item.optInt("fontSizeSp", 10).coerceIn(8, 28),
                    foregroundHex = item.optString("foregroundHex", "#E6E6E6"),
                    backgroundHex = item.optString("backgroundHex", "#101010"),
                    cursorHex = item.optString("cursorHex", "#FFB74D"),
                    cursorStyle = runCatching {
                        TerminalCursorStyle.valueOf(item.optString("cursorStyle", TerminalCursorStyle.BLOCK.name))
                    }.getOrDefault(TerminalCursorStyle.BLOCK),
                    cursorBlink = item.optBoolean("cursorBlink", true)
                )
            }
            out.distinctBy { it.id }
        }.getOrDefault(emptyList())
}
