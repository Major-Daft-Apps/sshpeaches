package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.HostCard
import com.majordaftapps.sshpeaches.app.ui.state.FavoritesSection

@Composable
fun FavoritesScreen(
    section: FavoritesSection,
    onToggleFavorite: (String) -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val hasFavorites = section.hostFavorites.isNotEmpty() ||
        section.identityFavorites.isNotEmpty() ||
        section.portFavorites.isNotEmpty()
    LaunchedEffect(hasFavorites) {
        onEmptyStateVisibleChanged(!hasFavorites)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasFavorites) {
            item { EmptyState(itemLabel = "favorite", showCreateHint = false) }
            return@LazyColumn
        }
        if (section.hostFavorites.isNotEmpty()) {
            item { SectionHeader("Hosts") }
            items(section.hostFavorites, key = { it.id }) { host ->
                HostCard(
                    host = host,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
        if (section.identityFavorites.isNotEmpty()) {
            item { SectionHeader("Identities") }
            items(section.identityFavorites, key = { it.id }) { identity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(identity.label, style = MaterialTheme.typography.titleMedium)
                            identity.username?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(identity.fingerprint, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onToggleFavorite(identity.id) }) {
                            Icon(Icons.Default.Star, contentDescription = "Unfavorite")
                        }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(forward.label, style = MaterialTheme.typography.titleMedium)
                            Text("${forward.type}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${forward.sourceHost}:${forward.sourcePort} -> ${forward.destinationHost}:${forward.destinationPort}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(forward.id) }) {
                            Icon(Icons.Default.Star, contentDescription = "Unfavorite")
                        }
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
