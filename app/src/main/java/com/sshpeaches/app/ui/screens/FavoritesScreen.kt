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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.components.EmptyState
import com.majordaftapps.sshpeaches.app.ui.components.HostCard
import com.majordaftapps.sshpeaches.app.ui.state.FavoritesSection
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun FavoritesScreen(
    section: FavoritesSection,
    snippets: List<Snippet> = emptyList(),
    openSessions: List<SessionService.SessionSnapshot> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    onDisconnectSession: (String) -> Unit = {},
    activeSshSessionHostIds: Set<String> = emptySet(),
    onHostAction: (HostConnection, ConnectionMode) -> Unit = { _, _ -> },
    onRunInfoCommand: (HostConnection, String) -> Boolean = { _, _ -> false },
    onInfoCommandsChange: (HostConnection, List<String>) -> Unit = { _, _ -> },
    onToggleFavorite: (String) -> Unit = {},
    onEmptyStateVisibleChanged: (Boolean) -> Unit = {}
) {
    val hasContent = openSessions.isNotEmpty() ||
        section.hostFavorites.isNotEmpty() ||
        section.identityFavorites.isNotEmpty() ||
        section.portFavorites.isNotEmpty() ||
        section.snippetFavorites.isNotEmpty()
    LaunchedEffect(hasContent) {
        onEmptyStateVisibleChanged(!hasContent)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_FAVORITES),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasContent) {
            item { EmptyState(itemLabel = "favorite", showCreateHint = false) }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Open Sessions",
                    style = MaterialTheme.typography.titleMedium
                )
                if (openSessions.isEmpty()) {
                    Text(
                        text = "No open sessions.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    openSessions.forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${session.host.name.ifBlank { session.host.host }} • ${session.mode.name}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = session.statusMessage ?: session.status.name,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { onOpenSession(session.hostId) },
                                    modifier = Modifier.testTag(
                                        UiTestTags.openSessionAction(session.hostId, "open")
                                    )
                                ) {
                                    Text("Open")
                                }
                                TextButton(
                                    onClick = { onDisconnectSession(session.hostId) },
                                    modifier = Modifier.testTag(
                                        UiTestTags.openSessionAction(session.hostId, "disconnect")
                                    )
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (section.hostFavorites.isNotEmpty()) {
            item { SectionHeader("Hosts") }
            items(section.hostFavorites, key = { it.id }) { host ->
                HostCard(
                    host = host,
                    snippets = snippets,
                    onToggleFavorite = onToggleFavorite,
                    onAction = onHostAction,
                    canRunInfoCommands = activeSshSessionHostIds.contains(host.id),
                    onRunInfoCommand = onRunInfoCommand,
                    onInfoCommandsChange = onInfoCommandsChange
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
        if (section.snippetFavorites.isNotEmpty()) {
            item { SectionHeader("Snippets") }
            items(section.snippetFavorites, key = { it.id }) { snippet ->
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                            if (snippet.description.isNotBlank()) {
                                Text(snippet.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(snippet.command, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onToggleFavorite(snippet.id) }) {
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
