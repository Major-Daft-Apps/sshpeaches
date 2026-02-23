package com.sshpeaches.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.service.SessionLogBus

private const val MAX_RENDERED_TERMINAL_CHARS = 24_000

data class QuickConnectRequest(
    val sessionId: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: AuthMethod,
    val password: String,
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
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val shellScrollState = rememberScrollState()
    val commandInput = remember(request?.sessionId) { mutableStateOf("") }
    val renderedTerminal = remember(shellOutput) { renderTerminalText(shellOutput) }
    val terminalKeys = remember {
        listOf(
            "Esc" to "\u001B",
            "Tab" to "\t",
            "Enter" to "\n",
            "Bksp" to "\u007F",
            "↑" to "\u001B[A",
            "↓" to "\u001B[B",
            "←" to "\u001B[D",
            "→" to "\u001B[C",
            "Ctrl+C" to "\u0003",
            "Ctrl+D" to "\u0004",
            "Ctrl+Z" to "\u001A"
        )
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
            append(if (it.useMosh) "Mosh requested" else "SSH")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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

            Spacer(modifier = Modifier.weight(if (state.phase == QuickConnectPhase.SUCCESS) 0.12f else 0.3f))

            if (state.phase == QuickConnectPhase.SUCCESS) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                    color = Color(0xFF080808)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Remote Shell",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFFFD1A3))
                        )
                        Text(
                            text = if (renderedTerminal.isBlank()) "Shell ready." else renderedTerminal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(shellScrollState),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFEDEDED),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInput.value,
                        onValueChange = { next ->
                            val prev = commandInput.value
                            when {
                                next.startsWith(prev) -> {
                                    val appended = next.removePrefix(prev)
                                    if (appended.isNotEmpty()) {
                                        onSendShellInput(appended)
                                    }
                                }
                                prev.startsWith(next) -> {
                                    val removed = prev.length - next.length
                                    repeat(removed) { onSendShellInput("\u007F") }
                                }
                                else -> {
                                    // Best effort for cursor edits: resync by replacing line locally.
                                    onSendShellInput("\u0015")
                                    if (next.isNotEmpty()) onSendShellInput(next)
                                }
                            }
                            commandInput.value = next
                        },
                        label = { Text("Live Input") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onSendShellInput("\n")
                            commandInput.value = ""
                        }),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        onSendShellInput("\n")
                        commandInput.value = ""
                    }) {
                        Text("Enter")
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    terminalKeys.forEach { (label, payload) ->
                        OutlinedButton(onClick = { onSendShellInput(payload) }) {
                            Text(label)
                        }
                    }
                    keyboardSlots
                        .filter { it.isNotBlank() }
                        .forEach { slot ->
                            OutlinedButton(onClick = { onSendShellInput(slot) }) {
                                Text(slot)
                            }
                        }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.phase == QuickConnectPhase.SUCCESS) 0.25f else 0.5f),
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

    raw.forEach { ch ->
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
                when (ch) {
                    'K' -> current.setLength(0)
                    'J' -> {
                        lines.clear()
                        lines.add(StringBuilder())
                        current = lines.last()
                    }
                }
                escapeMode = false
                csiMode = false
            }
            return@forEach
        }
        when (ch) {
            '\u001B' -> escapeMode = true
            '\r' -> current.setLength(0)
            '\n' -> newLine()
            '\b' -> if (current.isNotEmpty()) current.setLength(current.length - 1)
            else -> if (ch >= ' ' || ch == '\t') current.append(ch)
        }
    }
    return lines.joinToString("\n") { it.toString() }.takeLast(MAX_RENDERED_TERMINAL_CHARS)
}
