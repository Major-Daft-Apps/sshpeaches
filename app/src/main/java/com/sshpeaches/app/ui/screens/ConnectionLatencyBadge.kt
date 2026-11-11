package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.service.ConnectionLatency
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

/** What the latency badge reads: "42 ms", or nothing while there is no reading (before the first, or no answer). */
internal fun latencyBadgeText(latency: ConnectionLatency?): String =
    latency?.lastMs?.takeUnless { latency.lost }?.let { "$it ms" }.orEmpty()

/** The badge keeps room for a reading this wide from the start, so the title next to it never changes size. */
private const val LATENCY_BADGE_WIDEST = "000 ms"

/**
 * The session's round trip to its server, for Advanced settings → Show connection latency: green under 100 ms,
 * amber under 300 ms, red above. Without a reading (before the first check, or no answer to the last one) the
 * dot is grey and the text is blank, at the same width. Tapping it shows the last minute's figures.
 */
@Composable
fun ConnectionLatencyBadge(latency: ConnectionLatency?, modifier: Modifier = Modifier) {
    var showDetails by remember { mutableStateOf(false) }
    val text = latencyBadgeText(latency)
    val dot = when (latency?.quality.takeIf { text.isNotEmpty() }) {
        ConnectionLatency.Quality.GOOD -> Color(0xFF43A047)
        ConnectionLatency.Quality.FAIR -> Color(0xFFFFB300)
        ConnectionLatency.Quality.POOR -> Color(0xFFE53935)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val spoken = when {
        latency == null -> "not measured yet"
        text.isEmpty() -> "no answer"
        else -> text
    }
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val textWidth = with(LocalDensity.current) {
        remember(style, this) { measurer.measure(LATENCY_BADGE_WIDEST, style).size.width.toDp() }
    }
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = latency != null) { showDetails = true }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Connection latency $spoken" }
            .testTag(UiTestTags.CONNECTION_LATENCY_BADGE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, modifier = Modifier.widthIn(min = textWidth), style = style, textAlign = TextAlign.End, maxLines = 1)
    }
    if (showDetails && latency != null) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            modifier = Modifier.testTag(UiTestTags.CONNECTION_LATENCY_DIALOG),
            title = { Text("Connection latency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (latency.lost) "The last check got no answer within 5 seconds."
                        else "Round trip to the server: ${latency.lastMs} ms."
                    )
                    if (latency.samples > 0) {
                        Text(
                            "Last minute (${latency.samples} checks): fastest ${latency.minMs} ms, average " +
                                "${latency.avgMs} ms, slowest ${latency.maxMs} ms, varying by ${latency.jitterMs} ms."
                        )
                    }
                    Text(
                        if (latency.via == "SSH") {
                            "Timed with an SSH keepalive every 5 seconds. Under 100 ms feels instant; above 300 ms " +
                                "typing lags. Transfers show their own speed."
                        } else {
                            "Timed with the ${latency.via} terminal's own pings, the path your keys take. Under " +
                                "100 ms feels instant; above 300 ms typing lags."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } }
        )
    }
}
