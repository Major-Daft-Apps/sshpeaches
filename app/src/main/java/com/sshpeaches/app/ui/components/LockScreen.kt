package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LockScreenOverlay(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onBiometricUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pinState = remember { mutableStateOf("") }
    val errorState = remember { mutableStateOf<String?>(null) }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("SSHPeaches Locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (biometricEnabled && biometricAvailable) {
                    "Use your PIN or biometric to continue."
                } else {
                    "Use your PIN to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = pinState.value,
                onValueChange = { input ->
                    pinState.value = input.filter { it.isDigit() }.take(10)
                    errorState.value = null
                },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
            errorState.value?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = {
                    val success = onUnlockWithPin(pinState.value)
                    if (success) {
                        pinState.value = ""
                        errorState.value = null
                    } else {
                        errorState.value = "Incorrect PIN"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                enabled = pinState.value.length >= 4
            ) {
                Text("Unlock with PIN")
            }
            if (biometricEnabled && biometricAvailable) {
                TextButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Unlock with biometric")
                }
            } else if (biometricEnabled && !biometricAvailable) {
                Text(
                    "Biometric unlock unavailable on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
