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
import com.majordaftapps.sshpeaches.app.data.repository.InMemoryUptimeRepository
import com.majordaftapps.sshpeaches.app.data.repository.UptimeRepository
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.util.normalizeAssociatedHostIds
import com.majordaftapps.sshpeaches.app.util.SshKeyGenerator
import com.majordaftapps.sshpeaches.app.uptime.NoOpUptimeMonitorRunner
import com.majordaftapps.sshpeaches.app.uptime.UptimeMonitorRunnerDelegate
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import com.majordaftapps.sshpeaches.app.data.model.UptimeCheckMethod
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
    private val repository: AppRepository = InMemoryAppRepository(),
    private val uptimeRepository: UptimeRepository = InMemoryUptimeRepository(),
    private val uptimeMonitorRunner: UptimeMonitorRunnerDelegate = NoOpUptimeMonitorRunner()
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
    private val terminalBellModeFlow = MutableStateFlow(TerminalBellMode.DISABLED)
    private val terminalVolumeButtonsAdjustFontSizeFlow = MutableStateFlow(false)
    private val terminalMarginPxFlow = MutableStateFlow(0)
    private val moshServerCommandFlow = MutableStateFlow(DEFAULT_MOSH_SERVER_COMMAND)
    private val terminalProfilesFlow = MutableStateFlow(TerminalProfileDefaults.builtInProfiles)
    private val defaultTerminalProfileIdFlow = MutableStateFlow(TerminalProfileDefaults.DEFAULT_PROFILE_ID)
    private val crashReportsFlow = MutableStateFlow(false)
    private val analyticsFlow = MutableStateFlow(false)
    private val diagnosticsLoggingFlow = MutableStateFlow(false)
    private val includeSecretsInQrFlow = MutableStateFlow(false)
    private val autoStartForwardsFlow = MutableStateFlow(true)
    private val hostKeyPromptFlow = MutableStateFlow(true)
    private val autoTrustHostKeyFlow = MutableStateFlow(false)
    private val usageReportsFlow = MutableStateFlow(false)
    private val snippetRunTimeoutSecondsFlow = MutableStateFlow(10)
    private val pinConfiguredFlow = MutableStateFlow(SecurityManager.isPinSet())
    private val lockedFlow = MutableStateFlow(SecurityManager.isLocked())
    private val keyboardSlotsFlow = MutableStateFlow(KeyboardLayoutDefaults.DEFAULT_SLOTS)
    private var uptimeTickerJob: Job? = null
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
            SettingsStore.terminalBellMode.collect { value ->
                terminalBellModeFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.terminalVolumeButtonsAdjustFontSize.collect { value ->
                terminalVolumeButtonsAdjustFontSizeFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.terminalMarginPx.collect { value ->
                terminalMarginPxFlow.value = value
            }
        }
        viewModelScope.launch {
            SettingsStore.moshServerCommand.collect { value ->
                moshServerCommandFlow.value = value
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
            SettingsStore.includeSecretsInQr.collect { enabled ->
                includeSecretsInQrFlow.value = enabled
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
        val favoriteSnippets = snippets.filter { it.favorite }
        val favorites = FavoritesSection(
            hostFavorites = favoriteHosts,
            identityFavorites = favoriteIdentities,
            portFavorites = favoritePorts,
            snippetFavorites = favoriteSnippets
        )
        val recents = buildHomeRecents(hosts, identities, forwards, snippets)
        AppUiState(
            home = HomeSection(favorites = favorites, recents = recents),
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
        val terminalBellMode: TerminalBellMode,
        val terminalVolumeButtonsAdjustFontSize: Boolean,
        val terminalMarginPx: Int,
        val moshServerCommand: String,
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
        val terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
        val terminalVolumeButtonsAdjustFontSize: Boolean = false,
        val terminalMarginPx: Int = 0,
        val moshServerCommand: String = DEFAULT_MOSH_SERVER_COMMAND,
        val terminalProfiles: List<TerminalProfile> = TerminalProfileDefaults.builtInProfiles,
        val defaultTerminalProfileId: String = TerminalProfileDefaults.DEFAULT_PROFILE_ID
    )

    private data class TerminalPrivacyPartial(
        val terminalEmulation: TerminalEmulation = TerminalEmulation.XTERM,
        val terminalSelectionMode: TerminalSelectionMode = TerminalSelectionMode.NATURAL,
        val terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
        val terminalVolumeButtonsAdjustFontSize: Boolean = false,
        val terminalMarginPx: Int = 0,
        val moshServerCommand: String = DEFAULT_MOSH_SERVER_COMMAND,
        val terminalProfiles: List<TerminalProfile> = TerminalProfileDefaults.builtInProfiles,
        val defaultTerminalProfileId: String = TerminalProfileDefaults.DEFAULT_PROFILE_ID
    )

    private data class TerminalBehaviorPrefs(
        val terminalEmulation: TerminalEmulation = TerminalEmulation.XTERM,
        val terminalSelectionMode: TerminalSelectionMode = TerminalSelectionMode.NATURAL,
        val terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
        val terminalVolumeButtonsAdjustFontSize: Boolean = false
    )

    private data class TerminalAppearancePrefs(
        val terminalMarginPx: Int = 0,
        val moshServerCommand: String = DEFAULT_MOSH_SERVER_COMMAND,
        val terminalProfiles: List<TerminalProfile> = TerminalProfileDefaults.builtInProfiles,
        val defaultTerminalProfileId: String = TerminalProfileDefaults.DEFAULT_PROFILE_ID
    )

    private data class SharePrefs(
        val includeSecrets: Boolean,
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

    private val terminalBehaviorPrefsFlow = combine(
        terminalEmulationFlow,
        terminalSelectionModeFlow,
        terminalBellModeFlow,
        terminalVolumeButtonsAdjustFontSizeFlow
    ) { terminalEmulation, selectionMode, terminalBellMode, terminalVolumeButtonsAdjustFontSize ->
        TerminalBehaviorPrefs(
            terminalEmulation = terminalEmulation,
            terminalSelectionMode = selectionMode,
            terminalBellMode = terminalBellMode,
            terminalVolumeButtonsAdjustFontSize = terminalVolumeButtonsAdjustFontSize
        )
    }

    private val terminalAppearancePrefsFlow = combine(
        terminalMarginPxFlow,
        moshServerCommandFlow,
        terminalProfilesFlow,
        defaultTerminalProfileIdFlow
    ) { terminalMarginPx, moshServerCommand, profiles, defaultProfileId ->
        TerminalAppearancePrefs(
            terminalMarginPx = terminalMarginPx,
            moshServerCommand = moshServerCommand,
            terminalProfiles = profiles,
            defaultTerminalProfileId = defaultProfileId
        )
    }

    private val privacyPartialFlow = combine(
        privacyPartialBaseFlow,
        terminalBehaviorPrefsFlow,
        terminalAppearancePrefsFlow
    ) { partial, terminalBehavior, terminalAppearance ->
        partial.copy(
            terminalEmulation = terminalBehavior.terminalEmulation,
            terminalSelectionMode = terminalBehavior.terminalSelectionMode,
            terminalBellMode = terminalBehavior.terminalBellMode,
            terminalVolumeButtonsAdjustFontSize = terminalBehavior.terminalVolumeButtonsAdjustFontSize,
            terminalMarginPx = terminalAppearance.terminalMarginPx,
            moshServerCommand = terminalAppearance.moshServerCommand,
            terminalProfiles = terminalAppearance.terminalProfiles,
            defaultTerminalProfileId = terminalAppearance.defaultTerminalProfileId
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
            terminalBellMode = partial.terminalBellMode,
            terminalVolumeButtonsAdjustFontSize = partial.terminalVolumeButtonsAdjustFontSize,
            terminalMarginPx = partial.terminalMarginPx,
            moshServerCommand = partial.moshServerCommand,
            terminalProfiles = partial.terminalProfiles,
            defaultTerminalProfileId = partial.defaultTerminalProfileId,
            crash = crash,
            analytics = analytics,
            diagnostics = diagnostics
        )
    }

    private val shareBasePrefsFlow = combine(
        includeSecretsInQrFlow,
        autoStartForwardsFlow,
        hostKeyPromptFlow,
        autoTrustHostKeyFlow
    ) { includeSecrets, autoStart, hostKeyPrompt, autoTrustHostKey ->
        SharePrefs(includeSecrets, autoStart, hostKeyPrompt, autoTrustHostKey, false)
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
            terminalBellMode = privacy.terminalBellMode,
            terminalVolumeButtonsAdjustFontSize = privacy.terminalVolumeButtonsAdjustFontSize,
            terminalMarginPx = privacy.terminalMarginPx,
            moshServerCommand = privacy.moshServerCommand,
            terminalProfiles = privacy.terminalProfiles,
            defaultTerminalProfileId = privacy.defaultTerminalProfileId,
            crashReportsEnabled = privacy.crash,
            analyticsEnabled = privacy.analytics,
            diagnosticsLoggingEnabled = privacy.diagnostics,
            includeSecretsInQr = share.includeSecrets,
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

    private val uptimeUiState = combine(
        coreUiState,
        uptimeRepository.summaries
    ) { state, uptimeSummaries ->
        state.copy(uptimeSummaries = uptimeSummaries)
    }

    val uiState: StateFlow<AppUiState> = combine(
        uptimeUiState,
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
        append(state.terminalBellMode).append('|')
        append(state.terminalVolumeButtonsAdjustFontSize).append('|')
        append(state.terminalMarginPx).append('|')
        append(state.moshServerCommand).append('|')
        append(state.defaultTerminalProfileId).append('|')
        append(state.terminalProfiles.size).append('|')
        append(state.crashReportsEnabled).append('|')
        append(state.analyticsEnabled).append('|')
        append(state.diagnosticsLoggingEnabled).append('|')
        append(state.includeSecretsInQr).append('|')
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

    fun setTerminalBellMode(value: TerminalBellMode) {
        launchLogged("setTerminalBellMode", "value=$value") {
            SettingsStore.setTerminalBellMode(value)
        }
    }

    fun setTerminalVolumeButtonsAdjustFontSize(enabled: Boolean) {
        launchLogged("setTerminalVolumeButtonsAdjustFontSize", "enabled=$enabled") {
            SettingsStore.setTerminalVolumeButtonsAdjustFontSize(enabled)
        }
    }

    fun setTerminalMarginPx(pixels: Int) {
        launchLogged("setTerminalMarginPx", "pixels=$pixels") {
            SettingsStore.setTerminalMarginPx(pixels)
        }
    }

    fun setMoshServerCommand(command: String) {
        launchLogged("setMoshServerCommand", "command=${command.take(64)}") {
            SettingsStore.setMoshServerCommand(command)
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

    fun setIncludeSecretsInQr(enabled: Boolean) {
        launchLogged("setIncludeSecretsInQr", "enabled=$enabled") {
            SettingsStore.setIncludeSecretsInQr(enabled)
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
        val now = System.currentTimeMillis()
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
            createdEpochMillis = now,
            updatedEpochMillis = now,
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
        val now = System.currentTimeMillis()
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
            updatedEpochMillis = now,
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

    fun addHostToUptime(hostId: String) {
        launchLogged("addHostToUptime", "hostId=$hostId") {
            uptimeRepository.addHost(hostId)
        }
    }

    fun updateUptimeConfig(
        hostId: String,
        method: UptimeCheckMethod,
        port: Int,
        intervalMinutes: Int,
        enabled: Boolean
    ) {
        launchLogged(
            "updateUptimeConfig",
            "hostId=$hostId, method=$method, port=$port, intervalMinutes=$intervalMinutes, enabled=$enabled"
        ) {
            uptimeRepository.updateConfig(
                hostId = hostId,
                method = method,
                port = port.coerceIn(1, 65_535),
                intervalMinutes = intervalMinutes.coerceIn(1, 60),
                enabled = enabled
            )
        }
    }

    fun setUptimeEnabled(hostId: String, enabled: Boolean) {
        launchLogged("setUptimeEnabled", "hostId=$hostId, enabled=$enabled") {
            uptimeRepository.setEnabled(hostId, enabled)
        }
    }

    fun removeHostFromUptime(hostId: String) {
        launchLogged("removeHostFromUptime", "hostId=$hostId") {
            uptimeRepository.removeHost(hostId)
        }
    }

    fun refreshUptime(hostId: String? = null) {
        launchLogged("refreshUptime", "hostId=${hostId.orEmpty()}") {
            uptimeMonitorRunner.runDueChecks(hostId = hostId)
        }
    }

    fun importHostPasswordPayload(id: String, payload: String, passphrase: String): Boolean {
        logAction(
            "importHostPasswordPayload",
            "hostId=$id, payloadLength=${payload.length}, passphraseLength=${passphrase.length}"
        )
        val ok = runCatching {
            SecurityManager.importHostPasswordPayload(id, payload, passphrase)
            markHostHasPasswordWithRetry(id, true)
        }.onFailure { t ->
            UiDebugLog.error("importHostPasswordPayload", t, "hostId=$id")
        }.isSuccess
        logResult("importHostPasswordPayload", ok, "hostId=$id")
        return ok
    }

    fun importHost(host: HostConnection) {
        logAction("importHost", "hostId=${host.id}, favorite=${host.favorite}, hasPassword=${host.hasPassword}")
        if (host.name.isBlank() || host.host.isBlank() || host.username.isBlank()) {
            logResult("importHost", false, "validation-failed")
            return
        }
        val normalized = host.copy(
            name = host.name.trim(),
            host = host.host.trim(),
            port = host.port.coerceIn(1, 65_535),
            username = host.username.trim(),
            group = host.group?.takeIf { it.isNotBlank() },
            createdEpochMillis = host.createdEpochMillis ?: host.lastUsedEpochMillis ?: System.currentTimeMillis(),
            updatedEpochMillis = host.updatedEpochMillis ?: host.createdEpochMillis ?: host.lastUsedEpochMillis
                ?: System.currentTimeMillis(),
            preferredIdentityId = host.preferredIdentityId?.takeIf { it.isNotBlank() },
            preferredForwardId = host.preferredForwardId?.takeIf { it.isNotBlank() },
            terminalProfileId = host.terminalProfileId?.takeIf { it.isNotBlank() },
            infoCommands = host.infoCommands.map { it.trim() }.filter { it.isNotBlank() }
        )
        launchLogged("importHost", "hostId=${normalized.id}") {
            repository.addHost(normalized)
        }
    }

    fun updateHostOsMetadata(id: String, osMetadata: OsMetadata) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        if (existing.osMetadata == osMetadata) return
        launchLogged("updateHostOsMetadata", "hostId=$id, os=$osMetadata") {
            repository.updateHost(
                existing.copy(
                    osMetadata = osMetadata,
                    updatedEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateHostInfoCommands(id: String, commands: List<String>) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        val normalized = commands.map { it.trim() }.filter { it.isNotBlank() }
        if (existing.infoCommands == normalized) return
        launchLogged("updateHostInfoCommands", "hostId=$id, count=${normalized.size}") {
            repository.updateHost(
                existing.copy(
                    infoCommands = normalized,
                    updatedEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun markHostHasPasswordWithRetry(id: String, hasPassword: Boolean) {
        val current = uiState.value.hosts.find { it.id == id }
        if (current != null) {
            if (current.hasPassword == hasPassword) {
                logResult("markHostHasPasswordWithRetry", true, "hostId=$id, no-change")
                return
            }
            launchLogged("markHostHasPassword", "hostId=$id, hasPassword=$hasPassword") {
                repository.updateHost(
                    current.copy(
                        hasPassword = hasPassword,
                        updatedEpochMillis = System.currentTimeMillis()
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            repeat(12) {
                delay(75)
                val host = uiState.value.hosts.find { it.id == id }
                if (host != null) {
                    if (host.hasPassword != hasPassword) {
                        launchLogged("markHostHasPassword", "hostId=$id, hasPassword=$hasPassword") {
                            repository.updateHost(
                                host.copy(
                                    hasPassword = hasPassword,
                                    updatedEpochMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    return@launch
                }
            }
            logResult(
                "markHostHasPasswordWithRetry",
                false,
                "hostId=$id, hasPassword=$hasPassword, not-found-after-retry"
            )
        }
    }

    fun addPortForward(
        label: String,
        group: String?,
        type: PortForwardType,
        sourceHost: String,
        sourcePort: Int,
        ignoredDestHost: String,
        destPort: Int,
        enabled: Boolean,
        associatedHosts: List<String>
    ) {
        logAction("addPortForward", "labelBlank=${label.isBlank()}, type=$type, sourcePort=$sourcePort, destinationPort=$destPort, passedDestHost=${ignoredDestHost.isNotBlank()}, enabled=$enabled, associatedHosts=${associatedHosts.size}")
        if (label.isBlank()) {
            logResult("addPortForward", false, "validation-failed")
            return
        }
        val normalizedAssociatedHosts = normalizeAssociatedHostIds(associatedHosts)
        val selectedHost = normalizedAssociatedHosts.firstOrNull()?.let { selectedId ->
            uiState.value.hosts.find { it.id == selectedId }
        }
        if (selectedHost == null) {
            logResult("addPortForward", false, "validation-failed-no-host")
            return
        }
        if (type != PortForwardType.LOCAL) {
            logAction("addPortForward.localOnly", "requestedType=$type")
        }
        val normalizedType = PortForwardType.LOCAL
        val now = System.currentTimeMillis()
        val forward = PortForward(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = now,
            updatedEpochMillis = now,
            lastUsedEpochMillis = if (enabled) now else null,
            type = normalizedType,
            sourceHost = sourceHost.ifBlank { "127.0.0.1" },
            sourcePort = sourcePort,
            destinationHost = selectedHost.host,
            destinationPort = destPort,
            associatedHosts = normalizedAssociatedHosts,
            favorite = false,
            enabled = enabled
        )
        launchLogged("addPortForward", "forwardId=${forward.id}, type=$normalizedType") {
            repository.addPortForward(forward)
        }
    }

    fun importPortForward(forward: PortForward) {
        logAction("importPortForward", "forwardId=${forward.id}, favorite=${forward.favorite}, enabled=${forward.enabled}")
        if (forward.label.isBlank() || forward.destinationHost.isBlank()) {
            logResult("importPortForward", false, "validation-failed")
            return
        }
        val normalized = forward.copy(
            label = forward.label.trim(),
            group = forward.group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = forward.createdEpochMillis ?: System.currentTimeMillis(),
            updatedEpochMillis = forward.updatedEpochMillis ?: forward.createdEpochMillis ?: System.currentTimeMillis(),
            type = PortForwardType.LOCAL,
            sourceHost = forward.sourceHost.ifBlank { "127.0.0.1" },
            sourcePort = forward.sourcePort.coerceIn(1, 65_535),
            destinationHost = forward.destinationHost.trim(),
            destinationPort = forward.destinationPort.coerceIn(1, 65_535)
        )
        launchLogged("importPortForward", "forwardId=${normalized.id}") {
            repository.addPortForward(normalized)
        }
    }

    fun updatePortForward(
        id: String,
        label: String,
        group: String?,
        type: PortForwardType,
        sourceHost: String,
        sourcePort: Int,
        ignoredDestHost: String,
        destPort: Int,
        enabled: Boolean,
        associatedHosts: List<String>
    ) {
        logAction("updatePortForward", "forwardId=$id, type=$type, sourcePort=$sourcePort, destinationPort=$destPort, passedDestHost=${ignoredDestHost.isNotBlank()}, enabled=$enabled, associatedHosts=${associatedHosts.size}")
        val existing = uiState.value.portForwards.find { it.id == id }
        if (existing == null) {
            logResult("updatePortForward", false, "not-found")
            return
        }
        val normalizedAssociatedHosts = normalizeAssociatedHostIds(associatedHosts)
        val selectedHost = normalizedAssociatedHosts.firstOrNull()?.let { selectedId ->
            uiState.value.hosts.find { it.id == selectedId }
        }
        if (selectedHost == null) {
            logResult("updatePortForward", false, "validation-failed-no-host")
            return
        }
        if (type != PortForwardType.LOCAL) {
            logAction("updatePortForward.localOnly", "requestedType=$type")
        }
        val normalizedType = PortForwardType.LOCAL
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            updatedEpochMillis = now,
            lastUsedEpochMillis = if (enabled) now else existing.lastUsedEpochMillis,
            type = normalizedType,
            sourceHost = sourceHost.ifBlank { existing.sourceHost },
            sourcePort = sourcePort,
            destinationHost = selectedHost.host,
            destinationPort = destPort,
            enabled = enabled,
            associatedHosts = normalizedAssociatedHosts
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

    fun addSnippet(title: String, group: String?, description: String, command: String) {
        logAction(
            "addSnippet",
            "titleBlank=${title.isBlank()}, commandBlank=${command.isBlank()}"
        )
        if (command.isBlank()) {
            logResult("addSnippet", false, "validation-failed")
            return
        }
        val now = System.currentTimeMillis()
        val snippet = Snippet(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Snippet" },
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = now,
            updatedEpochMillis = now,
            description = description,
            command = command
        )
        launchLogged("addSnippet", "snippetId=${snippet.id}") {
            repository.addSnippet(snippet)
        }
    }

    fun importSnippet(snippet: Snippet) {
        logAction("importSnippet", "snippetId=${snippet.id}, favorite=${snippet.favorite}")
        if (snippet.command.isBlank()) {
            logResult("importSnippet", false, "validation-failed")
            return
        }
        val normalized = snippet.copy(
            title = snippet.title.ifBlank { "Snippet" },
            group = snippet.group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = snippet.createdEpochMillis ?: System.currentTimeMillis(),
            updatedEpochMillis = snippet.updatedEpochMillis ?: snippet.createdEpochMillis ?: System.currentTimeMillis(),
            command = snippet.command.trim(),
            tags = snippet.tags.map { it.trim() }.filter { it.isNotBlank() }
        )
        launchLogged("importSnippet", "snippetId=${normalized.id}") {
            repository.addSnippet(normalized)
        }
    }

    fun updateSnippet(id: String, title: String, group: String?, description: String, command: String) {
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
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            updatedEpochMillis = System.currentTimeMillis(),
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

    fun markHostUsed(id: String) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        launchLogged("markHostUsed", "hostId=$id") {
            repository.updateHost(
                existing.copy(
                    lastUsedEpochMillis = now,
                    updatedEpochMillis = now
                )
            )
        }
    }

    fun markIdentityUsed(id: String) {
        val existing = uiState.value.identities.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        launchLogged("markIdentityUsed", "identityId=$id") {
            repository.updateIdentity(
                existing.copy(
                    lastUsedEpochMillis = now,
                    updatedEpochMillis = now
                )
            )
        }
    }

    fun markPortForwardUsed(id: String) {
        val existing = uiState.value.portForwards.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        launchLogged("markPortForwardUsed", "forwardId=$id") {
            repository.updatePortForward(
                existing.copy(
                    lastUsedEpochMillis = now,
                    updatedEpochMillis = now
                )
            )
        }
    }

    fun markSnippetUsed(id: String) {
        val existing = uiState.value.snippets.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        launchLogged("markSnippetUsed", "snippetId=$id") {
            repository.updateSnippet(
                existing.copy(
                    lastUsedEpochMillis = now,
                    updatedEpochMillis = now
                )
            )
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
        uptimeTickerJob?.cancel()
        uptimeTickerJob = null
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
        startUptimeTicker()
        logResult("onAppForegrounded", true)
    }

    fun addIdentity(
        label: String,
        fingerprint: String,
        username: String?,
        group: String?,
        suppliedId: String? = null,
        hasPrivateKey: Boolean = false
    ) {
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
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = System.currentTimeMillis(),
            updatedEpochMillis = System.currentTimeMillis(),
            lastUsedEpochMillis = null,
            favorite = false,
            hasPrivateKey = hasPrivateKey
        )
        launchLogged("addIdentity", "identityId=${identity.id}, hasPrivateKey=$hasPrivateKey") {
            repository.addIdentity(identity)
        }
    }

    fun updateIdentity(id: String, label: String, fingerprint: String, username: String?, group: String?) {
        logAction("updateIdentity", "identityId=$id, labelBlank=${label.isBlank()}, fingerprintBlank=${fingerprint.isBlank()}, hasUsername=${!username.isNullOrBlank()}")
        val existing = uiState.value.identities.find { it.id == id }
        if (existing == null) {
            logResult("updateIdentity", false, "not-found")
            return
        }
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            fingerprint = fingerprint.trim().ifBlank { existing.fingerprint },
            username = username?.takeIf { it.isNotBlank() },
            group = group?.trim()?.takeIf { it.isNotEmpty() },
            updatedEpochMillis = System.currentTimeMillis()
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
            updatedEpochMillis = System.currentTimeMillis(),
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

    fun importIdentityPublicKey(id: String, publicKey: String): Boolean {
        logAction("importIdentityPublicKey", "identityId=$id, keyLength=${publicKey.length}")
        val ok = runCatching {
            SecurityManager.importIdentityPublicKey(id, publicKey.trim())
        }.onFailure { t ->
            UiDebugLog.error("importIdentityPublicKey", t, "identityId=$id")
        }.isSuccess
        logResult("importIdentityPublicKey", ok, "identityId=$id")
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

    fun importIdentityKeyPassphrasePayload(id: String, payload: String, passphrase: String): Boolean {
        logAction(
            "importIdentityKeyPassphrasePayload",
            "identityId=$id, payloadLength=${payload.length}, passphraseLength=${passphrase.length}"
        )
        val ok = runCatching {
            SecurityManager.importIdentityKeyPassphrasePayload(id, payload, passphrase)
        }.onFailure { t ->
            UiDebugLog.error("importIdentityKeyPassphrasePayload", t, "identityId=$id")
        }.isSuccess
        logResult("importIdentityKeyPassphrasePayload", ok, "identityId=$id")
        return ok
    }

    fun importIdentity(identity: Identity) {
        logAction("importIdentity", "identityId=${identity.id}, favorite=${identity.favorite}, hasPrivateKey=${identity.hasPrivateKey}")
        if (identity.fingerprint.isBlank()) {
            logResult("importIdentity", false, "validation-failed")
            return
        }
        val normalized = identity.copy(
            label = identity.label.ifBlank { "Identity ${System.currentTimeMillis() / 1000}" },
            fingerprint = identity.fingerprint.trim(),
            username = identity.username?.takeIf { it.isNotBlank() },
            group = identity.group?.trim()?.takeIf { it.isNotEmpty() },
            createdEpochMillis = identity.createdEpochMillis,
            updatedEpochMillis = identity.updatedEpochMillis ?: identity.createdEpochMillis,
            tags = identity.tags.map { it.trim() }.filter { it.isNotBlank() }
        )
        launchLogged("importIdentity", "identityId=${normalized.id}") {
            repository.addIdentity(normalized)
        }
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

    fun importKeyboardLayout(slots: List<KeyboardSlotAction>) {
        logAction("importKeyboardLayout", "slotCount=${slots.size}")
        val normalized = KeyboardLayoutDefaults.normalizeSlots(slots)
        keyboardSlotsFlow.value = normalized
        launchLogged("importKeyboardLayout", "slotCount=${normalized.size}") {
            SettingsStore.setKeyboardLayout(normalized)
        }
    }

    fun resetKeyboardLayout() {
        logAction("resetKeyboardLayout")
        keyboardSlotsFlow.value = KeyboardLayoutDefaults.DEFAULT_SLOTS
        launchLogged("resetKeyboardLayout") {
            SettingsStore.setKeyboardLayout(KeyboardLayoutDefaults.DEFAULT_SLOTS)
        }
    }

    fun importTerminalProfiles(profiles: List<TerminalProfile>, defaultProfileId: String?) {
        logAction(
            "importTerminalProfiles",
            "profileCount=${profiles.size}, defaultProfileId=${defaultProfileId.orEmpty()}"
        )
        launchLogged("importTerminalProfiles", "profileCount=${profiles.size}") {
            val builtInIds = TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet()
            val mergedCustom = terminalProfilesFlow.value
                .filterNot { builtInIds.contains(it.id) }
                .associateByTo(linkedMapOf()) { it.id }

            profiles
                .map(::normalizeProfile)
                .filterNot { builtInIds.contains(it.id) }
                .forEach { mergedCustom[it.id] = it }

            SettingsStore.setTerminalProfiles(mergedCustom.values.sortedBy { it.name.lowercase() })

            val availableIds = builtInIds + mergedCustom.keys
            defaultProfileId
                ?.takeIf { availableIds.contains(it) }
                ?.let { SettingsStore.setDefaultTerminalProfileId(it) }
        }
    }

    private fun normalizeProfile(profile: TerminalProfile): TerminalProfile {
        val normalizedName = profile.name.trim().ifBlank { "Custom Profile" }.take(48)
        return profile.copy(
            name = normalizedName,
            fontSizeSp = profile.fontSizeSp.coerceIn(6, 28)
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

    private fun startUptimeTicker() {
        uptimeTickerJob?.cancel()
        uptimeTickerJob = viewModelScope.launch {
            uptimeMonitorRunner.runDueChecks()
            while (true) {
                delay(60_000L)
                uptimeMonitorRunner.runDueChecks()
            }
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

        fun provideFactory(
            repository: AppRepository,
            uptimeRepository: UptimeRepository,
            uptimeMonitorRunner: UptimeMonitorRunnerDelegate
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AppViewModel(repository, uptimeRepository, uptimeMonitorRunner)
                }
            }
    }
}

private fun buildHomeRecents(
    hosts: List<HostConnection>,
    identities: List<Identity>,
    forwards: List<PortForward>,
    snippets: List<Snippet>
): List<HomeRecentItem> =
    buildList {
        hosts.forEach { host ->
            mostRecentEpoch(host.lastUsedEpochMillis, host.updatedEpochMillis, host.createdEpochMillis)?.let { epoch ->
                add(
                    HomeRecentItem(
                        key = "host_${host.id}",
                        entityId = host.id,
                        type = HomeRecentType.HOST,
                        title = host.name,
                        subtitle = "${host.username}@${host.host}:${host.port}",
                        sortEpochMillis = epoch,
                        favorite = host.favorite
                    )
                )
            }
        }
        identities.forEach { identity ->
            mostRecentEpoch(identity.lastUsedEpochMillis, identity.updatedEpochMillis, identity.createdEpochMillis)?.let { epoch ->
                add(
                    HomeRecentItem(
                        key = "identity_${identity.id}",
                        entityId = identity.id,
                        type = HomeRecentType.IDENTITY,
                        title = identity.label,
                        subtitle = identity.username?.takeIf { it.isNotBlank() } ?: identity.fingerprint,
                        sortEpochMillis = epoch,
                        favorite = identity.favorite
                    )
                )
            }
        }
        forwards.forEach { forward ->
            mostRecentEpoch(forward.lastUsedEpochMillis, forward.updatedEpochMillis, forward.createdEpochMillis)?.let { epoch ->
                add(
                    HomeRecentItem(
                        key = "forward_${forward.id}",
                        entityId = forward.id,
                        type = HomeRecentType.PORT_FORWARD,
                        title = forward.label,
                        subtitle = "${forward.sourceHost}:${forward.sourcePort} -> ${forward.destinationHost}:${forward.destinationPort}",
                        sortEpochMillis = epoch,
                        favorite = forward.favorite
                    )
                )
            }
        }
        snippets.forEach { snippet ->
            mostRecentEpoch(snippet.lastUsedEpochMillis, snippet.updatedEpochMillis, snippet.createdEpochMillis)?.let { epoch ->
                add(
                    HomeRecentItem(
                        key = "snippet_${snippet.id}",
                        entityId = snippet.id,
                        type = HomeRecentType.SNIPPET,
                        title = snippet.title,
                        subtitle = snippet.command,
                        sortEpochMillis = epoch,
                        favorite = snippet.favorite
                    )
                )
            }
        }
    }.sortedWith(
        compareByDescending<HomeRecentItem> { it.sortEpochMillis }
            .thenBy { it.title.lowercase() }
    )

private fun mostRecentEpoch(vararg values: Long?): Long? = values.filterNotNull().maxOrNull()
