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
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.TransferSettingsOverride
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reconnect, drop and latency scenarios through an impairing proxy (tools/network-impairment/netproxy.py on the
 * host, steered by the NETCTL lines this test logs, which netctl-watch.sh copies into its control file) in front of a
 * real OpenSSH server. Needs -e netHost/netPort/netUser/netPassword (the proxy's address as the emulator sees it);
 * skipped otherwise. Each scenario logs a timeline of the session's status (tag NetScenario) so app versions can be
 * compared, and fails only when the session doesn't recover.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveNetworkResilienceTest {
    private val args = InstrumentationRegistry.getArguments()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var gen = 0
    private var marker = 0

    private fun netctl(
        mode: String = "pass", newConn: String = "pass", once: Boolean = false, delayMs: Int = 0, jitterMs: Int = 0,
        reset: Boolean = false
    ) {
        gen++
        val json = JSONObject().put("mode", mode).put("new_conn", newConn).put("once", once)
            .put("delay_ms", delayMs).put("jitter_ms", jitterMs).put("reset", reset).put("gen", gen)
        Log.i("NetCtl", json.toString())
        Thread.sleep(1_500) // the host watcher applies it within about a second
    }

    private inner class Timeline(val service: SessionService, val sessionId: String) {
        private val start = System.nanoTime()
        private val events = mutableListOf<String>()
        @Volatile private var running = true
        private var last = ""
        private val thread = Thread {
            while (running) {
                val s = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
                val now = (s?.status?.name ?: "GONE") + " " + (s?.statusMessage ?: "")
                if (now != last) {
                    last = now
                    synchronized(events) { events += "%6.1fs %s".format((System.nanoTime() - start) / 1e9, now.take(140)) }
                }
                Thread.sleep(100)
            }
        }.apply { start() }
        fun note(text: String) = synchronized(events) { events += "%6.1fs -- %s".format((System.nanoTime() - start) / 1e9, text) }
        fun stop(name: String) {
            running = false; thread.join()
            Log.i("NetScenario", "== $name\n" + synchronized(events) { events.joinToString("\n") })
        }
    }

    private fun status(service: SessionService, id: String) = service.sessionsFlow().value.firstOrNull { it.hostId == id }

    /** Sends a fresh marker and waits for the shell to print it. True when the shell answered within [timeoutMs]. */
    private fun shellAnswers(service: SessionService, id: String, timeoutMs: Long): Boolean {
        val n = ++marker
        val deadline = System.currentTimeMillis() + timeoutMs
        var sentAt = 0L
        while (System.currentTimeMillis() < deadline) {
            // Not listed yet right after startSession; a session that has ended stays gone, so wait it out.
            val s = status(service, id)
            if (s != null && s.status == SessionStatus.ACTIVE && System.currentTimeMillis() - sentAt > 5_000) {
                service.sendShellInput(id, "echo NETOK-\$((1000+$n))\r")
                sentAt = System.currentTimeMillis()
            }
            if (service.shellOutputFlow().value[id]?.contains("NETOK-${1000 + n}") == true) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun scenario(name: String, body: (SessionService, String, Timeline) -> Unit) {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val id = "net-$name"
        withSessionService { service ->
            netctl()
            val host = HostConnection(
                id = id, name = name, host = args.getString("netHost")!!, port = args.getString("netPort")!!.toInt(),
                username = args.getString("netUser")!!, preferredAuth = AuthMethod.PASSWORD, hasPassword = false
            )
            val timeline = Timeline(service, id)
            try {
                service.startSession(
                    requestedSessionId = id, host = host, mode = ConnectionMode.SSH,
                    passwordOverride = args.getString("netPassword"), autoTrustUnknownHostKey = true,
                    hostKeyPromptEnabled = false
                )
                check(shellAnswers(service, id, 60_000)) { "never connected" }
                timeline.note("connected and shell answers")
                body(service, id, timeline)
            } finally {
                netctl()
                timeline.stop(name)
                service.stopSession(id)
                Thread.sleep(1_000)
            }
        }
    }

    private fun expectRecovery(service: SessionService, id: String, t: Timeline, withinMs: Long) {
        val ok = shellAnswers(service, id, withinMs)
        t.note(if (ok) "RECOVERED: shell answers" else "NOT RECOVERED within ${withinMs / 1000}s: ${status(service, id)?.status}")
        check(ok) { "did not recover" }
    }

    @Test fun s01_hardDropMidSession() = scenario("hard-drop") { service, id, t ->
        t.note("connection reset"); netctl(reset = true)
        expectRecovery(service, id, t, 60_000)
    }

    @Test fun s02_silentStallWhileForeground() = scenario("stall-foreground") { service, id, t ->
        t.note("link stalls"); netctl(mode = "stall")
        Thread.sleep(3_000)
        t.note("app comes to the foreground (liveness check)"); service.checkConnectionsAlive()
        Thread.sleep(12_000)
        t.note("link back"); netctl()
        expectRecovery(service, id, t, 90_000)
    }

    @Test fun s03_silentStallKeepaliveOnly() = scenario("stall-keepalive") { service, id, t ->
        t.note("link stalls (no app interaction)"); netctl(mode = "stall")
        Thread.sleep(100_000)
        t.note("link back after 100s"); netctl()
        expectRecovery(service, id, t, 120_000)
    }

    private fun stallDuringReconnect(service: SessionService, id: String, t: Timeline, stallAfterBytes: Int) {
        t.note("connection reset; the reconnect's first connection stalls after $stallAfterBytes server bytes")
        netctl(newConn = "stall_after:$stallAfterBytes", once = true, reset = true)
        expectRecovery(service, id, t, 180_000)
    }

    @Test fun s13_silentStallWhileTyping() = scenario("stall-typing") { service, id, t ->
        t.note("link stalls; the user keeps typing"); netctl(mode = "stall")
        val start = System.nanoTime()
        var reconnecting = false
        while (!reconnecting && System.nanoTime() - start < 120_000_000_000L) {
            service.sendShellBytes(id, "x".toByteArray())
            Thread.sleep(1_000)
            reconnecting = status(service, id)?.statusMessage.orEmpty().contains("Reconnecting")
        }
        t.note("reconnect began %.1fs after the first unanswered key".format((System.nanoTime() - start) / 1e9))
        Thread.sleep(3_000)
        t.note("link back"); netctl()
        service.sendShellBytes(id, byteArrayOf(0x15))
        expectRecovery(service, id, t, 90_000)
    }

    @Test fun s04_stallDuringReconnectBanner() = scenario("reconnect-stall-0") { s, id, t -> stallDuringReconnect(s, id, t, 0) }
    @Test fun s05_stallDuringReconnectKex() = scenario("reconnect-stall-1200") { s, id, t -> stallDuringReconnect(s, id, t, 1200) }
    @Test fun s06_stallDuringReconnectAfterKex() = scenario("reconnect-stall-2200") { s, id, t -> stallDuringReconnect(s, id, t, 2200) }
    @Test fun s07_stallDuringReconnectAuth() = scenario("reconnect-stall-3000") { s, id, t -> stallDuringReconnect(s, id, t, 3000) }
    @Test fun s08_stallDuringReconnectShell() = scenario("reconnect-stall-4500") { s, id, t -> stallDuringReconnect(s, id, t, 4500) }

    @Test fun s09_refusedOutage() = scenario("outage-refused-45s") { service, id, t ->
        t.note("current connection reset; new connections refused"); netctl(mode = "refuse", reset = true)
        Thread.sleep(45_000)
        t.note("server back"); netctl()
        expectRecovery(service, id, t, 150_000)
    }

    @Test fun s10_blackholeOutage() = scenario("outage-blackhole-75s") { service, id, t ->
        t.note("current connection reset; new connections hang (black hole)")
        netctl(newConn = "stall", reset = true)
        Thread.sleep(75_000)
        t.note("network back"); netctl()
        expectRecovery(service, id, t, 200_000)
    }

    @Test fun s11_slowNetworkReconnect() = scenario("slow-reconnect-rtt600") { service, id, t ->
        t.note("RTT 600 ms, then reset"); netctl(delayMs = 300)
        netctl(delayMs = 300, reset = true)
        expectRecovery(service, id, t, 120_000)
    }

    @Test fun s12_latency() = scenario("latency") { service, id, t ->
        for ((delay, jitter) in listOf(0 to 0, 100 to 30, 300 to 100)) {
            netctl(delayMs = delay, jitterMs = jitter)
            val samples = mutableListOf<Double>()
            repeat(12) {
                val start = System.nanoTime()
                check(shellAnswers(service, id, 30_000)) { "shell stopped answering" }
                samples += (System.nanoTime() - start) / 1e6
            }
            samples.sort()
            t.note("echo round trip at one-way ${delay}±${jitter} ms: median %.0f ms, max %.0f ms".format(samples[samples.size / 2], samples.last()))
        }
    }

    /** The file browser on the test server, with mid32.bin (32 MiB) in [PERF_DIR]. */
    private fun browserScenario(name: String, body: (SessionService, String, Timeline) -> Unit) {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val id = "net-$name"
        withSessionService { service ->
            netctl()
            val host = HostConnection(
                id = id, name = name, host = args.getString("netHost")!!, port = args.getString("netPort")!!.toInt(),
                username = args.getString("netUser")!!, preferredAuth = AuthMethod.PASSWORD, hasPassword = false
            )
            val timeline = Timeline(service, id)
            try {
                service.startSession(
                    requestedSessionId = id, host = host, mode = ConnectionMode.SCP,
                    passwordOverride = args.getString("netPassword"), autoTrustUnknownHostKey = true,
                    hostKeyPromptEnabled = false
                )
                check(waitUntil(60_000) { status(service, id)?.status == SessionStatus.ACTIVE }) { "never connected" }
                timeline.note("file browser connected")
                body(service, id, timeline)
            } finally {
                netctl()
                timeline.stop(name)
                service.stopSession(id)
                Thread.sleep(1_000)
            }
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Starts downloading mid32.bin, runs [disrupt] once a quarter of it has arrived, and waits for the result. */
    private fun downloadThrough(
        service: SessionService,
        id: String,
        t: Timeline,
        target: File,
        disrupt: (() -> Unit)?
    ): FileTransferStatus {
        target.delete()
        service.scpDownloadFile(id, "$PERF_DIR/mid32.bin", target.absolutePath)
        var sawRetry = false
        if (disrupt != null) {
            check(waitUntil(120_000) {
                val p = service.fileTransferProgressFlow().value[id]
                (p?.bytesTransferred ?: 0) > (8L shl 20) || p?.isTerminal == true
            }) { "download never started" }
            disrupt()
        }
        check(waitUntil(300_000) {
            val p = service.fileTransferProgressFlow().value[id]
            if (p?.retryMessage != null && !sawRetry) {
                sawRetry = true
                t.note("transfer: ${p.retryMessage}")
            }
            p?.isTerminal == true
        }) { "download never finished" }
        val p = checkNotNull(service.fileTransferProgressFlow().value[id])
        t.note("download ${p.status}${p.errorMessage?.let { ": $it" } ?: ""}")
        return p.status
    }

    private fun downloadSurvives(service: SessionService, id: String, t: Timeline, disrupt: () -> Unit) {
        val context = instrumentation.targetContext
        val disrupted = File(context.cacheDir, "net-disrupted.bin")
        val clean = File(context.cacheDir, "net-clean.bin")
        try {
            check(downloadThrough(service, id, t, disrupted, disrupt) == FileTransferStatus.SUCCEEDED) { "download gave up" }
            check(waitUntil(120_000) { status(service, id)?.status == SessionStatus.ACTIVE }) { "the browser didn't come back" }
            netctl()
            check(downloadThrough(service, id, t, clean, null) == FileTransferStatus.SUCCEEDED) { "clean download failed" }
            check(sha256(disrupted) == sha256(clean)) { "the disrupted download differs from a clean one" }
            t.note("disrupted download matches a clean one")
        } finally {
            disrupted.delete()
            clean.delete()
        }
    }

    @Test fun t01_downloadSurvivesHardDrop() = browserScenario("download-hard-drop") { service, id, t ->
        downloadSurvives(service, id, t) { t.note("every connection reset"); netctl(reset = true) }
    }

    @Test fun t02_downloadWaitsOutOutage() = browserScenario("download-outage-45s") { service, id, t ->
        downloadSurvives(service, id, t) {
            t.note("connections reset; new ones refused for 45 s"); netctl(mode = "refuse", reset = true)
            Thread.sleep(45_000)
            t.note("server back"); netctl()
        }
    }

    @Test fun t03_browserReconnectsInPlace() = browserScenario("browser-reconnect") { service, id, t ->
        service.listSftpDirectory(id, PERF_DIR)
        check(waitUntil(60_000) { service.remoteDirectoryFlow().value[id]?.path == PERF_DIR }) { "never listed $PERF_DIR" }
        val tokenBefore = service.remoteDirectoryFlow().value[id]?.refreshToken
        t.note("connection reset"); netctl(reset = true)
        // Nothing else lists here, so a new listing is the reconnect's (it can be done before netctl returns).
        check(waitUntil(120_000) {
            service.remoteDirectoryFlow().value[id]?.let { it.refreshToken != tokenBefore && it.complete } == true &&
                status(service, id)?.status == SessionStatus.ACTIVE
        }) { "never reconnected and listed again" }
        check(service.remoteDirectoryFlow().value[id]?.path == PERF_DIR) {
            "came back to ${service.remoteDirectoryFlow().value[id]?.path}, not $PERF_DIR"
        }
        t.note("RECOVERED: back in $PERF_DIR")
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val context = instrumentation.targetContext
        TransferSettingsOverride.apply(context)
        TransferSettingsOverride.withoutBoost(context)
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService(); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
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
        const val PERF_DIR = "/home/mosher/perf"
    }
}
