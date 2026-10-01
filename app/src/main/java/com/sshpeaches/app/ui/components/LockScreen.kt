package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.calculatePasswordRevealIndex

/**
 * Full-screen lock shown in its own window, so it sits above any dialog or bottom sheet that was
 * open when the app locked and receives key input instead of the content underneath.
 *
 * [onUnlockWithPin] returns null on success or the message to show. [externalMessage] shows
 * results that arrive outside this screen (e.g. a failed biometric unlock).
 */
@Composable
fun LockScreenOverlay(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onUnlockWithPin: suspend (String) -> String?,
    onBiometricUnlock: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    externalMessage: String? = null
) {
    Dialog(
        onDismissRequest = onBackPressed,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LockScreenContent(
            biometricEnabled = biometricEnabled,
            biometricAvailable = biometricAvailable,
            onUnlockWithPin = onUnlockWithPin,
            onBiometricUnlock = onBiometricUnlock,
            externalMessage = externalMessage,
            modifier = modifier
        )
    }
}

@Composable
private fun LockScreenContent(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onUnlockWithPin: suspend (String) -> String?,
    onBiometricUnlock: () -> Unit,
    externalMessage: String?,
    modifier: Modifier
) {
    val pinState = remember { mutableStateOf("") }
    val pinRevealIndex = remember { mutableIntStateOf(-1) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val unlocking = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AutoHidePasswordReveal(pinRevealIndex)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.LOCK_SCREEN_OVERLAY),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.sshpeaches),
                contentDescription = "SSHPeaches logo",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("SSHPeaches is locked", style = MaterialTheme.typography.headlineSmall)
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
                    val previous = pinState.value
                    val next = input.filter { it.isDigit() }.take(10)
                    pinState.value = next
                    pinRevealIndex.intValue = calculatePasswordRevealIndex(previous, next)
                    errorState.value = null
                },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = TailRevealPasswordVisualTransformation(pinRevealIndex.intValue),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.NumberPassword
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .testTag(UiTestTags.LOCK_SCREEN_PIN_INPUT)
            )
            (errorState.value ?: externalMessage)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = {
                    if (unlocking.value) return@Button
                    unlocking.value = true
                    errorState.value = null
                    scope.launch {
                        val error = runCatching { onUnlockWithPin(pinState.value) }
                            .getOrElse { "Couldn't verify PIN: ${it.message ?: it.javaClass.simpleName}" }
                        unlocking.value = false
                        if (error == null) {
                            pinState.value = ""
                            errorState.value = null
                        } else {
                            errorState.value = error
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .testTag(UiTestTags.LOCK_SCREEN_UNLOCK_BUTTON),
                enabled = pinState.value.length >= 4 && !unlocking.value
            ) {
                Text(if (unlocking.value) "Unlocking…" else "Unlock with PIN")
            }
            if (biometricEnabled && biometricAvailable) {
                TextButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(UiTestTags.LOCK_SCREEN_BIOMETRIC_BUTTON)
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
