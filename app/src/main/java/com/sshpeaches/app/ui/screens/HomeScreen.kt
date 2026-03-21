package com.majordaftapps.sshpeaches.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.components.HostCard
import com.majordaftapps.sshpeaches.app.ui.state.FavoritesSection
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import com.majordaftapps.sshpeaches.app.ui.state.HomeRecentItem
import com.majordaftapps.sshpeaches.app.ui.state.HomeRecentType
import com.majordaftapps.sshpeaches.app.ui.state.userFacingLabel
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun HomeScreen(
    favorites: FavoritesSection,
    recents: List<HomeRecentItem>,
    snippets: List<Snippet> = emptyList(),
    openSessions: List<SessionService.SessionSnapshot> = emptyList(),
    transferProgresses: Map<String, FileTransferProgress> = emptyMap(),
    activeSshSessionHostIds: Set<String> = emptySet(),
    hasAnyResources: Boolean,
    onOpenSession: (String) -> Unit = {},
    onDisconnectSession: (String) -> Unit = {},
    onHostAction: (HostConnection, ConnectionMode, FileTransferEntryMode?) -> Unit = { _, _, _ -> },
    onRunInfoCommand: (HostConnection, String) -> Boolean = { _, _ -> false },
    onInfoCommandsChange: (HostConnection, List<String>) -> Unit = { _, _ -> },
    onToggleFavorite: (String) -> Unit = {},
    onAddHost: () -> Unit = {},
    onAddIdentity: () -> Unit = {},
    onAddPortForward: () -> Unit = {},
    onAddSnippet: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCREEN_HOME)
    ) {
        if (!hasAnyResources) {
            HomeWelcome(
                onAddHost = onAddHost,
                onAddIdentity = onAddIdentity,
                onAddPortForward = onAddPortForward,
                onAddSnippet = onAddSnippet
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (openSessions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Open Sessions")
                            openSessions.forEach { session ->
                                OpenSessionCard(
                                    session = session,
                                    statusText = transferProgresses[session.hostId]?.statusMessage()
                                        ?: session.statusMessage
                                        ?: session.status.name,
                                    onOpenSession = onOpenSession,
                                    onDisconnectSession = onDisconnectSession
                                )
                            }
                        }
                    }
                }

                val hasFavorites = favorites.hostFavorites.isNotEmpty() ||
                    favorites.identityFavorites.isNotEmpty() ||
                    favorites.portFavorites.isNotEmpty() ||
                    favorites.snippetFavorites.isNotEmpty()
                if (hasFavorites) {
                    item { SectionHeader("Favorites") }
                    if (favorites.hostFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Hosts") }
                        items(favorites.hostFavorites, key = { it.id }) { host ->
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
                    if (favorites.identityFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Identities") }
                        items(favorites.identityFavorites, key = { it.id }) { identity ->
                            HomeEntityCard(
                                title = identity.label,
                                subtitle = identity.username?.takeIf { it.isNotBlank() } ?: identity.fingerprint,
                                icon = Icons.Default.Key,
                                onToggleFavorite = { onToggleFavorite(identity.id) }
                            )
                        }
                    }
                    if (favorites.portFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Port Forwards") }
                        items(favorites.portFavorites, key = { it.id }) { forward ->
                            HomeEntityCard(
                                title = forward.label,
                                subtitle = "${forward.sourceHost}:${forward.sourcePort} -> ${forward.destinationHost}:${forward.destinationPort}",
                                icon = Icons.Default.Bolt,
                                onToggleFavorite = { onToggleFavorite(forward.id) }
                            )
                        }
                    }
                    if (favorites.snippetFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Snippets") }
                        items(favorites.snippetFavorites, key = { it.id }) { snippet ->
                            HomeEntityCard(
                                title = snippet.title,
                                subtitle = snippet.description.ifBlank { snippet.command },
                                icon = Icons.Default.Code,
                                onToggleFavorite = { onToggleFavorite(snippet.id) }
                            )
                        }
                    }
                }

                if (recents.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recents",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag(UiTestTags.HOME_RECENTS_SECTION)
                        )
                    }
                    items(recents, key = { it.key }) { item ->
                        RecentCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWelcome(
    onAddHost: () -> Unit,
    onAddIdentity: () -> Unit,
    onAddPortForward: () -> Unit,
    onAddSnippet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(UiTestTags.HOME_WELCOME),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.sshpeaches),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 20.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "Welcome to SSHPeaches. To begin, create your first resource.",
            style = MaterialTheme.typography.titleMedium
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAddHost,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.HOME_WELCOME_ADD_HOST)
            ) {
                Text("Add Host")
            }
            Button(
                onClick = onAddIdentity,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.HOME_WELCOME_ADD_IDENTITY)
            ) {
                Text("Add Identity")
            }
            Button(
                onClick = onAddPortForward,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.HOME_WELCOME_ADD_FORWARD)
            ) {
                Text("Add Port Forward")
            }
            Button(
                onClick = onAddSnippet,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.HOME_WELCOME_ADD_SNIPPET)
            ) {
                Text("Add Snippet")
            }
        }
    }
}

@Composable
private fun OpenSessionCard(
    session: SessionService.SessionSnapshot,
    statusText: String,
    onOpenSession: (String) -> Unit,
    onDisconnectSession: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${session.host.name.ifBlank { session.host.host }} - ${session.mode.userFacingLabel()}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onOpenSession(session.hostId) },
                    modifier = Modifier.testTag(UiTestTags.openSessionAction(session.hostId, "open"))
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open session")
                }
                IconButton(
                    onClick = { onDisconnectSession(session.hostId) },
                    modifier = Modifier.testTag(UiTestTags.openSessionAction(session.hostId, "disconnect"))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Disconnect session")
                }
            }
        }
    }
}

@Composable
private fun HomeEntityCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(Icons.Default.Star, contentDescription = "Unfavorite")
            }
        }
    }
}

@Composable
private fun RecentCard(item: HomeRecentItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.homeRecentItem(item.key)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.type.icon(), contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (item.favorite) {
                    Icon(Icons.Default.Star, contentDescription = "Favorite")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.type.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        item.sortEpochMillis,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(text = label, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SubsectionHeader(label: String) {
    Text(text = label, style = MaterialTheme.typography.titleMedium)
}

private fun HomeRecentType.icon(): ImageVector = when (this) {
    HomeRecentType.HOST -> Icons.Default.Storage
    HomeRecentType.IDENTITY -> Icons.Default.Key
    HomeRecentType.PORT_FORWARD -> Icons.Default.Bolt
    HomeRecentType.SNIPPET -> Icons.Default.Code
}

private fun HomeRecentType.label(): String = when (this) {
    HomeRecentType.HOST -> "Host"
    HomeRecentType.IDENTITY -> "Identity"
    HomeRecentType.PORT_FORWARD -> "Port Forward"
    HomeRecentType.SNIPPET -> "Snippet"
}
