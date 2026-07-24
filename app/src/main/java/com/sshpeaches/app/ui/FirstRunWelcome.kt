package com.majordaftapps.sshpeaches.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

internal const val CREATOR_X_PROFILE_URL = "https://x.com/Zenul_Abidin"

private const val FIRST_RUN_PREFERENCES = "first_run_experience"
private const val KEY_WELCOME_COMPLETED = "welcome_completed"

internal object FirstRunWelcomePreferences {
    fun shouldShow(context: Context): Boolean =
        !preferences(context).getBoolean(KEY_WELCOME_COMPLETED, false)

    fun markCompleted(context: Context) {
        preferences(context).edit {
            putBoolean(KEY_WELCOME_COMPLETED, true)
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            FIRST_RUN_PREFERENCES,
            Context.MODE_PRIVATE
        )
}

internal enum class StartupOverlay {
    FIRST_RUN_WELCOME,
    PERMISSIONS,
    NONE
}

internal fun startupOverlay(
    showFirstRunWelcome: Boolean,
    hasMissingCorePermissions: Boolean
): StartupOverlay = when {
    showFirstRunWelcome -> StartupOverlay.FIRST_RUN_WELCOME
    hasMissingCorePermissions -> StartupOverlay.PERMISSIONS
    else -> StartupOverlay.NONE
}

@Composable
internal fun FirstRunWelcomeDialog(
    onContinue: () -> Unit,
    onFollowOnX: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .testTag(UiTestTags.FIRST_RUN_WELCOME_DIALOG),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "A NOTE FROM THE DEVELOPER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Image(
                    painter = painterResource(id = R.drawable.sshpeaches),
                    contentDescription = "SSHPeaches logo",
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = "Welcome to SSHPeaches",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Open source  •  No ads  •  No paid features",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "SSHPeaches is completely free and open source—no ads, " +
                        "subscriptions, or paid features. Just a capable SSH client, " +
                        "made for everyone.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "If you’d like project updates, the only thing I ask is that " +
                        "you follow me on X.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Thanks for being here. I hope you enjoy SSHPeaches!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onFollowOnX,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.FIRST_RUN_FOLLOW_X_BUTTON)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Follow @Zenul_Abidin on X")
                    }
                }

                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.FIRST_RUN_CONTINUE_BUTTON)
                ) {
                    Text("Continue to SSHPeaches")
                }
            }
        }
    }
}
