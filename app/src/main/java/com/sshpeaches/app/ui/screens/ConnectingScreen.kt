package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardIconPack
import com.majordaftapps.sshpeaches.app.ui.state.TerminalSelectionMode
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalInputRouter
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.termux.terminal.TerminalSession
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
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
    val terminalProfileId: String? = null
)

enum class QuickConnectPhase {
    IDLE,
    CONNECTING,
    SUCCESS,
    ERROR
}

data class QuickConnectUiState(
    val phase: QuickConnectPhase = QuickConnectPhase.IDLE,
    val message: String = ""
)

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
    keyboardSlots: List<KeyboardSlotAction>,
    onSendShellBytes: (ByteArray) -> Unit,
    onTerminalResize: (Int, Int) -> Unit,
    onSftpListDirectory: (String) -> Unit,
    onSftpDownload: (String, String?) -> Unit,
    onSftpUpload: (String, String) -> Unit,
    onScpDownload: (String, String?) -> Unit,
    onScpUpload: (String, String) -> Unit,
    onManageRemotePath: (operation: String, sourcePath: String, destinationPath: String?) -> Unit,
    resolveTerminalEmulator: (String) -> com.termux.terminal.TerminalEmulator? = { null },
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
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
    var uploadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    val sftpConsoleLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    val scpActivityLines = remember(request?.sessionId) { mutableStateListOf<String>() }
    var scpLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var scpRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var scpSelectedLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpSelectedRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf<String?>(null) }
    var scpLocalNewFolder by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var scpRemoteNewFolder by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var scpMoveLocalDestination by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var scpMoveRemoteDestination by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var pendingModifiers by remember(request?.sessionId) { mutableStateOf(setOf<KeyboardModifier>()) }

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
            append(if (it.mode == ConnectionMode.SSH) "Terminal session" else it.mode.name)
            it.forwardId?.let { id -> append(" | Forward: $id") }
            if (it.script.isNotBlank()) append(" | Script configured")
        }
    }
    val showTerminalSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SSH
    val showSftpConsoleSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SFTP
    val showScpDualPaneSession =
        state.phase == QuickConnectPhase.SUCCESS && request?.mode == ConnectionMode.SCP
    var lastSingleTapUptimeMillis by remember(request?.sessionId) { mutableStateOf(0L) }
    val terminalViewClient = remember(request?.sessionId) {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                terminalFontSizeSp = (terminalFontSizeSp * scale).coerceIn(9f, 28f)
                terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(8) })
                return 1f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                val now = System.currentTimeMillis()
                if (now - lastSingleTapUptimeMillis < 300L) {
                    if (keyboardFocused) {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        keyboardFocused = false
                    } else {
                        keyboardFocusRequester.requestFocus()
                        keyboardController?.show()
                        keyboardFocused = true
                    }
                } else {
                    if (!keyboardFocused) {
                        keyboardFocusRequester.requestFocus()
                        keyboardController?.show()
                        keyboardFocused = true
                    }
                }
                lastSingleTapUptimeMillis = now
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false

            override fun shouldEnforceCharBasedInput(): Boolean = true

            override fun isTerminalViewSelected(): Boolean = keyboardFocused

            override fun copyModeChanged(copyMode: Boolean) = Unit

            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
                return terminalInput.onAndroidKeyDown(e)
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

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
        uploadRemotePath = ""
        sftpConsoleLines.clear()
        scpActivityLines.clear()
        scpLocalPath = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        scpRemotePath = "."
        scpSelectedLocalPath = null
        scpSelectedRemotePath = null
        scpLocalNewFolder = ""
        scpRemoteNewFolder = ""
        scpMoveLocalDestination = ""
        scpMoveRemoteDestination = ""
        if (request?.mode == ConnectionMode.SFTP) {
            sftpConsoleLines += "Connected to ${request.host}:${request.port}"
            onSftpListDirectory(sftpPath)
        }
        if (request?.mode == ConnectionMode.SCP) {
            onSftpListDirectory(scpRemotePath)
        }
        pendingModifiers = emptySet()
        terminalViewRef?.onScreenUpdated()
    }
    LaunchedEffect(terminalProfile.id) {
        terminalEngine.applyProfile(terminalProfile)
        terminalViewRef?.setTextSize(with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(8) })
        terminalViewRef?.onScreenUpdated()
    }
    DisposableEffect(request?.sessionId) {
        onDispose {
            terminalViewRef = null
        }
    }
    DisposableEffect(lifecycleOwner, request?.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                keyboardFocused = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
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
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
        }
    }
    LaunchedEffect(remoteDirectory?.path, request?.sessionId, request?.mode) {
        val path = remoteDirectory?.path ?: return@LaunchedEffect
        when (request?.mode) {
            ConnectionMode.SFTP -> sftpPath = path
            ConnectionMode.SCP -> scpRemotePath = path
            ConnectionMode.SSH, null -> Unit
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        if (showTerminalSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                terminalViewRef = view
                                view.setTerminalViewClient(terminalViewClient)
                                view.setSelectionJoinBackLines(
                                    terminalSelectionMode == TerminalSelectionMode.NATURAL
                                )
                                val emulator = externalTerminalEmulator ?: terminalEngine.emulator()
                                view.attachEmulator(emulator)
                                val textSizePx = with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(8) }
                                view.setTextSize(textSizePx)
                                view.updateSize()
                                val resize = emulator.mColumns to emulator.mRows
                                if (lastResize != resize) {
                                    lastResize = resize
                                    onTerminalResize(emulator.mColumns, emulator.mRows)
                                }
                                view.onScreenUpdated()
                            }
                        )
                    }

                    CompactKeyRow(
                        keys = compactKeys,
                        activeModifiers = pendingModifiers,
                        onSendKey = { key ->
                            if (!key.enabled) return@CompactKeyRow
                            val action = key.action
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
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 2.dp, vertical = 0.dp)
                    )
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
                            if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                                return@onPreviewKeyEvent false
                            }
                            terminalInput.onAndroidKeyDown(keyEvent.nativeKeyEvent)
                        },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    )
                )
            }
        } else if (showSftpConsoleSession && request != null) {
            val remoteItems = remoteDirectory?.entries.orEmpty()
            val effectiveSftpPath = remoteDirectory?.path ?: sftpPath
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SFTP Browser",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = sftpPath,
                    onValueChange = { sftpPath = it },
                    label = { Text("Remote working directory") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val target = sftpPath.trim().ifBlank { "." }
                            onSftpListDirectory(target)
                            sftpConsoleLines += "Listing $target..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open")
                    }
                    Button(
                        onClick = {
                            val parent = parentPath(effectiveSftpPath)
                            sftpPath = parent
                            onSftpListDirectory(parent)
                            sftpConsoleLines += "Listing $parent..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Up")
                    }
                    Button(
                        onClick = {
                            onSftpListDirectory(effectiveSftpPath)
                            sftpConsoleLines += "Refreshing $effectiveSftpPath..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }
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
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Path: $effectiveSftpPath",
                            color = Color(0xFFE5E5E5),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (remoteItems.isEmpty()) {
                            Text(
                                text = "No entries returned for this path.",
                                color = Color(0xFFBDBDBD),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(remoteItems, key = { "${it.name}-${it.isDirectory}" }) { item ->
                                    val absolute = resolveChildPath(effectiveSftpPath, item.name)
                                    val selected = downloadRemotePath == absolute
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (selected) Color(0xFF2A2A2A) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                if (item.isDirectory) {
                                                    sftpPath = absolute
                                                    onSftpListDirectory(absolute)
                                                    sftpConsoleLines += "Listing $absolute..."
                                                } else {
                                                    downloadRemotePath = absolute
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (item.isDirectory) "[DIR] ${item.name}" else item.name,
                                            color = Color(0xFFE5E5E5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = downloadRemotePath,
                    onValueChange = { downloadRemotePath = it },
                    label = { Text("Download remote path") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = downloadLocalPath,
                    onValueChange = { downloadLocalPath = it },
                    label = { Text("Download local destination (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val remote = downloadRemotePath.trim()
                            if (remote.isBlank()) {
                                sftpConsoleLines += "Select or enter a remote file path to download."
                                return@Button
                            }
                            onSftpDownload(remote, downloadLocalPath.trim().ifBlank { null })
                            sftpConsoleLines += "Downloading $remote..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download")
                    }
                    Button(
                        onClick = {
                            downloadRemotePath = ""
                            downloadLocalPath = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }

                OutlinedTextField(
                    value = uploadLocalPath,
                    onValueChange = { uploadLocalPath = it },
                    label = { Text("Upload local path") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uploadRemotePath,
                    onValueChange = { uploadRemotePath = it },
                    label = { Text("Upload remote destination") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val local = uploadLocalPath.trim()
                            if (local.isBlank()) {
                                sftpConsoleLines += "Enter a local file path to upload."
                                return@Button
                            }
                            val remote = uploadRemotePath.trim().ifBlank {
                                inferRemoteDestination(local, effectiveSftpPath)
                            }
                            onSftpUpload(local, remote)
                            sftpConsoleLines += "Uploading $local -> $remote..."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Upload")
                    }
                    Button(
                        onClick = {
                            uploadRemotePath = effectiveSftpPath
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use Dir")
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = Color(0xFF0F0F0F)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        val activityLog = if (sftpConsoleLines.isEmpty()) {
                            "No activity yet."
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
            }
        } else if (showScpDualPaneSession && request != null) {
            val remoteItems = remoteDirectory?.entries.orEmpty()
            val effectiveRemotePath = remoteDirectory?.path ?: scpRemotePath
            val localItems = remember(scpLocalPath) { listLocalFiles(scpLocalPath) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF121212)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Local", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = scpLocalPath,
                                onValueChange = { scpLocalPath = it },
                                label = { Text("Local path") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { scpLocalPath = parentPath(scpLocalPath) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Up") }
                                Button(
                                    onClick = { scpSelectedLocalPath = null },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Clear") }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(localItems, key = { it.absolutePath }) { item ->
                                    val selected = scpSelectedLocalPath == item.absolutePath
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (selected) Color(0xFF2A2A2A) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                scpSelectedLocalPath = item.absolutePath
                                                if (item.isDirectory) {
                                                    scpLocalPath = item.absolutePath
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (item.isDirectory) "[DIR] ${item.name}" else item.name,
                                            color = Color(0xFFE5E5E5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = scpLocalNewFolder,
                                onValueChange = { scpLocalNewFolder = it },
                                label = { Text("New folder name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val parent = File(scpLocalPath)
                                        val folderName = scpLocalNewFolder.trim()
                                        if (folderName.isNotBlank()) {
                                            runCatching { File(parent, folderName).mkdirs() }
                                            scpLocalNewFolder = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("New") }
                                Button(
                                    onClick = {
                                        scpSelectedLocalPath?.let { selected ->
                                            runCatching {
                                                val target = File(selected)
                                                if (target.isDirectory) target.deleteRecursively() else target.delete()
                                            }
                                            scpSelectedLocalPath = null
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Delete") }
                            }
                            OutlinedTextField(
                                value = scpMoveLocalDestination,
                                onValueChange = { scpMoveLocalDestination = it },
                                label = { Text("Move selected to path") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val selected = scpSelectedLocalPath ?: return@Button
                                    val destination = scpMoveLocalDestination.trim()
                                    if (destination.isNotBlank()) {
                                        runCatching {
                                            File(selected).renameTo(File(destination))
                                        }
                                        scpMoveLocalDestination = ""
                                        scpSelectedLocalPath = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Move")
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF121212)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Remote", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = scpRemotePath,
                                onValueChange = { scpRemotePath = it },
                                label = { Text("Remote path") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scpRemotePath = parentPath(effectiveRemotePath)
                                        onSftpListDirectory(scpRemotePath)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Up") }
                                Button(
                                    onClick = { onSftpListDirectory(scpRemotePath) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Refresh") }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(remoteItems, key = { "${effectiveRemotePath}/${it.name}" }) { item ->
                                    val fullPath = resolveChildPath(effectiveRemotePath, item.name)
                                    val selected = scpSelectedRemotePath == fullPath
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (selected) Color(0xFF2A2A2A) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                scpSelectedRemotePath = fullPath
                                                if (item.isDirectory) {
                                                    scpRemotePath = fullPath
                                                    onSftpListDirectory(fullPath)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (item.isDirectory) "[DIR] ${item.name}" else item.name,
                                            color = Color(0xFFE5E5E5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = scpRemoteNewFolder,
                                onValueChange = { scpRemoteNewFolder = it },
                                label = { Text("New remote folder") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val name = scpRemoteNewFolder.trim()
                                        if (name.isNotBlank()) {
                                            onManageRemotePath("mkdir", resolveChildPath(effectiveRemotePath, name), null)
                                            onSftpListDirectory(effectiveRemotePath)
                                            scpRemoteNewFolder = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("New") }
                                Button(
                                    onClick = {
                                        scpSelectedRemotePath?.let { selected ->
                                            onManageRemotePath("delete", selected, null)
                                            onSftpListDirectory(effectiveRemotePath)
                                            scpSelectedRemotePath = null
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Delete") }
                            }
                            OutlinedTextField(
                                value = scpMoveRemoteDestination,
                                onValueChange = { scpMoveRemoteDestination = it },
                                label = { Text("Move selected to path") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val selected = scpSelectedRemotePath ?: return@Button
                                    val destination = scpMoveRemoteDestination.trim()
                                    if (destination.isNotBlank()) {
                                        onManageRemotePath("move", selected, destination)
                                        onSftpListDirectory(parentPath(destination))
                                        scpMoveRemoteDestination = ""
                                        scpSelectedRemotePath = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Move")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val local = scpSelectedLocalPath ?: return@Button
                            val source = File(local)
                            if (!source.isFile) return@Button
                            val remoteTarget = resolveChildPath(effectiveRemotePath, source.name)
                            onScpUpload(source.absolutePath, remoteTarget)
                            scpActivityLines += "Upload requested: ${source.absolutePath} -> $remoteTarget"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Local -> Remote")
                    }
                    Button(
                        onClick = {
                            val remote = scpSelectedRemotePath ?: return@Button
                            val localDir = File(scpLocalPath)
                            val localTarget = File(localDir, remote.substringAfterLast('/')).absolutePath
                            onScpDownload(remote, localTarget)
                            scpActivityLines += "Download requested: $remote -> $localTarget"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Remote -> Local")
                    }
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
                            .padding(12.dp)
                    ) {
                        val activityLog = when {
                            scpActivityLines.isNotEmpty() -> scpActivityLines.joinToString(separator = "\n")
                            else -> "No SCP activity yet. Use the panes above to start a transfer."
                        }
                        Text(
                            text = activityLog,
                            color = Color(0xFFE5E5E5),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        } else if (request != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.2f))

                Image(
                    painter = painterResource(id = R.drawable.sshpeaches),
                    contentDescription = "SSHPeaches logo",
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                state.message.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFBDBDBD))
                    )
                }

                Spacer(modifier = Modifier.weight(0.3f))

                ConnectionLogsPane(
                    renderedLogs = renderedLogs,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                )
            }
        }

        if (state.phase == QuickConnectPhase.ERROR) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
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
    onSendKey: (CompactTerminalKey) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keys.isEmpty()) return
    val keyShape = RoundedCornerShape(5.dp)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys
            .chunked(KeyboardLayoutDefaults.SLOT_COLUMNS)
            .take(KeyboardLayoutDefaults.SLOT_ROWS)
            .forEach { row ->
                Row(
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
                        CompactKeyButton(
                            key = key,
                            keyShape = keyShape,
                            modifierActive = modifierActive,
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
    onSendKey: (CompactTerminalKey) -> Unit
) {
    val scope = rememberCoroutineScope()
    var repeatJob by remember(key) { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { repeatJob?.cancel() }
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(KeyboardLayoutDefaults.COMPACT_KEY_HEIGHT_DP.dp)
            .clip(keyShape)
            .border(width = 1.dp, color = Color(0xFF474747), shape = keyShape)
            .background(
                when {
                    modifierActive -> Color(0xFF5B3A0F)
                    key.enabled -> Color(0xFF121212)
                    else -> Color(0xFF090909)
                }
            )
            .pointerInteropFilter { event: MotionEvent ->
                if (!key.enabled) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
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
        modifier = modifier,
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
private const val KEY_REPEAT_INITIAL_DELAY_MS = 350L
private const val KEY_REPEAT_INTERVAL_MS = 65L
