package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.components.EmptyState
import com.sshpeaches.app.ui.state.FavoritesSection

@Composable
fun FavoritesScreen(section: FavoritesSection) {
    val hasFavorites = section.hostFavorites.isNotEmpty() ||
        section.identityFavorites.isNotEmpty() ||
        section.portFavorites.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasFavorites) {
            item { EmptyState(itemLabel = "favorite") }
            return@LazyColumn
        }
        if (section.hostFavorites.isNotEmpty()) {
            item { SectionHeader("Hosts") }
            items(section.hostFavorites, key = { it.id }) { host ->
                HostCard(host = host)
            }
        }
        if (section.identityFavorites.isNotEmpty()) {
            item { SectionHeader("Identities") }
            items(section.identityFavorites, key = { it.id }) { identity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(identity.label, style = MaterialTheme.typography.titleMedium)
                        identity.username?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(identity.fingerprint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (section.portFavorites.isNotEmpty()) {
            item { SectionHeader("Port Forwards") }
            items(section.portFavorites, key = { it.id }) { forward ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(forward.label, style = MaterialTheme.typography.titleMedium)
                        Text("${forward.type}", style = MaterialTheme.typography.bodySmall)
                        Text("${forward.sourceHost}:${forward.sourcePort} → ${forward.destinationHost}:${forward.destinationPort}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium)
}
