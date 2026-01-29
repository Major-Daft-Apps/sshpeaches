package com.sshpeaches.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.OsMetadata
import com.sshpeaches.app.data.model.OsFamily
import androidx.compose.ui.res.painterResource

@Composable
fun HostCard(host: HostConnection, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val color = host.osMetadata.toColor()
                Box(
                    modifier = Modifier
                        .background(color = color, shape = CircleShape)
                        .padding(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = host.osMetadata.iconRes()),
                        contentDescription = host.osMetadata.label(),
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                    Text("${host.username}@${host.host}:${host.port}", style = MaterialTheme.typography.bodyMedium)
                    host.group?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(onClick = { /* TODO status */ }) {
                    Text(host.lastUsedEpochMillis?.let { "Last used" } ?: "New")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HostActionButton(label = "SSH", selected = host.defaultMode == ConnectionMode.SSH)
                HostActionButton(label = "SFTP", selected = host.defaultMode == ConnectionMode.SFTP)
                HostActionButton(label = "SCP", selected = host.defaultMode == ConnectionMode.SCP)
                Icon(Icons.Default.Info, contentDescription = "Info")
                Icon(Icons.Default.QrCode, contentDescription = "Share")
            }
        }
    }
}

@Composable
private fun HostActionButton(label: String, selected: Boolean) {
    TextButton(onClick = { /* TODO */ }) {
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun OsMetadata.label(): String = when (this) {
    is OsMetadata.Known -> family.displayName
    is OsMetadata.Custom -> label
    OsMetadata.Undetected -> "?"
}

@Composable
private fun OsMetadata.toColor(): Color = when (this) {
    is OsMetadata.Known -> Color(android.graphics.Color.parseColor(family.colorHex))
    else -> MaterialTheme.colorScheme.primary
}

private fun OsMetadata.iconRes(): Int = when (this) {
    is OsMetadata.Known -> when (family) {
        OsFamily.UBUNTU -> R.drawable.ic_os_ubuntu
        OsFamily.MAC -> R.drawable.ic_os_apple
        OsFamily.BSD -> R.drawable.ic_os_bsd
        else -> R.drawable.ic_os_penguin
    }
    else -> R.drawable.ic_os_penguin
}
