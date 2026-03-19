package com.majordaftapps.sshpeaches.app.ui.screens

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.security.SecurityManager
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
import android.content.Context
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.majordaftapps.sshpeaches.app.MainActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
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
    val password: String,
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
    val message: String = ""
) : Serializable

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectingScreen(
    request: QuickConnectRequest?,
    state: QuickConnectUiState,
    logs: List<SessionLogBus.Entry>,
    shellOutput: String,
    remoteDirectory: com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectorySnapshot?,
    terminalProfile: TerminalProfile,
    terminalSelectionMode: TerminalSelectionMode,
    terminalBellMode: TerminalBellMode = TerminalBellMode.DISABLED,
    useVolumeButtonsToAdjustFontSize: Boolean = false,
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
    onManageRemotePath: (operation: String, sourcePath: String, destinationPath: String?) -> Unit,
    resolveTerminalEmulator: (String) -> com.termux.terminal.TerminalEmulator? = { null },
    onRetry: () -> Unit,
    onToggleConnectedHostBar: () -> Unit,
    onOpenSettings: () -> Unit,
    findRequestToken: Int
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val terminalMarginDp = (terminalMarginPx.coerceIn(0, 128) / density.density).dp
    val bellThrottle = remember(request?.sessionId) { AtomicLong(0L) }
    val keyboardFocusRequester = remember(request?.sessionId) { FocusRequester() }
    val terminalEngine = remember(request?.sessionId, clipboardManager) {
        TermuxTerminalEngine(
            onWriteToRemote = onSendShellBytes,
            onCopyToClipboard = { text -> clipboardManager.setText(AnnotatedString(text)) },
            onRequestPasteText = { clipboardManager.getText()?.text }
        )
    }
    val terminalInput = remember(request?.sessionId) {
        TerminalInputRouter(
            emulatorProvider = { terminalEngine.emulator() },
            onWriteToRemote = onSendShellBytes
        )
    }
    var lastShellSnapshot by remember(request?.sessionId) { mutableStateOf("") }
    var imeBridgeValue by remember(request?.sessionId) { mutableStateOf(TextFieldValue("")) }
    var imeSentText by remember(request?.sessionId) { mutableStateOf("") }
    var terminalViewRef by remember(request?.sessionId) { mutableStateOf<TerminalView?>(null) }
    var keyboardFocused by remember(request?.sessionId) { mutableStateOf(false) }
    var terminalFontSizeSp by rememberSaveable(request?.sessionId) { mutableStateOf(10f) }
    var lastResize by remember(request?.sessionId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var sftpPath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var downloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var downloadLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var uploadLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    val sftpConsoleLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    var sftpCommandInput by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var sftpLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var sftpPendingDirectoryEcho by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var sftpLastRenderedDirectoryKey by remember(request?.sessionId) { mutableStateOf("") }
    var pendingSftpDownloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var pendingSftpUploadBasePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    val scpActivityLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    var scpRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var scpPendingListPath by remember(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpPendingListBaselineToken by remember(request?.sessionId) { mutableStateOf<Long?>(null) }
    var scpLastListedPath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var scpHomePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    val scpPathHistory = remember(request?.sessionId) { mutableStateListOf(".") }
    var scpPathHistoryIndex by rememberSaveable(request?.sessionId) { mutableStateOf(0) }
    var scpSelectedFile by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var pendingScpDownloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpUploadSourceUri by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpTransferInProgress by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var scpTransferStatus by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var showScpUploadVertical by rememberSaveable(request?.sessionId) {
        mutableStateOf(request?.initialFileTransferEntryMode == FileTransferEntryMode.UPLOAD)
    }
    var pendingModifiers by remember(request?.sessionId) { mutableStateOf(setOf<KeyboardModifier>()) }
    var showSnippetPicker by remember(request?.sessionId) { mutableStateOf(false) }
    var keyboardVisibleRequested by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpCommandRunning by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpShowCancel by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpAwaitDirectoryRefresh by remember(request?.sessionId) { mutableStateOf(false) }
    var sftpCommandStartLogCount by remember(request?.sessionId) { mutableStateOf(0) }
    var sftpCommandStartDirectoryKey by remember(request?.sessionId) { mutableStateOf("") }
    var sftpCancelDelayJob by remember(request?.sessionId) { mutableStateOf<Job?>(null) }
    var swipeNavigationEnabled by remember(request?.sessionId) { mutableStateOf(false) }
    var swipeStart by remember(request?.sessionId) { mutableStateOf<SwipeGestureStart?>(null) }
    var swipeIntercepting by remember(request?.sessionId) { mutableStateOf(false) }
    var swipeRepeatJob by remember(request?.sessionId) { mutableStateOf<Job?>(null) }
    var swipeRepeatKeyCode by remember(request?.sessionId) { mutableStateOf<Int?>(null) }
    var showFindDialog by remember(request?.sessionId) { mutableStateOf(false) }
    var findQuery by remember(request?.sessionId) { mutableStateOf("") }
    var findCaseSensitive by remember(request?.sessionId) { mutableStateOf(false) }
    var findMatchIndex by remember(request?.sessionId) { mutableStateOf(0) }

    val compactKeys = remember(keyboardSlots) {
        KeyboardLayoutDefaults.normalizeSlots(keyboardSlots).map { action ->
            CompactTerminalKey(
                label = KeyboardLayoutDefaults.compactLabel(action, fallback = "+"),
                action = action,
                enabled = !action.isEmpty(),
                repeatable = action.repeatable
            )
        }
    }
    val activeAliasIcons = remember(swipeNavigationEnabled) {
        if (swipeNavigationEnabled) setOf("swipe_nav") else emptySet()
    }
    val swipeNavMinDistancePx = with(density) { SWIPE_NAV_MIN_DISTANCE_DP.dp.toPx() }
    val latestRequest by rememberUpdatedState(request)
    val latestRemoteDirectory by rememberUpdatedState(remoteDirectory)
    val latestScpPendingListPath by rememberUpdatedState(scpPendingListPath)
    val latestScpRemotePath by rememberUpdatedState(scpRemotePath)
    val latestOnSftpListDirectory by rememberUpdatedState(onSftpListDirectory)

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
        QuickConnectPhase.ERROR -> "Connection failed"
        QuickConnectPhase.IDLE -> "Preparing..."
    }
    val statusColor = when (state.phase) {
        QuickConnectPhase.ERROR -> Color(0xFFFF6B6B)
        else -> colorResource(id = R.color.peachy_orange)
    }

    val hostName = request?.let { "${it.username}@${it.host}:${it.port}" } ?: "Quick Connect"
    val renderedLogs = logs.map { "[${it.level}] ${it.message}" }
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
    val userFacingStateMessage = when {
        request?.mode == ConnectionMode.SCP && state.phase != QuickConnectPhase.ERROR -> {
            when (state.phase) {
                QuickConnectPhase.SUCCESS -> {
                    if (request.initialFileTransferEntryMode == FileTransferEntryMode.UPLOAD) {
                        "Ready to upload files."
                    } else {
                        "Ready to download files."
                    }
                }

                else -> {
                    if (request.initialFileTransferEntryMode == FileTransferEntryMode.UPLOAD) {
                        "Preparing file upload..."
                    } else {
                        "Preparing file download..."
                    }
                }
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
    val toggleSystemKeyboard = {
        if (keyboardVisibleRequested) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            keyboardVisibleRequested = false
        } else {
            keyboardFocusRequester.requestFocus()
            keyboardController?.show()
            keyboardFocused = true
            keyboardVisibleRequested = true
        }
    }
    val terminalViewClient = remember(request?.sessionId) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                terminalFontSizeSp = (terminalFontSizeSp * scale).coerceIn(6f, 28f)
                terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) })
                return 1f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                onToggleConnectedHostBar()
                if (keyboardVisibleRequested) {
                    keyboardFocusRequester.requestFocus()
                    keyboardController?.show()
                    keyboardFocused = true
                }
            }

            override fun onDoubleTap(e: MotionEvent) {
                toggleSystemKeyboard()
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false

            override fun shouldEnforceCharBasedInput(): Boolean = true

            override fun isTerminalViewSelected(): Boolean = keyboardFocused

            override fun copyModeChanged(copyMode: Boolean) = Unit

            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
                if (handleVolumeKeyForFontSize(e)) return true
                return terminalInput.onAndroidKeyDown(e)
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
                return true
            }
        }
    }

    LaunchedEffect(renderedLogs.size) {
        if (renderedLogs.isNotEmpty()) {
            listState.animateScrollToItem(renderedLogs.size - 1)
        }
    }
    LaunchedEffect(request?.sessionId) {
        terminalEngine.reset()
        terminalEngine.applyProfile(terminalProfile)
        lastShellSnapshot = ""
        imeBridgeValue = TextFieldValue("")
        imeSentText = ""
        terminalFontSizeSp = terminalProfile.fontSizeSp.toFloat()
        lastResize = null
        sftpPath = "."
        downloadRemotePath = ""
        downloadLocalPath = ""
        uploadLocalPath = ""
        sftpCommandInput = ""
        sftpLocalPath = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        sftpPendingDirectoryEcho = null
        sftpLastRenderedDirectoryKey = ""
        pendingSftpDownloadRemotePath = null
        pendingSftpUploadBasePath = null
        sftpConsoleLines.clear()
        scpActivityLines.clear()
        scpRemotePath = "."
        scpPendingListPath = null
        scpPendingListBaselineToken = null
        scpLastListedPath = "."
        scpHomePath = null
        scpPathHistory.clear()
        scpPathHistory += "."
        scpPathHistoryIndex = 0
        scpSelectedFile = null
        pendingScpDownloadRemotePath = null
        scpUploadSourceUri = null
        scpTransferInProgress = false
        scpTransferStatus = null
        if (request?.mode == ConnectionMode.SFTP) {
            sftpConsoleLines += "Connected to ${request.host}:${request.port}"
            sftpConsoleLines += "Type 'help' for SFTP commands."
        }
        if (request?.mode == ConnectionMode.SCP) {
            scpPendingListPath = scpRemotePath
            scpPendingListBaselineToken = remoteDirectory?.refreshToken
            onSftpListDirectory(scpRemotePath)
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
        sftpCancelDelayJob?.cancel()
        sftpCancelDelayJob = null
        sftpCommandRunning = false
        sftpShowCancel = false
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
    LaunchedEffect(logs.size, request?.mode) {
        if (request?.mode != ConnectionMode.SCP || logs.isEmpty()) return@LaunchedEffect
        val latest = logs.last().message
        when {
            latest.startsWith("SCP download complete:") -> {
                scpTransferInProgress = false
                scpTransferStatus = "Download completed successfully."
            }
            latest.startsWith("SCP upload complete:") -> {
                scpTransferInProgress = false
                scpTransferStatus = "Upload completed successfully."
            }
            latest.startsWith("SCP download failed") -> {
                scpTransferInProgress = false
                scpTransferStatus = "Download failed. ${latest.substringAfter(':', "").trim()}"
            }
            latest.startsWith("SCP upload failed") -> {
                scpTransferInProgress = false
                scpTransferStatus = "Upload failed. ${latest.substringAfter(':', "").trim()}"
            }
        }
    }
    LaunchedEffect(scpTransferStatus, request?.mode) {
        if (request?.mode != ConnectionMode.SCP) return@LaunchedEffect
        val status = scpTransferStatus ?: return@LaunchedEffect
        delay(SCP_TRANSFER_STATUS_AUTO_DISMISS_MS)
        if (scpTransferStatus == status) {
            scpTransferStatus = null
        }
    }
    LaunchedEffect(terminalProfile.id, terminalProfile.font) {
        terminalEngine.applyProfile(terminalProfile)
        terminalViewRef?.let { view ->
            applyTerminalTypeface(view, terminalProfile.font)
        }
        terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) })
        terminalViewRef?.onScreenUpdated()
    }
    DisposableEffect(request?.sessionId) {
        onDispose {
            sftpCancelDelayJob?.cancel()
            swipeRepeatJob?.cancel()
            swipeRepeatJob = null
            swipeRepeatKeyCode = null
            terminalViewRef = null
        }
    }
    DisposableEffect(lifecycleOwner, request?.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (latestRequest?.mode == ConnectionMode.SCP) {
                        val targetPath = latestScpPendingListPath
                            ?: latestRemoteDirectory?.path
                            ?: latestScpRemotePath
                        if (latestScpPendingListPath != null || latestRemoteDirectory == null) {
                            scpRemotePath = targetPath
                            scpLastListedPath = targetPath
                            scpPendingListPath = targetPath
                            scpPendingListBaselineToken =
                                latestRemoteDirectory?.takeIf { it.path == targetPath }?.refreshToken
                            latestOnSftpListDirectory(targetPath)
                        }
                    }
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    keyboardFocused = false
                    keyboardVisibleRequested = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardVisibleRequested = false
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
                terminalEngine.appendIncoming(delta.toByteArray(StandardCharsets.UTF_8))
            }
        } else {
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
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            keyboardVisibleRequested = false
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            keyboardVisibleRequested = false
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
    LaunchedEffect(remoteDirectory?.path, request?.sessionId, request?.mode) {
        val path = remoteDirectory?.path ?: return@LaunchedEffect
        when (request?.mode) {
            ConnectionMode.SFTP -> sftpPath = path
            ConnectionMode.SCP -> {
                if (scpHomePath == null && (scpPendingListPath == "." || scpLastListedPath == ".")) {
                    scpHomePath = path
                }
                scpRemotePath = path
                scpLastListedPath = path
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
        if (scpPendingListPath == null || logs.isEmpty()) return@LaunchedEffect
        val latest = logs.last().message
        if (
            latest.startsWith("Directory listing failed for") ||
            latest.startsWith("SFTP operation failed:")
        ) {
            scpPendingListPath = null
            scpPendingListBaselineToken = null
            scpActivityLines.clear()
            scpActivityLines += latest
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
        sftpConsoleLines += line
        val overflow = sftpConsoleLines.size - 500
        if (overflow > 0) {
            repeat(overflow) { sftpConsoleLines.removeAt(0) }
        }
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

    LaunchedEffect(remoteDirectory?.path, remoteDirectory?.entries, request?.sessionId, request?.mode) {
        if (request?.mode != ConnectionMode.SFTP) return@LaunchedEffect
        val snapshot = remoteDirectory ?: return@LaunchedEffect
        val key = buildString {
            append(snapshot.path)
            append('|')
            append(snapshot.entries.size)
            snapshot.entries.forEach {
                append('|')
                append(if (it.isDirectory) "d:" else "f:")
                append(it.name)
                append(':')
                append(it.sizeBytes)
            }
        }
        if (key == sftpLastRenderedDirectoryKey) return@LaunchedEffect
        sftpLastRenderedDirectoryKey = key
        if (sftpPendingDirectoryEcho == null) return@LaunchedEffect
        appendSftpConsole("Remote directory: ${snapshot.path}")
        if (snapshot.entries.isEmpty()) {
            appendSftpConsole("(empty)")
        } else {
            snapshot.entries.forEach { entry ->
                val label = if (entry.isDirectory) "d" else "-"
                val size = entry.sizeBytes.toString()
                appendSftpConsole("$label $size ${entry.name}")
            }
        }
        sftpPendingDirectoryEcho = null
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
        val inline = request?.password.orEmpty()
        if (inline.isNotBlank()) return inline
        val hostId = request?.savedHostId ?: return ""
        return runCatching { SecurityManager.getHostPassword(hostId) }.getOrNull().orEmpty()
    }

    fun handleIconAlias(action: KeyboardSlotAction): Boolean {
        return when (action.iconId) {
            "code", "snippet_picker" -> {
                showSnippetPicker = true
                true
            }
            "up" -> {
                sendArrowKey(KeyEvent.KEYCODE_DPAD_UP)
                true
            }
            "down" -> {
                sendArrowKey(KeyEvent.KEYCODE_DPAD_DOWN)
                true
            }
            "left" -> {
                sendArrowKey(KeyEvent.KEYCODE_DPAD_LEFT)
                true
            }
            "right" -> {
                sendArrowKey(KeyEvent.KEYCODE_DPAD_RIGHT)
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
                toggleSystemKeyboard()
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
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .testTag(UiTestTags.SCREEN_CONNECTING)
    ) {
        if (showTerminalSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                        color = Color(0xFF080808)
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
                            update = { view ->
                                terminalViewRef = view
                                view.setTerminalViewClient(terminalViewClient)
                                view.setSelectionJoinBackLines(
                                    terminalSelectionMode == TerminalSelectionMode.NATURAL
                                )
                                val emulator = externalTerminalEmulator ?: terminalEngine.emulator()
                                view.attachEmulator(emulator)
                                applyTerminalTypeface(view, terminalProfile.font)
                                val textSizePx = with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(6) }
                                view.setTextSize(textSizePx)
                                view.updateSize()
                                val resize = emulator.mColumns to emulator.mRows
                                if (lastResize != resize) {
                                    lastResize = resize
                                    onTerminalResize(emulator.mColumns, emulator.mRows)
                                }
                                view.setOnTouchListener { _, event ->
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
                                        else -> {
                                            true
                                        }
                                    }
                                }
                                view.onScreenUpdated()
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showFindDialog) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                color = Color(0xFF101010),
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
                                                    color = Color(0xFF2D2D2D),
                                                    shape = RoundedCornerShape(6.dp)
                                                ),
                                            color = Color(0xFF161616),
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
                                                    onValueChange = { findQuery = it },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color(0xFFEDEDED)
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
                                                                    color = Color(0xFF8C8C8C)
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
                                                .clickable { findCaseSensitive = !findCaseSensitive },
                                            color = if (findCaseSensitive) Color(0xFF5B3A0F) else Color(0xFF1B1B1B),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Aa",
                                                    color = Color(0xFFEDEDED),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                if (findMatches.isNotEmpty()) {
                                                    val size = findMatches.size
                                                    findMatchIndex = ((findMatchIndex - 1) % size + size) % size
                                                }
                                            },
                                            enabled = findMatches.isNotEmpty(),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Previous result",
                                                tint = Color(0xFFDEDEDE)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (findMatches.isNotEmpty()) {
                                                    val size = findMatches.size
                                                    findMatchIndex = (findMatchIndex + 1).mod(size)
                                                }
                                            },
                                            enabled = findMatches.isNotEmpty(),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Next result",
                                                tint = Color(0xFFDEDEDE)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                showFindDialog = false
                                                terminalViewRef?.clearSearchHighlight()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close find",
                                                tint = Color(0xFFBDBDBD)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when {
                                            findQuery.isBlank() -> "Enter search text"
                                            findMatches.isEmpty() -> "No matches"
                                            else -> {
                                                val activeIndex = findMatchIndex.coerceIn(0, findMatches.lastIndex)
                                                val active = activeFindMatch
                                                if (active != null) {
                                                    "${activeIndex + 1}/${findMatches.size} line ${active.line}: ${active.preview}"
                                                } else {
                                                    "${activeIndex + 1}/${findMatches.size}"
                                                }
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Color(0xFFBDBDBD),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag(UiTestTags.CONNECTING_FIND_STATUS)
                                    )
                                }
                            }
                        }

                        CompactKeyRow(
                            keys = compactKeys,
                            activeModifiers = pendingModifiers,
                            activeAliasIcons = activeAliasIcons,
                            onSendKey = { key ->
                                if (!key.enabled) return@CompactKeyRow
                                val action = key.action
                                if (handleIconAlias(action)) {
                                    pendingModifiers = emptySet()
                                    return@CompactKeyRow
                                }
                                when (action.type) {
                                    KeyboardActionType.MODIFIER -> {
                                        val modifier = action.modifier ?: return@CompactKeyRow
                                        pendingModifiers = if (pendingModifiers.contains(modifier)) {
                                            pendingModifiers - modifier
                                        } else {
                                            pendingModifiers + modifier
                                        }
                                    }
                                    KeyboardActionType.TEXT -> {
                                        val text = action.text
                                        if (text.isBlank()) return@CompactKeyRow
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
                                        val keyCode = action.keyCode ?: return@CompactKeyRow
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }

                BasicTextField(
                    value = imeBridgeValue,
                    onValueChange = { next ->
                        if (next == imeBridgeValue) return@BasicTextField
                        imeBridgeValue = next

                        // Send deltas immediately so IME composing text is echoed in-terminal.
                        sendImeDelta(
                            previous = imeSentText,
                            next = next.text,
                            sendBackspace = terminalInput::sendBackspace,
                            sendInserted = { inserted ->
                                val modifiers = pendingModifiers
                                terminalInput.sendText(
                                    text = inserted,
                                    ctrlDown = modifiers.contains(KeyboardModifier.CTRL),
                                    altDown = modifiers.contains(KeyboardModifier.ALT),
                                    shiftDown = modifiers.contains(KeyboardModifier.SHIFT)
                                )
                                if (modifiers.isNotEmpty()) {
                                    pendingModifiers = emptySet()
                                }
                            }
                        )
                        imeSentText = next.text

                        if (imeSentText.length > IME_BUFFER_MAX_CHARS) {
                            val tail = imeSentText.takeLast(IME_BUFFER_KEEP_TAIL_CHARS)
                            imeSentText = tail
                            imeBridgeValue = TextFieldValue(
                                text = tail,
                                selection = TextRange(tail.length)
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(keyboardFocusRequester)
                        .onFocusChanged { keyboardFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            val nativeEvent = keyEvent.nativeKeyEvent
                            if (handleVolumeKeyForFontSize(nativeEvent)) {
                                return@onPreviewKeyEvent true
                            }
                            if (nativeEvent.action != KeyEvent.ACTION_DOWN) {
                                return@onPreviewKeyEvent false
                            }
                            terminalInput.onAndroidKeyDown(nativeEvent)
                        },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    )
                )
            }
        } else if (showScpTransferSession && request != null) {
            val remoteItems = remoteDirectory?.entries.orEmpty()
            val effectiveRemotePath = remoteDirectory?.path ?: scpRemotePath
            val normalizedRemoteInput = scpRemotePath.trim().ifBlank { "." }
            val scpListingInProgress = scpPendingListPath != null
            val canGoBack = scpPathHistoryIndex > 0 && !scpListingInProgress
            val canGoForward = scpPathHistoryIndex < scpPathHistory.lastIndex && !scpListingInProgress
            val transferFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color(0xFF8E8E8E),
                focusedLabelColor = Color(0xFFE5E5E5),
                unfocusedLabelColor = Color(0xFFBDBDBD),
                disabledLabelColor = Color(0xFF8E8E8E),
                cursorColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0xFF7A7A7A),
                disabledBorderColor = Color(0xFF555555),
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color(0xFFE5E5E5),
                disabledTrailingIconColor = Color(0xFF8E8E8E)
            )
            val scpDownloadDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                val selectedRemote = pendingScpDownloadRemotePath
                pendingScpDownloadRemotePath = null
                if (selectedRemote.isNullOrBlank()) return@rememberLauncherForActivityResult
                if (uri == null) {
                    scpTransferInProgress = false
                    scpTransferStatus = "Download cancelled."
                    appendScpActivity("Save location selection cancelled.", clearFirst = true)
                    return@rememberLauncherForActivityResult
                }
                scpTransferInProgress = true
                scpTransferStatus = null
                onScpDownload(selectedRemote, uri.toString())
                appendScpActivity("Downloading $selectedRemote -> $uri", clearFirst = true)
            }
            val scpUploadDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) {
                    appendScpActivity("Local file selection cancelled.", clearFirst = true)
                    return@rememberLauncherForActivityResult
                }
                val displayName = queryUriDisplayName(context, uri) ?: "upload.bin"
                scpUploadSourceUri = uri.toString()
                uploadLocalPath = displayName
                scpTransferStatus = null
                appendScpActivity("Selected local file: $displayName", clearFirst = true)
            }

            fun browseScpPath(target: String, recordHistory: Boolean = true) {
                if (scpPendingListPath != null) return
                val normalized = target.trim().ifBlank { "." }
                scpRemotePath = normalized
                scpLastListedPath = normalized
                scpPendingListPath = normalized
                scpPendingListBaselineToken = remoteDirectory?.refreshToken
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.CONNECTING_SCP_PANEL)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showScpUploadVertical) "Upload Files" else "Download Files",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (showScpUploadVertical) {
                        TextButton(
                            onClick = {
                                showScpUploadVertical = false
                                scpTransferStatus = null
                            },
                            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_BACK_TO_DOWNLOAD_BUTTON)
                        ) {
                            Text("Download file")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showScpUploadVertical = true
                                scpTransferStatus = null
                            },
                            modifier = Modifier.testTag(UiTestTags.CONNECTING_SCP_OPEN_UPLOAD_BUTTON)
                        ) {
                            Text("Upload file")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        enabled = canGoBack,
                        onClick = {
                            if (!canGoBack) return@IconButton
                            scpPathHistoryIndex -= 1
                            browseScpPath(scpPathHistory[scpPathHistoryIndex], recordHistory = false)
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) Color.White else Color(0xFF6E6E6E)
                        )
                    }
                    IconButton(
                        enabled = canGoForward,
                        onClick = {
                            if (!canGoForward) return@IconButton
                            scpPathHistoryIndex += 1
                            browseScpPath(scpPathHistory[scpPathHistoryIndex], recordHistory = false)
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) Color.White else Color(0xFF6E6E6E)
                        )
                    }
                    IconButton(
                        enabled = !scpListingInProgress,
                        onClick = { browseScpPath(parentPath(effectiveRemotePath)) }
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Up",
                            tint = if (scpListingInProgress) Color(0xFF6E6E6E) else Color.White
                        )
                    }
                    IconButton(
                        enabled = !scpListingInProgress,
                        onClick = { browseScpPath(scpHomePath ?: ".") }
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (scpListingInProgress) Color(0xFF6E6E6E) else Color.White
                        )
                    }
                    IconButton(
                        enabled = !scpListingInProgress,
                        onClick = { browseScpPath(effectiveRemotePath, recordHistory = false) }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (scpListingInProgress) Color(0xFF6E6E6E) else Color.White
                        )
                    }
                }
                OutlinedTextField(
                    value = scpRemotePath,
                    onValueChange = { scpRemotePath = it },
                    label = { Text("Remote path") },
                    singleLine = true,
                    enabled = !scpListingInProgress,
                    trailingIcon = {
                        if (scpListingInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFE5E5E5),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { browseScpPath(normalizedRemoteInput) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Open folder", tint = Color.White)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    colors = transferFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CONNECTING_SCP_REMOTE_DIR_INPUT)
                )
                if (scpListingInProgress) {
                    Text(
                        text = "Loading directory listing...",
                        color = Color(0xFF9E9E9E),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0xFF0F0F0F)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Location: $effectiveRemotePath",
                            color = Color(0xFFBDBDBD),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        if (remoteItems.isEmpty()) {
                            Text("(empty)", color = Color(0xFFBDBDBD))
                        } else {
                            remoteItems.forEach { item ->
                                val absolute = resolveChildPath(effectiveRemotePath, item.name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .alpha(if (scpListingInProgress) 0.65f else 1f)
                                        .background(
                                            when {
                                                !showScpUploadVertical &&
                                                    !item.isDirectory &&
                                                    scpSelectedFile == absolute -> Color(0xFF23435E)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = !scpListingInProgress) {
                                            if (item.isDirectory) {
                                                browseScpPath(absolute)
                                                if (!showScpUploadVertical) {
                                                    scpSelectedFile = null
                                                }
                                                scpTransferStatus = null
                                            } else if (!showScpUploadVertical) {
                                                scpSelectedFile = absolute
                                                scpTransferStatus = null
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (item.isDirectory) "📁 ${item.name}" else "📄 ${item.name}",
                                        color = if (item.isDirectory) Color(0xFFFFCC80) else Color(0xFFE5E5E5),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (item.isDirectory) "dir" else item.sizeBytes.toString(),
                                        color = Color(0xFF9E9E9E),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (showScpUploadVertical) {
                    Text(
                        text = "Choose a local file, then browse to the remote folder where it should be uploaded.",
                        color = Color(0xFFE5E5E5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = uploadLocalPath,
                        onValueChange = {},
                        label = { Text("Local file") },
                        readOnly = true,
                        enabled = !scpTransferInProgress,
                        trailingIcon = {
                            IconButton(
                                enabled = !scpTransferInProgress,
                                onClick = { scpUploadDocumentPicker.launch(arrayOf("*/*")) }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Choose local file", tint = Color.White)
                            }
                        },
                        colors = transferFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.CONNECTING_SCP_UPLOAD_LOCAL_INPUT)
                    )
                    Text(
                        text = "Remote folder: $effectiveRemotePath",
                        color = Color(0xFFE5E5E5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (scpUploadSourceUri.isNullOrBlank()) {
                                scpTransferStatus = "Choose a local file first."
                                return@Button
                            }
                            val remoteTarget = inferRemoteDestination(
                                uploadLocalPath.ifBlank { "upload.bin" },
                                effectiveRemotePath
                            )
                            scpTransferInProgress = true
                            scpTransferStatus = null
                            onScpUpload(scpUploadSourceUri!!, remoteTarget)
                            appendScpActivity(
                                "Uploading ${uploadLocalPath.ifBlank { "selected file" }} -> $remoteTarget",
                                clearFirst = true
                            )
                        },
                        enabled = !scpTransferInProgress && !scpListingInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.CONNECTING_SCP_UPLOAD_BUTTON)
                    ) {
                        Text("Upload file")
                    }
                } else {
                    Text(
                        text = scpSelectedFile?.let { "Selected remote file: $it" }
                            ?: "Select a remote file to download.",
                        color = Color(0xFFE5E5E5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (scpSelectedFile == null) {
                                scpTransferStatus = "Select a remote file first."
                                return@Button
                            }
                            val selectedRemote = scpSelectedFile ?: return@Button
                            pendingScpDownloadRemotePath = selectedRemote
                            scpDownloadDocumentPicker.launch(
                                selectedRemote.substringAfterLast('/').ifBlank { "download.bin" }
                            )
                        },
                        enabled = !scpTransferInProgress && !scpListingInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.CONNECTING_SCP_DOWNLOAD_BUTTON)
                    ) {
                        Text("Choose save location & Download")
                    }
                }

                if (scpTransferInProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Transfer in progress...",
                        color = Color(0xFFBDBDBD),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                AnimatedVisibility(
                    visible = scpTransferStatus != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val status = scpTransferStatus ?: return@AnimatedVisibility
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (
                            status.startsWith("Download completed") || status.startsWith("Upload completed")
                        ) {
                            Color(0xFF1B5E20)
                        } else {
                            Color(0xFF5D4037)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else if (showSftpCliSession && request != null) {
            val effectiveSftpPath = remoteDirectory?.path ?: sftpPath
            val currentRemoteSnapshotKey = remember(remoteDirectory) {
                remoteDirectory?.let { snapshot ->
                    buildString {
                        append(snapshot.path)
                        append('|')
                        append(snapshot.entries.size)
                        snapshot.entries.forEach { entry ->
                            append('|')
                            append(if (entry.isDirectory) 'd' else 'f')
                            append(':')
                            append(entry.name)
                            append(':')
                            append(entry.sizeBytes)
                        }
                    }
                }.orEmpty()
            }
            fun beginSftpCommandWait(waitForDirectoryRefresh: Boolean) {
                sftpCancelDelayJob?.cancel()
                sftpCommandRunning = true
                sftpShowCancel = false
                sftpAwaitDirectoryRefresh = waitForDirectoryRefresh
                sftpCommandStartLogCount = logs.size
                sftpCommandStartDirectoryKey = currentRemoteSnapshotKey
                sftpCancelDelayJob = scope.launch {
                    delay(SFTP_CANCEL_BUTTON_DELAY_MS)
                    if (sftpCommandRunning) {
                        sftpShowCancel = true
                    }
                }
            }
            val sftpDownloadDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                val remote = pendingSftpDownloadRemotePath
                pendingSftpDownloadRemotePath = null
                if (remote.isNullOrBlank()) return@rememberLauncherForActivityResult
                if (uri == null) {
                    appendSftpConsole("Save location selection cancelled.")
                    return@rememberLauncherForActivityResult
                }
                beginSftpCommandWait(waitForDirectoryRefresh = false)
                onSftpDownload(remote, uri.toString())
                appendSftpConsole("Downloading $remote -> $uri")
            }
            val sftpUploadDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val remoteBase = pendingSftpUploadBasePath
                pendingSftpUploadBasePath = null
                if (remoteBase.isNullOrBlank()) return@rememberLauncherForActivityResult
                if (uri == null) {
                    appendSftpConsole("Local file selection cancelled.")
                    return@rememberLauncherForActivityResult
                }
                val localName = queryUriDisplayName(context, uri) ?: "upload.bin"
                val remote = inferRemoteDestination(localName, remoteBase)
                beginSftpCommandWait(waitForDirectoryRefresh = false)
                onSftpUpload(uri.toString(), remote)
                appendSftpConsole("Uploading $localName -> $remote")
            }
            val consoleScrollState = rememberScrollState()

            fun resolveLocalPath(raw: String): String {
                val candidate = File(raw)
                return if (candidate.isAbsolute) {
                    candidate.absolutePath
                } else {
                    File(sftpLocalPath, raw).absolutePath
                }
            }

            fun finishSftpCommandWait() {
                sftpCancelDelayJob?.cancel()
                sftpCancelDelayJob = null
                sftpCommandRunning = false
                sftpShowCancel = false
                sftpAwaitDirectoryRefresh = false
                sftpCommandStartLogCount = 0
                sftpCommandStartDirectoryKey = ""
            }

            fun runSftpCommand(input: String) {
                val command = input.trim()
                if (command.isEmpty()) return
                if (sftpCommandRunning) {
                    appendSftpConsole("A command is already running. Tap Cancel to stop waiting.")
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
                        appendSftpConsole("          mkdir <path>, rm <path>, mv <src> <dst>, lcd <path>, lpwd, lls [path], refresh, clear")
                        appendSftpConsole("          get without a local path opens a save picker; put without a local path opens a file picker")
                    }
                    "clear" -> sftpConsoleLines.clear()
                    "pwd" -> appendSftpConsole(effectiveSftpPath)
                    "lpwd" -> appendSftpConsole(sftpLocalPath)
                    "refresh" -> {
                        beginSftpCommandWait(waitForDirectoryRefresh = true)
                        sftpPendingDirectoryEcho = effectiveSftpPath
                        onSftpListDirectory(effectiveSftpPath)
                    }
                    "ls" -> {
                        val target = resolveRemotePath(effectiveSftpPath, args.firstOrNull().orEmpty())
                        beginSftpCommandWait(waitForDirectoryRefresh = true)
                        sftpPath = target
                        sftpPendingDirectoryEcho = target
                        onSftpListDirectory(target)
                    }
                    "cd" -> {
                        val target = resolveRemotePath(effectiveSftpPath, args.firstOrNull() ?: ".")
                        beginSftpCommandWait(waitForDirectoryRefresh = true)
                        sftpPath = target
                        sftpPendingDirectoryEcho = target
                        onSftpListDirectory(target)
                    }
                    "lcd" -> {
                        val targetArg = args.firstOrNull()
                        if (targetArg.isNullOrBlank()) {
                            appendSftpConsole("usage: lcd <local-path>")
                        } else {
                            val target = File(resolveLocalPath(targetArg))
                            if (target.exists() && target.isDirectory) {
                                sftpLocalPath = target.absolutePath
                                appendSftpConsole("Local directory: ${target.absolutePath}")
                            } else {
                                appendSftpConsole("lcd failed: no such directory: ${target.absolutePath}")
                            }
                        }
                    }
                    "lls" -> {
                        val target = File(
                            resolveLocalPath(args.firstOrNull().orEmpty().ifBlank { "." })
                        )
                        if (!target.exists() || !target.isDirectory) {
                            appendSftpConsole("lls failed: no such directory: ${target.absolutePath}")
                        } else {
                            appendSftpConsole("Local directory: ${target.absolutePath}")
                            val entries = listLocalFiles(target.absolutePath)
                            if (entries.isEmpty()) {
                                appendSftpConsole("(empty)")
                            } else {
                                entries.forEach { item ->
                                    appendSftpConsole(
                                        "${if (item.isDirectory) "d" else "-"} ${item.length()} ${item.name}"
                                    )
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
                                pendingSftpDownloadRemotePath = remote
                                sftpDownloadDocumentPicker.launch(
                                    remote.substringAfterLast('/').ifBlank { "download.bin" }
                                )
                            } else {
                                val local = resolveLocalPath(explicitLocal)
                                beginSftpCommandWait(waitForDirectoryRefresh = false)
                                onSftpDownload(remote, local)
                                appendSftpConsole("Downloading $remote -> $local")
                            }
                        }
                    }
                    "put" -> {
                        val localArg = args.firstOrNull()
                        if (localArg.isNullOrBlank()) {
                            pendingSftpUploadBasePath = effectiveSftpPath
                            sftpUploadDocumentPicker.launch(arrayOf("*/*"))
                        } else {
                            val local = resolveLocalPath(localArg)
                            val remote = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                                resolveRemotePath(effectiveSftpPath, it)
                            } ?: inferRemoteDestination(local, effectiveSftpPath)
                            beginSftpCommandWait(waitForDirectoryRefresh = false)
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
                            beginSftpCommandWait(waitForDirectoryRefresh = false)
                            onManageRemotePath("mkdir", target, null)
                            appendSftpConsole("Created directory: $target")
                        }
                    }
                    "rm", "delete" -> {
                        val targetArg = args.firstOrNull()
                        if (targetArg.isNullOrBlank()) {
                            appendSftpConsole("usage: rm <remote-path>")
                        } else {
                            val target = resolveRemotePath(effectiveSftpPath, targetArg)
                            beginSftpCommandWait(waitForDirectoryRefresh = false)
                            onManageRemotePath("delete", target, null)
                            appendSftpConsole("Deleted: $target")
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
                            beginSftpCommandWait(waitForDirectoryRefresh = false)
                            onManageRemotePath("move", src, dst)
                            appendSftpConsole("Moved: $src -> $dst")
                        }
                    }
                    "exit", "quit", "bye" -> {
                        appendSftpConsole("Use the top-right close action to disconnect this session.")
                    }
                    else -> appendSftpConsole("Unknown command: $cmd. Type 'help' for commands.")
                }
            }

            LaunchedEffect(sftpCommandRunning, logs.size, currentRemoteSnapshotKey) {
                if (!sftpCommandRunning) return@LaunchedEffect
                val completedByLog = logs.size > sftpCommandStartLogCount
                val completedByDirectory =
                    sftpAwaitDirectoryRefresh &&
                        currentRemoteSnapshotKey.isNotBlank() &&
                        currentRemoteSnapshotKey != sftpCommandStartDirectoryKey
                if (completedByLog || completedByDirectory) {
                    finishSftpCommandWait()
                }
            }

            LaunchedEffect(sftpConsoleLines.size) {
                if (sftpConsoleLines.isNotEmpty()) {
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
                Text(
                    text = "SFTP Console",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Remote: $effectiveSftpPath",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = "Local: $sftpLocalPath",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(UiTestTags.CONNECTING_SFTP_CONSOLE),
                    color = Color(0xFF0F0F0F)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(consoleScrollState)
                            .padding(12.dp)
                    ) {
                        val activityLog = if (sftpConsoleLines.isEmpty()) {
                            "sftp> help"
                        } else {
                            sftpConsoleLines.joinToString(separator = "\n")
                        }
                        Text(
                            text = activityLog,
                            color = Color(0xFFE5E5E5),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
                OutlinedTextField(
                    value = sftpCommandInput,
                    onValueChange = { sftpCommandInput = it },
                    label = { Text("Command (e.g. ls, cd /, get file.txt)") },
                    singleLine = true,
                    enabled = !sftpCommandRunning,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (sftpCommandRunning) return@KeyboardActions
                            val cmd = sftpCommandInput
                            sftpCommandInput = ""
                            runSftpCommand(cmd)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CONNECTING_SFTP_COMMAND_INPUT)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (sftpCommandRunning && sftpShowCancel) {
                                finishSftpCommandWait()
                                appendSftpConsole("Cancelled waiting for command completion.")
                                return@Button
                            }
                            if (sftpCommandRunning) return@Button
                            val cmd = sftpCommandInput
                            sftpCommandInput = ""
                            runSftpCommand(cmd)
                        },
                        enabled = !sftpCommandRunning || sftpShowCancel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(UiTestTags.CONNECTING_SFTP_RUN_BUTTON)
                    ) {
                        Text(if (sftpCommandRunning && sftpShowCancel) "Cancel" else "Run")
                    }
                    Button(
                        onClick = { runSftpCommand("help") },
                        enabled = !sftpCommandRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(UiTestTags.CONNECTING_SFTP_HELP_BUTTON)
                    ) { Text("Help") }
                    Button(
                        onClick = { runSftpCommand("refresh") },
                        enabled = !sftpCommandRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(UiTestTags.CONNECTING_SFTP_REFRESH_BUTTON)
                    ) { Text("Refresh") }
                }
            }
        } else if (request != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.02f))

                Box(
                    modifier = Modifier.size(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(340.dp)
                            .blur(72.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x2EFFFFFF),
                                        Color(0x14FFFFFF),
                                        Color(0x08F7F4EF),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .blur(32.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x18FFFFFF),
                                        Color(0x0CFBF7F1),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.sshpeaches_activitybar),
                        contentDescription = "SSHPeaches logo",
                        colorFilter = ColorFilter.tint(Color(0xFFFA992A)),
                        modifier = Modifier.size(128.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.phase == QuickConnectPhase.CONNECTING || state.phase == QuickConnectPhase.IDLE) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = hostName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                    )
                }

                detailLine?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9E9E9E))
                    )
                }
                userFacingStateMessage.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFBDBDBD))
                    )
                }

                Spacer(modifier = Modifier.weight(0.04f))

                ConnectionLogsPane(
                    renderedLogs = renderedLogs,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f)
                )
            }
        }

        if (showSnippetPicker && showTerminalSession) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSnippetPicker = false },
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
                                        .clickable {
                                            runSnippetOnCurrentSession(snippet)
                                            showSnippetPicker = false
                                        },
                                    color = Color(0xFF111111),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = snippet.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        if (snippet.description.isNotBlank()) {
                                            Text(
                                                text = snippet.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFBDBDBD),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = snippet.command,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = Color(0xFFE5E5E5),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSnippetPicker = false }) {
                        Text("Close")
                    }
                }
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
                    style = MaterialTheme.typography.labelLarge.copy(color = Color.White)
                )
            }
        }
    }
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
        if (useWideLayout) {
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
    val scope = rememberCoroutineScope()
    var repeatJob by remember(key) { mutableStateOf<Job?>(null) }
    var pressed by remember(key) { mutableStateOf(false) }
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
            .border(width = 1.dp, color = Color(0xFF474747), shape = keyShape)
            .background(
                when {
                    pressed && key.enabled -> Color(0xFF5B3A0F)
                    modifierActive || aliasActive -> Color(0xFF5B3A0F)
                    key.enabled -> Color(0xFF121212)
                    else -> Color(0xFF090909)
                }
            )
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
                tint = if (key.enabled) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
                modifier = Modifier.size(15.dp)
            )
        } else {
            Text(
                text = key.label,
                color = if (key.enabled) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
                fontSize = KeyboardLayoutDefaults.COMPACT_KEY_FONT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectionLogsPane(
    renderedLogs: List<String>,
    listState: LazyListState,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.testTag(UiTestTags.CONNECTING_LOG_PANEL),
        color = Color(0xFF080808)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(renderedLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFBDBDBD),
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF080808),
                                Color(0x00080808)
                            )
                        )
                    )
            )
        }
    }
}

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
        view.setTypeface(resolveTerminalTypeface(font))
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                TERMINAL_BELL_VIBRATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(TERMINAL_BELL_VIBRATION_MS)
    }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }
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

private fun normalizeImeChunk(chunk: String): String {
    if (chunk.isEmpty()) return chunk
    val normalized = chunk
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u0000", "")
    return normalized.replace('\n', '\r')
}

private fun sendImeDelta(
    previous: String,
    next: String,
    sendBackspace: () -> Unit,
    sendInserted: (String) -> Unit
) {
    if (previous == next) return

    val prefixLength = commonPrefixLength(previous, next)
    val suffixLength = commonSuffixLength(previous, next, prefixLength)
    val previousMiddleEnd = previous.length - suffixLength
    val nextMiddleEnd = next.length - suffixLength

    val removed = previous.substring(prefixLength, previousMiddleEnd)
    if (removed.isNotEmpty()) {
        val deleteCount = removed.codePointCount(0, removed.length)
        repeat(deleteCount) { sendBackspace() }
    }

    val inserted = normalizeImeChunk(next.substring(prefixLength, nextMiddleEnd))
    if (inserted.isNotEmpty()) {
        sendInserted(inserted)
    }
}

private fun commonPrefixLength(a: String, b: String): Int {
    val max = minOf(a.length, b.length)
    var i = 0
    while (i < max && a[i] == b[i]) i++
    return i
}

private fun commonSuffixLength(a: String, b: String, prefixLength: Int): Int {
    val max = minOf(a.length, b.length) - prefixLength
    var i = 0
    while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}

private const val IME_BUFFER_MAX_CHARS = 512
private const val IME_BUFFER_KEEP_TAIL_CHARS = 160
private const val SFTP_CANCEL_BUTTON_DELAY_MS = 220L
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
private const val SCP_TRANSFER_STATUS_AUTO_DISMISS_MS = 3_500L
private const val TERMINAL_BELL_NOTIFICATION_CHANNEL_ID = "terminal_bell"
private const val TERMINAL_BELL_NOTIFICATION_ID_BASE = 24_000
private const val TERMINAL_BELL_THROTTLE_MS = 750L
private const val TERMINAL_BELL_VIBRATION_MS = 120L
