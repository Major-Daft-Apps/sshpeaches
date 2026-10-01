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
            "The basics, in a few steps each.",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Text(
                    topic.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            topic.steps.forEachIndexed { index, step ->
                HelpInstructionRow(number = index + 1, text = step)
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
            Text("Need more help?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open the support site.",
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
    val icon: ImageVector,
    val tint: Color,
    val steps: List<String>
)

private fun helpTopics(): List<HelpTopic> = listOf(
    HelpTopic(
        title = "Connect",
        icon = Icons.Default.PlayArrow,
        tint = Color(0xFF2E7D32),
        steps = listOf(
            "Menu → Quick Connect, or Hosts → + to save a server.",
            "Enter host, port, username, and a password or key.",
            "Tap connect. Use SSH for a terminal, SFTP or SCP for files."
        )
    ),
    HelpTopic(
        title = "Keys",
        icon = Icons.Default.Security,
        tint = Color(0xFF1565C0),
        steps = listOf(
            "Identities → + to import or create a key.",
            "On the host, set Auth to Identity and pick that key.",
            "Check the fingerprint before you accept a new host key."
        )
    ),
    HelpTopic(
        title = "Terminal",
        icon = Icons.Default.Keyboard,
        tint = PeachyOrange,
        steps = listOf(
            "Tap the terminal to type.",
            "Use the extra keys for Esc, Ctrl, Tab, and arrows.",
            "Keyboard Editor changes those keys."
        )
    ),
    HelpTopic(
        title = "Files",
        icon = Icons.Default.FolderOpen,
        tint = Color(0xFFEF6C00),
        steps = listOf(
            "Open a host with SFTP to browse, upload, or download.",
            "Use SCP when you already know the path to copy.",
            "Snippets save commands you run often."
        )
    ),
    HelpTopic(
        title = "If it fails",
        icon = Icons.Default.Warning,
        tint = Color(0xFFC62828),
        steps = listOf(
            "Can't connect: check host, port, and network.",
            "Login fails: check username, password, or key.",
            "Files fail: check the path and permissions."
        )
    )
)
