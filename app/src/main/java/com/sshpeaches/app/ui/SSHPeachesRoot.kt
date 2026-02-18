package com.sshpeaches.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.ssh.SshClientProvider
import com.sshpeaches.app.ui.components.AppDrawer
import com.sshpeaches.app.ui.components.AuthChoice
import com.sshpeaches.app.ui.navigation.Routes
import com.sshpeaches.app.ui.navigation.drawerDestinations
import com.sshpeaches.app.ui.screens.FavoritesScreen
import com.sshpeaches.app.ui.screens.HostsScreen
import com.sshpeaches.app.ui.screens.IdentitiesScreen
import com.sshpeaches.app.ui.screens.KeyboardEditorScreen
import com.sshpeaches.app.ui.screens.PortForwardScreen
import com.sshpeaches.app.ui.screens.SettingsScreen
import com.sshpeaches.app.ui.screens.SnippetManagerScreen
import com.sshpeaches.app.ui.state.AppUiState
import com.sshpeaches.app.ui.state.LockTimeout
import com.sshpeaches.app.ui.state.SortMode
import com.sshpeaches.app.ui.state.ThemeMode
import com.sshpeaches.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHPeachesRoot(
    uiState: AppUiState,
    onSortModeChange: (SortMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackgroundModeChange: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLockTimeoutChange: (LockTimeout) -> Unit,
    onCrashReportsToggle: (Boolean) -> Unit,
    onAnalyticsToggle: (Boolean) -> Unit,
    onDiagnosticsToggle: (Boolean) -> Unit,
    onIncludeIdentitiesToggle: (Boolean) -> Unit,
    onIncludeSettingsToggle: (Boolean) -> Unit,
    onAutoStartForwardsToggle: (Boolean) -> Unit,
    onHostKeyPromptToggle: (Boolean) -> Unit,
    onUsageReportsToggle: (Boolean) -> Unit,
    onHostAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode) -> Unit,
    onHostUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode) -> Unit,
    onHostDelete: (String) -> Unit,
    onPortForwardAdd: (String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onPortForwardUpdate: (String, String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onPortForwardDelete: (String) -> Unit,
    onStartSession: (HostConnection, ConnectionMode) -> Unit,
    onStopSession: (String) -> Unit,
    onIdentityAdd: (String, String, String?) -> Unit,
    onIdentityUpdate: (String, String, String, String?) -> Unit,
    onIdentityDelete: (String) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showQuickConnect = rememberSaveable { mutableStateOf(false) }
    val showAbout = rememberSaveable { mutableStateOf(false) }
    val editMode = rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val helpUrl = context.getString(R.string.project_website)
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route ?: Routes.FAVORITES
    val currentTitle = when (currentRoute) {
        Routes.FAVORITES -> "Favorites"
        Routes.HOSTS -> "Hosts"
        Routes.IDENTITIES -> "Identities"
        Routes.FORWARDS -> "Port Forwards"
        Routes.SNIPPETS -> "Snippets"
        Routes.KEYBOARD -> "Keyboard"
        Routes.SETTINGS -> "Settings"
        else -> "SSHPeaches"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                drawerTonalElevation = 0.dp
            ) {
                AppDrawer(
                    destinations = drawerDestinations,
                    currentRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        scope.launch { drawerState.close() }
                        when (destination.route) {
                            Routes.HELP -> {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))
                                context.startActivity(intent)
                            }
                            Routes.ABOUT -> showAbout.value = true
                            else -> {
                                if (destination.route != currentRoute) {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.FAVORITES)
                                    }
                                }
                            }
                        }
                    },
                    onQuickConnect = { showQuickConnect.value = true }
                )
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            AnimatedContent(targetState = editMode.value, label = "editMode") { editing ->
                                IconButton(onClick = { editMode.value = !editMode.value }) {
                                    Icon(
                                        imageVector = if (editing) Icons.Default.Done else Icons.Default.Edit,
                                        contentDescription = if (editing) "Done editing" else "Edit"
                                    )
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.FAVORITES,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                ) {
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(section = uiState.favorites)
                    }
                    composable(Routes.HOSTS) {
                        HostsScreen(
                            hosts = uiState.hosts,
                            sortMode = uiState.sortMode,
                            onSortModeChange = onSortModeChange,
                            editMode = editMode.value,
                            onImportFromQr = { /* TODO: implement QR decode + save host */ },
                            onAdd = onHostAdd,
                            onUpdate = onHostUpdate,
                            onDeleteHost = onHostDelete,
                            onStartSession = onStartSession,
                            onStopSession = onStopSession
                        )
                    }
                    composable(Routes.IDENTITIES) {
                        IdentitiesScreen(
                            items = uiState.identities,
                            onAdd = onIdentityAdd,
                            onUpdate = onIdentityUpdate,
                            onDelete = onIdentityDelete,
                            editMode = editMode.value,
                            onImportFromQr = { /* TODO */ }
                        )
                    }
                    composable(Routes.FORWARDS) {
                        PortForwardScreen(
                            items = uiState.portForwards,
                            hosts = uiState.hosts,
                            editMode = editMode.value,
                            onAdd = onPortForwardAdd,
                            onUpdate = onPortForwardUpdate,
                            onDelete = onPortForwardDelete,
                            onImportFromQr = { /* TODO */ }
                        )
                    }
                    composable(Routes.SNIPPETS) {
                        SnippetManagerScreen(snippets = uiState.snippets)
                    }
                    composable(Routes.KEYBOARD) {
                        KeyboardEditorScreen()
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            currentTheme = uiState.themeMode,
                            onThemeChange = onThemeModeChange,
                            allowBackgroundSessions = uiState.allowBackgroundSessions,
                            onBackgroundToggle = onBackgroundModeChange,
                            biometricEnabled = uiState.biometricLockEnabled,
                            onBiometricToggle = onBiometricToggle,
                            lockTimeout = uiState.lockTimeout,
                            onLockTimeoutChange = onLockTimeoutChange,
                            crashReportsEnabled = uiState.crashReportsEnabled,
                            onCrashReportsToggle = onCrashReportsToggle,
                            analyticsEnabled = uiState.analyticsEnabled,
                            onAnalyticsToggle = onAnalyticsToggle,
                            diagnosticsLoggingEnabled = uiState.diagnosticsLoggingEnabled,
                            onDiagnosticsToggle = onDiagnosticsToggle,
                            includeIdentities = uiState.includeIdentitiesInQr,
                            onIncludeIdentitiesToggle = onIncludeIdentitiesToggle,
                            includeSettings = uiState.includeSettingsInQr,
                            onIncludeSettingsToggle = onIncludeSettingsToggle,
                            autoStartForwards = uiState.autoStartForwards,
                            onAutoStartForwardsToggle = onAutoStartForwardsToggle,
                            hostKeyPromptEnabled = uiState.hostKeyPromptEnabled,
                            onHostKeyPromptToggle = onHostKeyPromptToggle,
                            usageReportsEnabled = uiState.usageReportsEnabled,
                            onUsageReportsToggle = onUsageReportsToggle
                        )
                    }
                }
            }
        }
    }

    if (showQuickConnect.value) {
        QuickConnectSheet(
            onDismiss = { showQuickConnect.value = false }
        )
    }

    if (showAbout.value) {
        AboutDialog(onDismiss = { showAbout.value = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickConnectSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val context = LocalContext.current
        val host = remember { mutableStateOf("") }
        val port = remember { mutableStateOf("22") }
        val username = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val auth = remember { mutableStateOf(AuthMethod.PASSWORD) }
        val status = remember { mutableStateOf<String?>(null) }
        val isConnecting = remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = host.value,
                onValueChange = { host.value = it },
                label = { Text("Host / IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port.value,
                onValueChange = { port.value = it },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username.value,
                onValueChange = { username.value = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthChoice("Password", AuthMethod.PASSWORD, auth.value) { auth.value = it }
                AuthChoice("Identity", AuthMethod.IDENTITY, auth.value) { auth.value = it }
                AuthChoice("Both", AuthMethod.PASSWORD_AND_IDENTITY, auth.value) { auth.value = it }
            }
            status.value?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = {
                    val hostValue = host.value.trim()
                    val userValue = username.value.trim()
                    val portValue = port.value.toIntOrNull() ?: 22
                    if (hostValue.isBlank() || userValue.isBlank()) {
                        status.value = "Host and username required"
                        return@Button
                    }
                    scope.launch(Dispatchers.IO) {
                        isConnecting.value = true
                        status.value = "Connecting..."
                        runCatching {
                            val client = SshClientProvider.createClient(
                                context,
                                HostConnection(
                                    id = "quick",
                                    name = hostValue,
                                    host = hostValue,
                                    port = portValue,
                                    username = userValue,
                                    preferredAuth = AuthMethod.PASSWORD
                                )
                            )
                            client.connect(hostValue, portValue)
                            when (auth.value) {
                                AuthMethod.PASSWORD -> client.authPassword(userValue, password.value)
                                AuthMethod.IDENTITY -> client.authPublickey(userValue)
                                AuthMethod.PASSWORD_AND_IDENTITY -> {
                                    runCatching { client.authPublickey(userValue) }
                                    client.authPassword(userValue, password.value)
                                }
                            }
                            client.disconnect()
                        }.onSuccess {
                            status.value = "Connected successfully"
                        }.onFailure { e ->
                            status.value = "Failed: ${e.message}"
                        }
                        isConnecting.value = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnecting.value
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun PlaceholderField(label: String, value: String = "") {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (value.isEmpty()) "Tap to configure" else value,
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("About SSHPeaches") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = com.sshpeaches.app.R.drawable.sshpeaches),
                    contentDescription = "SSHPeaches logo",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Text("Version 0.1.0", style = MaterialTheme.typography.titleMedium)
                val privacy = stringResource(id = R.string.privacy_policy_url)
                val context = LocalContext.current
                Text("Website: https://sshpeaches.app")
                Text("License: Apache-2.0 (draft)")
                Text("Support: support@sshpeaches.app")
                Text(
                    "Privacy Policy",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacy))
                        context.startActivity(intent)
                    }
                )
                Text("Created by Ali Sherief", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}
