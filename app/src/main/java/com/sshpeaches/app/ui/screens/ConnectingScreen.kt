package com.sshpeaches.app.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.TerminalProfile
import com.sshpeaches.app.service.SessionLogBus
import com.sshpeaches.app.ui.terminal.TerminalInputRouter
import com.sshpeaches.app.ui.terminal.TerminalRenderView
import com.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.nio.charset.StandardCharsets

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
    keyboardSlots: List<String>,
    onSendShellBytes: (ByteArray) -> Unit,
    onTerminalResize: (Int, Int) -> Unit,
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
    var imeCommittedText by remember(request?.sessionId) { mutableStateOf("") }
    var terminalViewRef by remember(request?.sessionId) { mutableStateOf<TerminalRenderView?>(null) }
    var keyboardFocused by remember(request?.sessionId) { mutableStateOf(false) }
    var terminalFontSizeSp by rememberSaveable(request?.sessionId) { mutableStateOf(10f) }
    var lastResize by remember(request?.sessionId) { mutableStateOf<Pair<Int, Int>?>(null) }

    val terminalKeys = remember {
        listOf(
            CompactTerminalKey("Esc", "\u001B", keyCode = KeyEvent.KEYCODE_ESCAPE),
            CompactTerminalKey("Tab", "\t", keyCode = KeyEvent.KEYCODE_TAB),
            CompactTerminalKey("Ent", "\r", keyCode = KeyEvent.KEYCODE_ENTER),
            CompactTerminalKey("Bk", "\u007F", keyCode = KeyEvent.KEYCODE_DEL),
            CompactTerminalKey("Up", "\u001B[A", keyCode = KeyEvent.KEYCODE_DPAD_UP),
            CompactTerminalKey("Dn", "\u001B[B", keyCode = KeyEvent.KEYCODE_DPAD_DOWN),
            CompactTerminalKey("Lt", "\u001B[D", keyCode = KeyEvent.KEYCODE_DPAD_LEFT),
            CompactTerminalKey("Rt", "\u001B[C", keyCode = KeyEvent.KEYCODE_DPAD_RIGHT),
            CompactTerminalKey("C-C", "\u0003", keyCode = KeyEvent.KEYCODE_C, ctrl = true),
            CompactTerminalKey("C-D", "\u0004", keyCode = KeyEvent.KEYCODE_D, ctrl = true),
            CompactTerminalKey("C-Z", "\u001A", keyCode = KeyEvent.KEYCODE_Z, ctrl = true)
        )
    }

    val compactKeys = remember(keyboardSlots) {
        val slotKeys = keyboardSlots
            .filter { it.isNotBlank() }
            .map { slot ->
                val compactLabel = slot.trim().replace("\n", " ").take(4)
                CompactTerminalKey(
                    label = if (compactLabel.isBlank()) "Slot" else compactLabel,
                    payload = slot
                )
            }
        terminalKeys + slotKeys
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
        imeCommittedText = ""
        terminalFontSizeSp = terminalProfile.fontSizeSp.toFloat()
        lastResize = null
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
        if (state.phase == QuickConnectPhase.SUCCESS) {
            keyboardFocusRequester.requestFocus()
            keyboardController?.show()
            keyboardFocused = true
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            keyboardFocused = false
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
                        onSendKey = { key ->
                            val sent = key.keyCode?.let { keyCode ->
                                terminalInput.sendVirtualKey(
                                    keyCode = keyCode,
                                    ctrlDown = key.ctrl,
                                    altDown = key.alt,
                                    shiftDown = key.shift
                                )
                            } ?: false
                            if (!sent) {
                                terminalInput.sendText(key.payload)
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

                        // Keep composition local until IME commits it, then send final delta once.
                        if (next.composition != null) return@BasicTextField

                        sendImeDelta(
                            previous = imeCommittedText,
                            next = next.text,
                            send = terminalInput::sendText
                        )
                        imeCommittedText = next.text

                        if (imeCommittedText.length > IME_BUFFER_MAX_CHARS) {
                            val tail = imeCommittedText.takeLast(IME_BUFFER_KEEP_TAIL_CHARS)
                            imeCommittedText = tail
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            terminalInput.sendText("\r")
                            imeBridgeValue = TextFieldValue("")
                            imeCommittedText = ""
                        }
                    )
                )
            }
        } else {
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
    onSendKey: (CompactTerminalKey) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keys.isEmpty()) return
    val keyShape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
                    .clip(keyShape)
                    .border(width = 1.dp, color = Color(0xFF474747), shape = keyShape)
                    .background(Color(0xFF121212))
                    .clickable { onSendKey(key) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = key.label,
                    color = Color(0xFFEDEDED),
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    val payload: String,
    val keyCode: Int? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
)

private fun normalizeImeChunk(chunk: String): String {
    if (chunk.isEmpty()) return chunk
    val normalized = chunk
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u0000", "")
    return normalized.replace('\n', '\r')
}

private fun sendImeDelta(previous: String, next: String, send: (String) -> Unit) {
    if (previous == next) return

    val prefixLength = commonPrefixLength(previous, next)
    val suffixLength = commonSuffixLength(previous, next, prefixLength)
    val previousMiddleEnd = previous.length - suffixLength
    val nextMiddleEnd = next.length - suffixLength

    val removed = previous.substring(prefixLength, previousMiddleEnd)
    if (removed.isNotEmpty()) {
        val deleteCount = removed.codePointCount(0, removed.length)
        repeat(deleteCount) { send("\u007F") }
    }

    val inserted = normalizeImeChunk(next.substring(prefixLength, nextMiddleEnd))
    if (inserted.isNotEmpty()) {
        send(inserted)
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
