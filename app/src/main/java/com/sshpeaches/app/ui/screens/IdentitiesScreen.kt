package com.sshpeaches.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.ui.components.EmptyState
import java.util.UUID

private const val TAG = "CW/IdentitiesScreen"

@Composable
fun IdentitiesScreen(
    items: List<Identity>,
    onAdd: (label: String, fingerprint: String, username: String?) -> Unit = { _, _, _ -> },
    onUpdate: (id: String, label: String, fingerprint: String, username: String?) -> Unit = { _, _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    editMode: Boolean = false,
    addRequest: Boolean = false,
    onAddConsumed: () -> Unit = {},
    onImportFromQr: () -> Unit = {}
) {
    val showDialog = remember { mutableStateOf(false) }
    val editingId = remember { mutableStateOf<String?>(null) }
    val labelState = remember { mutableStateOf("") }
    val fingerprintState = remember { mutableStateOf("") }
    val usernameState = remember { mutableStateOf("") }

    fun openDialog(identity: Identity?) {
        editingId.value = identity?.id
        labelState.value = identity?.label ?: ""
        fingerprintState.value = identity?.fingerprint ?: ""
        usernameState.value = identity?.username ?: ""
        showDialog.value = true
    }

    fun closeDialog() {
        showDialog.value = false
    }

    LaunchedEffect(addRequest) {
        if (addRequest) {
            openDialog(null)
            onAddConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (items.isEmpty()) {
            item { EmptyState(itemLabel = "identity") }
        } else {
            items(items, key = { it.id }) { identity ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(identity.label, style = MaterialTheme.typography.titleMedium)
                        Text(identity.fingerprint, style = MaterialTheme.typography.bodySmall)
                        identity.username?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (identity.favorite) {
                            Icon(Icons.Default.Star, contentDescription = null)
                        }
                        if (editMode) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(start = 8.dp)
                                    .clickable { 
                                        Log.i(TAG, "UI identity_edit_click id=${identity.id}")
                                        openDialog(identity) 
                                    }
                            )
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { 
                                        Log.i(TAG, "UI identity_delete_click id=${identity.id}")
                                        onDelete(identity.id) 
                                    }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showDialog.value) {
        val isEdit = editingId.value != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { closeDialog() },
            title = { Text(if (isEdit) "Edit identity" else "Add identity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = labelState.value,
                        onValueChange = { labelState.value = it },
                        label = { Text("Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = usernameState.value,
                        onValueChange = { usernameState.value = it },
                        label = { Text("Username (optional)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fingerprintState.value,
                        onValueChange = { fingerprintState.value = it },
                        label = { Text("Fingerprint") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isEdit) {
                        onUpdate(
                            editingId.value!!,
                            labelState.value,
                            fingerprintState.value,
                            usernameState.value.ifBlank { null }
                        )
                    } else {
                        onAdd(
                            labelState.value.ifBlank { "Identity ${UUID.randomUUID()}" },
                            fingerprintState.value,
                            usernameState.value.ifBlank { null }
                        )
                    }
                    closeDialog()
                }) { Text(if (isEdit) "Save" else "Add") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEdit) {
                        TextButton(onClick = {
                            onDelete(editingId.value!!)
                            closeDialog()
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { closeDialog() }) { Text("Cancel") }
                }
            }
        )
    }
}
