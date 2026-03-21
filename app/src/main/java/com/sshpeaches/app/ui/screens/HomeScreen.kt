package com.majordaftapps.sshpeaches.app.ui.screens

import android.text.format.DateUtils
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.ui.components.DeleteConfirmationDialog
import com.majordaftapps.sshpeaches.app.ui.components.HostCard
import com.majordaftapps.sshpeaches.app.ui.components.generateForwardQr
import com.majordaftapps.sshpeaches.app.ui.components.generateHostQr
import com.majordaftapps.sshpeaches.app.ui.components.generateIdentityQr
import com.majordaftapps.sshpeaches.app.ui.state.FavoritesSection
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import com.majordaftapps.sshpeaches.app.ui.state.HomeRecentItem
import com.majordaftapps.sshpeaches.app.ui.state.HomeRecentType
import com.majordaftapps.sshpeaches.app.ui.state.userFacingLabel
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.ExportPassphraseCache
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.updatePasswordStateWithReveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    favorites: FavoritesSection,
    recents: List<HomeRecentItem>,
    hosts: List<HostConnection> = emptyList(),
    identities: List<Identity> = emptyList(),
    portForwards: List<PortForward> = emptyList(),
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
    onEditHost: (String) -> Unit = {},
    onDeleteHost: (String) -> Unit = {},
    onEditIdentity: (String) -> Unit = {},
    onDeleteIdentity: (String) -> Unit = {},
    onTogglePortForwardEnabled: (PortForward, Boolean) -> Unit = { _, _ -> },
    onEditPortForward: (String) -> Unit = {},
    onDeletePortForward: (String) -> Unit = {},
    onRunSnippet: (Snippet) -> Unit = {},
    onEditSnippet: (String) -> Unit = {},
    onDeleteSnippet: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onAddHost: () -> Unit = {},
    onAddIdentity: () -> Unit = {},
    onAddPortForward: () -> Unit = {},
    onAddSnippet: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val overflowFavoriteKey = remember { mutableStateOf<String?>(null) }
    val overflowRecentKey = remember { mutableStateOf<String?>(null) }
    val shareQrBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val shareQrTitle = remember { mutableStateOf("Share QR") }
    val hostSharePrompt = remember { mutableStateOf<HostConnection?>(null) }
    val hostSharePassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val hostSharePassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val hostShareConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.host.orEmpty()) }
    val hostShareConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val hostSharePassphraseError = remember { mutableStateOf<String?>(null) }
    val identitySharePrompt = remember { mutableStateOf<Identity?>(null) }
    val identitySharePassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.identity.orEmpty()) }
    val identitySharePassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val identityShareConfirmPassphraseState = rememberSaveable { mutableStateOf(ExportPassphraseCache.identity.orEmpty()) }
    val identityShareConfirmPassphraseRevealIndex = remember { mutableIntStateOf(-1) }
    val identitySharePassphraseError = remember { mutableStateOf<String?>(null) }
    val pendingDelete = remember { mutableStateOf<HomeDeleteTarget?>(null) }
    val openingSessionId = remember { mutableStateOf<String?>(null) }
    val disconnectingSessionIds = remember { mutableStateMapOf<String, Boolean>() }
    AutoHidePasswordReveal(hostSharePassphraseRevealIndex)
    AutoHidePasswordReveal(hostShareConfirmPassphraseRevealIndex)
    AutoHidePasswordReveal(identitySharePassphraseRevealIndex)
    AutoHidePasswordReveal(identityShareConfirmPassphraseRevealIndex)

    fun showQr(title: String, bitmap: Bitmap?) {
        if (bitmap == null) {
            onShowMessage("Unable to generate QR.")
            return
        }
        shareQrTitle.value = title
        shareQrBitmap.value = bitmap
    }

    LaunchedEffect(openSessions) {
        val activeSessionIds = openSessions.map { it.hostId }.toSet()
        disconnectingSessionIds.keys
            .toList()
            .filterNot(activeSessionIds::contains)
            .forEach(disconnectingSessionIds::remove)
        if (openingSessionId.value != null && openingSessionId.value !in activeSessionIds) {
            openingSessionId.value = null
        }
    }

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
                                    isOpening = openingSessionId.value == session.hostId,
                                    isDisconnecting = disconnectingSessionIds[session.hostId] == true,
                                    onOpenSession = {
                                        if (disconnectingSessionIds[session.hostId] != true) {
                                            openingSessionId.value = session.hostId
                                            onOpenSession(session.hostId)
                                            scope.launch {
                                                delay(1_200)
                                                if (openingSessionId.value == session.hostId) {
                                                    openingSessionId.value = null
                                                }
                                            }
                                        }
                                    },
                                    onDisconnectSession = {
                                        if (disconnectingSessionIds[session.hostId] != true) {
                                            disconnectingSessionIds[session.hostId] = true
                                            if (openingSessionId.value == session.hostId) {
                                                openingSessionId.value = null
                                            }
                                            onDisconnectSession(session.hostId)
                                            scope.launch {
                                                delay(2_000)
                                                disconnectingSessionIds.remove(session.hostId)
                                            }
                                        }
                                    }
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
                                onInfoCommandsChange = onInfoCommandsChange,
                                onEdit = { onEditHost(it.id) },
                                onDelete = { pendingDelete.value = HomeDeleteTarget.Host(it) }
                            )
                        }
                    }
                    if (favorites.identityFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Identities") }
                        items(favorites.identityFavorites, key = { it.id }) { identity ->
                            HomeEntityCard(
                                overflowExpanded = overflowFavoriteKey.value == "identity_${identity.id}",
                                onOverflowOpen = { overflowFavoriteKey.value = "identity_${identity.id}" },
                                onOverflowDismiss = { overflowFavoriteKey.value = null },
                                overflowButtonTag = UiTestTags.homeFavoriteOverflowButton("identity_${identity.id}"),
                                title = identity.label,
                                subtitle = identity.username?.takeIf { it.isNotBlank() } ?: identity.fingerprint,
                                icon = Icons.Default.Key,
                                onToggleFavorite = { onToggleFavorite(identity.id) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share QR") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        if (identity.hasPrivateKey) {
                                            identitySharePrompt.value = identity
                                            identitySharePassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                            identityShareConfirmPassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                            identitySharePassphraseError.value = null
                                        } else {
                                            showQr("Share ${identity.label}", generateIdentityQr(identity, null))
                                        }
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("identity_${identity.id}", "share_qr")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        onEditIdentity(identity.id)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("identity_${identity.id}", "edit")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        pendingDelete.value = HomeDeleteTarget.Identity(identity)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("identity_${identity.id}", "delete")
                                    )
                                )
                            }
                        }
                    }
                    if (favorites.portFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Port Forwards") }
                        items(favorites.portFavorites, key = { it.id }) { forward ->
                            HomeEntityCard(
                                overflowExpanded = overflowFavoriteKey.value == "forward_${forward.id}",
                                onOverflowOpen = { overflowFavoriteKey.value = "forward_${forward.id}" },
                                onOverflowDismiss = { overflowFavoriteKey.value = null },
                                overflowButtonTag = UiTestTags.homeFavoriteOverflowButton("forward_${forward.id}"),
                                title = forward.label,
                                subtitle = "${forward.sourceHost}:${forward.sourcePort} -> ${forward.destinationHost}:${forward.destinationPort}",
                                icon = Icons.Default.Bolt,
                                onToggleFavorite = { onToggleFavorite(forward.id) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (forward.enabled) "Disable" else "Enable") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        onTogglePortForwardEnabled(forward, !forward.enabled)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("forward_${forward.id}", "toggle_enabled")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Share QR") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        showQr("Share ${forward.label}", generateForwardQr(forward))
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("forward_${forward.id}", "share_qr")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        onEditPortForward(forward.id)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("forward_${forward.id}", "edit")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        pendingDelete.value = HomeDeleteTarget.PortForward(forward)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("forward_${forward.id}", "delete")
                                    )
                                )
                            }
                        }
                    }
                    if (favorites.snippetFavorites.isNotEmpty()) {
                        item { SubsectionHeader("Snippets") }
                        items(favorites.snippetFavorites, key = { it.id }) { snippet ->
                            HomeEntityCard(
                                overflowExpanded = overflowFavoriteKey.value == "snippet_${snippet.id}",
                                onOverflowOpen = { overflowFavoriteKey.value = "snippet_${snippet.id}" },
                                onOverflowDismiss = { overflowFavoriteKey.value = null },
                                overflowButtonTag = UiTestTags.homeFavoriteOverflowButton("snippet_${snippet.id}"),
                                title = snippet.title,
                                subtitle = snippet.description.ifBlank { snippet.command },
                                icon = Icons.Default.Code,
                                onToggleFavorite = { onToggleFavorite(snippet.id) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Run") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        onRunSnippet(snippet)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("snippet_${snippet.id}", "run")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        onEditSnippet(snippet.id)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("snippet_${snippet.id}", "edit")
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        overflowFavoriteKey.value = null
                                        pendingDelete.value = HomeDeleteTarget.Snippet(snippet)
                                    },
                                    modifier = Modifier.testTag(
                                        UiTestTags.homeFavoriteOverflowAction("snippet_${snippet.id}", "delete")
                                    )
                                )
                            }
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
                        val host = hosts.firstOrNull { it.id == item.entityId }
                        val identity = identities.firstOrNull { it.id == item.entityId }
                        val forward = portForwards.firstOrNull { it.id == item.entityId }
                        val snippet = snippets.firstOrNull { it.id == item.entityId }
                        RecentCard(
                            item = item,
                            overflowExpanded = overflowRecentKey.value == item.key,
                            onOverflowOpen = { overflowRecentKey.value = item.key },
                            onOverflowDismiss = { overflowRecentKey.value = null }
                        ) {
                            when (item.type) {
                                HomeRecentType.HOST -> host?.let { recentHost ->
                                    DropdownMenuItem(
                                        text = { Text("Open SSH") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onHostAction(recentHost, ConnectionMode.SSH, null)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "open_ssh")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Download Files") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onHostAction(recentHost, ConnectionMode.SCP, FileTransferEntryMode.DOWNLOAD)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "download_files")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Upload Files") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onHostAction(recentHost, ConnectionMode.SCP, FileTransferEntryMode.UPLOAD)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "upload_files")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (recentHost.favorite) "Unfavorite" else "Favorite") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onToggleFavorite(recentHost.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "favorite")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share QR") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            if (recentHost.hasPassword) {
                                                hostSharePrompt.value = recentHost
                                                hostSharePassphraseState.value = ExportPassphraseCache.host.orEmpty()
                                                hostShareConfirmPassphraseState.value = ExportPassphraseCache.host.orEmpty()
                                                hostSharePassphraseError.value = null
                                            } else {
                                                showQr("Share ${recentHost.name.ifBlank { recentHost.host }}", generateHostQr(recentHost, null))
                                            }
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "share_qr")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onEditHost(recentHost.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "edit")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            pendingDelete.value = HomeDeleteTarget.Host(recentHost)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "delete")
                                        )
                                    )
                                }
                                HomeRecentType.IDENTITY -> identity?.let { recentIdentity ->
                                    DropdownMenuItem(
                                        text = { Text(if (recentIdentity.favorite) "Unfavorite" else "Favorite") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onToggleFavorite(recentIdentity.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "favorite")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share QR") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            if (recentIdentity.hasPrivateKey) {
                                                identitySharePrompt.value = recentIdentity
                                                identitySharePassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                identityShareConfirmPassphraseState.value = ExportPassphraseCache.identity.orEmpty()
                                                identitySharePassphraseError.value = null
                                            } else {
                                                showQr("Share ${recentIdentity.label}", generateIdentityQr(recentIdentity, null))
                                            }
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "share_qr")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onEditIdentity(recentIdentity.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "edit")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            pendingDelete.value = HomeDeleteTarget.Identity(recentIdentity)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "delete")
                                        )
                                    )
                                }
                                HomeRecentType.PORT_FORWARD -> forward?.let { recentForward ->
                                    DropdownMenuItem(
                                        text = { Text(if (recentForward.enabled) "Disable" else "Enable") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onTogglePortForwardEnabled(recentForward, !recentForward.enabled)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "toggle_enabled")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (recentForward.favorite) "Unfavorite" else "Favorite") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onToggleFavorite(recentForward.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "favorite")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share QR") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            showQr("Share ${recentForward.label}", generateForwardQr(recentForward))
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "share_qr")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onEditPortForward(recentForward.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "edit")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            pendingDelete.value = HomeDeleteTarget.PortForward(recentForward)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "delete")
                                        )
                                    )
                                }
                                HomeRecentType.SNIPPET -> snippet?.let { recentSnippet ->
                                    DropdownMenuItem(
                                        text = { Text("Run") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onRunSnippet(recentSnippet)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "run")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (recentSnippet.favorite) "Unfavorite" else "Favorite") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onToggleFavorite(recentSnippet.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "favorite")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            onEditSnippet(recentSnippet.id)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "edit")
                                        )
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            overflowRecentKey.value = null
                                            pendingDelete.value = HomeDeleteTarget.Snippet(recentSnippet)
                                        },
                                        modifier = Modifier.testTag(
                                            UiTestTags.homeRecentOverflowAction(item.key, "delete")
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete.value?.let { target ->
        DeleteConfirmationDialog(
            title = target.title,
            message = target.message,
            onConfirm = {
                when (target) {
                    is HomeDeleteTarget.Host -> onDeleteHost(target.host.id)
                    is HomeDeleteTarget.Identity -> onDeleteIdentity(target.identity.id)
                    is HomeDeleteTarget.PortForward -> onDeletePortForward(target.forward.id)
                    is HomeDeleteTarget.Snippet -> onDeleteSnippet(target.snippet.id)
                }
                pendingDelete.value = null
            },
            onDismiss = { pendingDelete.value = null }
        )
    }

    hostSharePrompt.value?.let { host ->
        AlertDialog(
            onDismissRequest = {
                hostSharePrompt.value = null
                hostSharePassphraseError.value = null
            },
            title = { Text("Encrypt Host Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a passphrase to encrypt this host's password.")
                    PasswordField(
                        value = hostSharePassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(
                                hostSharePassphraseState,
                                hostSharePassphraseRevealIndex,
                                it
                            )
                            hostSharePassphraseError.value = null
                        },
                        label = "Passphrase",
                        revealIndex = hostSharePassphraseRevealIndex.intValue
                    )
                    PasswordField(
                        value = hostShareConfirmPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(
                                hostShareConfirmPassphraseState,
                                hostShareConfirmPassphraseRevealIndex,
                                it
                            )
                            hostSharePassphraseError.value = null
                        },
                        label = "Confirm passphrase",
                        revealIndex = hostShareConfirmPassphraseRevealIndex.intValue
                    )
                    hostSharePassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val passphrase = hostSharePassphraseState.value
                    when {
                        passphrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH -> {
                            hostSharePassphraseError.value =
                                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        }
                        passphrase != hostShareConfirmPassphraseState.value -> {
                            hostSharePassphraseError.value = "Passphrases do not match."
                        }
                        else -> {
                            val bitmap = generateHostQr(host, passphrase)
                            if (bitmap == null) {
                                hostSharePassphraseError.value = "Unable to export password. Unlock the app and try again."
                            } else {
                                ExportPassphraseCache.host = passphrase
                                hostSharePrompt.value = null
                                hostSharePassphraseError.value = null
                                showQr("Share ${host.name.ifBlank { host.host }}", bitmap)
                            }
                        }
                    }
                }) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = {
                    hostSharePrompt.value = null
                    hostSharePassphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }

    identitySharePrompt.value?.let { identity ->
        AlertDialog(
            onDismissRequest = {
                identitySharePrompt.value = null
                identitySharePassphraseError.value = null
            },
            title = { Text("Encrypt Identity Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a passphrase to encrypt this identity's private key.")
                    PasswordField(
                        value = identitySharePassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(
                                identitySharePassphraseState,
                                identitySharePassphraseRevealIndex,
                                it
                            )
                            identitySharePassphraseError.value = null
                        },
                        label = "Passphrase",
                        revealIndex = identitySharePassphraseRevealIndex.intValue
                    )
                    PasswordField(
                        value = identityShareConfirmPassphraseState.value,
                        onValueChange = {
                            updatePasswordStateWithReveal(
                                identityShareConfirmPassphraseState,
                                identityShareConfirmPassphraseRevealIndex,
                                it
                            )
                            identitySharePassphraseError.value = null
                        },
                        label = "Confirm passphrase",
                        revealIndex = identityShareConfirmPassphraseRevealIndex.intValue
                    )
                    identitySharePassphraseError.value?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val passphrase = identitySharePassphraseState.value
                    when {
                        passphrase.length < SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH -> {
                            identitySharePassphraseError.value =
                                "Passphrase must be at least ${SecurityManager.MIN_SECRET_PASSPHRASE_LENGTH} characters."
                        }
                        passphrase != identityShareConfirmPassphraseState.value -> {
                            identitySharePassphraseError.value = "Passphrases do not match."
                        }
                        else -> {
                            val bitmap = generateIdentityQr(identity, passphrase)
                            if (bitmap == null) {
                                identitySharePassphraseError.value = "Unable to export key. Unlock the app and try again."
                            } else {
                                ExportPassphraseCache.identity = passphrase
                                identitySharePrompt.value = null
                                identitySharePassphraseError.value = null
                                showQr("Share ${identity.label}", bitmap)
                            }
                        }
                    }
                }) { Text("Generate QR") }
            },
            dismissButton = {
                TextButton(onClick = {
                    identitySharePrompt.value = null
                    identitySharePassphraseError.value = null
                }) { Text("Cancel") }
            }
        )
    }

    shareQrBitmap.value?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { shareQrBitmap.value = null },
            title = { Text(shareQrTitle.value) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = shareQrTitle.value,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Scan this QR on another device.")
                }
            },
            confirmButton = {
                TextButton(onClick = { shareQrBitmap.value = null }) { Text("Close") }
            }
        )
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
    isOpening: Boolean,
    isDisconnecting: Boolean,
    onOpenSession: () -> Unit,
    onDisconnectSession: () -> Unit
) {
    val effectiveStatusText = when {
        isDisconnecting -> "Disconnecting..."
        isOpening -> "Opening..."
        else -> statusText
    }
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
                    text = effectiveStatusText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onOpenSession,
                    enabled = !isDisconnecting,
                    modifier = Modifier.testTag(UiTestTags.openSessionAction(session.hostId, "open"))
                ) {
                    if (isOpening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open session")
                    }
                }
                FilledTonalIconButton(
                    onClick = onDisconnectSession,
                    enabled = !isDisconnecting,
                    modifier = Modifier.testTag(UiTestTags.openSessionAction(session.hostId, "disconnect"))
                ) {
                    if (isDisconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect session")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEntityCard(
    overflowExpanded: Boolean,
    onOverflowOpen: () -> Unit,
    onOverflowDismiss: () -> Unit,
    overflowButtonTag: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onToggleFavorite: () -> Unit,
    menuContent: @Composable () -> Unit
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(Icons.Default.Star, contentDescription = "Unfavorite")
                }
                IconButton(
                    onClick = onOverflowOpen,
                    modifier = Modifier.testTag(overflowButtonTag)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = onOverflowDismiss
                ) {
                    menuContent()
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    item: HomeRecentItem,
    overflowExpanded: Boolean,
    onOverflowOpen: () -> Unit,
    onOverflowDismiss: () -> Unit,
    menuContent: @Composable () -> Unit
) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.favorite) {
                        Icon(Icons.Default.Star, contentDescription = "Favorite")
                    }
                    IconButton(
                        onClick = onOverflowOpen,
                        modifier = Modifier.testTag(UiTestTags.homeRecentOverflowButton(item.key))
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = onOverflowDismiss
                    ) {
                        menuContent()
                    }
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

private sealed interface HomeDeleteTarget {
    val title: String
    val message: String

    data class Host(val host: HostConnection) : HomeDeleteTarget {
        override val title: String = "Delete host?"
        override val message: String = "Delete ${host.name.ifBlank { host.host }}?"
    }

    data class Identity(val identity: com.majordaftapps.sshpeaches.app.data.model.Identity) : HomeDeleteTarget {
        override val title: String = "Delete identity?"
        override val message: String = "Delete ${identity.label}?"
    }

    data class PortForward(val forward: com.majordaftapps.sshpeaches.app.data.model.PortForward) : HomeDeleteTarget {
        override val title: String = "Delete port forward?"
        override val message: String = "Delete ${forward.label}?"
    }

    data class Snippet(val snippet: com.majordaftapps.sshpeaches.app.data.model.Snippet) : HomeDeleteTarget {
        override val title: String = "Delete snippet?"
        override val message: String = "Delete ${snippet.title}?"
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealIndex: Int
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = TailRevealPasswordVisualTransformation(revealIndex),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password
        )
    )
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
