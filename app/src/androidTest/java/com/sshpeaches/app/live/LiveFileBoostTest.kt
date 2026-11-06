package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.service.BoostHelper
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.TransferSettingsOverride
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boost's file browser against a real server: the helper is uploaded and started over SSH, the app reaches it over UDP, and
 * listings and transfers through it must equal what SFTP gives. Needs -e boostHost/boostPort/boostUser/
 * boostPassword (an SSH server whose UDP ports the device can reach) and -e perfDir (see LiveSftpPerfTest).
 * Results are logged with tag NetScenario. The TCP fallback test also needs -e boostTcpPort N: the helper listens
 * there, and the device reaches it at -e boostTcpHost (default 127.0.0.1, for an adb reverse tcp:N tcp:N).
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveFileBoostTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir get() = args.getString("perfDir") ?: "/home/mosher/perf"
    private val results = mutableListOf<String>()
    private val logs = CopyOnWriteArrayList<Pair<String, String>>()
    private val logScope = CoroutineScope(Dispatchers.Default)

    private fun note(text: String) {
        results += text
        Log.i("NetScenario", text)
    }

    @Before
    fun throttle() {
        TransferSettingsOverride.apply(context)
    }

    @After
    fun restore() {
        runBlocking {
            SettingsStore.setBoost(false)
            SettingsStore.setBoostTcpFallback(true)
        }
        BoostHelper.dropUdpForTests = false
        BoostHelper.tcpPortForTests = 0
        BoostHelper.dialHostForTests = null
        BoostHelper.streamsForTests = 0
        logScope.cancel()
    }

    private fun secondsSince(start: Long) = (System.nanoTime() - start) / 1e9

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun open(service: SessionService, id: String) {
        service.startSession(
            requestedSessionId = id,
            host = HostConnection(
                id = id, name = id, host = args.getString("boostHost")!!, port = args.getString("boostPort")!!.toInt(),
                username = args.getString("boostUser")!!, preferredAuth = AuthMethod.PASSWORD
            ),
            mode = ConnectionMode.SCP, passwordOverride = args.getString("boostPassword"),
            autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
        )
        check(waitFor(60_000) {
            val s = service.sessionsFlow().value.firstOrNull { it.hostId == id }
            check(s?.status != SessionStatus.ERROR) { "session failed: ${s?.statusMessage}" }
            s?.status == SessionStatus.ACTIVE && service.remoteDirectoryFlow().value[id]?.complete == true
        }) { "never connected" }
    }

    private fun list(service: SessionService, id: String, path: String): Pair<Double, SessionService.RemoteDirectorySnapshot> {
        val before = service.remoteDirectoryFlow().value[id]
        val start = System.nanoTime()
        service.listSftpDirectory(id, path)
        check(waitFor(120_000) {
            val now = service.remoteDirectoryFlow().value[id]
            now != null && now !== before && now.complete && !now.fromCache && now.path.endsWith(path.substringAfterLast('/'))
        }) { "listing of $path never arrived" }
        return secondsSince(start) to service.remoteDirectoryFlow().value.getValue(id)
    }

    private fun transfer(service: SessionService, id: String, start: () -> Unit): Double {
        val begin = System.nanoTime()
        start()
        Thread.sleep(100)
        check(waitFor(300_000) {
            val p = service.fileTransferProgressFlow().value[id]
            check(p?.status != FileTransferStatus.FAILED) { "transfer failed: ${p?.errorMessage}" }
            val session = service.sessionsFlow().value.firstOrNull { it.hostId == id }
            check(session?.status != SessionStatus.ERROR) { "the session failed during the transfer: ${session?.statusMessage}" }
            p?.status == FileTransferStatus.SUCCEEDED
        }) { "transfer didn't finish" }
        return secondsSince(begin)
    }

    /** The session's "Boost on (…)" or "Boost isn't available: …" line. */
    private fun boostLine(id: String): String {
        check(waitFor(60_000) { logs.any { it.first == id && it.second.startsWith("Boost") } }) { "file boost never started: $logs" }
        return logs.first { it.first == id && it.second.startsWith("Boost") }.second
    }

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun boostListsAndTransfersLikeSftp() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        collectLogs()
        val sftpDown = File(context.cacheDir, "boost-sftp.bin")
        val boostDown = File(context.cacheDir, "boost-boost.bin")
        val upload = File(context.cacheDir, "boost-up.bin").apply { writeBytes(Random(9).nextBytes(4 * 1024 * 1024 + 321)) }
        val roundTrip = File(context.cacheDir, "boost-roundtrip.bin")
        val listings = mutableMapOf<String, List<SessionService.RemoteDirectoryEntry>>()
        withSessionService { service ->
            for (boost in listOf(false, true)) {
                runBlocking { SettingsStore.setBoost(boost) }
                Thread.sleep(500)
                val id = "boost-${if (boost) "on" else "off"}"
                val label = if (boost) "boost" else "sftp"
                try {
                    open(service, id)
                    if (boost) {
                        val line = boostLine(id)
                        note("boost: $line")
                        check(line.startsWith("Boost on")) { line }
                    }
                    for (folder in listOf("huge", "links")) {
                        val (seconds, snapshot) = list(service, id, "$dir/$folder")
                        note("$label: list $folder/ (${snapshot.entries.size}) %.2f s".format(seconds))
                        listings["$label/$folder"] = snapshot.entries
                    }
                    val target = if (boost) boostDown else sftpDown
                    target.delete()
                    val seconds = transfer(service, id) { service.scpDownloadFile(id, "$dir/mid.bin", target.absolutePath) }
                    note("$label: download 6 MiB %.2f s = %.1f MB/s".format(seconds, target.length() / 1e6 / seconds))
                    if (boost) {
                        val up = transfer(service, id) { service.scpUploadFile(id, upload.absolutePath, "$dir/boost-up.bin") }
                        note("boost: upload 4 MiB %.2f s".format(up))
                        roundTrip.delete()
                        transfer(service, id) { service.scpDownloadFile(id, "$dir/boost-up.bin", roundTrip.absolutePath) }
                    }
                } finally {
                    service.stopSession(id)
                    Thread.sleep(1_000)
                }
            }
        }
        // A link's "→" text: SFTP's bulk lookup can't read it on busybox servers (no find -printf), boost always can.
        fun comparable(entries: List<SessionService.RemoteDirectoryEntry>?) = entries.orEmpty().map { it.copy(linkTargetPath = null) }
        check(listings["boost/links"].orEmpty().filter { it.isSymbolicLink && !it.isBrokenLink }.all { !it.linkTargetPath.isNullOrBlank() }) {
            "boost listed links without their targets"
        }
        for (folder in listOf("huge", "links")) {
            check(comparable(listings["sftp/$folder"]) == comparable(listings["boost/$folder"])) {
                val sftp = comparable(listings["sftp/$folder"])
                val boost = comparable(listings["boost/$folder"])
                "boost listing of $folder differs: first difference ${sftp.zip(boost).firstOrNull { it.first != it.second }}"
            }
        }
        check(sha256(sftpDown) == sha256(boostDown)) { "boost download differs from SFTP's" }
        check(sha256(upload) == sha256(roundTrip)) { "boost upload round trip changed the file" }
        note("boost listings, download and upload match SFTP")
        listOf(sftpDown, boostDown, upload, roundTrip).forEach { it.delete() }
        Log.i("NetScenario", "== file-boost\n" + results.joinToString("\n"))
    }

    @Test
    fun boostFallsBackToTcpWhenUdpIsBlocked() {
        assumeTrue("needs -e boostHost and -e boostTcpPort", args.getString("boostHost") != null && args.getString("boostTcpPort") != null)
        BoostHelper.tcpPortForTests = args.getString("boostTcpPort")!!.toInt()
        BoostHelper.dialHostForTests = args.getString("boostTcpHost") ?: "127.0.0.1"
        val down = blockedUdpDownload(fallback = true) { line ->
            check(line.startsWith("Boost on (TCP)")) { line }
        }
        note("with UDP blocked, boost used TCP")
        down.delete()
        Log.i("NetScenario", "== file-boost-tcp\n" + results.joinToString("\n"))
    }

    @Test
    fun withoutTheTcpFallbackBlockedUdpLeavesSftp() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        val down = blockedUdpDownload(fallback = false) { line ->
            check(line.startsWith("Boost isn't available") && "UDP" in line) { line }
        }
        note("with UDP blocked and no TCP fallback, SFTP moved the file")
        down.delete()
        Log.i("NetScenario", "== file-boost-no-udp\n" + results.joinToString("\n"))
    }

    /**
     * Opens a session with UDP to the helper dropped and the TCP fallback [fallback], checks the boost line, and
     * downloads mid.bin, which must match the server's copy (by SHA-256).
     */
    private fun blockedUdpDownload(fallback: Boolean, checkLine: (String) -> Unit): File {
        collectLogs()
        val down = File(context.cacheDir, "boost-blocked.bin").apply { delete() }
        BoostHelper.dropUdpForTests = true
        val id = "boost-blocked-${if (fallback) "tcp" else "none"}"
        withSessionService { service ->
            runBlocking {
                SettingsStore.setBoost(true)
                SettingsStore.setBoostTcpFallback(fallback)
            }
            Thread.sleep(500)
            try {
                open(service, id)
                val line = boostLine(id)
                note("fallback ${if (fallback) "on" else "off"}: $line")
                checkLine(line)
                if (fallback) {
                    val (seconds, snapshot) = list(service, id, "$dir/huge")
                    note("tcp: list huge/ (${snapshot.entries.size}) %.2f s".format(seconds))
                    check(snapshot.entries.size == 20_000) { "listing has ${snapshot.entries.size} entries" }
                }
                val seconds = transfer(service, id) { service.scpDownloadFile(id, "$dir/mid.bin", down.absolutePath) }
                note("download 6 MiB %.2f s = %.1f MB/s".format(seconds, down.length() / 1e6 / seconds))
            } finally {
                service.stopSession(id)
                Thread.sleep(1_000)
            }
        }
        val expected = args.getString("midSha256") ?: MID_SHA256
        check(sha256(down) == expected) { "the download's SHA-256 is ${sha256(down)}, want $expected" }
        return down
    }

    /**
     * Cancelling a download, at any point of it, leaves the session as it was: no reconnect. The transfer thread
     * is interrupted to stop it, and an interrupt landing in SSH I/O on the session's own connection (the file's
     * stat, Boost's control messages) could break that connection.
     */
    @Test
    fun cancellingADownloadKeepsTheSession() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        collectLogs()
        val target = File(context.cacheDir, "boost-cancel.bin")
        withSessionService { service ->
            for (boost in listOf(true, false)) {
                runBlocking { SettingsStore.setBoost(boost) }
                Thread.sleep(500)
                val id = "boost-cancel-${if (boost) "on" else "off"}"
                val statuses = CopyOnWriteArrayList<SessionStatus>()
                val watcher = logScope.launch {
                    service.sessionsFlow().collect { list -> list.firstOrNull { it.hostId == id }?.let { statuses += it.status } }
                }
                try {
                    open(service, id)
                    if (boost) boostLine(id)
                    for (delayMs in listOf(0L, 300L, 1500L)) {
                        target.delete()
                        statuses.clear()
                        service.scpDownloadFile(id, "$dir/big.bin", target.absolutePath)
                        Thread.sleep(delayMs)
                        check(service.cancelFileTransfer(id)) { "nothing to cancel" }
                        check(waitFor(30_000) { service.fileTransferProgressFlow().value[id]?.isActive != true }) { "the cancel didn't end the transfer" }
                        Thread.sleep(3_000)
                        val reconnects = logs.filter { it.first == id && "Reconnecting" in it.second }
                        check(SessionStatus.CONNECTING !in statuses && reconnects.isEmpty()) {
                            "cancelling ${delayMs} ms into a ${if (boost) "Boost" else "SFTP"} download reconnected the session: $statuses $reconnects"
                        }
                        // And the session still works.
                        val (_, snapshot) = list(service, id, "$dir/many")
                        check(snapshot.entries.size >= 500) { "listing after the cancel: ${snapshot.entries.size}" }
                        note("${if (boost) "boost" else "sftp"}: cancelled $delayMs ms in, session kept")
                    }
                } finally {
                    watcher.cancel()
                    service.stopSession(id)
                    Thread.sleep(1_000)
                }
            }
        }
        target.delete()
        Log.i("NetScenario", "== cancel\n" + results.joinToString("\n"))
    }

    /**
     * The network going away and coming back (airplane mode on, then off) during a Boost download: the session
     * reconnects, and the download carries on (over SFTP if Boost's connection went with the network) and finishes
     * whole. 0.12.0 reported it as cancelled.
     */
    @Test
    fun aDownloadSurvivesTheNetworkDropping() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        collectLogs()
        val target = File(context.cacheDir, "boost-drop.bin").apply { delete() }
        val id = "boost-drop"
        withSessionService { service ->
            runBlocking { SettingsStore.setBoost(true) }
            Thread.sleep(500)
            try {
                open(service, id)
                note("drop: ${boostLine(id)}")
                service.scpDownloadFile(id, "$dir/big.bin", target.absolutePath)
                check(waitFor(60_000) { (service.fileTransferProgressFlow().value[id]?.bytesTransferred ?: 0L) > 0L }) { "the download never started" }
                service.simulateNetworkDropForTests()
                check(waitFor(300_000) { service.fileTransferProgressFlow().value[id]?.isActive == false }) { "the download never ended" }
                val end = service.fileTransferProgressFlow().value.getValue(id)
                check(end.status == FileTransferStatus.SUCCEEDED) {
                    "after the network dropped the download ended ${end.status}: ${end.errorMessage}; log: ${logs.filter { it.first == id }.map { it.second }.takeLast(12)}"
                }
                check(target.length() == 128L shl 20) { "the download is ${target.length()} bytes" }
                note("drop: the download carried on through the drop and finished")
            } finally {
                service.stopSession(id)
                target.delete()
            }
        }
        Log.i("NetScenario", "== network drop\n" + results.joinToString("\n"))
    }

    private fun collectLogs() {
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId.startsWith("boost-")) logs += it.hostId to it.message } }
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService()
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(10, TimeUnit.SECONDS))
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        /**
         * SHA-256 of mid.bin in the Mosh test container's perf folder, as seed-mosh-perf.sh on New enzu generates it
         * (pass -e midSha256 for another server).
         */
        const val MID_SHA256 = "9afe5fe11e176e1ef24c007c1d9197265df075a9a1ecc3d555bf4d62d23b33cb"
    }
}
