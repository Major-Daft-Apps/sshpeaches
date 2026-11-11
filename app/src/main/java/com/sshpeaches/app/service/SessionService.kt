package com.majordaftapps.sshpeaches.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.MainActivity
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.Snippet
import com.majordaftapps.sshpeaches.app.data.model.TerminalEmulation
import com.majordaftapps.sshpeaches.app.data.settings.DEFAULT_MOSH_SERVER_COMMAND
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.sftp.SftpChunkQueue
import com.majordaftapps.sshpeaches.app.sftp.SftpResumableDownloader
import com.majordaftapps.sshpeaches.app.sftp.SftpPartialDownload
import com.majordaftapps.sshpeaches.app.sftp.SftpSegmentWriter
import com.majordaftapps.sshpeaches.app.sftp.SftpSegmentedTransfer
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferCancelledException
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferSettings
import com.majordaftapps.sshpeaches.app.data.ssh.Ed25519IdentityKeyProvider
import com.majordaftapps.sshpeaches.app.data.ssh.HostSystemInfo
import com.majordaftapps.sshpeaches.app.data.ssh.HostSystemInfoCollector
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider.HostKeyPrompt as SshHostKeyPrompt
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.ui.logging.UiDebugLog
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import com.majordaftapps.sshpeaches.app.util.REMOTE_FORWARD_REFUSED_HINT
import com.majordaftapps.sshpeaches.app.util.displayName
import com.majordaftapps.sshpeaches.app.util.inferredDestinationHost
import com.majordaftapps.sshpeaches.app.util.legacyLoopbackDestinationHost
import com.majordaftapps.sshpeaches.app.widget.HostWidgets
import com.majordaftapps.sshpeaches.app.widget.WidgetSessionStore
import com.majordaftapps.sshpeaches.app.util.parseSnippetReference
import com.majordaftapps.sshpeaches.app.data.ssh.loadPrivateKeyFromMemory
import com.majordaftapps.sshpeaches.app.util.selectedHostId
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.concurrent.Promise
import net.schmizz.keepalive.KeepAliveRunner
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.OpenFailException
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import com.majordaftapps.sshpeaches.app.telemetry.TelemetryInitializer
import net.schmizz.sshj.userauth.UserAuthException
import com.majordaftapps.sshpeaches.app.data.ssh.INTERACTIVE_LOGIN_TIMEOUT_MS
import com.majordaftapps.sshpeaches.app.data.ssh.LoginCanceledException
import com.majordaftapps.sshpeaches.app.data.ssh.LoginChallenge
import com.majordaftapps.sshpeaches.app.data.ssh.authPasswordOrInteractive
import kotlin.math.min

/**
 * Foreground service that keeps SSH/Mosh sessions alive.
 * Sessions remain connected until explicitly stopped.
 */
class SessionService : Service() {

    // SupervisorJob: one failing task (a session, a transfer) must not cancel all the others.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Never-ending collectors live here and are cancelled in onDestroy; left in serviceScope they
    // kept every destroyed service instance alive. (serviceScope is not cancelled so that
    // disconnect work launched by stopAllSessions in onDestroy can still finish.)
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = SessionBinder()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeConnections = ConcurrentHashMap<String, ActiveConnection>()
    private val runtimeSessionPasswords = ConcurrentHashMap<String, String>()
    private val sessionSnapshots = MutableStateFlow<List<SessionSnapshot>>(emptyList())
    private val hostKeyPrompts = MutableStateFlow<List<HostKeyPrompt>>(emptyList())
    private val hostKeyPromptWaiters = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val passwordPrompts = MutableStateFlow<List<PasswordPrompt>>(emptyList())
    private val passwordPromptWaiters = ConcurrentHashMap<String, CompletableFuture<PasswordResponse>>()
    private val shellOutput = MutableStateFlow<Map<String, String>>(emptyMap())
    private val remoteDirectories = MutableStateFlow<Map<String, RemoteDirectorySnapshot>>(emptyMap())
    private val fileTransferProgress = MutableStateFlow<Map<String, FileTransferProgress>>(emptyMap())
    private val activeFileTransfers = ConcurrentHashMap<String, CopyOnWriteArrayList<ActiveFileTransfer>>()
    /** Each session's finished transfers, newest first, for the transfers pane. */
    private val finishedTransfers = ConcurrentHashMap<String, List<FileTransferProgress>>()
    /** How to start a finished transfer again, by operation id, with the session it belongs to. */
    private val transferRetries = ConcurrentHashMap<String, Pair<String, () -> Unit>>()
    /** Every session's transfers, running and waiting first, then finished: what the transfers pane lists. */
    private val transferLists = MutableStateFlow<Map<String, List<FileTransferProgress>>>(emptyMap())
    private val transferQueues = ConcurrentHashMap<String, Mutex>()
    private val listingGenerations = ConcurrentHashMap<String, AtomicLong>()
    private val lastRequestedListings = ConcurrentHashMap<String, String>()
    private val bulkLinkLookupUnavailable: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val fileBoosts = ConcurrentHashMap<String, BoostHelper>()
    private val fileBoostStarts = ConcurrentHashMap<String, Job>()
    private val fileBoostLastStart = ConcurrentHashMap<String, Long>()
    private val sessionSnapshotsState = sessionSnapshots.asStateFlow()
    private val hostKeyPromptsState = hostKeyPrompts.asStateFlow()
    private val passwordPromptsState = passwordPrompts.asStateFlow()
    private val shellOutputState = shellOutput.asStateFlow()
    private val remoteDirectoriesState = remoteDirectories.asStateFlow()
    private val fileTransferProgressState = fileTransferProgress.asStateFlow()
    private val shellSizes = ConcurrentHashMap<String, Pair<Int, Int>>()
    /** Boost terminals kept while their session's SSH connection reconnects: typing and output carry on meanwhile. */
    private val keptBoostShells = ConcurrentHashMap<String, ShellBinding>()
    /** Each file browser's recently listed folders, shown at once when it goes back, forward or up to one. */
    private val recentListings = ConcurrentHashMap<String, RecentListings<RemoteDirectorySnapshot>>()
    private val metrics by lazy { com.majordaftapps.sshpeaches.app.diagnostics.PerformanceMetrics.get(this) }
    /** Each session's round trips, sampled while Advanced settings → Show connection latency is on. */
    private val connectionLatencies = MutableStateFlow<Map<String, ConnectionLatency>>(emptyMap())
    private val latencySamples = ConcurrentHashMap<String, LatencySamples>()
    @Volatile
    private var latencyJob: Job? = null
    /** Hosts where a Boost terminal couldn't start lately, so connecting there doesn't wait on it again for a while. */
    private val boostTerminalUnavailableUntil = ConcurrentHashMap<String, Long>()
    /** Copies of the Boost helper to a server ("user@host:port") running behind a terminal. */
    private val boostInstalls = ConcurrentHashMap<String, Pair<SSHClient, Job>>()
    /** The last state each Boost terminal reported, so a run of reconnect attempts is logged once. */
    private val boostTerminalStates = ConcurrentHashMap<String, String>()
    /** When each shell last sent anything, for [checkUnansweredInput]. */
    private val lastShellOutputAtMs = ConcurrentHashMap<String, Long>()
    private val unansweredInputChecks = ConcurrentHashMap<String, Job>()
    private val shellReadSequence = ConcurrentHashMap<String, Int>()
    private val shellWriteSequence = ConcurrentHashMap<String, Int>()
    private val pendingShellOutputChunks = ConcurrentHashMap<String, StringBuilder>()
    private val pendingShellSnapshots = ConcurrentHashMap<String, String>()
    private val shellOutputPublishJobs = ConcurrentHashMap<String, Job>()
    private val moshTranscriptPublishJobs = ConcurrentHashMap<String, Job>()
    private val moshReconnectJobs = ConcurrentHashMap<String, Job>()
    private val transferNotificationRefreshJobs = ConcurrentHashMap<String, Job>()
    private val networkTransitionTracker = DefaultNetworkTransitionTracker<Network>()
    private val networkHandler = Handler(Looper.getMainLooper())
    private val sessionStateLock = Any()
    private val notificationStateLock = Any()
    private val fileTransferStateLock = Any()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    @Volatile
    private var serviceDestroyed = false
    private var foregroundNotificationStarted = false
    private val sessionNotificationIdentities = mutableMapOf<String, SessionNotificationIdentity>()
    private var nextSessionNotificationId = FIRST_SESSION_NOTIFICATION_ID
    private var nextSessionDisplayNumber = 1
    @Volatile
    private var diagnosticsEnabled: Boolean = false
    @Volatile
    private var sftpTransferSettings: SftpTransferSettings = SftpTransferSettings()
    @Volatile
    private var autoReconnectEnabled: Boolean = true
    /** Completing a session's signal makes its job drop the connection and reconnect. */
    private val reconnectSignals = ConcurrentHashMap<String, CompletableDeferred<String>>()
    @Volatile private var lastLivenessCheckAtMs = Long.MIN_VALUE / 2
    /** Sessions between losing their connection and getting a new one. */
    private val reconnectingSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val networkAvailable = MutableStateFlow(true)

    override fun onCreate() {
        super.onCreate()
        serviceDestroyed = false
        UiDebugLog.action("SessionService.onCreate")
        collectorScope.launch {
            SettingsStore.diagnosticsEnabled.collect { enabled ->
                diagnosticsEnabled = enabled
            }
        }
        collectorScope.launch {
            // Performance figures go into the weekly usage report, so they're collected only while it's on.
            SettingsStore.usageReportsEnabled.collect { enabled -> metrics.enabled = enabled }
        }
        collectorScope.launch {
            SettingsStore.showConnectionLatency.collect { enabled ->
                latencyJob?.cancel()
                latencyJob = if (enabled) serviceScope.launch { sampleLatencies() } else null
                if (!enabled) {
                    latencySamples.clear()
                    connectionLatencies.value = emptyMap()
                }
            }
        }
        collectorScope.launch {
            SettingsStore.sftpTransferSettings.collect { settings ->
                val before = sftpTransferSettings
                sftpTransferSettings = settings
                // A new port range applies to the next boost started (the field saves as it's typed).
                if (before.boost != settings.boost || before.boostTcpFallback != settings.boostTcpFallback) {
                    activeConnections.keys.forEach { sessionId ->
                        stopFileBoost(sessionId)
                        if (settings.boost) startFileBoost(sessionId, force = true)
                    }
                }
            }
        }
        collectorScope.launch {
            SettingsStore.autoReconnect.collect { enabled ->
                autoReconnectEnabled = enabled
            }
        }
        deleteLeftoverTempFiles()
        createChannel()
        registerDefaultNetworkMonitor()
        serviceScope.launch { SshClientProvider.warmUp(this@SessionService) }
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

    /**
     * Temp files are deleted in `finally` blocks, which do not run if the process is killed
     * mid-operation. That could leave a plaintext private key (ssh_identity_*.pem) in the cache
     * dir. No session exists yet when the service is created, so none of these are in use.
     */
    private fun deleteLeftoverTempFiles() {
        val prefixes = listOf("ssh_identity_", "sftp_dl_", "uri_upload_")
        runCatching {
            cacheDir.listFiles { file -> file.isFile && prefixes.any { file.name.startsWith(it) } }
                ?.forEach { it.delete() }
        }
        runCatching { SftpPartialDownload.deleteStale(sftpPartialDirectory(), PARTIAL_DOWNLOAD_MAX_AGE_MS) }
    }

    override fun onDestroy() {
        UiDebugLog.action("SessionService.onDestroy", "activeSessions=${activeJobs.size}")
        serviceDestroyed = true
        collectorScope.cancel()
        unregisterDefaultNetworkMonitor()
        networkHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        stopAllSessions()
        clearAllHostKeyPrompts()
        clearAllPasswordPrompts()
        runtimeSessionPasswords.clear()
        shellOutputPublishJobs.values.forEach { it.cancel() }
        shellOutputPublishJobs.clear()
        moshTranscriptPublishJobs.values.forEach { it.cancel() }
        moshTranscriptPublishJobs.clear()
        moshReconnectJobs.values.forEach { it.cancel() }
        moshReconnectJobs.clear()
        transferNotificationRefreshJobs.values.forEach { it.cancel() }
        transferNotificationRefreshJobs.clear()
        pendingShellOutputChunks.clear()
        pendingShellSnapshots.clear()
        shellOutput.value = emptyMap()
        remoteDirectories.value = emptyMap()
        fileTransferProgress.value = emptyMap()
        shellSizes.clear()
        shellReadSequence.clear()
        shellWriteSequence.clear()
        UiDebugLog.result("SessionService.onDestroy", true)
    }

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkAvailable.value = true
            // Boost terminals reattach now instead of after their next retry delay.
            kickBoostShells()
            // Transfers waiting out a retry delay try again now.
            activeFileTransfers.values.flatten().forEach { it.engine?.wakeUp() }
            networkTransitionTracker.onAvailable(
                network = network,
                isVpn = networkIsVpn(network)
            )?.let(::disconnectForNetworkTransition)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            networkTransitionTracker.onCapabilitiesChanged(
                network = network,
                isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )?.let(::disconnectForNetworkTransition)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            networkTransitionTracker.onLost(network)
                ?.let(::disconnectForNetworkTransition)
        }

        override fun onLost(network: Network) {
            networkTransitionTracker.onLost(network)
                ?.also { networkAvailable.value = false }
                ?.let(::disconnectForNetworkTransition)
        }
    }

    private fun registerDefaultNetworkMonitor() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = manager
        val initialNetwork = manager.activeNetwork
        networkTransitionTracker.seed(
            network = initialNetwork,
            isVpn = initialNetwork?.let(::networkIsVpn)
        )
        networkAvailable.value = initialNetwork != null
        runCatching {
            manager.registerDefaultNetworkCallback(defaultNetworkCallback)
        }.onSuccess {
            networkCallbackRegistered = true
        }.onFailure { error ->
            UiDebugLog.error("SessionService.networkMonitor", error, "register-failed")
        }
    }

    private fun unregisterDefaultNetworkMonitor() {
        val manager = connectivityManager
        if (manager != null && networkCallbackRegistered) {
            runCatching {
                manager.unregisterNetworkCallback(defaultNetworkCallback)
            }.onFailure { error ->
                UiDebugLog.error("SessionService.networkMonitor", error, "unregister-failed")
            }
        }
        networkCallbackRegistered = false
        connectivityManager = null
        networkTransitionTracker.seed(network = null, isVpn = null)
    }

    private fun networkIsVpn(network: Network): Boolean? =
        connectivityManager
            ?.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    /** Tests: what airplane mode on then off does, without changing the device: the network lost, then a new one. */
    @androidx.annotation.VisibleForTesting
    internal fun simulateNetworkDropForTests() {
        networkAvailable.value = false
        disconnectForNetworkTransition(DefaultNetworkTransition.LOST)
        networkHandler.postDelayed({
            networkAvailable.value = true
            kickBoostShells()
            activeFileTransfers.values.flatten().forEach { it.engine?.wakeUp() }
            disconnectForNetworkTransition(DefaultNetworkTransition.CHANGED)
        }, 2_000)
    }

    private fun disconnectForNetworkTransition(transition: DefaultNetworkTransition) {
        networkHandler.post {
            if (serviceDestroyed) return@post
            val sessionIds = ArrayList(activeJobs.keys)
            if (sessionIds.isEmpty()) {
                UiDebugLog.result(
                    "SessionService.networkTransition",
                    true,
                    "transition=$transition, activeSessions=0"
                )
                return@post
            }
            val cause = when (transition) {
                DefaultNetworkTransition.LOST -> "Network connection lost."
                DefaultNetworkTransition.CHANGED -> "Network route changed."
                DefaultNetworkTransition.VPN_STATE_CHANGED -> "VPN state changed."
            }
            sessionIds.forEach { sessionId ->
                // Mosh roams across networks by itself; closing it threw that away (and left the server running).
                if (activeConnections[sessionId]?.moshBinding != null) return@forEach
                // On a new route (another network, a VPN), transfer connections ran over the old one: cut them so
                // transfers reconnect now instead of after the stall timeout. A lost network may come back with the
                // same route and the same connections, so transfers just retry at once when it does; cutting
                // healthy connections on every "losing" notice (Samsung's Wi-Fi switching sends them while Wi-Fi
                // works) failed transfers that were fine.
                if (transition != DefaultNetworkTransition.LOST) {
                    activeTransfersFor(sessionId).forEach { it.engine?.cutConnections(cause.removeSuffix(".")) }
                    activeConnections[sessionId]?.transferPool?.dropAll()
                    // The file browser's helper connection ran over the old route: start it again on the new one.
                    stopFileBoost(sessionId)
                    // A Boost terminal reattaches over the new route by itself; this saves it waiting to notice.
                    boostShellFor(sessionId)?.kick()
                }
                activeTransfersFor(sessionId).forEach { it.engine?.wakeUp() }
                if (requestReconnect(sessionId, cause)) return@forEach
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = sessionId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "$cause Session disconnected."
                    )
                )
                stopSession(sessionId)
            }
            UiDebugLog.result(
                "SessionService.networkTransition",
                true,
                "transition=$transition, disconnectedSessions=${sessionIds.size}"
            )
        }
    }

    /**
     * Asks an active SSH or file browser session to reconnect. False when it can't (setting off, Mosh, or not
     * connected yet), so the caller falls back to closing it.
     */
    private fun requestReconnect(sessionId: String, reason: String): Boolean {
        if (!autoReconnectEnabled) return false
        if (sessionId in reconnectingSessions) return true
        val signal = reconnectSignals[sessionId] ?: return false
        reconnectingSessions.add(sessionId)
        signal.complete(reason)
        return true
    }

    private fun reconnectDelayMillis(attempt: Int): Long =
        RECONNECT_BACKOFF_MS.getOrElse(attempt - 1) { RECONNECT_BACKOFF_MS.last() }

    override fun onTimeout(startId: Int, fgsType: Int) {
        UiDebugLog.action(
            "SessionService.onTimeout",
            "startId=$startId, fgsType=$fgsType, activeSessions=${activeJobs.size}"
        )
        stopAllSessions()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundNotificationStarted = false
        stopSelf(startId)
        UiDebugLog.result("SessionService.onTimeout", true, "sessionsStopped=true")
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
        val useRuntimePasswordCache = !allowPasswordSave
        if (useRuntimePasswordCache && !passwordOverride.isNullOrBlank()) {
            runtimeSessionPasswords[sessionId] = passwordOverride
        }
        val useMoshTransport = host.useMosh && mode == ConnectionMode.SSH
        val needsPassword = host.preferredAuth != AuthMethod.IDENTITY
        val initialPassword = if (needsPassword) {
            when {
                !passwordOverride.isNullOrBlank() -> passwordOverride
                useRuntimePasswordCache -> runtimeSessionPasswords[sessionId]
                else -> runCatching { SecurityManager.getHostPassword(host.id) }.getOrNull()
            }
        } else {
            null
        }
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            val connectionTranscriptEnabled = AtomicBoolean(true)
            var client: SSHClient? = null
            var shellBinding: ShellBinding? = null
            var moshBinding: MoshBinding? = null
            var sftpBinding: SftpBinding? = null
            var activeForwardBindings: List<PortForwardBinding> = emptyList()
            var sessionHost = host
            // Plain SSH shells reconnect in place after the network drops or changes; the screen,
            // terminal contents, and the password that worked are kept for the new connection.
            // Plain SSH shells and the file browser reconnect in place; Mosh roams by itself.
            val reconnectable = !useMoshTransport
            var reconnectReason: String? = null
            var reconnectAttempt = 0
            var reusableTerminalEngine: TermuxTerminalEngine? = null
            var lastGoodPassword: String? = initialPassword
            // A login that needed answers only a person can give (a 2FA code) can't be repeated for transfer connections.
            val sawLoginChallenge = AtomicBoolean(false)
            var transferPool: SftpTransferPool? = null
            val osKnownAtStart = host.osMetadata is OsMetadata.Known
            while (true) {
            val attemptDeadlineMillis = System.currentTimeMillis() + CONNECTION_ATTEMPT_TIMEOUT_MS
            val attemptStartedAt = SystemClock.elapsedRealtime()
            val reconnectSignal = CompletableDeferred<String>()
            val attempt = runCatching {
                if (reconnectReason != null) {
                    updateSessionSnapshot(
                        sessionId, sessionHost, mode, SessionStatus.CONNECTING,
                        "$reconnectReason Waiting for the network..."
                    )
                    withTimeoutOrNull(RECONNECT_NETWORK_WAIT_MS) { networkAvailable.first { it } }
                    updateSessionSnapshot(
                        sessionId, sessionHost, mode, SessionStatus.CONNECTING,
                        "Reconnecting (attempt $reconnectAttempt of $MAX_RECONNECT_ATTEMPTS)..."
                    )
                    connectionTranscriptEnabled.set(true)
                } else {
                    updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Opening SSH connection...")
                }
                client = SshClientProvider.createClient(
                    this@SessionService,
                    sessionHost,
                    SessionLoggerFactory(
                        hostId = sessionId,
                        connectionTranscriptEnabled = connectionTranscriptEnabled::get,
                        diagnosticsEnabled = { diagnosticsEnabled }
                    ),
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
                        lastGoodPassword = authenticateWithPassword(
                            client = client!!,
                            host = host,
                            sessionId = sessionId,
                            mode = mode,
                            initialPassword = lastGoodPassword,
                            deadlineMillis = attemptDeadlineMillis,
                            allowPasswordSave = allowPasswordSave,
                            useRuntimePasswordCache = useRuntimePasswordCache,
                            onChallenge = { sawLoginChallenge.set(true) }
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
                            lastGoodPassword = authenticateWithPassword(
                                client = client!!,
                                host = host,
                                sessionId = sessionId,
                                mode = mode,
                                initialPassword = lastGoodPassword,
                                deadlineMillis = attemptDeadlineMillis,
                                allowPasswordSave = allowPasswordSave,
                                useRuntimePasswordCache = useRuntimePasswordCache,
                                onChallenge = { sawLoginChallenge.set(true) }
                            )
                        }
                    }
                }
                // The transcript explains failed connections; past login it only slows the rest down.
                connectionTranscriptEnabled.set(false)
                // A known OS is refreshed after the session is ready instead of delaying the shell.
                if (mode == ConnectionMode.SSH && !osKnownAtStart) {
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
                            // A Boost terminal kept through the reconnect carries on as it was; only SSH was new.
                            val kept = keptBoostShells.remove(sessionId)
                            shellBinding = if (kept != null && (kept.channel as? BoostTerminalChannel)?.alive == true) {
                                kept
                            } else {
                                kept?.let(::closeShellBinding)
                                updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting shell...")
                                openBoostShell(sessionId, sessionHost, client!!, terminalEmulation, reusableTerminalEngine)
                                    ?: openShell(
                                        sessionId,
                                        client!!,
                                        terminalEmulation,
                                        reusableTerminalEngine,
                                        attachTmux = sessionHost.attachTmux
                                    )
                            }
                            // An SSH shell's reader notices SSH dying; a Boost terminal's never does, so listen for it
                            // (port forwards need it back).
                            if (shellBinding?.channel is BoostTerminalChannel) {
                                client!!.transport.setDisconnectListener { reason, _ ->
                                    if (reason != DisconnectReason.BY_APPLICATION) {
                                        requestReconnect(sessionId, "Connection lost.")
                                    }
                                }
                            }
                        }
                    }
                    ConnectionMode.SFTP, ConnectionMode.SCP -> {
                        updateSessionSnapshot(
                            sessionId, sessionHost, mode, SessionStatus.CONNECTING,
                            if (mode == ConnectionMode.SFTP) "Opening SFTP subsystem..." else "Preparing file browser..."
                        )
                        val browserClient = client!!
                        sftpBinding = SftpBinding(browserClient.newSFTPClient())
                        // The browser's connection dying is otherwise only noticed at the next folder opened.
                        browserClient.transport.setDisconnectListener { reason, _ ->
                            if (reason != DisconnectReason.BY_APPLICATION) {
                                requestReconnect(sessionId, "Connection lost.")
                            }
                        }
                        // One pool for the session's life: transfers keep their connections while the browser's
                        // own connection reconnects.
                        if (transferPool == null) {
                            transferPool = newTransferPool(
                                sessionId = sessionId,
                                host = sessionHost,
                                password = { lastGoodPassword },
                                canLogInAgain = { !sawLoginChallenge.get() },
                                sessionClient = { client }
                            )
                        }
                        // A reconnect comes back to the folder the browser was showing, or last asked for.
                        val listingPath = if (reconnectReason == null) {
                            "."
                        } else {
                            lastRequestedListings[sessionId] ?: remoteDirectories.value[sessionId]?.path ?: "."
                        }
                        try {
                            refreshSftpDirectoryListing(
                                hostId = sessionId,
                                sftp = sftpBinding!!.client,
                                path = listingPath,
                                resolveSymbolicLinks = shouldResolveSftpLinkMetadata(mode),
                                generation = nextListingGeneration(sessionId),
                                client = browserClient
                            )
                        } catch (error: SFTPException) {
                            // That folder is gone or no longer ours to read: show the home folder instead.
                            if (listingPath == ".") throw error
                            refreshSftpDirectoryListing(
                                hostId = sessionId,
                                sftp = sftpBinding!!.client,
                                path = ".",
                                resolveSymbolicLinks = shouldResolveSftpLinkMetadata(mode),
                                generation = nextListingGeneration(sessionId),
                                client = browserClient
                            )
                        }
                    }
                }
                if (mode == ConnectionMode.SCP && reconnectReason == null) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = sessionId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "File browser ready. Browse remote files and download them."
                        )
                    )
                }
                val startupCommand = resolveStartupCommand(host.startupScript, availableSnippets)
                if (startupCommand.isNotBlank() && mode == ConnectionMode.SSH) {
                    runCatching {
                        client!!.startSession().use { shell ->
                            val cmd = shell.exec(startupCommand)
                            cmd.join(12, TimeUnit.SECONDS)
                            val output = runCatching {
                                collectCommandOutput(cmd, cmd.inputStream, REMOTE_OUTPUT_DRAIN_MS)
                            }.getOrNull()
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
                    availableForwards.filter { it.enabled && it.selectedHostId() == host.id }
                } else {
                    emptyList()
                }
                if (configuredForwards.isNotEmpty()) {
                    updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.CONNECTING, "Starting ${configuredForwards.size} port forward(s)...")
                    activeForwardBindings = activatePortForwards(
                        hostId = sessionId,
                        client = client!!,
                        sessionHost = sessionHost,
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
                    portForwardBindings = activeForwardBindings,
                    transferPool = transferPool
                )
                // A mosh-client that exited before the session was registered (during the startup script or port
                // forwards) found nothing to update, leaving a dead session marked ready.
                moshBinding?.session?.takeIf { !it.isRunning }?.let { handleMoshClientFinished(sessionId, it) }
                val modeLabel = when (mode) {
                    ConnectionMode.SSH -> if (useMoshTransport) "Mosh session ready" else "Interactive shell session ready"
                    ConnectionMode.SFTP -> "SFTP browser ready"
                    ConnectionMode.SCP -> "SFTP ready"
                }
                updateSessionSnapshot(sessionId, sessionHost, mode, SessionStatus.ACTIVE, modeLabel)
                metrics.recordConnect(
                    kind = when {
                        mode == ConnectionMode.SSH && useMoshTransport -> "mosh"
                        mode == ConnectionMode.SSH -> "terminal"
                        mode == ConnectionMode.SCP -> "browser"
                        else -> "console"
                    },
                    millis = SystemClock.elapsedRealtime() - attemptStartedAt,
                    reconnect = reconnectReason != null
                )
                if (reconnectReason == null) serviceScope.launch { SettingsStore.recordSuccessfulSession() }
                // Log in for transfers now, so the first one starts at once.
                transferPool?.let { pool -> serviceScope.launch { pool.warmUp() } }
                if (mode != ConnectionMode.SSH) {
                    stopFileBoost(sessionId)
                    startFileBoost(sessionId, force = true)
                }
                // Reconnected: the server answers again, so transfers waiting out a retry delay go now.
                if (reconnectReason != null) activeTransfersFor(sessionId).forEach { it.engine?.wakeUp() }
                TelemetryInitializer.breadcrumb("session active mode=$mode mosh=$useMoshTransport tmux=${sessionHost.attachTmux}")
                TelemetryInitializer.setState("open_sessions", activeJobs.size.toString())
                UiDebugLog.result("startSession", true, "sessionId=$sessionId, mode=$mode")
                reconnectAttempt = 0
                reconnectingSessions.remove(sessionId)
                if (reconnectable) {
                    reconnectSignals[sessionId] = reconnectSignal
                }
                val refreshOs = if (mode == ConnectionMode.SSH) osKnownAtStart else !osKnownAtStart
                if (refreshOs && currentCoroutineContext().isActive) {
                    detectRemoteOsMetadata(sessionId, client!!)?.let { detected ->
                        val activeConnection = activeConnections[sessionId]
                        if (
                            sessionHost.osMetadata != detected &&
                            activeConnection?.client === client &&
                            currentCoroutineContext().isActive
                        ) {
                            sessionHost = sessionHost.copy(osMetadata = detected)
                            var connectionUpdated = false
                            activeConnections.computeIfPresent(sessionId) { _, current ->
                                if (current.client === client) {
                                    connectionUpdated = true
                                    current.copy(host = sessionHost)
                                } else {
                                    current
                                }
                            }
                            if (!connectionUpdated || !currentCoroutineContext().isActive) {
                                return@let
                            }
                            updateSessionSnapshot(
                                sessionId,
                                sessionHost,
                                mode,
                                SessionStatus.ACTIVE,
                                modeLabel
                            )
                            SessionLogBus.emit(
                                SessionLogBus.Entry(
                                    hostId = sessionId,
                                    level = SessionLogBus.LogLevel.INFO,
                                    message = "Detected remote OS: ${detected.toSummaryLabel()}"
                                )
                            )
                        }
                    }
                }

                // Keep the connection until the user stops it, or until it needs a reconnect.
                reconnectSignal.await()
            }
            reconnectSignals.remove(sessionId, reconnectSignal)
            val retryReason: String? = attempt.fold(
                onSuccess = { reason -> reason },
                onFailure = { error ->
                    // While a Boost terminal lives, SSH (needed only for port forwards then) keeps trying.
                    val retryable = reconnectReason != null &&
                        error !is CancellationException &&
                        error.connectionFailureKind()?.isNetwork == true &&
                        (reconnectAttempt < MAX_RECONNECT_ATTEMPTS || boostShellFor(sessionId)?.alive == true)
                    if (retryable) reconnectReason else null
                }
            )
            if (retryReason != null && autoReconnectEnabled && currentCoroutineContext().isActive) {
                reconnectingSessions.add(sessionId)
                reusableTerminalEngine = shellBinding?.terminalEngine ?: reusableTerminalEngine
                // A live Boost terminal doesn't need SSH: keep it, so the screen stays usable and its shell carries on.
                shellBinding?.takeIf { (it.channel as? BoostTerminalChannel)?.alive == true }?.let { keptBoostShells[sessionId] = it }
                activeConnections.remove(sessionId)
                shellBinding?.takeUnless { keptBoostShells[sessionId] === it }?.let(::closeShellBinding)
                runCatching { sftpBinding?.client?.close() }
                stopFileBoost(sessionId)
                runCatching { activeForwardBindings.forEach { closePortForwardBinding(it) } }
                runCatching { client?.socket?.close() }
                runCatching { client?.disconnect() }
                client = null
                shellBinding = null
                sftpBinding = null
                activeForwardBindings = emptyList()
                reconnectAttempt += 1
                reportNetworkIssue(
                    if (reconnectAttempt == 1) "connection_lost" else "reconnect_failed",
                    sessionId,
                    attempt.exceptionOrNull(),
                    mapOf("reason" to retryReason, "attempt" to reconnectAttempt.toString())
                )
                reconnectReason = retryReason
                TelemetryInitializer.breadcrumb("session reconnect attempt=$reconnectAttempt mode=$mode")
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = sessionId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "$retryReason Reconnecting..."
                    )
                )
                updateSessionNotifications()
                delay(reconnectDelayMillis(reconnectAttempt))
                continue
            }
            reconnectingSessions.remove(sessionId)
            attempt.onFailure { e ->
                if (e !is CancellationException) {
                    val kind = e.connectionFailureKind()
                    TelemetryInitializer.breadcrumb("session failed mode=$mode kind=${kind ?: "unclassified"}")
                    if (kind == null) {
                        reportUnexpected(
                            "session_failed",
                            sessionId,
                            e,
                            context = mapOf(
                                "session_mode" to mode.name,
                                "session_auth" to host.preferredAuth.name,
                                "session_mosh" to useMoshTransport.toString(),
                                "session_reconnecting" to (reconnectReason != null).toString()
                            ),
                            host = host
                        )
                    }
                    clearHostKeyPromptsForHost(sessionId, trust = false)
                    clearPasswordPromptsForHost(sessionId, password = null)
                    val statusMessage = e.message ?: "Connection failed"
                    updateSessionSnapshot(
                        sessionId = sessionId,
                        host = sessionHost,
                        mode = mode,
                        status = SessionStatus.ERROR,
                        message = statusMessage,
                        failureKind = e.connectionFailureKind()
                    )
                    UiDebugLog.error("startSession", e, "sessionId=$sessionId, mode=$mode")
                    UiDebugLog.result("startSession", false, "sessionId=$sessionId, mode=$mode")
                }
            }
            break
            }
            cancelFileTransfer(sessionId)
            shellBinding?.let(::closeShellBinding)
            keptBoostShells.remove(sessionId)?.let(::closeShellBinding)
            runCatching { moshBinding?.inputQueue?.close() }
            runCatching { moshBinding?.inputWriterJob?.cancel() }
            runCatching { moshBinding?.let { shutDownMosh(client, it) } }
            runCatching { sftpBinding?.client?.close() }
            runCatching { transferPool?.close() }
            runCatching { activeForwardBindings.forEach { closePortForwardBinding(it) } }
            runCatching { client?.disconnect() }
            activeConnections.remove(sessionId)
            transferQueues.remove(sessionId)
            listingGenerations.remove(sessionId)
            lastRequestedListings.remove(sessionId)
            bulkLinkLookupUnavailable.remove(sessionId)
            stopFileBoost(sessionId)
            fileBoostLastStart.remove(sessionId)
            clearHostKeyPromptsForHost(sessionId, trust = false)
            clearPasswordPromptsForHost(sessionId, password = null)
            clearRuntimeSessionPassword(sessionId)
            clearShellOutputForHost(sessionId)
            clearRemoteDirectoryForHost(sessionId)
            clearFileTransferProgress(sessionId)
        }
        val existingJob = activeJobs.putIfAbsent(sessionId, job)
        if (existingJob != null) {
            job.cancel()
            UiDebugLog.result("startSession", false, "already-active sessionId=$sessionId")
            return
        }
        job.invokeOnCompletion { completionError ->
            keptBoostShells.remove(sessionId)?.let(::closeShellBinding)
            boostTerminalStates.remove(sessionId)
            if (activeJobs.remove(sessionId, job)) {
                activeConnections.remove(sessionId)
                clearRuntimeSessionPassword(sessionId)
                clearFileTransferProgress(sessionId)
                updateSessionNotifications()
                if (completionError is CancellationException) {
                    removeSessionSnapshot(sessionId)
                }
            }
        }
        job.start()
        updateSessionNotifications()
    }

    fun stopSession(hostId: String) {
        UiDebugLog.action("stopSession", "hostId=$hostId")
        // Closing the session ends a Boost terminal's shell on the server too.
        keptBoostShells.remove(hostId)?.let(::closeShellBinding)
        reconnectSignals.remove(hostId)
        reconnectingSessions.remove(hostId)
        cancelFileTransfer(hostId)
        clearHostKeyPromptsForHost(hostId, trust = false)
        clearPasswordPromptsForHost(hostId, password = null)
        clearRuntimeSessionPassword(hostId)
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
        clearFileTransferProgress(hostId)
        shellReadSequence.remove(hostId)
        shellWriteSequence.remove(hostId)
        UiDebugLog.result("stopSession", true, "hostId=$hostId")
    }

    fun hasActivePortForwards(): Boolean =
        activeConnections.values.any { it.portForwardBindings.isNotEmpty() }

    @Volatile
    private var backgroundStopJob: Job? = null

    /**
     * Stops all sessions once the app has been in the background for [timeoutMs]. Lives in the
     * service rather than the activity so it survives the task being swiped away, and polls
     * elapsedRealtime because coroutine delays do not advance while the device sleeps.
     */
    fun scheduleBackgroundSessionStop(timeoutMs: Long) {
        backgroundStopJob?.cancel()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        backgroundStopJob = serviceScope.launch {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                delay(remaining.coerceAtMost(BACKGROUND_STOP_POLL_MS))
            }
            withContext(Dispatchers.Main) {
                if (!hasActivePortForwards()) {
                    stopAllSessions()
                    UiDebugLog.result("SessionService.backgroundSessionTimeout", true, "stopNow=timer timeoutMs=$timeoutMs")
                }
            }
        }
    }

    fun cancelBackgroundSessionStop() {
        backgroundStopJob?.cancel()
        backgroundStopJob = null
    }

    /**
     * Pings each open SSH shell when the app comes back, and drops the connection of any that doesn't answer, which
     * reconnects it (or ends it, with auto-reconnect off). A connection can die silently while the phone sleeps; the
     * keepalive notices too, but only after a minute of the user typing into nothing.
     */
    fun checkConnectionsAlive() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLivenessCheckAtMs < LIVENESS_CHECK_MIN_INTERVAL_MS) return
        lastLivenessCheckAtMs = now
        activeConnections.keys.forEach(::probeConnection)
    }

    /**
     * Typing into a connection that died silently went nowhere until the keepalive gave up, over a minute later. A
     * shell answers input at once (the echo, a new prompt), so when nothing at all comes back within
     * [UNANSWERED_INPUT_GRACE_MS], the connection is probed; one that doesn't answer reconnects. Input that gets no
     * echo (a password prompt) costs one small request the server answers.
     */
    private fun checkUnansweredInput(sessionId: String) {
        if (sessionId in reconnectingSessions) return
        if (unansweredInputChecks[sessionId]?.isActive == true) return
        val sentAt = SystemClock.elapsedRealtime()
        unansweredInputChecks[sessionId] = serviceScope.launch {
            delay(UNANSWERED_INPUT_GRACE_MS)
            if ((lastShellOutputAtMs[sessionId] ?: Long.MIN_VALUE) >= sentAt) return@launch
            probeConnection(sessionId)
        }
    }

    /** Pings one open SSH shell's server, and drops the connection if it doesn't answer. */
    private fun probeConnection(sessionId: String) {
        val connection = activeConnections[sessionId] ?: return
        val client = connection.client ?: return
        if (connection.mode != ConnectionMode.SSH || connection.shellBinding == null) return
        if (sessionId in reconnectingSessions) return
        val probeStartedAt = SystemClock.elapsedRealtime()
        serviceScope.launch {
            val alive = withTimeoutOrNull(LIVENESS_PROBE_TIMEOUT_MS) {
                // Sending can block on the transport's write lock, so it runs apart and the timeout covers it.
                val reply = CompletableDeferred<Promise<SSHPacket, ConnectionException>?>()
                launch(Dispatchers.IO) {
                    reply.complete(
                        runCatching {
                            client.connection.sendGlobalRequest("keepalive@openssh.com", true, ByteArray(0))
                        }.getOrNull()
                    )
                }
                val promise = reply.await() ?: return@withTimeoutOrNull false
                // Polled rather than awaited: SSHJ logs an error when an awaited request is refused, and
                // OpenSSH refuses keepalives. Once fulfilled, retrieve() returns or throws without waiting.
                while (!promise.isFulfilled) delay(LIVENESS_PROBE_POLL_MS)
                runCatching { promise.retrieve() }
                    .fold(onSuccess = { true }, onFailure = ::isGlobalRequestRefusal)
            } ?: false
            // Output that arrived meanwhile is an answer too: on a laggy network the keepalive's reply can queue
            // behind it, and dropping that connection was what "Socket closed" was on bad links.
            val heardMeanwhile = (lastShellOutputAtMs[sessionId] ?: Long.MIN_VALUE) >= probeStartedAt
            if (alive || heardMeanwhile || activeConnections[sessionId]?.client !== client) return@launch
            reportNetworkIssue("no_answer", sessionId, null, mapOf("waited_ms" to LIVENESS_PROBE_TIMEOUT_MS.toString()))
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.WARN,
                    message = "The server stopped responding."
                )
            )
            dropDeadConnection(client)
        }
    }

    /** Closes the socket under a connection that stopped responding. Unlike closing the shell or disconnecting, this
     * can't block on the dead link; the shell reader then fails, which reconnects or ends the session. */
    /** Tests: drops the session's SSH connection the way a dead network does. */
    @androidx.annotation.VisibleForTesting
    internal fun dropSshConnectionForTests(sessionId: String) {
        activeConnections[sessionId]?.client?.let(::dropDeadConnection)
    }

    fun connectionLatencyFlow(): StateFlow<Map<String, ConnectionLatency>> = connectionLatencies.asStateFlow()

    /**
     * Every few seconds, times each session's round trip: a Boost terminal by its own pings (the path its keys
     * take), the rest by an SSH keepalive request, which OpenSSH answers at once. Mosh sessions are left out: there
     * is no SSH connection under them to time, and mosh-client shows its own connection state.
     */
    private suspend fun sampleLatencies() = coroutineScope {
        while (isActive) {
            val sessions = (activeConnections.keys + keptBoostShells.keys).toSet()
            latencySamples.keys.retainAll(sessions)
            sessions.map { sessionId ->
                async {
                    val boost = boostShellFor(sessionId)
                    val connection = activeConnections[sessionId]
                    when {
                        boost != null -> sessionId to ("Boost ${boost.transport}" to boost.rttMillis())
                        connection?.moshBinding != null || connection?.client == null || sessionId in reconnectingSessions -> null
                        else -> sessionId to ("SSH" to timeSshRoundTrip(connection.client))
                    }
                }
            }.awaitAll().filterNotNull().forEach { (sessionId, sample) ->
                latencySamples.getOrPut(sessionId) { LatencySamples() }.add(sample.second)
                sample.second?.let { metrics.recordLatency(sample.first, it) }
            }
            connectionLatencies.value = buildMap {
                latencySamples.forEach { (sessionId, samples) ->
                    val via = boostShellFor(sessionId)?.let { "Boost ${it.transport}" } ?: "SSH"
                    samples.summary(via)?.let { put(sessionId, it) }
                }
            }
            delay(LATENCY_SAMPLE_INTERVAL_MS)
        }
    }

    /** Milliseconds for an SSH keepalive request to be answered, or null without an answer within the probe timeout. */
    private suspend fun timeSshRoundTrip(client: SSHClient): Long? = withTimeoutOrNull(LIVENESS_PROBE_TIMEOUT_MS) {
        val start = System.nanoTime()
        val promise = withContext(Dispatchers.IO) {
            runCatching { client.connection.sendGlobalRequest("keepalive@openssh.com", true, ByteArray(0)) }.getOrNull()
        } ?: return@withTimeoutOrNull null
        // Polled, as in probeConnection: OpenSSH refuses keepalives, and an awaited refusal is logged as an error.
        while (!promise.isFulfilled) delay(LATENCY_POLL_MS)
        (System.nanoTime() - start) / 1_000_000
    }

    private fun dropDeadConnection(client: SSHClient) {
        runCatching { client.socket?.close() }
    }

    fun stopAllSessions() {
        UiDebugLog.action("stopAllSessions", "count=${activeJobs.size}")
        val ids = ArrayList(activeJobs.keys)
        ids.forEach { stopSession(it) }
        UiDebugLog.result("stopAllSessions", true)
    }

    /** Stops a terminal's I/O loops and ends its remote end. */
    private fun closeShellBinding(binding: ShellBinding) {
        runCatching { binding.resizeQueue.close() }
        runCatching { binding.inputQueue.close() }
        runCatching { binding.resizeWriterJob.cancel() }
        runCatching { binding.inputWriterJob.cancel() }
        runCatching { binding.outputReaderJob.cancel() }
        runCatching { binding.channel.close() }
    }

    /** The session's Boost terminal, whether its SSH connection is up or reconnecting. */
    private fun boostShellFor(sessionId: String): BoostTerminalChannel? =
        (activeConnections[sessionId]?.shellBinding ?: keptBoostShells[sessionId])?.channel as? BoostTerminalChannel

    private fun kickBoostShells() {
        (activeConnections.keys + keptBoostShells.keys).toSet().forEach { boostShellFor(it)?.kick() }
    }

    private fun closeConnectionResources(hostId: String, connection: ActiveConnection, trigger: String) {
        measureOperation("closeConnectionResources/$trigger", hostId, thresholdMs = 200L) {
            connection.shellBinding?.let(::closeShellBinding)
            runCatching { connection.moshBinding?.inputQueue?.close() }
            runCatching { connection.moshBinding?.inputWriterJob?.cancel() }
            runCatching { connection.moshBinding?.let { shutDownMosh(connection.client, it) } }
            runCatching { connection.sftpBinding?.client?.close() }
            runCatching { connection.transferPool?.close() }
            stopFileBoost(hostId)
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
                        val output = runCatching {
                            collectCommandOutput(command, command.inputStream, REMOTE_OUTPUT_DRAIN_MS)
                        }.getOrNull()
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

    fun sessionsFlow(): StateFlow<List<SessionSnapshot>> = sessionSnapshotsState
    fun activeSessionIdsForRouting(): Set<String> = HashSet(activeJobs.keys)
    fun hostKeyPromptsFlow(): StateFlow<List<HostKeyPrompt>> = hostKeyPromptsState
    fun passwordPromptsFlow(): StateFlow<List<PasswordPrompt>> = passwordPromptsState
    fun shellOutputFlow(): StateFlow<Map<String, String>> = shellOutputState
    fun remoteDirectoryFlow(): StateFlow<Map<String, RemoteDirectorySnapshot>> = remoteDirectoriesState
    fun fileTransferProgressFlow(): StateFlow<Map<String, FileTransferProgress>> = fileTransferProgressState
    fun resolveTerminalEmulator(hostId: String): TerminalEmulator? {
        val connection = activeConnections[hostId]
            ?: return keptBoostShells[hostId]?.terminalEngine?.emulator()
        return connection.shellBinding?.terminalEngine?.emulator()
            ?: connection.moshBinding?.session?.emulator
    }

    fun resolveMoshTerminalEmulator(hostId: String): TerminalEmulator? {
        return resolveTerminalEmulator(hostId)
    }

    fun respondToHostKeyPrompt(promptId: String, trust: Boolean) {
        hostKeyPromptWaiters.remove(promptId)?.complete(trust)
        hostKeyPrompts.update { prompts -> prompts.filterNot { it.id == promptId } }
    }

    fun respondToPasswordPrompt(promptId: String, password: String?, savePassword: Boolean) {
        val prompt = passwordPrompts.value.firstOrNull { it.id == promptId }
        if (prompt?.allowSave == false && prompt.isChallenge.not() && !password.isNullOrBlank()) {
            runtimeSessionPasswords[prompt.hostId] = password
        }
        passwordPromptWaiters.remove(promptId)?.complete(
            PasswordResponse(
                password = password,
                savePassword = savePassword
            )
        )
        passwordPrompts.update { prompts -> prompts.filterNot { it.id == promptId } }
    }

    fun getRuntimeSessionPassword(sessionId: String): String? = runtimeSessionPasswords[sessionId]

    fun moveRuntimeSessionPassword(fromSessionId: String, toSessionId: String) {
        if (fromSessionId == toSessionId) return
        val password = runtimeSessionPasswords.remove(fromSessionId) ?: return
        runtimeSessionPasswords[toSessionId] = password
    }

    fun clearRuntimeSessionPassword(sessionId: String) {
        runtimeSessionPasswords.remove(sessionId)
    }

    fun clearAllRuntimeSessionPasswords() {
        runtimeSessionPasswords.clear()
    }

    fun sendShellInput(hostId: String, text: String) {
        if (text.isEmpty()) return
        sendShellBytes(hostId, text.toByteArray(StandardCharsets.UTF_8))
    }

    fun sendShellBytes(hostId: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val connection = activeConnections[hostId]
        if (connection == null) {
            // SSH is reconnecting under a Boost terminal, which takes input meanwhile.
            val kept = keptBoostShells[hostId]
            if (kept == null || !kept.inputQueue.trySend(bytes.copyOf()).isSuccess) {
                UiDebugLog.result("sendShellBytes", false, "session-not-active hostId=$hostId")
            }
            return
        }
        connection.moshBinding?.let { mosh ->
            val reservedCapacity = if (bytes.size > MOSH_INTERACTIVE_INPUT_MAX_BYTES) {
                MOSH_INTERACTIVE_INPUT_RESERVE_BYTES
            } else {
                0
            }
            if (!mosh.inputQueue.tryEnqueue(bytes, reservedCapacity)) {
                UiDebugLog.result("sendShellBytes", false, "mosh-input-queue-full-or-closed hostId=$hostId")
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Mosh input buffer is full or closed; rejected ${bytes.size} byte(s). " +
                            "Retry after the session reconnects."
                    )
                )
            }
            return
        }
        val shellBinding = connection.shellBinding
        if (connection.mode != ConnectionMode.SSH || shellBinding == null) {
            UiDebugLog.result("sendShellBytes", false, "shell-not-available hostId=$hostId")
            return
        }
        if (shellBinding.inputQueue.trySend(bytes.copyOf()).isSuccess) {
            checkUnansweredInput(hostId)
        } else {
            UiDebugLog.result("sendShellBytes", false, "input-queue-closed hostId=$hostId")
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.WARN,
                    message = "Shell input queue is closed; dropped ${bytes.size} byte(s)."
                )
            )
        }
    }

    fun resizeShell(hostId: String, columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        val next = columns to rows
        // Remembered even while the session is reconnecting: the next shell's PTY is opened at this size.
        val previous = shellSizes.put(hostId, next)
        val connection = activeConnections[hostId] ?: run {
            keptBoostShells[hostId]?.takeIf { previous != next }?.resizeQueue?.trySend(next)
            return
        }
        if (connection.mode != ConnectionMode.SSH) return
        if (previous == next) return
        connection.moshBinding?.let { mosh ->
            serviceScope.launch(Dispatchers.Main) {
                runCatching {
                    mosh.session.updateSize(columns, rows, 0, 0)
                }
            }
            return
        }
        val shellBinding = connection.shellBinding ?: return
        if (!shellBinding.resizeQueue.trySend(next).isSuccess) {
            UiDebugLog.result("resizeShell", false, "resize-queue-closed hostId=$hostId")
        }
    }

    /**
     * Lists [path] for the browser. A newer request supersedes this one: its result and errors are dropped, so
     * tapping through folders quickly never shows an older folder last.
     */
    fun listSftpDirectory(hostId: String, path: String) {
        val targetPath = path.trim().ifBlank { "." }
        val resolveSymbolicLinks =
            // The connection registers only once the session is ready: a listing asked for while it opens goes by
            // the mode the session was started in (it used to be taken for the console's, without link details).
            shouldResolveSftpLinkMetadata(
                activeConnections[hostId]?.mode ?: sessionSnapshots.value.firstOrNull { it.hostId == hostId }?.mode
            )
        val generation = nextListingGeneration(hostId)
        lastRequestedListings[hostId] = targetPath
        // A folder the browser saw lately shows at once; the listing below then replaces it with what's there now.
        val cached = recentListings[hostId]?.get(targetPath)?.takeIf { resolveSymbolicLinks }
        cached?.let {
            setRemoteDirectorySnapshot(hostId, it.copy(refreshToken = SystemClock.elapsedRealtimeNanos(), fromCache = true))
        }
        serviceScope.launch {
            awaitBrowserConnection(hostId)
            val listed = runWithSftpClient(hostId) { sftp ->
                try {
                    refreshSftpDirectoryListing(
                        hostId = hostId,
                        sftp = sftp,
                        path = targetPath,
                        resolveSymbolicLinks = resolveSymbolicLinks,
                        generation = generation,
                        // Over a cached listing, the first entries of a big folder would look like a shrunken one.
                        showPartial = cached == null
                    )
                } catch (error: Exception) {
                    // The connection died under the listing: reconnecting lists this folder again.
                    if (error.isFatalSftpTransportFailure() && requestReconnect(hostId, "Connection lost.")) return@runWithSftpClient
                    if (isLatestListing(hostId, generation)) throw error
                }
            }
            if (!listed && isLatestListing(hostId, generation) && hostId !in reconnectingSessions) {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.ERROR,
                        message = "Directory listing failed for $targetPath"
                    )
                )
                UiDebugLog.result("listSftpDirectory", false, "sftp-not-active hostId=$hostId")
            }
        }
    }

    /**
     * Starts file boost for a file browser session when the setting is on: in the background, so the browser works
     * over SFTP meanwhile. [force] skips the wait between attempts.
     */
    private fun startFileBoost(sessionId: String, force: Boolean = false) {
        val settings = sftpTransferSettings
        if (!settings.boost || !BoostHelper.clientAvailable) return
        val connection = activeConnections[sessionId] ?: return
        if (connection.mode == ConnectionMode.SSH) return
        val ssh = connection.client ?: return
        if (fileBoostStarts[sessionId]?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - (fileBoostLastStart[sessionId] ?: Long.MIN_VALUE / 2) < FILE_BOOST_RETRY_MS) return
        fileBoostLastStart[sessionId] = now
        fileBoostStarts[sessionId] = serviceScope.launch {
            val result = runCatching {
                ssh.newSFTPClient().use { sftp -> BoostHelper.start(this@SessionService, ssh, sftp, settings.boostTcpFallback, settings.boostPortRange) }
            }
            result.onSuccess { boost ->
                if (activeConnections[sessionId]?.client !== ssh || !sftpTransferSettings.boost) {
                    boost.close()
                    return@onSuccess
                }
                fileBoosts.put(sessionId, boost)?.close()
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = sessionId,
                        level = SessionLogBus.LogLevel.INFO,
                        message = "Boost on (${boost.transport.uppercase()})."
                    )
                )
                metrics.recordBoost("files", boost.transport.lowercase())
            }.onFailure { error ->
                val reason = (error as? BoostUnavailable)?.message ?: (error.message ?: error.javaClass.simpleName)
                Log.i(FILE_BOOST_TAG, "Boost unavailable for $sessionId", if (BuildConfig.DEBUG) error else null)
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = sessionId,
                        level = SessionLogBus.LogLevel.INFO,
                        message = "Boost isn't available: $reason. Using SFTP."
                    )
                )
                metrics.recordBoost("files", com.majordaftapps.sshpeaches.app.diagnostics.PerformanceMetrics.boostReason(reason))
            }
        }
    }

    private fun stopFileBoost(sessionId: String) {
        fileBoostStarts.remove(sessionId)?.cancel()
        fileBoosts.remove(sessionId)?.let { boost -> serviceScope.launch { boost.close() } }
    }

    /** The session's working file boost, if any; a broken one is dropped and a new one started for next time. */
    private fun fileBoostFor(sessionId: String): BoostHelper? {
        val boost = fileBoosts[sessionId] ?: return null
        if (!boost.broken) return boost
        if (fileBoosts.remove(sessionId, boost)) serviceScope.launch { boost.close() }
        startFileBoost(sessionId)
        return null
    }

    /** A boost operation failed: unless the helper refused the request itself, the connection is suspect. */
    private fun fileBoostFailed(sessionId: String, boost: BoostHelper, error: Throwable, what: String) {
        if (error.message?.startsWith(com.majordaftapps.sshpeaches.boost.boostclient.Boostclient.RemoteErrorPrefix) != true) {
            boost.markBroken()
        }
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = sessionId,
                level = SessionLogBus.LogLevel.INFO,
                message = "Boost $what failed (${error.message ?: error.javaClass.simpleName}); using SFTP."
            )
        )
        reportNetworkIssue("boost_$what", sessionId, error)
    }

    /**
     * Downloads [source] over file boost into [channel] (whose descriptor is [descriptor]), resuming from and saving
     * to [partial] like the SFTP download, so either can carry on from the other.
     */
    private fun boostDownload(
        boost: BoostHelper,
        source: String,
        size: Long,
        mtime: Long,
        partial: SftpPartialDownload,
        channel: java.nio.channels.FileChannel,
        descriptor: java.io.FileDescriptor,
        progress: (Long) -> Unit,
        cancelled: () -> Boolean
    ) {
        val (resolved, _) = boost.allowRead(source)
        try {
            val saved = partial.load(source, size, mtime)
            if (saved == null) partial.deleteState()
            val queue = SftpChunkQueue(saved ?: SftpChunkQueue.plan(size, boostStreams()))
            val current = channel.size()
            if (current > size) channel.truncate(size) else if (current < size && size > 0L) {
                channel.write(java.nio.ByteBuffer.allocate(1), size - 1)
            }
            partial.save(source, size, mtime, queue.snapshot())
            runBoostTransfer(queue, progress, cancelled, onCheckpoint = { partial.save(source, size, mtime, it) }) { chunks, onChunk, stop ->
                android.os.ParcelFileDescriptor.dup(descriptor).use { dup ->
                    boost.download(resolved, dup.fd, chunks, boostStreams(), onChunk, stop)
                }
            }
            partial.deleteState()
        } catch (error: Throwable) {
            if (cancelled() || error is FileTransferCancelledException) partial.delete()
            throw error
        } finally {
            boost.forget(resolved)
        }
    }

    private fun boostStreams() = BoostHelper.streamsForTests.takeIf { it > 0 } ?: FILE_BOOST_STREAMS

    /** Uploads [local] to [destination] over file boost. */
    private fun boostUpload(boost: BoostHelper, destination: String, local: UploadSource, progress: (Long) -> Unit, cancelled: () -> Boolean) {
        val resolved = boost.allowWrite(destination, local.permissions)
        try {
            val queue = SftpChunkQueue(SftpChunkQueue.plan(local.size, boostStreams()))
            runBoostTransfer(queue, progress, cancelled, onCheckpoint = {}) { chunks, onChunk, stop ->
                android.os.ParcelFileDescriptor.dup(local.descriptor).use { dup ->
                    boost.upload(resolved, dup.fd, chunks, boostStreams(), onChunk, stop)
                }
            }
        } finally {
            boost.forget(resolved)
        }
    }

    /**
     * Runs one boost transfer of [queue]'s chunks: progress and checkpoints from the helper's per-chunk reports
     * (which arrive on Go's threads, so they're serialized here), cancellation polled.
     */
    private fun runBoostTransfer(
        queue: SftpChunkQueue,
        progress: (Long) -> Unit,
        cancelled: () -> Boolean,
        onCheckpoint: (List<SftpPartialDownload.RangeState>) -> Unit,
        run: (chunks: String, onChunk: (Int, Long) -> Unit, stop: () -> Boolean) -> Unit
    ) {
        val chunks = org.json.JSONArray(queue.snapshot().map { org.json.JSONArray(listOf(it.start, it.end, it.done)) }).toString()
        val lock = Any()
        var callbackError: Throwable? = null
        var lastCheckpoint = 0L
        run(chunks, { index, done ->
            synchronized(lock) {
                queue.advance(index, done)
                try {
                    progress(queue.bytesDone())
                } catch (error: Throwable) {
                    callbackError = error
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastCheckpoint >= 500L) {
                    lastCheckpoint = now
                    onCheckpoint(queue.snapshot())
                }
            }
        }, { cancelled() || synchronized(lock) { callbackError != null } })
        onCheckpoint(queue.snapshot())
        synchronized(lock) { callbackError }?.let { throw it }
        if (cancelled()) throw FileTransferCancelledException()
        if (!queue.isComplete()) throw IOException("Boost stopped before the whole file was moved")
    }

    private suspend fun awaitTransferPool(hostId: String): SftpTransferPool {
        awaitBrowserConnection(hostId)
        return activeConnections[hostId]?.transferPool ?: throw IOException("SFTP session is not connected.")
    }

    /** Waits while the session reconnects, so an operation started then runs on the new connection, not fails. */
    private suspend fun awaitBrowserConnection(hostId: String) {
        withTimeoutOrNull(RECONNECT_NETWORK_WAIT_MS + CONNECTION_ATTEMPT_TIMEOUT_MS) {
            while (hostId in reconnectingSessions || activeConnections[hostId] == null && activeJobs.containsKey(hostId)) {
                delay(200)
            }
        }
    }

    private fun beginFileTransfer(
        hostId: String,
        mode: ConnectionMode,
        direction: FileTransferDirection,
        fileName: String,
        sourceLabel: String,
        destinationLabel: String,
        totalBytes: Long? = null
    ): ActiveFileTransfer {
        val operationId = UUID.randomUUID().toString()
        val progress = FileTransferProgress(
            sessionId = hostId,
            mode = mode,
            direction = direction,
            fileName = fileName.ifBlank { "file" },
            sourceLabel = sourceLabel,
            destinationLabel = destinationLabel,
            operationId = operationId,
            totalBytes = totalBytes?.takeIf { it >= 0L }
        )
        val transfer = ActiveFileTransfer(
            sessionId = hostId,
            operationId = operationId,
            operationLabel = "${mode.name} ${direction.name.lowercase()}",
            initialProgress = progress
        )
        synchronized(fileTransferStateLock) {
            activeFileTransfers.getOrPut(hostId) { CopyOnWriteArrayList() }.add(transfer)
        }
        publishDisplayedTransfer(hostId)
        return transfer
    }

    private fun removeActiveTransfer(transfer: ActiveFileTransfer) {
        synchronized(fileTransferStateLock) {
            val list = activeFileTransfers[transfer.sessionId] ?: return
            list.remove(transfer)
            if (list.isEmpty()) {
                activeFileTransfers.remove(transfer.sessionId, list)
            }
        }
    }

    private fun activeTransfersFor(sessionId: String): List<ActiveFileTransfer> {
        return activeFileTransfers[sessionId]?.toList().orEmpty()
    }

    /** The transfer a session shows: the one running, or else the next in its queue. */
    private fun displayedTransfer(sessionId: String): ActiveFileTransfer? {
        val transfers = activeTransfersFor(sessionId)
        return transfers.firstOrNull { it.running } ?: transfers.firstOrNull()
    }

    private fun publishDisplayedTransfer(sessionId: String) {
        val transfers = activeTransfersFor(sessionId)
        publishTransferList(sessionId)
        val shown = transfers.firstOrNull { it.running } ?: transfers.firstOrNull() ?: return
        setFileTransferProgress(sessionId, shown.latestProgress.copy(queuedTransfers = transfers.size - 1))
    }

    /** Publishes [sessionId]'s transfers for the pane: the running one, those waiting in order, then the finished. */
    private fun publishTransferList(sessionId: String) {
        synchronized(fileTransferStateLock) {
            val active = activeTransfersFor(sessionId).sortedBy { if (it.running) 0 else 1 }
                .map { it.latestProgress.copy(waiting = !it.running) }
            val list = active + finishedTransfers[sessionId].orEmpty()
            transferLists.update { current -> if (list.isEmpty()) current - sessionId else current + (sessionId to list) }
        }
    }

    fun fileTransfersFlow(): StateFlow<Map<String, List<FileTransferProgress>>> = transferLists.asStateFlow()

    /** Cancels one of the session's transfers, running or waiting; the others carry on. */
    fun cancelFileTransfer(sessionId: String, operationId: String): Boolean {
        val transfer = activeTransfersFor(sessionId).firstOrNull { it.operationId == operationId } ?: return false
        transfer.cancellationRequested.compareAndSet(false, true)
        transfer.job?.cancel(FileTransferCancelledException())
        transfer.workerThread?.interrupt()
        return true
    }

    /** Starts a failed or cancelled transfer again: it joins the session's queue as a new transfer. */
    fun retryFileTransfer(sessionId: String, operationId: String): Boolean {
        val (owner, retry) = transferRetries[operationId] ?: return false
        if (owner != sessionId) return false
        transferRetries.remove(operationId)
        synchronized(fileTransferStateLock) {
            finishedTransfers.computeIfPresent(sessionId) { _, list -> list.filter { it.operationId != operationId } }
        }
        publishTransferList(sessionId)
        retry()
        return true
    }

    /** Forgets the session's finished transfers. */
    fun clearFinishedTransfers(sessionId: String) {
        synchronized(fileTransferStateLock) {
            finishedTransfers.remove(sessionId)?.forEach { transferRetries.remove(it.operationId) }
        }
        publishTransferList(sessionId)
    }

    private fun forgetTransfers(sessionId: String) {
        synchronized(fileTransferStateLock) {
            finishedTransfers.remove(sessionId)
            transferRetries.entries.removeIf { it.value.first == sessionId }
            transferLists.update { it - sessionId }
        }
    }

    private fun publishFileTransferFailure(
        hostId: String,
        mode: ConnectionMode,
        direction: FileTransferDirection,
        fileName: String,
        sourceLabel: String,
        destinationLabel: String,
        errorMessage: String
    ) {
        val transfer = beginFileTransfer(
            hostId = hostId,
            mode = mode,
            direction = direction,
            fileName = fileName,
            sourceLabel = sourceLabel,
            destinationLabel = destinationLabel
        )
        if (finishFileTransfer(transfer, FileTransferStatus.FAILED, errorMessage)) {
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.ERROR,
                    message = "${transfer.operationLabel} failed: $errorMessage"
                )
            )
        }
    }

    private fun inferTransferFileName(path: String, fallback: String = "file"): String =
        path.substringAfterLast('/').substringAfterLast('\\').ifBlank { fallback }

    private fun updateActiveFileTransfer(
        transfer: ActiveFileTransfer,
        transform: (FileTransferProgress) -> FileTransferProgress
    ) {
        val base = transfer.latestProgress
        if (!base.isActive) return
        val next = transform(base)
        transfer.latestProgress = next.copy(bytesPerSecond = transfer.sampleRate(next.bytesTransferred) ?: next.bytesPerSecond)
        if (displayedTransfer(transfer.sessionId) === transfer) {
            publishDisplayedTransfer(transfer.sessionId)
        } else {
            publishTransferList(transfer.sessionId)
        }
    }

    private fun throwIfFileTransferCancelled(transfer: ActiveFileTransfer) {
        if (transfer.cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw FileTransferCancelledException()
        }
    }

    private fun finishFileTransfer(
        transfer: ActiveFileTransfer,
        status: FileTransferStatus,
        errorMessage: String? = null
    ): Boolean {
        val terminal = synchronized(transfer) {
            val current = transfer.latestProgress
            if (!current.isActive) return false
            current.copy(
                bytesTransferred = if (status == FileTransferStatus.SUCCEEDED) {
                    current.totalBytes ?: current.bytesTransferred
                } else {
                    current.bytesTransferred
                },
                hasStarted = current.hasStarted || status == FileTransferStatus.SUCCEEDED,
                status = status,
                errorMessage = errorMessage?.takeIf { it.isNotBlank() },
                completedAtEpochMillis = System.currentTimeMillis(),
                queuedTransfers = 0,
                retryMessage = null,
                waiting = false,
                bytesPerSecond = null,
                canRetry = status != FileTransferStatus.SUCCEEDED && transferRetries.containsKey(transfer.operationId)
            ).also { transfer.latestProgress = it }
        }
        if (status == FileTransferStatus.SUCCEEDED) transferRetries.remove(transfer.operationId)
        // A transfer that had to retry and still finished: how often connections drop mid-transfer, and why.
        if (status == FileTransferStatus.SUCCEEDED && transfer.retries > 0) {
            reportNetworkIssue(
                "transfer_recovered", transfer.sessionId, null,
                mapOf(
                    "retries" to transfer.retries.toString(),
                    "last_reason" to transfer.lastRetryReason.orEmpty(),
                    "direction" to terminal.direction.name.lowercase(),
                    "route" to transfer.route
                )
            )
        }
        if (transfer.startedAtMs > 0L) {
            metrics.recordTransfer(
                direction = terminal.direction.name.lowercase(),
                route = transfer.route,
                status = status.name.lowercase(),
                bytes = terminal.bytesTransferred,
                millis = SystemClock.elapsedRealtime() - transfer.startedAtMs
            )
        }
        synchronized(fileTransferStateLock) {
            finishedTransfers[transfer.sessionId] =
                (listOf(terminal) + finishedTransfers[transfer.sessionId].orEmpty()).take(MAX_FINISHED_TRANSFERS)
        }
        // Release the transfer before showing its end, so a new transfer started from that state
        // queues behind nothing.
        removeActiveTransfer(transfer)
        if (activeTransfersFor(transfer.sessionId).isNotEmpty()) {
            publishDisplayedTransfer(transfer.sessionId)
            return true
        }
        publishTransferList(transfer.sessionId)
        setFileTransferProgress(hostId = transfer.sessionId, progress = terminal)
        return true
    }

    private fun transferQueueFor(sessionId: String): Mutex = transferQueues.getOrPut(sessionId) { Mutex() }

    /**
     * Runs [block] once the session's earlier transfers have finished: each transfer already spreads over several
     * connections, so running two at once would only split the same bandwidth.
     */
    private fun launchFileTransfer(
        transfer: ActiveFileTransfer,
        operationName: String,
        block: suspend () -> String
    ) {
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                transferQueueFor(transfer.sessionId).withLock {
                    throwIfFileTransferCancelled(transfer)
                    transfer.workerThread = Thread.currentThread()
                    transfer.running = true
                    transfer.startedAtMs = SystemClock.elapsedRealtime()
                    publishDisplayedTransfer(transfer.sessionId)
                    val successMessage = measureOperation(operationName, transfer.sessionId) {
                        block()
                    }
                    throwIfFileTransferCancelled(transfer)
                    transfer.workerThread = null
                    if (finishFileTransfer(transfer, FileTransferStatus.SUCCEEDED)) {
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = transfer.sessionId,
                                level = SessionLogBus.LogLevel.INFO,
                                message = successMessage
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                transfer.workerThread = null
                val cancelled = transfer.cancellationRequested.get() ||
                    error is CancellationException ||
                    error is FileTransferCancelledException ||
                    error is SftpTransferCancelledException
                val terminalStatus = if (cancelled) FileTransferStatus.CANCELLED else FileTransferStatus.FAILED
                if (finishFileTransfer(transfer, terminalStatus, if (cancelled) null else error.message)) {
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = transfer.sessionId,
                            level = if (cancelled) SessionLogBus.LogLevel.WARN else SessionLogBus.LogLevel.ERROR,
                            message = if (cancelled) {
                                "${transfer.operationLabel} cancelled."
                            } else {
                                "${transfer.operationLabel} failed: ${error.message ?: "unknown error"}"
                            }
                        )
                    )
                }
                if (!cancelled) {
                    reportUnexpected(
                        "file_transfer",
                        transfer.sessionId,
                        error,
                        context = mapOf(
                            "transfer_mode" to transfer.initialProgress.mode.name,
                            "transfer_direction" to transfer.initialProgress.direction.name
                        )
                    )
                }
            } finally {
                transfer.workerThread = null
                // A manually interrupted pooled worker must not carry its interrupt
                // flag into an unrelated coroutine.
                Thread.interrupted()
                removeActiveTransfer(transfer)
            }
        }
        transfer.job = job
        job.invokeOnCompletion {
            // Cancelled while still waiting in the queue: the block never ran.
            if (transfer.cancellationRequested.get() && finishFileTransfer(transfer, FileTransferStatus.CANCELLED)) {
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = transfer.sessionId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "${transfer.operationLabel} cancelled."
                    )
                )
            }
            removeActiveTransfer(transfer)
        }
        if (transfer.cancellationRequested.get()) {
            job.cancel(FileTransferCancelledException())
        }
        job.start()
    }

    /** Cancels the session's running transfer and every one waiting behind it. */
    fun cancelFileTransfer(sessionId: String): Boolean {
        val transfers = activeTransfersFor(sessionId)
        if (transfers.isEmpty()) return false
        transfers.forEach { transfer ->
            transfer.cancellationRequested.compareAndSet(false, true)
            transfer.job?.cancel(FileTransferCancelledException())
            transfer.workerThread?.interrupt()
        }
        return true
    }

    fun sftpDownloadFile(hostId: String, remotePath: String, localPath: String?) =
        startDownload(hostId, ConnectionMode.SFTP, remotePath, localPath, "sftpDownloadFile")

    fun scpDownloadFile(hostId: String, remotePath: String, localPath: String?) =
        startDownload(hostId, ConnectionMode.SCP, remotePath, localPath, "scpDownloadFile")

    fun sftpUploadFile(hostId: String, localPath: String, remotePath: String) =
        startUpload(hostId, ConnectionMode.SFTP, localPath, remotePath, "sftpUploadFile")

    fun scpUploadFile(hostId: String, localPath: String, remotePath: String) =
        startUpload(hostId, ConnectionMode.SCP, localPath, remotePath, "scpUploadFile")

    /**
     * Downloads [remotePath] in chunks over the session's transfer connections, writing each chunk straight into
     * the destination. A download that fails part way resumes when the same file is downloaded to the same place.
     */
    private fun startDownload(
        hostId: String,
        mode: ConnectionMode,
        remotePath: String,
        localPath: String?,
        operationName: String
    ) {
        val source = preserveRemoteTransferPath(remotePath)
        val destinationLabel = localPath?.trim().orEmpty().ifBlank { "Downloads" }
        val fileName = inferTransferFileName(source, fallback = "download.bin")
        if (source.isBlank()) {
            publishFileTransferFailure(
                hostId, mode, FileTransferDirection.DOWNLOAD, "download.bin", source, destinationLabel,
                "Remote path is required."
            )
            UiDebugLog.result(operationName, false, "blank-remote-path hostId=$hostId")
            return
        }
        // A session still connecting, or reconnecting, gets the transfer once it's back.
        if (!activeJobs.containsKey(hostId)) {
            publishFileTransferFailure(
                hostId, mode, FileTransferDirection.DOWNLOAD, fileName, source, destinationLabel,
                "SFTP session is not connected."
            )
            UiDebugLog.result(operationName, false, "sftp-not-active hostId=$hostId")
            return
        }
        val transfer = beginFileTransfer(
            hostId = hostId,
            mode = mode,
            direction = FileTransferDirection.DOWNLOAD,
            fileName = fileName,
            sourceLabel = source,
            destinationLabel = destinationLabel
        )
        transferRetries[transfer.operationId] = hostId to { startDownload(hostId, mode, remotePath, localPath, operationName) }
        launchFileTransfer(transfer, operationName) {
            val pool = awaitTransferPool(hostId)
            val destinationUri = localPath.toContentUriOrNull()
            val destinationFile = if (destinationUri == null) resolveDestinationFile(hostId, source, localPath) else null
            val resolvedLabel = destinationUri?.let(::describeUri) ?: destinationFile!!.absolutePath
            val attributes = remoteAttributes(hostId, pool, source)
            if (attributes.type == FileMode.Type.DIRECTORY) {
                throw IOException("$source is a folder; only files can be downloaded.")
            }
            val size = attributes.size.coerceAtLeast(0L)
            updateActiveFileTransfer(transfer) {
                it.copy(destinationLabel = resolvedLabel, totalBytes = size, openUri = destinationUri?.toString())
            }
            val settings = sftpTransferSettings.sanitized()
            val hostKey = transferHostKey(hostId)
            val progress = transferProgressPublisher(transfer, size)
            val cancelled = { transfer.cancellationRequested.get() }
            val onResume = { already: Long, total: Long ->
                val percent = if (total > 0L) already * 100 / total else 0L
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.INFO,
                        message = "Resuming download of $source from $percent%"
                    )
                )
            }
            fun downloadInto(partial: SftpPartialDownload, channel: java.nio.channels.FileChannel, descriptor: java.io.FileDescriptor) {
                fileBoostFor(hostId)?.let { boost ->
                    try {
                        transfer.route = "boost"
                        boostDownload(boost, source, size, attributes.mtime, partial, channel, descriptor, progress, cancelled)
                        return
                    } catch (error: Throwable) {
                        if (cancelled() || error is FileTransferCancelledException) throw error
                        // SFTP carries on from the chunks file boost finished.
                        transfer.route = "mixed"
                        fileBoostFailed(hostId, boost, error, "download")
                    }
                }
                SftpResumableDownloader.download(
                    leases = pool,
                    connections = settings.parallelDownloads,
                    remotePath = source,
                    size = size,
                    mtime = attributes.mtime,
                    partial = partial,
                    channel = channel,
                    settings = settings,
                    onBytesTransferred = progress,
                    isCancelled = cancelled,
                    onResume = onResume,
                    onRetrying = retryPublisher(transfer),
                    onStart = { transfer.engine = it }
                )
            }
            if (destinationFile != null) {
                // Written beside the destination under another name, then renamed: a download that stopped part
                // way never looks like the finished file.
                destinationFile.parentFile?.mkdirs()
                val part = File(destinationFile.parentFile, destinationFile.name + PARTIAL_DOWNLOAD_SUFFIX)
                val partial = SftpPartialDownload.forDestination(
                    sftpPartialDirectory(), hostKey, source, "file:${destinationFile.absolutePath}", part
                )
                RandomAccessFile(part, "rw").use { raf -> downloadInto(partial, raf.channel, raf.fd) }
                if (!part.renameTo(destinationFile)) {
                    part.copyTo(destinationFile, overwrite = true)
                    part.delete()
                }
            } else {
                val uri = checkNotNull(destinationUri)
                val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
                if (descriptor != null && descriptor.statSize >= 0L) {
                    descriptor.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                            val partial = SftpPartialDownload.forDestination(
                                sftpPartialDirectory(), hostKey, source, uri.toString(), null
                            )
                            try {
                                downloadInto(partial, channel, pfd.fileDescriptor)
                            } catch (error: Throwable) {
                                // The picker made this file for the download; leave it empty, not half written.
                                if (cancelled()) runCatching { channel.truncate(0L) }
                                throw error
                            }
                        }
                    }
                } else {
                    // A destination that only takes a stream (some cloud providers): download into the cache, then
                    // copy it over.
                    runCatching { descriptor?.close() }
                    val partial = SftpPartialDownload.forRemote(sftpPartialDirectory(), hostKey, source)
                    val data = checkNotNull(partial.dataFile)
                    RandomAccessFile(data, "rw").use { raf -> downloadInto(partial, raf.channel, raf.fd) }
                    try {
                        contentResolver.openOutputStream(uri, "w")?.use { output ->
                            data.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_BYTES) }
                        } ?: throw IOException("Unable to open destination stream for $uri")
                    } finally {
                        partial.delete()
                    }
                }
            }
            "SFTP download complete: $source -> $resolvedLabel"
        }
    }

    /**
     * Uploads [localPath] in chunks over the session's transfer connections, each keeping writes in flight instead of
     * waiting for every reply.
     */
    private fun startUpload(
        hostId: String,
        mode: ConnectionMode,
        localPath: String,
        remotePath: String,
        operationName: String
    ) {
        val source = localPath.trim()
        val destination = preserveRemoteTransferPath(remotePath)
        val fileName = inferTransferFileName(source, fallback = "upload.bin")
        if (source.isBlank() || destination.isBlank()) {
            publishFileTransferFailure(
                hostId, mode, FileTransferDirection.UPLOAD, fileName, source, destination,
                "Local source and remote destination are required."
            )
            UiDebugLog.result(operationName, false, "invalid-paths hostId=$hostId")
            return
        }
        // A session still connecting, or reconnecting, gets the transfer once it's back.
        if (!activeJobs.containsKey(hostId)) {
            publishFileTransferFailure(
                hostId, mode, FileTransferDirection.UPLOAD, fileName, source, destination,
                "SFTP session is not connected."
            )
            UiDebugLog.result(operationName, false, "sftp-not-active hostId=$hostId")
            return
        }
        val transfer = beginFileTransfer(
            hostId = hostId,
            mode = mode,
            direction = FileTransferDirection.UPLOAD,
            fileName = fileName,
            sourceLabel = source,
            destinationLabel = destination
        )
        transferRetries[transfer.operationId] = hostId to { startUpload(hostId, mode, localPath, remotePath, operationName) }
        launchFileTransfer(transfer, operationName) {
            val pool = awaitTransferPool(hostId)
            openUploadSource(source).use { local ->
                updateActiveFileTransfer(transfer) {
                    it.copy(
                        fileName = inferTransferFileName(local.label, fallback = "upload.bin"),
                        sourceLabel = local.label,
                        totalBytes = local.size
                    )
                }
                val settings = sftpTransferSettings.sanitized()
                val perConnection = minOf(
                    settings.sftpReadSize.toLong() * settings.sftpMaxRequests,
                    SftpTransferSettings.MAX_IN_FLIGHT_BYTES_PER_CONNECTION
                )
                val boosted = fileBoostFor(hostId)?.let { boost ->
                    try {
                        transfer.route = "boost"
                        boostUpload(boost, destination, local, transferProgressPublisher(transfer, local.size)) {
                            transfer.cancellationRequested.get()
                        }
                        true
                    } catch (error: Throwable) {
                        if (transfer.cancellationRequested.get() || error is FileTransferCancelledException) throw error
                        // SFTP starts the file over.
                        transfer.route = "mixed"
                        fileBoostFailed(hostId, boost, error, "upload")
                        false
                    }
                } ?: false
                if (!boosted) SftpSegmentedTransfer(
                    queue = SftpChunkQueue(SftpChunkQueue.plan(local.size, settings.parallelDownloads)),
                    workers = settings.parallelDownloads,
                    leases = pool,
                    requestSize = SftpTransferSettings.UPLOAD_WRITE_SIZE,
                    inFlightBytes = { lease ->
                        if (lease.shared) minOf(perConnection, SftpTransferSettings.SHARED_CONNECTION_IN_FLIGHT_BYTES) else perConnection
                    },
                    openIo = { sftp, first ->
                        SftpSegmentWriter.open(sftp, destination, local.channel, create = first, permissions = local.permissions)
                    },
                    isCancelled = { transfer.cancellationRequested.get() },
                    onProgress = transferProgressPublisher(transfer, local.size),
                    onRetrying = retryPublisher(transfer)
                ).also { transfer.engine = it }.run()
                local.modifiedAtSeconds?.let { mtime ->
                    runCatching {
                        activeConnections[hostId]?.sftpBinding?.client?.setattr(
                            destination,
                            FileAttributes.Builder().withAtimeMtime(mtime, mtime).build()
                        )
                    }
                }
                "SFTP upload complete: ${local.label} -> $destination"
            }
        }
    }

    /** A local file to upload, opened for reads at any offset. */
    private class UploadSource(
        val descriptor: java.io.FileDescriptor,
        val channel: java.nio.channels.FileChannel,
        val size: Long,
        val label: String,
        val permissions: Int,
        val modifiedAtSeconds: Long?,
        private val onClose: () -> Unit
    ) : AutoCloseable {
        override fun close() {
            runCatching { channel.close() }
            onClose()
        }
    }

    private fun openUploadSource(source: String): UploadSource {
        val uri = source.toContentUriOrNull()
        if (uri == null) {
            val file = File(source)
            require(file.exists()) { "Local file does not exist: $source" }
            require(file.isFile) { "Local path is not a file: $source" }
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            return UploadSource(
                descriptor = raf.fd,
                channel = channel,
                size = channel.size(),
                label = file.absolutePath,
                permissions = if (file.canExecute()) 0b111_101_101 else 0b110_100_100,
                modifiedAtSeconds = file.lastModified().takeIf { it > 0L }?.div(1000L),
                onClose = {}
            )
        }
        val label = describeUri(uri)
        val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (descriptor != null && descriptor.statSize >= 0L) {
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            return UploadSource(descriptor.fileDescriptor, channel, descriptor.statSize, label, 0b110_100_100, null) {
                runCatching { descriptor.close() }
            }
        }
        // A source that only gives a stream: copy it to the cache first so chunks can be read at any offset.
        runCatching { descriptor?.close() }
        val safeName = label.replace('/', '_').replace('\\', '_').ifBlank { "selected_file" }
        val staged = File.createTempFile("uri_upload_", "_$safeName", cacheDir)
        try {
            (contentResolver.openInputStream(uri) ?: throw IOException("Unable to open source stream for $uri")).use { input ->
                staged.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
            }
        } catch (error: Throwable) {
            staged.delete()
            throw error
        }
        val stagedFile = RandomAccessFile(staged, "r")
        val channel = stagedFile.channel
        return UploadSource(stagedFile.fd, channel, channel.size(), label, 0b110_100_100, null) { staged.delete() }
    }

    /** Looks [path] up on the browser's connection, which is already open; transfers' own may still be logging in. */
    private fun remoteAttributes(hostId: String, pool: SftpTransferPool, path: String): FileAttributes {
        activeConnections[hostId]?.sftpBinding?.client?.let { return it.stat(path) }
        val lease = pool.acquire { false } ?: throw IOException("SFTP session is not connected.")
        return try {
            lease.sftp.stat(path).also { lease.release() }
        } catch (error: Throwable) {
            lease.discard()
            throw error
        }
    }

    private fun transferHostKey(hostId: String): String =
        activeConnections[hostId]?.host?.let { "${it.username}@${it.host}:${it.port}" } ?: hostId

    /** Shows why a transfer is waiting to retry, and clears it once bytes move again. */
    private fun retryPublisher(transfer: ActiveFileTransfer): (String?) -> Unit = { message ->
        updateActiveFileTransfer(transfer) { it.copy(retryMessage = message) }
        if (message != null) {
            transfer.retries += 1
            transfer.lastRetryReason = message
            SessionLogBus.emit(
                SessionLogBus.Entry(hostId = transfer.sessionId, level = SessionLogBus.LogLevel.WARN, message = message)
            )
        }
    }

    private fun transferProgressPublisher(transfer: ActiveFileTransfer, totalBytes: Long?): (Long) -> Unit {
        val throttle = FileTransferProgressThrottle(updateIntervalNanos = FILE_TRANSFER_PROGRESS_UPDATE_INTERVAL_NANOS)
        return { transferred ->
            throwIfFileTransferCancelled(transfer)
            if (throttle.shouldPublish(transferred, totalBytes)) {
                updateActiveFileTransfer(transfer) {
                    it.copy(bytesTransferred = transferred, totalBytes = totalBytes ?: it.totalBytes, hasStarted = true)
                }
            }
        }
    }

    private fun newTransferPool(
        sessionId: String,
        host: HostConnection,
        password: () -> String?,
        canLogInAgain: () -> Boolean,
        sessionClient: () -> SSHClient?
    ): SftpTransferPool = SftpTransferPool(
        maxConnections = { sftpTransferSettings.sanitized().parallelDownloads },
        openConnection = {
            if (!canLogInAgain()) {
                throw IOException("logging in needed answers to the server's questions")
            }
            openTransferConnection(host, password())
        },
        sessionClient = { sessionClient()?.takeIf { it.isConnected } },
        schedule = { delayMs, action ->
            serviceScope.launch {
                delay(delayMs)
                action()
            }
        },
        onFallback = { error ->
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "File transfers will share this connection: a second login failed (${error.message ?: error.javaClass.simpleName})."
                )
            )
        }
    )

    /**
     * Opens and logs in another connection for transfers, with the credentials that just worked and only a host key
     * already trusted: it never asks the user anything.
     */
    private fun openTransferConnection(host: HostConnection, password: String?): SSHClient {
        val client = SshClientProvider.createClient(
            context = this,
            host = host,
            autoTrustUnknownHostKey = false,
            onHostKeyPrompt = null
        )
        // A transfer keeps its connection's link full, so keepalive replies can wait behind data: allow two minutes
        // of missed replies. The transfer's own stall watchdog notices a dead link sooner.
        (client.connection.keepAlive as? KeepAliveRunner)?.maxAliveCount = TRANSFER_KEEPALIVE_MAX_UNANSWERED
        try {
            client.connect(host.host, host.port)
            if (host.preferredAuth != AuthMethod.PASSWORD) {
                runCatching { authenticatePublicKeyQuietly(client, host) }
            }
            if (!client.isAuthenticated && host.preferredAuth != AuthMethod.IDENTITY && !password.isNullOrBlank()) {
                client.authPasswordOrInteractive(host.username, password) { null }
            }
            if (!client.isAuthenticated) throw UserAuthException("the server refused a second login")
            return client
        } catch (error: Throwable) {
            runCatching { client.disconnect() }
            throw error
        }
    }

    fun manageRemotePath(hostId: String, operation: String, sourcePath: String, destinationPath: String? = null) {
        val src = sourcePath.trim()
        if (src.isBlank()) return
        val normalizedOperation = operation.trim().lowercase()
        val destination = destinationPath?.trim()
        runCatching {
            validateRemotePathMutation(
                operation = normalizedOperation,
                sourcePath = src,
                destinationPath = destination
            )
        }.onFailure { error ->
            // Use the failure prefix the SFTP console and SCP browser wait for; any other text
            // left them stuck waiting ("A command is already running").
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.ERROR,
                    message = "SFTP operation failed: ${error.message ?: "remote path operation was rejected."}"
                )
            )
            UiDebugLog.result(
                "manageRemotePath",
                false,
                "path-policy-rejected hostId=$hostId operation=$normalizedOperation"
            )
            return
        }
        serviceScope.launch {
            awaitBrowserConnection(hostId)
            val ok = runWithSftpClient(hostId) { sftp ->
                measureOperation("manageRemotePath:$normalizedOperation", hostId) {
                    when (normalizedOperation) {
                        "mkdir" -> sftp.mkdir(src)
                        "delete_file", "delete_non_recursive", "rm" ->
                            deleteRemotePathNonRecursively(sftp, src)
                        "delete", "delete_recursive", "rm_recursive" ->
                            deleteRemotePathRecursively(sftp, src)
                        "move", "rename" -> {
                            val dest = destination.orEmpty()
                            require(dest.isNotBlank()) { "Destination is required." }
                            sftp.rename(src, dest)
                        }
                        "chmod" -> sftp.chmod(src, destination.orEmpty().toInt(8))
                        else -> error("Unsupported operation: $normalizedOperation")
                    }
                    SessionLogBus.emit(
                        SessionLogBus.Entry(
                            hostId = hostId,
                            level = SessionLogBus.LogLevel.INFO,
                            message = "Remote $normalizedOperation completed: $src${destination?.let { " -> $it" } ?: ""}"
                        )
                    )
                }
            }
            if (!ok) {
                // Failures with an exception were already reported. A missing transport was not,
                // and the SFTP console/SCP browser would otherwise wait for a result forever.
                if (!hasSftpTransport(hostId)) {
                    reportSftpNotConnected(hostId)
                }
                UiDebugLog.result("manageRemotePath", false, "sftp-not-active hostId=$hostId")
            }
        }
    }

    private fun nextListingGeneration(hostId: String): Long =
        listingGenerations.getOrPut(hostId) { AtomicLong() }.incrementAndGet()

    private fun isLatestListing(hostId: String, generation: Long): Boolean =
        listingGenerations[hostId]?.get()?.let { it == generation } ?: true

    private fun refreshSftpDirectoryListing(
        hostId: String,
        sftp: SFTPClient,
        path: String,
        resolveSymbolicLinks: Boolean,
        generation: Long? = null,
        client: SSHClient? = activeConnections[hostId]?.client,
        showPartial: Boolean = true
    ) {
        val bulkLookup = client?.takeIf { resolveSymbolicLinks && hostId !in bulkLinkLookupUnavailable }?.let { ssh ->
            { directory: String, names: List<String> ->
                runCatching { lookUpLinksInBulk(ssh, directory, names) }.getOrNull().also { result ->
                    // An SFTP-only account: don't try again for every folder of this session.
                    if (result == null) bulkLinkLookupUnavailable += hostId
                }
            }
        }
        val listingStartedAt = SystemClock.elapsedRealtime()
        measureOperation("sftpListDirectory", hostId, thresholdMs = 200L) {
            // File boost lists a folder, links and all, in one round trip; SFTP covers anything it can't do.
            val boosted = fileBoostFor(hostId)?.let { boost ->
                runCatching { parseBoostListing(boost.list(path), resolveSymbolicLinks) }
                    .onFailure { fileBoostFailed(hostId, boost, it, "listing") }
                    .getOrNull()
            }
            val directory = boosted ?: loadSftpDirectoryPipelined(
                sftp = sftp,
                requestedPath = path,
                resolveSymbolicLinks = resolveSymbolicLinks,
                // The file browser shows a big folder as it arrives; the console prints only the whole listing.
                onPartial = if (resolveSymbolicLinks && showPartial) {
                    { partial ->
                        if (generation == null || isLatestListing(hostId, generation)) {
                            setRemoteDirectorySnapshot(
                                hostId,
                                RemoteDirectorySnapshot(
                                    path = partial.path,
                                    entries = partial.entries,
                                    refreshToken = SystemClock.elapsedRealtimeNanos(),
                                    complete = false
                                )
                            )
                        }
                    }
                } else {
                    null
                },
                bulkLinkLookup = bulkLookup
            )
            val listing = RemoteDirectorySnapshot(
                path = directory.path,
                entries = directory.entries,
                refreshToken = SystemClock.elapsedRealtimeNanos()
            )
            // The browser's listings only (the console's skip link details), under the folder's own path and the one
            // asked for, which differ for "." and links.
            if (resolveSymbolicLinks) {
                recentListings.computeIfAbsent(hostId) { RecentListings(RECENT_LISTINGS) }.apply {
                    // "." is where the browser starts: the home folder, kept apart so Home is always instant.
                    if (path.trim() == "." || path.trim() == "~") {
                        putHome(directory.path, listing)
                    } else {
                        put(directory.path, listing)
                        put(path, listing)
                    }
                }
            }
            if (resolveSymbolicLinks) metrics.recordListing(directory.entries.size, SystemClock.elapsedRealtime() - listingStartedAt)
            if (generation != null && !isLatestListing(hostId, generation)) return@measureOperation
            setRemoteDirectorySnapshot(hostId, listing)
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Listed ${directory.entries.size} item(s) in ${directory.path}"
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

    private fun String?.toContentUriOrNull(): Uri? {
        val raw = this?.trim().orEmpty()
        return raw.takeIf { it.startsWith("content://") }?.let(Uri::parse)
    }

    private fun describeUri(uri: Uri): String = queryUriDisplayName(uri) ?: uri.toString()

    private fun queryUriDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun queryUriSize(uri: Uri): Long? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }
    }.getOrNull()?.takeIf { it >= 0L }

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
                reportSftpOperationFailure(hostId, err)
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
            reportSftpOperationFailure(hostId, err)
        }.getOrDefault(false)
    }

    /** Reads a small remote text file for the in-app editor. */
    suspend fun readRemoteTextFile(hostId: String, path: String): Result<String> = withContext(Dispatchers.IO) {
        awaitBrowserConnection(hostId)
        runCatching {
            val sftp = activeConnections[hostId]?.sftpBinding?.client ?: error("The file browser isn't connected.")
            val attributes = sftp.stat(path)
            // Devices, pipes and /proc entries report a size of 0 and never end (/dev/zero): only regular files.
            require(attributes.type == net.schmizz.sshj.sftp.FileMode.Type.REGULAR) {
                "Only regular files can be edited here."
            }
            val tooLarge = "This file is larger than ${MAX_EDITABLE_FILE_BYTES / 1024} KB. Download it to edit it."
            require(attributes.size <= MAX_EDITABLE_FILE_BYTES) { tooLarge }
            // The size is the server's word: stop reading at the limit whatever it said.
            val bytes = sftp.open(path).use { file ->
                file.RemoteFileInputStream().use { input -> readAtMost(input, MAX_EDITABLE_FILE_BYTES.toInt()) }
            } ?: throw IllegalArgumentException(tooLarge)
            require(bytes.none { it == 0.toByte() }) { "This looks like a binary file, so it can't be edited here." }
            String(bytes, StandardCharsets.UTF_8)
        }.onFailure { error ->
            if (error !is IllegalArgumentException && error !is IllegalStateException) {
                reportUnexpected("editor_read", hostId, error)
            }
        }
    }

    /** Everything [input] holds, or null if that is more than [limit] bytes. */
    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            output.write(buffer, 0, read)
            if (output.size() > limit) return null
        }
    }

    /** Replaces a remote file's contents (keeping its permissions) with [text]. */
    suspend fun writeRemoteTextFile(hostId: String, path: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        awaitBrowserConnection(hostId)
        runCatching {
            val sftp = activeConnections[hostId]?.sftpBinding?.client ?: error("The file browser isn't connected.")
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            sftp.open(path, EnumSet.of(OpenMode.WRITE, OpenMode.TRUNC)).use { file ->
                file.RemoteFileOutputStream().use { it.write(bytes) }
            }
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Saved $path (${bytes.size} bytes)"
                )
            )
        }.onFailure { error ->
            if (error !is IllegalStateException) reportUnexpected("editor_write", hostId, error)
        }
    }

    private fun sftpPartialDirectory(): File = File(cacheDir, "sftp-partial")

    private fun telemetrySecrets(hostId: String, host: HostConnection? = activeConnections[hostId]?.host): List<String?> =
        listOf(host?.host, host?.username, host?.name)

    /**
     * Sends an error to crash reporting (when the user allows it) only if it looks like our bug:
     * not a cancellation, a network problem, a login or host-key failure, or the server refusing
     * a file operation. Scrubbed of hosts, users, addresses, and paths.
     */
    private fun reportUnexpected(
        action: String,
        hostId: String,
        error: Throwable,
        context: Map<String, String> = emptyMap(),
        host: HostConnection? = activeConnections[hostId]?.host
    ) {
        if (error is CancellationException || error is FileTransferCancelledException) return
        // A host key or password prompt nobody answered in time: nothing went wrong in the app.
        if (error is ConnectionTimeoutException) return
        // A network failure is investigated like a crash: reported as a network issue, with what the connection was.
        error.connectionFailureKind()?.let { kind ->
            if (kind.isNetwork) reportNetworkIssue(action, hostId, error, context)
            return
        }
        if (generateSequence(error) { it.cause }.take(8).any { it is SFTPException }) return
        TelemetryInitializer.recordNonFatal(action, error, context, telemetrySecrets(hostId, host))
    }

    private val networkIssueLimiter = NetworkIssueLimiter(clock = SystemClock::elapsedRealtime)

    /**
     * Reports a network failure for investigation (Crash reports on), with what the connection was: its mode,
     * route (SSH, Boost, Mosh), the phone's network (Wi-Fi, mobile data) and its recent round trip.
     */
    private fun reportNetworkIssue(kind: String, hostId: String, error: Throwable?, context: Map<String, String> = emptyMap()) {
        val root = error?.let { generateSequence(it) { cause -> cause.cause }.take(16).last() }
        if (!networkIssueLimiter.allow("$kind|${root?.javaClass?.simpleName.orEmpty()}")) return
        val connection = activeConnections[hostId]
        val details = buildMap {
            put("network_issue", kind)
            put("net_mode", (connection?.mode ?: sessionSnapshots.value.firstOrNull { it.hostId == hostId }?.mode)?.name ?: "unknown")
            put("net_route", when {
                boostShellFor(hostId) != null -> "boost_terminal"
                fileBoostFor(hostId) != null -> "boost_files"
                connection?.moshBinding != null -> "mosh"
                else -> "ssh"
            })
            put("net_type", currentNetworkType())
            latencySamples[hostId]?.summary("")?.takeIf { it.samples > 0 }?.let {
                put("net_rtt_avg_ms", it.avgMs.toString())
                put("net_rtt_max_ms", it.maxMs.toString())
            }
            putAll(context)
        }
        TelemetryInitializer.recordNonFatal("network_$kind", NetworkIssue(kind, error), details, telemetrySecrets(hostId, connection?.host))
    }

    /** "wifi", "cellular", "ethernet" (plus "+vpn"), or "none". */
    private fun currentNetworkType(): String {
        val manager = connectivityManager ?: return "unknown"
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull() ?: return "none"
        val base = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "$base+vpn" else base
    }

    private fun hasSftpTransport(hostId: String): Boolean =
        activeConnections[hostId]?.let { it.sftpBinding?.client != null || it.client != null } == true

    /**
     * Reports a user-initiated remote operation that could not start because the session is gone.
     * Background listings stay silent: the file browser requests one while the connection is still
     * coming up, and reporting that showed a spurious "not connected" error on every open.
     */
    private fun reportSftpNotConnected(hostId: String): Boolean {
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.ERROR,
                message = "SFTP operation failed: the session is not connected."
            )
        )
        return false
    }

    private fun reportSftpOperationFailure(hostId: String, error: Throwable) {
        reportUnexpected("sftp_operation", hostId, error)
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.ERROR,
                message = "SFTP operation failed: ${error.message ?: "unknown error"}"
            )
        )
        closeSftpSessionForTransportFailure(hostId, error)
    }

    private fun closeSftpSessionForTransportFailure(hostId: String, error: Throwable) {
        val mode = activeConnections[hostId]?.mode
        if (!error.isFatalSftpTransportFailure() || mode == null || mode == ConnectionMode.SSH) {
            return
        }
        if (requestReconnect(hostId, "Connection lost.")) return
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.ERROR,
                message = "SFTP transport failed; session disconnected."
            )
        )
        stopSession(hostId)
    }

    private fun deleteRemotePathNonRecursively(sftp: SFTPClient, path: String) {
        val attributes = sftp.lstat(path)
        require(attributes.type != net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
            "Remote path is a directory; use recursive delete: $path"
        }
        sftp.rm(path)
    }

    private fun deleteRemotePathRecursively(sftp: SFTPClient, path: String) {
        val attributes = sftp.lstat(path)
        if (attributes.type != net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
            // lstat deliberately inspects the link itself. Never canonicalize here:
            // canonicalization would turn a request to unlink a symlink into deletion
            // of the symlink's target.
            sftp.rm(path)
            return
        }

        val children = sftp.ls(path).filterNot { it.name == "." || it.name == ".." }
        // Names come from the server. One like "../victim" or "a/b" would make the joined path point outside the
        // folder being deleted, so refuse the whole delete before removing anything.
        children.firstOrNull { it.name.isEmpty() || it.name.contains('/') || it.name == "." || it.name == ".." }
            ?.let { child -> throw IOException("Refusing to delete: the server listed an invalid name \"${child.name}\" in $path") }
        children.forEach { child ->
            val childPath = if (path.endsWith("/")) "$path${child.name}" else "$path/${child.name}"
            deleteRemotePathRecursively(sftp, childPath)
        }
        sftp.rmdir(path)
    }

    private fun activatePortForwards(
        hostId: String,
        client: SSHClient,
        sessionHost: HostConnection,
        forwards: List<PortForward>
    ): List<PortForwardBinding> {
        val bindings = mutableListOf<PortForwardBinding>()
        forwards.forEach { forward ->
            runCatching {
                val binding = when (forward.type) {
                    PortForwardType.LOCAL -> startLocalPortForward(hostId, client, sessionHost, forward)
                    PortForwardType.REMOTE -> startRemotePortForward(hostId, client, forward)
                    PortForwardType.DYNAMIC -> startDynamicPortForward(hostId, client, forward)
                }
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
                        message = "Failed to start ${forward.type.displayName()} forward ${forward.label}: " +
                            portForwardStartFailure(forward, err)
                    )
                )
            }
        }
        return bindings
    }

    private fun startLocalPortForward(
        hostId: String,
        client: SSHClient,
        sessionHost: HostConnection,
        forward: PortForward
    ): PortForwardBinding {
        val bindHost = normalizeBindHost(forward.sourceHost)
        val bindPort = requireValidPort(forward.sourcePort, "source")
        val destinationHost = forward.inferredDestinationHost()
        val destinationPort = requireValidPort(forward.destinationPort, "destination")
        val legacyDestinationHost = forward.legacyLoopbackDestinationHost(sessionHost)
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(bindHost, bindPort))
        val connectionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
        val acceptThread = Thread({
            while (!serverSocket.isClosed && !Thread.currentThread().isInterrupted) {
                val socket = try {
                    serverSocket.accept()
                } catch (err: IOException) {
                    if (!isExpectedForwardCloseException(serverSocket, err)) {
                        emitPortForwardWarning(hostId, forward, err)
                    }
                    break
                }
                socket.tcpNoDelay = true
                val connectionJob = serviceScope.launch(Dispatchers.IO) {
                    runCatching {
                        bridgeLocalPortForwardConnection(
                            hostId = hostId,
                            client = client,
                            forward = forward,
                            acceptedSocket = socket,
                            destinationHost = destinationHost,
                            destinationPort = destinationPort,
                            legacyDestinationHost = legacyDestinationHost
                        )
                    }.onFailure { err ->
                        if (!isExpectedForwardStreamException(err)) {
                            emitPortForwardConnectionWarning(hostId, forward, err)
                        }
                    }
                }
                connectionJobs += connectionJob
                connectionJob.invokeOnCompletion {
                    connectionJobs.remove(connectionJob)
                    runCatching { socket.close() }
                }
            }
        }, "sshpeaches-port-forward-$bindPort").apply {
            isDaemon = true
            start()
        }
        return PortForwardBinding(
            forwardId = forward.id,
            summary = "Local forward active: $bindHost:$bindPort -> $destinationHost:$destinationPort",
            close = {
                runCatching { serverSocket.close() }
                acceptThread.interrupt()
                synchronized(connectionJobs) { connectionJobs.toList() }.forEach { it.cancel() }
            }
        )
    }

    private suspend fun bridgeLocalPortForwardConnection(
        hostId: String,
        client: SSHClient,
        forward: PortForward,
        acceptedSocket: Socket,
        destinationHost: String,
        destinationPort: Int,
        legacyDestinationHost: String?
    ) {
        val directConnection = runCatching {
            client.newDirectConnection(destinationHost, destinationPort)
        }.recoverCatching { err ->
            val fallbackHost = legacyDestinationHost ?: throw err
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Retrying local forward ${forward.label} against $fallbackHost because the saved destination matched the SSH host."
                )
            )
            client.newDirectConnection(fallbackHost, destinationPort)
        }.getOrElse { err ->
            runCatching { acceptedSocket.close() }
            throw err
        }
        bridgePortForwardStreams(
            hostId = hostId,
            forward = forward,
            socket = acceptedSocket,
            channelInput = directConnection.inputStream,
            channelOutput = directConnection.outputStream,
            closeChannel = { directConnection.close() }
        )
    }

    /** Copies both ways between a socket on this phone and an SSH channel until either side ends. */
    private suspend fun bridgePortForwardStreams(
        hostId: String,
        forward: PortForward,
        socket: Socket,
        channelInput: InputStream,
        channelOutput: OutputStream,
        closeChannel: () -> Unit
    ) {
        val acceptedSocket = socket
        val acceptedInput = acceptedSocket.getInputStream()
        val acceptedOutput = acceptedSocket.getOutputStream()
        val remoteInput = channelInput
        val remoteOutput = channelOutput
        val upstreamJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                copyPortForwardStream(
                    input = acceptedInput,
                    output = remoteOutput
                )
            }.onFailure { err ->
                if (!isExpectedForwardStreamException(err)) {
                    emitPortForwardWarning(hostId, forward, err)
                }
            }.also {
                runCatching { remoteOutput.close() }
            }
        }
        val downstreamJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                copyPortForwardStream(
                    input = remoteInput,
                    output = acceptedOutput
                )
            }.onFailure { err ->
                if (!isExpectedForwardStreamException(err)) {
                    emitPortForwardWarning(hostId, forward, err)
                }
            }.also {
                runCatching {
                    if (!acceptedSocket.isClosed && !acceptedSocket.isOutputShutdown) {
                        acceptedSocket.shutdownOutput()
                    }
                }
            }
        }
        try {
            upstreamJob.join()
            downstreamJob.join()
        } catch (err: Exception) {
            if (!isExpectedForwardStreamException(err)) {
                emitPortForwardWarning(hostId, forward, err)
            }
        } finally {
            upstreamJob.cancel()
            downstreamJob.cancel()
            runCatching { closeChannel() }
            runCatching { acceptedSocket.close() }
        }
    }

    /**
     * Remote forward (ssh -R): the server listens on the forward's source address, and each connection it
     * accepts comes back over SSH and is connected, from this phone, to the destination.
     */
    private fun startRemotePortForward(
        hostId: String,
        client: SSHClient,
        forward: PortForward
    ): PortForwardBinding {
        val bindHost = normalizeBindHost(forward.sourceHost)
        val bindPort = requireValidPort(forward.sourcePort, "source")
        val destinationHost = forward.inferredDestinationHost()
        val destinationPort = requireValidPort(forward.destinationPort, "destination")
        val connectionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
        val closed = AtomicBoolean(false)
        val listener = ConnectListener { channel ->
            if (closed.get()) {
                runCatching { channel.reject(OpenFailException.Reason.ADMINISTRATIVELY_PROHIBITED, "forward closed") }
                return@ConnectListener
            }
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(destinationHost, destinationPort), PORT_FORWARD_CONNECT_TIMEOUT_MS)
            } catch (err: IOException) {
                runCatching { socket.close() }
                runCatching { channel.reject(OpenFailException.Reason.CONNECT_FAILED, "connection failed") }
                emitPortForwardConnectionWarning(hostId, forward, err)
                return@ConnectListener
            }
            try {
                channel.confirm()
            } catch (err: IOException) {
                runCatching { socket.close() }
                return@ConnectListener
            }
            val connectionJob = serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    bridgePortForwardStreams(
                        hostId = hostId,
                        forward = forward,
                        socket = socket,
                        channelInput = channel.inputStream,
                        channelOutput = channel.outputStream,
                        closeChannel = { channel.close() }
                    )
                }.onFailure { err ->
                    if (!isExpectedForwardStreamException(err)) {
                        emitPortForwardConnectionWarning(hostId, forward, err)
                    }
                }
            }
            connectionJobs += connectionJob
            connectionJob.invokeOnCompletion {
                connectionJobs.remove(connectionJob)
                runCatching { socket.close() }
                runCatching { channel.close() }
            }
        }
        val bound = client.remotePortForwarder.bind(RemotePortForwarder.Forward(bindHost, bindPort), listener)
        return PortForwardBinding(
            forwardId = forward.id,
            summary = "Remote forward active: server $bindHost:${bound.port} -> $destinationHost:$destinationPort",
            close = {
                closed.set(true)
                // Ask the server to stop listening, without holding up the caller: on a dying connection the
                // request waits for an answer that never comes, and the server drops the listener anyway.
                if (client.isConnected) {
                    serviceScope.launch(Dispatchers.IO) { runCatching { client.remotePortForwarder.cancel(bound) } }
                }
                synchronized(connectionJobs) { connectionJobs.toList() }.forEach { it.cancel() }
            }
        )
    }

    /**
     * Dynamic forward (ssh -D): a SOCKS proxy on this phone. Each client names a destination, which is
     * opened from the server.
     */
    private fun startDynamicPortForward(
        hostId: String,
        client: SSHClient,
        forward: PortForward
    ): PortForwardBinding {
        val bindHost = normalizeBindHost(forward.sourceHost)
        val bindPort = requireValidPort(forward.sourcePort, "source")
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(bindHost, bindPort))
        val connectionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
        val serverRefusalReported = AtomicBoolean(false)
        val acceptThread = Thread({
            while (!serverSocket.isClosed && !Thread.currentThread().isInterrupted) {
                val socket = try {
                    serverSocket.accept()
                } catch (err: IOException) {
                    if (!isExpectedForwardCloseException(serverSocket, err)) {
                        emitPortForwardWarning(hostId, forward, err)
                    }
                    break
                }
                socket.tcpNoDelay = true
                val connectionJob = serviceScope.launch(Dispatchers.IO) {
                    runCatching {
                        bridgeDynamicPortForwardConnection(hostId, client, forward, socket, serverRefusalReported)
                    }.onFailure { err ->
                        // A client that speaks something else, or gives up mid-handshake, is not worth a warning.
                        if (err !is SocksException && !isExpectedForwardStreamException(err)) {
                            emitPortForwardConnectionWarning(hostId, forward, err)
                        }
                    }
                }
                connectionJobs += connectionJob
                connectionJob.invokeOnCompletion {
                    connectionJobs.remove(connectionJob)
                    runCatching { socket.close() }
                }
            }
        }, "sshpeaches-socks-forward-$bindPort").apply {
            isDaemon = true
            start()
        }
        return PortForwardBinding(
            forwardId = forward.id,
            summary = "Dynamic forward active: SOCKS proxy on $bindHost:$bindPort",
            close = {
                runCatching { serverSocket.close() }
                acceptThread.interrupt()
                synchronized(connectionJobs) { connectionJobs.toList() }.forEach { it.cancel() }
            }
        )
    }

    private suspend fun bridgeDynamicPortForwardConnection(
        hostId: String,
        client: SSHClient,
        forward: PortForward,
        acceptedSocket: Socket,
        serverRefusalReported: AtomicBoolean
    ) {
        // The handshake is a few bytes: a client that stalls must not hold a thread.
        acceptedSocket.soTimeout = SOCKS_HANDSHAKE_TIMEOUT_MS
        val request = try {
            SocksHandshake.readRequest(acceptedSocket.getInputStream(), acceptedSocket.getOutputStream())
        } catch (err: IOException) {
            runCatching { acceptedSocket.close() }
            throw err
        }
        val directConnection = try {
            client.newDirectConnection(request.host, request.port)
        } catch (err: IOException) {
            val outcome = when ((err as? OpenFailException)?.reason) {
                OpenFailException.Reason.ADMINISTRATIVELY_PROHIBITED -> SocksOutcome.NOT_ALLOWED
                OpenFailException.Reason.CONNECT_FAILED -> SocksOutcome.REFUSED
                else -> SocksOutcome.FAILED
            }
            runCatching { request.reply(outcome) }
            runCatching { acceptedSocket.close() }
            // The destination being down is the client's business, like any proxy. A refusal by the server
            // means the forward cannot work at all: say so once, not for every connection of a browser.
            if (outcome == SocksOutcome.NOT_ALLOWED && serverRefusalReported.compareAndSet(false, true)) {
                emitPortForwardConnectionWarning(
                    hostId,
                    forward,
                    IOException("the server does not allow forwarding (AllowTcpForwarding in its sshd_config)")
                )
            }
            return
        }
        try {
            request.reply(SocksOutcome.CONNECTED)
            acceptedSocket.soTimeout = 0
        } catch (err: IOException) {
            runCatching { directConnection.close() }
            runCatching { acceptedSocket.close() }
            throw err
        }
        bridgePortForwardStreams(
            hostId = hostId,
            forward = forward,
            socket = acceptedSocket,
            channelInput = directConnection.inputStream,
            channelOutput = directConnection.outputStream,
            closeChannel = { directConnection.close() }
        )
    }

    /** Why a forward did not start, with what to check when the server refused a remote forward. */
    private fun portForwardStartFailure(forward: PortForward, err: Throwable): String {
        val reason = err.message ?: "unknown error"
        return if (forward.type == PortForwardType.REMOTE && err is ConnectionException) {
            "$reason. $REMOTE_FORWARD_REFUSED_HINT"
        } else {
            reason
        }
    }

    private fun copyPortForwardStream(
        input: InputStream,
        output: OutputStream
    ) {
        val buffer = ByteArray(DEFAULT_PORT_FORWARD_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            output.write(buffer, 0, read)
            output.flush()
        }
    }

    private fun emitPortForwardWarning(hostId: String, forward: PortForward, err: Throwable) {
        if (!activeJobs.containsKey(hostId)) return
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.WARN,
                message = "${forward.type.displayName()} forward ${forward.label} stopped: ${err.message ?: "unknown error"}"
            )
        )
    }

    private fun emitPortForwardConnectionWarning(hostId: String, forward: PortForward, err: Throwable) {
        if (!activeJobs.containsKey(hostId)) return
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = hostId,
                level = SessionLogBus.LogLevel.WARN,
                message = "${forward.type.displayName()} forward ${forward.label} connection failed: ${err.message ?: "unknown error"}"
            )
        )
    }

    private fun isExpectedForwardCloseException(serverSocket: ServerSocket, err: IOException): Boolean =
        serverSocket.isClosed || err is SocketException

    private fun isExpectedForwardStreamException(err: Throwable): Boolean =
        err is CancellationException || err is SocketException || err is IOException

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
        hostKeyPrompts.update { prompts -> prompts + uiPrompt }
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
            hostKeyPrompts.update { prompts -> prompts.filterNot { it.id == promptId } }
        }
    }

    private fun awaitPasswordDecision(
        host: HostConnection,
        sessionId: String,
        mode: ConnectionMode,
        reason: String,
        deadlineMillis: Long,
        allowSave: Boolean,
        challenge: LoginChallenge? = null
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
            allowSave = allowSave && challenge == null,
            title = if (challenge == null) "Password Required" else "Server Login Question",
            fieldLabel = challenge?.prompt?.trimEnd(':', ' ')?.ifBlank { null } ?: if (challenge == null) "Password" else "Answer",
            echo = challenge?.echo == true,
            isChallenge = challenge != null
        )
        passwordPromptWaiters[promptId] = waiter
        passwordPrompts.update { prompts -> prompts + prompt }
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
            passwordPrompts.update { prompts -> prompts.filterNot { it.id == promptId } }
        }
    }

    private fun clearHostKeyPromptsForHost(hostId: String, trust: Boolean) {
        val promptsForHost = hostKeyPrompts.value.filter { it.hostId == hostId }
        promptsForHost.forEach { prompt ->
            hostKeyPromptWaiters.remove(prompt.id)?.complete(trust)
        }
        if (promptsForHost.isNotEmpty()) {
            hostKeyPrompts.update { prompts -> prompts.filterNot { it.hostId == hostId } }
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
            passwordPrompts.update { prompts -> prompts.filterNot { it.hostId == hostId } }
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
        allowPasswordSave: Boolean,
        useRuntimePasswordCache: Boolean,
        onChallenge: () -> Unit = {}
    ): String {
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
                if (useRuntimePasswordCache && !password.isNullOrBlank()) {
                    runtimeSessionPasswords[sessionId] = password
                }
                if (password.isNullOrBlank()) {
                    throw RuntimeException("Connection canceled while waiting for password.")
                }
            }
            try {
                val currentPassword = password
                updateSessionSnapshot(sessionId, host, mode, SessionStatus.CONNECTING, "Authenticating as ${host.username}...")
                client.authPasswordOrInteractive(host.username, currentPassword) { challenge ->
                    onChallenge()
                    awaitPasswordDecision(
                        host = host,
                        sessionId = sessionId,
                        mode = mode,
                        reason = challenge.instruction.ifBlank { "The server asks for more information to log in." },
                        // A person may need to fetch a code; give each question its own window.
                        deadlineMillis = System.currentTimeMillis() + INTERACTIVE_LOGIN_TIMEOUT_MS,
                        allowSave = false,
                        challenge = challenge
                    )?.password
                }
                if (savePassword) {
                    runCatching { SecurityManager.storeHostPassword(host.id, currentPassword) }
                        .onSuccess {
                            // Keep the host's saved-password flag in step with the vault, or export
                            // and delete would treat the password as absent.
                            val repository = (application as SSHPeachesApplication).container.repository
                            serviceScope.launch { runCatching { repository.setHostHasPassword(host.id, true) } }
                        }
                        .onFailure { t ->
                            SessionLogBus.emit(
                                SessionLogBus.Entry(
                                    hostId = sessionId,
                                    level = SessionLogBus.LogLevel.WARN,
                                    message = "Signed in, but the password could not be saved: ${t.message ?: t.javaClass.simpleName}"
                                )
                            )
                        }
                }
                return currentPassword
            } catch (_: LoginCanceledException) {
                throw RuntimeException("Connection canceled while waiting for password.")
            } catch (_: UserAuthException) {
                if (useRuntimePasswordCache) {
                    clearRuntimeSessionPassword(sessionId)
                }
                failedAttempts += 1
                if (failedAttempts >= MAX_PASSWORD_PROMPT_ATTEMPTS) {
                    throw ConnectionFailure(
                        "Authentication failed after $MAX_PASSWORD_PROMPT_ATTEMPTS attempts.",
                        ConnectionFailureKind.AUTH_PASSWORD
                    )
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
                throw ConnectionFailure("No identity key selected for this host.", ConnectionFailureKind.KEY_MISSING)
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
                throw ConnectionFailure(
                    "Selected identity key is unavailable. Re-import the key and try again.",
                    ConnectionFailureKind.KEY_MISSING
                )
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
        val publicKey = runCatching {
            SecurityManager.getIdentityPublicKey(identityId)
        }.getOrNull()
        return try {
            UiDebugLog.action(
                "SessionService.authenticateWithIdentity",
                "sessionId=$sessionId, hostId=${host.id}, identityId=${identityId.take(8)}, " +
                    "required=$required, hasPassphrase=${!keyPassphrase.isNullOrBlank()}, " +
                    "hasPublicKey=${!publicKey.isNullOrBlank()}"
            )
            var providerSource = "sshj-memory"
            val keyProvider = Ed25519IdentityKeyProvider.load(
                client = client,
                privateKeyMaterial = privateKey,
                publicKeyMaterial = publicKey,
                passphrase = keyPassphrase
            )?.also {
                providerSource = "ed25519-pkcs8-direct"
            } ?: client.loadPrivateKeyFromMemory(privateKey, keyPassphrase)
            val keyType = runCatching { keyProvider.type.toString() }.getOrDefault("unknown")
            UiDebugLog.result(
                "SessionService.identityKeyProvider",
                true,
                "sessionId=$sessionId, hostId=${host.id}, identityId=${identityId.take(8)}, " +
                    "source=$providerSource, type=$keyType"
            )
            client.authPublickey(host.username, keyProvider)
            UiDebugLog.result(
                "SessionService.authenticateWithIdentity",
                client.isAuthenticated,
                "sessionId=$sessionId, hostId=${host.id}, identityId=${identityId.take(8)}, " +
                    "source=$providerSource, type=$keyType"
            )
            client.isAuthenticated
        } catch (authError: UserAuthException) {
            UiDebugLog.result(
                "SessionService.authenticateWithIdentity",
                false,
                "sessionId=$sessionId, hostId=${host.id}, identityId=${identityId.take(8)}, " +
                    "error=${authError.safeIdentityAuthSummary(host)}"
            )
            if (required) {
                throw ConnectionFailure("Identity authentication failed.", ConnectionFailureKind.AUTH_KEY, authError)
            }
            false
        }
    }

    private fun Throwable.safeIdentityAuthSummary(host: HostConnection): String {
        return generateSequence(this) { it.cause }
            .take(4)
            .joinToString(" <- ") { error ->
                val message = error.message
                    ?.sanitizeIdentityAuthLog(host)
                    ?.takeIf { it.isNotBlank() }
                listOfNotNull(error.javaClass.name, message).joinToString(" | ")
            }
    }

    private fun String.sanitizeIdentityAuthLog(host: HostConnection): String {
        var sanitized = this
        listOf(host.host to "<host>", host.username to "<user>", host.name to "<host-name>")
            .filter { (value, _) -> value.isNotBlank() }
            .forEach { (value, replacement) -> sanitized = sanitized.replace(value, replacement) }
        return sanitized
            .replace(Regex("(?i)(password|passphrase|secret)\\s*[:=]\\s*\\S+"), "\$1=<redacted>")
            .replace(Regex("-----BEGIN [^-]+-----[\\s\\S]*?-----END [^-]+-----"), "<private-key>")
            .replace(Regex("ssh-(rsa|ed25519|ecdsa-[^ ]+) [A-Za-z0-9+/=]+"), "ssh-$1 <public-key>")
    }

    private suspend fun startMoshServer(hostId: String, client: SSHClient): MoshConnect {
        return client.startSession().use { session ->
            val serverCommand = SettingsStore.moshServerCommand.first().ifBlank {
                DEFAULT_MOSH_SERVER_COMMAND
            }
            val command = session.exec(serverCommand)
            val stdout = AvailableLineReader(command.inputStream.bufferedReader(StandardCharsets.UTF_8))
            val stderr = AvailableLineReader(command.errorStream.bufferedReader(StandardCharsets.UTF_8))
            val combined = StringBuilder()
            val deadlineMillis = System.currentTimeMillis() + MOSH_BOOTSTRAP_TIMEOUT_MS
            var connect: MoshConnect? = null
            while (connect == null && System.currentTimeMillis() < deadlineMillis) {
                var readAny = false
                stdout.nextLine()?.let { line ->
                    readAny = true
                    combined.appendBounded(line)
                    connect = parseMoshConnectLine(line)
                }
                if (connect == null) {
                    stderr.nextLine()?.let { line ->
                        readAny = true
                        combined.appendBounded(line)
                        connect = parseMoshConnectLine(line)
                    }
                }
                // The command already exited without a MOSH CONNECT line (mosh-server missing, a locale error): no
                // point waiting out the deadline.
                if (connect == null && !readAny && command.exitStatus != null) break
                if (connect == null) {
                    delay(40)
                }
            }
            // mosh-server prints its pid after forking ("[mosh-server detached, pid = 1234]"); keep it so closing
            // the session can stop the server.
            var serverPid: Int? = null
            if (connect != null) {
                val pidDeadline = System.currentTimeMillis() + MOSH_SERVER_PID_WAIT_MS
                while (serverPid == null && System.currentTimeMillis() < pidDeadline) {
                    val line = stderr.nextLine() ?: stdout.nextLine()
                    if (line != null) {
                        serverPid = MOSH_SERVER_PID_PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    } else if (command.exitStatus != null) {
                        break
                    } else {
                        delay(20)
                    }
                }
            }
            runCatching { command.close() }
            connect ?: run {
                // A prompt such as "[sudo] password for x: " never ends with a newline; show it too.
                stdout.pendingText().takeIf { it.isNotBlank() }?.let { combined.appendBounded(it) }
                stderr.pendingText().takeIf { it.isNotBlank() }?.let { combined.appendBounded(it) }
                val detail = combined.toString().trim().ifBlank {
                    "No MOSH CONNECT reply received."
                }
                throw ConnectionFailure("Failed to start mosh-server. $detail", ConnectionFailureKind.MOSH_SERVER)
            }
            // mosh-client only takes a numeric address, so a host saved by name failed ("Bad IP address"). Use the
            // address this SSH connection reached, which is also the one mosh-server -s bound to.
            val address = client.remoteAddress?.hostAddress ?: client.remoteHostname
            connect!!.copy(address = address, serverPid = serverPid)
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
        terminalEmulation: TerminalEmulation,
        existingInputQueue: BoundedMoshInputQueue? = null,
        existingInputWriterJob: Job? = null
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
                moshConnect.address,
                moshConnect.port.toString()
            )
        } else {
            arrayOf(
                "mosh-client",
                moshConnect.address,
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
        val inputQueue = existingInputQueue ?: BoundedMoshInputQueue(
            capacityBytes = MOSH_INPUT_BUFFER_BYTES,
            chunkBytes = MOSH_INPUT_CHUNK_BYTES
        )
        val inputWriterJob = existingInputWriterJob ?: startMoshInputWriter(hostId, inputQueue)
        return MoshBinding(
            session = session,
            moshConnect = moshConnect,
            terminalEmulation = terminalEmulation,
            inputQueue = inputQueue,
            inputWriterJob = inputWriterJob
        )
    }

    private fun startMoshInputWriter(
        hostId: String,
        inputQueue: BoundedMoshInputQueue
    ): Job = serviceScope.launch(Dispatchers.IO) {
        while (isActive) {
            val payload = inputQueue.take() ?: break
            var offset = 0
            while (offset < payload.size && isActive) {
                val session = activeConnections[hostId]?.moshBinding?.session
                if (session == null || !session.isRunning) {
                    delay(MOSH_INPUT_RETRY_DELAY_MS)
                    continue
                }
                val chunkSize = min(MOSH_INPUT_CHUNK_BYTES, payload.size - offset)
                val wrote = session.writeWithTimeout(
                    payload,
                    offset,
                    chunkSize,
                    MOSH_INPUT_WRITE_TIMEOUT_MS
                )
                if (wrote) {
                    offset += chunkSize
                } else {
                    delay(MOSH_INPUT_RETRY_DELAY_MS)
                }
            }
            if (offset == payload.size) {
                emitShellStreamDiagnostic(hostId = hostId, direction = "TX", payload = payload, size = payload.size)
            }
        }
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
            if (finishedSession.exitStatus == 0) {
                // A clean exit: the remote shell ended (exit, Ctrl-D) and mosh-server shut down with it. Restarting
                // the client against the gone server only showed a dead screen marked "reconnected".
                closeSessionAfterShellExit(hostId, "Mosh session ended")
                return@launch
            }
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

    /**
     * Ends a Mosh session on both sides. SIGKILL alone left mosh-server and the remote shell running forever (it has
     * no network timeout by default): SIGTERM lets mosh-client tell the server to shut down, and the server is also
     * stopped over SSH while that connection still works. SIGKILL follows if the client doesn't exit.
     */
    private fun shutDownMosh(client: SSHClient?, binding: MoshBinding) {
        stopMoshServer(client, binding.moshConnect)
        val session = binding.session
        if (!session.isRunning) return
        runCatching { android.system.Os.kill(session.pid, android.system.OsConstants.SIGTERM) }
        serviceScope.launch {
            delay(MOSH_CLIENT_SHUTDOWN_GRACE_MS)
            withContext(Dispatchers.Main) { session.finishIfRunning() }
        }
    }

    private fun stopMoshServer(client: SSHClient?, moshConnect: MoshConnect) {
        val pid = moshConnect.serverPid ?: return
        val ssh = client?.takeIf { it.isConnected && it.isAuthenticated } ?: return
        runRemoteCommand(ssh, moshServerStopCommand(pid), timeoutSeconds = 3)
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
                    // A new mosh-client can't rejoin the old server: it starts again from the first state and
                    // packet number, which the server ignores (the screen froze and typing went nowhere), and it
                    // would reuse the session key with the same nonces. Start a new server instead, over the SSH
                    // connection that started the first one. That opens a new remote shell.
                    val sshClient = connection.client?.takeIf { it.isConnected && it.isAuthenticated }
                    if (sshClient == null || attempt > MOSH_RESTART_MAX_ATTEMPTS) {
                        stopMoshServer(connection.client, moshBinding.moshConnect)
                        closeSessionAfterShellExit(
                            hostId,
                            "Mosh stopped (client exit code ${moshBinding.session.exitStatus}) and could not be " +
                                "restarted" + if (sshClient == null) " without its SSH connection" else ""
                        )
                        return@launch
                    }
                    if (attempt > 1) {
                        val backoff = min(
                            MOSH_RECONNECT_MAX_DELAY_MS,
                            MOSH_RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 2).coerceAtMost(6))
                        )
                        delay(backoff)
                    }
                    val newBinding = runCatching {
                        stopMoshServer(sshClient, moshBinding.moshConnect)
                        val moshConnect = startMoshServer(hostId, sshClient)
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = hostId,
                                level = SessionLogBus.LogLevel.WARN,
                                message = "Started a new Mosh server; the remote shell is a new one."
                            )
                        )
                        startMoshClient(
                            hostId = hostId,
                            host = connection.host,
                            moshConnect = moshConnect,
                            terminalEmulation = moshBinding.terminalEmulation,
                            existingInputQueue = moshBinding.inputQueue,
                            existingInputWriterJob = moshBinding.inputWriterJob
                        )
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        SessionLogBus.emit(
                            SessionLogBus.Entry(
                                hostId = hostId,
                                level = SessionLogBus.LogLevel.WARN,
                                message = "Mosh reconnect attempt $attempt failed: ${error.message ?: "unknown error"}"
                            )
                        )
                    }.getOrNull() ?: continue
                    val replaced = activeConnections.replace(
                        hostId,
                        connection,
                        connection.copy(moshBinding = newBinding)
                    )
                    if (!replaced) {
                        newBinding.session.finishIfRunning()
                        return@launch
                    }
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
            // The emulator is written on the main thread; reading it here raced with output and resizes.
            val transcript = withContext(Dispatchers.Main) {
                session.emulator?.screen?.transcriptTextWithoutJoinedLines.orEmpty()
            }
            setShellOutputSnapshot(hostId, transcript)
            moshTranscriptPublishJobs.remove(hostId)
        }
    }

    private fun parseMoshConnectLine(line: String): MoshConnect? {
        val match = MOSH_CONNECT_PATTERN.find(line) ?: return null
        val port = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val key = match.groupValues.getOrNull(2).orEmpty()
        if (key.isBlank()) return null
        return MoshConnect(port = port, key = key, address = "")
    }

    /**
     * Returns whole lines from what has already arrived, never waiting for more. readLine() blocked until a newline,
     * so a password prompt with none hung mosh startup past its deadline.
     */
    private class AvailableLineReader(private val reader: BufferedReader) {
        private val pending = StringBuilder()

        fun nextLine(): String? {
            while (true) {
                val newline = pending.indexOf('\n')
                if (newline >= 0 || pending.length >= MAX_PENDING_LINE_CHARS) {
                    val end = if (newline >= 0) newline else pending.length
                    val line = pending.substring(0, end).removeSuffix("\r")
                    pending.delete(0, if (newline >= 0) end + 1 else end)
                    return line
                }
                if (!runCatching { reader.ready() }.getOrDefault(false)) return null
                val ch = runCatching { reader.read() }.getOrDefault(-1)
                if (ch < 0) return null
                pending.append(ch.toChar())
            }
        }

        fun pendingText(): String = pending.toString()

        private companion object {
            const val MAX_PENDING_LINE_CHARS = 4_096
        }
    }

    /** Keeps server text for an error message to the last few kilobytes, however much the server prints. */
    private fun StringBuilder.appendBounded(line: String) {
        appendLine(line)
        if (length > MAX_MOSH_BOOTSTRAP_OUTPUT_CHARS) delete(0, length - MAX_MOSH_BOOTSTRAP_OUTPUT_CHARS)
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
                    versionLabel = osVersionLabel(unameVersion)
                )
                uname.contains("FreeBSD", ignoreCase = true) ||
                    uname.contains("OpenBSD", ignoreCase = true) ||
                    uname.contains("NetBSD", ignoreCase = true) -> OsMetadata.Known(
                    family = OsFamily.BSD,
                    versionLabel = osVersionLabel(unameVersion)
                )
                uname.contains("Linux", ignoreCase = true) -> OsMetadata.Known(
                    family = OsFamily.GENERIC,
                    versionLabel = osVersionLabel(unameVersion)
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

    fun fetchHostSystemInfo(
        host: HostConnection,
        onResult: (HostSystemInfo) -> Unit
    ) {
        serviceScope.launch {
            val info = runCatching {
                collectHostSystemInfo(host)
            }.getOrElse { error ->
                HostSystemInfo(error = error.message?.takeIf { it.isNotBlank() } ?: "Unable to collect system info.")
            }
            withContext(Dispatchers.Main) {
                onResult(info)
            }
        }
    }

    private suspend fun collectHostSystemInfo(host: HostConnection): HostSystemInfo {
        val existing = clientForSavedHost(host.id)
        if (existing != null) {
            val raw = runRemoteCommand(existing, HostSystemInfoCollector.REMOTE_COMMAND, timeoutSeconds = 8)
                ?.trim()
                .orEmpty()
            if (raw.isBlank()) {
                return HostSystemInfo(error = "The remote host returned no system information.")
            }
            return HostSystemInfoCollector.parse(raw)
        }
        return collectHostSystemInfoWithNewConnection(host)
    }

    private fun clientForSavedHost(savedHostId: String): SSHClient? {
        return activeConnections.values.firstOrNull { connection ->
            connection.host.id == savedHostId && connection.client?.isConnected == true
        }?.client
    }

    private suspend fun collectHostSystemInfoWithNewConnection(host: HostConnection): HostSystemInfo {
        val autoTrust = SettingsStore.autoTrustHostKeyEnabled.first()
        var client: SSHClient? = null
        return try {
            client = SshClientProvider.createClient(
                context = this,
                host = host,
                autoTrustUnknownHostKey = autoTrust,
                onHostKeyPrompt = null
            )
            client.connect(host.host, host.port)
            val authenticated = authenticateForSystemInfo(client, host)
            if (!authenticated) {
                return HostSystemInfo(
                    error = "Could not authenticate. Open an SSH session once, or save a password or key for this host."
                )
            }
            val raw = runRemoteCommand(client, HostSystemInfoCollector.REMOTE_COMMAND, timeoutSeconds = 8)
                ?.trim()
                .orEmpty()
            if (raw.isBlank()) {
                HostSystemInfo(error = "The remote host returned no system information.")
            } else {
                HostSystemInfoCollector.parse(raw)
            }
        } catch (error: Exception) {
            HostSystemInfo(
                error = error.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to connect. Open an SSH session to collect live system info."
            )
        } finally {
            runCatching { client?.disconnect() }
        }
    }

    private fun authenticateForSystemInfo(client: SSHClient, host: HostConnection): Boolean {
        val savedPassword = runCatching { SecurityManager.getHostPassword(host.id) }.getOrNull()
        fun tryIdentity(): Boolean = runCatching { authenticatePublicKeyQuietly(client, host) }.getOrDefault(false)
        fun tryPassword(): Boolean {
            if (savedPassword.isNullOrBlank()) return false
            return runCatching {
                client.authPasswordOrInteractive(host.username, savedPassword)
                client.isAuthenticated
            }.getOrDefault(false)
        }
        return when (host.preferredAuth) {
            AuthMethod.IDENTITY -> tryIdentity()
            AuthMethod.PASSWORD -> tryPassword()
            AuthMethod.PASSWORD_AND_IDENTITY -> tryIdentity() || tryPassword()
        }
    }

    private fun authenticatePublicKeyQuietly(client: SSHClient, host: HostConnection): Boolean {
        val identityId = host.preferredIdentityId?.takeIf { it.isNotBlank() } ?: return false
        val privateKey = runCatching { SecurityManager.getIdentityKey(identityId) }.getOrNull()
        if (privateKey.isNullOrBlank()) return false
        val keyPassphrase = runCatching { SecurityManager.getIdentityKeyPassphrase(identityId) }.getOrNull()
        val publicKey = runCatching { SecurityManager.getIdentityPublicKey(identityId) }.getOrNull()
        val keyProvider = Ed25519IdentityKeyProvider.load(
            client = client,
            privateKeyMaterial = privateKey,
            publicKeyMaterial = publicKey,
            passphrase = keyPassphrase
        ) ?: client.loadPrivateKeyFromMemory(privateKey, keyPassphrase)
        client.authPublickey(host.username, keyProvider)
        return client.isAuthenticated
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
                    val stdout = runCatching {
                        collectCommandOutput(cmd, cmd.inputStream, REMOTE_OUTPUT_DRAIN_MS)
                    }.getOrNull().orEmpty()
                    if (stdout.isNotBlank()) {
                        stdout
                    } else {
                        runCatching { collectCommandOutput(cmd, cmd.errorStream, REMOTE_OUTPUT_DRAIN_MS) }.getOrNull()
                    }
                }
            }
        }.getOrNull()
    }

    /** A server-reported OS version as shown on host cards and saved in exports: short, one line, printable. */
    private fun osVersionLabel(raw: String): String? =
        raw.filter { !it.isISOControl() }.trim().take(MAX_OS_VERSION_LABEL_LENGTH).ifBlank { null }

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
                versionLabel = osVersionLabel(version)
            )
            id.isNotBlank() || like.contains("linux", ignoreCase = true) -> OsMetadata.Known(
                family = OsFamily.GENERIC,
                versionLabel = osVersionLabel(version)
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
                token == "centos" -> OsFamily.REDHAT
                token == "arch" || token == "archlinux" -> OsFamily.ARCH
                token == "linuxmint" || token == "mint" -> OsFamily.MINT
                token == "suse" || token.startsWith("opensuse") || token == "sles" -> OsFamily.SUSE
                token == "rhel" || token == "redhat" || token.contains("redhat") -> OsFamily.REDHAT
                token == "gentoo" -> OsFamily.GENERIC
                token == "pop" || token == "pop_os" || token == "pop!_os" -> OsFamily.GENERIC
                token == "manjaro" -> OsFamily.GENERIC
                token == "elementary" || token == "elementaryos" -> OsFamily.GENERIC
                token == "peppermint" -> OsFamily.GENERIC
                token == "lite" || token == "linuxlite" -> OsFamily.GENERIC
                token == "zorin" -> OsFamily.GENERIC
                token == "rocky" || token == "rockylinux" -> OsFamily.REDHAT
                token == "alma" || token == "almalinux" -> OsFamily.REDHAT
                token == "asahi" -> OsFamily.GENERIC
                token == "nixos" -> OsFamily.GENERIC
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
        terminalEmulation: TerminalEmulation,
        reuseTerminalEngine: TermuxTerminalEngine? = null,
        attachTmux: Boolean = false
    ): ShellBinding {
        val session = client.startSession()
        // A reconnect opens the new shell at the screen's size. The screen only reports a size when it changes,
        // so a shell left at the default thought it had more rows than the screen: tmux then drew its status
        // line below the bottom and scrolled the screen up a line on every redraw.
        val (columns, rows) = shellSizes[hostId] ?: (DEFAULT_SHELL_COLUMNS to DEFAULT_SHELL_ROWS)
        val allocated = runCatching {
            session.allocatePTY(
                terminalEmulation.ptyName,
                columns,
                rows,
                0,
                0,
                mutableMapOf<PTYMode, Int>()
            )
        }.isSuccess
        if (!allocated) {
            session.allocateDefaultPTY()
        }
        // sshj's session channel is both a Command and a Shell, so an exec'd tmux behaves like a shell.
        val shell = if (attachTmux) session.exec(TMUX_ATTACH_COMMAND) as Session.Shell else session.startShell()
        return bindShell(hostId, SshTerminalChannel(session, shell), reuseTerminalEngine)
    }

    /**
     * Copies the Boost helper to the server over [client] while the session is used over SSH; one copy per server at
     * a time. It ends with the connection: a later session starts it again.
     */
    private fun installBoostHelperBehind(sessionId: String, hostKey: String, client: SSHClient) {
        // A copy on a connection that has since died is on its way out: the new connection's waits its turn.
        boostInstalls[hostKey]?.let { (copying, job) -> if (job.isActive && copying === client) return }
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = sessionId,
                level = SessionLogBus.LogLevel.INFO,
                message = "Boost is being set up on this server in the background; this terminal uses SSH."
            )
        )
        boostInstalls[hostKey] = client to serviceScope.launch(Dispatchers.IO) {
            val result = runCatching {
                client.newSFTPClient().use { sftp -> BoostHelper.install(this@SessionService, client, sftp) }
            }
            if (!client.isConnected) return@launch
            val reason = result.exceptionOrNull()?.let { (it as? BoostUnavailable)?.message ?: (it.message ?: it.javaClass.simpleName) }
            Log.i(FILE_BOOST_TAG, "Boost helper copy for $sessionId: ${reason ?: "done"}")
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = if (reason == null) {
                        "Boost is set up on this server: the next terminal uses it."
                    } else {
                        "Boost couldn't be set up on this server: $reason."
                    }
                )
            )
        }
    }

    /**
     * Opens the terminal through Boost when it's on for terminals: the helper runs the login shell, reached over
     * UDP (or TCP), so the terminal survives the SSH connection and network changes. Null, and the caller opens an
     * SSH shell, when it's off, the host attaches tmux, or the helper can't start or be reached; a failure is
     * remembered for a while, so connecting to that host again doesn't wait on it.
     */
    private suspend fun openBoostShell(
        sessionId: String,
        host: HostConnection,
        client: SSHClient,
        terminalEmulation: TerminalEmulation,
        reuseTerminalEngine: TermuxTerminalEngine?
    ): ShellBinding? {
        val settings = sftpTransferSettings
        if (!settings.boost || !settings.boostTerminal || host.attachTmux || !BoostHelper.clientAvailable) return null
        val hostKey = "${host.username}@${host.host}:${host.port}"
        if ((boostTerminalUnavailableUntil[hostKey] ?: 0L) > SystemClock.elapsedRealtime()) return null
        fun unavailable(reason: String, error: Throwable?) {
            metrics.recordBoost("terminal", com.majordaftapps.sshpeaches.app.diagnostics.PerformanceMetrics.boostReason(reason))
            boostTerminalUnavailableUntil[hostKey] = SystemClock.elapsedRealtime() + BOOST_TERMINAL_RETRY_MS
            Log.i(FILE_BOOST_TAG, "Boost terminal unavailable for $sessionId", error)
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Boost isn't available for the terminal: $reason. Using SSH."
                )
            )
        }
        val (columns, rows) = shellSizes[sessionId] ?: (DEFAULT_SHELL_COLUMNS to DEFAULT_SHELL_ROWS)
        // A terminal never waits for the helper's copy to the server (megabytes, minutes on a phone's uplink): without
        // the helper it opens over SSH at once, the copy runs behind it, and the next terminal is on Boost.
        val start = serviceScope.async(Dispatchers.IO) {
            client.newSFTPClient().use { sftp ->
                BoostHelper.start(
                    this@SessionService, client, sftp, settings.boostTcpFallback, settings.boostPortRange, shells = true,
                    install = false
                )
            }
        }
        val helper = try {
            withTimeoutOrNull(BOOST_TERMINAL_START_TIMEOUT_MS) { start.await() }
        } catch (error: CancellationException) {
            serviceScope.launch { runCatching { start.await().close() } }
            throw error
        } catch (error: BoostNotInstalled) {
            installBoostHelperBehind(sessionId, hostKey, client)
            metrics.recordBoost("terminal", "setup_slow")
            return null
        } catch (error: Exception) {
            unavailable((error as? BoostUnavailable)?.message ?: (error.message ?: error.javaClass.simpleName), error)
            return null
        }
        if (helper == null) {
            serviceScope.launch { runCatching { start.await().close() } }
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = sessionId,
                    level = SessionLogBus.LogLevel.INFO,
                    message = "Boost is still being set up on this server; this terminal uses SSH."
                )
            )
            metrics.recordBoost("terminal", "setup_slow")
            return null
        }
        val channel = try {
            withContext(Dispatchers.IO) {
                BoostTerminalChannel(helper, columns, rows, terminalEmulation.ptyName) { state, detail ->
                    onBoostTerminalState(sessionId, state, detail)
                }
            }
        } catch (error: Exception) {
            runCatching { helper.close() }
            if (error is CancellationException) throw error
            unavailable(error.message ?: error.javaClass.simpleName, error)
            return null
        }
        boostTerminalStates[sessionId] = "connected"
        SessionLogBus.emit(
            SessionLogBus.Entry(
                hostId = sessionId,
                level = SessionLogBus.LogLevel.INFO,
                message = "Terminal on Boost (${helper.transport.uppercase()})."
            )
        )
        metrics.recordBoost("terminal", helper.transport.lowercase())
        return bindShell(sessionId, channel, reuseTerminalEngine)
    }

    /** Logs a Boost terminal's connection changes: once when it starts reconnecting, once when it's back. */
    private fun onBoostTerminalState(sessionId: String, state: String, detail: String) {
        val previous = boostTerminalStates.put(sessionId, state)
        if (previous == state) return
        val (level, message) = when (state) {
            "reconnecting" -> SessionLogBus.LogLevel.WARN to "Terminal reconnecting over Boost ($detail)..."
            "connected" -> SessionLogBus.LogLevel.INFO to "Terminal back on Boost (${detail.uppercase()})."
            "lost" -> {
                reportNetworkIssue("boost_terminal_lost", sessionId, null, mapOf("detail" to detail))
                SessionLogBus.LogLevel.WARN to "Boost lost the terminal: $detail."
            }
            else -> return
        }
        SessionLogBus.emit(SessionLogBus.Entry(hostId = sessionId, level = level, message = message))
    }

    /** Wires a terminal channel to the screen: a terminal engine (a reconnect's old one), and the I/O loops. */
    private fun bindShell(
        hostId: String,
        channel: TerminalChannel,
        reuseTerminalEngine: TermuxTerminalEngine?
    ): ShellBinding {
        val terminalEngine = reuseTerminalEngine ?: TermuxTerminalEngine(
            onWriteToRemote = {},
            onTerminalDiagnostic = { message ->
                if (!diagnosticsEnabled) return@TermuxTerminalEngine
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.DEBUG,
                        message = message
                    )
                )
            }
        )
        val outputReaderJob = startShellOutputReader(hostId, channel, terminalEngine)
        val inputQueue = CoroutineChannel<ByteArray>(CoroutineChannel.UNLIMITED)
        val inputWriterJob = startShellInputWriter(hostId, channel, inputQueue)
        val resizeQueue = CoroutineChannel<Pair<Int, Int>>(CoroutineChannel.CONFLATED)
        val resizeWriterJob = startShellResizeWriter(hostId, channel, resizeQueue)
        return ShellBinding(
            channel = channel,
            terminalEngine = terminalEngine,
            outputReaderJob = outputReaderJob,
            inputQueue = inputQueue,
            inputWriterJob = inputWriterJob,
            resizeQueue = resizeQueue,
            resizeWriterJob = resizeWriterJob
        )
    }

    private fun startShellOutputReader(
        hostId: String,
        channel: TerminalChannel,
        terminalEngine: TermuxTerminalEngine
    ): Job = serviceScope.launch(Dispatchers.IO) {
        val buffer = ByteArray(2048)
        val outputProcessor = TerminalOutputProcessor(
            appendToTerminal = terminalEngine::appendIncoming,
            onParserFailure = { error ->
                TelemetryInitializer.recordNonFatal("terminal_parser", error)
                emitShellLifecycleDiagnostic(
                    hostId,
                    "Terminal parser error: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
                )
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Terminal output could not be rendered; keeping the SSH connection open."
                    )
                )
            }
        )
        while (isActive) {
            val read = try {
                channel.inputStream.read(buffer)
            } catch (error: Exception) {
                if (!isActive) return@launch
                emitShellLifecycleDiagnostic(
                    hostId,
                    "RX stream error: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
                )
                if (requestReconnect(hostId, "Connection lost.")) return@launch
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Shell stream ended: ${error.message ?: "unknown error"}"
                    )
                )
                closeSessionAfterShellExit(
                    hostId,
                    "Shell stream ended: ${error.message ?: "unknown error"}"
                )
                return@launch
            }

            if (read < 0) {
                if (!isActive || hostId in reconnectingSessions) return@launch
                emitShellLifecycleDiagnostic(hostId, "RX EOF")
                closeSessionAfterShellExit(hostId, "Shell exited (EOF)")
                return@launch
            }
            if (read == 0) continue
            lastShellOutputAtMs[hostId] = SystemClock.elapsedRealtime()

            val payload = buffer.copyOf(read)
            outputProcessor.process(payload)

            appendShellOutput(hostId, String(payload, StandardCharsets.UTF_8))
            emitShellStreamDiagnostic(hostId = hostId, direction = "RX", payload = payload, size = payload.size)
        }
    }

    private fun startShellResizeWriter(
        hostId: String,
        channel: TerminalChannel,
        resizeQueue: CoroutineChannel<Pair<Int, Int>>
    ): Job = serviceScope.launch(Dispatchers.IO) {
        for ((columns, rows) in resizeQueue) {
            try {
                channel.resize(columns, rows)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isActive) return@launch
                SessionLogBus.emit(
                    SessionLogBus.Entry(
                        hostId = hostId,
                        level = SessionLogBus.LogLevel.WARN,
                        message = "Failed to resize remote terminal: ${error.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    private fun startShellInputWriter(
        hostId: String,
        channel: TerminalChannel,
        inputQueue: CoroutineChannel<ByteArray>
    ): Job = serviceScope.launch(Dispatchers.IO) {
        for (payload in inputQueue) {
            runCatching {
                writeShellInputPayload(hostId, channel, payload)
                emitShellStreamDiagnostic(hostId = hostId, direction = "TX", payload = payload, size = payload.size)
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

    private suspend fun writeShellInputPayload(hostId: String, channel: TerminalChannel, payload: ByteArray) {
        val timedOut = AtomicBoolean(false)
        val watchdog = serviceScope.launch(Dispatchers.IO) {
            delay(SHELL_INPUT_WRITE_TIMEOUT_MS)
            timedOut.set(true)
            SessionLogBus.emit(
                SessionLogBus.Entry(
                    hostId = hostId,
                    level = SessionLogBus.LogLevel.ERROR,
                    message = "Timed out writing ${payload.size} byte(s) to shell input; the connection stalled."
                )
            )
            // Closing the shell would wait on the same stalled link, so drop the connection under it instead.
            activeConnections[hostId]
                ?.takeIf { it.shellBinding?.channel === channel }
                ?.client
                ?.let(::dropDeadConnection)
                ?: runCatching { channel.close() }
        }
        try {
            channel.outputStream.write(payload)
            channel.outputStream.flush()
            if (timedOut.get()) {
                throw TimeoutException("Timed out writing to shell input")
            }
        } finally {
            watchdog.cancel()
        }
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
        unansweredInputChecks.remove(hostId)?.cancel()
        lastShellOutputAtMs.remove(hostId)
        shellOutputPublishJobs.remove(hostId)?.cancel()
        moshTranscriptPublishJobs.remove(hostId)?.cancel()
        pendingShellSnapshots.remove(hostId)
        pendingShellOutputChunks.remove(hostId)
        shellOutput.update { current -> current - hostId }
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
        shellOutput.update { outputByHost -> outputByHost + (hostId to next) }
    }

    private fun setRemoteDirectorySnapshot(hostId: String, snapshot: RemoteDirectorySnapshot) {
        if (remoteDirectories.value[hostId] == snapshot) return
        remoteDirectories.update { current -> current + (hostId to snapshot) }
    }

    private fun clearRemoteDirectoryForHost(hostId: String) {
        recentListings.remove(hostId)
        if (!remoteDirectories.value.containsKey(hostId)) return
        remoteDirectories.update { current -> current - hostId }
    }

    private fun setFileTransferProgress(
        hostId: String,
        progress: FileTransferProgress?,
        expectedOperationId: String? = null,
        requireActive: Boolean = false
    ): Boolean {
        var previous: FileTransferProgress? = null
        synchronized(fileTransferStateLock) {
            val current = fileTransferProgress.value
            val existing = current[hostId]
            previous = existing
            if (expectedOperationId != null) {
                if (existing?.operationId != expectedOperationId) return false
                if (requireActive && existing.isActive != true) return false
            }
            if (existing == progress) return false
            fileTransferProgress.value = if (progress == null) {
                current - hostId
            } else {
                current + (hostId to progress)
            }
        }
        if (progress == null || previous == null || !progress.hasStarted || !progress.isActive) {
            transferNotificationRefreshJobs.remove(hostId)?.cancel()
            updateSessionNotifications()
        } else {
            scheduleTransferNotificationRefresh(hostId)
        }
        return true
    }

    private fun clearFileTransferProgress(hostId: String) {
        forgetTransfers(hostId)
        transferNotificationRefreshJobs.remove(hostId)?.cancel()
        if (!fileTransferProgress.value.containsKey(hostId)) {
            updateSessionNotifications()
            return
        }
        fileTransferProgress.update { current -> current - hostId }
        updateSessionNotifications()
    }

    private fun scheduleTransferNotificationRefresh(hostId: String) {
        if (transferNotificationRefreshJobs[hostId]?.isActive == true) return
        transferNotificationRefreshJobs[hostId] = serviceScope.launch {
            delay(TRANSFER_NOTIFICATION_UPDATE_INTERVAL_MS)
            transferNotificationRefreshJobs.remove(hostId)
            if (fileTransferProgress.value[hostId]?.isActive == true) {
                updateSessionNotifications()
            }
        }
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
        val line = if (direction == "TX" && !BuildConfig.DEBUG) {
            // Typed input includes passwords, and diagnostics end up in copied logs and problem reports.
            "SHELL-DIAG $direction#$seq bytes=$size"
        } else {
            val previewSize = minOf(size, SHELL_DIAG_PREVIEW_BYTES)
            val hex = payload.toHexPreview(previewSize)
            val ascii = payload.toAsciiPreview(previewSize)
            val truncated = if (size > previewSize) " (+${size - previewSize} bytes)" else ""
            "SHELL-DIAG $direction#$seq bytes=$size hex=$hex ascii=\"$ascii\"$truncated"
        }
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
        message: String?,
        failureKind: ConnectionFailureKind? = null
    ) {
        // Under a live Boost terminal a reconnect is SSH's alone: the terminal stays usable, so the session stays
        // active instead of showing the reconnect screen.
        val boostCarriesOn = status == SessionStatus.CONNECTING &&
            (keptBoostShells[sessionId]?.channel as? BoostTerminalChannel)?.alive == true
        val snapshot = SessionSnapshot(
            hostId = sessionId,
            host = host,
            mode = mode,
            status = if (boostCarriesOn) SessionStatus.ACTIVE else status,
            statusMessage = if (boostCarriesOn) "Terminal on Boost; SSH reconnecting ($message)" else message,
            failureKind = failureKind
        )
        synchronized(sessionStateLock) {
            if (!activeJobs.containsKey(sessionId)) {
                UiDebugLog.result(
                    "sessionSnapshot",
                    false,
                    "ignored-inactive sessionId=$sessionId, mode=$mode, status=$status"
                )
                return
            }
            sessionSnapshots.upsertSessionSnapshot(snapshot)
            updateSessionNotifications()
            publishWidgetSessionState()
        }
        UiDebugLog.result(
            "sessionSnapshot",
            true,
            "sessionId=$sessionId, mode=$mode, status=$status, message=${message ?: "none"}"
        )
    }

    private fun generateSessionId(hostId: String, mode: ConnectionMode): String =
        "$hostId|${mode.name}|${UUID.randomUUID()}"

    private fun removeSessionSnapshot(hostId: String) {
        synchronized(sessionStateLock) {
            sessionSnapshots.removeSessionSnapshot(hostId)
            updateSessionNotifications()
            publishWidgetSessionState()
        }
    }

    private fun publishWidgetSessionState() {
        WidgetSessionStore.write(this, sessionSnapshots.value)
        HostWidgets.updateAllAsync(this)
    }

    private fun updateSessionNotifications() {
        // Copied, not toSet(): Kotlin's toSet() of a one-element collection reads the size, then iterator().next(),
        // which throws if a session ends in between (the key set of a ConcurrentHashMap changes under it). Same for
        // the other snapshots of activeJobs.
        val activeSessionIds = HashSet(activeJobs.keys)
        val snapshots = sessionSnapshots.value
            .filter { it.hostId in activeSessionIds }
            .sortedWith(
                compareBy<SessionSnapshot> { it.host.name.lowercase() }
                    .thenBy { it.hostId }
            )
        publishSessionNotifications(snapshots)
    }

    internal fun publishSessionNotifications(snapshots: List<SessionSnapshot>) {
        synchronized(notificationStateLock) {
            runCatching {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (snapshots.isEmpty()) {
                    sessionNotificationIdentities.values.forEach { identity ->
                        nm.cancel(SESSION_NOTIFICATION_TAG, identity.notificationId)
                    }
                    sessionNotificationIdentities.clear()
                    nextSessionNotificationId = FIRST_SESSION_NOTIFICATION_ID
                    nextSessionDisplayNumber = 1
                    if (foregroundNotificationStarted) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        foregroundNotificationStarted = false
                    }
                    return
                }

                val desiredSessionIds = snapshots.mapTo(mutableSetOf()) { it.hostId }
                val staleSessionIds = sessionNotificationIdentities.keys - desiredSessionIds
                staleSessionIds.forEach { sessionId ->
                    sessionNotificationIdentities.remove(sessionId)?.let { identity ->
                        nm.cancel(SESSION_NOTIFICATION_TAG, identity.notificationId)
                    }
                }

                val entries = snapshots.map { snapshot ->
                    snapshot to notificationIdentityFor(snapshot.hostId)
                }
                startForeground(
                    SUMMARY_NOTIFICATION_ID,
                    buildSessionSummaryNotification(entries)
                )
                foregroundNotificationStarted = true
                entries.forEach { (snapshot, identity) ->
                    nm.notify(
                        SESSION_NOTIFICATION_TAG,
                        identity.notificationId,
                        buildSessionNotification(snapshot, identity)
                    )
                }
            }.onFailure { error ->
                UiDebugLog.error(
                    "updateSessionNotifications",
                    error,
                    "activeJobs=${activeJobs.size}, snapshots=${sessionSnapshots.value.size}"
                )
            }
        }
    }

    private fun buildSessionSummaryNotification(
        entries: List<Pair<SessionSnapshot, SessionNotificationIdentity>>
    ): Notification {
        val count = entries.size
        val singleSessionId = entries.singleOrNull()?.first?.hostId
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (singleSessionId != null) {
                action = ACTION_OPEN_SESSION
                data = sessionActionUri("open", singleSessionId)
                putExtra(EXTRA_HOST_ID, singleSessionId)
            } else {
                action = ACTION_OPEN_SESSIONS
                data = Uri.parse("sshpeaches://sessions/open")
            }
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            SUMMARY_OPEN_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAllIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP_ALL
            data = Uri.parse("sshpeaches://sessions/disconnect-all")
        }
        val pendingStopAll = PendingIntent.getService(
            this,
            SUMMARY_STOP_REQUEST_CODE,
            stopAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (count == 1) "1 active SSH session" else "$count active SSH sessions"
        val openActionTitle = if (singleSessionId != null) "Open" else "Open sessions"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        entries.take(MAX_SUMMARY_SESSION_LINES).forEach { (snapshot, identity) ->
            style.addLine(
                "${sessionNotificationTitle(snapshot, identity)}: ${sessionNotificationText(snapshot)}"
            )
        }
        if (count > MAX_SUMMARY_SESSION_LINES) {
            style.addLine("+${count - MAX_SUMMARY_SESSION_LINES} more")
        }
        val notificationIcon = resolveNotificationIcon()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Tap to manage active connections")
            .setStyle(style)
            .setSmallIcon(notificationIcon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(SESSION_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentIntent(pendingOpen)
            .addAction(notificationIcon, openActionTitle, pendingOpen)
            .addAction(notificationIcon, "Disconnect all", pendingStopAll)
            .build()
    }

    private fun buildSessionNotification(
        snapshot: SessionSnapshot,
        identity: SessionNotificationIdentity
    ): Notification {
        val activeTransfer = fileTransferProgress.value[snapshot.hostId]
            ?.takeIf { it.isActive }
        val text = sessionNotificationText(snapshot)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_SESSION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = sessionActionUri("open", snapshot.hostId)
            putExtra(EXTRA_HOST_ID, snapshot.hostId)
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            identity.notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, SessionService::class.java).apply {
            action = ACTION_STOP_SESSION
            data = sessionActionUri("disconnect", snapshot.hostId)
            putExtra(EXTRA_HOST_ID, snapshot.hostId)
        }
        val pendingStop = PendingIntent.getService(
            this,
            identity.notificationId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationIcon = resolveNotificationIcon()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(sessionNotificationTitle(snapshot, identity))
            .setContentText(text)
            .setSmallIcon(notificationIcon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSubText("${snapshot.host.username}@${snapshot.host.host}:${snapshot.host.port}")
            .setGroup(SESSION_NOTIFICATION_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setSortKey(identity.displayNumber.toString().padStart(8, '0'))
            .setContentIntent(pendingOpen)
            .addAction(notificationIcon, "Open", pendingOpen)
            .addAction(notificationIcon, "Disconnect", pendingStop)
        val transferPercent = activeTransfer?.progressPercent
        when {
            activeTransfer == null -> builder.setProgress(0, 0, false)
            transferPercent != null && activeTransfer.hasStarted ->
                builder.setProgress(100, transferPercent, false)
            else -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun sessionNotificationText(snapshot: SessionSnapshot): String =
        fileTransferProgress.value[snapshot.hostId]
            ?.takeIf { it.isActive }
            ?.let { it.statusMessage() + if (it.queuedTransfers > 0) " · ${it.queuedTransfers} waiting" else "" }
            ?: when (snapshot.status) {
            SessionStatus.ACTIVE -> snapshot.statusMessage ?: "Connected"
            SessionStatus.CONNECTING -> snapshot.statusMessage ?: "Connecting"
            SessionStatus.ERROR -> snapshot.statusMessage ?: "Connection error"
        }

    private fun sessionNotificationTitle(
        snapshot: SessionSnapshot,
        identity: SessionNotificationIdentity
    ): String {
        val hostLabel = snapshot.host.name.ifBlank {
            "${snapshot.host.username}@${snapshot.host.host}"
        }
        val modeLabel = when (snapshot.mode) {
            ConnectionMode.SSH -> if (snapshot.host.useMosh) "Mosh" else "SSH"
            ConnectionMode.SFTP,
            ConnectionMode.SCP -> "SFTP"
        }
        return "$hostLabel • $modeLabel • Session ${identity.displayNumber}"
    }

    private fun sessionActionUri(action: String, sessionId: String): Uri =
        Uri.Builder()
            .scheme("sshpeaches")
            .authority("session")
            .appendPath(action)
            .appendPath(sessionId)
            .build()

    private fun notificationIdentityFor(sessionId: String): SessionNotificationIdentity =
        sessionNotificationIdentities.getOrPut(sessionId) {
            check(nextSessionNotificationId < Int.MAX_VALUE) {
                "Session notification ID space exhausted"
            }
            SessionNotificationIdentity(
                notificationId = nextSessionNotificationId++,
                displayNumber = nextSessionDisplayNumber++
            )
        }

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
        return R.drawable.ic_notification_logo
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
        private const val FILE_BOOST_TAG = "BoostHelper"
        private const val BACKGROUND_STOP_POLL_MS = 30_000L
        private const val CHANNEL_ID = "sessions"
        private const val SUMMARY_NOTIFICATION_ID = 42_000
        private const val FIRST_SESSION_NOTIFICATION_ID = 43_000
        private const val SUMMARY_OPEN_REQUEST_CODE = 19_241
        private const val SUMMARY_STOP_REQUEST_CODE = 19_242
        private const val SESSION_NOTIFICATION_TAG = "sshpeaches_session"
        private const val SESSION_NOTIFICATION_GROUP = "sshpeaches_active_sessions"
        private const val MAX_SUMMARY_SESSION_LINES = 5
        private const val ACTION_STOP_ALL = "com.majordaftapps.sshpeaches.app.service.ACTION_STOP_ALL"
        const val ACTION_STOP_SESSION = "com.majordaftapps.sshpeaches.app.service.ACTION_STOP_SESSION"
        const val ACTION_OPEN_SESSION = "com.majordaftapps.sshpeaches.app.service.ACTION_OPEN_SESSION"
        const val ACTION_OPEN_SESSIONS = "com.majordaftapps.sshpeaches.app.service.ACTION_OPEN_SESSIONS"
        const val EXTRA_HOST_ID = "extra_host_id"
        private const val CONNECTION_ATTEMPT_TIMEOUT_MS = 60_000L
        private const val DEFAULT_SHELL_COLUMNS = 120
        private const val DEFAULT_SHELL_ROWS = 40
        private const val MAX_RECONNECT_ATTEMPTS = 8
        /** Folders each file browser keeps for going back, forward or up. */
        private const val RECENT_LISTINGS = 5
        /** Finished transfers the pane keeps per session. */
        private const val MAX_FINISHED_TRANSFERS = 30
        /** How long a session waits for its Boost terminal to start on a server that has the helper. */
        private const val BOOST_TERMINAL_START_TIMEOUT_MS = 20_000L
        /** How long a host whose Boost terminal couldn't start uses SSH without trying again. */
        private const val BOOST_TERMINAL_RETRY_MS = 30 * 60_000L
        private const val MAX_EDITABLE_FILE_BYTES = 1024L * 1024L
        /** How long to keep reading a background command's output after it ran (or timed out). */
        private const val REMOTE_OUTPUT_DRAIN_MS = 2_000L
        private const val PORT_FORWARD_CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKS_HANDSHAKE_TIMEOUT_MS = 10_000
        private const val MAX_OS_VERSION_LABEL_LENGTH = 32
        private const val PARTIAL_DOWNLOAD_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        // Attach to (or create) tmux session "sshpeaches"; without tmux, fall back to the login shell.
        // Wrapped in sh -c so it works whatever the user's login shell is (fish included).
        internal const val TMUX_ATTACH_COMMAND =
            "sh -c 'command -v tmux >/dev/null 2>&1 && exec tmux new-session -A -s sshpeaches; exec \"\${SHELL:-/bin/sh}\" -l'"
        private const val RECONNECT_NETWORK_WAIT_MS = 60_000L
        private val RECONNECT_BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000)
        private const val TIMEOUT_WAITING_FOR_INPUT_MESSAGE = "Connection timed out while waiting for user input."
        private const val MAX_PASSWORD_PROMPT_ATTEMPTS = 3
        private const val MAX_SHELL_OUTPUT_CHARS = 32_000
        private const val DEFAULT_PORT_FORWARD_BUFFER_SIZE = 8 * 1024
        private const val SHELL_OUTPUT_PUBLISH_INTERVAL_MS = 40L
        private const val MOSH_SNAPSHOT_PUBLISH_INTERVAL_MS = 40L
        // Progress redraws the screen and the notification; a few times a second reads as smooth.
        private const val TRANSFER_NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val FILE_TRANSFER_PROGRESS_UPDATE_INTERVAL_NANOS = 250_000_000L
        private const val PARTIAL_DOWNLOAD_SUFFIX = ".part"
        private const val TRANSFER_KEEPALIVE_MAX_UNANSWERED = 6
        private const val FILE_BOOST_RETRY_MS = 30_000L
        private const val FILE_BOOST_STREAMS = 16
        private const val COPY_BUFFER_BYTES = 1 shl 20
        private const val SLOW_OPERATION_WARN_MS = 250L
        private const val SHELL_DIAG_PREVIEW_BYTES = 96
        private const val SHELL_INPUT_WRITE_TIMEOUT_MS = 10_000L
        // 10 s: 5 s dropped healthy connections on networks whose round trips spike past it.
        private const val LIVENESS_PROBE_TIMEOUT_MS = 10_000L
        private const val UNANSWERED_INPUT_GRACE_MS = 3_000L
        private const val LIVENESS_PROBE_POLL_MS = 50L
        private const val LATENCY_SAMPLE_INTERVAL_MS = 5_000L
        private const val LATENCY_POLL_MS = 5L
        private const val LIVENESS_CHECK_MIN_INTERVAL_MS = 2_000L
        private const val MOSH_BOOTSTRAP_TIMEOUT_MS = 15_000L
        private const val MAX_MOSH_BOOTSTRAP_OUTPUT_CHARS = 4_096
        private const val MOSH_INPUT_WRITE_TIMEOUT_MS = 1_000L
        private const val MOSH_INPUT_RETRY_DELAY_MS = 50L
        private const val MOSH_INPUT_CHUNK_BYTES = 2 * 1024
        private const val MOSH_INPUT_BUFFER_BYTES = 2 * 1024 * 1024
        private const val MOSH_INTERACTIVE_INPUT_RESERVE_BYTES = 64 * 1024
        private const val MOSH_INTERACTIVE_INPUT_MAX_BYTES = 64
        private const val MOSH_DEFAULT_COLUMNS = 120
        private const val MOSH_DEFAULT_ROWS = 40
        private const val MOSH_TRANSCRIPT_ROWS = 2000
        private const val MOSH_RECONNECT_BASE_DELAY_MS = 1_000L
        private const val MOSH_RECONNECT_MAX_DELAY_MS = 15_000L
        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
        private val MOSH_SERVER_PID_PATTERN = Regex("""mosh-server detached, pid = (\d+)""")
        private const val MOSH_SERVER_PID_WAIT_MS = 1_500L
        private const val MOSH_CLIENT_SHUTDOWN_GRACE_MS = 3_000L
        private const val MOSH_RESTART_MAX_ATTEMPTS = 3
        private val MOSH_CONNECT_PATTERN = Regex("""MOSH CONNECT\s+(\d+)\s+([A-Za-z0-9+/=]+)""")
    }

    private class ConnectionTimeoutException(message: String) : RuntimeException(message)

    private data class SessionNotificationIdentity(
        val notificationId: Int,
        val displayNumber: Int
    )

    private class FileTransferCancelledException :
        CancellationException("File transfer cancelled")

    private class ActiveFileTransfer(
        val sessionId: String,
        val operationId: String,
        val operationLabel: String,
        val initialProgress: FileTransferProgress
    ) {
        val cancellationRequested = AtomicBoolean(false)

        /** Set once the transfer has its turn in the session's queue. */
        @Volatile
        var running = false

        /** The chunked transfer moving the file, once it has started; woken when the network comes back. */
        @Volatile
        var engine: SftpSegmentedTransfer? = null

        @Volatile
        var latestProgress: FileTransferProgress = initialProgress

        @Volatile
        var job: Job? = null

        @Volatile
        var workerThread: Thread? = null

        /** When it got its turn (elapsed realtime), and what moved it: "sftp", "boost", or "mixed" after a fallback. */
        @Volatile
        var startedAtMs = 0L

        @Volatile
        var route = "sftp"

        /** Times its connections dropped and it retried, and why the last time. */
        @Volatile
        var retries = 0

        @Volatile
        var lastRetryReason: String? = null

        private var sampleAtNanos = 0L
        private var sampleBytes = 0L
        private var rate = 0.0

        /** Smoothed bytes per second, updated at most twice a second; null until there's a sample. */
        @Synchronized
        fun sampleRate(bytes: Long): Long? {
            val now = System.nanoTime()
            if (sampleAtNanos == 0L || bytes < sampleBytes) {
                sampleAtNanos = now
                sampleBytes = bytes
                return null
            }
            val elapsed = (now - sampleAtNanos) / 1e9
            if (elapsed < 0.5) return rate.takeIf { it > 0 }?.toLong()
            val instant = (bytes - sampleBytes) / elapsed
            rate = if (rate == 0.0) instant else rate * 0.7 + instant * 0.3
            sampleAtNanos = now
            sampleBytes = bytes
            return rate.toLong()
        }
    }

    private data class ActiveConnection(
        val host: HostConnection,
        val mode: ConnectionMode,
        val client: SSHClient?,
        val shellBinding: ShellBinding?,
        val moshBinding: MoshBinding?,
        val sftpBinding: SftpBinding?,
        val portForwardBindings: List<PortForwardBinding>,
        val transferPool: SftpTransferPool? = null
    )

    private data class ShellBinding(
        val channel: TerminalChannel,
        val terminalEngine: TermuxTerminalEngine,
        val outputReaderJob: Job,
        val inputQueue: CoroutineChannel<ByteArray>,
        val inputWriterJob: Job,
        val resizeQueue: CoroutineChannel<Pair<Int, Int>>,
        val resizeWriterJob: Job
    )

    private data class MoshBinding(
        val session: TerminalSession,
        val moshConnect: MoshConnect,
        val terminalEmulation: TerminalEmulation,
        val inputQueue: BoundedMoshInputQueue,
        val inputWriterJob: Job
    )

    private data class SftpBinding(
        val client: SFTPClient
    )

    private data class PortForwardBinding(
        val forwardId: String,
        val summary: String,
        val close: () -> Unit
    )

    private data class MoshConnect(
        val port: Int,
        val key: String,
        /** Numeric address mosh-client connects to. */
        val address: String,
        val serverPid: Int? = null
    ) {
        override fun toString() = "MoshConnect(port=$port, address=$address, serverPid=$serverPid)"
    }

    private data class PasswordResponse(
        val password: String?,
        val savePassword: Boolean
    )

    data class SessionSnapshot(
        val hostId: String,
        val host: HostConnection,
        val mode: ConnectionMode,
        val status: SessionStatus,
        val statusMessage: String?,
        val failureKind: ConnectionFailureKind? = null
    )

    data class RemoteDirectorySnapshot(
        val path: String,
        val entries: List<RemoteDirectoryEntry>,
        val refreshToken: Long = 0L,
        /** False while a big folder is still being read: [entries] is what has arrived so far. */
        val complete: Boolean = true,
        /** A recently seen listing shown at once (going back, forward or up); the folder is being listed again. */
        val fromCache: Boolean = false
    )

    data class RemoteDirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val absolutePath: String = "",
        val modifiedAtEpochMillis: Long? = null,
        val permissionSummary: String = "",
        val isSymbolicLink: Boolean = false,
        val linkTargetPath: String? = null,
        val linkTargetIsDirectory: Boolean? = null,
        val isBrokenLink: Boolean = false
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
        val allowSave: Boolean,
        val title: String = "Password Required",
        val fieldLabel: String = "Password",
        /** Show the answer while typing (the server marked it as not secret, e.g. some 2FA codes). */
        val echo: Boolean = false,
        /** A keyboard-interactive question other than the password; its answer is never cached. */
        val isChallenge: Boolean = false
    )

    enum class SessionStatus { CONNECTING, ACTIVE, ERROR }

}

/** A server answering a global request with SSH_MSG_REQUEST_FAILURE (as OpenSSH does for keepalives) is alive. */
internal fun isGlobalRequestRefusal(error: Throwable): Boolean {
    val message = error.message ?: return false
    return error is ConnectionException && error.cause == null &&
        message.startsWith("Global request [") && message.endsWith("] failed")
}
