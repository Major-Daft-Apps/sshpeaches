package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.service.SessionLogBus

private const val MAX_RENDERED_TERMINAL_CHARS = 24_000

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
    val script: String = ""
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
    keyboardSlots: List<String>,
    onSendShellInput: (String) -> Unit,
    onTerminalResize: (Int, Int) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val shellScrollState = rememberScrollState()
    val terminalSize = remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val keyboardFocusRequester = remember { FocusRequester() }
    val renderedTerminal = remember(shellOutput) { renderTerminalText(shellOutput) }
    var keyboardBridgeValue by remember(request?.sessionId) { mutableStateOf("") }
    var keyboardFocused by remember(request?.sessionId) { mutableStateOf(false) }
    var terminalFontSizeSp by remember(request?.sessionId) { mutableFloatStateOf(12f) }

    val terminalKeys = remember {
        listOf(
            "Esc" to "\u001B",
            "Tab" to "\t",
            "Ent" to "\n",
            "Bk" to "\u007F",
            "Up" to "\u001B[A",
            "Dn" to "\u001B[B",
            "Lt" to "\u001B[D",
            "Rt" to "\u001B[C",
            "C-C" to "\u0003",
            "C-D" to "\u0004",
            "C-Z" to "\u001A"
        )
    }

    val compactKeys = remember(keyboardSlots) {
        val slotKeys = keyboardSlots
            .filter { it.isNotBlank() }
            .map { slot ->
                val compactLabel = slot.trim().replace("\n", " ").take(4)
                (if (compactLabel.isBlank()) "Slot" else compactLabel) to slot
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
    val detailLine = request?.let {
        buildString {
            append(if (it.useMosh) "Mosh requested" else it.mode.name)
            it.forwardId?.let { id -> append(" | Forward: $id") }
            if (it.script.isNotBlank()) append(" | Script configured")
        }
    }

    LaunchedEffect(renderedLogs.size) {
        if (renderedLogs.isNotEmpty()) {
            listState.animateScrollToItem(renderedLogs.size - 1)
        }
    }
    LaunchedEffect(renderedTerminal.length) {
        if (renderedTerminal.isNotEmpty()) {
            shellScrollState.scrollTo(shellScrollState.maxValue)
        }
    }
    LaunchedEffect(terminalSize.value, state.phase, request?.sessionId) {
        if (state.phase != QuickConnectPhase.SUCCESS) return@LaunchedEffect
        val size = terminalSize.value
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val cellWidthPx = with(density) { 8.dp.toPx() }
        val cellHeightPx = with(density) { 16.dp.toPx() }
        val columns = (size.width / cellWidthPx).toInt().coerceAtLeast(20)
        val rows = (size.height / cellHeightPx).toInt().coerceAtLeast(8)
        onTerminalResize(columns, rows)
    }
    LaunchedEffect(state.phase, request?.sessionId) {
        if (state.phase == QuickConnectPhase.SUCCESS) {
            keyboardFocusRequester.requestFocus()
            keyboardFocused = true
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.phase == QuickConnectPhase.SUCCESS) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { terminalSize.value = it }
                        .pointerInput(request?.sessionId) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    terminalFontSizeSp = (terminalFontSizeSp * zoom).coerceIn(9f, 28f)
                                }
                            }
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            keyboardFocusRequester.requestFocus()
                            keyboardFocused = true
                            keyboardController?.show()
                        },
                    color = Color(0xFF080808)
                ) {
                    Text(
                        text = if (renderedTerminal.isBlank()) " " else renderedTerminal,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(shellScrollState),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFEDEDED),
                            fontSize = terminalFontSizeSp.sp
                        )
                    )
                }

                CompactKeyRow(
                    keys = compactKeys,
                    onSendShellInput = onSendShellInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinch to zoom (${terminalFontSizeSp.toInt()}sp)",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFBDBDBD))
                    )
                    OutlinedButton(
                        onClick = {
                            if (keyboardFocused) {
                                focusManager.clearFocus(force = true)
                                keyboardFocused = false
                                keyboardController?.hide()
                            } else {
                                keyboardFocusRequester.requestFocus()
                                keyboardFocused = true
                                keyboardController?.show()
                            }
                        },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (keyboardFocused) "Hide KB" else "Keyboard",
                            maxLines = 1,
                            fontSize = 10.sp
                        )
                    }
                }

                // Invisible text bridge used only to capture system keyboard input for the shell.
                BasicTextField(
                    value = keyboardBridgeValue,
                    onValueChange = { next ->
                        val previous = keyboardBridgeValue
                        when {
                            next.startsWith(previous) -> {
                                val appended = next.removePrefix(previous)
                                if (appended.isNotEmpty()) onSendShellInput(appended)
                            }

                            previous.startsWith(next) -> {
                                val removed = previous.length - next.length
                                repeat(removed) { onSendShellInput("\u007F") }
                            }

                            else -> {
                                if (next.isNotEmpty()) onSendShellInput(next)
                            }
                        }
                        keyboardBridgeValue = next.takeLast(64)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .alpha(0f)
                        .focusRequester(keyboardFocusRequester)
                        .onFocusChanged { keyboardFocused = it.isFocused },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSendShellInput("\n")
                            keyboardBridgeValue = ""
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
    keys: List<Pair<String, String>>,
    onSendShellInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keys.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { (label, payload) ->
            OutlinedButton(
                onClick = { onSendShellInput(payload) },
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEDEDED))
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
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

private fun renderTerminalText(raw: String): String {
    if (raw.isEmpty()) return ""
    val lines = mutableListOf(StringBuilder())
    var current = lines.last()
    var escapeMode = false
    var csiMode = false

    fun newLine() {
        lines.add(StringBuilder())
        if (lines.size > 300) {
            lines.removeAt(0)
        }
        current = lines.last()
    }

    val normalized = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    normalized.forEach { ch ->
        if (escapeMode) {
            if (!csiMode && ch == '[') {
                csiMode = true
                return@forEach
            }
            if (!csiMode) {
                escapeMode = false
                return@forEach
            }
            if (ch in '@'..'~') {
                escapeMode = false
                csiMode = false
            }
            return@forEach
        }
        when (ch) {
            '\u001B' -> escapeMode = true
            '\n' -> newLine()
            '\b' -> if (current.isNotEmpty()) current.setLength(current.length - 1)
            else -> if (ch >= ' ' || ch == '\t') current.append(ch)
        }
    }
    return lines.joinToString("\n") { it.toString() }.takeLast(MAX_RENDERED_TERMINAL_CHARS)
}
