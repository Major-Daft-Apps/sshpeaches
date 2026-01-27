package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.PortForward

@Composable
fun PortForwardScreen(items: List<PortForward>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { forward ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(forward.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (forward.type) {
                            com.sshpeaches.app.data.model.PortForwardType.LOCAL -> "Local ${forward.sourceHost}:${forward.sourcePort} → ${forward.destinationHost}:${forward.destinationPort}"
                            com.sshpeaches.app.data.model.PortForwardType.REMOTE -> "Remote ${forward.destinationHost}:${forward.destinationPort} ← ${forward.sourceHost}:${forward.sourcePort}"
                            com.sshpeaches.app.data.model.PortForwardType.DYNAMIC -> "Dynamic SOCKS on ${forward.sourceHost}:${forward.sourcePort}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val checked = remember { mutableStateOf(forward.enabled) }
                    Switch(checked = checked.value, onCheckedChange = { checked.value = it })
                }
            }
        }
    }
}
