package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.service.ConnectionLatency
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

/** What the latency badge reads: "42 ms", "No answer", or "…" before the first sample. */
internal fun latencyBadgeText(latency: ConnectionLatency?): String = when {
    latency == null -> "…"
    latency.lost -> "No answer"
    else -> "${latency.lastMs} ms"
}

/**
 * The session's round trip to its server, for Advanced settings → Show connection latency: green under 100 ms,
 * amber under 300 ms, red above or without an answer. Tapping it shows the last minute's figures.
 */
@Composable
fun ConnectionLatencyBadge(latency: ConnectionLatency?, modifier: Modifier = Modifier) {
    var showDetails by remember { mutableStateOf(false) }
    val dot = when (latency?.quality) {
        ConnectionLatency.Quality.GOOD -> Color(0xFF43A047)
        ConnectionLatency.Quality.FAIR -> Color(0xFFFFB300)
        ConnectionLatency.Quality.POOR -> Color(0xFFE53935)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = latencyBadgeText(latency)
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = latency != null) { showDetails = true }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Connection latency $text" }
            .testTag(UiTestTags.CONNECTION_LATENCY_BADGE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium)
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
