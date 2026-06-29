package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.theme.PeachyOrange

@Composable
fun HelpScreen(
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = remember { helpTopics() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.HELP_SCREEN),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HelpHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )
        }

        items(topics.size) { index ->
            HelpTopicCard(
                topic = topics[index],
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(UiTestTags.helpStep(index))
            )
        }

        item {
            SupportCard(
                onOpenSupport = onOpenSupport,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HelpHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text("How to use SSHPeaches", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Quick answers for the workflows people use most: connecting, keys, terminal input, file transfer, sharing, and common fixes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = topic.tint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        topic.icon,
                        contentDescription = null,
                        tint = topic.tint,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        topic.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            topic.steps.forEachIndexed { index, step ->
                HelpInstructionRow(number = index + 1, text = step)
            }

            if (topic.tips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topic.tips.forEach { tip ->
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpInstructionRow(
    number: Int,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SupportCard(
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Still stuck?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open the support site for longer troubleshooting notes, release updates, and ways to report a reproducible issue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(
                onClick = onOpenSupport,
                modifier = Modifier.testTag(UiTestTags.HELP_MORE_HELP_BUTTON)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("More help")
            }
        }
    }
}

private data class HelpTopic(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val tint: Color,
    val steps: List<String>,
    val tips: List<String> = emptyList()
)

private fun helpTopics(): List<HelpTopic> = listOf(
    HelpTopic(
        title = "Connect to a server",
        summary = "Use Quick Connect for one-off access or Hosts for saved servers.",
        icon = Icons.Default.PlayArrow,
        tint = Color(0xFF2E7D32),
        steps = listOf(
            "Open the drawer and tap Quick Connect for a temporary session, or open Hosts and tap + to save a server.",
            "Enter host name or IP address, port, username, and the authentication method your server accepts.",
            "For a saved host, use SSH for the terminal, SFTP for browsing files, or SCP for a focused copy workflow.",
            "If connection fails, open the session log and check the exact host, port, username, network reachability, and server-side SSH settings."
        )
    ),
    HelpTopic(
        title = "Set up keys and security",
        summary = "Import or generate identities, then attach them to hosts.",
        icon = Icons.Default.Security,
        tint = Color(0xFF1565C0),
        steps = listOf(
            "Open Identities and tap + to import an OpenSSH private key or create a new identity.",
            "Open a host, set Auth to Identity or Password + Identity, and choose the identity to use.",
            "When a host key prompt appears, compare the fingerprint with a trusted source before accepting it.",
            "Use Settings > Security to enable PIN or biometric unlock before storing sensitive connection data."
        ),
        tips = listOf(
            "Only clear a saved host key after you have verified the server really rotated its key."
        )
    ),
    HelpTopic(
        title = "Use the terminal",
        summary = "Control the shell, paste text, and customize the compact key row.",
        icon = Icons.Default.Keyboard,
        tint = PeachyOrange,
        steps = listOf(
            "Double-tap the terminal to show or hide the system keyboard.",
            "Use the compact key row for Esc, Tab, Ctrl, arrow/navigation keys, snippets, and custom sequences.",
            "Paste with the Android paste menu, Ctrl+Shift+V on hardware keyboards, or clipboard suggestions above supported keyboards.",
            "Open Keyboard Editor to replace any compact key with a letter, function key, modifier combo, saved sequence, snippet picker, or password injection action."
        )
    ),
    HelpTopic(
        title = "Move files and automate work",
        summary = "Use SFTP/SCP for transfer, snippets for repeated commands, and QR for local sharing.",
        icon = Icons.Default.FolderOpen,
        tint = Color(0xFFEF6C00),
        steps = listOf(
            "Tap SFTP on a host when you need to browse remote directories, create folders, rename, delete, upload, or download.",
            "Tap SCP when you already know what you want to copy and need a direct upload/download workflow.",
            "Open Snippets to save reusable commands, then run them against an active SSH session or expose them from the terminal key row.",
            "Use QR export/import from Hosts, Identities, Port Forwards, Snippets, or Settings > Transfer data to move local configuration between devices."
        ),
        tips = listOf(
            "Encrypted QR exports require the same passphrase during import. SSHPeaches does not upload these transfers to cloud storage."
        )
    ),
    HelpTopic(
        title = "Fix common problems",
        summary = "Start with the symptom, then verify the smallest thing that could be wrong.",
        icon = Icons.Default.Warning,
        tint = Color(0xFFC62828),
        steps = listOf(
            "Connection refused or timed out: verify the server is reachable from this device, the port is correct, and a firewall is not blocking SSH.",
            "Password keeps failing: confirm the username, password, server PasswordAuthentication setting, and whether the server requires a key instead.",
            "Identity login fails: re-import the key, confirm its passphrase, and check that the matching public key is in authorized_keys on the server.",
            "Transfers fail: confirm the remote path exists, your account has permission, and the local destination is writable.",
            "Background sessions stop: enable Run shells in background, allow notifications, and relax Android battery restrictions for SSHPeaches."
        ),
        tips = listOf(
            "For a useful bug report, include the action you took, Android version, connection mode, and the visible error text from the session log."
        )
    )
)
