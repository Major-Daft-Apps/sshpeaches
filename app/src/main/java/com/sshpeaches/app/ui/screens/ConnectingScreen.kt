package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalInputRouter
import com.majordaftapps.sshpeaches.app.ui.terminal.TerminalRenderView
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.nio.charset.StandardCharsets
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
    terminalProfile: TerminalProfile,
    keyboardSlots: List<KeyboardSlotAction>,
    onSendShellBytes: (ByteArray) -> Unit,
    onTerminalResize: (Int, Int) -> Unit,
    onSftpListDirectory: (String) -> Unit,
    onSftpDownload: (String, String?) -> Unit,
    onSftpUpload: (String, String) -> Unit,
    onScpDownload: (String, String?) -> Unit,
    onScpUpload: (String, String) -> Unit,
    resolveTerminalEmulator: (String) -> com.termux.terminal.TerminalEmulator? = { null },
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
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
    var terminalViewRef by remember(request?.sessionId) { mutableStateOf<TerminalRenderView?>(null) }
    var keyboardFocused by remember(request?.sessionId) { mutableStateOf(false) }
    var sessionTopBarVisible by rememberSaveable(request?.sessionId) { mutableStateOf(false) }
    var terminalFontSizeSp by rememberSaveable(request?.sessionId) { mutableStateOf(10f) }
    var lastResize by remember(request?.sessionId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var sftpPath by rememberSaveable(request?.sessionId) { mutableStateOf(".") }
    var downloadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var downloadLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var uploadLocalPath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
    var uploadRemotePath by rememberSaveable(request?.sessionId) { mutableStateOf("") }
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
    val showTransferSession =
        state.phase == QuickConnectPhase.SUCCESS && request != null && request.mode != ConnectionMode.SSH

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
        pendingModifiers = emptySet()
        terminalViewRef?.onTerminalUpdated()
    }
    LaunchedEffect(terminalProfile.id) {
        terminalEngine.applyProfile(terminalProfile)
        terminalViewRef?.setTerminalBackgroundColor(terminalEngine.backgroundColor())
        terminalViewRef?.onTerminalUpdated()
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
            terminalViewRef?.onTerminalUpdated()
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
        terminalViewRef?.onTerminalUpdated()
    }
    LaunchedEffect(state.phase, request?.sessionId) {
        if (showTerminalSession) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            sessionTopBarVisible = false
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
            sessionTopBarVisible = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        if (state.phase == QuickConnectPhase.SUCCESS) {
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
                                TerminalRenderView(it).apply {
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                terminalViewRef = view
                                val textSizePx = with(density) { terminalFontSizeSp.sp.toPx().toInt().coerceAtLeast(8) }
                                view.setTerminalBackgroundColor(terminalEngine.backgroundColor())
                                view.setTerminalTextSizePx(textSizePx)
                                view.bind(
                                    emulatorProvider = { externalTerminalEmulator ?: terminalEngine.emulator() },
                                    onSingleTap = {
                                        sessionTopBarVisible = !sessionTopBarVisible
                                    },
                                    onDoubleTap = {
                                        if (keyboardFocused) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            keyboardFocused = false
                                        } else {
                                            keyboardFocusRequester.requestFocus()
                                            keyboardController?.show()
                                            keyboardFocused = true
                                        }
                                    },
                                    onScaleDelta = { scale ->
                                        terminalFontSizeSp = (terminalFontSizeSp * scale).coerceIn(9f, 28f)
                                    },
                                    onResize = { columns, rows, cellWidthPx, cellHeightPx ->
                                        terminalEngine.resize(columns, rows, cellWidthPx, cellHeightPx)
                                        val resize = columns to rows
                                        if (lastResize != resize) {
                                            lastResize = resize
                                            onTerminalResize(columns, rows)
                                        }
                                    }
                                )
                                view.onTerminalUpdated()
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

                if (sessionTopBarVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = Color(0xE0111111)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = request?.name?.ifBlank { hostName } ?: hostName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close session",
                                    tint = Color.White
                                )
                            }
                        }
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
                            if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                                return@onPreviewKeyEvent false
                            }
                            terminalInput.onAndroidKeyDown(keyEvent.nativeKeyEvent)
                        },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrect = false
                    )
                )
            }
        } else if (showTransferSession && request != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${request.mode.name} Transfer",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close session",
                            tint = Color.White
                        )
                    }
                }

                if (request.mode == ConnectionMode.SFTP) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = sftpPath,
                            onValueChange = { sftpPath = it },
                            label = { Text("Remote directory") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSftpListDirectory(sftpPath) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("List")
                        }
                    }
                }

                Text("Download", color = Color.White, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = downloadRemotePath,
                    onValueChange = { downloadRemotePath = it },
                    label = { Text("Remote file path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = downloadLocalPath,
                    onValueChange = { downloadLocalPath = it },
                    label = { Text("Local destination (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (request.mode == ConnectionMode.SFTP) {
                            onSftpDownload(downloadRemotePath, downloadLocalPath.ifBlank { null })
                        } else {
                            onScpDownload(downloadRemotePath, downloadLocalPath.ifBlank { null })
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Download")
                }

                Text("Upload", color = Color.White, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = uploadLocalPath,
                    onValueChange = { uploadLocalPath = it },
                    label = { Text("Local file path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uploadRemotePath,
                    onValueChange = { uploadRemotePath = it },
                    label = { Text("Remote destination path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (request.mode == ConnectionMode.SFTP) {
                            onSftpUpload(uploadLocalPath, uploadRemotePath)
                        } else {
                            onScpUpload(uploadLocalPath, uploadRemotePath)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Upload")
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
                        Text(
                            text = shellOutput.ifBlank { "No directory listing yet. Use the controls above to transfer files." },
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

        if (state.phase != QuickConnectPhase.SUCCESS) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel connection",
                    tint = Color.White
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
        Text(
            text = key.label,
            color = if (key.enabled) Color(0xFFEDEDED) else Color(0xFF7B7B7B),
            fontSize = KeyboardLayoutDefaults.COMPACT_KEY_FONT_SP.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
