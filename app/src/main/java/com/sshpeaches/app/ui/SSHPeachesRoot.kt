package com.sshpeaches.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.QrCodeScanner
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
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import com.sshpeaches.app.ui.screens.ConnectingScreen
import com.sshpeaches.app.ui.state.AppUiState
import com.sshpeaches.app.ui.state.LockTimeout
import com.sshpeaches.app.ui.state.SortMode
import com.sshpeaches.app.ui.state.ThemeMode
import com.sshpeaches.app.ui.theme.CarbonBlack
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.ssh.SshClientProvider
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.logging.CrashLogger
import android.util.Log

private const val TAG = "CW/SSHPeachesRoot"

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
    onHostKeyPromptToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showQuickConnect = rememberSaveable { mutableStateOf(false) }
    val showAbout = rememberSaveable { mutableStateOf(false) }
    val editMode = rememberSaveable { mutableStateOf(false) }
    val connectingHost = remember { mutableStateOf<String?>(null) }
    val connectionLogs = remember { mutableStateOf(listOf<String>()) }
    val showAddDialog = remember { mutableStateOf(false) }
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
                                    Log.i(TAG, "NAV navigate from=$currentRoute to=${destination.route}")
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                                val managementRoutes = listOf(Routes.HOSTS, Routes.IDENTITIES, Routes.FORWARDS, Routes.SNIPPETS)
                                if (currentRoute in managementRoutes) {
                                    // 1. QR Scan
                                    IconButton(onClick = {
                                        Log.i(TAG, "UI qr_scan_trigger screen=$currentRoute")
                                        // Trigger QR scan for the active screen
                                        when (currentRoute) {
                                            Routes.HOSTS -> { /* Scan for host */ }
                                            Routes.IDENTITIES -> { /* Scan for identity */ }
                                            Routes.FORWARDS -> { /* Scan for forward */ }
                                            Routes.SNIPPETS -> { /* Scan for snippet */ }
                                        }
                                    }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                                    }

                                    // 2. Edit/Done
                                    AnimatedContent(targetState = editMode.value, label = "editMode") { editing ->
                                        IconButton(onClick = { editMode.value = !editMode.value }) {
                                            Icon(
                                                imageVector = if (editing) Icons.Default.Done else Icons.Default.Edit,
                                                contentDescription = if (editing) "Done editing" else "Edit"
                                            )
                                        }
                                    }

                                    // 3. Add Item
                                    IconButton(onClick = { showAddDialog.value = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Item")
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
                                addRequest = showAddDialog.value,
                                onAddConsumed = { showAddDialog.value = false },
                                onImportFromQr = { /* TODO: implement QR decode + save host */ }
                            )
                        }
                        composable(Routes.IDENTITIES) {
                            IdentitiesScreen(
                                items = uiState.identities,
                                editMode = editMode.value,
                                addRequest = showAddDialog.value,
                                onAddConsumed = { showAddDialog.value = false },
                                onImportFromQr = { /* TODO */ }
                            )
                        }
                        composable(Routes.FORWARDS) {
                            PortForwardScreen(
                                items = uiState.portForwards,
                                hosts = uiState.hosts,
                                editMode = editMode.value,
                                addRequest = showAddDialog.value,
                                onAddConsumed = { showAddDialog.value = false },
                                onImportFromQr = { /* TODO */ }
                            )
                        }
                        composable(Routes.SNIPPETS) {
                            SnippetManagerScreen(
                                snippets = uiState.snippets,
                                addRequest = showAddDialog.value,
                                onAddConsumed = { showAddDialog.value = false }
                            )
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
                                onHostKeyPromptToggle = onHostKeyPromptToggle
                            )
                        }
                    }
                }
            }
            

            // Connection Overlay
            connectingHost.value?.let { host ->
                ConnectingScreen(
                    hostName = host,
                    logs = connectionLogs.value,
                    onCancel = { connectingHost.value = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showQuickConnect.value) {
        QuickConnectSheet(
            onDismiss = { showQuickConnect.value = false },
            onConnectStart = { host ->
                connectingHost.value = host
                connectionLogs.value = emptyList()
            },
            onLogUpdate = { log ->
                connectionLogs.value = connectionLogs.value + log
            },
            onFinished = {
                connectingHost.value = null
            }
        )
    }

    if (showAbout.value) {
        AboutDialog(onDismiss = { showAbout.value = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickConnectSheet(
    onDismiss: () -> Unit,
    onConnectStart: (String) -> Unit,
    onLogUpdate: (String) -> Unit,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val context = LocalContext.current
        val host = remember { mutableStateOf("") }
        val port = remember { mutableStateOf("22") }
        val username = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val auth = remember { mutableStateOf(AuthMethod.PASSWORD) }
        val status = remember { mutableStateOf<String?>(null) }
        val isConnecting = remember { mutableStateOf(false) }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    onDismiss() // Close sheet to show connecting screen
                    onConnectStart(hostValue)

                    scope.launch(Dispatchers.IO) {
                        isConnecting.value = true
                        Log.i(TAG, "SSH connect start host=$hostValue port=$portValue user=$userValue auth=${auth.value}")
                        onLogUpdate("ssh_socket_connect: Connecting to $hostValue:$portValue...")
                        val t0 = SystemClock.elapsedRealtime()
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
                            onLogUpdate("ssh_connect: Socket connecting, now waiting for callbacks")
                            client.connect(hostValue, portValue)
                            onLogUpdate("ssh_client_connection_callback: SSH transport established")
                            onLogUpdate("ssh_analyze_banner: Talking to OpenSSH client version: ${client.transport.clientVersion}")

                            when (auth.value) {
                                AuthMethod.PASSWORD -> {
                                    onLogUpdate("ssh_auth_password: Attempting password authentication for $userValue")
                                    client.authPassword(userValue, password.value)
                                }
                                AuthMethod.IDENTITY -> {
                                    onLogUpdate("ssh_auth_publickey: Attempting public key authentication for $userValue")
                                    client.authPublickey(userValue)
                                }
                                AuthMethod.PASSWORD_AND_IDENTITY -> {
                                    onLogUpdate("ssh_auth_methods: Attempting multi-factor authentication")
                                    runCatching { 
                                        onLogUpdate("ssh_auth_publickey: Trying public key...")
                                        client.authPublickey(userValue) 
                                    }
                                    onLogUpdate("ssh_auth_password: Trying password...")
                                    client.authPassword(userValue, password.value)
                                }
                            }
                            onLogUpdate("ssh_connect_success: Authentication successful!")
                            val ms = SystemClock.elapsedRealtime() - t0
                            Log.i(TAG, "SSH connect success host=$hostValue ms=$ms")
                            // In a real app, we'd navigate to the terminal here. 
                            // For now, we'll just wait a bit so the user can see the success.
                            kotlinx.coroutines.delay(1500)
                            client.disconnect()
                        }.onSuccess {
                            onLogUpdate("ssh_disconnect: Session finished.")
                        }.onFailure { e ->
                            val ms = SystemClock.elapsedRealtime() - t0
                            Log.e(TAG, "SSH connect fail host=$hostValue ms=$ms err=${e.javaClass.simpleName} msg=${e.message?.take(200)}", e)
                            CrashLogger.logNonFatal("QuickConnect", e)
                            onLogUpdate("ssh_error: Failed: ${e.message}")
                            kotlinx.coroutines.delay(3000)
                        }
                        isConnecting.value = false
                        onFinished()
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
