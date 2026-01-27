package com.sshpeaches.app.ui.state

import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet

data class AppUiState(
    val favorites: FavoritesSection = FavoritesSection(),
    val hosts: List<HostConnection> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val portForwards: List<PortForward> = emptyList(),
    val snippets: List<Snippet> = emptyList()
)

data class FavoritesSection(
    val hostFavorites: List<HostConnection> = emptyList(),
    val identityFavorites: List<Identity> = emptyList(),
    val portFavorites: List<PortForward> = emptyList()
)
