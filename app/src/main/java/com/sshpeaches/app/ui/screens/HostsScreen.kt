package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.state.SortMode

@Composable
fun HostsScreen(
    hosts: List<HostConnection>,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    editMode: Boolean = false,
    onAdd: () -> Unit = {},
    onImportFromQr: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onEdit: (HostConnection) -> Unit = {}
) {
    val search = remember { mutableStateOf("") }
    val showMenu = remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = search.value,
                onValueChange = { search.value = it },
                placeholder = { Text("Search hosts") },
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Column {
                IconButton(onClick = { showMenu.value = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showMenu.value, onDismissRequest = { showMenu.value = false }) {
                    DropdownMenuItem(
                        text = { Text("Last Used", fontWeight = if (sortMode == SortMode.LAST_USED) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            showMenu.value = false
                            onSortModeChange(SortMode.LAST_USED)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alphabetical", fontWeight = if (sortMode == SortMode.ALPHABETICAL) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            showMenu.value = false
                            onSortModeChange(SortMode.ALPHABETICAL)
                        }
                    )
                }
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Add host")
                }
            }
            items(hosts.filter { it.name.contains(search.value, ignoreCase = true) }, key = { it.id }) { host ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HostCard(host = host)
                    if (editMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { onEdit(host) }) { Text("Edit") }
                            TextButton(onClick = { onDelete(host.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
