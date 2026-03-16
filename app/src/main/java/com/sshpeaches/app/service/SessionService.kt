package com.majordaftapps.sshpeaches.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider.HostKeyPrompt as SshHostKeyPrompt
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.widget.HostWidgets
import com.majordaftapps.sshpeaches.app.widget.WidgetSessionStore
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.net.ServerSocket
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
import kotlinx.coroutines.withContext
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * Foreground service that keeps SSH/Mosh sessions alive.
 * Sessions remain connected until explicitly stopped.
 */
class SessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val binder = SessionBinder()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private val sessionSnapshots = MutableStateFlow<List<SessionSnapshot>>(emptyList())
    private val hostKeyPrompts = MutableStateFlow<List<HostKeyPrompt>>(emptyList())
    private val hostKeyPromptWaiters = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val passwordPrompts = MutableStateFlow<List<PasswordPrompt>>(emptyList())
    private val passwordPromptWaiters = ConcurrentHashMap<String, CompletableFuture<PasswordResponse>>()
    private val shellOutput = MutableStateFlow<Map<String, String>>(emptyMap())
    private val remoteDirectories = MutableStateFlow<Map<String, RemoteDirectorySnapshot>>(emptyMap())
    private val shellSizes = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val shellReadSequence = ConcurrentHashMap<String, Int>()
    private val shellWriteSequence = ConcurrentHashMap<String, Int>()
    private val pendingShellOutputChunks = ConcurrentHashMap<String, StringBuilder>()
    private val pendingShellSnapshots = ConcurrentHashMap<String, String>()
    private val shellOutputPublishJobs = ConcurrentHashMap<String, Job>()
    private val moshTranscriptPublishJobs = ConcurrentHashMap<String, Job>()
    private val moshReconnectJobs = ConcurrentHashMap<String, Job>()
    private val notificationStateLock = Any()
    private var foregroundNotificationId: Int? = null
    private val sessionNotificationIds = mutableSetOf<Int>()
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
        UiDebugLog.result("SessionService.onCreate", true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UiDebugLog.action("SessionService.onStartCommand", "action=${intent?.action}, startId=$startId")
        when (intent?.action) {
            ACTION_STOP_ALL -> stopAllSessions()
            ACTION_STOP_SESSION -> intent.getStringExtra(EXTRA_HOST_ID)?.let { hostId ->
                if (hostId.isNotBlank()) {
                    stopSession(hostId)
                }
            }
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
        shellOutputPublishJobs.values.forEach { it.cancel() }
        shellOutputPublishJobs.clear()
        moshTranscriptPublishJobs.values.forEach { it.cancel() }
        moshTranscriptPublishJobs.clear()
        moshReconnectJobs.values.forEach { it.cancel() }
        moshReconnectJobs.clear()
        pendingShellOutputChunks.clear()
        pendingShellSnapshots.clear()
        shellOutput.value = emptyMap()
        remoteDirectories.value = emptyMap()
        shellSizes.clear()
        shellReadSequence.clear()
        shellWriteSequence.clear()
        UiDebugLog.result("SessionService.onDestroy", true)
    }

    fun startSession(
        requestedSessionId: String? = null,
        host: HostConnection,
        mode: ConnectionMode,
        passwordOverride: String? = null,
        availableForwards: List<PortForward> = emptyList(),
        availableSnippets: List<Snippet> = emptyList(),
        autoStartForwards: Boolean = true,
        autoTrustUnknownHostKey: Boolean = false,
        hostKeyPromptEnabled: Boolean = true,
        allowPasswordSave: Boolean = false,
        terminalEmulation: TerminalEmulation = TerminalEmulation.XTERM
    ) {
        val resolvedSessionId = requestedSessionId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: generateSessionId(host.id, mode)
        UiDebugLog.action(
            "startSession",
            "sessionId=$resolvedSessionId, hostId=${host.id}, mode=$mode, alreadyActive=${activeJobs.containsKey(resolvedSessionId)}, hasPasswordOverride=${!passwordOverride.isNullOrBlank()}"
        )
        if (activeJobs.containsKey(resolvedSessionId)) {
            UiDebugLog.result("startSession", false, "already-active sessionId=$resolvedSessionId")
            return
        }
        val sessionId = resolvedSessionId
        val useMoshTransport = host.useMosh && mode == ConnectionMode.SSH
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
            var moshBinding: MoshBinding? = null
            var sftpBinding: SftpBinding? = null
            var scpBinding: ScpBinding? = null
            var activeForwardBindings: List<PortForwardBinding> = emptyList()
            var sessionHost = host
            val attemptDeadlineMillis = System.currentTimeMillis() + CONNECTION_ATTEMPT_TIMEOUT_MS
            runCatching {
                updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Opening SSH connection...")
                client = SshClientProvider.createClient(
                    this@SessionService,
                    sessionHost,
                    SessionLoggerFactory(sessionId),
                    autoTrustUnknownHostKey = autoTrustUnknownHostKey,
                    onHostKeyPrompt = if (hostKeyPromptEnabled) {
                        { prompt ->
                            awaitHostKeyDecision(sessionId, prompt, attemptDeadlineMillis)
                        }
                    } else {
                        null
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
                            sessionId = sessionId,
                            mode = mode,
                            initialPassword = initialPassword,
                            deadlineMillis = attemptDeadlineMillis,
                            allowPasswordSave = allowPasswordSave
                        )
                    }
                    AuthMethod.IDENTITY -> {
                        authenticateWithIdentity(
                            client = client!!,
                            host = host,
                            sessionId = sessionId,
                            mode = mode,
                            required = true
                        )
                    }
                    AuthMethod.PASSWORD_AND_IDENTITY -> {
                        authenticateWithIdentity(
                            client = client!!,
                            host = host,
                            sessionId = sessionId,
                            mode = mode,
                            required = false
                        )
                        if (!client!!.isAuthenticated) {
                            authenticateWithPassword(
                                client = client!!,
                                host = host,
                                sessionId = sessionId,
                                mode = mode,
                                initialPassword = initialPassword,
                                deadlineMillis = attemptDeadlineMillis,
                                allowPasswordSave = allowPasswordSave
                            )
                        }
                    }
                }
                detectRemoteOsMetadata(sessionId, client!!)?.let { detected ->
                    if (sessionHost.osMetadata != detected) {
                        sessionHost = sessionHost.copy(osMetadata = detected)
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = sessionId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = "Detected remote OS: ${detected.toSummaryLabel()}"
                            )
                        )
                    }
                }
                when (mode) {
                    ConnectionMode.SSH -> {
                        if (useMoshTransport) {
                            updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting mosh-server...")
                            val moshConnect = startMoshServer(sessionId, client!!)
                            throwIfAttemptTimedOut(attemptDeadlineMillis)
                            updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting mosh client...")
                            moshBinding = startMoshClient(
                                hostId = sessionId,
                                host = sessionHost,
                                moshConnect = moshConnect,
                                terminalEmulation = terminalEmulation
                            )
                        } else {
                            updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting shell...")
                            shellBinding = openShell(sessionId, client!!, terminalEmulation)
                        }
                    }
                    ConnectionMode.SFTP -> {
                        updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Opening SFTP subsystem...")
                        sftpBinding = SftpBinding(client!!.newSFTPClient())
                        refreshSftpDirectoryListing(sessionId, sftpBinding!!.client, ".")
                    }
                    ConnectionMode.SCP -> {
                        updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Preparing SCP transfer channel...")
                        scpBinding = ScpBinding(client!!.newSCPFileTransfer())
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = sessionId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = "SCP ready. Use quick transfer controls to upload/download files."
                            )
                        )
                    }
                }
                val startupCommand = resolveStartupCommand(host.startupScript, availableSnippets)
                if (startupCommand.isNotBlank() && mode == ConnectionMode.SSH) {
                    runCatching {
                        client!!.startSession().use { shell ->
                            val cmd = shell.exec(startupCommand)
                            cmd.join(12, TimeUnit.SECONDS)
                            val output = runCatching { cmd.inputStream.bufferedReader().readText() }.getOrNull()
                            if (!output.isNullOrBlank()) {
                                SessionLogBus.emit(
                                    SessionLogBus.Entry(
                                        hostId = sessionId,
                                        level = SessionLogBus.LogLevel.INFO,
                                        message = output.trim()
                                    )
                                )
                            }
                        }
                    }.onFailure { err ->
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = sessionId,
                                level = SessionLogBus.LogLevel.WARN,
                                message = "Startup script failed: ${err.message ?: "unknown error"}"
                            )
                        )
                    }
                } else if (mode == ConnectionMode.SSH &&
                    parseSnippetReference(host.startupScript) != null &&
                    host.startupScript.isNotBlank()
                ) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = sessionId,
                            level = SessionLogBus.LogLevel.WARN,
                            message = "Startup snippet is missing; skipped startup command."
                        )
                    )
                }
                val configuredForwards = if (autoStartForwards) {
                    availableForwards.filter { it.enabled && it.associatedHosts.contains(host.id) }
                } else {
                    emptyList()
                }
                if (configuredForwards.isNotEmpty()) {
                    updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting ${configuredForwards.size} port forward(s)...")
                    activeForwardBindings = activatePortForwards(
                        hostId = sessionId,
                        client = client!!,
                        forwards = configuredForwards
                    )
                }
                activeConnections[sessionId] = ActiveConnection(
                    host = sessionHost,
                    mode = mode,
                    client = client,
                    shellBinding = shellBinding,
                    moshBinding = moshBinding,
                    sftpBinding = sftpBinding,
                    scpBinding = scpBinding,
                    portForwardBindings = activeForwardBindings
                )
                val modeLabel = when (mode) {
                    ConnectionMode.SSH -> if (useMoshTransport) "Mosh session ready" else "Interactive shell session ready"
                    ConnectionMode.SFTP -> "SFTP browser ready"
                    ConnectionMode.SCP -> "SCP transfer ready"
                }
                updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.ACTIVE, modeLabel)
                UiDebugLog.result("startSession", true, "sessionId=$sessionId, mode=$mode")

                // Keep the connection alive until user stops it.
                while (currentCoroutineContext().isActive) {
                    delay(10_000)
                }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    clearHostKeyPromptsForHost(sessionId, trust = false)
                    clearPasswordPromptsForHost(sessionId, password = null)
                    val statusMessage = e.message ?: "Connection failed"
                    updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.ERROR, statusMessage)
                    UiDebugLog.error("startSession", e, "sessionId=$sessionId, mode=$mode")
                    UiDebugLog.result("startSession", false, "sessionId=$sessionId, mode=$mode")
                }
            }
            runCatching { shellBinding?.shell?.close() }
            runCatching { shellBinding?.session?.close() }
            runCatching { moshBinding?.session?.finishIfRunning() }
            runCatching { sftpBinding?.client?.close() }
            runCatching { activeForwardBindings.forEach { closePortForwardBinding(it) } }
            runCatching { client?.disconnect() }
            activeConnections.remove(sessionId)
            clearHostKeyPromptsForHost(sessionId, trust = false)
            clearPasswordPromptsForHost(sessionId, password = null)
            clearShellOutputForHost(sessionId)
            clearRemoteDirectoryForHost(sessionId)
        }
        job.invokeOnCompletion {
            activeJobs.remove(sessionId)
            activeConnections.remove(sessionId)
            updateSessionNotifications()
            if (it is CancellationException) {
                removeSessionSnapshot(sessionId)
            }
        }
        activeJobs[sessionId] = job
        updateSessionNotifications()
    }

    fun stopSession(hostId: String) {
        UiDebugLog.action("stopSession", "hostId=$hostId")
        clearHostKeyPromptsForHost(hostId, trust = false)
        clearPasswordPromptsForHost(hostId, password = null)
        moshReconnectJobs.remove(hostId)?.cancel()
        val connection = activeConnections.remove(hostId)
        connection?.let {
            serviceScope.launch {
                closeConnectionResources(hostId, it, trigger = "stopSession")
            }
        }
        activeJobs.remove(hostId)?.cancel()
        updateSessionNotifications()
        removeSessionSnapshot(hostId)
        clearShellOutputForHost(hostId)
        clearRemoteDirectoryForHost(hostId)
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

    private fun closeConnectionResources(hostId: String, connection: ActiveConnection, trigger: String) {
        measureOperation("closeConnectionResources/$trigger", hostId, thresholdMs = 200L) {
            runCatching { connection.shellBinding?.shell?.close() }
            runCatching { connection.shellBinding?.session?.close() }
            runCatching { connection.moshBinding?.session?.finishIfRunning() }
            runCatching { connection.sftpBinding?.client?.close() }
            runCatching { connection.portForwardBindings.forEach { closePortForwardBinding(it) } }
            runCatching { connection.client?.disconnect() }
        }
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
        val payload = shortcut.toShellCommandPayload()
        serviceScope.launch {
            runCatching {
                if (connection.moshBinding != null || connection.shellBinding != null) {
                    sendShellBytes(hostId, payload.toByteArray(StandardCharsets.UTF_8))
                } else {
                    val client = connection.client ?: error("No active SSH client")
                    client.startSession().use { session ->
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

    private fun String.toShellCommandPayload(): String {
        val trimmed = trimEnd()
        if (trimmed.isEmpty()) return this
        return if (trimmed.contains('\n') || trimmed.contains('\r')) this else "$trimmed\r"
    }

    fun sessionsFlow(): StateFlow<List<SessionSnapshot>> = sessionSnapshots.asStateFlow()
    fun hostKeyPromptsFlow(): StateFlow<List<HostKeyPrompt>> = hostKeyPrompts.asStateFlow()
    fun passwordPromptsFlow(): StateFlow<List<PasswordPrompt>> = passwordPrompts.asStateFlow()
    fun shellOutputFlow(): StateFlow<Map<String, String>> = shellOutput.asStateFlow()
    fun remoteDirectoryFlow(): StateFlow<Map<String, RemoteDirectorySnapshot>> = remoteDirectories.asStateFlow()
    fun resolveTerminalEmulator(hostId: String): TerminalEmulator? {
        return activeConnections[hostId]?.moshBinding?.session?.emulator
    }

    fun resolveMoshTerminalEmulator(hostId: String): TerminalEmulator? {
        return resolveTerminalEmulator(hostId)
    }

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
        sendShellBytes(hostId, text.toByteArray(StandardCharsets.UTF_8))
    }

    fun sendShellBytes(hostId: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val connection = activeConnections[hostId]
        if (connection == null) {
            UiDebugLog.result("sendShellBytes", false, "session-not-active hostId=$hostId")
            return
        }
        connection.moshBinding?.let { mosh ->
            serviceScope.launch {
                runCatching {
                    mosh.session.write(bytes, 0, bytes.size)
                    emitShellStreamDiagnostic(hostId = hostId, direction = "TX", payload = bytes, size = bytes.size)
                }.onFailure { err ->
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.ERROR,
                            message = "Failed to write to mosh client: ${err.message ?: "unknown error"}"
                        )
                    )
                }
            }
            return
        }
        val shell = connection.shellBinding?.shell
        if (connection.mode != ConnectionMode.SSH || shell == null) {
            UiDebugLog.result("sendShellBytes", false, "shell-not-available hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
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
        if (connection.mode != ConnectionMode.SSH) return
        val next = columns to rows
        if (shellSizes[hostId] == next) return
        shellSizes[hostId] = next
        connection.moshBinding?.let { mosh ->
            serviceScope.launch(Dispatchers.Main) {
                runCatching {
                    mosh.session.updateSize(columns, rows, 0, 0)
                }
            }
            return
        }
        val shell = connection.shellBinding?.shell ?: return
        serviceScope.launch {
            runCatching {
                shell.changeWindowDimensions(columns, rows, 0, 0)
            }
        }
    }

    fun listSftpDirectory(hostId: String, path: String) {
        val targetPath = path.trim().ifBlank { "." }
        serviceScope.launch {
            val listed = runWithSftpClient(hostId) { sftp ->
                refreshSftpDirectoryListing(hostId, sftp, targetPath)
            }
            if (!listed) {
                UiDebugLog.result("listSftpDirectory", false, "sftp-not-active hostId=$hostId")
            }
        }
    }

    fun sftpDownloadFile(hostId: String, remotePath: String, localPath: String?) {
        val connection = activeConnections[hostId]
        val sftp = connection?.sftpBinding?.client
        if (sftp == null) {
            UiDebugLog.result("sftpDownloadFile", false, "sftp-not-active hostId=$hostId")
            return
        }
        val source = remotePath.trim()
        if (source.isBlank()) {
            UiDebugLog.result("sftpDownloadFile", false, "blank-remote-path hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                measureOperation("sftpDownloadFile", hostId) {
                    val destination = resolveDestinationFile(hostId, source, localPath)
                    destination.parentFile?.mkdirs()
                    sftp.get(source, destination.absolutePath)
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "SFTP download complete: $source -> ${destination.absolutePath}"
                        )
                    )
                }
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "SFTP download failed for $source: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun sftpUploadFile(hostId: String, localPath: String, remotePath: String) {
        val connection = activeConnections[hostId]
        val sftp = connection?.sftpBinding?.client
        if (sftp == null) {
            UiDebugLog.result("sftpUploadFile", false, "sftp-not-active hostId=$hostId")
            return
        }
        val source = localPath.trim()
        val destination = remotePath.trim()
        if (source.isBlank() || destination.isBlank()) {
            UiDebugLog.result("sftpUploadFile", false, "invalid-paths hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                measureOperation("sftpUploadFile", hostId) {
                    val sourceFile = File(source)
                    require(sourceFile.exists()) { "Local file does not exist: $source" }
                    require(sourceFile.isFile) { "Local path is not a file: $source" }
                    sftp.put(sourceFile.absolutePath, destination)
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "SFTP upload complete: ${sourceFile.absolutePath} -> $destination"
                        )
                    )
                }
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "SFTP upload failed to $destination: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun scpDownloadFile(hostId: String, remotePath: String, localPath: String?) {
        val connection = activeConnections[hostId]
        val scp = connection?.scpBinding?.transfer
        if (scp == null) {
            UiDebugLog.result("scpDownloadFile", false, "scp-not-active hostId=$hostId")
            return
        }
        val source = remotePath.trim()
        if (source.isBlank()) {
            UiDebugLog.result("scpDownloadFile", false, "blank-remote-path hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                measureOperation("scpDownloadFile", hostId) {
                    val destination = resolveDestinationFile(hostId, source, localPath)
                    destination.parentFile?.mkdirs()
                    scp.download(source, destination.absolutePath)
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "SCP download complete: $source -> ${destination.absolutePath}"
                        )
                    )
                }
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "SCP download failed for $source: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun scpUploadFile(hostId: String, localPath: String, remotePath: String) {
        val connection = activeConnections[hostId]
        val scp = connection?.scpBinding?.transfer
        if (scp == null) {
            UiDebugLog.result("scpUploadFile", false, "scp-not-active hostId=$hostId")
            return
        }
        val source = localPath.trim()
        val destination = remotePath.trim()
        if (source.isBlank() || destination.isBlank()) {
            UiDebugLog.result("scpUploadFile", false, "invalid-paths hostId=$hostId")
            return
        }
        serviceScope.launch {
            runCatching {
                measureOperation("scpUploadFile", hostId) {
                    val sourceFile = File(source)
                    require(sourceFile.exists()) { "Local file does not exist: $source" }
                    require(sourceFile.isFile) { "Local path is not a file: $source" }
                    scp.upload(sourceFile.absolutePath, destination)
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "SCP upload complete: ${sourceFile.absolutePath} -> $destination"
                        )
                    )
                }
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "SCP upload failed to $destination: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun manageRemotePath(hostId: String, operation: String, sourcePath: String, destinationPath: String? = null) {
        val src = sourcePath.trim()
        if (src.isBlank()) return
        serviceScope.launch {
            val ok = runWithSftpClient(hostId) { sftp ->
                measureOperation("manageRemotePath:$operation", hostId) {
                    when (operation.lowercase()) {
                        "mkdir" -> sftp.mkdir(src)
                        "delete" -> deleteRemotePathRecursively(sftp, src)
                        "move" -> {
                            val dest = destinationPath?.trim().orEmpty()
                            require(dest.isNotBlank()) { "Destination is required." }
                            sftp.rename(src, dest)
                        }
                        else -> error("Unsupported operation: $operation")
                    }
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Remote $operation completed: $src${destinationPath?.let { " -> $it" } ?: ""}"
                        )
                    )
                }
            }
            if (!ok) {
                UiDebugLog.result("manageRemotePath", false, "sftp-not-active hostId=$hostId")
            }
        }
    }

    private fun refreshSftpDirectoryListing(hostId: String, sftp: SFTPClient, path: String) {
        measureOperation("sftpListDirectory", hostId, thresholdMs = 200L) {
            val listingPath = path.trim().ifBlank { "." }
            val canonicalPath = runCatching { sftp.canonicalize(listingPath) }.getOrDefault(listingPath)
            val listing = sftp.ls(canonicalPath)
                .filterNot { it.name == "." || it.name == ".." }
                .sortedWith(
                    compareByDescending<RemoteResourceInfo> { it.isDirectory() }
                        .thenBy { it.name.lowercase() }
                )
            val entries = listing.map { item ->
                RemoteDirectoryEntry(
                    name = item.name,
                    isDirectory = item.isDirectory(),
                    sizeBytes = item.attributes.size
                )
            }
            setRemoteDirectorySnapshot(
                hostId,
                RemoteDirectorySnapshot(
                    path = canonicalPath,
                    entries = entries
                )
            )
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Listed ${listing.size} item(s) in $canonicalPath"
                )
            )
        }
    }

    private fun resolveDestinationFile(hostId: String, remotePath: String, localPath: String?): File {
        val explicit = localPath?.trim().orEmpty()
        if (explicit.isNotEmpty()) {
            val destination = File(explicit)
            if (destination.isDirectory) {
                val fallbackName = remotePath.substringAfterLast('/').ifBlank { "download.bin" }
                return File(destination, fallbackName)
            }
            return destination
        }
        val base = File(getExternalFilesDir(null) ?: filesDir, "transfers/$hostId")
        base.mkdirs()
        val filename = remotePath.substringAfterLast('/').ifBlank { "download.bin" }
        return File(base, filename)
    }

    private fun runWithSftpClient(hostId: String, action: (SFTPClient) -> Unit): Boolean {
        val connection = activeConnections[hostId] ?: return false
        val persistent = connection.sftpBinding?.client
        if (persistent != null) {
            return runCatching {
                measureOperation("runWithSftpClient:persistent", hostId) {
                    action(persistent)
                    true
                }
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "SFTP operation failed: ${err.message ?: "unknown error"}"
                    )
                )
            }.getOrDefault(false)
        }
        val client = connection.client ?: return false
        return runCatching {
            measureOperation("runWithSftpClient:temporary", hostId) {
                client.newSFTPClient().use { temporary ->
                    action(temporary)
                }
            }
            true
        }.onFailure { err ->
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.ERROR,
                    message = "SFTP operation failed: ${err.message ?: "unknown error"}"
                )
            )
        }.getOrDefault(false)
    }

    private fun deleteRemotePathRecursively(sftp: SFTPClient, path: String) {
        val canonical = runCatching { sftp.canonicalize(path) }.getOrDefault(path)
        val listing = runCatching { sftp.ls(canonical) }.getOrNull()
        if (listing == null) {
            sftp.rm(canonical)
            return
        }
        val children = listing.filterNot { it.name == "." || it.name == ".." }
        if (children.isEmpty()) {
            runCatching { sftp.rmdir(canonical) }.getOrElse { sftp.rm(canonical) }
            return
        }
        children.forEach { child ->
            val childPath = if (canonical.endsWith("/")) "$canonical${child.name}" else "$canonical/${child.name}"
            if (child.isDirectory()) {
                deleteRemotePathRecursively(sftp, childPath)
            } else {
                sftp.rm(childPath)
            }
        }
        runCatching { sftp.rmdir(canonical) }.getOrElse { sftp.rm(canonical) }
    }

    private fun activatePortForwards(
        hostId: String,
        client: SSHClient,
        forwards: List<PortForward>
    ): List<PortForwardBinding> {
        val bindings = mutableListOf<PortForwardBinding>()
        forwards.forEach { forward ->
            if (forward.type != com.majordaftapps.sshpeaches.app.data.model.PortForwardType.LOCAL) {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Only Local forwarding is supported. Skipping ${forward.label} (${forward.type})."
                    )
                )
                return@forEach
            }
            runCatching {
                val binding = startLocalPortForward(hostId, client, forward)
                bindings += binding
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.INFO,
                        message = binding.summary
                    )
                )
            }.onFailure { err ->
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "Failed to start ${forward.type} forward ${forward.label}: ${err.message ?: "unknown error"}"
                    )
                )
            }
        }
        return bindings
    }

    private fun startLocalPortForward(hostId: String, client: SSHClient, forward: PortForward): PortForwardBinding {
        val bindHost = normalizeBindHost(forward.sourceHost)
        val bindPort = requireValidPort(forward.sourcePort, "source")
        val destinationHost = forward.destinationHost.ifBlank { "127.0.0.1" }
        val destinationPort = requireValidPort(forward.destinationPort, "destination")
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(bindHost, bindPort))
        val localForwarder = client.newLocalPortForwarder(
            Parameters(bindHost, bindPort, destinationHost, destinationPort),
            serverSocket
        )
        val listenJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                localForwarder.listen(Thread.currentThread())
            }.onFailure { err ->
                if (activeJobs.containsKey(hostId)) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.WARN,
                            message = "Local forward ${forward.label} stopped: ${err.message ?: "unknown error"}"
                        )
                    )
                }
            }
        }
        return PortForwardBinding(
            forwardId = forward.id,
            summary = "Local forward active: $bindHost:$bindPort -> $destinationHost:$destinationPort",
            close = {
                runCatching { localForwarder.close() }
                listenJob.cancel()
            }
        )
    }

    private fun normalizeBindHost(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.isBlank()) "127.0.0.1" else trimmed
    }

    private fun requireValidPort(port: Int, label: String): Int {
        require(port in 1..65535) { "Invalid $label port: $port" }
        return port
    }

    private fun closePortForwardBinding(binding: PortForwardBinding) {
        runCatching { binding.close.invoke() }
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
        sessionId: String,
        mode: ConnectionMode,
        reason: String,
        deadlineMillis: Long,
        allowSave: Boolean
    ): PasswordResponse? {
        val promptId = UUID.randomUUID().toString()
        val waiter = CompletableFuture<PasswordResponse>()
        val prompt = PasswordPrompt(
            id = promptId,
            hostId = sessionId,
            host = host.host,
            port = host.port,
            username = host.username,
            reason = reason,
            allowSave = allowSave
        )
        passwordPromptWaiters[promptId] = waiter
        passwordPrompts.value = passwordPrompts.value + prompt
        updateSessionSnapshot(sessionId, host, mode, SessionStatus.CONNECTING, reason)
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
        sessionId: String,
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
                    sessionId = sessionId,
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
                updateSessionSnapshot(sessionId, host, mode, SessionStatus.CONNECTING, "Authenticating as ${host.username}...")
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

    private fun authenticateWithIdentity(
        client: SSHClient,
        host: HostConnection,
        sessionId: String,
        mode: ConnectionMode,
        required: Boolean
    ): Boolean {
        updateSessionSnapshot(sessionId, host, mode, SessionStatus.CONNECTING, "Authenticating with identity...")
        val identityId = host.preferredIdentityId?.takeIf { it.isNotBlank() }
        if (identityId == null) {
            if (required) {
                throw RuntimeException("No identity key selected for this host.")
            }
            return false
        }
        val privateKey = runCatching {
            SecurityManager.getIdentityKey(identityId)
        }.getOrNull()
        val keyPassphrase = runCatching {
            SecurityManager.getIdentityKeyPassphrase(identityId)
        }.getOrNull()
        if (privateKey.isNullOrBlank()) {
            if (required) {
                throw RuntimeException("Selected identity key is unavailable. Re-import the key and try again.")
            }
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.WARN,
                    message = "Selected identity key is unavailable. Falling back to password."
                )
            )
            return false
        }
        val tempKeyFile = writeIdentityKeyTempFile(host.id, privateKey)
        return try {
            val keyProvider = if (keyPassphrase.isNullOrBlank()) {
                client.loadKeys(tempKeyFile.absolutePath)
            } else {
                client.loadKeys(tempKeyFile.absolutePath, keyPassphrase.toCharArray())
            }
            client.authPublickey(host.username, keyProvider)
            client.isAuthenticated
        } catch (authError: UserAuthException) {
            if (required) {
                throw RuntimeException("Identity authentication failed.", authError)
            }
            false
        } finally {
            tempKeyFile.delete()
        }
    }

    private fun writeIdentityKeyTempFile(hostId: String, privateKey: String): File {
        val file = File.createTempFile("ssh_identity_${hostId}_", ".pem", cacheDir)
        file.writeText(
            privateKey.trim().let { key ->
                if (key.endsWith("\n")) key else "$key\n"
            },
            Charsets.UTF_8
        )
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        return file
    }

    private suspend fun startMoshServer(hostId: String, client: SSHClient): MoshConnect {
        return client.startSession().use { session ->
            val command = session.exec("LANG=en_US.UTF-8 mosh-server new -c 256")
            val stdout = command.inputStream.bufferedReader(StandardCharsets.UTF_8)
            val stderr = command.errorStream.bufferedReader(StandardCharsets.UTF_8)
            val combined = StringBuilder()
            val deadlineMillis = System.currentTimeMillis() + MOSH_BOOTSTRAP_TIMEOUT_MS
            var connect: MoshConnect? = null
            while (connect == null && System.currentTimeMillis() < deadlineMillis) {
                readLineIfReady(stdout)?.let { line ->
                    combined.appendLine(line)
                    connect = parseMoshConnectLine(line)
                }
                if (connect == null) {
                    readLineIfReady(stderr)?.let { line ->
                        combined.appendLine(line)
                        connect = parseMoshConnectLine(line)
                    }
                }
                if (connect == null) {
                    delay(40)
                }
            }
            runCatching { command.close() }
            connect ?: run {
                val detail = combined.toString().trim().ifBlank {
                    "No MOSH CONNECT reply received."
                }
                throw RuntimeException("Failed to start mosh-server. $detail")
            }
        }.also { connect ->
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Mosh server ready on UDP port ${connect.port}"
                )
            )
        }
    }

    private suspend fun startMoshClient(
        hostId: String,
        host: HostConnection,
        moshConnect: MoshConnect,
        terminalEmulation: TerminalEmulation
    ): MoshBinding {
        val runtime = MoshRuntime.prepare(this)
        val initialSize = shellSizes[hostId] ?: (MOSH_DEFAULT_COLUMNS to MOSH_DEFAULT_ROWS)
        val env = arrayOf(
            "MOSH_KEY=${moshConnect.key}",
            "TERM=${terminalEmulation.ptyName}",
            "TERMINFO=${runtime.terminfoDir.absolutePath}",
            "LD_LIBRARY_PATH=${runtime.libDir.absolutePath}",
            "HOME=${filesDir.absolutePath}",
            "TMPDIR=${cacheDir.absolutePath}",
            "PATH=${runtime.rootDir.resolve("bin").absolutePath}:/system/bin:/system/xbin"
        )
        val linkerPath = "/system/bin/linker64"
        val useLinkerLauncher = runCatching { java.io.File(linkerPath).canExecute() }.getOrDefault(false)
        val shellPath = if (useLinkerLauncher) linkerPath else runtime.clientBinary.absolutePath
        val args = if (useLinkerLauncher) {
            arrayOf(
                linkerPath,
                runtime.clientBinary.absolutePath,
                host.host,
                moshConnect.port.toString()
            )
        } else {
            arrayOf(
                "mosh-client",
                host.host,
                moshConnect.port.toString()
            )
        }
        if (useLinkerLauncher) {
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.DEBUG,
                    message = "Launching mosh client via linker64."
                )
            )
        }
        val terminalClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                scheduleMoshTranscriptSnapshot(hostId, changedSession)
            }

            override fun onTitleChanged(changedSession: TerminalSession) = Unit

            override fun onSessionFinished(finishedSession: TerminalSession) {
                handleMoshClientFinished(hostId, finishedSession)
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit

            override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit

            override fun onBell(session: TerminalSession) = Unit

            override fun onColorsChanged(session: TerminalSession) = Unit

            override fun onTerminalCursorStateChange(state: Boolean) = Unit
        }
        val session = withContext(Dispatchers.Main) {
            TerminalSession(
                shellPath,
                runtime.rootDir.absolutePath,
                args,
                env,
                MOSH_TRANSCRIPT_ROWS,
                terminalClient
            ).also { term ->
                term.updateSize(initialSize.first, initialSize.second, 0, 0)
            }
        }
        return MoshBinding(
            session = session,
            moshConnect = moshConnect,
            terminalEmulation = terminalEmulation
        )
    }

    private fun handleMoshClientFinished(hostId: String, finishedSession: TerminalSession) {
        serviceScope.launch {
            moshTranscriptPublishJobs.remove(hostId)?.cancel()
            val transcript = finishedSession.emulator?.screen?.transcriptTextWithoutJoinedLines.orEmpty()
            val tail = transcript
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(4)
                .joinToString(" | ")
            if (tail.isNotBlank()) {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Mosh client tail: $tail"
                    )
                )
            }
            if (!activeJobs.containsKey(hostId)) return@launch
            val connection = activeConnections[hostId] ?: return@launch
            val moshBinding = connection.moshBinding ?: return@launch
            if (moshBinding.session !== finishedSession) return@launch
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.WARN,
                    message = "Mosh client exited (code ${finishedSession.exitStatus}). Reconnecting..."
                )
            )
            updateSessionSnapshot(
                sessionId = hostId,
                host = connection.host,
                mode = ConnectionMode.SSH,
                status = SessionStatus.CONNECTING,
                message = "Mosh disconnected. Reconnecting..."
            )
            scheduleMoshReconnect(hostId)
        }
    }

    private fun scheduleMoshReconnect(hostId: String) {
        if (moshReconnectJobs[hostId]?.isActive == true) return
        moshReconnectJobs[hostId] = serviceScope.launch {
            var attempt = 0
            try {
                while (isActive && activeJobs.containsKey(hostId)) {
                    val connection = activeConnections[hostId] ?: break
                    val moshBinding = connection.moshBinding ?: break
                    attempt += 1
                    if (attempt > 1) {
                        val backoff = min(
                            MOSH_RECONNECT_MAX_DELAY_MS,
                            MOSH_RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 2).coerceAtMost(6))
                        )
                        delay(backoff)
                    }
                    val newBinding = runCatching {
                        startMoshClient(
                            hostId = hostId,
                            host = connection.host,
                            moshConnect = moshBinding.moshConnect,
                            terminalEmulation = moshBinding.terminalEmulation
                        )
                    }.onFailure { error ->
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = hostId,
                                level = SessionLogBus.LogLevel.WARN,
                                message = "Mosh reconnect attempt $attempt failed: ${error.message ?: "unknown error"}"
                            )
                        )
                    }.getOrNull() ?: continue
                    activeConnections[hostId] = connection.copy(moshBinding = newBinding)
                    updateSessionSnapshot(
                        sessionId = hostId,
                        host = connection.host,
                        mode = ConnectionMode.SSH,
                        status = SessionStatus.ACTIVE,
                        message = "Mosh session reconnected"
                    )
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Mosh reconnected after $attempt attempt(s)."
                        )
                    )
                    return@launch
                }
            } finally {
                moshReconnectJobs.remove(hostId)
            }
        }
    }

    private fun scheduleMoshTranscriptSnapshot(hostId: String, session: TerminalSession) {
        if (moshTranscriptPublishJobs[hostId]?.isActive == true) return
        moshTranscriptPublishJobs[hostId] = serviceScope.launch {
            delay(MOSH_SNAPSHOT_PUBLISH_INTERVAL_MS)
            val transcript = session.emulator?.screen?.transcriptTextWithoutJoinedLines.orEmpty()
            setShellOutputSnapshot(hostId, transcript)
            moshTranscriptPublishJobs.remove(hostId)
        }
    }

    private fun parseMoshConnectLine(line: String): MoshConnect? {
        val match = MOSH_CONNECT_PATTERN.find(line) ?: return null
        val port = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val key = match.groupValues.getOrNull(2).orEmpty()
        if (key.isBlank()) return null
        return MoshConnect(port = port, key = key)
    }

    private fun readLineIfReady(reader: BufferedReader): String? {
        if (!reader.ready()) return null
        return runCatching { reader.readLine() }.getOrNull()
    }

    private fun detectRemoteOsMetadata(hostId: String, client: SSHClient): OsMetadata? {
        return runCatching {
            val osRelease = runRemoteCommand(
                client = client,
                command = "cat /etc/os-release 2>/dev/null || cat /usr/lib/os-release 2>/dev/null",
                timeoutSeconds = 3
            )
            parseOsRelease(osRelease)?.let { return@runCatching it }
            val uname = runRemoteCommand(client, "uname -s 2>/dev/null", timeoutSeconds = 2)
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            val unameVersion = runRemoteCommand(client, "uname -r 2>/dev/null", timeoutSeconds = 2)
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            when {
                uname.equals("Darwin", ignoreCase = true) -> OsMetadata.Known(
                    family = OsFamily.MAC,
                    versionLabel = unameVersion.ifBlank { null }
                )
                uname.contains("FreeBSD", ignoreCase = true) ||
                    uname.contains("OpenBSD", ignoreCase = true) ||
                    uname.contains("NetBSD", ignoreCase = true) -> OsMetadata.Known(
                    family = OsFamily.BSD,
                    versionLabel = unameVersion.ifBlank { null }
                )
                uname.contains("Linux", ignoreCase = true) -> OsMetadata.Known(
                    family = OsFamily.GENERIC,
                    versionLabel = unameVersion.ifBlank { null }
                )
                else -> {
                    val windowsVer = runRemoteCommand(client, "cmd.exe /c ver", timeoutSeconds = 2).orEmpty()
                    if (windowsVer.contains("Windows", ignoreCase = true)) {
                        OsMetadata.Known(family = OsFamily.WINDOWS)
                    } else {
                        null
                    }
                }
            }
        }.onFailure { err ->
            UiDebugLog.action(
                "detectRemoteOsMetadata",
                "hostId=$hostId, failed=${err::class.java.simpleName}: ${err.message ?: "unknown"}"
            )
        }.getOrNull()
    }

    private fun runRemoteCommand(
        client: SSHClient,
        command: String,
        timeoutSeconds: Long
    ): String? {
        return runCatching {
            measureOperation("runRemoteCommand", thresholdMs = 1_000L) {
                client.startSession().use { session ->
                    val cmd = session.exec(command)
                    cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                    val stdout = runCatching { cmd.inputStream.bufferedReader(StandardCharsets.UTF_8).readText() }.getOrNull().orEmpty()
                    if (stdout.isNotBlank()) {
                        stdout
                    } else {
                        runCatching { cmd.errorStream.bufferedReader(StandardCharsets.UTF_8).readText() }.getOrNull()
                    }
                }
            }
        }.getOrNull()
    }

    private fun parseOsRelease(raw: String?): OsMetadata? {
        if (raw.isNullOrBlank()) return null
        val values = mutableMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val index = trimmed.indexOf('=')
            if (index <= 0) return@forEach
            val key = trimmed.substring(0, index).trim()
            val value = trimmed.substring(index + 1).trim().trim('"', '\'')
            values[key] = value
        }
        val id = values["ID"].orEmpty()
        val like = values["ID_LIKE"].orEmpty()
        val version = values["VERSION_ID"].orEmpty().ifBlank { values["VERSION"].orEmpty() }
        val family = mapOsReleaseToFamily(id, like)
        return when {
            family != null -> OsMetadata.Known(
                family = family,
                versionLabel = version.takeIf { it.isNotBlank() }
            )
            id.isNotBlank() || like.contains("linux", ignoreCase = true) -> OsMetadata.Known(
                family = OsFamily.GENERIC,
                versionLabel = version.takeIf { it.isNotBlank() }
            )
            else -> null
        }
    }

    private fun mapOsReleaseToFamily(idValue: String, idLikeValue: String): OsFamily? {
        val allCandidates = buildList {
            add(idValue)
            addAll(idLikeValue.split(' ', '\t'))
        }.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return allCandidates.firstNotNullOfOrNull { token ->
            when {
                token == "ubuntu" -> OsFamily.UBUNTU
                token == "debian" -> OsFamily.DEBIAN
                token == "fedora" -> OsFamily.FEDORA
                token == "centos" -> OsFamily.CENTOS
                token == "arch" || token == "archlinux" -> OsFamily.ARCH
                token == "linuxmint" || token == "mint" -> OsFamily.MINT
                token == "suse" || token.startsWith("opensuse") || token == "sles" -> OsFamily.SUSE
                token == "rhel" || token == "redhat" || token.contains("redhat") -> OsFamily.REDHAT
                token == "gentoo" -> OsFamily.GENTOO
                token == "pop" || token == "pop_os" || token == "pop!_os" -> OsFamily.POP_OS
                token == "manjaro" -> OsFamily.MANJARO
                token == "elementary" || token == "elementaryos" -> OsFamily.ELEMENTARY
                token == "peppermint" -> OsFamily.PEPPERMINT
                token == "lite" || token == "linuxlite" -> OsFamily.LITE
                token == "zorin" -> OsFamily.ZORIN
                token == "rocky" || token == "rockylinux" -> OsFamily.ROCKY
                token == "alma" || token == "almalinux" -> OsFamily.ALMA
                token == "asahi" -> OsFamily.ASAHI
                token == "nixos" -> OsFamily.NIXOS
                token == "freebsd" || token == "openbsd" || token == "netbsd" || token == "bsd" -> OsFamily.BSD
                token == "windows" || token == "msys" || token == "mingw" || token == "cygwin" -> OsFamily.WINDOWS
                token == "linux" -> OsFamily.GENERIC
                else -> null
            }
        }
    }

    private fun OsMetadata.toSummaryLabel(): String = when (this) {
        is OsMetadata.Known -> listOfNotNull(family.displayName, versionLabel?.takeIf { it.isNotBlank() }).joinToString(" ")
        is OsMetadata.Custom -> label
        OsMetadata.Undetected -> "Unknown"
    }

    private fun openShell(
        hostId: String,
        client: SSHClient,
        terminalEmulation: TerminalEmulation
    ): ShellBinding {
        val session = client.startSession()
        val allocated = runCatching {
            session.allocatePTY(
                terminalEmulation.ptyName,
                120,
                40,
                0,
                0,
                mutableMapOf<PTYMode, Int>()
            )
        }.isSuccess
        if (!allocated) {
            session.allocateDefaultPTY()
        }
        val shell = session.startShell()
        serviceScope.launch {
            var reachedEof = false
            runCatching {
                val buffer = ByteArray(2048)
                while (true) {
                    val read = shell.inputStream.read(buffer)
                    if (read < 0) {
                        reachedEof = true
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
                closeSessionAfterShellExit(
                    hostId,
                    "Shell stream ended: ${err.message ?: "unknown error"}"
                )
            }
            if (reachedEof) {
                closeSessionAfterShellExit(hostId, "Shell exited (EOF)")
            }
        }
        return ShellBinding(session = session, shell = shell)
    }

    private fun closeSessionAfterShellExit(hostId: String, reason: String) {
        if (!activeJobs.containsKey(hostId)) return
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.INFO,
                message = "$reason. Closing session."
            )
        )
        UiDebugLog.action("autoCloseSessionOnShellExit", "hostId=$hostId, reason=$reason")
        stopSession(hostId)
    }

    private fun appendShellOutput(hostId: String, text: String) {
        if (text.isEmpty()) return
        val buffer = pendingShellOutputChunks.computeIfAbsent(hostId) { StringBuilder() }
        synchronized(buffer) {
            buffer.append(text)
            if (buffer.length > MAX_SHELL_OUTPUT_CHARS * 2) {
                buffer.delete(0, buffer.length - MAX_SHELL_OUTPUT_CHARS)
            }
        }
        scheduleShellOutputPublish(hostId)
    }

    private fun setShellOutputSnapshot(hostId: String, text: String) {
        pendingShellSnapshots[hostId] = text.takeLast(MAX_SHELL_OUTPUT_CHARS)
        scheduleShellOutputPublish(hostId)
    }

    private fun clearShellOutputForHost(hostId: String) {
        shellOutputPublishJobs.remove(hostId)?.cancel()
        moshTranscriptPublishJobs.remove(hostId)?.cancel()
        pendingShellSnapshots.remove(hostId)
        pendingShellOutputChunks.remove(hostId)
        if (shellOutput.value.containsKey(hostId)) {
            val updated = shellOutput.value.toMutableMap()
            updated.remove(hostId)
            shellOutput.value = updated
        }
        shellSizes.remove(hostId)
        shellReadSequence.remove(hostId)
        shellWriteSequence.remove(hostId)
    }

    private fun scheduleShellOutputPublish(hostId: String) {
        if (shellOutputPublishJobs[hostId]?.isActive == true) return
        shellOutputPublishJobs[hostId] = serviceScope.launch {
            delay(SHELL_OUTPUT_PUBLISH_INTERVAL_MS)
            publishShellOutput(hostId)
            shellOutputPublishJobs.remove(hostId)
            val hasPendingChunk = (pendingShellOutputChunks[hostId]?.length ?: 0) > 0
            if (pendingShellSnapshots.containsKey(hostId) || hasPendingChunk) {
                scheduleShellOutputPublish(hostId)
            }
        }
    }

    private fun publishShellOutput(hostId: String) {
        val snapshot = pendingShellSnapshots.remove(hostId)
        val chunk = pendingShellOutputChunks.remove(hostId)?.let { builder ->
            synchronized(builder) { builder.toString() }
        }.orEmpty()
        val current = shellOutput.value[hostId].orEmpty()
        val next = when {
            snapshot != null -> snapshot
            chunk.isNotEmpty() -> (current + chunk).takeLast(MAX_SHELL_OUTPUT_CHARS)
            else -> current
        }
        if (next == current) return
        val updated = shellOutput.value.toMutableMap()
        updated[hostId] = next
        shellOutput.value = updated
    }

    private fun setRemoteDirectorySnapshot(hostId: String, snapshot: RemoteDirectorySnapshot) {
        if (remoteDirectories.value[hostId] == snapshot) return
        val updated = remoteDirectories.value.toMutableMap()
        updated[hostId] = snapshot
        remoteDirectories.value = updated
    }

    private fun clearRemoteDirectoryForHost(hostId: String) {
        if (!remoteDirectories.value.containsKey(hostId)) return
        val updated = remoteDirectories.value.toMutableMap()
        updated.remove(hostId)
        remoteDirectories.value = updated
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

    private inline fun <T> measureOperation(
        operation: String,
        hostId: String? = null,
        thresholdMs: Long = SLOW_OPERATION_WARN_MS,
        block: () -> T
    ): T {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= thresholdMs) {
                val suffix = hostId?.let { ", hostId=$it" }.orEmpty()
                Log.w(PERF_TAG, "Slow operation: $operation took ${elapsedMs}ms$suffix")
                if (diagnosticsEnabled && hostId != null) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.WARN,
                            message = "Slow operation: $operation (${elapsedMs}ms)"
                        )
                    )
                }
                UiDebugLog.action("servicePerf", "operation=$operation, elapsedMs=$elapsedMs, hostId=${hostId ?: "n/a"}")
            }
        }
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
        sessionId: String,
        host: HostConnection,
        mode: ConnectionMode,
        status: SessionStatus,
        message: String?
    ) {
        val snapshot = SessionSnapshot(
            hostId = sessionId,
            host = host,
            mode = mode,
            status = status,
            statusMessage = message
        )
        sessionSnapshots.value = sessionSnapshots.value
            .filterNot { it.hostId == sessionId } + snapshot
        if (activeJobs.containsKey(sessionId)) {
            updateSessionNotifications()
        }
        publishWidgetSessionState()
        UiDebugLog.result(
            "sessionSnapshot",
            true,
            "sessionId=$sessionId, mode=$mode, status=$status, message=${message ?: "none"}"
        )
    }

    private fun generateSessionId(hostId: String, mode: ConnectionMode): String =
        "$hostId|${mode.name}|${UUID.randomUUID()}"

    private fun removeSessionSnapshot(hostId: String) {
        sessionSnapshots.value = sessionSnapshots.value.filterNot { it.hostId == hostId }
        updateSessionNotifications()
        publishWidgetSessionState()
    }

    private fun publishWidgetSessionState() {
        WidgetSessionStore.write(this, sessionSnapshots.value)
        HostWidgets.updateAll(this)
    }

    private fun updateSessionNotifications() {
        synchronized(notificationStateLock) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeHostIds = activeJobs.keys.toSet()
            val snapshots = sessionSnapshots.value
                .filter { activeHostIds.contains(it.hostId) }
                .sortedBy { it.host.name.lowercase() }

            if (snapshots.isEmpty()) {
                val existingNotificationIds = sessionNotificationIds.toSet()
                existingNotificationIds.forEach { id -> nm.cancel(id) }
                sessionNotificationIds.clear()
                if (foregroundNotificationId != null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundNotificationId = null
                }
                return
            }

            val existingNotificationIds = sessionNotificationIds.toSet()
            val desiredIds = snapshots.map { notificationIdForHost(it.hostId) }.toSet()
            val staleIds = existingNotificationIds - desiredIds
            staleIds.forEach { id -> nm.cancel(id) }

            snapshots.forEachIndexed { index, snapshot ->
                val notificationId = notificationIdForHost(snapshot.hostId)
                val notification = buildSessionNotification(snapshot, snapshots.size)
                if (index == 0) {
                    startForeground(notificationId, notification)
                    foregroundNotificationId = notificationId
                } else {
                    nm.notify(notificationId, notification)
                }
            }
            sessionNotificationIds.clear()
            sessionNotificationIds.addAll(desiredIds)
        }
    }

    private fun buildSessionNotification(
        snapshot: SessionSnapshot,
        totalSessions: Int
    ): Notification {
        val text = when (snapshot.status) {
            SessionStatus.ACTIVE -> snapshot.statusMessage ?: "Connected"
            SessionStatus.CONNECTING -> snapshot.statusMessage ?: "Connecting"
            SessionStatus.ERROR -> snapshot.statusMessage ?: "Connection error"
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_SESSION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HOST_ID, snapshot.hostId)
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE + snapshot.hostId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP_SESSION
            putExtra(EXTRA_HOST_ID, snapshot.hostId)
        }
        val pendingStop = PendingIntent.getService(
            this,
            STOP_SESSION_REQUEST_CODE + snapshot.hostId.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationIcon = resolveNotificationIcon()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${snapshot.host.username}@${snapshot.host.host}:${snapshot.host.port}")
            .setContentText(text)
            .setSmallIcon(notificationIcon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSubText(if (totalSessions == 1) "1 active session" else "$totalSessions active sessions")
            .setContentIntent(pendingOpen)
            .addAction(notificationIcon, "Open", pendingOpen)
            .addAction(notificationIcon, "Disconnect", pendingStop)
        return builder.build()
    }

    private fun notificationIdForHost(hostId: String): Int = NOTIFICATION_ID_BASE + hostId.hashCode().absoluteValue

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSHPeaches Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active SSH sessions and controls"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    private fun resolveNotificationIcon(): Int {
        val appIcon = applicationInfo.icon
        if (appIcon != 0) return appIcon
        return runCatching {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)).icon
        }.getOrDefault(android.R.drawable.stat_notify_more)
            .takeIf { it != 0 }
            ?: android.R.drawable.stat_notify_more
    }

    private fun resolveStartupCommand(startupScript: String, snippets: List<Snippet>): String {
        val raw = startupScript.trim()
        if (raw.isBlank()) return ""
        val snippetId = parseSnippetReference(raw) ?: return raw
        return snippets.firstOrNull { it.id == snippetId }?.command?.trim().orEmpty()
    }

    inner class SessionBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }

    companion object {
        private const val PERF_TAG = "SSHPeachesPerf"
        private const val CHANNEL_ID = "sessions"
        private const val OPEN_APP_REQUEST_CODE = 19_241
        private const val STOP_SESSION_REQUEST_CODE = 19_243
        private const val NOTIFICATION_ID_BASE = 42_000
        private const val ACTION_STOP_ALL = "com.majordaftapps.sshpeaches.app.service.ACTION_STOP_ALL"
        const val ACTION_STOP_SESSION = "com.majordaftapps.sshpeaches.app.service.ACTION_STOP_SESSION"
        const val ACTION_OPEN_SESSION = "com.majordaftapps.sshpeaches.app.service.ACTION_OPEN_SESSION"
        const val EXTRA_HOST_ID = "extra_host_id"
        private const val CONNECTION_ATTEMPT_TIMEOUT_MS = 60_000L
        private const val TIMEOUT_WAITING_FOR_INPUT_MESSAGE = "Connection timed out while waiting for user input."
        private const val MAX_PASSWORD_PROMPT_ATTEMPTS = 3
        private const val MAX_SHELL_OUTPUT_CHARS = 32_000
        private const val SHELL_OUTPUT_PUBLISH_INTERVAL_MS = 40L
        private const val MOSH_SNAPSHOT_PUBLISH_INTERVAL_MS = 40L
        private const val SLOW_OPERATION_WARN_MS = 250L
        private const val SHELL_DIAG_PREVIEW_BYTES = 96
        private const val MOSH_BOOTSTRAP_TIMEOUT_MS = 15_000L
        private const val MOSH_DEFAULT_COLUMNS = 120
        private const val MOSH_DEFAULT_ROWS = 40
        private const val MOSH_TRANSCRIPT_ROWS = 2000
        private const val MOSH_RECONNECT_BASE_DELAY_MS = 1_000L
        private const val MOSH_RECONNECT_MAX_DELAY_MS = 15_000L
        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
        private val MOSH_CONNECT_PATTERN = Regex("""MOSH CONNECT\s+(\d+)\s+([A-Za-z0-9+/=]+)""")
    }

    private class ConnectionTimeoutException(message: String) : RuntimeException(message)

    private data class ActiveConnection(
        val host: HostConnection,
        val mode: ConnectionMode,
        val client: SSHClient?,
        val shellBinding: ShellBinding?,
        val moshBinding: MoshBinding?,
        val sftpBinding: SftpBinding?,
        val scpBinding: ScpBinding?,
        val portForwardBindings: List<PortForwardBinding>
    )

    private data class ShellBinding(
        val session: Session,
        val shell: Session.Shell
    )

    private data class MoshBinding(
        val session: TerminalSession,
        val moshConnect: MoshConnect,
        val terminalEmulation: TerminalEmulation
    )

    private data class SftpBinding(
        val client: SFTPClient
    )

    private data class ScpBinding(
        val transfer: SCPFileTransfer
    )

    private data class PortForwardBinding(
        val forwardId: String,
        val summary: String,
        val close: () -> Unit
    )

    private data class MoshConnect(
        val port: Int,
        val key: String
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

    data class RemoteDirectorySnapshot(
        val path: String,
        val entries: List<RemoteDirectoryEntry>
    )

    data class RemoteDirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long
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
