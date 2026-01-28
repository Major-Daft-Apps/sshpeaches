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
    private val darkThemeEnabled = MutableStateFlow(true)

    val uiState: StateFlow<AppUiState> = combine(
        repository.hosts,
        repository.identities,
        repository.portForwards,
        repository.snippets,
        sortMode,
        darkThemeEnabled
    ) { hosts, identities, forwards, snippets, mode, dark ->
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
            sortMode = mode,
            useDarkTheme = dark
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState()
    )

    fun setSortMode(mode: SortMode) {
        sortMode.value = mode
    }

    fun setDarkTheme(enabled: Boolean) {
        darkThemeEnabled.value = enabled
    }

    companion object {
        private val byLastUsed = compareByDescending<HostConnection> { it.lastUsedEpochMillis ?: 0L }
        private val byName = compareBy<HostConnection> { it.name.lowercase() }
    }
}
