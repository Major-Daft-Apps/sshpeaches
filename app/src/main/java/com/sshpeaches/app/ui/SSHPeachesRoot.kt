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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.BackgroundBehavior
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfileDefaults
import com.majordaftapps.sshpeaches.app.data.local.Converters
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
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
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.state.AppUiState
import com.majordaftapps.sshpeaches.app.ui.state.BackgroundSessionTimeout
import com.majordaftapps.sshpeaches.app.ui.state.LockTimeout
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionRemediation
import com.majordaftapps.sshpeaches.app.ui.permissions.CorePermissionStatus
import com.majordaftapps.sshpeaches.app.ui.state.SortMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.state.ThemeMode
import com.majordaftapps.sshpeaches.app.ui.util.AutoHidePasswordReveal
import com.majordaftapps.sshpeaches.app.ui.util.TailRevealPasswordVisualTransformation
import com.majordaftapps.sshpeaches.app.ui.util.updatePasswordStateWithReveal
import com.majordaftapps.sshpeaches.app.ui.util.rememberBottomSheetMaxHeight
import com.majordaftapps.sshpeaches.app.ui.util.toSentenceCaseLabel
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService.HostKeyPrompt
import com.majordaftapps.sshpeaches.app.service.SessionService.PasswordPrompt
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionSnapshot
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.majordaftapps.sshpeaches.app.util.snippetReference
import java.util.UUID
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
    onBackgroundSessionTimeoutChange: (BackgroundSessionTimeout) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLockTimeoutChange: (LockTimeout) -> Unit,
    onCustomLockTimeoutMinutesChange: (Int) -> Unit,
    onSnippetRunTimeoutSecondsChange: (Int) -> Unit,
    onTerminalEmulationChange: (com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation) -> Unit,
    onTerminalSelectionModeChange: (TerminalSelectionMode) -> Unit,
    onCrashReportsToggle: (Boolean) -> Unit,
    onAnalyticsToggle: (Boolean) -> Unit,
    onDiagnosticsToggle: (Boolean) -> Unit,
    onIncludeSecretsInQrToggle: (Boolean) -> Unit,
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
    onImportHost: (HostConnection) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onHostOsMetadataImported: (String, OsMetadata) -> Unit,
    onHostInfoCommandsChange: (String, List<String>) -> Unit,
    onPortForwardAdd: (String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onImportPortForward: (PortForward) -> Unit,
    onPortForwardUpdate: (String, String, PortForwardType, String, Int, String, Int, Boolean, List<String>) -> Unit,
    onPortForwardDelete: (String) -> Unit,
    onStartSession: (String, HostConnection, ConnectionMode, String?) -> Unit,
    onStopSession: (String) -> Unit,
    onIdentityAdd: (String, String, String?, String?) -> Unit,
    onImportIdentity: (Identity) -> Unit,
    onIdentityUpdate: (String, String, String, String?) -> Unit,
    onIdentityDelete: (String) -> Unit,
    onImportHostPasswordPayload: (String, String, String) -> Boolean,
    onImportIdentityKey: (String, String, String) -> Boolean,
    onImportIdentityKeyPlain: (String, String) -> Boolean,
    onStoreIdentityPublicKey: (String, String) -> Boolean,
    onImportIdentityPublicKey: (String, String) -> Boolean,
    onStoreIdentityKeyPassphrase: (String, String?) -> Unit,
    onImportIdentityKeyPassphrasePayload: (String, String, String) -> Boolean,
    onCopyIdentityKeyToHost: suspend (String, String, String?, String?) -> Boolean,
    onRemoveIdentityKey: (String) -> Unit,
    onKeyboardSlotChange: (Int, KeyboardSlotAction) -> Unit,
    onImportKeyboardLayout: (List<KeyboardSlotAction>) -> Unit,
    onKeyboardReset: () -> Unit,
    onImportTerminalProfiles: (List<TerminalProfile>, String?) -> Unit,
    onSnippetAdd: (String, String, String) -> Unit,
    onImportSnippet: (Snippet) -> Unit,
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
    onRespondToPasswordPrompt: (String, String?, Boolean) -> Unit,
    corePermissions: List<CorePermissionStatus>,
    onRequestCorePermissions: () -> Unit,
    onOpenAppPermissionSettings: () -> Unit,
    requestedStartupRoute: String? = null,
    onStartupRouteHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showQuickConnect = rememberSaveable { mutableStateOf(false) }
    val showAbout = rememberSaveable { mutableStateOf(false) }
    val quickConnectRequest = rememberSaveable { mutableStateOf<QuickConnectRequest?>(null) }
    val quickConnectState = rememberSaveable { mutableStateOf(QuickConnectUiState()) }
    val pendingConnectingNavigation = rememberSaveable { mutableStateOf(false) }
    val routeBeforeConnecting = rememberSaveable { mutableStateOf(Routes.FAVORITES) }
    val sawSnapshotForCurrentRequest = rememberSaveable { mutableStateOf(false) }
    val editMode = rememberSaveable { mutableStateOf(false) }
    val autoResumeHandled = rememberSaveable { mutableStateOf(false) }
    val connectedHostBarCollapsed = rememberSaveable { mutableStateOf(false) }
    val connectingFindRequestToken = rememberSaveable { mutableIntStateOf(0) }
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
        uiState.favorites.portFavorites.isEmpty() &&
        uiState.favorites.snippetFavorites.isEmpty()
    val routeEmptyStateVisible = when (currentRoute) {
        Routes.FAVORITES -> emptyStateByRoute[Routes.FAVORITES] ?: favoritesEmpty
        Routes.HOSTS -> uiState.hosts.isEmpty()
        Routes.IDENTITIES -> emptyStateByRoute[Routes.IDENTITIES] ?: uiState.identities.isEmpty()
        Routes.FORWARDS -> emptyStateByRoute[Routes.FORWARDS] ?: uiState.portForwards.isEmpty()
        Routes.SNIPPETS -> emptyStateByRoute[Routes.SNIPPETS] ?: uiState.snippets.isEmpty()
        else -> false
    }
    val showEditAction = currentRoute in editSupportedRoutes && !routeEmptyStateVisible
    val missingCorePermissions = corePermissions.filterNot { it.granted }
    val activeSshSessions = sessions.filter {
        it.status == com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE &&
            it.mode == ConnectionMode.SSH
    }
    val snippetRunSelection = remember { mutableStateOf<Snippet?>(null) }
    val snippetRunTargetHostId = remember { mutableStateOf<String?>(null) }
    val snippetRunInProgress = remember { mutableStateOf(false) }
    val snippetRunResult = remember { mutableStateOf<SnippetRunResult?>(null) }

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

    fun navigateBackFromConnecting() {
        pendingConnectingNavigation.value = false
        val popped = navController.popBackStack()
        if (!popped) {
            val destination = routeBeforeConnecting.value
                .takeIf { it != Routes.CONNECTING }
                ?: Routes.FAVORITES
            navController.navigate(destination) {
                popUpTo(Routes.FAVORITES)
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = currentRoute == Routes.CONNECTING && !drawerState.isOpen) {
        navigateBackFromConnecting()
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
        Routes.KEYBOARD -> "Keyboard Editor"
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

    LaunchedEffect(requestedStartupRoute, backStackEntry?.destination?.route) {
        val startupRoute = requestedStartupRoute ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        if (startupRoute != currentRoute && startupRoute != Routes.CONNECTING) {
            navController.navigate(startupRoute) {
                popUpTo(Routes.FAVORITES)
                launchSingleTop = true
            }
        }
        onStartupRouteHandled()
    }

    LaunchedEffect(quickConnectRequest.value?.sessionId) {
        sawSnapshotForCurrentRequest.value = false
        connectedHostBarCollapsed.value = false
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

    fun resetSessionLogs(sessionId: String) {
        sessionLogs.removeAll { it.hostId == sessionId }
    }

    fun openOrStartSavedHostSession(
        host: HostConnection,
        mode: ConnectionMode,
        password: String?
    ) {
        val sessionId = sessionIdFor(host.id, mode)
        val existingSnapshot = sessions.firstOrNull { it.hostId == sessionId }
        if (
            existingSnapshot?.status ==
            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.CONNECTING ||
            existingSnapshot?.status ==
            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE
        ) {
            quickConnectRequest.value = quickRequestFromSnapshot(existingSnapshot)
            quickConnectState.value = quickStateFromSnapshot(existingSnapshot, existingSnapshot.host)
            pendingConnectingNavigation.value = false
        } else {
            if (existingSnapshot != null) {
                onStopSession(sessionId)
            }
            resetSessionLogs(sessionId)
            pendingConnectingNavigation.value = true
            quickConnectRequest.value = QuickConnectRequest(
                sessionId = sessionId,
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
            onStartSession(sessionId, host, mode, password)
        }
        scope.launch {
            drawerState.close()
            navController.navigate(Routes.CONNECTING)
        }
    }

    fun importTransferPayloadFromQr(encodedPayload: String, passphrase: String?): String {
        val root = decodeTransferPayload(encodedPayload) ?: return "Invalid export QR payload."

        var importedHosts = 0
        var importedIdentities = 0
        var importedForwards = 0
        var importedSnippets = 0
        var importedTerminalProfiles = 0
        var importedPasswords = 0
        var importedPrivateKeys = 0
        var importedKeyPassphrases = 0
        var protectedImportFailures = 0
        var metadataImportFailures = 0
        var settingsApplied = false
        var keyboardLayoutApplied = false

        val existingIdentityByFingerprint = uiState.identities
            .associateBy { it.fingerprint.trim() }
            .toMutableMap()
        val existingForwardByKey = uiState.portForwards
            .associateBy {
                forwardTransferKey(
                    label = it.label,
                    sourceHost = it.sourceHost,
                    sourcePort = it.sourcePort,
                    destinationHost = it.destinationHost,
                    destinationPort = it.destinationPort
                )
            }
            .toMutableMap()
        val existingSnippetByKey = uiState.snippets
            .associateBy { snippetTransferKey(it.title, it.command) }
            .toMutableMap()
        val identityIdMap = mutableMapOf<String, String>()
        val forwardIdMap = mutableMapOf<String, String>()
        val snippetIdMap = mutableMapOf<String, String>()
        val pendingHostPasswordImports = mutableListOf<Pair<String, String>>()
        val pendingIdentityPublicKeyImports = mutableListOf<Pair<String, String>>()
        val pendingIdentityKeyImports = mutableListOf<Pair<String, String>>()
        val pendingIdentityKeyPassphraseImports = mutableListOf<Pair<String, String>>()

        root.optJSONArray("identities")?.let { identities ->
            for (index in 0 until identities.length()) {
                val item = identities.optJSONObject(index) ?: continue
                val fingerprint = item.optString("fingerprint").trim()
                if (fingerprint.isBlank()) continue

                val exportedId = item.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                val existing = existingIdentityByFingerprint[fingerprint]
                val targetId = existing?.id ?: exportedId
                identityIdMap[exportedId] = targetId

                if (existing == null) {
                    val importedIdentity = Identity(
                        id = targetId,
                        label = item.optString("label").ifBlank { "Identity" },
                        fingerprint = fingerprint,
                        username = item.optString("username").trim().ifBlank { null },
                        createdEpochMillis = optNullableLong(item, "createdEpochMillis")
                            ?: System.currentTimeMillis(),
                        lastUsedEpochMillis = optNullableLong(item, "lastUsedEpochMillis"),
                        favorite = item.optBoolean("favorite", false),
                        tags = jsonStringList(item.optJSONArray("tags")),
                        notes = item.optString("notes"),
                        hasPrivateKey = false,
                        keyImportEpochMillis = null
                    )
                    onImportIdentity(importedIdentity)
                    existingIdentityByFingerprint[fingerprint] = importedIdentity
                    importedIdentities += 1
                } else if (item.optBoolean("favorite", false) && !existing.favorite) {
                    onToggleFavorite(existing.id)
                }

                item.optString("publicKey").trim().ifBlank { null }?.let {
                    pendingIdentityPublicKeyImports += targetId to it
                }
                item.optString("keyPayload").trim().ifBlank { null }?.let {
                    pendingIdentityKeyImports += targetId to it
                }
                item.optString("keyPassphrasePayload").trim().ifBlank { null }?.let {
                    pendingIdentityKeyPassphraseImports += targetId to it
                }
            }
        }

        root.optJSONArray("snippets")?.let { snippets ->
            for (index in 0 until snippets.length()) {
                val item = snippets.optJSONObject(index) ?: continue
                val title = item.optString("title").trim().ifBlank { "Imported Snippet" }
                val command = item.optString("command").trim()
                if (command.isBlank()) continue

                val exportedId = item.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                val key = snippetTransferKey(title, command)
                val existing = existingSnippetByKey[key]
                val targetId = existing?.id ?: exportedId
                snippetIdMap[exportedId] = targetId

                if (existing == null) {
                    val importedSnippet = Snippet(
                        id = targetId,
                        title = title,
                        description = item.optString("description"),
                        command = command,
                        tags = jsonStringList(item.optJSONArray("tags")),
                        autoRunHostIds = jsonStringList(item.optJSONArray("autoRunHostIds")),
                        requireConfirmation = item.optBoolean("requireConfirmation", true),
                        favorite = item.optBoolean("favorite", false)
                    )
                    onImportSnippet(importedSnippet)
                    existingSnippetByKey[key] = importedSnippet
                    importedSnippets += 1
                } else if (item.optBoolean("favorite", false) && !existing.favorite) {
                    onToggleFavorite(existing.id)
                }
            }
        }

        root.optJSONArray("portForwards")?.let { forwards ->
            for (index in 0 until forwards.length()) {
                val item = forwards.optJSONObject(index) ?: continue
                val label = item.optString("label").trim().ifBlank { "Imported Forward" }
                val sourceHost = item.optString("sourceHost").trim().ifBlank { "127.0.0.1" }
                val sourcePort = item.optInt("sourcePort", 1).coerceIn(1, 65_535)
                val destinationHost = item.optString("destinationHost").trim()
                val destinationPort = item.optInt("destinationPort", 1).coerceIn(1, 65_535)
                if (destinationHost.isBlank()) continue

                val exportedId = item.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                val key = forwardTransferKey(
                    label = label,
                    sourceHost = sourceHost,
                    sourcePort = sourcePort,
                    destinationHost = destinationHost,
                    destinationPort = destinationPort
                )
                val existing = existingForwardByKey[key]
                val targetId = existing?.id ?: exportedId
                forwardIdMap[exportedId] = targetId

                if (existing == null) {
                    val importedForward = PortForward(
                        id = targetId,
                        label = label,
                        type = runCatching {
                            PortForwardType.valueOf(item.optString("type", PortForwardType.LOCAL.name))
                        }.getOrDefault(PortForwardType.LOCAL),
                        sourceHost = sourceHost,
                        sourcePort = sourcePort,
                        destinationHost = destinationHost,
                        destinationPort = destinationPort,
                        associatedHosts = jsonStringList(item.optJSONArray("associatedHosts")),
                        favorite = item.optBoolean("favorite", false),
                        enabled = item.optBoolean("enabled", true)
                    )
                    onImportPortForward(importedForward)
                    existingForwardByKey[key] = importedForward
                    importedForwards += 1
                } else if (item.optBoolean("favorite", false) && !existing.favorite) {
                    onToggleFavorite(existing.id)
                }
            }
        }

        root.optJSONArray("hosts")?.let { hosts ->
            for (index in 0 until hosts.length()) {
                val item = hosts.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val host = item.optString("host").trim()
                val username = item.optString("username").trim()
                if (name.isBlank() || host.isBlank() || username.isBlank()) continue
                val port = item.optInt("port", 22).coerceIn(1, 65_535)
                val auth = runCatching {
                    AuthMethod.valueOf(item.optString("auth", AuthMethod.PASSWORD.name))
                }.getOrDefault(AuthMethod.PASSWORD)
                val defaultMode = runCatching {
                    ConnectionMode.valueOf(item.optString("defaultMode", ConnectionMode.SSH.name))
                }.getOrDefault(ConnectionMode.SSH)
                val targetId = item.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                val encryptedPasswordPayload = item.optString("pwdPayload").trim().ifBlank { null }
                val startupScript = item.optString("startupScript")
                val remappedStartupScript = parseSnippetReference(startupScript)
                    ?.let { snippetId -> snippetIdMap[snippetId]?.let(::snippetReference) ?: startupScript }
                    ?: startupScript
                onImportHost(
                    HostConnection(
                        id = targetId,
                        name = name,
                        host = host,
                        port = port,
                        username = username,
                        preferredAuth = auth,
                        group = item.optString("group").trim().ifBlank { null },
                        lastUsedEpochMillis = optNullableLong(item, "lastUsedEpochMillis"),
                        favorite = item.optBoolean("favorite", false),
                        osMetadata = Converters.toOsMetadata(
                            item.optString("osMetadata", Converters.fromOsMetadata(OsMetadata.Undetected))
                        ),
                        notes = item.optString("notes"),
                        defaultMode = defaultMode,
                        attachedForwards = jsonStringList(item.optJSONArray("attachedForwards"))
                            .map { forwardIdMap[it] ?: it },
                        snippets = jsonStringList(item.optJSONArray("snippets"))
                            .map { snippetIdMap[it] ?: it },
                        hasPassword = false,
                        useMosh = item.optBoolean("useMosh", false),
                        preferredIdentityId = item.optString("preferredIdentityId").trim().ifBlank { null }
                            ?.let { identityIdMap[it] ?: it },
                        preferredForwardId = item.optString("preferredForwardId").trim().ifBlank { null }
                            ?.let { forwardIdMap[it] ?: it },
                        startupScript = remappedStartupScript,
                        backgroundBehavior = runCatching {
                            BackgroundBehavior.valueOf(
                                item.optString("backgroundBehavior", BackgroundBehavior.INHERIT.name)
                            )
                        }.getOrDefault(BackgroundBehavior.INHERIT),
                        terminalProfileId = item.optString("terminalProfileId").trim().ifBlank { null },
                        infoCommands = jsonStringList(item.optJSONArray("infoCommands"))
                    )
                )
                encryptedPasswordPayload?.let { pendingHostPasswordImports += targetId to it }
                importedHosts += 1
            }
        }

        root.optJSONObject("settings")?.let { settings ->
            settingsApplied = true
            runCatching {
                ThemeMode.valueOf(settings.optString("themeMode", uiState.themeMode.name))
            }.getOrNull()?.let(onThemeModeChange)
            onBackgroundModeChange(settings.optBoolean("allowBackgroundSessions", uiState.allowBackgroundSessions))
            runCatching {
                BackgroundSessionTimeout.valueOf(
                    settings.optString("backgroundSessionTimeout", uiState.backgroundSessionTimeout.name)
                )
            }.getOrNull()?.let(onBackgroundSessionTimeoutChange)
            onBiometricToggle(settings.optBoolean("biometricLockEnabled", uiState.biometricLockEnabled))
            runCatching {
                LockTimeout.valueOf(settings.optString("lockTimeout", uiState.lockTimeout.name))
            }.getOrNull()?.let(onLockTimeoutChange)
            onCustomLockTimeoutMinutesChange(
                settings.optInt("customLockTimeoutMinutes", uiState.customLockTimeoutMinutes).coerceIn(1, 180)
            )
            runCatching {
                com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation.valueOf(
                    settings.optString("terminalEmulation", uiState.terminalEmulation.name)
                )
            }.getOrNull()?.let(onTerminalEmulationChange)
            runCatching {
                TerminalSelectionMode.valueOf(
                    settings.optString("terminalSelectionMode", uiState.terminalSelectionMode.name)
                )
            }.getOrNull()?.let(onTerminalSelectionModeChange)
            onCrashReportsToggle(settings.optBoolean("crashReportsEnabled", uiState.crashReportsEnabled))
            onAnalyticsToggle(settings.optBoolean("analyticsEnabled", uiState.analyticsEnabled))
            onDiagnosticsToggle(
                settings.optBoolean("diagnosticsLoggingEnabled", uiState.diagnosticsLoggingEnabled)
            )
            onAutoStartForwardsToggle(settings.optBoolean("autoStartForwards", uiState.autoStartForwards))
            onHostKeyPromptToggle(settings.optBoolean("hostKeyPromptEnabled", uiState.hostKeyPromptEnabled))
            onAutoTrustHostKeyToggle(settings.optBoolean("autoTrustHostKey", uiState.autoTrustHostKey))
            onUsageReportsToggle(settings.optBoolean("usageReportsEnabled", uiState.usageReportsEnabled))
            onSnippetRunTimeoutSecondsChange(
                settings.optInt("snippetRunTimeoutSeconds", uiState.snippetRunTimeoutSeconds).coerceIn(1, 60)
            )

            val importedProfiles = terminalProfilesFromJson(settings.optJSONArray("terminalProfiles"))
            val importedDefaultProfileId = settings.optString("defaultTerminalProfileId").trim().ifBlank { null }
            if (importedProfiles.isNotEmpty() || importedDefaultProfileId != null) {
                importedTerminalProfiles = importedProfiles.count {
                    it.id !in TerminalProfileDefaults.builtInProfiles.map { profile -> profile.id }.toSet()
                }
                onImportTerminalProfiles(importedProfiles, importedDefaultProfileId)
            }

            settings.optJSONArray("keyboardLayout")?.let { layout ->
                onImportKeyboardLayout(keyboardLayoutFromJson(layout))
                keyboardLayoutApplied = true
            }
        }

        pendingIdentityPublicKeyImports.forEach { (identityId, publicKey) ->
            if (!onImportIdentityPublicKey(identityId, publicKey)) {
                metadataImportFailures += 1
            }
        }

        if (!passphrase.isNullOrBlank()) {
            pendingHostPasswordImports.forEach { (hostId, payload) ->
                if (onImportHostPasswordPayload(hostId, payload, passphrase)) {
                    importedPasswords += 1
                } else {
                    protectedImportFailures += 1
                }
            }
            pendingIdentityKeyImports.forEach { (identityId, payload) ->
                if (onImportIdentityKey(identityId, payload, passphrase)) {
                    importedPrivateKeys += 1
                } else {
                    protectedImportFailures += 1
                }
            }
            pendingIdentityKeyPassphraseImports.forEach { (identityId, payload) ->
                if (onImportIdentityKeyPassphrasePayload(identityId, payload, passphrase)) {
                    importedKeyPassphrases += 1
                } else {
                    protectedImportFailures += 1
                }
            }
        } else {
            protectedImportFailures += pendingHostPasswordImports.size +
                pendingIdentityKeyImports.size +
                pendingIdentityKeyPassphraseImports.size
        }

        val parts = mutableListOf<String>()
        parts += "$importedHosts hosts"
        parts += "$importedIdentities identities"
        parts += "$importedForwards forwards"
        parts += "$importedSnippets snippets"
        if (importedTerminalProfiles > 0) parts += "$importedTerminalProfiles terminal profiles"
        if (keyboardLayoutApplied) parts += "keyboard layout applied"
        if (importedPasswords > 0) parts += "$importedPasswords passwords"
        if (importedPrivateKeys > 0) parts += "$importedPrivateKeys private keys"
        if (importedKeyPassphrases > 0) parts += "$importedKeyPassphrases key passphrases"
        if (settingsApplied) parts += "settings applied"
        val summary = "Import complete: ${parts.joinToString(", ")}."
        return buildString {
            append(summary)
            if (protectedImportFailures > 0) {
                append(' ')
                append("$protectedImportFailures protected items could not be imported. Check the export passphrase and unlock state.")
            }
            if (metadataImportFailures > 0) {
                append(' ')
                append("$metadataImportFailures identity metadata items could not be imported.")
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
                                            popUpTo(Routes.FAVORITES) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
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
                        val showConnectingTopBar =
                            quickConnectState.value.phase != QuickConnectPhase.CONNECTING &&
                                !connectedHostBarCollapsed.value
                        val showTopBar =
                            currentRoute != Routes.CONNECTING || showConnectingTopBar
                        AnimatedVisibility(
                            visible = showTopBar,
                            enter = fadeIn(animationSpec = tween(120)) + expandVertically(
                                animationSpec = tween(150),
                                expandFrom = Alignment.Top
                            ),
                            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(
                                animationSpec = tween(120),
                                shrinkTowards = Alignment.Top
                            )
                        ) {
                            TopAppBar(
                                title = { Text(currentTitle) },
                                navigationIcon = {
                                    if (currentRoute == Routes.CONNECTING) {
                                        IconButton(onClick = { navigateBackFromConnecting() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                    } else {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    }
                                },
                                actions = {
                                    if (currentRoute == Routes.CONNECTING) {
                                        if (activeSessionRequest?.mode == ConnectionMode.SSH) {
                                            IconButton(
                                                onClick = {
                                                    connectingFindRequestToken.intValue += 1
                                                },
                                                modifier = Modifier.testTag(UiTestTags.CONNECTING_FIND_BUTTON)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Find"
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { closeCurrentConnectingSession() },
                                            modifier = Modifier.testTag(UiTestTags.CONNECTING_CLOSE_BUTTON)
                                        ) {
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
                            val activeSshSessionHostIds = sessions
                                .filter {
                                    it.status == com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE &&
                                        it.mode == ConnectionMode.SSH
                                }
                                .map { it.host.id }
                                .toSet()
                            FavoritesScreen(
                                section = uiState.favorites,
                                snippets = uiState.snippets,
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
                                activeSshSessionHostIds = activeSshSessionHostIds,
                                onHostAction = { host, mode ->
                                    openOrStartSavedHostSession(host, mode, password = null)
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
                                },
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
                                snippets = uiState.snippets,
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
                                        resetSessionLogs(next.sessionId)
                                        quickConnectRequest.value = next
                                        quickConnectState.value = QuickConnectUiState(
                                            phase = QuickConnectPhase.CONNECTING,
                                            message = "Connecting to ${next.host}:${next.port}..."
                                        )
                                        onStartSession(
                                            next.sessionId,
                                            quickConnectHost(next),
                                            next.mode,
                                            next.password
                                        )
                                    }
                                },
                                onToggleConnectedHostBar = {
                                    connectedHostBarCollapsed.value = !connectedHostBarCollapsed.value
                                },
                                onOpenSettings = {
                                    if (currentRoute != Routes.SETTINGS) {
                                        navController.navigate(Routes.SETTINGS)
                                    }
                                },
                                findRequestToken = connectingFindRequestToken.intValue,
                                onCloseConnection = { closeCurrentConnectingSession() }
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
                            onImportPasswordPayload = onImportHostPasswordPayload,
                            onUpdate = onHostUpdate,
                            onDeleteHost = onHostDelete,
                            onStartSession = { host, mode, password ->
                                openOrStartSavedHostSession(host, mode, password)
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
                            hosts = uiState.hosts,
                            isLocked = uiState.isLocked,
                            onAdd = onIdentityAdd,
                            onUpdate = onIdentityUpdate,
                            onDelete = onIdentityDelete,
                            onImportIdentityKey = onImportIdentityKey,
                            onImportIdentityKeyPlain = onImportIdentityKeyPlain,
                            onStoreIdentityPublicKey = onStoreIdentityPublicKey,
                            onStoreIdentityKeyPassphrase = onStoreIdentityKeyPassphrase,
                            onCopyKeyToHost = onCopyIdentityKeyToHost,
                            onRemoveIdentityKey = onRemoveIdentityKey,
                            onToggleFavorite = onToggleFavorite,
                            onShowMessage = showMessage,
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
                                    if (activeSshSessions.isEmpty()) {
                                        showMessage("No active SSH session to run snippet.")
                                    } else {
                                        val preferredSessionId = quickConnectRequest.value?.sessionId
                                        snippetRunTargetHostId.value = activeSshSessions
                                            .firstOrNull { it.hostId == preferredSessionId }
                                            ?.hostId
                                            ?: activeSshSessions.first().hostId
                                        snippetRunSelection.value = snippet
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
                                backgroundSessionTimeout = uiState.backgroundSessionTimeout,
                                onBackgroundSessionTimeoutChange = onBackgroundSessionTimeoutChange,
                                biometricEnabled = uiState.biometricLockEnabled,
                                onBiometricToggle = onBiometricToggle,
                                lockTimeout = uiState.lockTimeout,
                                onLockTimeoutChange = onLockTimeoutChange,
                                customLockTimeoutMinutes = uiState.customLockTimeoutMinutes,
                                onCustomLockTimeoutMinutesChange = onCustomLockTimeoutMinutesChange,
                                snippetRunTimeoutSeconds = uiState.snippetRunTimeoutSeconds,
                                onSnippetRunTimeoutSecondsChange = onSnippetRunTimeoutSecondsChange,
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
                                includeSecretsInQr = uiState.includeSecretsInQr,
                                onIncludeSecretsInQrToggle = onIncludeSecretsInQrToggle,
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
                                onGenerateExportPayload = { passphrase -> buildExportPayload(uiState, passphrase) },
                                onTransferPayloadRequiresPassphrase = ::transferPayloadRequiresPassphrase,
                                onImportFromQrPayload = ::importTransferPayloadFromQr,
                                onShowMessage = showMessage,
                                corePermissions = corePermissions,
                                onManagePermissions = onOpenAppPermissionSettings
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

    if (missingCorePermissions.isNotEmpty()) {
        PermissionRequiredDialog(
            missingCorePermissions = missingCorePermissions,
            onManagePermissions = onOpenAppPermissionSettings,
            onRequestNow = onRequestCorePermissions
        )
    }

    if (showQuickConnect.value && !uiState.isLocked) {
        QuickConnectSheet(
            onDismiss = { showQuickConnect.value = false },
            portForwards = uiState.portForwards,
            identities = uiState.identities,
            snippets = uiState.snippets,
            terminalProfiles = uiState.terminalProfiles,
            defaultTerminalProfileId = uiState.defaultTerminalProfileId,
            onConnect = { host, port, username, auth, password, saveToHosts, useMosh, preferredIdentityId, forwardId, script, terminalProfileId ->
                var savedHostId: String? = null
                if (saveToHosts) {
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
                    resetSessionLogs(request.sessionId)
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
                        request.sessionId,
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
            modifier = Modifier.testTag(UiTestTags.HOST_KEY_PROMPT_DIALOG),
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
                Button(
                    onClick = { onRespondToHostKeyPrompt(prompt.id, true) },
                    modifier = Modifier.testTag(UiTestTags.HOST_KEY_PROMPT_ACCEPT)
                ) {
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
                            },
                            modifier = Modifier.testTag(UiTestTags.HOST_KEY_PROMPT_ACCEPT_ALWAYS)
                        ) {
                            Text("Yes (Don't Ask Again)")
                        }
                    }
                    TextButton(
                        onClick = { onRespondToHostKeyPrompt(prompt.id, false) },
                        modifier = Modifier.testTag(UiTestTags.HOST_KEY_PROMPT_REJECT)
                    ) {
                        Text("No")
                    }
                }
            }
        )
    }
    val promptPassword = remember(passwordPrompt?.id) { mutableStateOf("") }
    val promptPasswordRevealIndex = remember(passwordPrompt?.id) { mutableIntStateOf(-1) }
    val promptSavePassword = remember(passwordPrompt?.id) { mutableStateOf(false) }
    val passwordFocusRequester = remember(passwordPrompt?.id) { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    AutoHidePasswordReveal(promptPasswordRevealIndex)
    LaunchedEffect(passwordPrompt?.id) {
        if (passwordPrompt != null) {
            withFrameNanos { }
            runCatching { passwordFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    passwordPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier.testTag(UiTestTags.PASSWORD_PROMPT_DIALOG),
            title = { Text("Password Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prompt.reason)
                    Text("${prompt.username}@${prompt.host}:${prompt.port}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = promptPassword.value,
                        onValueChange = { updatePasswordStateWithReveal(promptPassword, promptPasswordRevealIndex, it) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = TailRevealPasswordVisualTransformation(promptPasswordRevealIndex.intValue),
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
                            .testTag(UiTestTags.PASSWORD_PROMPT_INPUT)
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
                    modifier = Modifier.testTag(UiTestTags.PASSWORD_PROMPT_CONFIRM),
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
                TextButton(
                    onClick = { onRespondToPasswordPrompt(prompt.id, null, false) },
                    modifier = Modifier.testTag(UiTestTags.PASSWORD_PROMPT_CANCEL)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    snippetRunSelection.value?.let { snippet ->
        val selectedSession = activeSshSessions.firstOrNull { it.hostId == snippetRunTargetHostId.value }
            ?: activeSshSessions.firstOrNull()
        AlertDialog(
            onDismissRequest = {
                if (!snippetRunInProgress.value) {
                    snippetRunSelection.value = null
                }
            },
            title = { Text("Run Snippet") },
            text = {
                val snippetTitle = snippet.title.ifBlank { "Snippet" }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Run \"$snippetTitle\" on:")
                    if (activeSshSessions.isEmpty()) {
                        Text(
                            "No active SSH sessions available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activeSshSessions.forEach { session ->
                                val selected = session.hostId == snippetRunTargetHostId.value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !snippetRunInProgress.value) {
                                            snippetRunTargetHostId.value = session.hostId
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            if (!snippetRunInProgress.value) {
                                                snippetRunTargetHostId.value = session.hostId
                                            }
                                        },
                                        enabled = !snippetRunInProgress.value
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .weight(1f)
                                    ) {
                                        Text(session.host.name)
                                        Text(
                                            "${session.host.username}@${session.host.host}:${session.host.port}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !snippetRunInProgress.value && selectedSession != null,
                    onClick = {
                        val target = activeSshSessions.firstOrNull { it.hostId == snippetRunTargetHostId.value }
                            ?: activeSshSessions.firstOrNull()
                        if (target == null) {
                            snippetRunSelection.value = null
                            showMessage("No active SSH session to run snippet.")
                            return@Button
                        }
                        val marker = "__SSHPEACHES_SNIPPET_DONE_${UUID.randomUUID().toString().replace("-", "")}__"
                        val payload = buildSnippetRunCommandPayload(snippet.command, marker)
                        if (payload.isBlank()) {
                            snippetRunSelection.value = null
                            showMessage("Snippet command is empty.")
                            return@Button
                        }
                        scope.launch {
                            snippetRunInProgress.value = true
                            try {
                                val timeoutSeconds = uiState.snippetRunTimeoutSeconds.coerceIn(1, 60)
                                val baselineOutput = shellOutputs[target.hostId].orEmpty()
                                var latestDelta = ""
                                onSendSessionShortcut(target.hostId, payload)
                                val deltaUntilMarker = withTimeoutOrNull(timeoutSeconds * 1_000L) {
                                    snapshotFlow { shellOutputs[target.hostId].orEmpty() }
                                        .map { output ->
                                            extractShellDelta(
                                                previousOutput = baselineOutput,
                                                latestOutput = output
                                            ).also { delta -> latestDelta = delta }
                                        }
                                        .first { it.contains(marker) }
                                }
                                val rawOutput = if (deltaUntilMarker != null) {
                                    deltaUntilMarker.substringBefore(marker)
                                } else {
                                    latestDelta
                                }
                                val hostLabel = target.host.name.ifBlank {
                                    "${target.host.username}@${target.host.host}"
                                }
                                snippetRunResult.value = SnippetRunResult(
                                    snippetTitle = snippet.title.ifBlank { "Snippet" },
                                    hostLabel = hostLabel,
                                    output = sanitizeSnippetOutput(rawOutput).ifBlank { "(no output)" },
                                    timedOut = deltaUntilMarker == null,
                                    timeoutSeconds = timeoutSeconds
                                )
                            } catch (error: Throwable) {
                                showMessage("Failed to run snippet: ${error.message ?: "unknown error"}")
                            } finally {
                                snippetRunInProgress.value = false
                                snippetRunSelection.value = null
                            }
                        }
                    }
                ) {
                    Text(if (snippetRunInProgress.value) "Running..." else "Run")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !snippetRunInProgress.value,
                    onClick = { snippetRunSelection.value = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    snippetRunResult.value?.let { result ->
        AlertDialog(
            onDismissRequest = { snippetRunResult.value = null },
            title = { Text(if (result.timedOut) "Snippet Timed Out" else "Snippet Result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${result.snippetTitle} on ${result.hostLabel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (result.timedOut) {
                        Text(
                            "Command did not finish in ${result.timeoutSeconds}s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = result.output,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { snippetRunResult.value = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAbout.value && !uiState.isLocked) {
        val website = context.getString(R.string.project_website)
        val supportUrl = context.getString(R.string.support_url)
        val privacy = context.getString(R.string.privacy_policy_url)
        AboutDialog(
            onDismiss = { showAbout.value = false },
            onOpenWebsite = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website)))
            },
            onOpenSupport = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl)))
            },
            onOpenPrivacy = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacy)))
            },
            onOpenSourceLicenses = {
                showAbout.value = false
                navController.navigate(Routes.OPEN_SOURCE_LICENSES)
            }
        )
    }
}

@Composable
internal fun PermissionRequiredDialog(
    missingCorePermissions: List<CorePermissionStatus>,
    onManagePermissions: () -> Unit,
    onRequestNow: () -> Unit
) {
    val canRequestAny = missingCorePermissions.any { it.remediation == CorePermissionRemediation.REQUEST }
    AlertDialog(
        modifier = Modifier.testTag(UiTestTags.PERMISSION_REQUIRED_DIALOG),
        onDismissRequest = {},
        title = { Text("Permissions Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (canRequestAny) {
                        "SSHPeaches needs these permissions to work properly, including background SSH sessions and session notifications."
                    } else {
                        "SSHPeaches needs permissions that must be enabled from system settings before background SSH sessions and notifications can work properly."
                    }
                )
                missingCorePermissions.forEach { permission ->
                    val suffix = if (permission.remediation == CorePermissionRemediation.SETTINGS) {
                        " (enable in Settings)"
                    } else {
                        ""
                    }
                    Text("- ${permission.title}$suffix")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onManagePermissions,
                modifier = Modifier.testTag(UiTestTags.PERMISSION_REQUIRED_MANAGE_BUTTON)
            ) {
                Text("Manage permissions")
            }
        },
        dismissButton = if (canRequestAny) {
            {
                TextButton(
                    onClick = onRequestNow,
                    modifier = Modifier.testTag(UiTestTags.PERMISSION_REQUIRED_REQUEST_BUTTON)
                ) {
                    Text("Request now")
                }
            }
        } else {
            null
        }
    )
}

private fun sessionIdFor(hostId: String, mode: ConnectionMode): String =
    "$hostId|${mode.name}|${UUID.randomUUID()}"

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

private data class SnippetRunResult(
    val snippetTitle: String,
    val hostLabel: String,
    val output: String,
    val timedOut: Boolean,
    val timeoutSeconds: Int
)

private fun buildSnippetRunCommandPayload(command: String, marker: String): String {
    val trimmedCommand = command.trimEnd()
    if (trimmedCommand.isBlank()) return ""
    return buildString {
        append(trimmedCommand)
        append("\n")
        append("printf \"\\n")
        append(marker)
        append("\\n\"\n")
    }
}

private fun extractShellDelta(previousOutput: String, latestOutput: String): String {
    return if (latestOutput.startsWith(previousOutput)) {
        latestOutput.substring(previousOutput.length)
    } else {
        latestOutput
    }
}

private val ansiEscapeRegex = Regex("\\u001B\\[[;?0-9]*[ -/]*[@-~]")

private fun sanitizeSnippetOutput(value: String): String {
    return value
        .replace("\u0000", "")
        .replace("\r", "")
        .replace(ansiEscapeRegex, "")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickConnectSheet(
    onDismiss: () -> Unit,
    portForwards: List<PortForward>,
    identities: List<Identity>,
    snippets: List<Snippet>,
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
        val host = rememberSaveable { mutableStateOf("") }
        val port = rememberSaveable { mutableStateOf("22") }
        val username = rememberSaveable { mutableStateOf("") }
        val password = rememberSaveable { mutableStateOf("") }
        val revealPasswordIndex = remember { mutableIntStateOf(-1) }
        val auth = rememberSaveable { mutableStateOf(AuthMethod.PASSWORD) }
        val saveToHosts = rememberSaveable { mutableStateOf(false) }
        val useMosh = rememberSaveable { mutableStateOf(false) }
        val selectedIdentityId = rememberSaveable { mutableStateOf<String?>(null) }
        val identityExpanded = remember { mutableStateOf(false) }
        val selectedForwardId = rememberSaveable { mutableStateOf<String?>(null) }
        val selectedStartupSnippetId = rememberSaveable { mutableStateOf<String?>(null) }
        val startupSnippetExpanded = remember { mutableStateOf(false) }
        val selectedTerminalProfileId = rememberSaveable { mutableStateOf<String?>(null) }
        val terminalProfileExpanded = remember { mutableStateOf(false) }
        val hostHistory = rememberSaveable { mutableStateOf(listOf<String>()) }
        val userHistory = rememberSaveable { mutableStateOf(listOf<String>()) }
        val status = rememberSaveable { mutableStateOf<String?>(null) }

        AutoHidePasswordReveal(revealPasswordIndex)

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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                    autoCorrect = false
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.QUICK_CONNECT_HOST_INPUT)
            )
            OutlinedTextField(
                value = port.value,
                onValueChange = { port.value = it },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    autoCorrect = false
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.QUICK_CONNECT_PORT_INPUT)
            )
            OutlinedTextField(
                value = username.value,
                onValueChange = { username.value = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                    autoCorrect = false
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.QUICK_CONNECT_USERNAME_INPUT)
            )
            OutlinedTextField(
                value = password.value,
                onValueChange = { next -> updatePasswordStateWithReveal(password, revealPasswordIndex, next) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = TailRevealPasswordVisualTransformation(revealPasswordIndex.intValue),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrect = false
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.QUICK_CONNECT_PASSWORD_INPUT)
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
            val availableIdentities = identities
            ExposedDropdownMenuBox(
                expanded = identityExpanded.value,
                onExpandedChange = { identityExpanded.value = !identityExpanded.value }
            ) {
                val selectedIdentity = availableIdentities.firstOrNull { it.id == selectedIdentityId.value }
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
                    availableIdentities.forEach { identity ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (identity.hasPrivateKey) {
                                        identity.label
                                    } else {
                                        "${identity.label} (no key)"
                                    }
                                )
                            },
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
            } else if (auth.value != AuthMethod.PASSWORD) {
                val selectedHasKey = identities.firstOrNull { it.id == selectedIdentityId.value }?.hasPrivateKey == true
                if (!selectedHasKey) {
                    Text(
                        "Selected identity has no private key imported.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
            ExposedDropdownMenuBox(
                expanded = startupSnippetExpanded.value,
                onExpandedChange = {
                    if (snippets.isNotEmpty()) {
                        startupSnippetExpanded.value = !startupSnippetExpanded.value
                    }
                }
            ) {
                val selectedSnippet = snippets.firstOrNull { it.id == selectedStartupSnippetId.value }
                TextField(
                    value = selectedSnippet?.title ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Startup command") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = startupSnippetExpanded.value)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = startupSnippetExpanded.value,
                    onDismissRequest = { startupSnippetExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            selectedStartupSnippetId.value = null
                            startupSnippetExpanded.value = false
                        }
                    )
                    snippets.forEach { snippet ->
                        DropdownMenuItem(
                            text = { Text(snippet.title) },
                            onClick = {
                                selectedStartupSnippetId.value = snippet.id
                                startupSnippetExpanded.value = false
                            }
                        )
                    }
                }
            }
            val selectedStartupSnippet = snippets.firstOrNull { it.id == selectedStartupSnippetId.value }
            if (selectedStartupSnippet != null) {
                Text(
                    selectedStartupSnippet.command,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (snippets.isEmpty()) {
                Text(
                    "No snippets available for startup command.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                Text("Save to Hosts")
                androidx.compose.material3.Switch(
                    checked = saveToHosts.value,
                    onCheckedChange = { saveToHosts.value = it }
                )
            }
            status.value?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(UiTestTags.QUICK_CONNECT_STATUS_TEXT)
                )
            }
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
                        saveToHosts.value,
                        useMosh.value,
                        selectedIdentityId.value,
                        selectedForwardId.value,
                        selectedStartupSnippetId.value?.let { snippetReference(it) }.orEmpty(),
                        selectedTerminalProfileId.value
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.QUICK_CONNECT_CONNECT_BUTTON),
                enabled = true
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
internal fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSourceLicenses: () -> Unit
) {
    val makerLogo = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        R.drawable.major_daft_apps_white
    } else {
        R.drawable.major_daft_apps_black
    }
    AlertDialog(
        modifier = Modifier.testTag(UiTestTags.ABOUT_DIALOG),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(
                text = "About SSHPeaches",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
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
                val appVersion = if (BuildConfig.DEBUG) {
                    "${BuildConfig.VERSION_NAME} (debug)"
                } else {
                    BuildConfig.VERSION_NAME
                }
                Text(
                    stringResource(id = R.string.about_version, appVersion),
                    style = MaterialTheme.typography.titleMedium
                )
                val website = stringResource(id = R.string.project_website)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Website:")
                    Text(
                        website,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .testTag(UiTestTags.ABOUT_WEBSITE_LINK)
                            .clickable(onClick = onOpenWebsite)
                    )
                }
                Text("License: GPL-3.0")
                Text(
                    "Open Source License Notices",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .testTag(UiTestTags.ABOUT_LICENSES_LINK)
                        .clickable { onOpenSourceLicenses() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Support",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .testTag(UiTestTags.ABOUT_SUPPORT_LINK)
                            .clickable(onClick = onOpenSupport)
                    )
                    Text("  |  ")
                    Text(
                        "Privacy Policy",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .testTag(UiTestTags.ABOUT_PRIVACY_LINK)
                            .clickable(onClick = onOpenPrivacy)
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

private fun decodeTransferPayload(encodedPayload: String): JSONObject? {
    val payload = runCatching {
        String(Base64.decode(encodedPayload.trim(), Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull() ?: return null
    return runCatching { JSONObject(payload) }.getOrNull()
}

private fun transferPayloadRequiresPassphrase(encodedPayload: String): Boolean {
    val root = decodeTransferPayload(encodedPayload) ?: return false
    val hostsRequirePassphrase = root.optJSONArray("hosts")?.let { hosts ->
        (0 until hosts.length()).any { index ->
            hosts.optJSONObject(index)?.optString("pwdPayload").orEmpty().isNotBlank()
        }
    } ?: false
    val identitiesRequirePassphrase = root.optJSONArray("identities")?.let { identities ->
        (0 until identities.length()).any { index ->
            val item = identities.optJSONObject(index)
            item?.optString("keyPayload").orEmpty().isNotBlank() ||
                item?.optString("keyPassphrasePayload").orEmpty().isNotBlank()
        }
    } ?: false
    return hostsRequirePassphrase || identitiesRequirePassphrase
}

private fun optNullableLong(item: JSONObject, key: String): Long? =
    if (item.has(key) && !item.isNull(key)) item.optLong(key) else null

private fun jsonStringList(array: JSONArray?): List<String> = buildList {
    if (array == null) return@buildList
    for (index in 0 until array.length()) {
        array.optString(index).trim().ifBlank { null }?.let(::add)
    }
}

private fun forwardTransferKey(
    label: String,
    sourceHost: String,
    sourcePort: Int,
    destinationHost: String,
    destinationPort: Int
): String = listOf(
    label.trim(),
    sourceHost.trim(),
    sourcePort.toString(),
    destinationHost.trim(),
    destinationPort.toString()
).joinToString("|")

private fun snippetTransferKey(title: String, command: String): String =
    listOf(title.trim(), command.trim()).joinToString("|")

private fun keyboardSlotToJson(slot: KeyboardSlotAction): JSONObject = JSONObject().apply {
    put("type", slot.type.name)
    put("label", slot.label)
    put("text", slot.text)
    put("keyCode", slot.keyCode ?: JSONObject.NULL)
    put("modifier", slot.modifier?.name ?: JSONObject.NULL)
    put("sequence", slot.sequence)
    put("ctrl", slot.ctrl)
    put("alt", slot.alt)
    put("shift", slot.shift)
    put("repeatable", slot.repeatable)
    put("iconId", slot.iconId)
}

private fun keyboardLayoutToJson(slots: List<KeyboardSlotAction>): JSONArray = JSONArray().apply {
    KeyboardLayoutDefaults.normalizeSlots(slots).forEach { put(keyboardSlotToJson(it)) }
}

private fun keyboardLayoutFromJson(array: JSONArray?): List<KeyboardSlotAction> {
    if (array == null) return KeyboardLayoutDefaults.DEFAULT_SLOTS
    val parsed = List(minOf(array.length(), KeyboardLayoutDefaults.SLOT_COUNT)) { index ->
        val item = array.optJSONObject(index)
        KeyboardSlotAction(
            type = runCatching {
                KeyboardActionType.valueOf(
                    item?.optString("type", KeyboardActionType.TEXT.name) ?: KeyboardActionType.TEXT.name
                )
            }.getOrDefault(KeyboardActionType.TEXT),
            label = item?.optString("label").orEmpty(),
            text = item?.optString("text").orEmpty(),
            keyCode = item?.takeIf { it.has("keyCode") && !it.isNull("keyCode") }?.optInt("keyCode"),
            modifier = item?.takeIf { it.has("modifier") && !it.isNull("modifier") }?.optString("modifier")
                ?.let { value -> runCatching { KeyboardModifier.valueOf(value) }.getOrNull() },
            sequence = item?.optString("sequence").orEmpty(),
            ctrl = item?.optBoolean("ctrl", false) ?: false,
            alt = item?.optBoolean("alt", false) ?: false,
            shift = item?.optBoolean("shift", false) ?: false,
            repeatable = item?.optBoolean("repeatable", false) ?: false,
            iconId = item?.optString("iconId").orEmpty()
        )
    }
    return KeyboardLayoutDefaults.normalizeSlots(parsed)
}

private fun terminalProfileToJson(profile: TerminalProfile): JSONObject = JSONObject().apply {
    put("id", profile.id)
    put("name", profile.name)
    put("fontSizeSp", profile.fontSizeSp)
    put("foregroundHex", profile.foregroundHex)
    put("backgroundHex", profile.backgroundHex)
    put("cursorHex", profile.cursorHex)
    put("cursorStyle", profile.cursorStyle.name)
    put("cursorBlink", profile.cursorBlink)
}

private fun terminalProfilesToJson(profiles: List<TerminalProfile>): JSONArray = JSONArray().apply {
    profiles.forEach { put(terminalProfileToJson(it)) }
}

private fun terminalProfilesFromJson(array: JSONArray?): List<TerminalProfile> {
    if (array == null) return emptyList()
    val out = mutableListOf<TerminalProfile>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.optString("id").trim()
        if (id.isBlank()) continue
        out += TerminalProfile(
            id = id,
            name = item.optString("name", "Profile").trim().ifBlank { "Profile" },
            fontSizeSp = item.optInt("fontSizeSp", 10).coerceIn(6, 28),
            foregroundHex = item.optString("foregroundHex", "#E6E6E6"),
            backgroundHex = item.optString("backgroundHex", "#101010"),
            cursorHex = item.optString("cursorHex", "#FFB74D"),
            cursorStyle = runCatching {
                TerminalCursorStyle.valueOf(
                    item.optString("cursorStyle", TerminalCursorStyle.BLOCK.name)
                )
            }.getOrDefault(TerminalCursorStyle.BLOCK),
            cursorBlink = item.optBoolean("cursorBlink", true)
        )
    }
    return out
}

private fun buildExportPayload(state: AppUiState, passphrase: String?): String? {
    val includeSecrets = state.includeSecretsInQr
    val secretsPassphrase = if (includeSecrets) passphrase?.takeIf { it.isNotBlank() } ?: return null else null
    val payload = JSONObject().apply {
        put("v", 2)
        put("exportedAt", System.currentTimeMillis())
        put("hosts", JSONArray().apply {
            state.hosts.forEach { host ->
                val encryptedPasswordPayload = if (includeSecrets && host.hasPassword) {
                    SecurityManager.exportHostPasswordPayload(host.id, secretsPassphrase!!) ?: return null
                } else {
                    null
                }
                put(JSONObject().apply {
                    put("id", host.id)
                    put("name", host.name)
                    put("host", host.host)
                    put("port", host.port)
                    put("username", host.username)
                    put("auth", host.preferredAuth.name)
                    put("group", host.group)
                    put("lastUsedEpochMillis", host.lastUsedEpochMillis ?: JSONObject.NULL)
                    put("favorite", host.favorite)
                    put("notes", host.notes)
                    put("defaultMode", host.defaultMode.name)
                    put("attachedForwards", JSONArray(host.attachedForwards))
                    put("snippets", JSONArray(host.snippets))
                    put("osMetadata", Converters.fromOsMetadata(host.osMetadata))
                    put("hasPassword", host.hasPassword)
                    put("useMosh", host.useMosh)
                    put("preferredIdentityId", host.preferredIdentityId)
                    put("preferredForwardId", host.preferredForwardId)
                    put("startupScript", host.startupScript)
                    put("backgroundBehavior", host.backgroundBehavior.name)
                    put("terminalProfileId", host.terminalProfileId ?: "")
                    put("infoCommands", JSONArray(host.infoCommands))
                    encryptedPasswordPayload?.let { put("pwdPayload", it) }
                })
            }
        })
        put("identities", JSONArray().apply {
            state.identities.forEach { identity ->
                val encryptedKeyPayload = if (includeSecrets && identity.hasPrivateKey) {
                    SecurityManager.exportIdentityKeyPayload(identity.id, secretsPassphrase!!) ?: return null
                } else {
                    null
                }
                val encryptedKeyPassphrasePayload = if (includeSecrets) {
                    SecurityManager.exportIdentityKeyPassphrasePayload(identity.id, secretsPassphrase!!)
                } else {
                    null
                }
                put(JSONObject().apply {
                    put("id", identity.id)
                    put("label", identity.label)
                    put("fingerprint", identity.fingerprint)
                    put("username", identity.username)
                    put("createdEpochMillis", identity.createdEpochMillis)
                    put("lastUsedEpochMillis", identity.lastUsedEpochMillis ?: JSONObject.NULL)
                    put("favorite", identity.favorite)
                    put("tags", JSONArray(identity.tags))
                    put("notes", identity.notes)
                    put("hasPrivateKey", identity.hasPrivateKey)
                    put("keyImportEpochMillis", identity.keyImportEpochMillis ?: JSONObject.NULL)
                    SecurityManager.exportIdentityPublicKey(identity.id)?.let { put("publicKey", it) }
                    encryptedKeyPayload?.let { put("keyPayload", it) }
                    encryptedKeyPassphrasePayload?.let { put("keyPassphrasePayload", it) }
                })
            }
        })
        put("settings", JSONObject().apply {
            put("themeMode", state.themeMode.name)
            put("allowBackgroundSessions", state.allowBackgroundSessions)
            put("backgroundSessionTimeout", state.backgroundSessionTimeout.name)
            put("biometricLockEnabled", state.biometricLockEnabled)
            put("lockTimeout", state.lockTimeout.name)
            put("customLockTimeoutMinutes", state.customLockTimeoutMinutes)
            put("terminalEmulation", state.terminalEmulation.name)
            put("terminalSelectionMode", state.terminalSelectionMode.name)
            put("terminalProfiles", terminalProfilesToJson(state.terminalProfiles))
            put("defaultTerminalProfileId", state.defaultTerminalProfileId)
            put("crashReportsEnabled", state.crashReportsEnabled)
            put("analyticsEnabled", state.analyticsEnabled)
            put("diagnosticsLoggingEnabled", state.diagnosticsLoggingEnabled)
            put("autoStartForwards", state.autoStartForwards)
            put("hostKeyPromptEnabled", state.hostKeyPromptEnabled)
            put("autoTrustHostKey", state.autoTrustHostKey)
            put("usageReportsEnabled", state.usageReportsEnabled)
            put("snippetRunTimeoutSeconds", state.snippetRunTimeoutSeconds)
            put("keyboardLayout", keyboardLayoutToJson(state.keyboardSlots))
        })
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
                    put("favorite", forward.favorite)
                    put("enabled", forward.enabled)
                })
            }
        })
        put("snippets", JSONArray().apply {
            state.snippets.forEach { snippet ->
                put(JSONObject().apply {
                    put("id", snippet.id)
                    put("title", snippet.title)
                    put("description", snippet.description)
                    put("command", snippet.command)
                    put("tags", JSONArray(snippet.tags))
                    put("autoRunHostIds", JSONArray(snippet.autoRunHostIds))
                    put("requireConfirmation", snippet.requireConfirmation)
                    put("favorite", snippet.favorite)
                })
            }
        })
    }.toString()
    return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
