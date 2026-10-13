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
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.service.DefaultNetworkTransition
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Mosh session against a real OpenSSH + mosh-server, given with -e liveMoshHost/liveMoshPort/liveMoshUsername/
 * liveMoshPassword. After logging [PAUSE_MARKER] the test expects a script to freeze the server for longer than the
 * SSH keepalive allows (so the bootstrap SSH connection dies) and then resume it: Mosh has to carry on regardless.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveMoshTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val arguments = InstrumentationRegistry.getArguments()

    private val moshHost: String? get() = arguments.getString("liveMoshHost")
    private val moshPort: Int get() = arguments.getString("liveMoshPort")?.toIntOrNull() ?: 22
    private val moshUser: String get() = arguments.getString("liveMoshUsername") ?: "mosher"
    private val moshPassword: String? get() = arguments.getString("liveMoshPassword")

    private fun moshHostConnection(address: String = moshHost!!) = HostConnection(
        id = "mosh-host",
        name = "mosh",
        host = address,
        port = moshPort,
        username = moshUser,
        preferredAuth = AuthMethod.PASSWORD,
        useMosh = true
    )

    private fun startMosh(service: SessionService, sessionId: String, host: HostConnection) {
        service.startSession(
            requestedSessionId = sessionId,
            host = host,
            mode = ConnectionMode.SSH,
            passwordOverride = moshPassword,
            autoTrustUnknownHostKey = true,
            hostKeyPromptEnabled = false
        )
        waitForStatus(service, sessionId, SessionStatus.ACTIVE, timeoutMillis = 60_000)
        check(service.sessionsFlow().value.first { it.hostId == sessionId }.statusMessage.orEmpty().contains("Mosh")) {
            "Not a Mosh session: ${service.sessionsFlow().value}"
        }
    }

    /** mosh-server processes on the test server, counted over a separate SSH connection. */
    private fun moshServerCount(): Int = SshClientProvider.createClient(
        InstrumentationRegistry.getInstrumentation().targetContext,
        moshHostConnection().copy(useMosh = false)
    ).use { ssh ->
        ssh.connect(moshHost, moshPort)
        ssh.authPassword(moshUser, moshPassword)
        ssh.startSession().use { session ->
            val command = session.exec("ps -o args | grep -c '^[m]osh-server'")
            val output = command.inputStream.bufferedReader().readText().trim()
            command.join(10, TimeUnit.SECONDS)
            output.toIntOrNull() ?: error("Unexpected ps output: $output")
        }
    }

    private fun waitForMoshServerCount(expected: Int, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = moshServerCount()
        while (count != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000)
            count = moshServerCount()
        }
        check(count == expected) { "Expected $expected mosh-server process(es) on the server, found $count" }
    }

    private fun waitUntilGone(service: SessionService, sessionId: String, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (service.sessionsFlow().value.none { it.hostId == sessionId && it.status != SessionStatus.ERROR }) return
            Thread.sleep(250)
        }
        error("Session still open: ${service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }}")
    }

    private fun moshClientPid(service: SessionService, sessionId: String): Int {
        val connections = SessionService::class.java.getDeclaredField("activeConnections")
            .apply { isAccessible = true }.get(service) as Map<*, *>
        val connection = checkNotNull(connections[sessionId])
        val binding = connection.javaClass.getMethod("getMoshBinding").invoke(connection)!!
        val session = binding.javaClass.getMethod("getSession").invoke(binding) as com.termux.terminal.TerminalSession
        return session.pid
    }

    @Test
    fun exitEndsTheSessionAndTheServer() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        withSessionService { service ->
            val before = moshServerCount()
            startMosh(service, "mosh-exit", moshHostConnection())
            try {
                service.sendShellInput("mosh-exit", "echo \$((40+2))-up\r")
                waitForScreen(service, "mosh-exit", "42-up", 30_000)
                service.sendShellInput("mosh-exit", "exit\r")
                waitUntilGone(service, "mosh-exit", 30_000)
                // And it stays gone: no "reconnect" to the server that just shut down.
                Thread.sleep(5_000)
                waitUntilGone(service, "mosh-exit", 1_000)
                waitForMoshServerCount(before, 20_000)
            } finally {
                service.stopSession("mosh-exit")
            }
        }
    }

    @Test
    fun closingTheSessionShutsTheServerDown() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        withSessionService { service ->
            val before = moshServerCount()
            startMosh(service, "mosh-close", moshHostConnection())
            service.sendShellInput("mosh-close", "echo \$((40+3))-up\r")
            waitForScreen(service, "mosh-close", "43-up", 30_000)
            check(moshServerCount() == before + 1)
            service.stopSession("mosh-close")
            waitForMoshServerCount(before, 20_000)
        }
    }

    @Test
    fun hostSavedByNameWorks() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        // nip.io resolves "<ip>.nip.io" to <ip>; skip where there is no DNS.
        val name = "$moshHost.nip.io"
        assumeTrue("No DNS for $name", runCatching { InetAddress.getByName(name) }.isSuccess)
        withSessionService { service ->
            startMosh(service, "mosh-name", moshHostConnection(name))
            try {
                service.sendShellInput("mosh-name", "echo \$((40+4))-by-name\r")
                waitForScreen(service, "mosh-name", "44-by-name", 30_000)
            } finally {
                service.stopSession("mosh-name")
            }
        }
    }

    @Test
    fun networkChangeKeepsTheSession() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        withSessionService { service ->
            startMosh(service, "mosh-roam", moshHostConnection())
            try {
                SessionService::class.java.declaredMethods
                    .first { it.name.startsWith("disconnectForNetworkTransition") }
                    .apply { isAccessible = true }
                    .invoke(service, DefaultNetworkTransition.CHANGED)
                Thread.sleep(3_000)
                service.sendShellInput("mosh-roam", "echo \$((40+5))-roamed\r")
                waitForScreen(service, "mosh-roam", "45-roamed", 30_000)
            } finally {
                service.stopSession("mosh-roam")
            }
        }
    }

    /** A plain SSH shell (no Mosh) on the real OpenSSH server, which has a real PTY for `stty`. */
    @Test
    fun plainSshReconnectKeepsTheTerminalSize() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        val sessionId = "ssh-size"
        withSessionService { service ->
            service.startSession(
                requestedSessionId = sessionId,
                host = moshHostConnection().copy(useMosh = false),
                mode = ConnectionMode.SSH,
                passwordOverride = moshPassword,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false
            )
            try {
                waitForStatus(service, sessionId, SessionStatus.ACTIVE, timeoutMillis = 60_000)
                service.resizeShell(sessionId, 70, 20)
                service.sendShellInput(sessionId, "echo size-\$(stty size | tr ' ' x)-before\r")
                waitForScreen(service, sessionId, "size-20x70-before", 30_000)

                // A network change drops plain SSH sessions and reconnects them in place, with a new shell.
                SessionService::class.java.declaredMethods
                    .first { it.name.startsWith("disconnectForNetworkTransition") }
                    .apply { isAccessible = true }
                    .invoke(service, DefaultNetworkTransition.CHANGED)
                waitForStatus(service, sessionId, SessionStatus.CONNECTING, timeoutMillis = 30_000)
                waitForStatus(service, sessionId, SessionStatus.ACTIVE, timeoutMillis = 60_000)

                // The screen didn't change size, so it sends no resize: the new shell has to start at its size.
                // At the default 120x40, tmux drew its status line below the screen and scrolled it every second.
                service.sendShellInput(sessionId, "echo size-\$(stty size | tr ' ' x)-after\r")
                waitForScreen(service, sessionId, "size-20x70-after", 30_000)
            } finally {
                service.stopSession(sessionId)
            }
        }
    }

    @Test
    fun killedMoshClientRecoversOrEndsCleanly() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        withSessionService { service ->
            val before = moshServerCount()
            startMosh(service, "mosh-kill", moshHostConnection())
            try {
                service.sendShellInput("mosh-kill", "echo \$((40+6))-before-kill\r")
                waitForScreen(service, "mosh-kill", "46-before-kill", 30_000)
                android.os.Process.sendSignal(moshClientPid(service, "mosh-kill"), 9)
                Thread.sleep(3_000)
                // Either it comes back and works, or it ends; never a live-looking dead screen.
                val deadline = System.currentTimeMillis() + 60_000
                var worked = false
                while (System.currentTimeMillis() < deadline && !worked) {
                    val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == "mosh-kill" }
                    if (snapshot == null || snapshot.status == SessionStatus.ERROR) break
                    if (snapshot.status == SessionStatus.ACTIVE) {
                        service.sendShellInput("mosh-kill", "echo \$((40+7))-after-kill\r")
                        worked = runCatching { waitForScreen(service, "mosh-kill", "47-after-kill", 15_000) }.isSuccess
                    } else {
                        Thread.sleep(500)
                    }
                }
                val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == "mosh-kill" }
                check(worked || snapshot == null || snapshot.status == SessionStatus.ERROR) {
                    "Killed mosh-client left a session that looks open but doesn't work: $snapshot"
                }
            } finally {
                service.stopSession("mosh-kill")
            }
            waitForMoshServerCount(before, 20_000)
        }
    }

    @Test
    fun moshSessionTypesRendersResizesAndSurvivesAFrozenServer() {
        assumeTrue("Needs -e liveMoshHost", moshHost != null)
        val sessionId = "mosh-test"
        val host = moshHostConnection()
        withSessionService { service ->
            startMosh(service, sessionId, host)
            try {

                // Typing reaches the shell, and the output comes back (computed, so a local echo can't fake it).
                service.sendShellInput(sessionId, "echo \$((6*7))-typed\r")
                waitForScreen(service, sessionId, "42-typed", 30_000)

                // UTF-8 both ways.
                service.sendShellInput(sessionId, "printf 'h\\303\\251llo-%s\\n' \"\$(printf '\\346\\227\\245')\"\r")
                waitForScreen(service, sessionId, "héllo-日", 30_000)

                // A burst of output, then the shell is still responsive.
                service.sendShellInput(sessionId, "seq 1 20000; echo \$((1000+337))-after-burst\r")
                waitForScreen(service, sessionId, "1337-after-burst", 90_000)

                // The terminal size reaches the server.
                service.resizeShell(sessionId, 71, 23)
                Thread.sleep(1_000)
                service.sendShellInput(sessionId, "stty size | tr ' ' x\r")
                waitForScreen(service, sessionId, "23x71", 30_000)

                // Freeze the server past the SSH keepalive limit; input typed meanwhile must arrive afterwards.
                Log.i(TAG, PAUSE_MARKER)
                Thread.sleep(5_000)
                service.sendShellInput(sessionId, "echo \$((2000+24))-during-freeze\r")
                waitForScreen(service, sessionId, "2024-during-freeze", 240_000)
                val status = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
                check(status != null && status.status == SessionStatus.ACTIVE) { "Session after the freeze: $status" }

                // System info still works with the bootstrap SSH connection gone.
                val info = CountDownLatch(1)
                var infoError: String? = "no result"
                service.fetchHostSystemInfo(host) { result ->
                    infoError = result.error
                    info.countDown()
                }
                check(info.await(60, TimeUnit.SECONDS)) { "System info never returned" }
                check(infoError == null) { "System info failed: $infoError" }
            } finally {
                service.stopSession(sessionId)
                Log.i(TAG, DONE_MARKER)
            }
        }
    }

    private fun screenText(service: SessionService, sessionId: String): String {
        var text = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            text = service.resolveMoshTerminalEmulator(sessionId)?.screen?.transcriptText.orEmpty()
        }
        return text
    }

    private fun waitForScreen(service: SessionService, sessionId: String, needle: String, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
            check(snapshot != null) { "Session $sessionId disappeared" }
            check(snapshot.status != SessionStatus.ERROR) { "Session failed: ${snapshot.statusMessage}" }
            if (screenText(service, sessionId).contains(needle)) return
            Thread.sleep(250)
        }
        error("\"$needle\" never appeared. Screen:\n${screenText(service, sessionId).takeLast(1500)}")
    }

    private val seenSessions = mutableSetOf<String>()

    private fun waitForStatus(service: SessionService, sessionId: String, status: SessionStatus, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
            if (snapshot == null) {
                check(sessionId !in seenSessions) { "Session $sessionId disappeared" }
                Thread.sleep(200)
                continue
            }
            seenSessions += sessionId
            check(snapshot.status != SessionStatus.ERROR) { "Session failed: ${snapshot.statusMessage}" }
            if (snapshot.status == status) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $status within ${timeoutMillis}ms")
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
            check(connected.await(10, TimeUnit.SECONDS)) { "Timed out binding SessionService" }
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        const val TAG = "LiveMoshTest"
        const val PAUSE_MARKER = "MOSH_TEST_PAUSE"
        const val DONE_MARKER = "MOSH_TEST_DONE"
    }
}
