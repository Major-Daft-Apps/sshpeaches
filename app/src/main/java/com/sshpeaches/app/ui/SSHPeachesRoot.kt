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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import kotlinx.coroutines.CompletableDeferred
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
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.transport.TransportException
import java.net.ConnectException
import java.net.SocketTimeoutException

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
    val isAuthFailed = remember { mutableStateOf(false) }
    val authRetryDeferred = remember { mutableStateOf<CompletableDeferred<String>?>(null) }
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
                    onQuickConnect = {
                        scope.launch { drawerState.close() }
                        showQuickConnect.value = true
                    }
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
            // Use AnimatedVisibility for smooth transitions (in and out)
            AnimatedVisibility(
                visible = connectingHost.value != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                connectingHost.value?.let { host ->
                    ConnectingScreen(
                        hostName = host,
                        logs = connectionLogs.value,
                        onCancel = { 
                            connectingHost.value = null
                            authRetryDeferred.value?.cancel() 
                        },
                        isAuthFailed = isAuthFailed.value,
                        onRetryAuth = { newPassword ->
                            authRetryDeferred.value?.complete(newPassword)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showQuickConnect.value) {
        QuickConnectSheet(
            onDismiss = { showQuickConnect.value = false },
            onConnect = { hostConn, password, authMethod ->
                showQuickConnect.value = false
                scope.launch { drawerState.close() }
                connectingHost.value = hostConn.host
                connectionLogs.value = emptyList()
                
                scope.launch(Dispatchers.IO) {
                    Log.i(TAG, "SSH connect start host=${hostConn.host} port=${hostConn.port} user=${hostConn.username} auth=$authMethod")
                    connectionLogs.value = listOf("ssh_socket_connect: Connecting to ${hostConn.host}:${hostConn.port}...")
                    val t0 = SystemClock.elapsedRealtime()
                    runCatching {
                        val client = SshClientProvider.createClient(context, hostConn)
                        connectionLogs.value = connectionLogs.value + "ssh_connect: Socket connecting, now waiting for callbacks"
                        client.connect(hostConn.host, hostConn.port)
                        connectionLogs.value = connectionLogs.value + "ssh_client_connection_callback: SSH transport established"
                        connectionLogs.value = connectionLogs.value + "ssh_analyze_banner: Talking to OpenSSH client version: ${client.transport.clientVersion}"

                        connectionLogs.value = connectionLogs.value + "ssh_analyze_banner: Talking to OpenSSH client version: ${client.transport.clientVersion}"

                        var currentPassword = password
                        var authenticated = false
                        
                        while(client.isConnected && !authenticated) {
                            try {
                                isAuthFailed.value = false
                                when (authMethod) {
                                    AuthMethod.PASSWORD -> {
                                        connectionLogs.value = connectionLogs.value + "ssh_auth_password: Attempting password authentication for ${hostConn.username}"
                                        client.authPassword(hostConn.username, currentPassword)
                                    }
                                    AuthMethod.IDENTITY -> {
                                        connectionLogs.value = connectionLogs.value + "ssh_auth_publickey: Attempting public key authentication for ${hostConn.username}"
                                        client.authPublickey(hostConn.username)
                                    }
                                    AuthMethod.PASSWORD_AND_IDENTITY -> {
                                        connectionLogs.value = connectionLogs.value + "ssh_auth_methods: Attempting multi-factor authentication"
                                        runCatching { 
                                            connectionLogs.value = connectionLogs.value + "ssh_auth_publickey: Trying public key..."
                                            client.authPublickey(hostConn.username) 
                                        }
                                        connectionLogs.value = connectionLogs.value + "ssh_auth_password: Trying password..."
                                        client.authPassword(hostConn.username, currentPassword)
                                    }
                                }
                                authenticated = true
                                connectionLogs.value = connectionLogs.value + "ssh_connect_success: Authentication successful!"
                            } catch (e: UserAuthException) {
                                connectionLogs.value = connectionLogs.value + "ssh_auth_fail: Authentication failed."
                                
                                // Indicate UI failure and wait for new input
                                isAuthFailed.value = true
                                val deferred = CompletableDeferred<String>()
                                authRetryDeferred.value = deferred
                                
                                try {
                                    connectionLogs.value = connectionLogs.value + "ssh_auth_retry: Waiting for user input..."
                                    currentPassword = deferred.await()
                                    connectionLogs.value = connectionLogs.value + "ssh_auth_retry: Retrying with new password..."
                                } catch (cancel: Exception) {
                                    throw UserAuthException("User cancelled authentication retry")
                                }
                            }
                        }

                        if (authenticated) {
                            val ms = SystemClock.elapsedRealtime() - t0
                            Log.i(TAG, "SSH connect success host=${hostConn.host} ms=$ms")
                            kotlinx.coroutines.delay(1500)
                            client.disconnect()
                        }
                    }.onSuccess {
                        connectionLogs.value = connectionLogs.value + "ssh_disconnect: Session finished."
                    }.onFailure { e ->
                        val ms = SystemClock.elapsedRealtime() - t0
                        
                        // Categorize error for better user feedback
                        val errorMsg = when(e) {
                            is UserAuthException -> "Authentication failed: Check username/password/key."
                            is ConnectException -> "Connection refused: Server not listening on port ${hostConn.port}."
                            is SocketTimeoutException -> "Connection timed out: Server unreachable."
                            is TransportException -> {
                                if (e.message?.contains("Host key verification failed") == true) {
                                    "Security Alert: Host key verification failed! (MITM warning)"
                                } else {
                                    "Transport error: ${e.message}"
                                }
                            }
                            else -> "Error: ${e.message ?: e.javaClass.simpleName}"
                        }
                        
                        Log.e(TAG, "SSH connect fail host=${hostConn.host} ms=$ms err=${e.javaClass.simpleName} msg=$errorMsg", e)
                        
                        // For expected network/auth errors, maybe skip crash reporting or log as warning
                        if (e !is UserAuthException && e !is ConnectException) {
                            CrashLogger.logNonFatal("QuickConnect", e)
                        }
                        
                        connectionLogs.value = connectionLogs.value + "ssh_error: $errorMsg"
                        kotlinx.coroutines.delay(3000)
                    }
                    connectingHost.value = null
                }
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
    onConnect: (HostConnection, String, AuthMethod) -> Unit
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
                    onConnect(
                        HostConnection(
                            id = "quick",
                            name = hostValue,
                            host = hostValue,
                            port = portValue,
                            username = userValue,
                            preferredAuth = AuthMethod.PASSWORD
                        ),
                        password.value,
                        auth.value
                    )
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
