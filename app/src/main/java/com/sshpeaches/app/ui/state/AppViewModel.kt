package com.majordaftapps.sshpeaches.app.ui.state

import com.majordaftapps.sshpeaches.app.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.data.repository.AppRepository
import com.majordaftapps.sshpeaches.app.data.repository.InMemoryAppRepository
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(
    private val repository: AppRepository = InMemoryAppRepository()
) : ViewModel() {

    private val sortMode = MutableStateFlow(SortMode.LAST_USED)
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val backgroundSessionsFlow = MutableStateFlow(true)
    private val backgroundSessionTimeoutFlow = MutableStateFlow(BackgroundSessionTimeout.FOREVER)
    private val biometricFlow = MutableStateFlow(false)
    private val lockTimeoutFlow = MutableStateFlow(LockTimeout.FIVE_MIN)
    private val customLockTimeoutMinutesFlow = MutableStateFlow(30)
    private val terminalEmulationFlow = MutableStateFlow(TerminalEmulation.XTERM)
    private val terminalSelectionModeFlow = MutableStateFlow(TerminalSelectionMode.NATURAL)
    private val terminalProfilesFlow = MutableStateFlow(TerminalProfileDefaults.builtInProfiles)
    private val defaultTerminalProfileIdFlow = MutableStateFlow(TerminalProfileDefaults.DEFAULT_PROFILE_ID)
    private val crashReportsFlow = MutableStateFlow(false)
    private val analyticsFlow = MutableStateFlow(false)
    private val diagnosticsLoggingFlow = MutableStateFlow(false)
    private val includeIdentitiesFlow = MutableStateFlow(true)
    private val includeSettingsFlow = MutableStateFlow(true)
    private val autoStartForwardsFlow = MutableStateFlow(true)
    private val hostKeyPromptFlow = MutableStateFlow(true)
    private val autoTrustHostKeyFlow = MutableStateFlow(true)
    private val usageReportsFlow = MutableStateFlow(false)
    private val snippetRunTimeoutSecondsFlow = MutableStateFlow(10)
    private val pinConfiguredFlow = MutableStateFlow(SecurityManager.isPinSet())
    private val lockedFlow = MutableStateFlow(SecurityManager.isLocked())
    private val keyboardSlotsFlow = MutableStateFlow(KeyboardLayoutDefaults.DEFAULT_SLOTS)
    private var lockTimerJob: Job? = null
    private var appInBackground: Boolean = false

    init {
        viewModelScope.launch {
            SecurityManager.pinConfiguredState().collect { configured ->
                pinConfiguredFlow.value = configured
            }
        }
        viewModelScope.launch {
            SecurityManager.lockState().collect { locked ->
                lockedFlow.value = locked
                if (locked) {
                    lockTimerJob?.cancel()
                    lockTimerJob = null
                } else if (appInBackground) {
                    scheduleLockTimer(lockTimeoutFlow.value)
                } else {
                    lockTimerJob?.cancel()
                    lockTimerJob = null
                }
            }
        }
        viewModelScope.launch {
            SettingsStore.allowBackgroundSessions.collect { enabled ->
                backgroundSessionsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.backgroundSessionTimeout.collect { timeout ->
                backgroundSessionTimeoutFlow.value = timeout
            }
        }
        viewModelScope.launch {
            SettingsStore.themeMode.collect { mode ->
                themeModeFlow.value = mode
            }
        }
        viewModelScope.launch {
            SettingsStore.biometricLockEnabled.collect { enabled ->
                biometricFlow.value = enabled && SecurityManager.isPinSet()
            }
        }
        viewModelScope.launch {
            SettingsStore.lockTimeout.collect { timeout ->
                lockTimeoutFlow.value = timeout
            }
        }
        viewModelScope.launch {
            SettingsStore.terminalEmulation.collect { value ->
                terminalEmulationFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.terminalSelectionMode.collect { value ->
                terminalSelectionModeFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.terminalProfiles.collect { value ->
                terminalProfilesFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.defaultTerminalProfileId.collect { value ->
                defaultTerminalProfileIdFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.customLockTimeoutMinutes.collect { minutes ->
                customLockTimeoutMinutesFlow.value = minutes
            }
        }
        viewModelScope.launch {
            SettingsStore.crashReportsEnabled.collect { enabled ->
                crashReportsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.analyticsEnabled.collect { enabled ->
                analyticsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.diagnosticsEnabled.collect { enabled ->
                diagnosticsLoggingFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.includeIdentities.collect { enabled ->
                includeIdentitiesFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.includeSettings.collect { enabled ->
                includeSettingsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.autoStartForwards.collect { enabled ->
                autoStartForwardsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.hostKeyPromptEnabled.collect { enabled ->
                hostKeyPromptFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.autoTrustHostKeyEnabled.collect { enabled ->
                autoTrustHostKeyFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.usageReportsEnabled.collect { enabled ->
                usageReportsFlow.value = enabled
            }
        }
        viewModelScope.launch {
            SettingsStore.snippetRunTimeoutSeconds.collect { seconds ->
                snippetRunTimeoutSecondsFlow.value = seconds
            }
        }
        viewModelScope.launch {
            SettingsStore.keyboardLayout.collect { slots ->
                keyboardSlotsFlow.value = slots
            }
        }
    }

    private val baseUiState = combine(
        repository.hosts,
        repository.identities,
        repository.portForwards,
        repository.snippets,
        sortMode
    ) { hosts, identities, forwards, snippets, mode ->
        val hostList = hosts.sortedWith(if (mode == SortMode.ALPHABETICAL) byName else byLastUsed)
        val favoriteHosts = hostList.filter { it.favorite }
        val favoriteIdentities = identities.filter { it.favorite }
        val favoritePorts = forwards.filter { it.favorite }
        AppUiState(
            favorites = FavoritesSection(favoriteHosts, favoriteIdentities, favoritePorts),
            hosts = hostList,
            identities = identities,
            portForwards = forwards,
            snippets = snippets,
            sortMode = mode
        )
    }

    private data class PrivacyPrefs(
        val theme: ThemeMode,
        val background: Boolean,
        val backgroundTimeout: BackgroundSessionTimeout,
        val biometric: Boolean,
        val timeout: LockTimeout,
        val customTimeoutMinutes: Int,
        val terminalEmulation: TerminalEmulation,
        val terminalSelectionMode: TerminalSelectionMode,
        val terminalProfiles: List<TerminalProfile>,
        val defaultTerminalProfileId: String,
        val crash: Boolean,
        val analytics: Boolean,
        val diagnostics: Boolean
    )

    private data class PrivacyPartial(
        val theme: ThemeMode,
        val background: Boolean,
        val backgroundTimeout: BackgroundSessionTimeout,
        val biometric: Boolean,
        val timeout: LockTimeout,
        val customTimeoutMinutes: Int,
        val terminalEmulation: TerminalEmulation = TerminalEmulation.XTERM,
        val terminalSelectionMode: TerminalSelectionMode = TerminalSelectionMode.NATURAL,
        val terminalProfiles: List<TerminalProfile> = TerminalProfileDefaults.builtInProfiles,
        val defaultTerminalProfileId: String = TerminalProfileDefaults.DEFAULT_PROFILE_ID
    )

    private data class SharePrefs(
        val includeIds: Boolean,
        val includeSettings: Boolean,
        val autoStart: Boolean,
        val hostKeyPrompt: Boolean,
        val autoTrustHostKey: Boolean,
        val usage: Boolean
    )

    private val lockPrefsFlow = combine(
        lockTimeoutFlow,
        customLockTimeoutMinutesFlow
    ) { timeout, customMinutes ->
        timeout to customMinutes
    }

    private val privacyPartialBaseFlow = combine(
        themeModeFlow,
        backgroundSessionsFlow,
        backgroundSessionTimeoutFlow,
        biometricFlow,
        lockPrefsFlow
    ) { theme, background, backgroundTimeout, biometric, lockPrefs ->
        PrivacyPartial(
            theme = theme,
            background = background,
            backgroundTimeout = backgroundTimeout,
            biometric = biometric,
            timeout = lockPrefs.first,
            customTimeoutMinutes = lockPrefs.second
        )
    }

    private val privacyPartialFlow = combine(
        privacyPartialBaseFlow,
        terminalEmulationFlow,
        terminalSelectionModeFlow,
        terminalProfilesFlow,
        defaultTerminalProfileIdFlow
    ) { partial, terminalEmulation, selectionMode, profiles, defaultProfileId ->
        partial.copy(
            terminalEmulation = terminalEmulation,
            terminalSelectionMode = selectionMode,
            terminalProfiles = profiles,
            defaultTerminalProfileId = defaultProfileId
        )
    }

    private val privacyPrefsFlow = combine(
        privacyPartialFlow,
        crashReportsFlow,
        analyticsFlow,
        diagnosticsLoggingFlow
    ) { partial, crash, analytics, diagnostics ->
        PrivacyPrefs(
            theme = partial.theme,
            background = partial.background,
            backgroundTimeout = partial.backgroundTimeout,
            biometric = partial.biometric,
            timeout = partial.timeout,
            customTimeoutMinutes = partial.customTimeoutMinutes,
            terminalEmulation = partial.terminalEmulation,
            terminalSelectionMode = partial.terminalSelectionMode,
            terminalProfiles = partial.terminalProfiles,
            defaultTerminalProfileId = partial.defaultTerminalProfileId,
            crash = crash,
            analytics = analytics,
            diagnostics = diagnostics
        )
    }

    private val shareBasePrefsFlow = combine(
        includeIdentitiesFlow,
        includeSettingsFlow,
        autoStartForwardsFlow,
        hostKeyPromptFlow,
        autoTrustHostKeyFlow
    ) { includeIds, includeSettings, autoStart, hostKeyPrompt, autoTrustHostKey ->
        SharePrefs(includeIds, includeSettings, autoStart, hostKeyPrompt, autoTrustHostKey, false)
    }

    private val sharePrefsFlow = combine(
        shareBasePrefsFlow,
        usageReportsFlow
    ) { base, usage ->
        base.copy(usage = usage)
    }

    private val coreUiStateBase = combine(
        baseUiState,
        privacyPrefsFlow,
        sharePrefsFlow,
        pinConfiguredFlow,
        lockedFlow
    ) { state, privacy, share, pinSet, locked ->
        state.copy(
            themeMode = privacy.theme,
            allowBackgroundSessions = privacy.background,
            backgroundSessionTimeout = privacy.backgroundTimeout,
            biometricLockEnabled = privacy.biometric,
            lockTimeout = privacy.timeout,
            customLockTimeoutMinutes = privacy.customTimeoutMinutes,
            terminalEmulation = privacy.terminalEmulation,
            terminalSelectionMode = privacy.terminalSelectionMode,
            terminalProfiles = privacy.terminalProfiles,
            defaultTerminalProfileId = privacy.defaultTerminalProfileId,
            crashReportsEnabled = privacy.crash,
            analyticsEnabled = privacy.analytics,
            diagnosticsLoggingEnabled = privacy.diagnostics,
            includeIdentitiesInQr = share.includeIds,
            includeSettingsInQr = share.includeSettings,
            autoStartForwards = share.autoStart,
            hostKeyPromptEnabled = share.hostKeyPrompt,
            autoTrustHostKey = share.autoTrustHostKey,
            usageReportsEnabled = share.usage,
            pinConfigured = pinSet,
            isLocked = locked
        )
    }

    private val coreUiState = combine(
        coreUiStateBase,
        snippetRunTimeoutSecondsFlow
    ) { state, snippetTimeout ->
        state.copy(snippetRunTimeoutSeconds = snippetTimeout)
    }

    val uiState: StateFlow<AppUiState> = combine(
        coreUiState,
        keyboardSlotsFlow
    ) { state, slots ->
        state.copy(keyboardSlots = slots)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState()
    )

    init {
        if (BuildConfig.DEBUG) {
            viewModelScope.launch(Dispatchers.Default) {
                uiState
                    .map { stateFingerprint(it) }
                    .distinctUntilChanged()
                    .collect {
                        UiDebugLog.state("AppViewModel.uiState", uiState.value)
                    }
            }
        }
    }

    private fun logAction(action: String, details: String? = null) {
        UiDebugLog.action(action, details)
    }

    private fun logResult(action: String, success: Boolean, details: String? = null) {
        UiDebugLog.result(action, success, details)
    }

    private fun launchLogged(action: String, details: String? = null, work: suspend () -> Unit) {
        logAction(action, details)
        viewModelScope.launch {
            try {
                work()
                TelemetryInitializer.logUsageEvent(action)
                logResult(action, true)
            } catch (t: Throwable) {
                TelemetryInitializer.recordNonFatal(action, t)
                UiDebugLog.error(action, t)
                logResult(action, false, t.message ?: "exception")
                throw t
            }
        }
    }

    private fun stateFingerprint(state: AppUiState): String = buildString {
        append(state.hosts.size).append('|')
        append(state.identities.size).append('|')
        append(state.portForwards.size).append('|')
        append(state.snippets.size).append('|')
        append(state.sortMode).append('|')
        append(state.themeMode).append('|')
        append(state.allowBackgroundSessions).append('|')
        append(state.backgroundSessionTimeout).append('|')
        append(state.biometricLockEnabled).append('|')
        append(state.lockTimeout).append('|')
        append(state.customLockTimeoutMinutes).append('|')
        append(state.terminalEmulation).append('|')
        append(state.terminalSelectionMode).append('|')
        append(state.defaultTerminalProfileId).append('|')
        append(state.terminalProfiles.size).append('|')
        append(state.crashReportsEnabled).append('|')
        append(state.analyticsEnabled).append('|')
        append(state.diagnosticsLoggingEnabled).append('|')
        append(state.includeIdentitiesInQr).append('|')
        append(state.includeSettingsInQr).append('|')
        append(state.autoStartForwards).append('|')
        append(state.hostKeyPromptEnabled).append('|')
        append(state.autoTrustHostKey).append('|')
        append(state.usageReportsEnabled).append('|')
        append(state.pinConfigured).append('|')
        append(state.isLocked).append('|')
        append(state.keyboardSlots.size).append('|')
        append(state.keyboardSlots.count { !it.isEmpty() })
    }

    fun setSortMode(mode: SortMode) {
        logAction("setSortMode", "mode=$mode")
        sortMode.value = mode
        logResult("setSortMode", true)
    }

    fun setThemeMode(mode: ThemeMode) {
        launchLogged("setThemeMode", "mode=$mode") {
            SettingsStore.setThemeMode(mode)
        }
    }

    fun setBackgroundSessions(enabled: Boolean) {
        launchLogged("setBackgroundSessions", "enabled=$enabled") {
            SettingsStore.setAllowBackgroundSessions(enabled)
        }
    }

    fun setBackgroundSessionTimeout(timeout: BackgroundSessionTimeout) {
        launchLogged("setBackgroundSessionTimeout", "timeout=$timeout") {
            SettingsStore.setBackgroundSessionTimeout(timeout)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        logAction("setBiometricLock", "enabled=$enabled")
        if (enabled && !SecurityManager.isPinSet()) {
            logResult("setBiometricLock", false, "pin-not-configured")
            return
        }
        launchLogged("setBiometricLock", "enabled=$enabled") {
            SettingsStore.setBiometricLockEnabled(enabled)
        }
    }

    fun setLockTimeout(timeout: LockTimeout) {
        launchLogged("setLockTimeout", "timeout=$timeout") {
            SettingsStore.setLockTimeout(timeout)
            if (!lockedFlow.value && appInBackground) {
                scheduleLockTimer(timeout)
            }
        }
    }

    fun setCustomLockTimeoutMinutes(minutes: Int) {
        launchLogged("setCustomLockTimeoutMinutes", "minutes=$minutes") {
            SettingsStore.setCustomLockTimeoutMinutes(minutes)
            if (!lockedFlow.value && appInBackground && lockTimeoutFlow.value == LockTimeout.CUSTOM) {
                scheduleLockTimer(LockTimeout.CUSTOM)
            }
        }
    }

    fun setTerminalEmulation(value: TerminalEmulation) {
        launchLogged("setTerminalEmulation", "value=$value") {
            SettingsStore.setTerminalEmulation(value)
        }
    }

    fun setTerminalSelectionMode(value: TerminalSelectionMode) {
        launchLogged("setTerminalSelectionMode", "value=$value") {
            SettingsStore.setTerminalSelectionMode(value)
        }
    }

    fun setDefaultTerminalProfile(profileId: String) {
        launchLogged("setDefaultTerminalProfile", "profileId=$profileId") {
            val profiles = terminalProfilesFlow.value
            val selected = if (profiles.any { it.id == profileId }) {
                profileId
            } else {
                profiles.firstOrNull()?.id ?: TerminalProfileDefaults.DEFAULT_PROFILE_ID
            }
            SettingsStore.setDefaultTerminalProfileId(selected)
        }
    }

    fun saveTerminalProfile(profile: TerminalProfile) {
        launchLogged("saveTerminalProfile", "profileId=${profile.id}") {
            val normalized = normalizeProfile(profile)
            val builtInIds = TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet()
            if (builtInIds.contains(normalized.id)) {
                logResult("saveTerminalProfile", false, "cannot-overwrite-builtin")
                return@launchLogged
            }
            val duplicateName = terminalProfilesFlow.value.any {
                it.id != normalized.id && it.name.equals(normalized.name, ignoreCase = true)
            }
            if (duplicateName) {
                logResult("saveTerminalProfile", false, "duplicate-name")
                return@launchLogged
            }
            val updated = terminalProfilesFlow.value
                .filterNot { it.id == normalized.id }
                .plus(normalized)
                .sortedBy { it.name.lowercase() }
            SettingsStore.setTerminalProfiles(updated)
        }
    }

    fun deleteTerminalProfile(profileId: String) {
        launchLogged("deleteTerminalProfile", "profileId=$profileId") {
            val builtInIds = TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet()
            if (builtInIds.contains(profileId)) {
                logResult("deleteTerminalProfile", false, "cannot-delete-builtin")
                return@launchLogged
            }
            val updated = terminalProfilesFlow.value.filterNot { it.id == profileId }
            SettingsStore.setTerminalProfiles(updated)

            if (defaultTerminalProfileIdFlow.value == profileId) {
                val replacement = updated.firstOrNull()?.id ?: TerminalProfileDefaults.DEFAULT_PROFILE_ID
                SettingsStore.setDefaultTerminalProfileId(replacement)
            }

            val hostsUsingProfile = uiState.value.hosts.filter { it.terminalProfileId == profileId }
            hostsUsingProfile.forEach { host ->
                repository.updateHost(host.copy(terminalProfileId = null))
            }
        }
    }

    fun setCrashReports(enabled: Boolean) {
        launchLogged("setCrashReports", "enabled=$enabled") {
            SettingsStore.setCrashReportsEnabled(enabled)
        }
    }

    fun setAnalytics(enabled: Boolean) {
        launchLogged("setAnalytics", "enabled=$enabled") {
            SettingsStore.setAnalyticsEnabled(enabled)
        }
    }

    fun setDiagnosticsLogging(enabled: Boolean) {
        launchLogged("setDiagnosticsLogging", "enabled=$enabled") {
            SettingsStore.setDiagnosticsEnabled(enabled)
        }
    }

    fun setIncludeIdentities(enabled: Boolean) {
        launchLogged("setIncludeIdentities", "enabled=$enabled") {
            SettingsStore.setIncludeIdentities(enabled)
        }
    }

    fun setIncludeSettings(enabled: Boolean) {
        launchLogged("setIncludeSettings", "enabled=$enabled") {
            SettingsStore.setIncludeSettings(enabled)
        }
    }

    fun setAutoStartForwards(enabled: Boolean) {
        launchLogged("setAutoStartForwards", "enabled=$enabled") {
            SettingsStore.setAutoStartForwards(enabled)
        }
    }

    fun setHostKeyPrompt(enabled: Boolean) {
        launchLogged("setHostKeyPrompt", "enabled=$enabled") {
            SettingsStore.setHostKeyPromptEnabled(enabled)
        }
    }

    fun setAutoTrustHostKey(enabled: Boolean) {
        launchLogged("setAutoTrustHostKey", "enabled=$enabled") {
            SettingsStore.setAutoTrustHostKeyEnabled(enabled)
        }
    }

    fun setUsageReports(enabled: Boolean) {
        launchLogged("setUsageReports", "enabled=$enabled") {
            SettingsStore.setUsageReportsEnabled(enabled)
        }
    }

    fun setSnippetRunTimeoutSeconds(seconds: Int) {
        launchLogged("setSnippetRunTimeoutSeconds", "seconds=$seconds") {
            SettingsStore.setSnippetRunTimeoutSeconds(seconds)
        }
    }

    fun restoreDefaultSettings() {
        launchLogged("restoreDefaultSettings") {
            SettingsStore.resetToDefaults()
            if (!lockedFlow.value && appInBackground) {
                scheduleLockTimer(LockTimeout.FIVE_MIN)
            }
        }
    }

    fun addHost(
        name: String,
        host: String,
        port: Int,
        username: String,
        auth: AuthMethod,
        group: String?,
        notes: String,
        defaultMode: ConnectionMode,
        useMosh: Boolean,
        preferredIdentityId: String?,
        preferredForwardId: String?,
        startupScript: String,
        backgroundBehavior: BackgroundBehavior,
        terminalProfileId: String?,
        password: String?,
        suppliedId: String? = null
    ) {
        logAction(
            "addHost",
            "nameBlank=${name.isBlank()}, hostBlank=${host.isBlank()}, usernameBlank=${username.isBlank()}, port=$port, auth=$auth, mode=$defaultMode, useMosh=$useMosh, hasIdentity=${!preferredIdentityId.isNullOrBlank()}, hasForward=${!preferredForwardId.isNullOrBlank()}, hasScript=${startupScript.isNotBlank()}, hasPasswordInput=${!password.isNullOrBlank()}, hasTerminalProfile=${!terminalProfileId.isNullOrBlank()}"
        )
        if (name.isBlank() || host.isBlank() || username.isBlank()) {
            logResult("addHost", false, "validation-failed")
            return
        }
        val id = suppliedId ?: UUID.randomUUID().toString()
        val canStoreSecret = !SecurityManager.isLocked()
        val hasPassword = !password.isNullOrBlank() && canStoreSecret
        if (hasPassword) {
            SecurityManager.storeHostPassword(id, password!!)
        } else if (password != null) {
            SecurityManager.clearHostPassword(id)
        }
        val entry = HostConnection(
            id = id,
            name = name.trim(),
            host = host.trim(),
            port = port,
            username = username.trim(),
            preferredAuth = auth,
            group = group?.takeIf { it.isNotBlank() },
            notes = notes,
            defaultMode = defaultMode,
            hasPassword = hasPassword,
            useMosh = useMosh,
            preferredIdentityId = preferredIdentityId,
            preferredForwardId = preferredForwardId,
            startupScript = startupScript,
            backgroundBehavior = backgroundBehavior,
            terminalProfileId = terminalProfileId
        )
        launchLogged("addHost", "hostId=$id, storedPassword=$hasPassword") {
            repository.addHost(entry)
        }
    }

    fun updateHost(
        id: String,
        name: String,
        host: String,
        port: Int,
        username: String,
        auth: AuthMethod,
        group: String?,
        notes: String,
        defaultMode: ConnectionMode,
        useMosh: Boolean,
        preferredIdentityId: String?,
        preferredForwardId: String?,
        startupScript: String,
        backgroundBehavior: BackgroundBehavior,
        terminalProfileId: String?,
        password: String?
    ) {
        logAction(
            "updateHost",
            "hostId=$id, port=$port, auth=$auth, mode=$defaultMode, useMosh=$useMosh, hasIdentity=${!preferredIdentityId.isNullOrBlank()}, hasForward=${!preferredForwardId.isNullOrBlank()}, hasScript=${startupScript.isNotBlank()}, passwordProvided=${password != null}, hasTerminalProfile=${!terminalProfileId.isNullOrBlank()}"
        )
        val existing = uiState.value.hosts.find { it.id == id }
        if (existing == null) {
            logResult("updateHost", false, "not-found")
            return
        }
        val canStoreSecret = !SecurityManager.isLocked()
        val hasPassword = when {
            !password.isNullOrBlank() && canStoreSecret -> {
                SecurityManager.storeHostPassword(id, password)
                true
            }
            password != null && password.isBlank() -> {
                SecurityManager.clearHostPassword(id)
                false
            }
            else -> existing.hasPassword
        }
        val updated = existing.copy(
            name = name.ifBlank { existing.name },
            host = host.ifBlank { existing.host },
            port = port,
            username = username.ifBlank { existing.username },
            preferredAuth = auth,
            group = group?.takeIf { it.isNotBlank() },
            notes = notes,
            defaultMode = defaultMode,
            hasPassword = hasPassword,
            useMosh = useMosh,
            preferredIdentityId = preferredIdentityId,
            preferredForwardId = preferredForwardId,
            startupScript = startupScript,
            backgroundBehavior = backgroundBehavior,
            terminalProfileId = terminalProfileId
        )
        launchLogged("updateHost", "hostId=$id, storedPassword=$hasPassword") {
            repository.updateHost(updated)
        }
    }

    fun deleteHost(id: String) {
        logAction("deleteHost", "hostId=$id")
        val existing = uiState.value.hosts.find { it.id == id }
        if (existing == null) {
            logResult("deleteHost", false, "not-found")
            return
        }
        launchLogged("deleteHost", "hostId=$id, hadPassword=${existing.hasPassword}") {
            if (existing.hasPassword) {
                SecurityManager.clearHostPassword(id)
            }
            repository.deleteHost(existing)
        }
    }

    fun updateHostOsMetadata(id: String, osMetadata: OsMetadata) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        if (existing.osMetadata == osMetadata) return
        launchLogged("updateHostOsMetadata", "hostId=$id, os=$osMetadata") {
            repository.updateHost(existing.copy(osMetadata = osMetadata))
        }
    }

    fun updateHostInfoCommands(id: String, commands: List<String>) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        val normalized = commands.map { it.trim() }.filter { it.isNotBlank() }
        if (existing.infoCommands == normalized) return
        launchLogged("updateHostInfoCommands", "hostId=$id, count=${normalized.size}") {
            repository.updateHost(existing.copy(infoCommands = normalized))
        }
    }

    fun addPortForward(
        label: String,
        type: PortForwardType,
        sourceHost: String,
        sourcePort: Int,
        destHost: String,
        destPort: Int,
        enabled: Boolean,
        associatedHosts: List<String>
    ) {
        logAction("addPortForward", "labelBlank=${label.isBlank()}, type=$type, sourcePort=$sourcePort, destinationPort=$destPort, enabled=$enabled, associatedHosts=${associatedHosts.size}")
        if (label.isBlank()) {
            logResult("addPortForward", false, "validation-failed")
            return
        }
        if (type != PortForwardType.LOCAL) {
            logAction("addPortForward.localOnly", "requestedType=$type")
        }
        val normalizedType = PortForwardType.LOCAL
        val forward = PortForward(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            type = normalizedType,
            sourceHost = sourceHost.ifBlank { "127.0.0.1" },
            sourcePort = sourcePort,
            destinationHost = destHost,
            destinationPort = destPort,
            associatedHosts = associatedHosts,
            favorite = false,
            enabled = enabled
        )
        launchLogged("addPortForward", "forwardId=${forward.id}, type=$normalizedType") {
            repository.addPortForward(forward)
        }
    }

    fun updatePortForward(
        id: String,
        label: String,
        type: PortForwardType,
        sourceHost: String,
        sourcePort: Int,
        destHost: String,
        destPort: Int,
        enabled: Boolean,
        associatedHosts: List<String>
    ) {
        logAction("updatePortForward", "forwardId=$id, type=$type, sourcePort=$sourcePort, destinationPort=$destPort, enabled=$enabled, associatedHosts=${associatedHosts.size}")
        val existing = uiState.value.portForwards.find { it.id == id }
        if (existing == null) {
            logResult("updatePortForward", false, "not-found")
            return
        }
        if (type != PortForwardType.LOCAL) {
            logAction("updatePortForward.localOnly", "requestedType=$type")
        }
        val normalizedType = PortForwardType.LOCAL
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            type = normalizedType,
            sourceHost = sourceHost.ifBlank { existing.sourceHost },
            sourcePort = sourcePort,
            destinationHost = destHost,
            destinationPort = destPort,
            enabled = enabled,
            associatedHosts = associatedHosts
        )
        launchLogged("updatePortForward", "forwardId=$id") {
            repository.updatePortForward(updated)
        }
    }

    fun deletePortForward(id: String) {
        logAction("deletePortForward", "forwardId=$id")
        val existing = uiState.value.portForwards.find { it.id == id }
        if (existing == null) {
            logResult("deletePortForward", false, "not-found")
            return
        }
        launchLogged("deletePortForward", "forwardId=$id") {
            repository.deletePortForward(existing)
        }
    }

    fun addSnippet(title: String, description: String, command: String) {
        logAction(
            "addSnippet",
            "titleBlank=${title.isBlank()}, commandBlank=${command.isBlank()}"
        )
        if (command.isBlank()) {
            logResult("addSnippet", false, "validation-failed")
            return
        }
        val snippet = Snippet(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Snippet" },
            description = description,
            command = command
        )
        launchLogged("addSnippet", "snippetId=${snippet.id}") {
            repository.addSnippet(snippet)
        }
    }

    fun updateSnippet(id: String, title: String, description: String, command: String) {
        logAction("updateSnippet", "snippetId=$id, commandBlank=${command.isBlank()}")
        val existing = uiState.value.snippets.find { it.id == id }
        if (existing == null) {
            logResult("updateSnippet", false, "not-found")
            return
        }
        if (command.isBlank()) {
            logResult("updateSnippet", false, "validation-failed")
            return
        }
        val updated = existing.copy(
            title = title.ifBlank { existing.title },
            description = description,
            command = command
        )
        launchLogged("updateSnippet", "snippetId=$id") {
            repository.updateSnippet(updated)
        }
    }

    fun deleteSnippet(id: String) {
        logAction("deleteSnippet", "snippetId=$id")
        val existing = uiState.value.snippets.find { it.id == id }
        if (existing == null) {
            logResult("deleteSnippet", false, "not-found")
            return
        }
        launchLogged("deleteSnippet", "snippetId=$id") {
            repository.deleteSnippet(existing)
        }
    }

    fun toggleFavorite(id: String) {
        launchLogged("toggleFavorite", "id=$id") {
            repository.toggleFavorite(id)
        }
    }

    fun setPin(pin: String) {
        logAction("setPin", "pinLength=${pin.length}")
        SecurityManager.setPin(pin)
        pinConfiguredFlow.value = true
        lockedFlow.value = SecurityManager.isLocked()
        lockTimerJob?.cancel()
        lockTimerJob = null
        logResult("setPin", true, "locked=${lockedFlow.value}")
    }

    fun clearPin() {
        logAction("clearPin")
        SecurityManager.clearPin()
        pinConfiguredFlow.value = SecurityManager.isPinSet()
        lockedFlow.value = SecurityManager.isLocked()
        lockTimerJob?.cancel()
        lockTimerJob = null
        viewModelScope.launch {
            SettingsStore.setBiometricLockEnabled(false)
        }
        logResult("clearPin", true, "pinConfigured=${pinConfiguredFlow.value}, locked=${lockedFlow.value}")
    }

    fun lockApp() {
        logAction("lockApp")
        SecurityManager.lock()
        lockedFlow.value = SecurityManager.isLocked()
        lockTimerJob?.cancel()
        lockTimerJob = null
        logResult("lockApp", true, "locked=${lockedFlow.value}")
    }

    fun unlockWithPin(pin: String): Boolean {
        logAction("unlockWithPin", "pinLength=${pin.length}")
        val ok = SecurityManager.verifyPin(pin)
        if (ok) {
            lockedFlow.value = SecurityManager.isLocked()
            if (!lockedFlow.value && appInBackground) {
                scheduleLockTimer(lockTimeoutFlow.value)
            }
        }
        logResult("unlockWithPin", ok, "locked=${lockedFlow.value}")
        return ok
    }

    fun unlockWithBiometric() {
        logAction("unlockWithBiometric")
        SecurityManager.unlock()
        lockedFlow.value = SecurityManager.isLocked()
        if (!lockedFlow.value && appInBackground) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
        logResult("unlockWithBiometric", true, "locked=${lockedFlow.value}")
    }

    fun onUserInteraction() {
        logAction("onUserInteraction", "locked=${lockedFlow.value}")
        logResult("onUserInteraction", true)
    }

    fun onAppBackgrounded() {
        logAction("onAppBackgrounded", "locked=${lockedFlow.value}")
        appInBackground = true
        if (!lockedFlow.value) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
        logResult("onAppBackgrounded", true, "timerActive=${lockTimerJob != null}")
    }

    fun onAppForegrounded() {
        logAction("onAppForegrounded", "locked=${lockedFlow.value}")
        appInBackground = false
        lockTimerJob?.cancel()
        lockTimerJob = null
        logResult("onAppForegrounded", true)
    }

    fun addIdentity(label: String, fingerprint: String, username: String?, suppliedId: String? = null, hasPrivateKey: Boolean = false) {
        logAction("addIdentity", "labelBlank=${label.isBlank()}, fingerprintBlank=${fingerprint.isBlank()}, hasUsername=${!username.isNullOrBlank()}, hasPrivateKey=$hasPrivateKey")
        if (fingerprint.isBlank()) {
            logResult("addIdentity", false, "validation-failed")
            return
        }
        val identity = Identity(
            id = suppliedId ?: UUID.randomUUID().toString(),
            label = label.ifBlank { "Identity ${System.currentTimeMillis() / 1000}" },
            fingerprint = fingerprint.trim(),
            username = username?.takeIf { it.isNotBlank() },
            createdEpochMillis = System.currentTimeMillis(),
            lastUsedEpochMillis = null,
            favorite = false,
            hasPrivateKey = hasPrivateKey
        )
        launchLogged("addIdentity", "identityId=${identity.id}, hasPrivateKey=$hasPrivateKey") {
            repository.addIdentity(identity)
        }
    }

    fun updateIdentity(id: String, label: String, fingerprint: String, username: String?) {
        logAction("updateIdentity", "identityId=$id, labelBlank=${label.isBlank()}, fingerprintBlank=${fingerprint.isBlank()}, hasUsername=${!username.isNullOrBlank()}")
        val existing = uiState.value.identities.find { it.id == id }
        if (existing == null) {
            logResult("updateIdentity", false, "not-found")
            return
        }
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            fingerprint = fingerprint.trim().ifBlank { existing.fingerprint },
            username = username?.takeIf { it.isNotBlank() }
        )
        launchLogged("updateIdentity", "identityId=$id") {
            repository.updateIdentity(updated)
        }
    }

    fun deleteIdentity(id: String) {
        logAction("deleteIdentity", "identityId=$id")
        val existing = uiState.value.identities.find { it.id == id }
        if (existing == null) {
            logResult("deleteIdentity", false, "not-found")
            return
        }
        launchLogged("deleteIdentity", "identityId=$id, hadPrivateKey=${existing.hasPrivateKey}") {
            SecurityManager.clearIdentityKey(id)
            repository.deleteIdentity(existing)
        }
    }

    fun markIdentityHasKey(id: String, hasKey: Boolean) {
        logAction("markIdentityHasKey", "identityId=$id, hasKey=$hasKey")
        val current = uiState.value.identities.find { it.id == id }
        if (current == null) {
            logResult("markIdentityHasKey", false, "not-found")
            return
        }
        if (current.hasPrivateKey == hasKey) {
            logResult("markIdentityHasKey", true, "no-change")
            return
        }
        val updated = current.copy(
            hasPrivateKey = hasKey,
            keyImportEpochMillis = if (hasKey) System.currentTimeMillis() else null
        )
        launchLogged("markIdentityHasKey", "identityId=$id, hasKey=$hasKey") {
            repository.updateIdentity(updated)
        }
    }

    fun importIdentityKeyFromPayload(id: String, payload: String, passphrase: String): Boolean {
        logAction("importIdentityKeyFromPayload", "identityId=$id, payloadLength=${payload.length}, passphraseLength=${passphrase.length}")
        val ok = runCatching {
            SecurityManager.importIdentityKeyPayload(id, payload, passphrase)
            SecurityManager.getIdentityKey(id)?.let { privateKey ->
                val derivedPublic = SshKeyGenerator.derivePublicKeyFromPrivate(privateKey).orEmpty()
                if (derivedPublic.isNotBlank()) {
                    SecurityManager.storeIdentityPublicKey(id, derivedPublic)
                }
            }
            markIdentityHasKeyWithRetry(id, true)
        }.onFailure { t ->
            UiDebugLog.error("importIdentityKeyFromPayload", t, "identityId=$id")
        }.isSuccess
        logResult("importIdentityKeyFromPayload", ok, "identityId=$id")
        return ok
    }

    fun importIdentityKeyPlain(id: String, key: String): Boolean {
        logAction("importIdentityKeyPlain", "identityId=$id, keyLength=${key.length}")
        val ok = runCatching {
            SecurityManager.storeIdentityKey(id, key)
            val derivedPublic = SshKeyGenerator.derivePublicKeyFromPrivate(key).orEmpty()
            if (derivedPublic.isNotBlank()) {
                SecurityManager.storeIdentityPublicKey(id, derivedPublic)
            }
            markIdentityHasKeyWithRetry(id, true)
        }.onFailure { t ->
            UiDebugLog.error("importIdentityKeyPlain", t, "identityId=$id")
        }.isSuccess
        logResult("importIdentityKeyPlain", ok, "identityId=$id")
        return ok
    }

    fun removeIdentityKey(id: String) {
        logAction("removeIdentityKey", "identityId=$id")
        SecurityManager.clearIdentityKey(id)
        markIdentityHasKey(id, false)
        logResult("removeIdentityKey", true, "identityId=$id")
    }

    fun storeIdentityPublicKey(id: String, publicKey: String): Boolean {
        logAction("storeIdentityPublicKey", "identityId=$id, keyLength=${publicKey.length}")
        val ok = runCatching {
            SecurityManager.storeIdentityPublicKey(id, publicKey.trim())
        }.isSuccess
        logResult("storeIdentityPublicKey", ok, "identityId=$id")
        return ok
    }

    fun storeIdentityKeyPassphrase(id: String, passphrase: String?) {
        logAction("storeIdentityKeyPassphrase", "identityId=$id, passphraseLength=${passphrase?.length ?: 0}")
        runCatching {
            SecurityManager.storeIdentityKeyPassphrase(id, passphrase)
        }.onFailure { t ->
            UiDebugLog.error("storeIdentityKeyPassphrase", t, "identityId=$id")
        }
        logResult("storeIdentityKeyPassphrase", true, "identityId=$id")
    }

    private fun markIdentityHasKeyWithRetry(id: String, hasKey: Boolean) {
        if (uiState.value.identities.any { it.id == id }) {
            markIdentityHasKey(id, hasKey)
            return
        }
        viewModelScope.launch {
            repeat(12) {
                delay(75)
                if (uiState.value.identities.any { identity -> identity.id == id }) {
                    markIdentityHasKey(id, hasKey)
                    return@launch
                }
            }
            logResult("markIdentityHasKeyWithRetry", false, "identityId=$id, hasKey=$hasKey, not-found-after-retry")
        }
    }

    fun updateKeyboardSlot(index: Int, value: KeyboardSlotAction) {
        logAction("updateKeyboardSlot", "index=$index, type=${value.type}")
        if (index !in 0 until KeyboardLayoutDefaults.SLOT_COUNT) {
            logResult("updateKeyboardSlot", false, "index-out-of-range")
            return
        }
        val updated = keyboardSlotsFlow.value.toMutableList().also { list ->
            list[index] = value
        }.toList()
        keyboardSlotsFlow.value = updated
        launchLogged("updateKeyboardSlot", "index=$index") {
            SettingsStore.setKeyboardLayout(updated)
        }
    }

    fun resetKeyboardLayout() {
        logAction("resetKeyboardLayout")
        keyboardSlotsFlow.value = KeyboardLayoutDefaults.DEFAULT_SLOTS
        launchLogged("resetKeyboardLayout") {
            SettingsStore.setKeyboardLayout(KeyboardLayoutDefaults.DEFAULT_SLOTS)
        }
    }

    private fun normalizeProfile(profile: TerminalProfile): TerminalProfile {
        val normalizedName = profile.name.trim().ifBlank { "Custom Profile" }.take(48)
        return profile.copy(
            name = normalizedName,
            fontSizeSp = profile.fontSizeSp.coerceIn(8, 28)
        )
    }

    private fun scheduleLockTimer(timeout: LockTimeout) {
        lockTimerJob?.cancel()
        val duration = lockTimeoutDuration(timeout) ?: return
        if (duration <= 0L) {
            lockApp()
            return
        }
        lockTimerJob = viewModelScope.launch {
            delay(duration)
            lockApp()
        }
    }

    private fun lockTimeoutDuration(timeout: LockTimeout): Long? = when (timeout) {
        LockTimeout.IMMEDIATE -> 0L
        LockTimeout.ONE_MIN -> 60_000L
        LockTimeout.FIVE_MIN -> 300_000L
        LockTimeout.FIFTEEN_MIN -> 900_000L
        LockTimeout.CUSTOM -> customLockTimeoutMinutesFlow.value * 60_000L
    }

    companion object {
        private val byLastUsed = compareByDescending<HostConnection> { it.lastUsedEpochMillis ?: 0L }
        private val byName = compareBy<HostConnection> { it.name.lowercase() }

        fun provideFactory(repository: AppRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AppViewModel(repository)
                }
            }
    }
}
