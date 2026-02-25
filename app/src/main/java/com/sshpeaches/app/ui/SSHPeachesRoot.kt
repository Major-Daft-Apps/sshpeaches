package com.sshpeaches.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.BackgroundBehavior
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.PortForwardType
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.TerminalProfile
import com.sshpeaches.app.data.model.TerminalProfileDefaults
import com.sshpeaches.app.ui.components.AppDrawer
import com.sshpeaches.app.ui.components.AuthChoice
import com.sshpeaches.app.ui.components.LockScreenOverlay
import com.sshpeaches.app.ui.navigation.Routes
import com.sshpeaches.app.ui.navigation.drawerDestinations
import com.sshpeaches.app.ui.screens.ConnectingScreen
import com.sshpeaches.app.ui.screens.FavoritesScreen
import com.sshpeaches.app.ui.screens.HostsScreen
import com.sshpeaches.app.ui.screens.IdentitiesScreen
import com.sshpeaches.app.ui.screens.KeyboardEditorScreen
import com.sshpeaches.app.ui.screens.PortForwardScreen
import com.sshpeaches.app.ui.screens.QuickConnectPhase
import com.sshpeaches.app.ui.screens.QuickConnectRequest
import com.sshpeaches.app.ui.screens.QuickConnectUiState
import com.sshpeaches.app.ui.screens.SettingsScreen
import com.sshpeaches.app.ui.screens.SnippetManagerScreen
import com.sshpeaches.app.ui.state.AppUiState
import com.sshpeaches.app.ui.state.LockTimeout
import com.sshpeaches.app.ui.state.SortMode
import com.sshpeaches.app.ui.state.ThemeMode
import com.sshpeaches.app.ui.util.rememberBottomSheetMaxHeight
import com.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.sshpeaches.app.service.SessionLogBus
import com.sshpeaches.app.service.SessionService.HostKeyPrompt
import com.sshpeaches.app.service.SessionService.PasswordPrompt
import com.sshpeaches.app.service.SessionService.SessionSnapshot
import com.sshpeaches.app.R
import java.util.UUID
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHPeachesRoot(
    uiState: AppUiState,
    biometricAvailable: Boolean,
    onSortModeChange: (SortMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackgroundModeChange: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLockTimeoutChange: (LockTimeout) -> Unit,
    onCustomLockTimeoutMinutesChange: (Int) -> Unit,
    onTerminalEmulationChange: (com.sshpeaches.app.data.model.TerminalEmulation) -> Unit,
    onCrashReportsToggle: (Boolean) -> Unit,
    onAnalyticsToggle: (Boolean) -> Unit,
    onDiagnosticsToggle: (Boolean) -> Unit,
    onIncludeIdentitiesToggle: (Boolean) -> Unit,
    onIncludeSettingsToggle: (Boolean) -> Unit,
    onAutoStartForwardsToggle: (Boolean) -> Unit,
    onHostKeyPromptToggle: (Boolean) -> Unit,
    onAutoTrustHostKeyToggle: (Boolean) -> Unit,
    onUsageReportsToggle: (Boolean) -> Unit,
    onDefaultTerminalProfileChange: (String) -> Unit,
    onSaveTerminalProfile: (TerminalProfile) -> Unit,
    onDeleteTerminalProfile: (String) -> Unit,
    onRestoreDefaultSettings: () -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onLockApp: () -> Unit,
    onUnlockWithPin: (String) -> Boolean,
    onBiometricUnlock: () -> Unit,
    onHostAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String, BackgroundBehavior, String?, String?, String?) -> Unit,
    onHostUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String, BackgroundBehavior, String?, String?) -> Unit,
    onHostDelete: (String) -> Unit,
    onPortForwardAdd: (String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onPortForwardUpdate: (String, String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onPortForwardDelete: (String) -> Unit,
    onStartSession: (HostConnection, ConnectionMode, String?) -> Unit,
    onStopSession: (String) -> Unit,
    onIdentityAdd: (String, String, String?, String?) -> Unit,
    onIdentityUpdate: (String, String, String, String?) -> Unit,
    onIdentityDelete: (String) -> Unit,
    onImportIdentityKey: (String, String, String) -> Boolean,
    onImportIdentityKeyPlain: (String, String) -> Boolean,
    onRemoveIdentityKey: (String) -> Unit,
    onKeyboardSlotChange: (Int, String) -> Unit,
    onKeyboardReset: () -> Unit,
    onSnippetAdd: (String, String, String) -> Unit,
    onSnippetUpdate: (String, String, String, String) -> Unit,
    onSnippetDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSendSessionShortcut: (String, String) -> Unit,
    onSendShellBytes: (String, ByteArray) -> Unit,
    onResizeShell: (String, Int, Int) -> Unit,
    sessions: List<SessionSnapshot>,
    shellOutputs: Map<String, String>,
    hostKeyPrompts: List<HostKeyPrompt>,
    passwordPrompts: List<PasswordPrompt>,
    requestedOpenSessionId: String?,
    onOpenSessionRequestHandled: () -> Unit,
    onRespondToHostKeyPrompt: (String, Boolean) -> Unit,
    onRespondToPasswordPrompt: (String, String?, Boolean) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showQuickConnect = rememberSaveable { mutableStateOf(false) }
    val showAbout = rememberSaveable { mutableStateOf(false) }
    val quickConnectRequest = remember { mutableStateOf<QuickConnectRequest?>(null) }
    val quickConnectState = remember { mutableStateOf(QuickConnectUiState()) }
    val pendingConnectingNavigation = remember { mutableStateOf(false) }
    val pendingFavoriteHostId = remember { mutableStateOf<String?>(null) }
    val editMode = rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val helpUrl = context.getString(R.string.support_url)
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route ?: Routes.FAVORITES
    val currentTitle = when (currentRoute) {
        Routes.FAVORITES -> "Favorites"
        Routes.CONNECTING -> "Connecting"
        Routes.HOSTS -> "Hosts"
        Routes.IDENTITIES -> "Identities"
        Routes.FORWARDS -> "Port Forwards"
        Routes.SNIPPETS -> "Snippets"
        Routes.KEYBOARD -> "Keyboard"
        Routes.SETTINGS -> "Settings"
        else -> "SSHPeaches"
    }

    LaunchedEffect(uiState.isLocked) {
        if (uiState.isLocked) {
            showQuickConnect.value = false
            showAbout.value = false
        }
    }

    LaunchedEffect(uiState.hosts, pendingFavoriteHostId.value) {
        val pendingId = pendingFavoriteHostId.value ?: return@LaunchedEffect
        if (uiState.hosts.any { it.id == pendingId }) {
            onToggleFavorite(pendingId)
            pendingFavoriteHostId.value = null
        }
    }

    LaunchedEffect(requestedOpenSessionId, sessions, currentRoute) {
        val targetHostId = requestedOpenSessionId ?: return@LaunchedEffect
        val snapshot = sessions.firstOrNull { it.hostId == targetHostId } ?: return@LaunchedEffect
        val host = snapshot.host
        val savedHostId = uiState.hosts.firstOrNull { it.id == targetHostId }?.id
        quickConnectRequest.value = QuickConnectRequest(
            sessionId = snapshot.hostId,
            name = host.name,
            host = host.host,
            port = host.port,
            username = host.username,
            auth = host.preferredAuth,
            password = "",
            mode = snapshot.mode,
            savedHostId = savedHostId,
            useMosh = host.useMosh,
            forwardId = host.preferredForwardId,
            script = host.startupScript,
            terminalProfileId = host.terminalProfileId
        )
        quickConnectState.value = when (snapshot.status) {
            com.sshpeaches.app.service.SessionService.SessionStatus.CONNECTING -> {
                QuickConnectUiState(
                    phase = QuickConnectPhase.CONNECTING,
                    message = snapshot.statusMessage ?: "Connecting to ${host.host}:${host.port}..."
                )
            }

            com.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE -> {
                QuickConnectUiState(
                    phase = QuickConnectPhase.SUCCESS,
                    message = snapshot.statusMessage ?: "Connected successfully"
                )
            }

            com.sshpeaches.app.service.SessionService.SessionStatus.ERROR -> {
                QuickConnectUiState(
                    phase = QuickConnectPhase.ERROR,
                    message = snapshot.statusMessage ?: "Connection failed"
                )
            }
        }
        pendingConnectingNavigation.value = false
        if (currentRoute != Routes.CONNECTING) {
            navController.navigate(Routes.CONNECTING) {
                popUpTo(Routes.FAVORITES)
            }
        }
        onOpenSessionRequestHandled()
    }

    LaunchedEffect(quickConnectRequest.value?.sessionId, sessions) {
        val request = quickConnectRequest.value ?: return@LaunchedEffect
        val snapshot = sessions.firstOrNull { it.hostId == request.sessionId }
        when (snapshot?.status) {
            null -> {
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.CONNECTING,
                    message = "Connecting to ${request.host}:${request.port}..."
                )
            }

            com.sshpeaches.app.service.SessionService.SessionStatus.CONNECTING -> {
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.CONNECTING,
                    message = snapshot.statusMessage ?: "Connecting to ${request.host}:${request.port}..."
                )
            }

            com.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE -> {
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.SUCCESS,
                    message = snapshot.statusMessage ?: "Connected successfully"
                )
            }

            com.sshpeaches.app.service.SessionService.SessionStatus.ERROR -> {
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.ERROR,
                    message = snapshot.statusMessage ?: "Connection failed"
                )
            }
        }
    }
    LaunchedEffect(currentRoute, quickConnectRequest.value?.sessionId) {
        if (currentRoute == Routes.CONNECTING || quickConnectRequest.value == null) {
            pendingConnectingNavigation.value = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
        }
    val sessionLogs = remember { mutableStateListOf<SessionLogBus.Entry>() }

    LaunchedEffect(Unit) {
        SessionLogBus.entries.collect { entry ->
            sessionLogs.add(entry)
            val overflow = sessionLogs.size - 400
            if (overflow > 0) {
                repeat(overflow) { sessionLogs.removeAt(0) }
            }
        }
    }

    Box {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute != Routes.CONNECTING,
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
                        onQuickConnect = {
                            scope.launch { drawerState.close() }
                            showQuickConnect.value = true
                        }
                    )
                }
            },
            scrimColor = Color.Black.copy(alpha = 0.4f)
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (currentRoute != Routes.CONNECTING) {
                            TopAppBar(
                                title = { Text(currentTitle) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                actions = {
                                    AnimatedContent(
                                        targetState = editMode.value,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.82f)) togetherWith
                                                (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.12f))
                                        },
                                        label = "editMode"
                                    ) { editing ->
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
                            FavoritesScreen(
                                section = uiState.favorites,
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                        composable(Routes.CONNECTING) {
                            val request = quickConnectRequest.value
                            val logs = request?.let { current ->
                                sessionLogs.filter { it.hostId == current.sessionId }
                            } ?: emptyList()
                            val shellOutput = request?.let { current ->
                                shellOutputs[current.sessionId].orEmpty()
                            }.orEmpty()
                            val activeTerminalProfile = uiState.terminalProfiles.firstOrNull {
                                it.id == request?.terminalProfileId
                            } ?: uiState.terminalProfiles.firstOrNull {
                                it.id == uiState.defaultTerminalProfileId
                            } ?: uiState.terminalProfiles.firstOrNull()
                                ?: TerminalProfileDefaults.builtInProfiles.first()
                            ConnectingScreen(
                                request = request,
                                state = quickConnectState.value,
                                logs = logs,
                                shellOutput = shellOutput,
                                terminalProfile = activeTerminalProfile,
                                keyboardSlots = uiState.keyboardSlots,
                                onSendShellBytes = { payload ->
                                    request?.let { current ->
                                        onSendShellBytes(current.sessionId, payload)
                                    }
                                },
                                onTerminalResize = { cols, rows ->
                                    request?.let { current ->
                                        onResizeShell(current.sessionId, cols, rows)
                                    }
                                },
                                onClose = {
                                    request?.let { onStopSession(it.sessionId) }
                                    pendingConnectingNavigation.value = false
                                    navController.popBackStack()
                                    quickConnectRequest.value = null
                                },
                                onRetry = {
                                    quickConnectRequest.value?.let { current ->
                                        onStopSession(current.sessionId)
                                        val next = if (current.savedHostId == null) {
                                            current.copy(sessionId = "quick-${UUID.randomUUID()}")
                                        } else {
                                            current.copy(sessionId = current.savedHostId)
                                        }
                                        quickConnectRequest.value = next
                                        quickConnectState.value = QuickConnectUiState(
                                            phase = QuickConnectPhase.CONNECTING,
                                            message = "Connecting to ${next.host}:${next.port}..."
                                        )
                                        onStartSession(
                                            quickConnectHost(next),
                                            next.mode,
                                            next.password
                                        )
                                    }
                                }
                            )
                        }
                        composable(Routes.HOSTS) {
                        HostsScreen(
                            hosts = uiState.hosts,
                            portForwards = uiState.portForwards,
                            terminalProfiles = uiState.terminalProfiles,
                            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
                            sortMode = uiState.sortMode,
                            onSortModeChange = onSortModeChange,
                            editMode = editMode.value,
                            pinConfigured = uiState.pinConfigured,
                            canStoreCredentials = !uiState.isLocked,
                            onImportFromQr = { showMessage("Host imported from QR") },
                            onToggleFavorite = onToggleFavorite,
                            onAdd = onHostAdd,
                            onUpdate = onHostUpdate,
                            onDeleteHost = onHostDelete,
                            onStartSession = { host, mode, password ->
                                pendingConnectingNavigation.value = true
                                quickConnectRequest.value = QuickConnectRequest(
                                    sessionId = host.id,
                                    name = host.name,
                                    host = host.host,
                                    port = host.port,
                                    username = host.username,
                                    auth = host.preferredAuth,
                                    password = password ?: "",
                                    mode = mode,
                                    savedHostId = host.id,
                                    useMosh = host.useMosh,
                                    forwardId = host.preferredForwardId,
                                    script = host.startupScript,
                                    terminalProfileId = host.terminalProfileId
                                )
                                quickConnectState.value = QuickConnectUiState(
                                    phase = QuickConnectPhase.CONNECTING,
                                    message = "Connecting to ${host.host}:${host.port}..."
                                )
                                onStartSession(host, mode, password)
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate(Routes.CONNECTING)
                                }
                            },
                            onStopSession = onStopSession
                        )
                    }
                    composable(Routes.IDENTITIES) {
                        IdentitiesScreen(
                            items = uiState.identities,
                            pinConfigured = uiState.pinConfigured,
                            isLocked = uiState.isLocked,
                            onAdd = onIdentityAdd,
                            onUpdate = onIdentityUpdate,
                            onDelete = onIdentityDelete,
                            onImportIdentityKey = onImportIdentityKey,
                            onImportIdentityKeyPlain = onImportIdentityKeyPlain,
                            onRemoveIdentityKey = onRemoveIdentityKey,
                            onToggleFavorite = onToggleFavorite,
                            onShowMessage = showMessage,
                            editMode = editMode.value,
                            onImportFromQr = { showMessage("Identity imported from QR") }
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
                                onToggleFavorite = onToggleFavorite,
                                onImportFromQr = { showMessage("Port forward imported from QR") }
                            )
                        }
                        composable(Routes.SNIPPETS) {
                            SnippetManagerScreen(
                                snippets = uiState.snippets,
                                onAdd = onSnippetAdd,
                                onUpdate = onSnippetUpdate,
                                onDelete = onSnippetDelete,
                                onRun = { snippet ->
                                    val target = sessions.firstOrNull()
                                    if (target != null) {
                                        onSendSessionShortcut(target.hostId, snippet.command)
                                        showMessage("Ran snippet on ${target.host.name}")
                                    } else {
                                        showMessage("No active session to run snippet.")
                                    }
                                }
                            )
                        }
                    composable(Routes.KEYBOARD) {
                        KeyboardEditorScreen(
                            slots = uiState.keyboardSlots,
                            onSlotChange = onKeyboardSlotChange,
                            onReset = onKeyboardReset
                        )
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
                                customLockTimeoutMinutes = uiState.customLockTimeoutMinutes,
                                onCustomLockTimeoutMinutesChange = onCustomLockTimeoutMinutesChange,
                                terminalEmulation = uiState.terminalEmulation,
                                onTerminalEmulationChange = onTerminalEmulationChange,
                                terminalProfiles = uiState.terminalProfiles,
                                defaultTerminalProfileId = uiState.defaultTerminalProfileId,
                                onDefaultTerminalProfileChange = onDefaultTerminalProfileChange,
                                onSaveTerminalProfile = onSaveTerminalProfile,
                                onDeleteTerminalProfile = onDeleteTerminalProfile,
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
                                autoTrustHostKey = uiState.autoTrustHostKey,
                                onAutoTrustHostKeyToggle = onAutoTrustHostKeyToggle,
                                usageReportsEnabled = uiState.usageReportsEnabled,
                                onUsageReportsToggle = onUsageReportsToggle,
                                onRestoreDefaultSettings = onRestoreDefaultSettings,
                                pinConfigured = uiState.pinConfigured,
                                isLocked = uiState.isLocked,
                                biometricAvailable = biometricAvailable,
                                onSetPin = onSetPin,
                                onClearPin = onClearPin,
                                onLockApp = onLockApp,
                                onUnlockWithPin = onUnlockWithPin,
                                onGenerateExportPayload = { buildExportPayload(uiState) },
                                onShowMessage = showMessage
                            )
                        }
                    }
                }
            }
        }
        if (uiState.isLocked) {
            LockScreenOverlay(
                biometricEnabled = uiState.biometricLockEnabled,
                biometricAvailable = biometricAvailable,
                onUnlockWithPin = onUnlockWithPin,
                onBiometricUnlock = onBiometricUnlock,
                modifier = Modifier.zIndex(1f)
            )
        }
    }

    if (showQuickConnect.value && !uiState.isLocked) {
        QuickConnectSheet(
            onDismiss = { showQuickConnect.value = false },
            portForwards = uiState.portForwards,
            terminalProfiles = uiState.terminalProfiles,
            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
            onConnect = { host, port, username, auth, password, pinToFavorites, useMosh, forwardId, script, terminalProfileId ->
                var savedHostId: String? = null
                if (pinToFavorites) {
                    val pinnedId = UUID.randomUUID().toString()
                    savedHostId = pinnedId
                    onHostAdd(
                        host,
                        host,
                        port,
                        username,
                        auth,
                        null,
                        "",
                        ConnectionMode.SSH,
                        useMosh,
                        forwardId,
                        script,
                        BackgroundBehavior.INHERIT,
                        terminalProfileId,
                        password,
                        pinnedId
                    )
                    pendingFavoriteHostId.value = pinnedId
                }
                quickConnectRequest.value = QuickConnectRequest(
                    sessionId = savedHostId ?: "quick-${UUID.randomUUID()}",
                    name = host,
                    host = host,
                    port = port,
                    username = username,
                    auth = auth,
                    password = password,
                    mode = ConnectionMode.SSH,
                    savedHostId = savedHostId,
                    useMosh = useMosh,
                    forwardId = forwardId,
                    script = script,
                    terminalProfileId = terminalProfileId
                )
                quickConnectRequest.value?.let { request ->
                    pendingConnectingNavigation.value = true
                    quickConnectState.value = QuickConnectUiState(
                        phase = QuickConnectPhase.CONNECTING,
                        message = "Connecting to ${request.host}:${request.port}..."
                    )
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = request.sessionId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Connecting as ${request.username} (${request.auth})"
                        )
                    )
                    if (request.useMosh) {
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = request.sessionId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = "Mosh requested. Using SSH fallback in this MVP build."
                            )
                        )
                    }
                    request.forwardId?.let { selectedForward ->
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = request.sessionId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = "Selected forward: $selectedForward"
                            )
                        )
                    }
                    onStartSession(
                        quickConnectHost(request),
                        request.mode,
                        request.password
                    )
                }
                showQuickConnect.value = false
                scope.launch {
                    drawerState.close()
                    navController.navigate(Routes.CONNECTING)
                }
            }
        )
    }

    val hostKeyPrompt = hostKeyPrompts.firstOrNull()
    val passwordPrompt = if (hostKeyPrompt == null) passwordPrompts.firstOrNull() else null
    hostKeyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(if (prompt.keyChanged) "Host Key Changed" else "Trust Host Key?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prompt.keyChanged) {
                        Text(
                            "WARNING: The host key for ${prompt.host}:${prompt.port} changed. " +
                                "Only continue if you trust this change."
                        )
                    } else {
                        Text("Do you want to trust this host key for ${prompt.host}:${prompt.port}?")
                    }
                    Text("Fingerprint: ${prompt.fingerprint}")
                }
            },
            confirmButton = {
                Button(onClick = { onRespondToHostKeyPrompt(prompt.id, true) }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!prompt.keyChanged) {
                        TextButton(
                            onClick = {
                                onAutoTrustHostKeyToggle(true)
                                onRespondToHostKeyPrompt(prompt.id, true)
                            }
                        ) {
                            Text("Yes (Don't Ask Again)")
                        }
                    }
                    TextButton(onClick = { onRespondToHostKeyPrompt(prompt.id, false) }) {
                        Text("No")
                    }
                }
            }
        )
    }
    val promptPassword = remember(passwordPrompt?.id) { mutableStateOf("") }
    val promptSavePassword = remember(passwordPrompt?.id) { mutableStateOf(false) }
    val passwordFocusRequester = remember(passwordPrompt?.id) { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(passwordPrompt?.id) {
        if (passwordPrompt != null) {
            passwordFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    passwordPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Password Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prompt.reason)
                    Text("${prompt.username}@${prompt.host}:${prompt.port}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = promptPassword.value,
                        onValueChange = { promptPassword.value = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (promptPassword.value.isNotBlank()) {
                                    onRespondToPasswordPrompt(
                                        prompt.id,
                                        promptPassword.value,
                                        prompt.allowSave && promptSavePassword.value
                                    )
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester)
                    )
                    if (prompt.allowSave) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = promptSavePassword.value,
                                onCheckedChange = { promptSavePassword.value = it }
                            )
                            Text("Save password for this host")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = promptPassword.value.isNotBlank(),
                    onClick = {
                        onRespondToPasswordPrompt(
                            prompt.id,
                            promptPassword.value,
                            prompt.allowSave && promptSavePassword.value
                        )
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { onRespondToPasswordPrompt(prompt.id, null, false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAbout.value && !uiState.isLocked) {
        AboutDialog(onDismiss = { showAbout.value = false })
    }
}

private fun quickConnectHost(request: QuickConnectRequest): HostConnection =
    HostConnection(
        id = request.savedHostId ?: request.sessionId,
        name = request.name,
        host = request.host,
        port = request.port,
        username = request.username,
        preferredAuth = request.auth,
        defaultMode = request.mode,
        useMosh = request.useMosh,
        preferredForwardId = request.forwardId,
        startupScript = request.script,
        terminalProfileId = request.terminalProfileId
    )

private class MaskPasswordWithTailReveal(
    private val revealedIndex: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = buildString(text.length) {
            text.text.forEachIndexed { index, char ->
                append(if (index == revealedIndex) char else '\u2022')
            }
        }
        return TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickConnectSheet(
    onDismiss: () -> Unit,
    portForwards: List<PortForward>,
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    onConnect: (String, Int, String, AuthMethod, String, Boolean, Boolean, String?, String, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetMaxHeight = rememberBottomSheetMaxHeight()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val host = remember { mutableStateOf("") }
        val port = remember { mutableStateOf("22") }
        val username = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val revealPasswordIndex = remember { mutableStateOf(-1) }
        val auth = remember { mutableStateOf(AuthMethod.PASSWORD) }
        val pinToFavorites = remember { mutableStateOf(false) }
        val useMosh = remember { mutableStateOf(false) }
        val selectedForwardId = remember { mutableStateOf<String?>(null) }
        val selectedTerminalProfileId = remember { mutableStateOf<String?>(null) }
        val terminalProfileExpanded = remember { mutableStateOf(false) }
        val script = remember { mutableStateOf("") }
        val hostHistory = rememberSaveable { mutableStateOf(listOf<String>()) }
        val userHistory = rememberSaveable { mutableStateOf(listOf<String>()) }
        val status = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(password.value) {
            if (password.value.isEmpty()) {
                revealPasswordIndex.value = -1
                return@LaunchedEffect
            }
            revealPasswordIndex.value = password.value.lastIndex
            delay(700)
            if (revealPasswordIndex.value == password.value.lastIndex) {
                revealPasswordIndex.value = -1
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .verticalScroll(rememberScrollState())
                .imePadding()
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
                visualTransformation = MaskPasswordWithTailReveal(revealPasswordIndex.value),
                modifier = Modifier.fillMaxWidth()
            )
            if (hostHistory.value.isNotEmpty()) {
                Text("Last used hosts", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hostHistory.value.forEach { recent ->
                        OutlinedButton(onClick = { host.value = recent }) {
                            Text(recent)
                        }
                    }
                }
            }
            if (userHistory.value.isNotEmpty()) {
                Text("Last used users", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    userHistory.value.forEach { recent ->
                        OutlinedButton(onClick = { username.value = recent }) {
                            Text(recent)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthChoice("Password", AuthMethod.PASSWORD, auth.value) { auth.value = it }
                AuthChoice("Identity", AuthMethod.IDENTITY, auth.value) { auth.value = it }
                AuthChoice("Both", AuthMethod.PASSWORD_AND_IDENTITY, auth.value) { auth.value = it }
            }
            if (portForwards.isNotEmpty()) {
                Text("Forwarded port", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selectedForwardId.value = null }) {
                        Text("None")
                    }
                    portForwards.forEach { forward ->
                        val selected = selectedForwardId.value == forward.id
                        OutlinedButton(onClick = { selectedForwardId.value = forward.id }) {
                            Text(if (selected) "✓ ${forward.label}" else forward.label)
                        }
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = terminalProfileExpanded.value,
                onExpandedChange = { terminalProfileExpanded.value = !terminalProfileExpanded.value }
            ) {
                val effectiveProfileId = selectedTerminalProfileId.value ?: defaultTerminalProfileId
                TextField(
                    value = terminalProfiles.firstOrNull { it.id == effectiveProfileId }?.name ?: "App default",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Terminal profile") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = terminalProfileExpanded.value)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = terminalProfileExpanded.value,
                    onDismissRequest = { terminalProfileExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("App default") },
                        onClick = {
                            selectedTerminalProfileId.value = null
                            terminalProfileExpanded.value = false
                        }
                    )
                    terminalProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                selectedTerminalProfileId.value = profile.id
                                terminalProfileExpanded.value = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = script.value,
                onValueChange = { script.value = it },
                label = { Text("Optional script") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use Mosh")
                androidx.compose.material3.Switch(
                    checked = useMosh.value,
                    onCheckedChange = { useMosh.value = it }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pin to Favorites")
                androidx.compose.material3.Switch(
                    checked = pinToFavorites.value,
                    onCheckedChange = { pinToFavorites.value = it }
                )
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
                    hostHistory.value = (listOf(hostValue) + hostHistory.value.filterNot { it.equals(hostValue, true) }).take(5)
                    userHistory.value = (listOf(userValue) + userHistory.value.filterNot { it.equals(userValue, true) }).take(5)
                    onConnect(
                        hostValue,
                        portValue,
                        userValue,
                        auth.value,
                        password.value,
                        pinToFavorites.value,
                        useMosh.value,
                        selectedForwardId.value,
                        script.value,
                        selectedTerminalProfileId.value
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val makerLogo = if (isSystemInDarkTheme()) {
        R.drawable.major_daft_apps_white
    } else {
        R.drawable.major_daft_apps_black
    }
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
                val appVersion = stringResource(id = R.string.app_version)
                Text(
                    stringResource(id = R.string.about_version, appVersion),
                    style = MaterialTheme.typography.titleMedium
                )
                val website = stringResource(id = R.string.project_website)
                val supportUrl = stringResource(id = R.string.support_url)
                val privacy = stringResource(id = R.string.privacy_policy_url)
                val context = LocalContext.current
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Website:")
                    Text(
                        website,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(website))
                            context.startActivity(intent)
                        }
                    )
                }
                Text("License: Apache-2.0 (draft)")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Support:")
                    Text(
                        supportUrl,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
                            context.startActivity(intent)
                        }
                    )
                }
                Text(
                    "Privacy Policy",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacy))
                        context.startActivity(intent)
                    }
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(id = makerLogo),
                        contentDescription = "Major Daft Apps logo",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text("Made by Major Daft Apps", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}

private fun buildExportPayload(state: AppUiState): String {
    val payload = JSONObject().apply {
        put("v", 1)
        put("exportedAt", System.currentTimeMillis())
        put("hosts", JSONArray().apply {
            state.hosts.forEach { host ->
                put(JSONObject().apply {
                    put("id", host.id)
                    put("name", host.name)
                    put("host", host.host)
                    put("port", host.port)
                    put("username", host.username)
                    put("auth", host.preferredAuth.name)
                    put("group", host.group)
                    put("notes", host.notes)
                    put("defaultMode", host.defaultMode.name)
                    put("hasPassword", host.hasPassword)
                    put("terminalProfileId", host.terminalProfileId ?: "")
                })
            }
        })
        if (state.includeIdentitiesInQr) {
            put("identities", JSONArray().apply {
                state.identities.forEach { identity ->
                    put(JSONObject().apply {
                        put("id", identity.id)
                        put("label", identity.label)
                        put("fingerprint", identity.fingerprint)
                        put("username", identity.username)
                        put("hasPrivateKey", identity.hasPrivateKey)
                    })
                }
            })
        }
        if (state.includeSettingsInQr) {
            put("settings", JSONObject().apply {
                put("themeMode", state.themeMode.name)
                put("allowBackgroundSessions", state.allowBackgroundSessions)
                put("biometricLockEnabled", state.biometricLockEnabled)
                put("lockTimeout", state.lockTimeout.name)
                put("customLockTimeoutMinutes", state.customLockTimeoutMinutes)
                put("autoStartForwards", state.autoStartForwards)
                put("hostKeyPromptEnabled", state.hostKeyPromptEnabled)
                put("autoTrustHostKey", state.autoTrustHostKey)
            })
        }
        put("portForwards", JSONArray().apply {
            state.portForwards.forEach { forward ->
                put(JSONObject().apply {
                    put("id", forward.id)
                    put("label", forward.label)
                    put("type", forward.type.name)
                    put("sourceHost", forward.sourceHost)
                    put("sourcePort", forward.sourcePort)
                    put("destinationHost", forward.destinationHost)
                    put("destinationPort", forward.destinationPort)
                    put("associatedHosts", JSONArray(forward.associatedHosts))
                    put("enabled", forward.enabled)
                })
            }
        })
    }.toString()
    return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

