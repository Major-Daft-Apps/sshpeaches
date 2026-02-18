package com.sshpeaches.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.repository.AppRepository
import com.sshpeaches.app.data.repository.InMemoryAppRepository
import com.sshpeaches.app.data.settings.SettingsStore
import com.sshpeaches.app.security.SecurityManager
import com.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val crashReportsFlow = MutableStateFlow(false)
    private val analyticsFlow = MutableStateFlow(false)
    private val diagnosticsLoggingFlow = MutableStateFlow(false)
    private val includeIdentitiesFlow = MutableStateFlow(true)
    private val includeSettingsFlow = MutableStateFlow(true)
    private val autoStartForwardsFlow = MutableStateFlow(true)
    private val hostKeyPromptFlow = MutableStateFlow(true)
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
            SettingsStore.biometricLockEnabled.collect { enabled ->
                biometricFlow.value = enabled && SecurityManager.isPinSet()
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
        val crash: Boolean,
        val analytics: Boolean,
        val diagnostics: Boolean
    )

    private data class PrivacyPartial(
        val theme: ThemeMode,
        val background: Boolean,
        val biometric: Boolean,
        val timeout: LockTimeout
    )

    private data class SharePrefs(
        val includeIds: Boolean,
        val includeSettings: Boolean,
        val autoStart: Boolean,
        val hostKeyPrompt: Boolean,
        val usage: Boolean
    )

    private val privacyPartialFlow = combine(
        themeModeFlow,
        backgroundSessionsFlow,
        biometricFlow,
        lockTimeoutFlow
    ) { theme, background, biometric, timeout ->
        PrivacyPartial(theme, background, biometric, timeout)
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
            crash = crash,
            analytics = analytics,
            diagnostics = diagnostics
        )
    }

    private val sharePrefsFlow = combine(
        includeIdentitiesFlow,
        includeSettingsFlow,
        autoStartForwardsFlow,
        hostKeyPromptFlow,
        usageReportsFlow
    ) { includeIds, includeSettings, autoStart, hostKeyPrompt, usage ->
        SharePrefs(includeIds, includeSettings, autoStart, hostKeyPrompt, usage)
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
            crashReportsEnabled = privacy.crash,
            analyticsEnabled = privacy.analytics,
            diagnosticsLoggingEnabled = privacy.diagnostics,
            includeIdentitiesInQr = share.includeIds,
            includeSettingsInQr = share.includeSettings,
            autoStartForwards = share.autoStart,
            hostKeyPromptEnabled = share.hostKeyPrompt,
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

    fun setSortMode(mode: SortMode) {
        sortMode.value = mode
    }

    fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }

    fun setBackgroundSessions(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setAllowBackgroundSessions(enabled)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        if (enabled && !SecurityManager.isPinSet()) return
        viewModelScope.launch {
            SettingsStore.setBiometricLockEnabled(enabled)
        }
    }

    fun setLockTimeout(timeout: LockTimeout) {
        lockTimeoutFlow.value = timeout
        if (!lockedFlow.value) {
            scheduleLockTimer(timeout)
        }
    }

    fun setCrashReports(enabled: Boolean) {
        crashReportsFlow.value = enabled
    }

    fun setAnalytics(enabled: Boolean) {
        analyticsFlow.value = enabled
    }

    fun setDiagnosticsLogging(enabled: Boolean) {
        diagnosticsLoggingFlow.value = enabled
    }

    fun setIncludeIdentities(enabled: Boolean) {
        includeIdentitiesFlow.value = enabled
    }

    fun setIncludeSettings(enabled: Boolean) {
        includeSettingsFlow.value = enabled
    }

    fun setAutoStartForwards(enabled: Boolean) {
        autoStartForwardsFlow.value = enabled
    }

    fun setHostKeyPrompt(enabled: Boolean) {
        hostKeyPromptFlow.value = enabled
    }

    fun setUsageReports(enabled: Boolean) {
        usageReportsFlow.value = enabled
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
        password: String?,
        suppliedId: String? = null
    ) {
        if (name.isBlank() || host.isBlank() || username.isBlank()) return
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
            hasPassword = hasPassword
        )
        viewModelScope.launch {
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
        password: String?
    ) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
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
            hasPassword = hasPassword
        )
        viewModelScope.launch {
            repository.updateHost(updated)
        }
    }

    fun deleteHost(id: String) {
        val existing = uiState.value.hosts.find { it.id == id } ?: return
        viewModelScope.launch {
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
        if (label.isBlank()) return
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
        viewModelScope.launch {
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
        val existing = uiState.value.portForwards.find { it.id == id } ?: return
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
        viewModelScope.launch {
            repository.updatePortForward(updated)
        }
    }

    fun deletePortForward(id: String) {
        val existing = uiState.value.portForwards.find { it.id == id } ?: return
        viewModelScope.launch {
            repository.deletePortForward(existing)
        }
    }

    fun setPin(pin: String) {
        SecurityManager.setPin(pin)
        pinConfiguredFlow.value = true
        lockedFlow.value = SecurityManager.isLocked()
        lockTimerJob?.cancel()
        lockTimerJob = null
    }

    fun lockApp() {
        SecurityManager.lock()
        lockedFlow.value = SecurityManager.isLocked()
        lockTimerJob?.cancel()
        lockTimerJob = null
    }

    fun unlockWithPin(pin: String): Boolean {
        val ok = SecurityManager.verifyPin(pin)
        if (ok) {
            lockedFlow.value = SecurityManager.isLocked()
            if (!lockedFlow.value) {
                scheduleLockTimer(lockTimeoutFlow.value)
            }
        }
        return ok
    }

    fun unlockWithBiometric() {
        if (!SecurityManager.isPinSet()) return
        SecurityManager.unlock()
        lockedFlow.value = SecurityManager.isLocked()
        if (!lockedFlow.value) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
    }

    fun onUserInteraction() {
        if (!lockedFlow.value) {
            scheduleLockTimer(lockTimeoutFlow.value)
        }
    }

    fun addIdentity(label: String, fingerprint: String, username: String?, suppliedId: String? = null, hasPrivateKey: Boolean = false) {
        if (fingerprint.isBlank()) return
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
        viewModelScope.launch {
            repository.addIdentity(identity)
        }
    }

    fun updateIdentity(id: String, label: String, fingerprint: String, username: String?) {
        val existing = uiState.value.identities.find { it.id == id } ?: return
        val updated = existing.copy(
            label = label.ifBlank { existing.label },
            fingerprint = fingerprint.trim().ifBlank { existing.fingerprint },
            username = username?.takeIf { it.isNotBlank() }
        )
        viewModelScope.launch {
            repository.updateIdentity(updated)
        }
    }

    fun deleteIdentity(id: String) {
        val existing = uiState.value.identities.find { it.id == id } ?: return
        viewModelScope.launch {
            if (existing.hasPrivateKey) {
                SecurityManager.clearIdentityKey(id)
            }
            repository.deleteIdentity(existing)
        }
    }

    fun markIdentityHasKey(id: String, hasKey: Boolean) {
        val current = uiState.value.identities.find { it.id == id } ?: return
        if (current.hasPrivateKey == hasKey) return
        val updated = current.copy(hasPrivateKey = hasKey)
        viewModelScope.launch {
            repository.updateIdentity(updated)
        }
    }

    fun importIdentityKeyFromPayload(id: String, payload: String, passphrase: String): Boolean {
        return runCatching {
            SecurityManager.importIdentityKeyPayload(id, payload, passphrase)
            markIdentityHasKey(id, true)
        }.isSuccess
    }

    fun importIdentityKeyPlain(id: String, key: String): Boolean {
        return runCatching {
            SecurityManager.storeIdentityKey(id, key)
            markIdentityHasKey(id, true)
        }.isSuccess
    }

    fun updateKeyboardSlot(index: Int, value: String) {
        if (index !in 0 until KeyboardLayoutDefaults.SLOT_COUNT) return
        val updated = keyboardSlotsFlow.value.toMutableList().also { list ->
            list[index] = value
        }.toList()
        keyboardSlotsFlow.value = updated
        viewModelScope.launch {
            SettingsStore.setKeyboardLayout(updated)
        }
    }

    fun resetKeyboardLayout() {
        keyboardSlotsFlow.value = KeyboardLayoutDefaults.DEFAULT_SLOTS
        viewModelScope.launch {
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
        LockTimeout.CUSTOM -> null // TODO expose UI for user-defined timeout
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
