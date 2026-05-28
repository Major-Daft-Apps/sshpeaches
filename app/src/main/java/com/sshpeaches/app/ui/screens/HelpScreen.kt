package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import kotlinx.coroutines.delay

@Composable
fun HelpScreen(
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember { helpSteps() }
    val selectedStep = rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(steps.size) {
        while (steps.isNotEmpty()) {
            delay(4_500)
            selectedStep.intValue = (selectedStep.intValue + 1) % steps.size
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.HELP_SCREEN),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
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
                    Text("Guided help", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    "Walk through the core SSHPeaches workflows without leaving the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AnimatedGuidePreview(
                step = steps[selectedStep.intValue],
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        items(steps.size) { index ->
            HelpStepRow(
                step = steps[index],
                selected = selectedStep.intValue == index,
                onClick = { selectedStep.intValue = index },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(UiTestTags.helpStep(index))
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Need more help?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Open the support site for docs, troubleshooting notes, and release updates.",
                        style = MaterialTheme.typography.bodyMedium
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
    }
}

@Composable
private fun AnimatedGuidePreview(
    step: HelpStep,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "help-preview")
    val progress = transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "help-progress"
    )
    val pulse = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "help-pulse"
    )
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 230.dp),
        color = colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        step.icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(step.label, style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "help-step-content"
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MockTopBar(title = current.mockTitle)
                    MockScreenBody(step = current, pulse = pulse.value)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colorScheme.outline.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun MockTopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun MockScreenBody(step: HelpStep, pulse: Float) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (index == step.highlightIndex) {
                        step.tint.copy(alpha = 0.22f + 0.08f * pulse)
                    } else {
                        colorScheme.surface
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (index == step.highlightIndex) step.tint else colorScheme.outline)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (index == 0) 0.82f else 0.64f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colorScheme.onSurfaceVariant.copy(alpha = 0.34f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (index == 2) 0.72f else 0.48f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .width(92.dp)
                .height(136.dp),
            color = Color.Black,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 4) pulse else 0.75f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(step.tint)
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpStepRow(
    step: HelpStep,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) colors.primaryContainer else colors.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(step.icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(step.title, style = MaterialTheme.typography.titleSmall)
                Text(step.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) {
                Text(if (selected) "Viewing" else "View")
            }
        }
    }
}

private data class HelpStep(
    val title: String,
    val label: String,
    val description: String,
    val mockTitle: String,
    val icon: ImageVector,
    val tint: Color,
    val highlightIndex: Int
)

private fun helpSteps(): List<HelpStep> = listOf(
    HelpStep(
        title = "Start a session",
        label = "Hosts",
        description = "Pick a saved host, confirm credentials, and keep SSH, SFTP, or SCP in the same shell.",
        mockTitle = "Hosts",
        icon = Icons.Default.PlayArrow,
        tint = Color(0xFF2E7D32),
        highlightIndex = 0
    ),
    HelpStep(
        title = "Move files",
        label = "SFTP and SCP",
        description = "Use the file tools beside an active session to browse, upload, download, rename, and delete.",
        mockTitle = "File transfer",
        icon = Icons.Default.Build,
        tint = Color(0xFF1565C0),
        highlightIndex = 1
    ),
    HelpStep(
        title = "Track uptime",
        label = "Monitoring",
        description = "Add monitors for saved hosts and review 24-hour and 7-day status history from Uptime.",
        mockTitle = "Uptime",
        icon = Icons.Default.Timeline,
        tint = Color(0xFF6A1B9A),
        highlightIndex = 2
    ),
    HelpStep(
        title = "Tune the app",
        label = "Settings",
        description = "Adjust keyboard rows, terminal themes, lock behavior, background sessions, and app icon.",
        mockTitle = "Settings",
        icon = Icons.Default.Settings,
        tint = Color(0xFFEF6C00),
        highlightIndex = 1
    )
)
