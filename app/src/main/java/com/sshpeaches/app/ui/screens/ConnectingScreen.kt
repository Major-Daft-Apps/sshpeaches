package com.majordaftapps.sshpeaches.app.ui.screens

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.toColorInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import com.majordaftapps.sshpeaches.app.service.FileTransferDirection
import com.majordaftapps.sshpeaches.app.service.FileTransferProgress
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.sftpDirectoryRefreshKey
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.state.FileTransferEntryMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalBellMode
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.state.userFacingLabel
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalInputRouter
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import com.majordaftapps.sshpeaches.app.ui.terminal.resolveTerminalTypeface
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.WcWidth
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import com.majordaftapps.sshpeaches.app.MainActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QuickConnectRequest(
    val sessionId: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: AuthMethod,
    val mode: ConnectionMode = ConnectionMode.SSH,
    val savedHostId: String? = null,
    val useMosh: Boolean = false,
    val preferredIdentityId: String? = null,
    val forwardId: String? = null,
    val script: String = "",
    val terminalProfileId: String? = null,
    val initialFileTransferEntryMode: FileTransferEntryMode = FileTransferEntryMode.DOWNLOAD
) : Serializable

enum class QuickConnectPhase {
    IDLE,
    CONNECTING,
    SUCCESS,
    ERROR
}

data class QuickConnectUiState(
    val phase: QuickConnectPhase = QuickConnectPhase.IDLE,
    val message: String = "",
    val failureKind: ConnectionFailureKind? = null
) : Serializable

private data class RemoteBreadcrumb(
    val label: String,
    val path: String
)

private typealias RemoteDirectorySnapshot =
    com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectorySnapshot
private typealias RemoteDirectoryEntry =
    com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectoryEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectingScreen(
    request: QuickConnectRequest?,
    state: QuickConnectUiState,
    logs: List<SessionLogBus.Entry>,
    shellOutput: String,
    remoteDirectory: com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectorySnapshot?,
    activeFileTransfer: FileTransferProgress? = null,
    terminalProfile: TerminalProfile,
    terminalSelectionMode: TerminalSelectionMode,
    terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
    diagnosticsLoggingEnabled: Boolean = false,
    useVolumeButtonsToAdjustFontSize: Boolean = false,
    useBuiltInKeyboard: Boolean = false,
    terminalMarginPx: Int = 0,
    keyboardSlots: List<KeyboardSlotAction>,
    snippets: List<Snippet>,
    onSendShellBytes: (ByteArray) -> Unit,
    onTerminalResize: (Int, Int) -> Unit,
    onSftpListDirectory: (String) -> Unit,
    onSftpDownload: (String, String?) -> Unit,
    onSftpUpload: (String, String) -> Unit,
    onScpDownload: (String, String?) -> Unit,
    onScpUpload: (String, String) -> Unit,
    onCancelFileTransfer: () -> Unit = {},
    onManageRemotePath: (operation: String, sourcePath: String, destinationPath: String?) -> Unit,
    resolveTerminalEmulator: (String) -> com.termux.terminal.TerminalEmulator? = { null },
    resolveRuntimeSessionPassword: (String) -> String? = { null },
    onRetry: () -> Unit,
    onToggleConnectedHostBar: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    findRequestToken: Int,
    applyStatusBarsPadding: Boolean = true
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? MainActivity
    val clipboardManager = LocalClipboardManager.current
    val currentOnSendShellBytes = rememberUpdatedState(onSendShellBytes)
    val currentResolveTerminalEmulator = rememberUpdatedState(resolveTerminalEmulator)
    val currentRequest = rememberUpdatedState(request)
    val currentDiagnosticsLoggingEnabled = rememberUpdatedState(diagnosticsLoggingEnabled)
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val terminalMarginDp = (terminalMarginPx.coerceIn(0, 128) / density.density).dp
    val bellThrottle = remember(request?.sessionId) { AtomicLong(0L) }
    val keyboardFocusRequester = remember(request?.sessionId) { FocusRequester() }
    val terminalEngine = remember(request?.sessionId, clipboardManager) {
        TermuxTerminalEngine(
            onWriteToRemote = { payload -> currentOnSendShellBytes.value(payload) },
            onCopyToClipboard = { _ -> },
            onRequestPasteText = { null },
            onTerminalDiagnostic = { message ->
                val sessionId = currentRequest.value?.sessionId ?: return@TermuxTerminalEngine
                if (!currentDiagnosticsLoggingEnabled.value) return@TermuxTerminalEngine
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = sessionId,
                        level = SessionLogBus.LogLevel.DEBUG,
                        message = message
                    )
                )
            }
        )
    }
    val terminalInput = remember(request?.sessionId, clipboardManager) {
        TerminalInputRouter(
            emulatorProvider = {
                currentRequest.value?.let { currentResolveTerminalEmulator.value(it.sessionId) }
                    ?: terminalEngine.emulator()
            },
            onWriteToRemote = { payload -> currentOnSendShellBytes.value(payload) },
            onRequestPasteText = { clipboardManager.getText()?.text }
        )
    }
    var lastShellSnapshot by remember(request?.sessionId) { mutableStateOf("") }
    var terminalViewRef by remember(request?.sessionId) { mutableStateOf<TerminalView?>(null) }
    var terminalImeBridgeRef by remember(request?.sessionId) { mutableStateOf<TerminalImeBridgeEditText?>(null) }
    var keyboardFocused by remember(request?.sessionId) { mutableStateOf(false) }
    var terminalFontSizeSp by rememberSaveable(request?.sessionId) {
        mutableStateOf(terminalProfile.fontSizeSp.toFloat())
    }
    val currentTerminalProfile = rememberUpdatedState(terminalProfile)
    val currentTerminalFontSizeSp = rememberUpdatedState(terminalFontSizeSp)
    var lastAppliedProfileFontSizeSp by remember(request?.sessionId) { mutableStateOf<Float?>(null) }
    var lastResize by remember(request?.sessionId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var sftpPath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    val sftpConsoleLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    var sftpCommandInput by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var sftpLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var sftpPendingDirectoryEcho by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var sftpLastRenderedDirectoryKey by remember(request?.sessionId) { mutableStateOf("") }
    var sftpListShowAll by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var sftpConsoleRevision by remember(request?.sessionId) { mutableStateOf(0L) }
    var pendingSftpDownloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var pendingSftpUploadBasePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    val scpActivityLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    var scpRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var scpPendingListPath by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpPendingListBaselineToken by remember(request?.sessionId) { mutableStateOf<Long?>(null) }
    var scpLastListedPath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var scpHomePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpVisibleEntries by remember(request?.sessionId) {
        mutableStateOf<List<com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectoryEntry>>(emptyList())
    }
    val scpPathHistory = remember(request?.sessionId) { mutableStateListOf(".") }
    var scpPathHistoryIndex by rememberSaveable(request?.sessionId) { mutableStateOf(0) }
    var scpSelectedPath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var pendingScpDownloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpTransferStatus by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var sftpTransferStatus by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var lastHandledScpResultLog by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var lastHandledTerminalTransferId by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var showScpUploadVertical by rememberSaveable(request?.sessionId) {
        mutableStateOf(request?.initialFileTransferEntryMode == FileTransferEntryMode.UPLOAD)
    }
    var scpActionsExpanded by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var scpPendingManualPathTarget by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpPendingManualPathFallback by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpPendingLinkPathTarget by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpPendingLinkPathFallback by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var showScpRenameDialog by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var scpRenameValue by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var showScpMoveDialog by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var scpMoveDestination by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var showScpNewFolderDialog by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var scpNewFolderValue by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var showScpDeleteDialog by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var pendingModifiers by remember(request?.sessionId) { mutableStateOf(setOf<KeyboardModifier>()) }
    var showSnippetPicker by remember(request?.sessionId) { mutableStateOf(false) }
    var keyboardVisibleRequested by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpCommandRunning by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpAwaitDirectoryRefresh by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpCommandStartLogCount by remember(request?.sessionId) { mutableStateOf(0) }
    var sftpCommandStartDirectoryKey by remember(request?.sessionId) { mutableStateOf("") }
    var swipeNavigationEnabled by remember(request?.sessionId) { mutableStateOf(false) }
    var swipeStart by remember(request?.sessionId) { mutableStateOf<SwipeGestureStart?>(null) }
    var swipeIntercepting by remember(request?.sessionId) { mutableStateOf(false) }
    var swipeRepeatJob by remember(request?.sessionId) { mutableStateOf<Job?>(null) }
    var swipeRepeatKeyCode by remember(request?.sessionId) { mutableStateOf<Int?>(null) }
    var isFnRowVisible by rememberSaveable(request?.sessionId, useBuiltInKeyboard) {
        mutableStateOf(false)
    }
    val supportsSystemKeyboard = !useBuiltInKeyboard
    var showFindDialog by remember(request?.sessionId) { mutableStateOf(false) }
    var findQuery by remember(request?.sessionId) { mutableStateOf("") }
    var findCaseSensitive by remember(request?.sessionId) { mutableStateOf(false) }
    var findMatchIndex by remember(request?.sessionId) { mutableStateOf(0) }

    LaunchedEffect(useBuiltInKeyboard) {
        if (useBuiltInKeyboard) {
            terminalImeBridgeRef?.hideTerminalKeyboard()
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            keyboardVisibleRequested = false
        }
    }

    val compactKeys = remember(keyboardSlots, useBuiltInKeyboard, isFnRowVisible) {
        val source = when {
            isFnRowVisible && useBuiltInKeyboard -> KeyboardLayoutDefaults.builtInFnLayout(keyboardSlots)
            isFnRowVisible -> KeyboardLayoutDefaults.customFnLayout(keyboardSlots)
            useBuiltInKeyboard -> KeyboardLayoutDefaults.builtInCompactLayout(keyboardSlots)
            else -> KeyboardLayoutDefaults.normalizeSlots(keyboardSlots)
        }
        source.map { action ->
            CompactTerminalKey(
                label = KeyboardLayoutDefaults.compactLabel(action, fallback = "+"),
                action = action,
                enabled = !action.isEmpty(),
                repeatable = action.repeatable
            )
        }
    }
    val activeAliasIcons = remember(swipeNavigationEnabled, isFnRowVisible, useBuiltInKeyboard) {
        buildSet {
            if (swipeNavigationEnabled) {
                add("swipe_nav")
            }
            if (isFnRowVisible) {
                add("fn_active")
            }
        }
    }
    val swipeNavMinDistancePx = with(density) { SWIPE_NAV_MIN_DISTANCE_DP.dp.toPx() }

    fun applyTerminalFontSizeDelta(deltaSp: Float): Boolean {
        val updated = (terminalFontSizeSp + deltaSp).coerceIn(6f, 28f)
        if (updated != terminalFontSizeSp) {
            terminalFontSizeSp = updated
            terminalViewRef?.setTextSize(with(density) { updated.sp.toPx().toInt().coerceAtLeast(6) })
        }
        return true
    }

    fun handleVolumeKeyForFontSize(event: KeyEvent): Boolean {
        if (!useVolumeButtonsToAdjustFontSize) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) applyTerminalFontSizeDelta(1f) else true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) applyTerminalFontSizeDelta(-1f) else true
            }
            else -> false
        }
    }

    fun browseScpPath(target: String, recordHistory: Boolean = true, clearSelection: Boolean = true, clearStatus: Boolean = true) {
        if (scpPendingListPath != null) return
        val normalized = target.trim().ifBlank { "." }
        scpRemotePath = normalized
        scpLastListedPath = normalized
        scpPendingListPath = normalized
        scpPendingListBaselineToken = remoteDirectory?.refreshToken
        if (clearSelection) {
            scpSelectedPath = null
        }
        if (clearStatus) {
            scpTransferStatus = null
        }
        onSftpListDirectory(normalized)
        if (recordHistory) {
            while (scpPathHistory.size - 1 > scpPathHistoryIndex) {
                scpPathHistory.removeAt(scpPathHistory.lastIndex)
            }
            if (scpPathHistory.lastOrNull() != normalized) {
                scpPathHistory += normalized
            }
            scpPathHistoryIndex = scpPathHistory.lastIndex
        }
    }

    SideEffect {
        terminalEngine.setOnBellAction {
            val now = SystemClock.elapsedRealtime()
            val previous = bellThrottle.get()
            if (now - previous < TERMINAL_BELL_THROTTLE_MS) return@setOnBellAction
            if (!bellThrottle.compareAndSet(previous, now)) return@setOnBellAction
            when (terminalBellMode) {
                TerminalBellMode.DISABLED -> Unit
                TerminalBellMode.VIBRATE_DEVICE -> vibrateTerminalBell(context)
                TerminalBellMode.SHOW_NOTIFICATION -> showTerminalBellNotification(context, request)
            }
        }
    }

    val statusText = when (state.phase) {
        QuickConnectPhase.CONNECTING -> "Connecting..."
        QuickConnectPhase.SUCCESS -> "Connected"
        QuickConnectPhase.ERROR -> when (state.failureKind) {
            ConnectionFailureKind.NETWORK -> "Network error"
            null -> "Connection failed"
        }
        QuickConnectPhase.IDLE -> "Preparing..."
    }
    val statusColor = when (state.phase) {
        QuickConnectPhase.ERROR -> MaterialTheme.colorScheme.error
        else -> colorResource(id = R.color.peachy_orange)
    }
    val terminalPanelColor = parseComposeColor(terminalProfile.backgroundHex, MaterialTheme.colorScheme.surface)

    val hostName = request?.let { "${it.username}@${it.host}:${it.port}" } ?: "Quick Connect"
    val renderedLogs = remember(logs) {
        logs.flatMap { entry -> entry.message.lines() }
    }
    val externalTerminalEmulator = request?.let { resolveTerminalEmulator(it.sessionId) }
    val hasExternalTerminalEmulator = externalTerminalEmulator != null
    val detailLine = request?.let {
        buildString {
            append(it.mode.userFacingLabel(it.initialFileTransferEntryMode))
            it.forwardId?.let { id -> append(" | Forward: $id") }
            if (it.script.isNotBlank()) append(" | Script configured")
        }
    }
    val showTerminalSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SSH
    val showSftpCliSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SFTP
    val showScpTransferSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SCP
    val scpTransferActive =
        request?.mode == ConnectionMode.SCP &&
            activeFileTransfer?.mode == ConnectionMode.SCP &&
            activeFileTransfer.isActive
    val sftpTransferActive =
        request?.mode == ConnectionMode.SFTP &&
            activeFileTransfer?.mode == ConnectionMode.SFTP &&
            activeFileTransfer.isActive
    val activeTransferMessage = activeFileTransfer?.statusMessage()
    val userFacingStateMessage = when {
        !activeTransferMessage.isNullOrBlank() -> activeTransferMessage
        request?.mode == ConnectionMode.SCP && state.phase != QuickConnectPhase.ERROR -> {
            when (state.phase) {
                QuickConnectPhase.SUCCESS -> "Ready to transfer files."
                else -> "Preparing file transfer..."
            }
        }

        else -> state.message
    }
    val transcriptForFind = if (showFindDialog && showTerminalSession) {
        terminalViewRef?.getFullTranscriptText().orEmpty().ifBlank { shellOutput }
    } else {
        ""
    }
    val emulatorForFind = if (showFindDialog && showTerminalSession) {
        terminalViewRef?.mEmulator ?: externalTerminalEmulator ?: terminalEngine.emulator()
    } else {
        null
    }
    val findMatches = remember(
        transcriptForFind,
        findQuery,
        findCaseSensitive,
        terminalSelectionMode,
        emulatorForFind
    ) {
        computeTerminalFindMatches(
            text = transcriptForFind,
            query = findQuery,
            caseSensitive = findCaseSensitive,
            emulator = emulatorForFind,
            joinWrappedRows = terminalSelectionMode == TerminalSelectionMode.NATURAL
        )
    }
    val activeFindMatch = if (findMatches.isEmpty()) {
        null
    } else {
        findMatches[findMatchIndex.coerceIn(0, findMatches.lastIndex)]
    }
    fun hideSystemKeyboard() {
        if (!supportsSystemKeyboard) {
            keyboardVisibleRequested = false
            return
        }
        terminalImeBridgeRef?.hideTerminalKeyboard()
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        keyboardFocused = false
        keyboardVisibleRequested = false
    }

    fun showSystemKeyboard() {
        if (!supportsSystemKeyboard) {
            keyboardVisibleRequested = false
            return
        }
        runCatching { keyboardFocusRequester.requestFocus() }
        terminalImeBridgeRef?.showTerminalKeyboard() ?: keyboardController?.show()
        keyboardFocused = true
        keyboardVisibleRequested = true
    }

    fun toggleSystemKeyboard() {
        if (!supportsSystemKeyboard) {
            keyboardVisibleRequested = false
            return
        }
        if (keyboardVisibleRequested) {
            hideSystemKeyboard()
        } else {
            showSystemKeyboard()
        }
    }
    fun recordTerminalResize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        val resize = columns to rows
        if (lastResize == resize) return
        lastResize = resize
        onTerminalResize(columns, rows)
    }
    LaunchedEffect(keyboardVisibleRequested, terminalImeBridgeRef, supportsSystemKeyboard) {
        if (keyboardVisibleRequested && supportsSystemKeyboard) {
            terminalImeBridgeRef?.showTerminalKeyboard()
        } else if (!supportsSystemKeyboard) {
            keyboardVisibleRequested = false
        }
    }
    fun handleTerminalAndroidKeyEvent(event: KeyEvent): Boolean {
        val modifiers = pendingModifiers
        val handled = terminalInput.onAndroidKeyDown(
            event = event,
            ctrlDown = modifiers.contains(KeyboardModifier.CTRL),
            altDown = modifiers.contains(KeyboardModifier.ALT),
            shiftDown = modifiers.contains(KeyboardModifier.SHIFT)
        )
        if (handled && modifiers.isNotEmpty()) {
            pendingModifiers = emptySet()
        }
        return handled
    }

    val terminalViewClient = remember(request?.sessionId, supportsSystemKeyboard) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                terminalFontSizeSp = (terminalFontSizeSp * scale).coerceIn(6f, 28f)
                terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) })
                return 1f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                onToggleConnectedHostBar()
                if (supportsSystemKeyboard && keyboardVisibleRequested) {
                    showSystemKeyboard()
                }
            }

            override fun onDoubleTap(e: MotionEvent) {
                if (supportsSystemKeyboard) {
                    toggleSystemKeyboard()
                }
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false

            override fun shouldEnforceCharBasedInput(): Boolean = true

            override fun isTerminalViewSelected(): Boolean = keyboardFocused

            override fun copyModeChanged(copyMode: Boolean) {
                if (copyMode || !supportsSystemKeyboard || !keyboardVisibleRequested) return
                val bridge = terminalImeBridgeRef ?: return
                bridge.post {
                    if (
                        terminalImeBridgeRef === bridge &&
                        bridge.isAttachedToWindow &&
                        keyboardVisibleRequested &&
                        supportsSystemKeyboard
                    ) {
                        bridge.showTerminalKeyboard()
                    }
                }
            }

            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
                if (handleVolumeKeyForFontSize(e)) return true
                return handleTerminalAndroidKeyEvent(e)
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = handleVolumeKeyForFontSize(e)

            override fun onLongPress(event: MotionEvent): Boolean = false

            override fun readControlKey(): Boolean = pendingModifiers.contains(KeyboardModifier.CTRL)

            override fun readAltKey(): Boolean = pendingModifiers.contains(KeyboardModifier.ALT)

            override fun readShiftKey(): Boolean = pendingModifiers.contains(KeyboardModifier.SHIFT)

            override fun readFnKey(): Boolean = false

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
                val text = runCatching { String(Character.toChars(codePoint)) }.getOrDefault("")
                if (text.isBlank()) return true
                terminalInput.sendText(
                    text = text,
                    ctrlDown = ctrlDown || pendingModifiers.contains(KeyboardModifier.CTRL),
                    altDown = pendingModifiers.contains(KeyboardModifier.ALT),
                    shiftDown = pendingModifiers.contains(KeyboardModifier.SHIFT)
                )
                if (pendingModifiers.isNotEmpty()) {
                    pendingModifiers = emptySet()
                }
                return true
            }

            override fun onPasteText(emulator: TerminalEmulator?, text: String?): Boolean {
                if (emulator == null || text.isNullOrEmpty()) return false
                return terminalInput.pasteText(text, emulator)
            }

            override fun onTerminalSizeChanged(columns: Int, rows: Int) {
                recordTerminalResize(columns, rows)
            }
        }
    }
    val activityHardwareKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        showTerminalSession && handleVolumeKeyForFontSize(event)
    }

    LaunchedEffect(logs.lastOrNull(), renderedLogs.size) {
        if (renderedLogs.isNotEmpty()) {
            withFrameNanos { }
            listState.scrollToItem(renderedLogs.lastIndex)
        }
    }
    LaunchedEffect(terminalProfile.fontSizeSp) {
        val previous = lastAppliedProfileFontSizeSp
        lastAppliedProfileFontSizeSp = terminalProfile.fontSizeSp.toFloat()
        if (previous != null && previous != terminalProfile.fontSizeSp.toFloat()) {
            terminalFontSizeSp = terminalProfile.fontSizeSp.toFloat()
        }
    }
    LaunchedEffect(request?.sessionId) {
        terminalEngine.reset()
        terminalEngine.applyProfile(terminalProfile)
        lastShellSnapshot = ""
        lastAppliedProfileFontSizeSp = terminalProfile.fontSizeSp.toFloat()
        lastResize = null
        sftpPath = "."
        sftpCommandInput = ""
        sftpLocalPath = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        sftpPendingDirectoryEcho = null
        sftpLastRenderedDirectoryKey = ""
        pendingSftpDownloadRemotePath = null
        pendingSftpUploadBasePath = null
        sftpConsoleLines.clear()
        sftpConsoleRevision = 0L
        scpActivityLines.clear()
        scpRemotePath = "."
        scpPendingListPath = null
        scpPendingListBaselineToken = null
        scpLastListedPath = "."
        scpHomePath = null
        scpVisibleEntries = emptyList()
        scpPathHistory.clear()
        scpPathHistory += "."
        scpPathHistoryIndex = 0
        scpSelectedPath = null
        pendingScpDownloadRemotePath = null
        scpTransferStatus = null
        sftpTransferStatus = null
        scpActionsExpanded = false
        scpPendingManualPathTarget = null
        scpPendingManualPathFallback = null
        scpPendingLinkPathTarget = null
        scpPendingLinkPathFallback = null
        showScpRenameDialog = false
        scpRenameValue = ""
        showScpMoveDialog = false
        scpMoveDestination = ""
        showScpNewFolderDialog = false
        scpNewFolderValue = ""
        showScpDeleteDialog = false
        if (request?.mode == ConnectionMode.SFTP) {
            sftpConsoleLines += "Connected to ${request.host}:${request.port}"
            sftpConsoleLines += "Type 'help' for SFTP commands."
            sftpConsoleRevision += 1L
        }
        if (request?.mode == ConnectionMode.SCP) {
            if (remoteDirectory == null) {
                scpPendingListPath = scpRemotePath
                scpPendingListBaselineToken = null
                onSftpListDirectory(scpRemotePath)
            } else {
                scpPendingListPath = null
                scpPendingListBaselineToken = null
            }
        }
        pendingModifiers = emptySet()
        showSnippetPicker = false
        keyboardVisibleRequested = false
        swipeNavigationEnabled = false
        swipeStart = null
        swipeIntercepting = false
        swipeRepeatJob?.cancel()
        swipeRepeatJob = null
        swipeRepeatKeyCode = null
        terminalViewRef?.clearSearchHighlight()
        showFindDialog = false
        findQuery = ""
        findCaseSensitive = false
        sftpCommandRunning = false
        sftpAwaitDirectoryRefresh = false
        sftpCommandStartLogCount = 0
        sftpCommandStartDirectoryKey = ""
        terminalViewRef?.onScreenUpdated()
    }
    LaunchedEffect(request?.sessionId, request?.initialFileTransferEntryMode) {
        if (request?.mode == ConnectionMode.SCP) {
            showScpUploadVertical =
                request.initialFileTransferEntryMode == FileTransferEntryMode.UPLOAD
        }
    }
    LaunchedEffect(logs, request?.mode) {
        if (request?.mode != ConnectionMode.SCP || logs.isEmpty()) return@LaunchedEffect
        val entry = logs.asReversed().firstOrNull { item ->
            val message = item.message
            message.startsWith("Remote move completed:") ||
                message.startsWith("Remote delete completed:") ||
                message.startsWith("Remote mkdir completed:") ||
                message.startsWith("SFTP operation failed:")
        } ?: return@LaunchedEffect
        val key = "${entry.timestamp}:${entry.message}"
        if (key == lastHandledScpResultLog) return@LaunchedEffect
        lastHandledScpResultLog = key
        val latest = entry.message
        when {
            latest.startsWith("Remote move completed:") -> {
                scpTransferStatus = "Move completed successfully."
                browseScpPath(scpLastListedPath, recordHistory = false, clearStatus = false)
            }
            latest.startsWith("Remote delete completed:") -> {
                scpTransferStatus = "Delete completed successfully."
                browseScpPath(scpLastListedPath, recordHistory = false, clearStatus = false)
            }
            latest.startsWith("Remote mkdir completed:") -> {
                scpTransferStatus = "Folder created successfully."
                browseScpPath(scpLastListedPath, recordHistory = false, clearStatus = false)
            }
            latest.startsWith("SFTP operation failed:") -> {
                scpTransferStatus = "Operation failed. ${latest.substringAfter(':', "").trim()}"
            }
        }
    }
    LaunchedEffect(
        activeFileTransfer?.operationId,
        activeFileTransfer?.status,
        request?.mode
    ) {
        val transfer = activeFileTransfer ?: return@LaunchedEffect
        if (transfer.isActive) {
            if (request?.mode == ConnectionMode.SCP) scpTransferStatus = null
            if (request?.mode == ConnectionMode.SFTP) sftpTransferStatus = null
            return@LaunchedEffect
        }
        val transferId = transfer.operationId.ifBlank {
            "${transfer.sessionId}:${transfer.direction}:${transfer.completedAtEpochMillis}"
        }
        if (lastHandledTerminalTransferId == transferId) return@LaunchedEffect
        lastHandledTerminalTransferId = transferId
        if (
            request?.mode == ConnectionMode.SCP &&
            transfer.status == FileTransferStatus.SUCCEEDED &&
            transfer.direction == FileTransferDirection.UPLOAD
        ) {
            browseScpPath(scpLastListedPath, recordHistory = false, clearStatus = false)
        }
        if (request?.mode == ConnectionMode.SFTP) {
            onShowMessage(transfer.statusMessage())
        }
    }
    LaunchedEffect(terminalProfile) {
        terminalEngine.applyProfile(terminalProfile)
        externalTerminalEmulator?.let { emulator ->
            TermuxTerminalEngine.applyProfileToEmulator(emulator, terminalProfile)
        }
        terminalViewRef?.let { view ->
            applyTerminalTypeface(view, terminalProfile.font)
        }
        terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) })
        terminalViewRef?.onScreenUpdated()
    }
    DisposableEffect(request?.sessionId) {
        onDispose {
            swipeRepeatJob?.cancel()
            swipeRepeatJob = null
            swipeRepeatKeyCode = null
            terminalViewRef = null
            terminalImeBridgeRef = null
        }
    }
    DisposableEffect(activity, request?.sessionId) {
        activity?.setHardwareKeyHandler { event -> activityHardwareKeyHandler(event) }
        onDispose {
            activity?.setHardwareKeyHandler(null)
        }
    }
    DisposableEffect(lifecycleOwner, request?.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val profile = currentTerminalProfile.value
                    terminalEngine.applyProfile(profile)
                    terminalViewRef?.let { view ->
                        view.mEmulator?.let { emulator ->
                            TermuxTerminalEngine.applyProfileToEmulator(emulator, profile)
                        }
                        applyTerminalTypeface(view, profile.font)
                        view.setTextSize(
                            with(density) {
                                currentTerminalFontSizeSp.value.sp.toPx().toInt().coerceAtLeast(6)
                            }
                        )
                        view.onScreenUpdated()
                    }
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    hideSystemKeyboard()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hideSystemKeyboard()
        }
    }
    LaunchedEffect(shellOutput, request?.sessionId, hasExternalTerminalEmulator) {
        if (request == null) return@LaunchedEffect
        if (hasExternalTerminalEmulator) {
            terminalViewRef?.onScreenUpdated()
            return@LaunchedEffect
        }
        val snapshot = shellOutput
        if (snapshot.startsWith(lastShellSnapshot)) {
            val delta = snapshot.substring(lastShellSnapshot.length)
            if (delta.isNotEmpty()) {
                if (diagnosticsLoggingEnabled) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = request.sessionId,
                            level = SessionLogBus.LogLevel.DEBUG,
                            message = "TERM-REPLAY append-delta prev=${lastShellSnapshot.length} next=${snapshot.length} delta=${delta.length}"
                        )
                    )
                }
                terminalEngine.appendIncoming(delta.toByteArray(StandardCharsets.UTF_8))
            }
        } else {
            if (diagnosticsLoggingEnabled) {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = request.sessionId,
                        level = SessionLogBus.LogLevel.DEBUG,
                        message = "TERM-REPLAY reset-snapshot prev=${lastShellSnapshot.length} next=${snapshot.length}"
                    )
                )
            }
            terminalEngine.reset()
            if (snapshot.isNotEmpty()) {
                terminalEngine.appendIncoming(snapshot.toByteArray(StandardCharsets.UTF_8))
            }
        }
        lastShellSnapshot = snapshot
        terminalViewRef?.onScreenUpdated()
    }
    LaunchedEffect(state.phase, request?.sessionId) {
        if (showTerminalSession) {
            hideSystemKeyboard()
        } else {
            hideSystemKeyboard()
        }
    }
    LaunchedEffect(findRequestToken, showTerminalSession) {
        if (findRequestToken > 0 && showTerminalSession) {
            showFindDialog = true
        }
    }
    LaunchedEffect(findQuery, findCaseSensitive, showFindDialog) {
        if (showFindDialog) {
            findMatchIndex = 0
        }
    }
    LaunchedEffect(findMatches.size, showFindDialog) {
        if (!showFindDialog || findMatches.isEmpty()) {
            findMatchIndex = 0
        } else if (findMatchIndex !in findMatches.indices) {
            findMatchIndex = 0
        }
    }
    LaunchedEffect(showFindDialog, activeFindMatch, terminalViewRef) {
        val view = terminalViewRef ?: return@LaunchedEffect
        if (!showFindDialog) {
            view.clearSearchHighlight()
            view.onScreenUpdated()
            return@LaunchedEffect
        }
        val match = activeFindMatch
        if (match == null || findQuery.isBlank()) {
            view.clearSearchHighlight()
            view.onScreenUpdated()
            return@LaunchedEffect
        }
        val startRow = match.rowStart
        val startColumn = match.columnStart
        if (startRow == null || startColumn == null) {
            view.clearSearchHighlight()
            view.onScreenUpdated()
            return@LaunchedEffect
        }
        val endRow = match.rowEnd ?: startRow
        val endColumnExclusive = (match.columnEndExclusive ?: (startColumn + 1)).coerceAtLeast(startColumn + 1)
        view.setSearchHighlight(startRow, startColumn, endRow, endColumnExclusive - 1)
        view.revealTranscriptRow(startRow)
        view.onScreenUpdated()
    }
    LaunchedEffect(remoteDirectory?.path, remoteDirectory?.entries, request?.sessionId, request?.mode) {
        val snapshot = remoteDirectory ?: return@LaunchedEffect
        val path = snapshot.path
        when (request?.mode) {
            ConnectionMode.SFTP -> sftpPath = path
            ConnectionMode.SCP -> {
                if (scpHomePath == null && (scpPendingListPath == "." || scpLastListedPath == ".")) {
                    scpHomePath = path
                }
                scpRemotePath = path
                scpLastListedPath = path
                scpVisibleEntries = snapshot.entries
            }
            ConnectionMode.SSH, null -> Unit
        }
    }
    LaunchedEffect(
        remoteDirectory?.path,
        remoteDirectory?.refreshToken,
        request?.sessionId,
        request?.mode,
        scpPendingListPath,
        scpPendingListBaselineToken
    ) {
        if (request?.mode != ConnectionMode.SCP) return@LaunchedEffect
        val pendingPath = scpPendingListPath ?: return@LaunchedEffect
        val snapshot = remoteDirectory ?: return@LaunchedEffect
        if (scpPendingListBaselineToken == snapshot.refreshToken) return@LaunchedEffect
        if (pendingPath == ".") {
            scpHomePath = snapshot.path
        }
        if (snapshot.path != pendingPath) {
            scpRemotePath = snapshot.path
            scpLastListedPath = snapshot.path
            if (scpPathHistoryIndex in scpPathHistory.indices && scpPathHistory[scpPathHistoryIndex] == pendingPath) {
                scpPathHistory[scpPathHistoryIndex] = snapshot.path
            } else if (scpPathHistory.lastOrNull() == pendingPath) {
                scpPathHistory[scpPathHistory.lastIndex] = snapshot.path
                scpPathHistoryIndex = scpPathHistory.lastIndex
            }
        }
        if (scpPendingManualPathTarget == pendingPath) {
            scpPendingManualPathTarget = null
            scpPendingManualPathFallback = null
        }
        if (scpPendingLinkPathTarget == pendingPath) {
            scpPendingLinkPathTarget = null
            scpPendingLinkPathFallback = null
        }
        scpVisibleEntries = snapshot.entries
        scpActivityLines.clear()
        scpActivityLines += "Listing ${snapshot.path}:"
        if (snapshot.entries.isEmpty()) {
            scpActivityLines += "(empty)"
        } else {
            snapshot.entries.forEach { entry ->
                val marker = if (entry.isDirectory) "d" else "-"
                scpActivityLines += "$marker ${entry.sizeBytes} ${entry.name}"
            }
        }
        scpPendingListPath = null
        scpPendingListBaselineToken = null
    }
    LaunchedEffect(logs.size, request?.mode, scpPendingListPath) {
        if (request?.mode != ConnectionMode.SCP) return@LaunchedEffect
        val failedPendingPath = scpPendingListPath ?: return@LaunchedEffect
        if (logs.isEmpty()) return@LaunchedEffect
        val latest = logs.last().message
        if (
            latest.startsWith("Directory listing failed for") ||
            latest.startsWith("SFTP operation failed:")
        ) {
            val manualFallback = scpPendingManualPathFallback
            val linkFallback = scpPendingLinkPathFallback
            val shouldRestoreManualPath =
                scpPendingManualPathTarget == failedPendingPath && !manualFallback.isNullOrBlank()
            val shouldRestoreLinkPath =
                scpPendingLinkPathTarget == failedPendingPath && !linkFallback.isNullOrBlank()
            scpPendingListPath = null
            scpPendingListBaselineToken = null
            scpActivityLines.clear()
            scpActivityLines += latest
            scpTransferStatus = latest
            when {
                shouldRestoreManualPath -> {
                    val fallbackPath = manualFallback ?: return@LaunchedEffect
                    if (scpPathHistoryIndex in scpPathHistory.indices && scpPathHistory[scpPathHistoryIndex] == failedPendingPath) {
                        scpPathHistory.removeAt(scpPathHistoryIndex)
                        if (scpPathHistory.isEmpty()) {
                            scpPathHistory += fallbackPath
                        }
                        scpPathHistoryIndex = scpPathHistory.lastIndex.coerceAtLeast(0)
                    }
                    scpRemotePath = fallbackPath
                    scpLastListedPath = fallbackPath
                    scpPendingManualPathTarget = null
                    scpPendingManualPathFallback = null
                    Toast.makeText(context, "Couldn't open $failedPendingPath", Toast.LENGTH_SHORT).show()
                }
                shouldRestoreLinkPath -> {
                    val fallbackPath = linkFallback ?: return@LaunchedEffect
                    if (scpPathHistoryIndex in scpPathHistory.indices && scpPathHistory[scpPathHistoryIndex] == failedPendingPath) {
                        scpPathHistory.removeAt(scpPathHistoryIndex)
                        if (scpPathHistory.isEmpty()) {
                            scpPathHistory += fallbackPath
                        }
                        scpPathHistoryIndex = scpPathHistory.lastIndex.coerceAtLeast(0)
                    }
                    scpRemotePath = fallbackPath
                    scpLastListedPath = fallbackPath
                    scpPendingLinkPathTarget = null
                    scpPendingLinkPathFallback = null
                    Toast.makeText(context, "Broken link: $failedPendingPath", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun inferRemoteDestination(localPath: String, currentDir: String): String {
        val fileName = localPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "upload.bin" }
        val base = currentDir.trim().ifBlank { "." }
        return if (base.endsWith("/")) "$base$fileName" else "$base/$fileName"
    }

    fun resolveChildPath(base: String, child: String): String {
        val cleanBase = base.trim().ifBlank { "." }
        if (child.startsWith("/")) return child
        return if (cleanBase.endsWith("/")) "$cleanBase$child" else "$cleanBase/$child"
    }

    fun parentPath(path: String): String {
        val normalized = path.trim().ifBlank { "." }
        if (normalized == "." || normalized == "/") return normalized
        val trimmed = normalized.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = ".")
        return if (parent.isBlank()) "/" else parent
    }

    fun listLocalFiles(path: String): List<File> {
        val dir = runCatching { File(path) }.getOrNull() ?: return emptyList()
        val list = runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
        return list.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun appendSftpConsole(line: String) {
        if (sftpConsoleLines.size >= SFTP_CONSOLE_MAX_LINES) {
            sftpConsoleLines.removeAt(0)
        }
        sftpConsoleLines += line
        sftpConsoleRevision += 1L
    }

    fun appendSftpConsoleLines(lines: List<String>) {
        if (lines.isEmpty()) return
        val incoming = lines.takeLast(SFTP_CONSOLE_MAX_LINES)
        val retainedLineCount = (SFTP_CONSOLE_MAX_LINES - incoming.size).coerceAtLeast(0)
        val updated = buildList {
            addAll(sftpConsoleLines.takeLast(retainedLineCount))
            addAll(incoming)
        }
        sftpConsoleLines.clear()
        sftpConsoleLines.addAll(updated)
        sftpConsoleRevision += 1L
    }

    fun appendScpActivity(line: String, clearFirst: Boolean = false) {
        if (clearFirst) {
            scpActivityLines.clear()
        }
        scpActivityLines += line
        val overflow = scpActivityLines.size - 500
        if (overflow > 0) {
            repeat(overflow) { scpActivityLines.removeAt(0) }
        }
    }

    fun resolveRemotePath(current: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == ".") return current
        if (trimmed == "..") return parentPath(current)
        if (trimmed.startsWith("/")) return trimmed
        var base = current
        var remainder = trimmed
        while (remainder.startsWith("../")) {
            base = parentPath(base)
            remainder = remainder.removePrefix("../")
        }
        val normalized = remainder.removePrefix("./")
        return if (normalized.isBlank()) base else resolveChildPath(base, normalized)
    }

    LaunchedEffect(
        remoteDirectory?.path,
        remoteDirectory?.refreshToken,
        request?.sessionId,
        request?.mode
    ) {
        if (request?.mode != ConnectionMode.SFTP) return@LaunchedEffect
        val snapshot = remoteDirectory ?: return@LaunchedEffect
        val key = sftpDirectoryRefreshKey(snapshot)
        if (key == sftpLastRenderedDirectoryKey) return@LaunchedEffect
        sftpLastRenderedDirectoryKey = key
        if (sftpPendingDirectoryEcho == null) return@LaunchedEffect
        val output = buildList {
            add("Remote directory: ${snapshot.path}")
            val listedEntries = snapshot.entries
                .let { entries -> if (sftpListShowAll) entries else entries.filterNot { it.name.startsWith(".") } }
            if (listedEntries.isEmpty()) {
                add("(empty)")
            } else {
                listedEntries
                    .take(SFTP_DIRECTORY_ENTRY_OUTPUT_LIMIT)
                    .forEach { entry ->
                        val label = if (entry.isDirectory) "d" else "-"
                        val size = entry.sizeBytes.toString()
                        add("$label $size ${entry.name}")
                    }
                val hiddenEntryCount =
                    listedEntries.size - SFTP_DIRECTORY_ENTRY_OUTPUT_LIMIT
                if (hiddenEntryCount > 0) {
                    add("… $hiddenEntryCount more entries not shown")
                }
            }
        }
        appendSftpConsoleLines(output)
        sftpPendingDirectoryEcho = null
    }
    LaunchedEffect(logs.size, request?.mode, sftpPendingDirectoryEcho, remoteDirectory?.path) {
        if (request?.mode != ConnectionMode.SFTP) return@LaunchedEffect
        val failedPendingPath = sftpPendingDirectoryEcho ?: return@LaunchedEffect
        if (logs.isEmpty()) return@LaunchedEffect
        val latest = logs.last().message
        if (
            latest.startsWith("Directory listing failed for") ||
            latest.startsWith("SFTP operation failed:")
        ) {
            sftpPendingDirectoryEcho = null
            remoteDirectory?.path?.let { sftpPath = it }
            appendSftpConsole(latest)
            val failureDetail = latest
                .substringAfter(':', "")
                .trim()
                .takeIf { it.isNotBlank() && !it.equals(failedPendingPath, ignoreCase = false) }
            val toastMessage = buildString {
                append("Couldn't open ")
                append(failedPendingPath)
                if (failureDetail != null) {
                    append(": ")
                    append(failureDetail)
                }
            }
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun runSnippetOnCurrentSession(snippet: Snippet) {
        val command = snippet.command.trim()
        if (command.isBlank()) return
        val payload = if (command.endsWith("\n") || command.endsWith("\r")) command else "$command\r"
        terminalInput.sendRawSequence(payload)
    }

    fun sendCommandWithEnter(command: String) {
        if (command.isBlank()) return
        terminalInput.sendRawSequence("$command\r")
    }

    fun sendArrowKey(keyCode: Int): Boolean =
        runCatching {
            terminalInput.sendVirtualKey(
                keyCode = keyCode,
                ctrlDown = false,
                altDown = false,
                shiftDown = false,
                fallbackSequence = null
            )
        }.getOrDefault(false)

    fun stopSwipeRepeat() {
        swipeRepeatJob?.cancel()
        swipeRepeatJob = null
        swipeRepeatKeyCode = null
    }

    fun startSwipeRepeat(keyCode: Int) {
        if (swipeRepeatKeyCode == keyCode && swipeRepeatJob?.isActive == true) {
            return
        }
        swipeRepeatJob?.cancel()
        swipeRepeatKeyCode = keyCode
        if (!sendArrowKey(keyCode)) {
            stopSwipeRepeat()
            return
        }
        swipeRepeatJob = scope.launch {
            delay(SWIPE_NAV_REPEAT_INITIAL_DELAY_MS)
            while (isActive) {
                if (!sendArrowKey(keyCode)) {
                    stopSwipeRepeat()
                    break
                }
                delay(SWIPE_NAV_REPEAT_INTERVAL_MS)
            }
        }
    }

    fun resolveInjectPassword(): String {
        val current = request ?: return ""
        return if (current.savedHostId != null) {
            runCatching { SecurityManager.getHostPassword(current.savedHostId) }.getOrNull().orEmpty()
        } else {
            resolveRuntimeSessionPassword(current.sessionId).orEmpty()
        }
    }

    fun handleIconAlias(action: KeyboardSlotAction): Boolean {
        return when (action.iconId) {
            "code", "snippet_picker" -> {
                showSnippetPicker = true
                true
            }
            "key" -> {
                val password = resolveInjectPassword()
                if (password.isNotEmpty()) {
                    terminalInput.sendText(
                        text = password,
                        ctrlDown = false,
                        altDown = false,
                        shiftDown = false
                    )
                }
                true
            }
            "swipe_nav" -> {
                swipeNavigationEnabled = !swipeNavigationEnabled
                swipeStart = null
                swipeIntercepting = false
                if (!swipeNavigationEnabled) {
                    stopSwipeRepeat()
                }
                true
            }
            "folder" -> {
                sendCommandWithEnter("pwd")
                true
            }
            "home" -> {
                sendCommandWithEnter("cd")
                true
            }
            "reset" -> {
                sendCommandWithEnter("reset")
                true
            }
            "keyboard" -> {
                if (supportsSystemKeyboard) {
                    toggleSystemKeyboard()
                }
                true
            }
            "terminal" -> {
                terminalInput.sendRawSequence("\u001A")
                true
            }
            "build", "settings" -> {
                onOpenSettings()
                true
            }
            "search" -> {
                showFindDialog = true
                true
            }
            "fn", "fn_active", "fn_back" -> {
                isFnRowVisible = !isFnRowVisible
                true
            }
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun updateTerminalView(view: TerminalView) {
        terminalViewRef = view
        view.setTerminalViewClient(terminalViewClient)
        view.setSelectionJoinBackLines(
            terminalSelectionMode == TerminalSelectionMode.NATURAL
        )
        val emulator = externalTerminalEmulator ?: terminalEngine.emulator()
        view.attachEmulator(emulator)
        TermuxTerminalEngine.applyProfileToEmulator(emulator, terminalProfile)
        applyTerminalTypeface(view, terminalProfile.font)
        val textSizePx = with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) }
        view.setTextSize(textSizePx)
        view.updateSize()
        recordTerminalResize(emulator.mColumns, emulator.mRows)
        view.setOnTouchListener { touchedView, event ->
            if (!swipeNavigationEnabled) {
                swipeStart = null
                swipeIntercepting = false
                stopSwipeRepeat()
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStart = SwipeGestureStart(
                        x = event.x,
                        y = event.y,
                        timestampMs = event.eventTime
                    )
                    swipeIntercepting = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val start = swipeStart
                    if (start != null && !swipeIntercepting) {
                        val dx = abs(event.x - start.x)
                        val dy = abs(event.y - start.y)
                        if (dx >= swipeNavMinDistancePx || dy >= swipeNavMinDistancePx) {
                            swipeIntercepting = true
                        }
                    }
                    if (swipeIntercepting && start != null) {
                        val keyCode = resolveSwipeKeyCode(
                            start = start,
                            endX = event.x,
                            endY = event.y,
                            endTimeMs = event.eventTime,
                            minDistancePx = swipeNavMinDistancePx,
                            maxDurationMs = null
                        )
                        if (keyCode != null) {
                            startSwipeRepeat(keyCode)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val start = swipeStart
                    if (start != null && swipeIntercepting && swipeRepeatKeyCode == null) {
                        val keyCode = resolveSwipeKeyCode(
                            start = start,
                            endX = event.x,
                            endY = event.y,
                            endTimeMs = event.eventTime,
                            minDistancePx = swipeNavMinDistancePx
                        )
                        if (keyCode != null) {
                            sendArrowKey(keyCode)
                        }
                    } else if (start != null) {
                        touchedView.performClick()
                    }
                    stopSwipeRepeat()
                    swipeStart = null
                    val consumed = true
                    swipeIntercepting = false
                    consumed
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopSwipeRepeat()
                    swipeStart = null
                    val consumed = true
                    swipeIntercepting = false
                    consumed
                }
                else -> true
            }
        }
        view.onScreenUpdated()
    }

    fun dismissFindDialog() {
        showFindDialog = false
        terminalViewRef?.clearSearchHighlight()
    }

    fun handleCompactKeyPress(key: CompactTerminalKey) {
        if (!key.enabled) return
        if (terminalViewRef?.isSelectingText == true) {
            terminalViewRef?.stopTextSelectionMode()
        }
        val action = key.action
        if (handleIconAlias(action)) {
            if (action.iconId != "fn" && action.iconId != "fn_active") {
                pendingModifiers = emptySet()
            }
            return
        }
        when (action.type) {
            KeyboardActionType.MODIFIER -> {
                val modifier = action.modifier ?: return
                pendingModifiers = if (pendingModifiers.contains(modifier)) {
                    pendingModifiers - modifier
                } else {
                    pendingModifiers + modifier
                }
            }
            KeyboardActionType.TEXT -> {
                val text = action.text
                if (text.isBlank()) return
                val modifiers = pendingModifiers
                terminalInput.sendText(
                    text = text,
                    ctrlDown = modifiers.contains(KeyboardModifier.CTRL),
                    altDown = modifiers.contains(KeyboardModifier.ALT),
                    shiftDown = modifiers.contains(KeyboardModifier.SHIFT)
                )
                pendingModifiers = emptySet()
            }
            KeyboardActionType.KEY -> {
                val keyCode = action.keyCode ?: return
                val modifiers = pendingModifiers
                val sent = terminalInput.sendVirtualKey(
                    keyCode = keyCode,
                    ctrlDown = modifiers.contains(KeyboardModifier.CTRL) || action.ctrl,
                    altDown = modifiers.contains(KeyboardModifier.ALT) || action.alt,
                    shiftDown = modifiers.contains(KeyboardModifier.SHIFT) || action.shift,
                    fallbackSequence = action.sequence.ifBlank { null }
                )
                if (!sent && action.sequence.isNotBlank()) {
                    terminalInput.sendRawSequence(action.sequence)
                }
                pendingModifiers = emptySet()
            }
            KeyboardActionType.SEQUENCE -> {
                val sequence = action.sequence.ifBlank { action.text }
                if (sequence.isNotBlank()) {
                    terminalInput.sendRawSequence(sequence)
                }
                pendingModifiers = emptySet()
            }
            KeyboardActionType.PASSWORD_INJECT -> {
                val password = resolveInjectPassword()
                if (password.isNotEmpty()) {
                    terminalInput.sendText(
                        text = password,
                        ctrlDown = false,
                        altDown = false,
                        shiftDown = false
                    )
                }
                pendingModifiers = emptySet()
            }
            KeyboardActionType.SNIPPET_PICKER -> {
                showSnippetPicker = true
                pendingModifiers = emptySet()
            }
        }
    }

    fun handleTerminalImeText(text: String) {
        if (text.isEmpty()) return
        val modifiers = pendingModifiers
        terminalInput.sendText(
            text = text,
            ctrlDown = modifiers.contains(KeyboardModifier.CTRL),
            altDown = modifiers.contains(KeyboardModifier.ALT),
            shiftDown = modifiers.contains(KeyboardModifier.SHIFT)
        )
        if (modifiers.isNotEmpty()) {
            pendingModifiers = emptySet()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
            .testTag(UiTestTags.SCREEN_CONNECTING)
    ) {
        if (showTerminalSession) {
            ConnectingTerminalContent(
                terminalPanelColor = terminalPanelColor,
                terminalViewClient = terminalViewClient,
                terminalMarginDp = terminalMarginDp,
                showFindDialog = showFindDialog,
                findQuery = findQuery,
                onFindQueryChange = { findQuery = it },
                findCaseSensitive = findCaseSensitive,
                onFindCaseSensitiveChange = { findCaseSensitive = it },
                findMatches = findMatches,
                findMatchIndex = findMatchIndex,
                onFindMatchIndexChange = { findMatchIndex = it },
                activeFindMatch = activeFindMatch,
                onDismissFind = ::dismissFindDialog,
                compactKeys = compactKeys,
                pendingModifiers = pendingModifiers,
                activeAliasIcons = activeAliasIcons,
                onSendCompactKey = ::handleCompactKeyPress,
                onImeTextInput = ::handleTerminalImeText,
                keyboardVisibleRequested = keyboardVisibleRequested,
                supportsSystemKeyboard = supportsSystemKeyboard,
                keyboardFocusRequester = keyboardFocusRequester,
                onImeBridgeReady = { terminalImeBridgeRef = it },
                onKeyboardFocusChanged = { keyboardFocused = it },
                handleVolumeKeyForFontSize = ::handleVolumeKeyForFontSize,
                terminalInput = terminalInput,
                onAndroidKeyEvent = ::handleTerminalAndroidKeyEvent,
                updateTerminalView = ::updateTerminalView
            )
        } else if (showScpTransferSession && request != null) {
            ConnectingScpContent(
                sessionId = request.sessionId,
                remoteDirectory = remoteDirectory,
                scpVisibleEntries = scpVisibleEntries,
                scpLastListedPath = scpLastListedPath,
                scpPendingListPath = scpPendingListPath,
                scpPathHistory = scpPathHistory,
                scpPathHistoryIndex = scpPathHistoryIndex,
                onScpPathHistoryIndexChange = { scpPathHistoryIndex = it },
                scpSelectedPath = scpSelectedPath,
                onScpSelectedPathChange = { scpSelectedPath = it },
                scpRemotePath = scpRemotePath,
                onScpRemotePathChange = { scpRemotePath = it },
                pendingScpDownloadRemotePath = pendingScpDownloadRemotePath,
                onPendingScpDownloadRemotePathChange = { pendingScpDownloadRemotePath = it },
                scpTransferStatus = scpTransferStatus,
                onScpTransferStatusChange = { scpTransferStatus = it },
                showScpUploadVertical = showScpUploadVertical,
                scpActionsExpanded = scpActionsExpanded,
                onScpActionsExpandedChange = { scpActionsExpanded = it },
                onScpPendingManualPathTargetChange = { scpPendingManualPathTarget = it },
                onScpPendingManualPathFallbackChange = { scpPendingManualPathFallback = it },
                onScpPendingLinkPathTargetChange = { scpPendingLinkPathTarget = it },
                onScpPendingLinkPathFallbackChange = { scpPendingLinkPathFallback = it },
                scpHomePath = scpHomePath,
                showScpRenameDialog = showScpRenameDialog,
                onShowScpRenameDialogChange = { showScpRenameDialog = it },
                scpRenameValue = scpRenameValue,
                onScpRenameValueChange = { scpRenameValue = it },
                showScpMoveDialog = showScpMoveDialog,
                onShowScpMoveDialogChange = { showScpMoveDialog = it },
                scpMoveDestination = scpMoveDestination,
                onScpMoveDestinationChange = { scpMoveDestination = it },
                showScpNewFolderDialog = showScpNewFolderDialog,
                onShowScpNewFolderDialogChange = { showScpNewFolderDialog = it },
                scpNewFolderValue = scpNewFolderValue,
                onScpNewFolderValueChange = { scpNewFolderValue = it },
                showScpDeleteDialog = showScpDeleteDialog,
                onShowScpDeleteDialogChange = { showScpDeleteDialog = it },
                activeFileTransfer = activeFileTransfer,
                scpTransferActive = scpTransferActive,
                context = context,
                hideKeyboard = { keyboardController?.hide() },
                onCopyRemotePath = { clipboardManager.setText(AnnotatedString(it)) },
                browseScpPath = ::browseScpPath,
                onScpDownload = onScpDownload,
                onScpUpload = onScpUpload,
                onCancelFileTransfer = onCancelFileTransfer,
                onManageRemotePath = onManageRemotePath,
                inferRemoteDestination = ::inferRemoteDestination,
                resolveChildPath = ::resolveChildPath,
                parentPath = ::parentPath
            )
        } else if (showSftpCliSession && request != null) {
            ConnectingSftpContent(
                sessionId = request.sessionId,
                remoteDirectory = remoteDirectory,
                logs = logs,
                context = context,
                activeFileTransfer = activeFileTransfer,
                sftpTransferActive = sftpTransferActive,
                sftpTransferStatus = sftpTransferStatus,
                sftpPath = sftpPath,
                onSftpPathChange = { sftpPath = it },
                sftpLocalPath = sftpLocalPath,
                onSftpLocalPathChange = { sftpLocalPath = it },
                sftpConsoleLines = sftpConsoleLines,
                sftpConsoleRevision = sftpConsoleRevision,
                appendSftpConsole = ::appendSftpConsole,
                clearSftpConsole = {
                    sftpConsoleLines.clear()
                    sftpConsoleRevision += 1L
                },
                sftpCommandInput = sftpCommandInput,
                onSftpCommandInputChange = { sftpCommandInput = it },
                onSftpPendingDirectoryEchoChange = { sftpPendingDirectoryEcho = it },
                pendingSftpDownloadRemotePath = pendingSftpDownloadRemotePath,
                onPendingSftpDownloadRemotePathChange = { pendingSftpDownloadRemotePath = it },
                pendingSftpUploadBasePath = pendingSftpUploadBasePath,
                onPendingSftpUploadBasePathChange = { pendingSftpUploadBasePath = it },
                sftpCommandRunning = sftpCommandRunning,
                onSftpCommandRunningChange = { sftpCommandRunning = it },
                sftpAwaitDirectoryRefresh = sftpAwaitDirectoryRefresh,
                onSftpAwaitDirectoryRefreshChange = { sftpAwaitDirectoryRefresh = it },
                sftpCommandStartLogCount = sftpCommandStartLogCount,
                onSftpCommandStartLogCountChange = { sftpCommandStartLogCount = it },
                sftpCommandStartDirectoryKey = sftpCommandStartDirectoryKey,
                onSftpCommandStartDirectoryKeyChange = { sftpCommandStartDirectoryKey = it },
                sftpListShowAll = sftpListShowAll,
                onSftpListShowAllChange = { sftpListShowAll = it },
                onSftpListDirectory = onSftpListDirectory,
                onSftpDownload = onSftpDownload,
                onSftpUpload = onSftpUpload,
                onCancelFileTransfer = onCancelFileTransfer,
                onManageRemotePath = onManageRemotePath,
                inferRemoteDestination = ::inferRemoteDestination,
                resolveRemotePath = ::resolveRemotePath,
                listLocalFiles = ::listLocalFiles
            )
        } else if (request != null) {
            ConnectingStatusContent(
                state = state,
                statusText = statusText,
                statusColor = statusColor,
                hostName = hostName,
                detailLine = detailLine,
                userFacingStateMessage = userFacingStateMessage,
                activeFileTransfer = activeFileTransfer,
                renderedLogs = renderedLogs,
                listState = listState
            )
        }

        if (showSnippetPicker && showTerminalSession) {
            SnippetPickerDialog(
                snippets = snippets,
                onRunSnippet = {
                    runSnippetOnCurrentSession(it)
                    showSnippetPicker = false
                },
                onDismiss = { showSnippetPicker = false }
            )
        }
        if (state.phase == QuickConnectPhase.ERROR) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .testTag(UiTestTags.CONNECTING_RETRY_BUTTON)
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun ConnectingTerminalContent(
    terminalPanelColor: Color,
    terminalViewClient: TerminalViewClient,
    terminalMarginDp: Dp,
    showFindDialog: Boolean,
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    findCaseSensitive: Boolean,
    onFindCaseSensitiveChange: (Boolean) -> Unit,
    findMatches: List<TerminalFindMatch>,
    findMatchIndex: Int,
    onFindMatchIndexChange: (Int) -> Unit,
    activeFindMatch: TerminalFindMatch?,
    onDismissFind: () -> Unit,
    compactKeys: List<CompactTerminalKey>,
    pendingModifiers: Set<KeyboardModifier>,
    activeAliasIcons: Set<String>,
    onSendCompactKey: (CompactTerminalKey) -> Unit,
    onImeTextInput: (String) -> Unit,
    keyboardVisibleRequested: Boolean,
    supportsSystemKeyboard: Boolean,
    keyboardFocusRequester: FocusRequester,
    onImeBridgeReady: (TerminalImeBridgeEditText?) -> Unit,
    onKeyboardFocusChanged: (Boolean) -> Unit,
    handleVolumeKeyForFontSize: (KeyEvent) -> Boolean,
    terminalInput: TerminalInputRouter,
    onAndroidKeyEvent: (KeyEvent) -> Boolean,
    updateTerminalView: (TerminalView) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                stateDescription = if (keyboardVisibleRequested) {
                    KEYBOARD_REQUESTED_STATE
                } else {
                    KEYBOARD_HIDDEN_STATE
                }
            }
            .testTag(UiTestTags.CONNECTING_KEYBOARD_STATE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.ime
                        .exclude(WindowInsets.navigationBars)
                        .only(WindowInsetsSides.Bottom)
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(UiTestTags.CONNECTING_TERMINAL_PANEL),
                color = terminalPanelColor
            ) {
                AndroidView(
                    factory = {
                        TerminalView(it, null).apply {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            setTerminalViewClient(terminalViewClient)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(terminalMarginDp),
                    update = updateTerminalView
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showFindDialog) {
                    TerminalFindPanel(
                        findQuery = findQuery,
                        onFindQueryChange = onFindQueryChange,
                        findCaseSensitive = findCaseSensitive,
                        onFindCaseSensitiveChange = onFindCaseSensitiveChange,
                        findMatches = findMatches,
                        findMatchIndex = findMatchIndex,
                        onFindMatchIndexChange = onFindMatchIndexChange,
                        activeFindMatch = activeFindMatch,
                        onDismiss = onDismissFind
                    )
                }

                CompactKeyRow(
                    keys = compactKeys,
                    activeModifiers = pendingModifiers,
                    activeAliasIcons = activeAliasIcons,
                    onSendKey = onSendCompactKey,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        TerminalImeBridge(
            onTextInput = onImeTextInput,
            onBackspace = terminalInput::sendBackspace,
            supportsSystemKeyboard = supportsSystemKeyboard,
            onBridgeReady = onImeBridgeReady,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(1.dp)
                .alpha(0f)
                .focusRequester(keyboardFocusRequester)
                .onFocusChanged { onKeyboardFocusChanged(it.isFocused) },
            handleVolumeKeyForFontSize = handleVolumeKeyForFontSize,
            terminalInput = terminalInput,
            onAndroidKeyEvent = onAndroidKeyEvent
        )
    }
}

@Composable
private fun TerminalImeBridge(
    onTextInput: (String) -> Unit,
    onBackspace: () -> Unit,
    handleVolumeKeyForFontSize: (KeyEvent) -> Boolean,
    terminalInput: TerminalInputRouter,
    onAndroidKeyEvent: (KeyEvent) -> Boolean,
    supportsSystemKeyboard: Boolean,
    onBridgeReady: (TerminalImeBridgeEditText?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTextInput = rememberUpdatedState(onTextInput)
    val currentOnBackspace = rememberUpdatedState(onBackspace)
    val currentHandleVolumeKeyForFontSize = rememberUpdatedState(handleVolumeKeyForFontSize)
    val currentTerminalInput = rememberUpdatedState(terminalInput)
    val currentOnAndroidKeyEvent = rememberUpdatedState(onAndroidKeyEvent)
    val currentOnBridgeReady = rememberUpdatedState(onBridgeReady)

    DisposableEffect(Unit) {
        onDispose {
            currentOnBridgeReady.value(null)
        }
    }

    AndroidView(
        factory = { context ->
            TerminalImeBridgeEditText(context).apply {
                isFocusable = supportsSystemKeyboard
                isFocusableInTouchMode = supportsSystemKeyboard
                showSoftInputOnFocus = supportsSystemKeyboard
                isCursorVisible = false
                setTextColor(android.graphics.Color.TRANSPARENT)
                setHintTextColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                background = null
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                isSaveEnabled = false
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setRawInputType(TERMINAL_IME_INPUT_TYPE)
                imeOptions = TERMINAL_IME_OPTIONS
                privateImeOptions = TERMINAL_PRIVATE_IME_OPTIONS
                onPasteRequested = { currentTerminalInput.value.pasteFromClipboard() }
                onInlinePasteText = { currentTerminalInput.value.pasteText(it) }
                onCommittedText = { currentOnTextInput.value(normalizeImeChunk(it)) }
                onImeBackspace = { currentOnBackspace.value() }
                onImeForwardDelete = {
                    currentTerminalInput.value.sendVirtualKey(KeyEvent.KEYCODE_FORWARD_DEL)
                }

                var restoring = false

                fun restoreSentinel() {
                    restoring = true
                    setText(TERMINAL_IME_SENTINEL)
                    setSelection(TERMINAL_IME_SENTINEL.length)
                    restoring = false
                }

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        if (restoring) return
                        val raw = s?.toString().orEmpty()
                        when {
                            raw == TERMINAL_IME_SENTINEL -> setSelection(raw.length)
                            raw.isEmpty() -> {
                                currentOnBackspace.value()
                                restoreSentinel()
                            }
                            raw.startsWith(TERMINAL_IME_SENTINEL) -> {
                                raw.removePrefix(TERMINAL_IME_SENTINEL)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { currentOnTextInput.value(normalizeImeChunk(it)) }
                                restoreSentinel()
                            }
                            else -> {
                                raw.replace(TERMINAL_IME_SENTINEL, "")
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { currentOnTextInput.value(normalizeImeChunk(it)) }
                                restoreSentinel()
                            }
                        }
                    }
                })

                setOnKeyListener { _, _, event ->
                    if (currentHandleVolumeKeyForFontSize.value(event)) {
                        true
                    } else if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_MULTIPLE) {
                        currentOnAndroidKeyEvent.value(event)
                    } else {
                        false
                    }
                }

                restoreSentinel()
                currentOnBridgeReady.value(this)
            }
        },
        update = { bridge ->
            bridge.isFocusable = supportsSystemKeyboard
            bridge.isFocusableInTouchMode = supportsSystemKeyboard
            bridge.showSoftInputOnFocus = supportsSystemKeyboard
            bridge.onPasteRequested = { currentTerminalInput.value.pasteFromClipboard() }
            bridge.onInlinePasteText = { currentTerminalInput.value.pasteText(it) }
            bridge.onCommittedText = { currentOnTextInput.value(normalizeImeChunk(it)) }
            bridge.onImeBackspace = { currentOnBackspace.value() }
            bridge.onImeForwardDelete = {
                currentTerminalInput.value.sendVirtualKey(KeyEvent.KEYCODE_FORWARD_DEL)
            }
            currentOnBridgeReady.value(bridge)
        },
        modifier = modifier
    )
}

private class TerminalImeBridgeEditText(context: Context) : AppCompatEditText(context) {
    var onPasteRequested: (() -> Boolean)? = null
    var onInlinePasteText: ((String) -> Boolean)? = null
    var onCommittedText: ((String) -> Unit)? = null
    var onImeBackspace: (() -> Unit)? = null
    var onImeForwardDelete: (() -> Unit)? = null

    fun showTerminalKeyboard() {
        fun showNow() {
            showSoftInputOnFocus = true
            requestFocus()
            post {
                if (isAttachedToWindow) {
                    requestFocus()
                    inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        if (isAttachedToWindow) {
            showNow()
        } else {
            post {
                if (isAttachedToWindow) showNow()
            }
        }
    }

    fun hideTerminalKeyboard() {
        inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.inputType = TERMINAL_IME_INPUT_TYPE
        outAttrs.imeOptions = TERMINAL_IME_OPTIONS
        outAttrs.privateImeOptions = TERMINAL_PRIVATE_IME_OPTIONS
        EditorInfoCompat.setContentMimeTypes(outAttrs, TERMINAL_IME_CONTENT_MIME_TYPES)
        val shadow = SpannableStringBuilder()
        Selection.setSelection(shadow, 0)
        return object : BaseInputConnection(this@TerminalImeBridgeEditText, true) {
            override fun getEditable(): Editable = shadow

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                return super.setComposingText(text, newCursorPosition).also { reportSelection() }
            }

            override fun setComposingRegion(start: Int, end: Int): Boolean {
                return super.setComposingRegion(start, end).also { reportSelection() }
            }

            override fun setSelection(start: Int, end: Int): Boolean {
                return super.setSelection(start, end).also { reportSelection() }
            }

            override fun finishComposingText(): Boolean {
                val finished = super.finishComposingText()
                flushPendingText()
                return finished
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val committed = super.commitText(text, newCursorPosition)
                flushPendingText()
                return committed
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val protected = protectedRange()
                val localBefore = protected.first
                val localAfter = shadow.length - protected.last
                val deleted = super.deleteSurroundingText(beforeLength, afterLength)
                repeat((beforeLength - localBefore).coerceAtLeast(0)) { onImeBackspace?.invoke() }
                repeat((afterLength - localAfter).coerceAtLeast(0)) { onImeForwardDelete?.invoke() }
                reportSelection()
                return deleted
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                val protected = protectedRange()
                val localBefore = Character.codePointCount(shadow, 0, protected.first)
                val localAfter = Character.codePointCount(shadow, protected.last, shadow.length)
                val deleted = super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                repeat((beforeLength - localBefore).coerceAtLeast(0)) { onImeBackspace?.invoke() }
                repeat((afterLength - localAfter).coerceAtLeast(0)) { onImeForwardDelete?.invoke() }
                reportSelection()
                return deleted
            }

            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
                ExtractedText().apply {
                    text = shadow.toString()
                    startOffset = 0
                    partialStartOffset = -1
                    partialEndOffset = -1
                    selectionStart = Selection.getSelectionStart(shadow).coerceAtLeast(0)
                    selectionEnd = Selection.getSelectionEnd(shadow).coerceAtLeast(0)
                }

            override fun performContextMenuAction(id: Int): Boolean {
                if (id == android.R.id.copy && copySelectedTextToClipboard()) {
                    return true
                }
                if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                    if (onPasteRequested?.invoke() == true) return true
                }
                return super.performContextMenuAction(id)
            }

            override fun commitContent(
                inputContentInfo: InputContentInfo,
                flags: Int,
                opts: Bundle?
            ): Boolean {
                val text = inputContentInfo.readTerminalText(context, flags)
                if (!text.isNullOrEmpty() && onInlinePasteText?.invoke(text) == true) {
                    return true
                }
                return super.commitContent(inputContentInfo, flags, opts)
            }

            private fun protectedRange(): IntRange {
                val selectionStart = Selection.getSelectionStart(shadow).coerceIn(0, shadow.length)
                val selectionEnd = Selection.getSelectionEnd(shadow).coerceIn(0, shadow.length)
                var start = minOf(selectionStart, selectionEnd)
                var end = maxOf(selectionStart, selectionEnd)
                val composingStart = getComposingSpanStart(shadow)
                val composingEnd = getComposingSpanEnd(shadow)
                if (composingStart >= 0 && composingEnd >= 0) {
                    start = minOf(start, composingStart, composingEnd)
                    end = maxOf(end, composingStart, composingEnd)
                }
                return start..end
            }

            private fun copySelectedTextToClipboard(): Boolean {
                val selectionStart = Selection.getSelectionStart(shadow)
                val selectionEnd = Selection.getSelectionEnd(shadow)
                if (selectionStart < 0 || selectionEnd < 0 || selectionStart == selectionEnd) {
                    return false
                }
                val start = minOf(selectionStart, selectionEnd).coerceIn(0, shadow.length)
                val end = maxOf(selectionStart, selectionEnd).coerceIn(0, shadow.length)
                if (start == end) return false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return false
                val clipData = ClipData.newPlainText(
                    "Terminal input",
                    shadow.subSequence(start, end)
                ).apply {
                    description.extras = PersistableBundle().apply {
                        putBoolean(TERMINAL_CLIPBOARD_SENSITIVE_EXTRA, true)
                        // AOSP SystemUI honors this only on emulators; physical devices
                        // safely ignore the private hint.
                        putBoolean(TERMINAL_CLIPBOARD_SUPPRESS_OVERLAY_EXTRA, true)
                    }
                }
                clipboard.setPrimaryClip(
                    clipData
                )
                return true
            }

            private fun flushPendingText() {
                val pending = shadow.toString()
                removeComposingSpans(shadow)
                shadow.clear()
                Selection.setSelection(shadow, 0)
                reportSelection()
                if (pending.isNotEmpty()) {
                    onCommittedText?.invoke(pending)
                }
            }

            private fun reportSelection() {
                val selectionStart = Selection.getSelectionStart(shadow).coerceAtLeast(0)
                val selectionEnd = Selection.getSelectionEnd(shadow).coerceAtLeast(0)
                inputMethodManager()?.updateSelection(
                    this@TerminalImeBridgeEditText,
                    selectionStart,
                    selectionEnd,
                    getComposingSpanStart(shadow),
                    getComposingSpanEnd(shadow)
                )
            }
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            if (onPasteRequested?.invoke() == true) return true
        }
        return super.onTextContextMenuItem(id)
    }

    private fun inputMethodManager(): InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
}

private fun InputContentInfo.readTerminalText(context: Context, flags: Int): String? {
    if (!description.hasMimeType("text/*")) return null
    val permissionRequested =
        flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0 &&
            runCatching {
                requestPermission()
                true
            }.getOrDefault(false)
    return try {
        context.contentResolver.openInputStream(contentUri)
            ?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(TERMINAL_IME_CONTENT_READ_BUFFER_BYTES)
                var remaining = TERMINAL_IME_INLINE_PASTE_MAX_BYTES
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                String(output.toByteArray(), StandardCharsets.UTF_8)
            }
            ?.takeIf { it.isNotEmpty() }
    } finally {
        if (permissionRequested) {
            runCatching { releasePermission() }
        }
    }
}

@Composable
private fun TerminalFindPanel(
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    findCaseSensitive: Boolean,
    onFindCaseSensitiveChange: (Boolean) -> Unit,
    findMatches: List<TerminalFindMatch>,
    findMatchIndex: Int,
    onFindMatchIndexChange: (Int) -> Unit,
    activeFindMatch: TerminalFindMatch?,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .border(
                            width = 1.dp,
                            color = colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = findQuery,
                            onValueChange = onFindQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Search,
                                autoCorrect = false
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.CONNECTING_FIND_INPUT),
                            decorationBox = { inner ->
                                if (findQuery.isBlank()) {
                                    Text(
                                        text = "Find",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onFindCaseSensitiveChange(!findCaseSensitive) },
                    color = if (findCaseSensitive) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aa",
                            color = if (findCaseSensitive) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (findMatches.isNotEmpty()) {
                            val size = findMatches.size
                            onFindMatchIndexChange(((findMatchIndex - 1) % size + size) % size)
                        }
                    },
                    enabled = findMatches.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous result",
                        tint = if (findMatches.isNotEmpty()) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        if (findMatches.isNotEmpty()) {
                            onFindMatchIndexChange((findMatchIndex + 1).mod(findMatches.size))
                        }
                    },
                    enabled = findMatches.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next result",
                        tint = if (findMatches.isNotEmpty()) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close find",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = when {
                    findQuery.isBlank() -> "Enter search text"
                    findMatches.isEmpty() -> "No matches"
                    else -> {
                        val activeIndex = findMatchIndex.coerceIn(0, findMatches.lastIndex)
                        if (activeFindMatch != null) {
                            "${activeIndex + 1}/${findMatches.size} line ${activeFindMatch.line}: ${activeFindMatch.preview}"
                        } else {
                            "${activeIndex + 1}/${findMatches.size}"
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(UiTestTags.CONNECTING_FIND_STATUS)
            )
        }
    }
}

@Composable
private fun ConnectingScpContent(
    sessionId: String,
    remoteDirectory: RemoteDirectorySnapshot?,
    scpVisibleEntries: List<RemoteDirectoryEntry>,
    scpLastListedPath: String,
    scpPendingListPath: String?,
    scpPathHistory: List<String>,
    scpPathHistoryIndex: Int,
    onScpPathHistoryIndexChange: (Int) -> Unit,
    scpSelectedPath: String?,
    onScpSelectedPathChange: (String?) -> Unit,
    scpRemotePath: String,
    onScpRemotePathChange: (String) -> Unit,
    pendingScpDownloadRemotePath: String?,
    onPendingScpDownloadRemotePathChange: (String?) -> Unit,
    scpTransferStatus: String?,
    onScpTransferStatusChange: (String?) -> Unit,
    showScpUploadVertical: Boolean,
    scpActionsExpanded: Boolean,
    onScpActionsExpandedChange: (Boolean) -> Unit,
    onScpPendingManualPathTargetChange: (String?) -> Unit,
    onScpPendingManualPathFallbackChange: (String?) -> Unit,
    onScpPendingLinkPathTargetChange: (String?) -> Unit,
    onScpPendingLinkPathFallbackChange: (String?) -> Unit,
    scpHomePath: String?,
    showScpRenameDialog: Boolean,
    onShowScpRenameDialogChange: (Boolean) -> Unit,
    scpRenameValue: String,
    onScpRenameValueChange: (String) -> Unit,
    showScpMoveDialog: Boolean,
    onShowScpMoveDialogChange: (Boolean) -> Unit,
    scpMoveDestination: String,
    onScpMoveDestinationChange: (String) -> Unit,
    showScpNewFolderDialog: Boolean,
    onShowScpNewFolderDialogChange: (Boolean) -> Unit,
    scpNewFolderValue: String,
    onScpNewFolderValueChange: (String) -> Unit,
    showScpDeleteDialog: Boolean,
    onShowScpDeleteDialogChange: (Boolean) -> Unit,
    activeFileTransfer: FileTransferProgress?,
    scpTransferActive: Boolean,
    context: Context,
    hideKeyboard: () -> Unit,
    onCopyRemotePath: (String) -> Unit,
    browseScpPath: (String, Boolean, Boolean, Boolean) -> Unit,
    onScpDownload: (String, String?) -> Unit,
    onScpUpload: (String, String) -> Unit,
    onCancelFileTransfer: () -> Unit,
    onManageRemotePath: (String, String, String?) -> Unit,
    inferRemoteDestination: (String, String) -> String,
    resolveChildPath: (String, String) -> String,
    parentPath: (String) -> String
) {
    var showHiddenFiles by rememberSaveable(sessionId) { mutableStateOf(false) }
    val remoteItems = (remoteDirectory?.entries ?: scpVisibleEntries).let { entries ->
        if (showHiddenFiles) entries else entries.filterNot { it.name.startsWith(".") }
    }
    val effectiveRemotePath = remoteDirectory?.path ?: scpLastListedPath
    val scpListingInProgress = scpPendingListPath != null
    val canGoBack = scpPathHistoryIndex > 0 && !scpListingInProgress
    val canGoForward = scpPathHistoryIndex < scpPathHistory.lastIndex && !scpListingInProgress
    val colorScheme = MaterialTheme.colorScheme
    val entryPathFor: (RemoteDirectoryEntry) -> String = { entry ->
        entry.absolutePath.ifBlank { resolveChildPath(effectiveRemotePath, entry.name) }
    }
    val selectedEntry = remoteItems.firstOrNull { entryPathFor(it) == scpSelectedPath }
    val selectedPath = selectedEntry?.let(entryPathFor)
    var pendingUploadSourceUri by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var pendingUploadDisplayName by rememberSaveable(sessionId) { mutableStateOf("") }
    var pendingUploadDestination by rememberSaveable(sessionId) { mutableStateOf("") }
    val pendingUploadCollision = remoteItems.firstOrNull { entry ->
        entryPathFor(entry).trimEnd('/') == pendingUploadDestination.trim().trimEnd('/')
    }
    val uploadDestinationInCurrentFolder =
        pendingUploadDestination.isNotBlank() &&
            parentPath(pendingUploadDestination.trim()).trimEnd('/').ifBlank { "/" } ==
            effectiveRemotePath.trimEnd('/').ifBlank { "/" }
    val canDownloadSelected = selectedEntry != null &&
        !selectedEntry.isDirectory &&
        !scpTransferActive &&
        !scpListingInProgress
    val canMutateSelection = selectedEntry != null && !scpTransferActive && !scpListingInProgress
    val deleteProtectedSelection = selectedEntry != null &&
        isProtectedRemoteSystemDirectory(
            path = selectedPath,
            isDirectory = selectedEntry.isDirectory
        )
    val canMoveSelection = canMutateSelection && !deleteProtectedSelection
    val canDeleteSelection = canMutateSelection && !deleteProtectedSelection
    val canChooseUploadSource = !scpTransferActive &&
        !scpListingInProgress
    val pendingScpDownloadRemotePathForResult =
        rememberSaveable(sessionId) { mutableStateOf(pendingScpDownloadRemotePath) }
    val scpDownloadDocumentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val selectedRemote =
                pendingScpDownloadRemotePathForResult.value ?: pendingScpDownloadRemotePath
            pendingScpDownloadRemotePathForResult.value = null
            onPendingScpDownloadRemotePathChange(null)
            if (selectedRemote.isNullOrBlank()) return@rememberLauncherForActivityResult
            if (uri == null) {
                onScpTransferStatusChange("Download cancelled.")
                return@rememberLauncherForActivityResult
            }
            onScpTransferStatusChange(null)
            onScpDownload(selectedRemote, uri.toString())
        }
    val scpUploadDocumentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                onScpTransferStatusChange("Local file selection cancelled.")
                return@rememberLauncherForActivityResult
            }
            val displayName = queryUriDisplayName(context, uri) ?: "upload.bin"
            pendingUploadSourceUri = uri.toString()
            pendingUploadDisplayName = displayName
            pendingUploadDestination = inferRemoteDestination(displayName, effectiveRemotePath)
            onScpTransferStatusChange(
                if (displayName == "upload.bin") {
                    "Review the remote filename before uploading."
                } else {
                    null
                }
            )
        }

    fun submitScpPathJump() {
        if (scpListingInProgress) return
        val targetPath = scpRemotePath.trim().ifBlank { "." }
        hideKeyboard()
        if (targetPath == effectiveRemotePath) {
            onScpRemotePathChange(effectiveRemotePath)
            return
        }
        onScpPendingManualPathTargetChange(targetPath)
        onScpPendingManualPathFallbackChange(effectiveRemotePath)
        browseScpPath(targetPath, true, true, true)
    }

    fun launchScpDownloadPicker() {
        val selectedRemotePath = scpSelectedPath ?: return
        pendingScpDownloadRemotePathForResult.value = selectedRemotePath
        onPendingScpDownloadRemotePathChange(selectedRemotePath)
        scpDownloadDocumentPicker.launch(
            selectedRemotePath.substringAfterLast('/').ifBlank { "download.bin" }
        )
    }

    fun handleScpUploadAction() {
        if (!canChooseUploadSource) return
        scpUploadDocumentPicker.launch(arrayOf("*/*"))
    }

    LaunchedEffect(remoteItems, effectiveRemotePath, scpSelectedPath) {
        val selectedRemotePath = scpSelectedPath ?: return@LaunchedEffect
        if (remoteItems.none { entryPathFor(it) == selectedRemotePath }) {
            onScpSelectedPathChange(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.CONNECTING_SCP_PANEL)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val showRefreshNavigation = maxWidth >= 380.dp
            val showHomeNavigation = maxWidth >= 430.dp
            val showForwardNavigation = maxWidth >= 480.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = canGoBack,
                    onClick = {
                        if (!canGoBack) return@IconButton
                        val nextIndex = scpPathHistoryIndex - 1
                        onScpPathHistoryIndexChange(nextIndex)
                        browseScpPath(scpPathHistory[nextIndex], false, true, true)
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canGoBack) colorScheme.onSurface else colorScheme.onSurfaceVariant)
                }
                if (showForwardNavigation) {
                    IconButton(
                        enabled = canGoForward,
                        onClick = {
                            if (!canGoForward) return@IconButton
                            val nextIndex = scpPathHistoryIndex + 1
                            onScpPathHistoryIndexChange(nextIndex)
                            browseScpPath(scpPathHistory[nextIndex], false, true, true)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canGoForward) colorScheme.onSurface else colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(enabled = !scpListingInProgress, onClick = { browseScpPath(parentPath(effectiveRemotePath), true, true, true) }) {
                    Icon(Icons.Default.ArrowUpward, "Up", tint = if (scpListingInProgress) colorScheme.onSurfaceVariant else colorScheme.onSurface)
                }
                if (showRefreshNavigation) {
                    IconButton(enabled = !scpListingInProgress, onClick = { browseScpPath(effectiveRemotePath, false, true, true) }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = if (scpListingInProgress) colorScheme.onSurfaceVariant else colorScheme.onSurface)
                    }
                }
                if (showHomeNavigation) {
                    IconButton(
                        enabled = !scpListingInProgress,
                        onClick = { browseScpPath(scpHomePath ?: ".", true, true, true) },
                        modifier = Modifier.testTag(UiTestTags.connectingScpAction("home"))
                    ) {
                        Icon(Icons.Default.Home, "Home", tint = if (scpListingInProgress) colorScheme.onSurfaceVariant else colorScheme.onSurface)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { showHiddenFiles = !showHiddenFiles },
                    modifier = Modifier.testTag(UiTestTags.connectingScpAction("toggle_hidden"))
                ) {
                    Icon(
                        if (showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showHiddenFiles) "Hide hidden files" else "Show hidden files",
                        tint = if (showHiddenFiles) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = ::handleScpUploadAction,
                    enabled = canChooseUploadSource,
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON)
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        "Upload file to this folder",
                        tint = if (canChooseUploadSource) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = ::launchScpDownloadPicker,
                    enabled = canDownloadSelected,
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON)
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        "Download selected file",
                        tint = if (canDownloadSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(
                        onClick = { onScpActionsExpandedChange(true) },
                        enabled = !scpTransferActive || (!showForwardNavigation && canGoForward),
                        modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_ACTIONS_BUTTON)
                    ) {
                        Icon(Icons.Default.MoreVert, "Actions")
                    }
                    DropdownMenu(
                        expanded = scpActionsExpanded,
                        onDismissRequest = { onScpActionsExpandedChange(false) }
                    ) {
                        if (!showForwardNavigation) {
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                enabled = canGoForward,
                                onClick = {
                                    if (!canGoForward) return@DropdownMenuItem
                                    val nextIndex = scpPathHistoryIndex + 1
                                    onScpActionsExpandedChange(false)
                                    onScpPathHistoryIndexChange(nextIndex)
                                    browseScpPath(scpPathHistory[nextIndex], false, true, true)
                                },
                                modifier = Modifier.testTag(UiTestTags.connectingScpAction("forward"))
                            )
                        }
                        if (!showHomeNavigation) {
                            DropdownMenuItem(
                                text = { Text("Home") },
                                enabled = !scpListingInProgress,
                                onClick = {
                                    if (scpListingInProgress) return@DropdownMenuItem
                                    onScpActionsExpandedChange(false)
                                    browseScpPath(scpHomePath ?: ".", true, true, true)
                                },
                                modifier = Modifier.testTag(UiTestTags.connectingScpAction("home"))
                            )
                        }
                        if (!showRefreshNavigation) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                enabled = !scpListingInProgress,
                                onClick = {
                                    if (scpListingInProgress) return@DropdownMenuItem
                                    onScpActionsExpandedChange(false)
                                    browseScpPath(effectiveRemotePath, false, true, true)
                                },
                                modifier = Modifier.testTag(UiTestTags.connectingScpAction("refresh"))
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            enabled = canMoveSelection,
                            onClick = {
                                val selectedRemotePath = scpSelectedPath ?: return@DropdownMenuItem
                                onScpActionsExpandedChange(false)
                                onScpRenameValueChange(selectedRemotePath.substringAfterLast('/').ifBlank { selectedEntry?.name.orEmpty() })
                                onShowScpRenameDialogChange(true)
                            },
                            modifier = Modifier.testTag(UiTestTags.connectingScpAction("rename"))
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            enabled = canMoveSelection,
                            onClick = {
                                val selectedRemotePath = scpSelectedPath ?: return@DropdownMenuItem
                                onScpActionsExpandedChange(false)
                                onScpMoveDestinationChange(selectedRemotePath)
                                onShowScpMoveDialogChange(true)
                            },
                            modifier = Modifier.testTag(UiTestTags.connectingScpAction("move"))
                        )
                        DropdownMenuItem(
                            text = { Text("Copy path") },
                            enabled = canMutateSelection,
                            onClick = {
                                val selectedRemotePath = scpSelectedPath ?: return@DropdownMenuItem
                                onScpActionsExpandedChange(false)
                                onCopyRemotePath(selectedRemotePath)
                                onScpTransferStatusChange("Remote path copied.")
                            },
                            modifier = Modifier.testTag(UiTestTags.connectingScpAction("copy_path"))
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = canDeleteSelection,
                            onClick = {
                                onScpActionsExpandedChange(false)
                                if (canDeleteSelection) onShowScpDeleteDialogChange(true)
                            },
                            modifier = Modifier.testTag(UiTestTags.connectingScpAction("delete"))
                        )
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            enabled = !scpTransferActive && !scpListingInProgress,
                            onClick = {
                                onScpActionsExpandedChange(false)
                                onScpNewFolderValueChange("")
                                onShowScpNewFolderDialogChange(true)
                            },
                            modifier = Modifier.testTag(UiTestTags.connectingScpAction("new_folder"))
                        )
                    }
                }
            }
        }
        TextField(
            value = scpRemotePath,
            onValueChange = onScpRemotePathChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.CONNECTING_SCP_REMOTE_DIR_INPUT),
            singleLine = true,
            enabled = !scpListingInProgress,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("Remote path", style = MaterialTheme.typography.bodyMedium) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrect = false
            ),
            keyboardActions = KeyboardActions(onGo = { submitScpPathJump() }, onDone = { submitScpPathJump() }),
            trailingIcon = {
                IconButton(enabled = !scpListingInProgress, onClick = { submitScpPathJump() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Go to path")
                }
            }
        )
        Text(
            text = selectedEntry?.let {
                "Selected: ${entryPathFor(it)}. Use Download or the actions menu."
            } ?: if (showScpUploadVertical) {
                "Upload into this folder, or select a remote file to download."
            } else {
                "Tap a folder to open it. Select a file to download, or upload into this folder."
            },
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (scpListingInProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceVariant
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (remoteItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (scpListingInProgress) "Loading folder…" else "This folder is empty.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(remoteItems, key = { entryPathFor(it) }) { item ->
                        val absolute = entryPathFor(item)
                        val selected = scpSelectedPath == absolute
                        val opensDirectory =
                            !item.isBrokenLink && (item.isDirectory || item.linkTargetIsDirectory == true)
                        val metadataText = buildList {
                            add(
                                when {
                                    item.isBrokenLink -> "Broken link"
                                    item.isDirectory -> "Folder"
                                    else -> com.majordaftapps.sshpeaches.app.service.formatByteCount(item.sizeBytes)
                                }
                            )
                            item.modifiedAtEpochMillis?.takeIf { it > 0L }?.let {
                                add(formatRemoteModifiedTime(it))
                            }
                            when {
                                item.isSymbolicLink && !item.linkTargetPath.isNullOrBlank() ->
                                    add("→ ${item.linkTargetPath}")
                                item.permissionSummary.isNotBlank() -> add(item.permissionSummary)
                            }
                        }.joinToString(" · ")
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !scpListingInProgress) {
                                    when {
                                        item.isBrokenLink -> {
                                            onScpSelectedPathChange(absolute)
                                            onScpTransferStatusChange("Broken link: ${item.name}")
                                            Toast.makeText(context, "Broken link: ${item.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        opensDirectory -> {
                                            onScpSelectedPathChange(null)
                                            onScpPendingLinkPathTargetChange(if (item.isSymbolicLink) absolute else null)
                                            onScpPendingLinkPathFallbackChange(if (item.isSymbolicLink) effectiveRemotePath else null)
                                            browseScpPath(absolute, true, true, true)
                                        }
                                        else -> {
                                            onScpSelectedPathChange(absolute)
                                            onScpTransferStatusChange(null)
                                        }
                                    }
                                }
                                .then(
                                    if (opensDirectory) {
                                        Modifier.semantics {
                                            stateDescription = "Folder. Double tap to open."
                                        }
                                    } else {
                                        Modifier.semantics {
                                            this.selected = selected
                                            stateDescription =
                                                if (selected) "Selected" else "Not selected"
                                        }
                                    }
                                )
                                .testTag(UiTestTags.connectingScpRemoteRow(absolute)),
                            color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when {
                                        item.isSymbolicLink -> Icons.Default.Link
                                        item.isDirectory -> Icons.Default.Folder
                                        else -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        item.isSymbolicLink && item.isBrokenLink -> colorScheme.error
                                        item.isSymbolicLink -> colorScheme.primary
                                        item.isDirectory -> colorScheme.primary
                                        else -> colorScheme.onSurfaceVariant
                                    }
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = when {
                                            item.isSymbolicLink && item.isBrokenLink -> colorScheme.error
                                            item.isSymbolicLink -> colorScheme.primary
                                            else -> colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = metadataText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = if (item.isBrokenLink) colorScheme.error else colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = activeFileTransfer != null, enter = fadeIn(), exit = fadeOut()) {
            activeFileTransfer?.let { transfer ->
                FileTransferStatusStrip(
                    transfer = transfer,
                    onCancel = onCancelFileTransfer,
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_TRANSFER_STRIP)
                )
            }
        }
        AnimatedVisibility(
            visible = scpTransferStatus != null && activeFileTransfer?.isActive != true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            scpTransferStatus?.let { status ->
                ScpStatusStrip(status = status, modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_STATUS_STRIP))
            }
        }
    }

    if (!pendingUploadSourceUri.isNullOrBlank()) {
        val collisionIsDirectory =
            pendingUploadCollision?.isDirectory == true ||
                pendingUploadCollision?.linkTargetIsDirectory == true
        AlertDialog(
            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_UPLOAD_DIALOG),
            onDismissRequest = {
                pendingUploadSourceUri = null
                pendingUploadDisplayName = ""
                pendingUploadDestination = ""
                onScpTransferStatusChange("Upload cancelled.")
            },
            title = { Text("Upload file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Local file: ${pendingUploadDisplayName.ifBlank { "Unknown filename" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextField(
                        value = pendingUploadDestination,
                        onValueChange = { pendingUploadDestination = it },
                        singleLine = true,
                        label = { Text("Remote destination") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.CONNECTING_SCP_UPLOAD_DESTINATION_INPUT)
                    )
                    when {
                        !uploadDestinationInCurrentFolder -> Text(
                            "Keep the destination in the open folder ($effectiveRemotePath).",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        collisionIsDirectory -> Text(
                            "A folder already exists at this destination. Choose a different filename.",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        pendingUploadCollision != null -> Text(
                            "A file already exists at this destination. Continuing will replace it.",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        else -> Text(
                            "Review the destination before starting the upload.",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = uploadDestinationInCurrentFolder && !collisionIsDirectory,
                    onClick = {
                        val sourceUri = pendingUploadSourceUri ?: return@TextButton
                        val destination = pendingUploadDestination.trim()
                        if (!uploadDestinationInCurrentFolder || collisionIsDirectory) return@TextButton
                        pendingUploadSourceUri = null
                        pendingUploadDisplayName = ""
                        pendingUploadDestination = ""
                        onScpTransferStatusChange("Starting upload to $destination…")
                        onScpUpload(sourceUri, destination)
                    },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_UPLOAD_CONFIRM_BUTTON)
                ) {
                    Text(if (pendingUploadCollision != null) "Replace" else "Upload")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUploadSourceUri = null
                        pendingUploadDisplayName = ""
                        pendingUploadDestination = ""
                        onScpTransferStatusChange("Upload cancelled.")
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showScpRenameDialog && selectedEntry != null) {
        val validRename = isValidRemoteChildName(scpRenameValue)
        AlertDialog(
            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_RENAME_DIALOG),
            onDismissRequest = { onShowScpRenameDialogChange(false) },
            title = { Text("Rename") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = scpRenameValue,
                        onValueChange = onScpRenameValueChange,
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_RENAME_INPUT)
                    )
                    if (!validRename && scpRenameValue.isNotBlank()) {
                        Text(
                            "Enter a single filename without /, \\, . or ..",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validRename && !deleteProtectedSelection,
                    onClick = {
                        if (!validRename || deleteProtectedSelection) return@TextButton
                        val sourcePath = scpSelectedPath ?: return@TextButton
                        onShowScpRenameDialogChange(false)
                        onScpSelectedPathChange(null)
                        onManageRemotePath("move", sourcePath, resolveChildPath(parentPath(sourcePath), scpRenameValue.trim()))
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { onShowScpRenameDialogChange(false) }) { Text("Cancel") } }
        )
    }
    if (showScpMoveDialog && selectedEntry != null) {
        AlertDialog(
            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_MOVE_DIALOG),
            onDismissRequest = { onShowScpMoveDialogChange(false) },
            title = { Text("Move") },
            text = {
                TextField(
                    value = scpMoveDestination,
                    onValueChange = onScpMoveDestinationChange,
                    singleLine = true,
                    label = { Text("Destination path") },
                    modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_MOVE_INPUT)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = scpMoveDestination.trim().isNotBlank(),
                    onClick = {
                        val sourcePath = scpSelectedPath ?: return@TextButton
                        onShowScpMoveDialogChange(false)
                        onScpSelectedPathChange(null)
                        onManageRemotePath("move", sourcePath, scpMoveDestination.trim())
                    }
                ) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { onShowScpMoveDialogChange(false) }) { Text("Cancel") } }
        )
    }
    if (showScpNewFolderDialog) {
        val validFolderName = isValidRemoteChildName(scpNewFolderValue)
        AlertDialog(
            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_DIALOG),
            onDismissRequest = { onShowScpNewFolderDialogChange(false) },
            title = { Text("New folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = scpNewFolderValue,
                        onValueChange = onScpNewFolderValueChange,
                        singleLine = true,
                        label = { Text("Folder name") },
                        modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_NEW_FOLDER_INPUT)
                    )
                    if (!validFolderName && scpNewFolderValue.isNotBlank()) {
                        Text(
                            "Enter a single folder name without /, \\, . or ..",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validFolderName,
                    onClick = {
                        if (!validFolderName) return@TextButton
                        onShowScpNewFolderDialogChange(false)
                        onManageRemotePath("mkdir", resolveChildPath(effectiveRemotePath, scpNewFolderValue.trim()), null)
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { onShowScpNewFolderDialogChange(false) }) { Text("Cancel") } }
        )
    }
    if (showScpDeleteDialog && selectedEntry != null) {
        AlertDialog(
            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_DELETE_DIALOG),
            onDismissRequest = { onShowScpDeleteDialogChange(false) },
            title = { Text("Delete") },
            text = {
                Text(
                    if (deleteProtectedSelection) {
                        "${selectedEntry.name} is a protected system folder and cannot be deleted."
                    } else if (selectedEntry.isSymbolicLink) {
                        "Permanently delete the link ${selectedPath.orEmpty()}? The link target will not be deleted."
                    } else if (selectedEntry.isDirectory) {
                        "Permanently delete the folder ${selectedPath.orEmpty()} and everything inside it? This cannot be undone."
                    } else {
                        "Permanently delete the file ${selectedPath.orEmpty()}? This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleteProtectedSelection,
                    onClick = {
                        if (deleteProtectedSelection) return@TextButton
                        val sourcePath = scpSelectedPath ?: return@TextButton
                        onShowScpDeleteDialogChange(false)
                        onScpSelectedPathChange(null)
                        onManageRemotePath("delete", sourcePath, null)
                    }
                ) { Text("Delete permanently", color = colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { onShowScpDeleteDialogChange(false) }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ConnectingSftpContent(
    sessionId: String,
    remoteDirectory: RemoteDirectorySnapshot?,
    logs: List<SessionLogBus.Entry>,
    context: Context,
    activeFileTransfer: FileTransferProgress?,
    sftpTransferActive: Boolean,
    sftpTransferStatus: String?,
    sftpPath: String,
    onSftpPathChange: (String) -> Unit,
    sftpLocalPath: String,
    onSftpLocalPathChange: (String) -> Unit,
    sftpConsoleLines: List<String>,
    sftpConsoleRevision: Long,
    appendSftpConsole: (String) -> Unit,
    clearSftpConsole: () -> Unit,
    sftpCommandInput: String,
    onSftpCommandInputChange: (String) -> Unit,
    onSftpPendingDirectoryEchoChange: (String?) -> Unit,
    pendingSftpDownloadRemotePath: String?,
    onPendingSftpDownloadRemotePathChange: (String?) -> Unit,
    pendingSftpUploadBasePath: String?,
    onPendingSftpUploadBasePathChange: (String?) -> Unit,
    sftpCommandRunning: Boolean,
    onSftpCommandRunningChange: (Boolean) -> Unit,
    sftpAwaitDirectoryRefresh: Boolean,
    onSftpAwaitDirectoryRefreshChange: (Boolean) -> Unit,
    sftpCommandStartLogCount: Int,
    onSftpCommandStartLogCountChange: (Int) -> Unit,
    sftpCommandStartDirectoryKey: String,
    onSftpCommandStartDirectoryKeyChange: (String) -> Unit,
    sftpListShowAll: Boolean,
    onSftpListShowAllChange: (Boolean) -> Unit,
    onSftpListDirectory: (String) -> Unit,
    onSftpDownload: (String, String?) -> Unit,
    onSftpUpload: (String, String) -> Unit,
    onCancelFileTransfer: () -> Unit,
    onManageRemotePath: (String, String, String?) -> Unit,
    inferRemoteDestination: (String, String) -> String,
    resolveRemotePath: (String, String) -> String,
    listLocalFiles: (String) -> List<File>
) {
    val colorScheme = MaterialTheme.colorScheme
    val effectiveSftpPath = remoteDirectory?.path ?: sftpPath
    val currentRemoteSnapshotKey = remember(
        remoteDirectory?.path,
        remoteDirectory?.refreshToken
    ) {
        sftpDirectoryRefreshKey(remoteDirectory)
    }

    fun beginSftpCommandWait(waitForDirectoryRefresh: Boolean) {
        onSftpCommandRunningChange(true)
        onSftpAwaitDirectoryRefreshChange(waitForDirectoryRefresh)
        onSftpCommandStartLogCountChange(logs.size)
        onSftpCommandStartDirectoryKeyChange(currentRemoteSnapshotKey)
    }

    val pendingSftpDownloadRemotePathForResult =
        rememberSaveable(sessionId) { mutableStateOf(pendingSftpDownloadRemotePath) }
    val pendingSftpUploadBasePathForResult =
        rememberSaveable(sessionId) { mutableStateOf(pendingSftpUploadBasePath) }
    val sftpDownloadDocumentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val remote =
                pendingSftpDownloadRemotePathForResult.value ?: pendingSftpDownloadRemotePath
            pendingSftpDownloadRemotePathForResult.value = null
            onPendingSftpDownloadRemotePathChange(null)
            if (remote.isNullOrBlank()) return@rememberLauncherForActivityResult
            if (uri == null) {
                appendSftpConsole("Save location selection cancelled.")
                return@rememberLauncherForActivityResult
            }
            beginSftpCommandWait(false)
            onSftpDownload(remote, uri.toString())
            appendSftpConsole("Downloading $remote -> $uri")
        }
    val sftpUploadDocumentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val remoteBase =
                pendingSftpUploadBasePathForResult.value ?: pendingSftpUploadBasePath
            pendingSftpUploadBasePathForResult.value = null
            onPendingSftpUploadBasePathChange(null)
            if (remoteBase.isNullOrBlank()) return@rememberLauncherForActivityResult
            if (uri == null) {
                appendSftpConsole("Local file selection cancelled.")
                return@rememberLauncherForActivityResult
            }
            val localName = queryUriDisplayName(context, uri) ?: "upload.bin"
            val remote = inferRemoteDestination(localName, remoteBase)
            beginSftpCommandWait(false)
            onSftpUpload(uri.toString(), remote)
            appendSftpConsole("Uploading $localName -> $remote")
        }
    val consoleScrollState = rememberScrollState()
    val sftpBusy = sftpCommandRunning || sftpTransferActive

    fun resolveLocalPath(raw: String): String {
        val candidate = File(raw)
        return if (candidate.isAbsolute) candidate.absolutePath else File(sftpLocalPath, raw).absolutePath
    }

    fun finishSftpCommandWait() {
        onSftpCommandRunningChange(false)
        onSftpAwaitDirectoryRefreshChange(false)
        onSftpCommandStartLogCountChange(0)
        onSftpCommandStartDirectoryKeyChange("")
    }

    fun runSftpCommand(input: String) {
        val command = input.trim()
        if (command.isEmpty()) return
        if (sftpTransferActive) {
            appendSftpConsole("A file transfer is already running for this session.")
            return
        }
        if (sftpCommandRunning) {
            appendSftpConsole("A command is already running. Wait for its server response.")
            return
        }
        appendSftpConsole("sftp> $command")
        val tokens = tokenizeSftpCommand(command)
        if (tokens.isEmpty()) return
        val cmd = tokens.first().lowercase()
        val args = tokens.drop(1)
        when (cmd) {
            "help", "?" -> {
                appendSftpConsole("Commands: ls [path], cd <path>, pwd, get <remote> [local], put [local] [remote]")
                appendSftpConsole("          mkdir <path>, rm <file>, rm -r <folder>, mv <src> <dst>, lcd <path>, lpwd, lls [path], refresh, clear")
                appendSftpConsole("          get without a local path opens a save picker; put without a local path opens a file picker")
            }
            "clear" -> clearSftpConsole()
            "pwd" -> appendSftpConsole(effectiveSftpPath)
            "lpwd" -> appendSftpConsole(sftpLocalPath)
            "refresh" -> {
                beginSftpCommandWait(true)
                onSftpPendingDirectoryEchoChange(effectiveSftpPath)
                onSftpListDirectory(effectiveSftpPath)
            }
            "ls" -> {
                val showAllFlag = args.firstOrNull()?.let { it == "-a" || it == "-la" || it == "-al" }
                val targetArg = if (showAllFlag == true) args.getOrNull(1) else args.firstOrNull()
                onSftpListShowAllChange(showAllFlag == true)
                val target = resolveRemotePath(effectiveSftpPath, targetArg.orEmpty())
                beginSftpCommandWait(true)
                onSftpPathChange(target)
                onSftpPendingDirectoryEchoChange(target)
                onSftpListDirectory(target)
            }
            "cd" -> {
                val target = resolveRemotePath(effectiveSftpPath, args.firstOrNull() ?: ".")
                beginSftpCommandWait(true)
                onSftpPathChange(target)
                onSftpPendingDirectoryEchoChange(target)
                onSftpListDirectory(target)
            }
            "lcd" -> {
                val targetArg = args.firstOrNull()
                if (targetArg.isNullOrBlank()) {
                    appendSftpConsole("usage: lcd <local-path>")
                } else {
                    val target = File(resolveLocalPath(targetArg))
                    if (target.exists() && target.isDirectory) {
                        onSftpLocalPathChange(target.absolutePath)
                        appendSftpConsole("Local directory: ${target.absolutePath}")
                    } else {
                        appendSftpConsole("lcd failed: no such directory: ${target.absolutePath}")
                    }
                }
            }
            "lls" -> {
                val target = File(resolveLocalPath(args.firstOrNull().orEmpty().ifBlank { "." }))
                if (!target.exists() || !target.isDirectory) {
                    appendSftpConsole("lls failed: no such directory: ${target.absolutePath}")
                } else {
                    appendSftpConsole("Local directory: ${target.absolutePath}")
                    val entries = listLocalFiles(target.absolutePath)
                    if (entries.isEmpty()) {
                        appendSftpConsole("(empty)")
                    } else {
                        entries.forEach { item ->
                            appendSftpConsole("${if (item.isDirectory) "d" else "-"} ${item.length()} ${item.name}")
                        }
                    }
                }
            }
            "get" -> {
                val remoteArg = args.firstOrNull()
                if (remoteArg.isNullOrBlank()) {
                    appendSftpConsole("usage: get <remote-path> [local-path]")
                } else {
                    val remote = resolveRemotePath(effectiveSftpPath, remoteArg)
                    val explicitLocal = args.getOrNull(1)?.takeIf { it.isNotBlank() }
                    if (explicitLocal == null) {
                        pendingSftpDownloadRemotePathForResult.value = remote
                        onPendingSftpDownloadRemotePathChange(remote)
                        sftpDownloadDocumentPicker.launch(remote.substringAfterLast('/').ifBlank { "download.bin" })
                    } else {
                        val local = resolveLocalPath(explicitLocal)
                        beginSftpCommandWait(false)
                        onSftpDownload(remote, local)
                        appendSftpConsole("Downloading $remote -> $local")
                    }
                }
            }
            "put" -> {
                val localArg = args.firstOrNull()
                if (localArg.isNullOrBlank()) {
                    pendingSftpUploadBasePathForResult.value = effectiveSftpPath
                    onPendingSftpUploadBasePathChange(effectiveSftpPath)
                    sftpUploadDocumentPicker.launch(arrayOf("*/*"))
                } else {
                    val local = resolveLocalPath(localArg)
                    val remote = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        resolveRemotePath(effectiveSftpPath, it)
                    } ?: inferRemoteDestination(local, effectiveSftpPath)
                    beginSftpCommandWait(false)
                    onSftpUpload(local, remote)
                    appendSftpConsole("Uploading $local -> $remote")
                }
            }
            "mkdir" -> {
                val targetArg = args.firstOrNull()
                if (targetArg.isNullOrBlank()) {
                    appendSftpConsole("usage: mkdir <remote-path>")
                } else {
                    val target = resolveRemotePath(effectiveSftpPath, targetArg)
                    beginSftpCommandWait(false)
                    onManageRemotePath("mkdir", target, null)
                    appendSftpConsole("Creating directory: $target")
                }
            }
            "rm", "delete" -> {
                val recursive = args.firstOrNull() == "-r" || args.firstOrNull() == "-R"
                val targetArg = if (recursive) args.getOrNull(1) else args.firstOrNull()
                if (targetArg.isNullOrBlank()) {
                    appendSftpConsole("usage: rm <file> or rm -r <folder>")
                } else {
                    val target = resolveRemotePath(effectiveSftpPath, targetArg)
                    beginSftpCommandWait(false)
                    onManageRemotePath(if (recursive) "delete" else "delete_file", target, null)
                    appendSftpConsole(
                        if (recursive) {
                            "Recursively deleting $target and everything inside it…"
                        } else {
                            "Deleting file or link: $target"
                        }
                    )
                }
            }
            "mv", "rename" -> {
                val srcArg = args.getOrNull(0)
                val dstArg = args.getOrNull(1)
                if (srcArg.isNullOrBlank() || dstArg.isNullOrBlank()) {
                    appendSftpConsole("usage: mv <remote-src> <remote-dst>")
                } else {
                    val src = resolveRemotePath(effectiveSftpPath, srcArg)
                    val dst = resolveRemotePath(effectiveSftpPath, dstArg)
                    beginSftpCommandWait(false)
                    onManageRemotePath("move", src, dst)
                    appendSftpConsole("Moving: $src -> $dst")
                }
            }
            "exit", "quit", "bye" -> appendSftpConsole("Use the top-right close action to disconnect this session.")
            else -> appendSftpConsole("Unknown command: $cmd. Type 'help' for commands.")
        }
    }

    LaunchedEffect(sftpCommandRunning, logs.size, currentRemoteSnapshotKey) {
        if (!sftpCommandRunning) return@LaunchedEffect
        val commandOutcome = logs
            .drop(sftpCommandStartLogCount.coerceAtMost(logs.size))
            .map { it.message }
            .lastOrNull { message ->
                message.startsWith("Remote mkdir completed:") ||
                    message.startsWith("Remote move completed:") ||
                    message.startsWith("Remote delete completed:") ||
                    message.startsWith("Remote delete_file completed:") ||
                    message.startsWith("SFTP operation failed:") ||
                    message.startsWith("Directory listing failed for")
            }
        val completedByDirectory =
            sftpAwaitDirectoryRefresh &&
                currentRemoteSnapshotKey.isNotBlank() &&
                currentRemoteSnapshotKey != sftpCommandStartDirectoryKey
        if (
            commandOutcome != null &&
            !commandOutcome.startsWith("Directory listing failed for")
        ) {
            appendSftpConsole(commandOutcome)
        }
        if (commandOutcome != null || completedByDirectory) {
            finishSftpCommandWait()
        }
    }
    LaunchedEffect(
        activeFileTransfer?.operationId,
        activeFileTransfer?.status,
        sftpCommandRunning
    ) {
        val transfer = activeFileTransfer ?: return@LaunchedEffect
        if (sftpCommandRunning && transfer.isTerminal) {
            appendSftpConsole(transfer.statusMessage())
            finishSftpCommandWait()
        }
    }
    LaunchedEffect(sftpConsoleRevision) {
        if (sftpConsoleLines.isNotEmpty()) {
            withFrameNanos { }
            consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.ime
                    .exclude(WindowInsets.navigationBars)
                    .only(WindowInsetsSides.Bottom)
            )
            .testTag(UiTestTags.CONNECTING_SFTP_PANEL)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("SFTP Console", color = colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
        Text("Remote: $effectiveSftpPath", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        Text("Local: $sftpLocalPath", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        if (activeFileTransfer != null) {
            FileTransferProgressCard(
                transfer = activeFileTransfer,
                onCancel = onCancelFileTransfer
            )
        } else if (sftpTransferStatus != null) {
            ScpStatusStrip(status = sftpTransferStatus.orEmpty(), modifier = Modifier.testTag(UiTestTags.CONNECTING_SFTP_STATUS_STRIP))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(UiTestTags.CONNECTING_SFTP_CONSOLE),
            color = colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(consoleScrollState)
                    .padding(12.dp)
            ) {
                val activityLog = if (sftpConsoleLines.isEmpty()) "sftp> help" else sftpConsoleLines.joinToString("\n")
                Text(activityLog, color = colorScheme.onSurface, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
        OutlinedTextField(
            value = sftpCommandInput,
            onValueChange = onSftpCommandInputChange,
            label = { Text("Command (e.g. ls, cd /, get file.txt)") },
            singleLine = true,
            enabled = !sftpBusy,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
                autoCorrect = false
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (sftpBusy) return@KeyboardActions
                    val cmd = sftpCommandInput
                    onSftpCommandInputChange("")
                    runSftpCommand(cmd)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (sftpBusy) return@Button
                    val cmd = sftpCommandInput
                    onSftpCommandInputChange("")
                    runSftpCommand(cmd)
                },
                enabled = !sftpBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON)
            ) { Text(if (sftpCommandRunning) "Working…" else "Run") }
            Button(
                onClick = { runSftpCommand("help") },
                enabled = !sftpBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.CONNECTING_SFTP_HELP_BUTTON)
            ) { Text("Help") }
            Button(
                onClick = { runSftpCommand("refresh") },
                enabled = !sftpBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.CONNECTING_SFTP_REFRESH_BUTTON)
            ) { Text("Refresh") }
        }
    }
}

@Composable
private fun ConnectingStatusContent(
    state: QuickConnectUiState,
    statusText: String,
    statusColor: Color,
    hostName: String,
    detailLine: String?,
    userFacingStateMessage: String,
    activeFileTransfer: FileTransferProgress?,
    renderedLogs: List<String>,
    listState: LazyListState
) {
    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
    ) {
        val isShortHeight = maxHeight <= 520.dp
        val heroSize = if (isShortHeight) 180.dp else 360.dp
        val outerGlowSize = if (isShortHeight) 168.dp else 340.dp
        val innerGlowSize = if (isShortHeight) 120.dp else 250.dp
        val logoSize = if (isShortHeight) 72.dp else 128.dp
        val logsMaxHeight = minOf(180.dp, maxHeight * 0.24f)
        val contentSpacing = if (isShortHeight) 8.dp else 12.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (isShortHeight) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                Box(modifier = Modifier.size(heroSize), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(outerGlowSize)
                            .blur(if (isShortHeight) 36.dp else 72.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF), Color(0x08F7F4EF), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(innerGlowSize)
                            .blur(if (isShortHeight) 16.dp else 32.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x18FFFFFF), Color(0x0CFBF7F1), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.sshpeaches_activitybar),
                        contentDescription = "SSHPeaches logo",
                        colorFilter = ColorFilter.tint(Color(0xFFFA992A)),
                        modifier = Modifier.size(logoSize),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = statusText,
                    style = (if (isShortHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium).copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.phase == QuickConnectPhase.CONNECTING || state.phase == QuickConnectPhase.IDLE) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                    }
                    Text(
                        text = hostName,
                        style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                    )
                }
                detailLine?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
                }
                userFacingStateMessage.takeIf { it.isNotBlank() }?.let { message ->
                    Text(text = message, style = MaterialTheme.typography.bodySmall.copy(color = colorScheme.onSurfaceVariant))
                }
                activeFileTransfer?.let { transfer ->
                    FileTransferProgressCard(transfer = transfer)
                }
            }
            if (renderedLogs.isNotEmpty()) {
                ConnectionLogsPane(
                    renderedLogs = renderedLogs,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = logsMaxHeight)
                        .wrapContentHeight(align = Alignment.Bottom)
                )
            }
        }
    }
}

@Composable
private fun SnippetPickerDialog(
    snippets: List<Snippet>,
    onRunSnippet: (Snippet) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run Snippet") },
        text = {
            if (snippets.isEmpty()) {
                Text("No snippets available. Create snippets in Snippets first.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CONNECTING_SNIPPET_PICKER)
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRunSnippet(snippet) },
                            color = colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = snippet.title, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                                if (snippet.description.isNotBlank()) {
                                    Text(
                                        text = snippet.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = snippet.command,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CompactKeyRow(
    keys: List<CompactTerminalKey>,
    activeModifiers: Set<KeyboardModifier>,
    activeAliasIcons: Set<String>,
    onSendKey: (CompactTerminalKey) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keys.isEmpty()) return
    val keyShape = RoundedCornerShape(5.dp)
    val rows = remember(keys) {
        keys
            .chunked(KeyboardLayoutDefaults.SLOT_COLUMNS)
            .take(KeyboardLayoutDefaults.SLOT_ROWS)
    }
    BoxWithConstraints(modifier = modifier) {
        val useWideLayout = maxWidth >= COMPACT_KEY_WIDE_LAYOUT_MIN_WIDTH
        val shouldSplitColumns = useWideLayout && rows.size > 2
        if (shouldSplitColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactKeyRows(
                    rows = rows.take(2),
                    rowOffset = 0,
                    keyShape = keyShape,
                    activeModifiers = activeModifiers,
                    activeAliasIcons = activeAliasIcons,
                    onSendKey = onSendKey,
                    modifier = Modifier.weight(1f)
                )
                CompactKeyRows(
                    rows = rows.drop(2),
                    rowOffset = 2,
                    keyShape = keyShape,
                    activeModifiers = activeModifiers,
                    activeAliasIcons = activeAliasIcons,
                    onSendKey = onSendKey,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            CompactKeyRows(
                rows = rows,
                rowOffset = 0,
                keyShape = keyShape,
                activeModifiers = activeModifiers,
                activeAliasIcons = activeAliasIcons,
                onSendKey = onSendKey,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompactKeyRows(
    rows: List<List<CompactTerminalKey>>,
    rowOffset: Int,
    keyShape: RoundedCornerShape,
    activeModifiers: Set<KeyboardModifier>,
    activeAliasIcons: Set<String>,
    onSendKey: (CompactTerminalKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(KeyboardLayoutDefaults.SLOT_COLUMNS) { index ->
                    val key = row.getOrNull(index)
                    if (key == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
                        )
                        return@repeat
                    }
                    val modifierActive = key.action.type == KeyboardActionType.MODIFIER &&
                        key.action.modifier != null &&
                        activeModifiers.contains(key.action.modifier)
                    val aliasActive = key.action.iconId in activeAliasIcons
                    val flatIndex = (rowOffset + rowIndex) * KeyboardLayoutDefaults.SLOT_COLUMNS + index
                    val testTag = if (key.action.iconId == "keyboard") {
                        UiTestTags.CONNECTING_KEYBOARD_TOGGLE
                    } else {
                        UiTestTags.connectingCompactKey(flatIndex)
                    }
                    CompactKeyButton(
                        key = key,
                        keyShape = keyShape,
                        modifierActive = modifierActive,
                        aliasActive = aliasActive,
                        testTag = testTag,
                        onSendKey = onSendKey
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RowScope.CompactKeyButton(
    key: CompactTerminalKey,
    keyShape: RoundedCornerShape,
    modifierActive: Boolean,
    aliasActive: Boolean,
    testTag: String,
    onSendKey: (CompactTerminalKey) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val peachAccentColor = colorResource(id = R.color.peachy_orange)
    val scope = rememberCoroutineScope()
    var repeatJob by remember(key) { mutableStateOf<Job?>(null) }
    var pressed by remember(key) { mutableStateOf(false) }
    val useLegacyDarkKeyPalette = colorScheme.background.luminance() < 0.5f
    val lightModePeachAccentColor = run {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(peachAccentColor.toArgb(), hsv)
        Color.hsv(hsv[0], hsv[1] * 0.82f, hsv[2])
    }
    val keyBorderColor = if (useLegacyDarkKeyPalette) {
        Color(0xFF474747)
    } else {
        colorScheme.outline.copy(alpha = 0.7f)
    }
    val keyBackgroundColor = when {
        pressed && key.enabled && useLegacyDarkKeyPalette -> Color(0xFF5B3A0F)
        (modifierActive || aliasActive) && useLegacyDarkKeyPalette -> Color(0xFF5B3A0F)
        key.enabled && useLegacyDarkKeyPalette -> Color(0xFF121212)
        useLegacyDarkKeyPalette -> Color(0xFF090909)
        pressed && key.enabled -> lightModePeachAccentColor
        modifierActive || aliasActive -> lightModePeachAccentColor
        key.enabled -> colorScheme.surfaceVariant
        else -> colorScheme.surface
    }
    val keyContentColor = if (useLegacyDarkKeyPalette) {
        if (key.enabled) Color(0xFFEDEDED) else Color(0xFF7B7B7B)
    } else {
        if (key.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant
    }
    DisposableEffect(Unit) {
        onDispose {
            repeatJob?.cancel()
            pressed = false
        }
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
            .testTag(testTag)
            .clip(keyShape)
            .border(width = 1.dp, color = keyBorderColor, shape = keyShape)
            .background(keyBackgroundColor)
            .pointerInteropFilter { event: MotionEvent ->
                if (!key.enabled) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pressed = true
                        onSendKey(key)
                        if (key.repeatable) {
                            repeatJob?.cancel()
                            repeatJob = scope.launch {
                                delay(KEY_REPEAT_INITIAL_DELAY_MS)
                                while (isActive) {
                                    onSendKey(key)
                                    delay(KEY_REPEAT_INTERVAL_MS)
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        repeatJob?.cancel()
                        repeatJob = null
                        true
                    }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val icon = KeyboardIconPack.byId(key.action.iconId)
        if (icon != null) {
            Icon(
                imageVector = icon.icon,
                contentDescription = icon.label,
                tint = keyContentColor,
                modifier = Modifier.size(15.dp)
            )
        } else {
            Text(
                text = key.label,
                color = keyContentColor,
                fontSize = KeyboardLayoutDefaults.COMPACT_KEY_FONT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileTransferProgressCard(
    transfer: FileTransferProgress,
    onCancel: (() -> Unit)? = null
) {
    FileTransferStatusStrip(
        transfer = transfer,
        onCancel = onCancel,
        modifier = Modifier
    )
}

@Composable
private fun FileTransferStatusStrip(
    transfer: FileTransferProgress,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val progressFraction = transfer.progressFraction
    var cancellationRequested by remember(transfer.operationId) { mutableStateOf(false) }
    val containerColor = when (transfer.status) {
        FileTransferStatus.SUCCEEDED -> Color(0xFF123C22)
        FileTransferStatus.FAILED -> colorScheme.error.copy(alpha = 0.14f)
        FileTransferStatus.CANCELLED -> colorScheme.surfaceVariant
        FileTransferStatus.ACTIVE -> colorScheme.surfaceVariant
    }
    val headlineColor = when (transfer.status) {
        FileTransferStatus.SUCCEEDED -> Color(0xFF78E08F)
        FileTransferStatus.FAILED -> colorScheme.error
        else -> colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = transfer.statusMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = headlineColor
            )
            Text(
                text = "${transfer.sourceLabel} -> ${transfer.destinationLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transfer.isActive) {
                if (progressFraction != null && transfer.hasStarted) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surface
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surface
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transfer.progressSummary(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (onCancel != null) {
                        TextButton(
                            enabled = !cancellationRequested,
                            onClick = {
                                cancellationRequested = true
                                onCancel()
                            }
                        ) {
                            Text(if (cancellationRequested) "Cancelling…" else "Cancel transfer")
                        }
                    }
                }
            } else if (transfer.hasStarted) {
                Text(
                    text = transfer.progressSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScpStatusStrip(
    status: String,
    modifier: Modifier = Modifier
) {
    val success = isSuccessfulTransferStatus(status)
    val containerColor = when {
        success -> Color(0xFF123C22)

        status.contains("failed", ignoreCase = true) ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)

        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        status.contains("failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
        success -> Color(0xFF78E08F)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun isSuccessfulTransferStatus(status: String): Boolean {
    return status.startsWith("Download completed", ignoreCase = true) ||
        status.startsWith("Upload completed", ignoreCase = true) ||
        status.startsWith("Move completed", ignoreCase = true) ||
        status.startsWith("Delete completed", ignoreCase = true) ||
        status.startsWith("Folder created", ignoreCase = true)
}

private fun formatRemoteModifiedTime(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "Unknown"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestampMillis))
}

@Composable
private fun ConnectionLogsPane(
    renderedLogs: List<String>,
    listState: LazyListState,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.testTag(UiTestTags.CONNECTING_LOG_PANEL),
        color = Color(0xFF090909),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Top
        ) {
            items(renderedLogs) { log ->
                Text(
                    text = log,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF9E9E9E),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 0.sp,
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }
    }
}

private fun parseComposeColor(value: String, fallback: Color): Color =
    runCatching { Color(value.toColorInt()) }.getOrDefault(fallback)

private data class CompactTerminalKey(
    val label: String,
    val action: KeyboardSlotAction,
    val enabled: Boolean,
    val repeatable: Boolean
)

private data class SwipeGestureStart(
    val x: Float,
    val y: Float,
    val timestampMs: Long
)

private data class TerminalFindMatch(
    val index: Int,
    val line: Int,
    val preview: String,
    val rowStart: Int? = null,
    val rowEnd: Int? = null,
    val columnStart: Int? = null,
    val columnEndExclusive: Int? = null
)

private data class IndexedTerminalTranscript(
    val text: String,
    val rowByIndex: IntArray,
    val columnByIndex: IntArray
)

private fun resolveSwipeKeyCode(
    start: SwipeGestureStart,
    endX: Float,
    endY: Float,
    endTimeMs: Long,
    minDistancePx: Float,
    maxDurationMs: Long? = SWIPE_NAV_MAX_DURATION_MS
): Int? {
    if (maxDurationMs != null && endTimeMs - start.timestampMs > maxDurationMs) return null
    val dx = endX - start.x
    val dy = endY - start.y
    val absX = abs(dx)
    val absY = abs(dy)
    if (absX < minDistancePx && absY < minDistancePx) return null

    val horizontalDominant = absX > absY * SWIPE_NAV_DIRECTION_RATIO
    val verticalDominant = absY > absX * SWIPE_NAV_DIRECTION_RATIO
    return when {
        horizontalDominant -> if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        verticalDominant -> if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        else -> null
    }
}

private fun computeTerminalFindMatches(
    text: String,
    query: String,
    caseSensitive: Boolean,
    emulator: TerminalEmulator?,
    joinWrappedRows: Boolean
): List<TerminalFindMatch> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return emptyList()
    val indexedTranscript = emulator?.let {
        buildIndexedTerminalTranscript(it, joinWrappedRows)
    }
    val searchableText = indexedTranscript?.text ?: text
    if (searchableText.isBlank()) return emptyList()
    val haystack = if (caseSensitive) searchableText else searchableText.lowercase()
    val needle = if (caseSensitive) trimmedQuery else trimmedQuery.lowercase()
    val matches = mutableListOf<TerminalFindMatch>()
    var startAt = 0
    while (startAt < haystack.length) {
        val hit = haystack.indexOf(needle, startAt)
        if (hit < 0) break
        val line = searchableText.substring(0, hit).count { it == '\n' } + 1
        val lineStart = searchableText.lastIndexOf('\n', hit - 1).let { idx -> if (idx >= 0) idx + 1 else 0 }
        val lineEnd = searchableText.indexOf('\n', hit).let { idx -> if (idx >= 0) idx else searchableText.length }
        val lineText = searchableText.substring(lineStart, lineEnd).trim()
        val preview = if (lineText.length > FIND_PREVIEW_MAX_CHARS) {
            "${lineText.take(FIND_PREVIEW_MAX_CHARS - 3)}..."
        } else {
            lineText
        }
        val matchEndExclusive = (hit + needle.length).coerceAtMost(searchableText.length)
        val matchEndIndex = (matchEndExclusive - 1).coerceAtLeast(hit)
        val rowStart = indexedTranscript?.rowByIndex?.getOrNull(hit)
        val rowEnd = indexedTranscript?.rowByIndex?.getOrNull(matchEndIndex) ?: rowStart
        val columnStart = indexedTranscript?.columnByIndex?.getOrNull(hit)
        var columnEndExclusive = if (columnStart != null) {
            columnStart + maxOf(trimmedQuery.codePointCount(0, trimmedQuery.length), 1)
        } else {
            null
        }
        if (indexedTranscript != null && rowEnd != null && matchEndExclusive < indexedTranscript.text.length) {
            val nextRow = indexedTranscript.rowByIndex.getOrNull(matchEndExclusive)
            if (nextRow == rowEnd) {
                columnEndExclusive = indexedTranscript.columnByIndex.getOrNull(matchEndExclusive) ?: columnEndExclusive
            } else if (rowEnd != rowStart) {
                columnEndExclusive = emulator.mColumns
            }
        } else if (emulator != null && rowEnd != null && rowEnd != rowStart) {
            columnEndExclusive = emulator.mColumns
        }
        if (columnStart != null) {
            columnEndExclusive = (columnEndExclusive ?: (columnStart + 1)).coerceAtLeast(columnStart + 1)
        }
        matches += TerminalFindMatch(
            index = hit,
            line = line,
            preview = preview,
            rowStart = rowStart,
            rowEnd = rowEnd,
            columnStart = columnStart,
            columnEndExclusive = columnEndExclusive
        )
        startAt = hit + maxOf(needle.length, 1)
        if (matches.size >= FIND_RESULT_LIMIT) break
    }
    return matches
}

private fun buildIndexedTerminalTranscript(
    emulator: TerminalEmulator,
    joinWrappedRows: Boolean
): IndexedTerminalTranscript {
    val screen = emulator.screen
    val activeTranscriptRows = screen.activeTranscriptRows
    val endRow = emulator.mRows - 1
    val builder = StringBuilder()
    val rowByIndex = ArrayList<Int>()
    val columnByIndex = ArrayList<Int>()
    for (row in -activeTranscriptRows..endRow) {
        val rowText = screen.getSelectedText(
            0,
            row,
            emulator.mColumns,
            row,
            false,
            false
        )
        var column = 0
        var charIndex = 0
        while (charIndex < rowText.length) {
            val codePoint = Character.codePointAt(rowText, charIndex)
            val charCount = Character.charCount(codePoint)
            repeat(charCount) { offset ->
                builder.append(rowText[charIndex + offset])
                rowByIndex += row
                columnByIndex += column
            }
            val width = WcWidth.width(codePoint).coerceAtLeast(0)
            column += width
            charIndex += charCount
        }
        val appendNewline = row < endRow && !(joinWrappedRows && screen.getLineWrap(row))
        if (appendNewline) {
            builder.append('\n')
            rowByIndex += row
            columnByIndex += column
        }
    }
    return IndexedTerminalTranscript(
        text = builder.toString(),
        rowByIndex = rowByIndex.toIntArray(),
        columnByIndex = columnByIndex.toIntArray()
    )
}

private fun copyUriToLocalCache(context: Context, uri: Uri, prefix: String): File? {
    val contentResolver = context.contentResolver
    val displayName = queryUriDisplayName(context, uri) ?: "selected_file"
    val safeName = displayName
        .replace('/', '_')
        .replace('\\', '_')
    val cacheSubDir = File(context.cacheDir, prefix).apply { mkdirs() }
    val target = File(cacheSubDir, safeName)
    return runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to read selected file stream")
        target
    }.getOrNull()
}

private fun queryUriDisplayName(context: Context, uri: Uri): String? {
    val contentResolver = context.contentResolver
    return runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun applyTerminalTypeface(
    view: TerminalView,
    font: com.majordaftapps.sshpeaches.app.data.model.TerminalFont
) {
    runCatching {
        view.setTypeface(resolveTerminalTypeface(view.context, font))
    }
}

private fun vibrateTerminalBell(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(
            TERMINAL_BELL_VIBRATION_MS,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
    )
}

private fun showTerminalBellNotification(context: Context, request: QuickConnectRequest?) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notificationManager = NotificationManagerCompat.from(context)
    if (!notificationManager.areNotificationsEnabled()) return
    val systemNotificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
    systemNotificationManager.createNotificationChannel(
        NotificationChannel(
            TERMINAL_BELL_NOTIFICATION_CHANNEL_ID,
            "SSHPeaches Terminal Bell",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for terminal bell events"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
    )
    val content = request?.let { "${it.username}@${it.host}:${it.port} requested attention" }
        ?: "An active session requested attention"
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        request?.sessionId?.hashCode()?.and(Int.MAX_VALUE) ?: 0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    notificationManager.notify(
        TERMINAL_BELL_NOTIFICATION_ID_BASE + (request?.sessionId?.hashCode()?.and(Int.MAX_VALUE) ?: 0),
        NotificationCompat.Builder(context, TERMINAL_BELL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Terminal bell")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    )
}

private fun tokenizeSftpCommand(input: String): List<String> {
    val tokenPattern = Regex("""[^\s"']+|"([^"]*)"|'([^']*)'""")
    return tokenPattern.findAll(input).map { match ->
        match.groups[1]?.value ?: match.groups[2]?.value ?: match.value
    }.toList()
}

private fun buildRemoteBreadcrumbs(path: String): List<RemoteBreadcrumb> {
    val normalized = path.trim().ifBlank { "." }
    if (normalized == "." || normalized == "/") {
        return listOf(RemoteBreadcrumb(normalized, normalized))
    }

    val cleaned = normalized.replace('\\', '/').trimEnd('/')
    val isAbsolute = cleaned.startsWith("/")
    val segments = cleaned.split('/').filter { it.isNotBlank() && it != "." }
    val breadcrumbs = mutableListOf(
        RemoteBreadcrumb(
            label = if (isAbsolute) "/" else ".",
            path = if (isAbsolute) "/" else "."
        )
    )
    var current = if (isAbsolute) "/" else "."
    segments.forEach { segment ->
        current = when {
            isAbsolute && current == "/" -> "/$segment"
            current.endsWith("/") -> "$current$segment"
            else -> "$current/$segment"
        }
        breadcrumbs += RemoteBreadcrumb(label = segment, path = current)
    }
    return breadcrumbs
}

private val PROTECTED_REMOTE_SYSTEM_DIRECTORIES = setOf(
    "/",
    "/bin",
    "/boot",
    "/dev",
    "/etc",
    "/home",
    "/lib",
    "/lib64",
    "/lost+found",
    "/media",
    "/mnt",
    "/opt",
    "/proc",
    "/root",
    "/run",
    "/sbin",
    "/srv",
    "/sys",
    "/tmp",
    "/usr",
    "/var"
)

private fun isProtectedRemoteSystemDirectory(path: String?, isDirectory: Boolean): Boolean {
    if (!isDirectory) return false
    val normalized = path
        ?.trim()
        ?.replace('\\', '/')
        ?.trimEnd('/')
        ?.ifBlank { "/" }
        ?: return false
    return normalized in PROTECTED_REMOTE_SYSTEM_DIRECTORIES
}

private fun isValidRemoteChildName(value: String): Boolean {
    val name = value.trim()
    return name.isNotEmpty() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name
}

private fun normalizeImeChunk(chunk: String): String {
    if (chunk.isEmpty()) return chunk
    val normalized = chunk
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u0000", "")
    return normalized.replace('\n', '\r')
}

private const val TERMINAL_IME_SENTINEL = "\u0001"
private const val TERMINAL_CLIPBOARD_SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"
private const val TERMINAL_CLIPBOARD_SUPPRESS_OVERLAY_EXTRA =
    "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY"
private const val KEYBOARD_REQUESTED_STATE = "keyboard_requested"
private const val KEYBOARD_HIDDEN_STATE = "keyboard_hidden"
private const val TERMINAL_PRIVATE_IME_OPTIONS =
    "com.google.android.inputmethod.latin.noPersonalizedLearning=true;com.google.android.inputmethod.latin.noMicrophoneKey=true"
private val TERMINAL_IME_CONTENT_MIME_TYPES = arrayOf(
    ClipDescription.MIMETYPE_TEXT_PLAIN,
    ClipDescription.MIMETYPE_TEXT_HTML
)
private val TERMINAL_IME_INPUT_TYPE = InputType.TYPE_CLASS_TEXT or
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
    InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
private val TERMINAL_IME_OPTIONS = EditorInfo.IME_ACTION_NONE or
    EditorInfo.IME_FLAG_NO_EXTRACT_UI or
    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
private const val TERMINAL_IME_INLINE_PASTE_MAX_BYTES = 1_048_576
private const val TERMINAL_IME_CONTENT_READ_BUFFER_BYTES = 8_192
private const val SFTP_CONSOLE_MAX_LINES = 500
private const val SFTP_DIRECTORY_ENTRY_OUTPUT_LIMIT = SFTP_CONSOLE_MAX_LINES - 2
private const val KEY_REPEAT_INITIAL_DELAY_MS = 350L
private const val KEY_REPEAT_INTERVAL_MS = 65L
private val COMPACT_KEY_WIDE_LAYOUT_MIN_WIDTH = 600.dp
private const val SWIPE_NAV_REPEAT_INITIAL_DELAY_MS = 350L
private const val SWIPE_NAV_REPEAT_INTERVAL_MS = 65L
private const val SWIPE_NAV_MIN_DISTANCE_DP = 28
private const val SWIPE_NAV_MAX_DURATION_MS = 1200L
private const val SWIPE_NAV_DIRECTION_RATIO = 1.2f
private const val FIND_RESULT_LIMIT = 200
private const val FIND_PREVIEW_MAX_CHARS = 120
private const val TERMINAL_BELL_NOTIFICATION_CHANNEL_ID = "terminal_bell"
private const val TERMINAL_BELL_NOTIFICATION_ID_BASE = 24_000
private const val TERMINAL_BELL_THROTTLE_MS = 750L
private const val TERMINAL_BELL_VIBRATION_MS = 120L
