package com.majordaftapps.sshpeaches.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.components.AppDrawer
import com.majordaftapps.sshpeaches.app.ui.components.AuthChoice
import com.majordaftapps.sshpeaches.app.ui.components.LockScreenOverlay
import com.majordaftapps.sshpeaches.app.ui.navigation.Routes
import com.majordaftapps.sshpeaches.app.ui.navigation.drawerDestinations
import com.majordaftapps.sshpeaches.app.ui.screens.ConnectingScreen
import com.majordaftapps.sshpeaches.app.ui.screens.FavoritesScreen
import com.majordaftapps.sshpeaches.app.ui.screens.HostsScreen
import com.majordaftapps.sshpeaches.app.ui.screens.IdentitiesScreen
import com.majordaftapps.sshpeaches.app.ui.screens.KeyboardEditorScreen
import com.majordaftapps.sshpeaches.app.ui.screens.OpenSourceLicensesScreen
import com.majordaftapps.sshpeaches.app.ui.screens.PortForwardScreen
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectPhase
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectRequest
import com.majordaftapps.sshpeaches.app.ui.screens.QuickConnectUiState
import com.majordaftapps.sshpeaches.app.ui.screens.SettingsScreen
import com.majordaftapps.sshpeaches.app.ui.screens.SnippetEditorScreen
import com.majordaftapps.sshpeaches.app.ui.screens.SnippetManagerScreen
import com.majordaftapps.sshpeaches.app.ui.screens.ThemeEditorScreen
import com.majordaftapps.sshpeaches.app.ui.screens.ThemeProfileEditorScreen
import com.majordaftapps.sshpeaches.app.ui.state.AppUiState
import com.majordaftapps.sshpeaches.app.ui.state.LockTimeout
import com.majordaftapps.sshpeaches.app.ui.state.SortMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.util.rememberBottomSheetMaxHeight
import com.majordaftapps.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService.HostKeyPrompt
import com.majordaftapps.sshpeaches.app.service.SessionService.PasswordPrompt
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionSnapshot
import com.majordaftapps.sshpeaches.app.R
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
    onTerminalEmulationChange: (com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation) -> Unit,
    onTerminalSelectionModeChange: (TerminalSelectionMode) -> Unit,
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
    onHostAdd: (String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String?, String, BackgroundBehavior, String?, String?, String?) -> Unit,
    onHostUpdate: (String, String, String, Int, String, AuthMethod, String?, String, ConnectionMode, Boolean, String?, String?, String, BackgroundBehavior, String?, String?) -> Unit,
    onHostDelete: (String) -> Unit,
    onHostInfoCommandsChange: (String, List<String>) -> Unit,
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
    onKeyboardSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onKeyboardReset: () -> Unit,
    onSnippetAdd: (String, String, String) -> Unit,
    onSnippetUpdate: (String, String, String, String) -> Unit,
    onSnippetDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSendSessionShortcut: (String, String) -> Unit,
    onSendShellBytes: (String, ByteArray) -> Unit,
    onResizeShell: (String, Int, Int) -> Unit,
    onListSftpDirectory: (String, String) -> Unit,
    onSftpDownloadFile: (String, String, String?) -> Unit,
    onSftpUploadFile: (String, String, String) -> Unit,
    onManageRemotePath: (String, String, String, String?) -> Unit,
    onScpDownloadFile: (String, String, String?) -> Unit,
    onScpUploadFile: (String, String, String) -> Unit,
    resolveTerminalEmulator: (String) -> com.termux.terminal.TerminalEmulator?,
    sessions: List<SessionSnapshot>,
    shellOutputs: Map<String, String>,
    remoteDirectories: Map<String, com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectorySnapshot>,
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
    val routeBeforeConnecting = rememberSaveable { mutableStateOf(Routes.FAVORITES) }
    val sawSnapshotForCurrentRequest = remember { mutableStateOf(false) }
    val pendingFavoriteHostId = remember { mutableStateOf<String?>(null) }
    val editMode = rememberSaveable { mutableStateOf(false) }
    val autoResumeHandled = rememberSaveable { mutableStateOf(false) }
    val emptyStateByRoute = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val helpUrl = context.getString(R.string.support_url)
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route ?: Routes.FAVORITES
    val editSupportedRoutes = remember {
        setOf(
            Routes.HOSTS,
            Routes.IDENTITIES,
            Routes.FORWARDS,
            Routes.SNIPPETS
        )
    }
    val favoritesEmpty = uiState.favorites.hostFavorites.isEmpty() &&
        uiState.favorites.identityFavorites.isEmpty() &&
        uiState.favorites.portFavorites.isEmpty()
    val routeEmptyStateVisible = when (currentRoute) {
        Routes.FAVORITES -> emptyStateByRoute[Routes.FAVORITES] ?: favoritesEmpty
        Routes.HOSTS -> uiState.hosts.isEmpty()
        Routes.IDENTITIES -> emptyStateByRoute[Routes.IDENTITIES] ?: uiState.identities.isEmpty()
        Routes.FORWARDS -> emptyStateByRoute[Routes.FORWARDS] ?: uiState.portForwards.isEmpty()
        Routes.SNIPPETS -> emptyStateByRoute[Routes.SNIPPETS] ?: uiState.snippets.isEmpty()
        else -> false
    }
    val showEditAction = currentRoute in editSupportedRoutes && !routeEmptyStateVisible

    fun quickStateFromSnapshot(
        snapshot: com.majordaftapps.sshpeaches.app.service.SessionService.SessionSnapshot,
        host: HostConnection
    ): QuickConnectUiState = when (snapshot.status) {
        com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.CONNECTING -> {
            QuickConnectUiState(
                phase = QuickConnectPhase.CONNECTING,
                message = snapshot.statusMessage ?: "Connecting to ${host.host}:${host.port}..."
            )
        }

        com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE -> {
            QuickConnectUiState(
                phase = QuickConnectPhase.SUCCESS,
                message = snapshot.statusMessage ?: "Connected successfully"
            )
        }

        com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ERROR -> {
            QuickConnectUiState(
                phase = QuickConnectPhase.ERROR,
                message = snapshot.statusMessage ?: "Connection failed"
            )
        }
    }

    fun quickRequestFromSnapshot(
        snapshot: com.majordaftapps.sshpeaches.app.service.SessionService.SessionSnapshot
    ): QuickConnectRequest {
        val host = snapshot.host
        val savedHostId = uiState.hosts.firstOrNull { it.id == host.id }?.id
        return QuickConnectRequest(
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
            preferredIdentityId = host.preferredIdentityId,
            forwardId = host.preferredForwardId,
            script = host.startupScript,
            terminalProfileId = host.terminalProfileId
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    val activeSessionRequest = quickConnectRequest.value
    val currentTitle = when (currentRoute) {
        Routes.FAVORITES -> "Favorites"
        Routes.CONNECTING -> activeSessionRequest?.name?.ifBlank {
            activeSessionRequest.host
        } ?: "Connecting"
        Routes.HOSTS -> "Hosts"
        Routes.IDENTITIES -> "Identities"
        Routes.FORWARDS -> "Port Forwards"
        Routes.SNIPPETS -> "Snippets"
        Routes.SNIPPET_EDITOR_ROUTE -> "Snippet Editor"
        Routes.KEYBOARD -> "Keyboard"
        Routes.THEME_EDITOR -> "Theme Editor"
        Routes.THEME_EDITOR_EDIT_ROUTE -> "Edit Terminal Theme"
        Routes.SETTINGS -> "Settings"
        Routes.OPEN_SOURCE_LICENSES -> "Open Source Licenses"
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

    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.CONNECTING) {
            routeBeforeConnecting.value = currentRoute
        }
        if (!showEditAction && editMode.value) {
            editMode.value = false
        }
    }

    LaunchedEffect(requestedOpenSessionId, sessions, currentRoute) {
        val targetHostId = requestedOpenSessionId ?: return@LaunchedEffect
        val snapshot = sessions.firstOrNull { it.hostId == targetHostId } ?: return@LaunchedEffect
        val host = snapshot.host
        quickConnectRequest.value = quickRequestFromSnapshot(snapshot)
        quickConnectState.value = quickStateFromSnapshot(snapshot, host)
        pendingConnectingNavigation.value = false
        autoResumeHandled.value = true
        if (currentRoute != Routes.CONNECTING) {
            navController.navigate(Routes.CONNECTING) {
                popUpTo(Routes.FAVORITES)
            }
        }
        onOpenSessionRequestHandled()
    }

    LaunchedEffect(quickConnectRequest.value?.sessionId) {
        sawSnapshotForCurrentRequest.value = false
    }

    LaunchedEffect(quickConnectRequest.value?.sessionId, sessions, currentRoute) {
        val request = quickConnectRequest.value ?: return@LaunchedEffect
        val snapshot = sessions.firstOrNull { it.hostId == request.sessionId }
        when (snapshot?.status) {
            null -> {
                if (sawSnapshotForCurrentRequest.value) {
                    pendingConnectingNavigation.value = false
                    quickConnectRequest.value = null
                    quickConnectState.value = QuickConnectUiState()
                    if (currentRoute == Routes.CONNECTING) {
                        val destination = routeBeforeConnecting.value
                            .takeIf { it != Routes.CONNECTING }
                            ?: Routes.FAVORITES
                        navController.navigate(destination) {
                            popUpTo(Routes.FAVORITES)
                        }
                    }
                } else {
                    quickConnectState.value = QuickConnectUiState(
                        phase = QuickConnectPhase.CONNECTING,
                        message = "Connecting to ${request.host}:${request.port}..."
                    )
                }
            }

            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.CONNECTING -> {
                sawSnapshotForCurrentRequest.value = true
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.CONNECTING,
                    message = snapshot.statusMessage ?: "Connecting to ${request.host}:${request.port}..."
                )
            }

            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE -> {
                sawSnapshotForCurrentRequest.value = true
                quickConnectState.value = QuickConnectUiState(
                    phase = QuickConnectPhase.SUCCESS,
                    message = snapshot.statusMessage ?: "Connected successfully"
                )
            }

            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ERROR -> {
                sawSnapshotForCurrentRequest.value = true
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

    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            autoResumeHandled.value = false
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

    fun closeCurrentConnectingSession() {
        val request = quickConnectRequest.value
        if (request != null) {
            onStopSession(request.sessionId)
        }
        pendingConnectingNavigation.value = false
        val destination = routeBeforeConnecting.value
            .takeIf { it != Routes.CONNECTING }
            ?: Routes.FAVORITES
        navController.navigate(destination) {
            popUpTo(Routes.FAVORITES)
        }
        quickConnectRequest.value = null
        quickConnectState.value = QuickConnectUiState()
    }

    Box {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
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
                                    val helpUri = Uri.parse(helpUrl)
                                    val customTab = CustomTabsIntent.Builder()
                                        .setShowTitle(true)
                                        .build()
                                    runCatching {
                                        customTab.launchUrl(context, helpUri)
                                    }.onFailure {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, helpUri))
                                    }
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
                        TopAppBar(
                            title = { Text(currentTitle) },
                            navigationIcon = {
                                if (currentRoute != Routes.CONNECTING) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            },
                            actions = {
                                if (currentRoute == Routes.CONNECTING) {
                                    IconButton(onClick = { closeCurrentConnectingSession() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close session"
                                        )
                                    }
                                } else if (uiState.pinConfigured) {
                                    IconButton(
                                        onClick = onLockApp,
                                        enabled = !uiState.isLocked
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock app"
                                        )
                                    }
                                }
                                if (showEditAction) {
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
                            FavoritesScreen(
                                section = uiState.favorites,
                                onToggleFavorite = onToggleFavorite,
                                onEmptyStateVisibleChanged = { emptyStateByRoute[Routes.FAVORITES] = it }
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
                            val remoteDirectory = request?.let { current ->
                                remoteDirectories[current.sessionId]
                            }
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
                                remoteDirectory = remoteDirectory,
                                terminalProfile = activeTerminalProfile,
                                terminalSelectionMode = uiState.terminalSelectionMode,
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
                                onSftpListDirectory = { path ->
                                    request?.let { current ->
                                        onListSftpDirectory(current.sessionId, path)
                                    }
                                },
                                onSftpDownload = { remotePath, localPath ->
                                    request?.let { current ->
                                        onSftpDownloadFile(current.sessionId, remotePath, localPath)
                                    }
                                },
                                onSftpUpload = { localPath, remotePath ->
                                    request?.let { current ->
                                        onSftpUploadFile(current.sessionId, localPath, remotePath)
                                    }
                                },
                                onScpDownload = { remotePath, localPath ->
                                    request?.let { current ->
                                        onScpDownloadFile(current.sessionId, remotePath, localPath)
                                    }
                                },
                                onScpUpload = { localPath, remotePath ->
                                    request?.let { current ->
                                        onScpUploadFile(current.sessionId, localPath, remotePath)
                                    }
                                },
                                onManageRemotePath = { operation, sourcePath, destinationPath ->
                                    request?.let { current ->
                                        onManageRemotePath(current.sessionId, operation, sourcePath, destinationPath)
                                    }
                                },
                                resolveTerminalEmulator = resolveTerminalEmulator,
                                onClose = {
                                    closeCurrentConnectingSession()
                                },
                                onRetry = {
                                    quickConnectRequest.value?.let { current ->
                                        onStopSession(current.sessionId)
                                        val next = if (current.savedHostId == null) {
                                            current.copy(
                                                sessionId = sessionIdFor("quick-${UUID.randomUUID()}", current.mode)
                                            )
                                        } else {
                                            current.copy(
                                                sessionId = sessionIdFor(current.savedHostId, current.mode)
                                            )
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
                        val activeSshSessionHostIds = sessions
                            .filter {
                                it.status == com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE &&
                                    it.mode == ConnectionMode.SSH
                            }
                            .map { it.host.id }
                            .toSet()
                        HostsScreen(
                            hosts = uiState.hosts,
                            identities = uiState.identities,
                            portForwards = uiState.portForwards,
                            snippets = uiState.snippets,
                            terminalProfiles = uiState.terminalProfiles,
                            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
                            sortMode = uiState.sortMode,
                            onSortModeChange = onSortModeChange,
                            editMode = editMode.value,
                            canStoreCredentials = !uiState.isLocked,
                            onImportFromQr = { showMessage("Host imported from QR") },
                            onToggleFavorite = onToggleFavorite,
                            onAdd = onHostAdd,
                            onUpdate = onHostUpdate,
                            onDeleteHost = onHostDelete,
                            onStartSession = { host, mode, password ->
                                pendingConnectingNavigation.value = true
                                quickConnectRequest.value = QuickConnectRequest(
                                    sessionId = sessionIdFor(host.id, mode),
                                    name = host.name,
                                    host = host.host,
                                    port = host.port,
                                    username = host.username,
                                    auth = host.preferredAuth,
                                    password = password ?: "",
                                    mode = mode,
                                    savedHostId = host.id,
                                    useMosh = host.useMosh,
                                    preferredIdentityId = host.preferredIdentityId,
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
                            onStopSession = onStopSession,
                            activeSshSessionHostIds = activeSshSessionHostIds,
                            openSessions = sessions,
                            onOpenSession = { sessionId ->
                                sessions.firstOrNull { it.hostId == sessionId }?.let { snapshot ->
                                    quickConnectRequest.value = quickRequestFromSnapshot(snapshot)
                                    quickConnectState.value = quickStateFromSnapshot(snapshot, snapshot.host)
                                    pendingConnectingNavigation.value = false
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Routes.CONNECTING)
                                    }
                                }
                            },
                            onDisconnectSession = { sessionId ->
                                onStopSession(sessionId)
                                if (quickConnectRequest.value?.sessionId == sessionId) {
                                    quickConnectRequest.value = null
                                    quickConnectState.value = QuickConnectUiState()
                                }
                            },
                            onRunInfoCommand = { host, command ->
                                val activeSshSession = sessions.firstOrNull {
                                    it.host.id == host.id &&
                                        it.status == com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE &&
                                        it.mode == ConnectionMode.SSH
                                }
                                if (activeSshSession != null) {
                                    onSendSessionShortcut(activeSshSession.hostId, command)
                                    showMessage("Ran command on ${host.name}")
                                    true
                                } else {
                                    showMessage("No active SSH session for ${host.name}")
                                    false
                                }
                            },
                            onInfoCommandsChange = { host, commands ->
                                onHostInfoCommandsChange(host.id, commands)
                            }
                        )
                    }
                    composable(Routes.IDENTITIES) {
                        IdentitiesScreen(
                            items = uiState.identities,
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
                            onImportFromQr = { showMessage("Identity imported from QR") },
                            onEmptyStateVisibleChanged = { emptyStateByRoute[Routes.IDENTITIES] = it }
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
                                onImportFromQr = { showMessage("Port forward imported from QR") },
                                onEmptyStateVisibleChanged = { emptyStateByRoute[Routes.FORWARDS] = it }
                            )
                        }
                        composable(Routes.SNIPPETS) {
                            SnippetManagerScreen(
                                snippets = uiState.snippets,
                                onAdd = onSnippetAdd,
                                onCreateSnippet = {
                                    navController.navigate(Routes.snippetEditor())
                                },
                                onEditSnippet = { snippetId ->
                                    navController.navigate(Routes.snippetEditor(snippetId))
                                },
                                onDelete = onSnippetDelete,
                                onImportFromQr = { showMessage("Snippet imported from QR") },
                                onEmptyStateVisibleChanged = { emptyStateByRoute[Routes.SNIPPETS] = it },
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
                        composable(
                            route = Routes.SNIPPET_EDITOR_ROUTE,
                            arguments = listOf(
                                navArgument("snippetId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val snippetId = backStackEntry.arguments?.getString("snippetId").orEmpty().ifBlank { null }
                            val initialSnippet = uiState.snippets.firstOrNull { it.id == snippetId }
                            SnippetEditorScreen(
                                initialSnippet = initialSnippet,
                                onSave = { title, description, command ->
                                    if (initialSnippet == null) {
                                        onSnippetAdd(title, description, command)
                                    } else {
                                        onSnippetUpdate(initialSnippet.id, title, description, command)
                                    }
                                },
                                onDelete = initialSnippet?.let {
                                    {
                                        onSnippetDelete(it.id)
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() },
                                onShowMessage = showMessage
                            )
                        }
                    composable(Routes.KEYBOARD) {
                        KeyboardEditorScreen(
                            slots = uiState.keyboardSlots,
                            onSlotChange = onKeyboardSlotChange,
                            onReset = onKeyboardReset
                        )
                    }
                    composable(Routes.THEME_EDITOR) {
                        ThemeEditorScreen(
                            terminalProfiles = uiState.terminalProfiles,
                            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
                            onDefaultTerminalProfileChange = onDefaultTerminalProfileChange,
                            onDeleteTerminalProfile = onDeleteTerminalProfile,
                            onCreateTheme = {
                                navController.navigate(Routes.themeEditorEdit())
                            },
                            onEditTheme = { profileId ->
                                navController.navigate(Routes.themeEditorEdit(profileId = profileId))
                            },
                            onDuplicateTheme = { profileId ->
                                navController.navigate(Routes.themeEditorEdit(profileId = profileId, duplicate = true))
                            },
                            onShowMessage = showMessage
                        )
                    }
                    composable(
                        route = Routes.THEME_EDITOR_EDIT_ROUTE,
                        arguments = listOf(
                            navArgument("profileId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("duplicate") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val profileId = backStackEntry.arguments?.getString("profileId").orEmpty().ifBlank { null }
                        val duplicate = backStackEntry.arguments?.getBoolean("duplicate") ?: false
                        val selected = uiState.terminalProfiles.firstOrNull { it.id == profileId }
                        val builtInIds = TerminalProfileDefaults.builtInProfiles.map { it.id }.toSet()
                        val initialProfile = remember(profileId, duplicate, uiState.terminalProfiles) {
                            if (selected == null) {
                                TerminalProfileDefaults.customTemplate(name = "Custom Theme")
                            } else {
                                val shouldDuplicate = duplicate || builtInIds.contains(selected.id)
                                if (shouldDuplicate) {
                                    selected.copy(
                                        id = "custom-${UUID.randomUUID()}",
                                        name = "${selected.name} Copy"
                                    )
                                } else {
                                    selected
                                }
                            }
                        }
                        val isEditingExisting = selected != null && !duplicate && !builtInIds.contains(selected.id)
                        ThemeProfileEditorScreen(
                            initialProfile = initialProfile,
                            existingProfiles = uiState.terminalProfiles,
                            isEditingExisting = isEditingExisting,
                            onSaveTheme = onSaveTerminalProfile,
                            onNavigateBack = { navController.popBackStack() },
                            onShowMessage = showMessage
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
                                terminalSelectionMode = uiState.terminalSelectionMode,
                                onTerminalSelectionModeChange = onTerminalSelectionModeChange,
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
                                onUnlockWithPin = onUnlockWithPin,
                                onGenerateExportPayload = { buildExportPayload(uiState) },
                                onShowMessage = showMessage
                            )
                        }
                        composable(Routes.OPEN_SOURCE_LICENSES) {
                            OpenSourceLicensesScreen()
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
            identities = uiState.identities,
            terminalProfiles = uiState.terminalProfiles,
            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
            onConnect = { host, port, username, auth, password, pinToFavorites, useMosh, preferredIdentityId, forwardId, script, terminalProfileId ->
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
                        preferredIdentityId,
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
                    sessionId = sessionIdFor(
                        savedHostId ?: "quick-${UUID.randomUUID()}",
                        ConnectionMode.SSH
                    ),
                    name = host,
                    host = host,
                    port = port,
                    username = username,
                    auth = auth,
                    password = password,
                    mode = ConnectionMode.SSH,
                    savedHostId = savedHostId,
                    useMosh = useMosh,
                    preferredIdentityId = preferredIdentityId,
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
                                message = "Mosh transport requested."
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
        AboutDialog(
            onDismiss = { showAbout.value = false },
            onOpenSourceLicenses = {
                showAbout.value = false
                navController.navigate(Routes.OPEN_SOURCE_LICENSES)
            }
        )
    }
}

private fun sessionIdFor(hostId: String, mode: ConnectionMode): String =
    "$hostId|${mode.name}"

private fun baseHostIdFromSessionId(sessionId: String): String =
    sessionId.substringBefore('|')

private fun quickConnectHost(request: QuickConnectRequest): HostConnection =
    HostConnection(
        id = request.savedHostId ?: baseHostIdFromSessionId(request.sessionId),
        name = request.name,
        host = request.host,
        port = request.port,
        username = request.username,
        preferredAuth = request.auth,
        defaultMode = request.mode,
        useMosh = request.useMosh,
        preferredIdentityId = request.preferredIdentityId,
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
    identities: List<Identity>,
    terminalProfiles: List<TerminalProfile>,
    defaultTerminalProfileId: String,
    onConnect: (String, Int, String, AuthMethod, String, Boolean, Boolean, String?, String?, String, String?) -> Unit
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
        val selectedIdentityId = remember { mutableStateOf<String?>(null) }
        val identityExpanded = remember { mutableStateOf(false) }
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
            val identitiesWithKeys = identities.filter { it.hasPrivateKey }
            ExposedDropdownMenuBox(
                expanded = identityExpanded.value,
                onExpandedChange = { identityExpanded.value = !identityExpanded.value }
            ) {
                val selectedIdentity = identitiesWithKeys.firstOrNull { it.id == selectedIdentityId.value }
                TextField(
                    value = selectedIdentity?.label ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Identity key") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = identityExpanded.value) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = identityExpanded.value,
                    onDismissRequest = { identityExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedIdentityId.value = null
                            identityExpanded.value = false
                        }
                    )
                    identitiesWithKeys.forEach { identity ->
                        DropdownMenuItem(
                            text = { Text(identity.label) },
                            onClick = {
                                selectedIdentityId.value = identity.id
                                identityExpanded.value = false
                            }
                        )
                    }
                }
            }
            if (auth.value != AuthMethod.PASSWORD && selectedIdentityId.value == null) {
                Text(
                    "Select an identity key for identity authentication.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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
                            Text(if (selected) "[Selected] ${forward.label}" else forward.label)
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
                    val portValueRaw = port.value.trim()
                    val userValue = username.value.trim()
                    if (hostValue.isBlank() || portValueRaw.isBlank() || userValue.isBlank()) {
                        status.value = "Host, port, and username required"
                        return@Button
                    }
                    val portValue = portValueRaw.toIntOrNull()
                    if (portValue == null || portValue !in 1..65535) {
                        status.value = "Enter a valid port (1-65535)"
                        return@Button
                    }
                    if (auth.value != AuthMethod.PASSWORD && selectedIdentityId.value == null) {
                        status.value = "Select an identity key for identity authentication"
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
                        selectedIdentityId.value,
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
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenSourceLicenses: () -> Unit
) {
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
                    painter = painterResource(id = com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches),
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
                Text("License: GPL-3.0")
                Text(
                    "Open Source License Notices",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenSourceLicenses() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Support",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
                            context.startActivity(intent)
                        }
                    )
                    Text("  |  ")
                    Text(
                        "Privacy Policy",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacy))
                            context.startActivity(intent)
                        }
                    )
                }
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
