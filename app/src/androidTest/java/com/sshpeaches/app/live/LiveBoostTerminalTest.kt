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
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boost's terminal against a real server: the login shell runs in the helper, the terminal reaches it over UDP,
 * and it carries on, the same shell, while the session's SSH connection drops and reconnects under it. Takes the
 * -e boostHost/boostPort/boostUser/boostPassword arguments LiveFileBoostTest does. Results are logged with tag
 * NetScenario.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveBoostTerminalTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = CopyOnWriteArrayList<Pair<String, String>>()
    private val logScope = CoroutineScope(Dispatchers.Default)
    private val results = mutableListOf<String>()

    private fun note(text: String) {
        results += text
        Log.i("NetScenario", text)
    }

    @After
    fun restore() {
        runBlocking {
            SettingsStore.setBoost(false)
            SettingsStore.setBoostTerminal(true)
        }
        logScope.cancel()
    }

    private val host get() = HostConnection(
        id = "boost-term", name = "boost-term", host = args.getString("boostHost")!!,
        port = args.getString("boostPort")!!.toInt(), username = args.getString("boostUser")!!,
        preferredAuth = AuthMethod.PASSWORD
    )

    private fun waitFor(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(50)
        }
    }

    private fun output(service: SessionService, id: String) = service.shellOutputFlow().value[id].orEmpty()

    private fun open(service: SessionService, id: String) {
        service.startSession(
            requestedSessionId = id, host = host.copy(id = id, name = id), mode = ConnectionMode.SSH,
            passwordOverride = args.getString("boostPassword"), autoTrustUnknownHostKey = true,
            hostKeyPromptEnabled = false
        )
        waitFor(60_000, "the session") {
            val s = service.sessionsFlow().value.firstOrNull { it.hostId == id }
            check(s?.status != SessionStatus.ERROR) { "session failed: ${s?.statusMessage}" }
            s?.status == SessionStatus.ACTIVE
        }
    }

    /** Echoes [marker] and [words] (expanded by the shell) and returns the line that printed. */
    private fun run(service: SessionService, id: String, words: String, marker: String): String {
        service.sendShellInput(id, "echo $marker-\$((40+2)) $words\n")
        waitFor(20_000, "$marker in the terminal") { output(service, id).contains("$marker-42") }
        return output(service, id).lines().last { it.contains("$marker-42") }
    }

    /** sshpeaches-boost processes running on the test server, counted over a separate SSH connection. */
    private fun helpersOnServer(): Int = SshClientProvider.createClient(context, host).use { ssh ->
        ssh.connect(host.host, host.port)
        ssh.authPassword(host.username, args.getString("boostPassword"))
        ssh.startSession().use { session ->
            val command = session.exec("ps -o args | grep -c '^[^ ]*sshpeaches-boost serve'")
            val text = command.inputStream.bufferedReader().readText().trim()
            command.join(10, TimeUnit.SECONDS)
            text.toIntOrNull() ?: error("unexpected ps output: $text")
        }
    }

    @Test
    fun terminalOutlivesItsSshConnection() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        val id = "boost-term-1"
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) logs += it.hostId to it.message } }
        val statuses = CopyOnWriteArrayList<SessionStatus>()
        val helpersBefore = helpersOnServer()
        runBlocking {
            SettingsStore.setBoost(true)
            SettingsStore.setBoostTerminal(true)
        }
        Thread.sleep(500)
        withSessionService { service ->
            try {
                open(service, id)
                waitFor(30_000, "the terminal's transport") { logs.any { it.second.startsWith("Terminal on Boost") || "Using SSH" in it.second } }
                val line = logs.first { it.second.startsWith("Terminal on Boost") || "Using SSH" in it.second }.second
                note("boost terminal: $line")
                check(line.startsWith("Terminal on Boost (UDP)")) { line }
                val shellPid = run(service, id, "pid=\$\$ term=\$TERM", "first").substringAfter("pid=").substringBefore(' ')
                note("shell pid $shellPid")

                val messages = CopyOnWriteArrayList<String>()
                logScope.launch {
                    service.sessionsFlow().collect { list ->
                        list.firstOrNull { it.hostId == id }?.let {
                            statuses += it.status
                            messages += it.statusMessage.orEmpty()
                        }
                    }
                }
                val dropped = System.nanoTime()
                service.dropSshConnectionForTests(id)
                // Typing while SSH reconnects reaches the same shell at once.
                val during = run(service, id, "during-pid=\$\$", "during")
                note("typed while SSH reconnected: answered in %.2f s".format((System.nanoTime() - dropped) / 1e9))
                check(during.contains("during-pid=$shellPid")) { "a different shell answered: $during" }
                waitFor(90_000, "SSH to reconnect") {
                    val reconnecting = messages.indexOfFirst { it.startsWith("Terminal on Boost; SSH reconnecting") }
                    reconnecting >= 0 && messages.drop(reconnecting).any { it == "Interactive shell session ready" }
                }
                note("SSH reconnected under the terminal")
                val after = run(service, id, "after-pid=\$\$", "after")
                check(after.contains("after-pid=$shellPid")) { "a different shell answered after the reconnect: $after" }
                check(SessionStatus.CONNECTING !in statuses && SessionStatus.ERROR !in statuses) {
                    "the session didn't stay usable while SSH reconnected: $statuses"
                }
                note("same shell through the SSH reconnect; session stayed ${statuses.distinct()}")
            } finally {
                service.stopSession(id)
            }
            // Closing the session ends the shell, and with it the helper.
            val deadline = System.currentTimeMillis() + 20_000
            var helpers = helpersOnServer()
            while (helpers > helpersBefore && System.currentTimeMillis() < deadline) {
                Thread.sleep(1_000)
                helpers = helpersOnServer()
            }
            check(helpers <= helpersBefore) { "$helpers helper(s) still running after the session closed (was $helpersBefore)" }
            note("closing the session ended the helper")
        }
        Log.i("NetScenario", "== boost-terminal\n" + results.joinToString("\n"))
    }

    /** With Show connection latency on, a file browser is timed over SSH and a Boost terminal by its pings. */
    @Test
    fun latencyIsMeasuredOverSshAndBoost() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        runBlocking {
            SettingsStore.setShowConnectionLatency(true)
            SettingsStore.setBoost(true)
            SettingsStore.setBoostTerminal(true)
        }
        try {
            withSessionService { service ->
                val terminal = "boost-term-latency"
                val browser = "boost-browser-latency"
                try {
                    open(service, terminal)
                    service.startSession(
                        requestedSessionId = browser, host = host.copy(id = browser, name = browser), mode = ConnectionMode.SCP,
                        passwordOverride = args.getString("boostPassword"), autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
                    )
                    waitFor(60_000, "both latencies") {
                        val l = service.connectionLatencyFlow().value
                        l[terminal]?.lastMs != null && l[browser]?.lastMs != null
                    }
                    val l = service.connectionLatencyFlow().value
                    note("latency: terminal ${l[terminal]}, browser ${l[browser]}")
                    check(l.getValue(terminal).via.startsWith("Boost")) { "the Boost terminal was timed ${l[terminal]?.via}" }
                    check(l.getValue(browser).via == "SSH") { "the file browser was timed ${l[browser]?.via}" }
                    check(l.values.all { (it.lastMs ?: 0) in 0..2_000 }) { "implausible round trips: $l" }
                } finally {
                    service.stopSession(terminal)
                    service.stopSession(browser)
                }
            }
        } finally {
            runBlocking { SettingsStore.setShowConnectionLatency(false) }
        }
        Log.i("NetScenario", "== latency\n" + results.joinToString("\n"))
    }

    @Test
    fun boostTerminalOffUsesSsh() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        val id = "boost-term-off"
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) logs += it.hostId to it.message } }
        runBlocking {
            SettingsStore.setBoost(true)
            SettingsStore.setBoostTerminal(false)
        }
        Thread.sleep(500)
        withSessionService { service ->
            try {
                open(service, id)
                run(service, id, "true", "plain")
                check(logs.none { it.second.startsWith("Terminal on Boost") }) { "the terminal went over Boost: $logs" }
            } finally {
                service.stopSession(id)
            }
        }
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
}
