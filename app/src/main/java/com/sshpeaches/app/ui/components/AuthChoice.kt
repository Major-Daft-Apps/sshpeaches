package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod

@Composable
fun AuthChoice(label: String, value: AuthMethod, current: AuthMethod, onSelect: (AuthMethod) -> Unit) {
    val selected = value == current
    Button(
        onClick = { onSelect(value) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label)
    }
}
