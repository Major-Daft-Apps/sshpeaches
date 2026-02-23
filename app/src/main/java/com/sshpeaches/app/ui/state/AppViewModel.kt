package com.sshpeaches.app.ui.state

import com.sshpeaches.app.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.BackgroundBehavior
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.model.Snippet
import com.sshpeaches.app.data.repository.AppRepository
import com.sshpeaches.app.data.repository.InMemoryAppRepository
import com.sshpeaches.app.data.settings.SettingsStore
import com.sshpeaches.app.security.SecurityManager
import com.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.sshpeaches.app.ui.logging.UiDebugLog
import kotlinx.coroutines.Job
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
    private val biometricFlow = MutableStateFlow(false)
    private val lockTimeoutFlow = MutableStateFlow(LockTimeout.FIVE_MIN)
    private val customLockTimeoutMinutesFlow = MutableStateFlow(30)
    private val crashReportsFlow = MutableStateFlow(false)
    private val analyticsFlow = MutableStateFlow(false)
    private val diagnosticsLoggingFlow = MutableStateFlow(false)
    private val includeIdentitiesFlow = MutableStateFlow(true)
    private val includeSettingsFlow = MutableStateFlow(true)
    private val autoStartForwardsFlow = MutableStateFlow(true)
    private val hostKeyPromptFlow = MutableStateFlow(true)
    private val autoTrustHostKeyFlow = MutableStateFlow(true)
    private val usageReportsFlow = MutableStateFlow(false)
    private val pinConfiguredFlow = MutableStateFlow(SecurityManager.isPinSet())
    private val lockedFlow = MutableStateFlow(SecurityManager.isLocked())
    private val keyboardSlotsFlow = MutableStateFlow(KeyboardLayoutDefaults.DEFAULT_SLOTS)
    private var lockTimerJob: Job? = null

    init {
        viewModelScope.launch {
            SecurityManager.lockState().collect { locked ->
                lockedFlow.value = locked
                if (locked) {
                    lockTimerJob?.cancel()
                    lockTimerJob = null
                } else {
                    scheduleLockTimer(lockTimeoutFlow.value)
                }
            }
        }
        viewModelScope.launch {
            SettingsStore.allowBackgroundSessions.collect { enabled ->
                backgroundSessionsFlow.value = enabled
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
        val biometric: Boolean,
        val timeout: LockTimeout,
        val customTimeoutMinutes: Int,
        val crash: Boolean,
        val analytics: Boolean,
        val diagnostics: Boolean
    )

    private data class PrivacyPartial(
        val theme: ThemeMode,
        val background: Boolean,
        val biometric: Boolean,
        val timeout: LockTimeout,
        val customTimeoutMinutes: Int
    )

    private data class SharePrefs(
        val includeIds: Boolean,
        val includeSettings: Boolean,
        val autoStart: Boolean,
        val hostKeyPrompt: Boolean,
        val autoTrustHostKey: Boolean,
        val usage: Boolean
    )

    private val privacyPartialFlow = combine(
        themeModeFlow,
        backgroundSessionsFlow,
        biometricFlow,
        lockTimeoutFlow,
        customLockTimeoutMinutesFlow
    ) { theme, background, biometric, timeout, customMinutes ->
        PrivacyPartial(theme, background, biometric, timeout, customMinutes)
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
            biometric = partial.biometric,
            timeout = partial.timeout,
            customTimeoutMinutes = partial.customTimeoutMinutes,
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

    private val coreUiState = combine(
        baseUiState,
        privacyPrefsFlow,
        sharePrefsFlow,
        pinConfiguredFlow,
        lockedFlow
    ) { state, privacy, share, pinSet, locked ->
        state.copy(
            themeMode = privacy.theme,
            allowBackgroundSessions = privacy.background,
            biometricLockEnabled = privacy.biometric,
            lockTimeout = privacy.timeout,
            customLockTimeoutMinutes = privacy.customTimeoutMinutes,
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
            viewModelScope.launch {
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
                logResult(action, true)
            } catch (t: Throwable) {
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
        append(state.biometricLockEnabled).append('|')
        append(state.lockTimeout).append('|')
        append(state.customLockTimeoutMinutes).append('|')
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
        append(state.keyboardSlots.joinToString(separator = ","))
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
            if (!lockedFlow.value) {
                scheduleLockTimer(timeout)
            }
        }
    }

    fun setCustomLockTimeoutMinutes(minutes: Int) {
        launchLogged("setCustomLockTimeoutMinutes", "minutes=$minutes") {
            SettingsStore.setCustomLockTimeoutMinutes(minutes)
            if (!lockedFlow.value && lockTimeoutFlow.value == LockTimeout.CUSTOM) {
                scheduleLockTimer(LockTimeout.CUSTOM)
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
        preferredForwardId: String?,
        startupScript: String,
        backgroundBehavior: BackgroundBehavior,
        password: String?,
        suppliedId: String? = null
    ) {
        logAction(
            "addHost",
            "nameBlank=${name.isBlank()}, hostBlank=${host.isBlank()}, usernameBlank=${username.isBlank()}, port=$port, auth=$auth, mode=$defaultMode, useMosh=$useMosh, hasForward=${!preferredForwardId.isNullOrBlank()}, hasScript=${startupScript.isNotBlank()}, hasPasswordInput=${!password.isNullOrBlank()}"
        )
        if (name.isBlank() || host.isBlank() || username.isBlank()) {
            logResult("addHost", false, "validation-failed")
            return
        }
        val id = suppliedId ?: UUID.randomUUID().toString()
        val canStoreSecret = SecurityManager.isPinSet() && !SecurityManager.isLocked()
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
            preferredForwardId = preferredForwardId,
            startupScript = startupScript,
            backgroundBehavior = backgroundBehavior
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
        preferredForwardId: String?,
        startupScript: String,
        backgroundBehavior: BackgroundBehavior,
        password: String?
    ) {
        logAction(
            "updateHost",
            "hostId=$id, port=$port, auth=$auth, mode=$defaultMode, useMosh=$useMosh, hasForward=${!preferredForwardId.isNullOrBlank()}, hasScript=${startupScript.isNotBlank()}, passwordProvided=${password != null}"
        )
        val existing = uiState.value.hosts.find { it.id == id }
        if (existing == null) {
            logResult("updateHost", false, "not-found")
            return
        }
        val canStoreSecret = SecurityManager.isPinSet() && !SecurityManager.isLocked()
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
            preferredForwardId = preferredForwardId,
            startupScript = startupScript,
            backgroundBehavior = backgroundBehavior
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
        val forward = PortForward(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            type = type,
            sourceHost = sourceHost.ifBlank { "127.0.0.1" },
            sourcePort = sourcePort,
            destinationHost = destHost,
            destinationPort = destPort,
            associatedHosts = associatedHosts,
            favorite = false,
            enabled = enabled
        )
        launchLogged("addPortForward", "forwardId=${forward.id}, type=$type") {
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
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            type = type,
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
            if (!lockedFlow.value) {
                scheduleLockTimer(lockTimeoutFlow.value)
            }
        }
        logResult("unlockWithPin", ok, "locked=${lockedFlow.value}")
        return ok
    }

    fun unlockWithBiometric() {
        logAction("unlockWithBiometric")
        if (!SecurityManager.isPinSet()) {
            logResult("unlockWithBiometric", false, "pin-not-configured")
            return
        }
        SecurityManager.unlock()
        lockedFlow.value = SecurityManager.isLocked()
        if (!lockedFlow.value) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
        logResult("unlockWithBiometric", true, "locked=${lockedFlow.value}")
    }

    fun onUserInteraction() {
        logAction("onUserInteraction", "locked=${lockedFlow.value}")
        if (!lockedFlow.value) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
        logResult("onUserInteraction", true)
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
            if (existing.hasPrivateKey) {
                SecurityManager.clearIdentityKey(id)
            }
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
            markIdentityHasKey(id, true)
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
            markIdentityHasKey(id, true)
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

    fun updateKeyboardSlot(index: Int, value: String) {
        logAction("updateKeyboardSlot", "index=$index, valueBlank=${value.isBlank()}")
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
