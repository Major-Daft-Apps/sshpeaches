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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.Snippet

@Composable
fun SnippetManagerScreen(snippets: List<Snippet>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(snippets, key = { it.id }) { snippet ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                    Text(snippet.description, style = MaterialTheme.typography.bodyMedium)
                    Text(snippet.command, style = MaterialTheme.typography.bodySmall)
                    RowActions()
                }
            }
        }
    }
}

@Composable
private fun RowActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { /* TODO run snippet */ }) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Run")
        }
        IconButton(onClick = { /* TODO edit */ }) {
            Icon(Icons.Default.Edit, contentDescription = null)
        }
    }
}
