package com.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.ui.navigation.DrawerDestination

@Composable
fun AppDrawer(
    destinations: List<DrawerDestination>,
    currentRoute: String,
    onDestinationSelected: (DrawerDestination) -> Unit,
    onQuickConnect: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SSHPeaches", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onQuickConnect, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Quick Connect", modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp)) {
            items(destinations) { dest ->
                NavigationDrawerItem(
                    label = { Text(dest.label) },
                    icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = null) },
                    selected = currentRoute == dest.route,
                    onClick = { onDestinationSelected(dest) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DrawerDefaults.containerColor,
                        unselectedContainerColor = DrawerDefaults.containerColor
                    )
                )
            }
        }
    }
}
