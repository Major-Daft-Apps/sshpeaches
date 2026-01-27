package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.state.FavoritesSection

@Composable
fun FavoritesScreen(section: FavoritesSection) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (section.hostFavorites.isNotEmpty()) {
            item { SectionHeader("Hosts") }
            items(section.hostFavorites, key = { it.id }) { host ->
                HostCard(host = host)
            }
        }
        if (section.identityFavorites.isNotEmpty()) {
            item { SectionHeader("Identities") }
            items(section.identityFavorites, key = { it.id }) { identity ->
                Column { Text(identity.label, style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (section.portFavorites.isNotEmpty()) {
            item { SectionHeader("Port Forwards") }
            items(section.portFavorites, key = { it.id }) { forward ->
                Column { Text("${forward.label} • ${forward.type}") }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium)
}
