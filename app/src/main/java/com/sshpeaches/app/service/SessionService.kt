package com.sshpeaches.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sshpeaches.app.R
import com.sshpeaches.app.data.model.AuthMethod
import com.sshpeaches.app.data.model.ConnectionMode
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.settings.SettingsStore
import com.sshpeaches.app.data.ssh.SshClientProvider
import com.sshpeaches.app.data.ssh.SshClientProvider.HostKeyPrompt as SshHostKeyPrompt
import com.sshpeaches.app.security.SecurityManager
import com.sshpeaches.app.ui.logging.UiDebugLog
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException

/**
 * Foreground service that keeps SSH/Mosh sessions alive.
 * Sessions remain connected until explicitly stopped.
 */
class SessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val binder = SessionBinder()
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeConnections = mutableMapOf<String, ActiveConnection>()
    private val sessionSnapshots = MutableStateFlow<List<SessionSnapshot>>(emptyList())
    private val hostKeyPrompts = MutableStateFlow<List<HostKeyPrompt>>(emptyList())
    private val hostKeyPromptWaiters = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val passwordPrompts = MutableStateFlow<List<PasswordPrompt>>(emptyList())
    private val passwordPromptWaiters = ConcurrentHashMap<String, CompletableFuture<PasswordResponse>>()
    private val shellOutput = MutableStateFlow<Map<String, String>>(emptyMap())
    private val shellSizes = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val shellReadSequence = ConcurrentHashMap<String, Int>()
    private val shellWriteSequence = ConcurrentHashMap<String, Int>()
    @Volatile
    private var diagnosticsEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        UiDebugLog.action("SessionService.onCreate")
        serviceScope.launch {
            SettingsStore.diagnosticsEnabled.collect { enabled ->
                diagnosticsEnabled = enabled
            }
        }
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("SSHPeaches Sessions", "No active sessions")
        )
        UiDebugLog.result("SessionService.onCreate", true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UiDebugLog.action("SessionService.onStartCommand", "action=${intent?.action}, startId=$startId")
        when (intent?.action) {
            ACTION_STOP -> intent.getStringExtra(EXTRA_HOST_ID)?.let { stopSession(it) }
        }
        UiDebugLog.result("SessionService.onStartCommand", true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        UiDebugLog.action("SessionService.onDestroy", "activeSessions=${activeJobs.size}")
        super.onDestroy()
        stopAllSessions()
        clearAllHostKeyPrompts()
        clearAllPasswordPrompts()
        shellOutput.value = emptyMap()
        shellSizes.clear()
        shellReadSequence.clear()
        shellWriteSequence.clear()
        UiDebugLog.result("SessionService.onDestroy", true)
    }

    fun startSession(
        host: HostConnection,
        mode: ConnectionMode,
        passwordOverride: String? = null,
        availableForwards: List<PortForward> = emptyList(),
        autoStartForwards: Boolean = true,
        autoTrustUnknownHostKey: Boolean = true,
        allowPasswordSave: Boolean = false
    ) {
        UiDebugLog.action(
            "startSession",
            "hostId=${host.id}, mode=$mode, alreadyActive=${activeJobs.containsKey(host.id)}, hasPasswordOverride=${!passwordOverride.isNullOrBlank()}"
        )
        if (activeJobs.containsKey(host.id)) {
            UiDebugLog.result("startSession", false, "already-active hostId=${host.id}")
            return
        }
        val needsPassword = host.preferredAuth != AuthMethod.IDENTITY
        val initialPassword = if (needsPassword) {
            when {
                !passwordOverride.isNullOrBlank() -> passwordOverride
                else -> runCatching { SecurityManager.getHostPassword(host.id) }.getOrNull()
            }
        } else {
            null
        }
        val job = serviceScope.launch {
            var client: SSHClient? = null
            var shellBinding: ShellBinding? = null
            val attemptDeadlineMillis = System.currentTimeMillis() + CONNECTION_ATTEMPT_TIMEOUT_MS
            runCatching {
                updateSessionSnapshot(host, mode, SessionStatus.CONNECTING, "Opening SSH connection...")
                client = SshClientProvider.createClient(
                    this@SessionService,
                    host,
                    SessionLoggerFactory(host.id),
                    autoTrustUnknownHostKey = autoTrustUnknownHostKey,
                    onHostKeyPrompt = { prompt ->
                        awaitHostKeyDecision(host.id, prompt, attemptDeadlineMillis)
                    }
                )
                throwIfAttemptTimedOut(attemptDeadlineMillis)
                client!!.connect(host.host, host.port)
                throwIfAttemptTimedOut(attemptDeadlineMillis)
                when (host.preferredAuth) {
                    AuthMethod.PASSWORD -> {
                        authenticateWithPassword(
                            client = client!!,
                            host = host,
                            mode = mode,
                            initialPassword = initialPassword,
                            deadlineMillis = attemptDeadlineMillis,
                            allowPasswordSave = allowPasswordSave
                        )
                    }
                    AuthMethod.IDENTITY -> {
                        updateSessionSnapshot(host, mode, SessionStatus.CONNECTING, "Authenticating with identity...")
                        client!!.authPublickey(host.username)
                    }
                    AuthMethod.PASSWORD_AND_IDENTITY -> {
                        runCatching { client!!.authPublickey(host.username) }
                        authenticateWithPassword(
                            client = client!!,
                            host = host,
                            mode = mode,
                            initialPassword = initialPassword,
                            deadlineMillis = attemptDeadlineMillis,
                            allowPasswordSave = allowPasswordSave
                        )
                    }
                }
                if (mode == ConnectionMode.SSH) {
                    updateSessionSnapshot(host, mode, SessionStatus.CONNECTING, "Starting shell...")
                    shellBinding = openShell(host.id, client!!)
                }
                if (host.startupScript.isNotBlank() && mode == ConnectionMode.SSH) {
                    runCatching {
                        client!!.startSession().use { shell ->
                            val cmd = shell.exec(host.startupScript)
                            cmd.join(12, TimeUnit.SECONDS)
                            val output = runCatching { cmd.inputStream.bufferedReader().readText() }.getOrNull()
                            if (!output.isNullOrBlank()) {
                                SessionLogBus.emit(
                                    SessionLogBus.Entry(
                                        hostId = host.id,
                                        level = SessionLogBus.LogLevel.INFO,
                                        message = output.trim()
                                    )
                                )
                            }
                        }
                    }.onFailure { err ->
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = host.id,
                                level = SessionLogBus.LogLevel.WARN,
                                message = "Startup script failed: ${err.message ?: "unknown error"}"
                            )
                        )
                    }
                }
                val configuredForwards = if (autoStartForwards) {
                    availableForwards.filter { it.enabled && it.associatedHosts.contains(host.id) }
                } else {
                    emptyList()
                }
                if (configuredForwards.isNotEmpty()) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = host.id,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Prepared ${configuredForwards.size} associated forward(s)"
                        )
                    )
                }
                activeConnections[host.id] = ActiveConnection(
                    host = host,
                    mode = mode,
                    client = client!!,
                    shellBinding = shellBinding
                )
                val modeLabel = when (mode) {
                    ConnectionMode.SSH -> "Interactive shell session ready"
                    ConnectionMode.SFTP -> "SFTP control session ready"
                    ConnectionMode.SCP -> "SCP transfer session ready"
                }
                updateSessionSnapshot(host, mode, SessionStatus.ACTIVE, modeLabel)
                UiDebugLog.result("startSession", true, "hostId=${host.id}, mode=$mode")

                // Keep the connection alive until user stops it.
                while (currentCoroutineContext().isActive) {
                    delay(10_000)
                }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    clearHostKeyPromptsForHost(host.id, trust = false)
                    clearPasswordPromptsForHost(host.id, password = null)
                    val statusMessage = e.message ?: "Connection failed"
                    updateSessionSnapshot(host, mode, SessionStatus.ERROR, statusMessage)
                    UiDebugLog.error("startSession", e, "hostId=${host.id}, mode=$mode")
                    UiDebugLog.result("startSession", false, "hostId=${host.id}, mode=$mode")
                }
            }
            runCatching { shellBinding?.shell?.close() }
            runCatching { shellBinding?.session?.close() }
            runCatching { client?.disconnect() }
            activeConnections.remove(host.id)
            clearHostKeyPromptsForHost(host.id, trust = false)
            clearPasswordPromptsForHost(host.id, password = null)
            clearShellOutputForHost(host.id)
        }
        job.invokeOnCompletion {
            activeJobs.remove(host.id)
            activeConnections.remove(host.id)
            cancelHostNotification(host.id)
            updateSummaryNotification()
            if (it is CancellationException) {
                removeSessionSnapshot(host.id)
            }
        }
        activeJobs[host.id] = job
        showHostNotification(host)
        updateSummaryNotification()
    }

    fun stopSession(hostId: String) {
        UiDebugLog.action("stopSession", "hostId=$hostId")
        clearHostKeyPromptsForHost(hostId, trust = false)
        clearPasswordPromptsForHost(hostId, password = null)
        activeConnections.remove(hostId)?.let { connection ->
            runCatching { connection.shellBinding?.shell?.close() }
            runCatching { connection.shellBinding?.session?.close() }
            runCatching { connection.client.disconnect() }
        }
        activeJobs.remove(hostId)?.cancel()
        cancelHostNotification(hostId)
        updateSummaryNotification()
        removeSessionSnapshot(hostId)
        clearShellOutputForHost(hostId)
        shellReadSequence.remove(hostId)
        shellWriteSequence.remove(hostId)
        UiDebugLog.result("stopSession", true, "hostId=$hostId")
    }

    fun stopAllSessions() {
        UiDebugLog.action("stopAllSessions", "count=${activeJobs.size}")
        val ids = activeJobs.keys.toList()
        ids.forEach { stopSession(it) }
        UiDebugLog.result("stopAllSessions", true)
    }

    fun sendKeyboardShortcut(hostId: String, shortcut: String) {
        UiDebugLog.action("sendKeyboardShortcut", "hostId=$hostId, shortcutBlank=${shortcut.isBlank()}")
        if (shortcut.isBlank()) {
            UiDebugLog.result("sendKeyboardShortcut", false, "blank-shortcut")
            return
        }
        val connection = activeConnections[hostId]
        if (connection == null) {
            UiDebugLog.result("sendKeyboardShortcut", false, "session-not-active hostId=$hostId")
            return
        }
        if (connection.mode != ConnectionMode.SSH) {
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.WARN,
                    message = "Snippet/shortcut execution is only available in SSH mode."
                )
            )
            UiDebugLog.result("sendKeyboardShortcut", false, "unsupported-mode hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                connection.client.startSession().use { session ->
                    val command = session.exec(shortcut)
                    command.join(8, TimeUnit.SECONDS)
                    val output = runCatching { command.inputStream.bufferedReader().readText() }.getOrNull()
                    if (!output.isNullOrBlank()) {
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = hostId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = output.trim()
                            )
                        )
                    }
                }
            }.onSuccess {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.INFO,
                        message = "Executed: $shortcut"
                    )
                )
                UiDebugLog.result("sendKeyboardShortcut", true, "hostId=$hostId")
            }.onFailure { error ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = error.message ?: "Command failed"
                    )
                )
                UiDebugLog.result("sendKeyboardShortcut", false, "hostId=$hostId")
            }
        }
    }

    fun sessionsFlow(): StateFlow<List<SessionSnapshot>> = sessionSnapshots.asStateFlow()
    fun hostKeyPromptsFlow(): StateFlow<List<HostKeyPrompt>> = hostKeyPrompts.asStateFlow()
    fun passwordPromptsFlow(): StateFlow<List<PasswordPrompt>> = passwordPrompts.asStateFlow()
    fun shellOutputFlow(): StateFlow<Map<String, String>> = shellOutput.asStateFlow()

    fun respondToHostKeyPrompt(promptId: String, trust: Boolean) {
        hostKeyPromptWaiters.remove(promptId)?.complete(trust)
        hostKeyPrompts.value = hostKeyPrompts.value.filterNot { it.id == promptId }
    }

    fun respondToPasswordPrompt(promptId: String, password: String?, savePassword: Boolean) {
        passwordPromptWaiters.remove(promptId)?.complete(
            PasswordResponse(
                password = password,
                savePassword = savePassword
            )
        )
        passwordPrompts.value = passwordPrompts.value.filterNot { it.id == promptId }
    }

    fun sendShellInput(hostId: String, text: String) {
        if (text.isEmpty()) return
        val connection = activeConnections[hostId]
        if (connection == null) {
            UiDebugLog.result("sendShellInput", false, "session-not-active hostId=$hostId")
            return
        }
        val shell = connection.shellBinding?.shell
        if (connection.mode != ConnectionMode.SSH || shell == null) {
            UiDebugLog.result("sendShellInput", false, "shell-not-available hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                val bytes = text.toByteArray(StandardCharsets.UTF_8)
                shell.outputStream.write(bytes)
                shell.outputStream.flush()
                emitShellStreamDiagnostic(hostId = hostId, direction = "TX", payload = bytes, size = bytes.size)
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "Failed to write to shell: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun resizeShell(hostId: String, columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        val connection = activeConnections[hostId] ?: return
        val shell = connection.shellBinding?.shell ?: return
        if (connection.mode != ConnectionMode.SSH) return
        val next = columns to rows
        if (shellSizes[hostId] == next) return
        shellSizes[hostId] = next
        serviceScope.launch {
            runCatching {
                shell.changeWindowDimensions(columns, rows, 0, 0)
            }
        }
    }

    private fun awaitHostKeyDecision(
        hostId: String,
        prompt: SshHostKeyPrompt,
        deadlineMillis: Long
    ): Boolean {
        val promptId = UUID.randomUUID().toString()
        val waiter = CompletableFuture<Boolean>()
        val uiPrompt = HostKeyPrompt(
            id = promptId,
            hostId = hostId,
            host = prompt.host,
            port = prompt.port,
            fingerprint = prompt.fingerprint,
            keyChanged = prompt.keyChanged
        )
        hostKeyPromptWaiters[promptId] = waiter
        hostKeyPrompts.value = hostKeyPrompts.value + uiPrompt
        val warning = if (prompt.keyChanged) {
            "WARNING: Host key changed for ${prompt.host}:${prompt.port} (${prompt.fingerprint})"
        } else {
            "Unknown host key for ${prompt.host}:${prompt.port} (${prompt.fingerprint})"
        }
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = if (prompt.keyChanged) SessionLogBus.LogLevel.WARN else SessionLogBus.LogLevel.INFO,
                message = warning
            )
        )
        val remainingMillis = millisUntilDeadline(deadlineMillis)
        if (remainingMillis <= 0L) {
            throw ConnectionTimeoutException(TIMEOUT_WAITING_FOR_INPUT_MESSAGE)
        }
        return try {
            waiter.get(remainingMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw ConnectionTimeoutException(TIMEOUT_WAITING_FOR_INPUT_MESSAGE)
        } catch (_: CancellationException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            hostKeyPromptWaiters.remove(promptId)
            hostKeyPrompts.value = hostKeyPrompts.value.filterNot { it.id == promptId }
        }
    }

    private fun awaitPasswordDecision(
        host: HostConnection,
        mode: ConnectionMode,
        reason: String,
        deadlineMillis: Long,
        allowSave: Boolean
    ): PasswordResponse? {
        val promptId = UUID.randomUUID().toString()
        val waiter = CompletableFuture<PasswordResponse>()
        val prompt = PasswordPrompt(
            id = promptId,
            hostId = host.id,
            host = host.host,
            port = host.port,
            username = host.username,
            reason = reason,
            allowSave = allowSave
        )
        passwordPromptWaiters[promptId] = waiter
        passwordPrompts.value = passwordPrompts.value + prompt
        updateSessionSnapshot(host, mode, SessionStatus.CONNECTING, reason)
        val remainingMillis = millisUntilDeadline(deadlineMillis)
        if (remainingMillis <= 0L) {
            throw ConnectionTimeoutException(TIMEOUT_WAITING_FOR_INPUT_MESSAGE)
        }
        return try {
            waiter.get(remainingMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw ConnectionTimeoutException(TIMEOUT_WAITING_FOR_INPUT_MESSAGE)
        } catch (_: CancellationException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            passwordPromptWaiters.remove(promptId)
            passwordPrompts.value = passwordPrompts.value.filterNot { it.id == promptId }
        }
    }

    private fun clearHostKeyPromptsForHost(hostId: String, trust: Boolean) {
        val promptsForHost = hostKeyPrompts.value.filter { it.hostId == hostId }
        promptsForHost.forEach { prompt ->
            hostKeyPromptWaiters.remove(prompt.id)?.complete(trust)
        }
        if (promptsForHost.isNotEmpty()) {
            hostKeyPrompts.value = hostKeyPrompts.value.filterNot { it.hostId == hostId }
        }
    }

    private fun clearAllHostKeyPrompts() {
        hostKeyPromptWaiters.values.forEach { it.complete(false) }
        hostKeyPromptWaiters.clear()
        hostKeyPrompts.value = emptyList()
    }

    private fun clearPasswordPromptsForHost(hostId: String, password: String?) {
        val promptsForHost = passwordPrompts.value.filter { it.hostId == hostId }
        promptsForHost.forEach { prompt ->
            passwordPromptWaiters.remove(prompt.id)?.complete(
                PasswordResponse(
                    password = password,
                    savePassword = false
                )
            )
        }
        if (promptsForHost.isNotEmpty()) {
            passwordPrompts.value = passwordPrompts.value.filterNot { it.hostId == hostId }
        }
    }

    private fun clearAllPasswordPrompts() {
        passwordPromptWaiters.values.forEach {
            it.complete(
                PasswordResponse(
                    password = null,
                    savePassword = false
                )
            )
        }
        passwordPromptWaiters.clear()
        passwordPrompts.value = emptyList()
    }

    private fun authenticateWithPassword(
        client: SSHClient,
        host: HostConnection,
        mode: ConnectionMode,
        initialPassword: String?,
        deadlineMillis: Long,
        allowPasswordSave: Boolean
    ) {
        var password = initialPassword
        var savePassword = false
        var failedAttempts = 0
        while (true) {
            throwIfAttemptTimedOut(deadlineMillis)
            if (password.isNullOrBlank()) {
                val reason = if (failedAttempts == 0) {
                    "Password required. Enter password to continue."
                } else {
                    "Authentication failed. Enter password and try again."
                }
                val response = awaitPasswordDecision(
                    host = host,
                    mode = mode,
                    reason = reason,
                    deadlineMillis = deadlineMillis,
                    allowSave = allowPasswordSave
                )
                password = response?.password
                savePassword = response?.savePassword == true && allowPasswordSave
                if (password.isNullOrBlank()) {
                    throw RuntimeException("Connection canceled while waiting for password.")
                }
            }
            try {
                val currentPassword = password
                updateSessionSnapshot(host, mode, SessionStatus.CONNECTING, "Authenticating as ${host.username}...")
                client.authPassword(host.username, currentPassword)
                if (savePassword) {
                    runCatching { SecurityManager.storeHostPassword(host.id, currentPassword) }
                }
                return
            } catch (_: UserAuthException) {
                failedAttempts += 1
                if (failedAttempts >= MAX_PASSWORD_PROMPT_ATTEMPTS) {
                    throw RuntimeException("Authentication failed after $MAX_PASSWORD_PROMPT_ATTEMPTS attempts.")
                }
                password = null
                savePassword = false
            }
        }
    }

    private fun openShell(hostId: String, client: SSHClient): ShellBinding {
        val session = client.startSession()
        session.allocateDefaultPTY()
        val shell = session.startShell()
        runCatching {
            // Trigger an initial prompt on shells that wait for first input.
            val bytes = "\n".toByteArray(StandardCharsets.UTF_8)
            shell.outputStream.write(bytes)
            shell.outputStream.flush()
            emitShellStreamDiagnostic(hostId = hostId, direction = "TX", payload = bytes, size = bytes.size)
        }
        serviceScope.launch {
            runCatching {
                val buffer = ByteArray(2048)
                while (true) {
                    val read = shell.inputStream.read(buffer)
                    if (read < 0) {
                        emitShellLifecycleDiagnostic(hostId, "RX EOF")
                        break
                    }
                    if (read > 0) {
                        val text = String(buffer, 0, read, StandardCharsets.UTF_8)
                        appendShellOutput(hostId, text)
                        emitShellStreamDiagnostic(hostId = hostId, direction = "RX", payload = buffer, size = read)
                    }
                }
            }.onFailure { err ->
                emitShellLifecycleDiagnostic(
                    hostId,
                    "RX stream error: ${err::class.java.simpleName}: ${err.message ?: "unknown error"}"
                )
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Shell stream ended: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
        return ShellBinding(session = session, shell = shell)
    }

    private fun appendShellOutput(hostId: String, text: String) {
        if (text.isEmpty()) return
        val current = shellOutput.value[hostId].orEmpty()
        val next = (current + text).takeLast(MAX_SHELL_OUTPUT_CHARS)
        val updated = shellOutput.value.toMutableMap()
        updated[hostId] = next
        shellOutput.value = updated
    }

    private fun clearShellOutputForHost(hostId: String) {
        if (!shellOutput.value.containsKey(hostId)) return
        val updated = shellOutput.value.toMutableMap()
        updated.remove(hostId)
        shellOutput.value = updated
        shellSizes.remove(hostId)
        shellReadSequence.remove(hostId)
        shellWriteSequence.remove(hostId)
    }

    private fun emitShellLifecycleDiagnostic(hostId: String, message: String) {
        if (!diagnosticsEnabled) return
        val line = "SHELL-DIAG $message"
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.DEBUG,
                message = line
            )
        )
        UiDebugLog.action("shellDiag", "hostId=$hostId, $message")
    }

    private fun emitShellStreamDiagnostic(
        hostId: String,
        direction: String,
        payload: ByteArray,
        size: Int
    ) {
        if (!diagnosticsEnabled || size <= 0) return
        val sequenceMap = if (direction == "RX") shellReadSequence else shellWriteSequence
        val seq = (sequenceMap[hostId] ?: 0) + 1
        sequenceMap[hostId] = seq
        val previewSize = minOf(size, SHELL_DIAG_PREVIEW_BYTES)
        val hex = payload.toHexPreview(previewSize)
        val ascii = payload.toAsciiPreview(previewSize)
        val truncated = if (size > previewSize) " (+${size - previewSize} bytes)" else ""
        val line = "SHELL-DIAG $direction#$seq bytes=$size hex=$hex ascii=\"$ascii\"$truncated"
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.DEBUG,
                message = line
            )
        )
        UiDebugLog.action("shellDiag", "hostId=$hostId, $direction#$seq bytes=$size")
    }

    private fun ByteArray.toHexPreview(length: Int): String {
        if (length <= 0) return ""
        val out = StringBuilder(length * 3)
        for (index in 0 until length) {
            if (index > 0) out.append(' ')
            val value = this[index].toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    private fun ByteArray.toAsciiPreview(length: Int): String {
        if (length <= 0) return ""
        val out = StringBuilder(length * 2)
        for (index in 0 until length) {
            val value = this[index].toInt() and 0xFF
            when (value) {
                0x0A -> out.append("\\n")
                0x0D -> out.append("\\r")
                0x09 -> out.append("\\t")
                0x08 -> out.append("\\b")
                0x1B -> out.append("\\e")
                in 0x20..0x7E -> out.append(value.toChar())
                else -> {
                    out.append("\\x")
                    out.append(HEX_DIGITS[value ushr 4])
                    out.append(HEX_DIGITS[value and 0x0F])
                }
            }
        }
        return out.toString()
    }

    private fun throwIfAttemptTimedOut(deadlineMillis: Long) {
        if (millisUntilDeadline(deadlineMillis) <= 0L) {
            throw ConnectionTimeoutException(TIMEOUT_WAITING_FOR_INPUT_MESSAGE)
        }
    }

    private fun millisUntilDeadline(deadlineMillis: Long): Long {
        val remaining = deadlineMillis - System.currentTimeMillis()
        return if (remaining > 0L) remaining else 0L
    }

    private fun updateSessionSnapshot(
        host: HostConnection,
        mode: ConnectionMode,
        status: SessionStatus,
        message: String?
    ) {
        val snapshot = SessionSnapshot(
            hostId = host.id,
            host = host,
            mode = mode,
            status = status,
            statusMessage = message
        )
        sessionSnapshots.value = sessionSnapshots.value
            .filterNot { it.hostId == host.id } + snapshot
        UiDebugLog.result(
            "sessionSnapshot",
            true,
            "hostId=${host.id}, mode=$mode, status=$status, message=${message ?: "none"}"
        )
    }

    private fun removeSessionSnapshot(hostId: String) {
        sessionSnapshots.value = sessionSnapshots.value.filterNot { it.hostId == hostId }
    }

    private fun updateSummaryNotification() {
        val summary = when {
            activeJobs.isEmpty() -> "No active sessions"
            activeJobs.size == 1 -> "Connected to 1 host"
            else -> "Connected to ${activeJobs.size} hosts"
        }
        val notif = buildNotification("SSHPeaches Sessions", summary)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun showHostNotification(host: HostConnection) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val stopIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_HOST_ID, host.id)
        }
        val pendingStop = PendingIntent.getService(
            this,
            host.id.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(host.name)
            .setContentText("Connected to ${host.host}:${host.port}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Disconnect", pendingStop)
            .build()
        nm.notify(host.id.hashCode(), notif)
    }

    private fun cancelHostNotification(hostId: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(hostId.hashCode())
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSHPeaches Sessions",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    inner class SessionBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.sshpeaches.app.service.ACTION_STOP"
        private const val EXTRA_HOST_ID = "extra_host_id"
        private const val CONNECTION_ATTEMPT_TIMEOUT_MS = 60_000L
        private const val TIMEOUT_WAITING_FOR_INPUT_MESSAGE = "Connection timed out while waiting for user input."
        private const val MAX_PASSWORD_PROMPT_ATTEMPTS = 3
        private const val MAX_SHELL_OUTPUT_CHARS = 32_000
        private const val SHELL_DIAG_PREVIEW_BYTES = 96
        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
    }

    private class ConnectionTimeoutException(message: String) : RuntimeException(message)

    private data class ActiveConnection(
        val host: HostConnection,
        val mode: ConnectionMode,
        val client: SSHClient,
        val shellBinding: ShellBinding?
    )

    private data class ShellBinding(
        val session: Session,
        val shell: Session.Shell
    )

    private data class PasswordResponse(
        val password: String?,
        val savePassword: Boolean
    )

    data class SessionSnapshot(
        val hostId: String,
        val host: HostConnection,
        val mode: ConnectionMode,
        val status: SessionStatus,
        val statusMessage: String?
    )

    data class HostKeyPrompt(
        val id: String,
        val hostId: String,
        val host: String,
        val port: Int,
        val fingerprint: String,
        val keyChanged: Boolean
    )

    data class PasswordPrompt(
        val id: String,
        val hostId: String,
        val host: String,
        val port: Int,
        val username: String,
        val reason: String,
        val allowSave: Boolean
    )

    enum class SessionStatus { CONNECTING, ACTIVE, ERROR }
}
