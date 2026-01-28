package com.sshpeaches.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.repository.AppRepository
import com.sshpeaches.app.data.repository.InMemoryAppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

    val uiState: StateFlow<AppUiState> = combine(
        baseUiState,
        themeModeFlow,
        backgroundSessionsFlow,
        biometricFlow,
        lockTimeoutFlow,
        crashReportsFlow,
        analyticsFlow,
        diagnosticsLoggingFlow,
        includeIdentitiesFlow,
        includeSettingsFlow,
        autoStartForwardsFlow,
        hostKeyPromptFlow,
        usageReportsFlow
    ) { state, theme, background, biometric, timeout, crash, analytics, diagnostics, includeIds, includeSettings, autoStart, hostKeyPrompt, usage ->
        state.copy(
            themeMode = theme,
            allowBackgroundSessions = background,
            biometricLockEnabled = biometric,
            lockTimeout = timeout,
            crashReportsEnabled = crash,
            analyticsEnabled = analytics,
            diagnosticsLoggingEnabled = diagnostics,
            includeIdentitiesInQr = includeIds,
            includeSettingsInQr = includeSettings,
            autoStartForwards = autoStart,
            hostKeyPromptEnabled = hostKeyPrompt,
            usageReportsEnabled = usage
        )
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
        backgroundSessionsFlow.value = enabled
    }

    fun setBiometricLock(enabled: Boolean) {
        biometricFlow.value = enabled
    }

    fun setLockTimeout(timeout: LockTimeout) {
        lockTimeoutFlow.value = timeout
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

    companion object {
        private val byLastUsed = compareByDescending<HostConnection> { it.lastUsedEpochMillis ?: 0L }
        private val byName = compareBy<HostConnection> { it.name.lowercase() }
    }
}
