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
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import com.majordaftapps.sshpeaches.app.service.BoostHelper
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A new user's first minutes, on a phone's uplink: the settings are the defaults (Boost on, for files and the
 * terminal), the server has never seen the app, so the Boost helper has to be copied to it, and the link is slow.
 * The terminal and the file browser must open at once and answer all the while; the helper arrives in the
 * background and the next terminal uses it.
 *
 * 0.14.1 changed the helper, so every server needed the copy again: 7 MB sent over the session's own connection
 * with a megabyte in flight. Behind that, on a slow uplink, nothing else got through in time: the terminal's
 * "is the server there?" check gave up and dropped the connection, the copy started again from nothing on the
 * reconnect, and so on.
 *
 * Arguments: -e boostHost/boostUser/boostPassword as LiveFileBoostTest, -e boostPort (the server's real port, for
 * checks made on the side) and -e slowPort (the same server through enzu-runners/slowlink.py). The runner removes
 * ~/.cache/sshpeaches on the server before each test. Results are logged with tag NetScenario.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveSlowLinkFirstConnectTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = CopyOnWriteArrayList<String>()
    private val logScope = CoroutineScope(Dispatchers.Default)
    private val results = mutableListOf<String>()

    private fun note(text: String) {
        results += text
        Log.i("NetScenario", text)
    }

    @Before
    fun defaults() {
        assumeTrue("needs -e slowPort", args.getString("slowPort") != null)
        runBlocking {
            SettingsStore.setBoost(true)
            SettingsStore.setBoostTerminal(true)
        }
        Thread.sleep(500)
    }

    @After
    fun restore() {
        runBlocking {
            SettingsStore.setBoost(false)
            SettingsStore.setBoostTerminal(true)
        }
        logScope.cancel()
    }

    private fun host(id: String, port: String = "slowPort") = HostConnection(
        id = id, name = id, host = args.getString("boostHost")!!, port = args.getString(port)!!.toInt(),
        username = args.getString("boostUser")!!, preferredAuth = AuthMethod.PASSWORD
    )

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun secondsSince(start: Long) = (System.nanoTime() - start) / 1e9

    private fun status(service: SessionService, id: String) =
        service.sessionsFlow().value.firstOrNull { it.hostId == id }

    /** Starts the session and returns the seconds until it was ready to use. */
    private fun open(service: SessionService, id: String, mode: ConnectionMode): Double {
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) logs += it.message } }
        val start = System.nanoTime()
        service.startSession(
            requestedSessionId = id, host = host(id), mode = mode,
            passwordOverride = args.getString("boostPassword"), autoTrustUnknownHostKey = true,
            hostKeyPromptEnabled = false
        )
        check(waitFor(90_000) {
            val s = status(service, id)
            check(s?.status != SessionStatus.ERROR) { "session failed: ${s?.statusMessage}" }
            s?.status == SessionStatus.ACTIVE &&
                (mode == ConnectionMode.SSH || service.remoteDirectoryFlow().value[id]?.complete == true)
        }) { "the session never opened; log: $logs" }
        return secondsSince(start)
    }

    /** What [command] prints on the server, asked over the server's real port. */
    private fun onServer(command: String): String = SshClientProvider.createClient(context, host("side", "boostPort")).use { ssh ->
        ssh.connect(args.getString("boostHost")!!, args.getString("boostPort")!!.toInt())
        ssh.authPassword(args.getString("boostUser")!!, args.getString("boostPassword"))
        ssh.startSession().use { session ->
            val running = session.exec(command)
            val text = running.inputStream.bufferedReader().readText().trim()
            running.join(10, TimeUnit.SECONDS)
            text
        }
    }

    /** Whether this version's helper is on the server. */
    private fun helperOnServer(): Boolean =
        onServer("ls .cache/sshpeaches/boost/*/sshpeaches-boost 2>/dev/null | wc -l") == "1"

    /** Bytes of a helper copy that hasn't finished, 0 without one. */
    private fun partOnServer(): Long =
        onServer("cat .cache/sshpeaches/boost/*/*.part 2>/dev/null | wc -c").toLongOrNull() ?: 0L

    /** What must never show up in the session's log, or as its state, while the helper is copied. */
    private fun assertNeverDropped(service: SessionService, id: String) {
        val trouble = logs.filter { line ->
            TROUBLE.any { line.contains(it, ignoreCase = true) }
        }
        check(trouble.isEmpty()) { "the connection was dropped: $trouble" }
        check(status(service, id)?.status == SessionStatus.ACTIVE) { "session is ${status(service, id)?.status}" }
    }

    @Test
    fun terminal_opensAtOnceAndAnswersWhileTheHelperIsCopied() {
        check(!helperOnServer()) { "the runner should have removed the helper" }
        val id = "slow-term"
        withSessionService { service ->
            try {
                val opened = open(service, id, ConnectionMode.SSH)
                note("terminal ready after %.1f s".format(opened))
                // Type like a user would, until the helper has arrived (and for a minute at least).
                var worst = 0.0
                var typed = 0
                val begin = System.nanoTime()
                while (secondsSince(begin) < MAX_COPY_SECONDS) {
                    val marker = "m${typed++}"
                    val sent = System.nanoTime()
                    service.sendShellInput(id, "echo $marker-\$((40+2))\n")
                    val answered = waitFor(ANSWER_LIMIT_MS) {
                        service.shellOutputFlow().value[id].orEmpty().contains("$marker-42")
                    }
                    assertNeverDropped(service, id)
                    check(answered) {
                        "typed after %.0f s and nothing came back in %d s; log: %s"
                            .format(secondsSince(begin), ANSWER_LIMIT_MS / 1000, logs)
                    }
                    worst = maxOf(worst, secondsSince(sent))
                    if (secondsSince(begin) > 60 && helperOnServer()) break
                    Thread.sleep(2_000)
                }
                note("typed $typed commands over %.0f s; slowest answer %.1f s".format(secondsSince(begin), worst))
                check(opened < OPEN_LIMIT_SECONDS) { "the terminal took %.1f s to open".format(opened) }
                check(waitFor(60_000) { helperOnServer() }) { "the helper never arrived; log: $logs" }
                note("helper on the server after %.0f s".format(secondsSince(begin)))
                assertNeverDropped(service, id)
            } finally {
                service.stopSession(id)
                Log.i("NetScenario", "== slow-link terminal\n" + results.joinToString("\n"))
            }
            // The next terminal finds the helper and runs on it.
            val next = "slow-term-2"
            try {
                open(service, next, ConnectionMode.SSH)
                check(logs.any { it.startsWith("Terminal on Boost") }) { "the next terminal didn't use Boost: $logs" }
                note("next terminal: " + logs.last { it.startsWith("Terminal on Boost") })
            } finally {
                service.stopSession(next)
                Log.i("NetScenario", "== slow-link terminal\n" + results.joinToString("\n"))
            }
        }
    }

    @Test
    fun fileBrowser_opensAtOnceAndListsWhileTheHelperIsCopied() {
        check(!helperOnServer()) { "the runner should have removed the helper" }
        val id = "slow-files"
        withSessionService { service ->
            try {
                val opened = open(service, id, ConnectionMode.SCP)
                note("file browser ready after %.1f s".format(opened))
                var worst = 0.0
                var listed = 0
                val begin = System.nanoTime()
                while (secondsSince(begin) < MAX_COPY_SECONDS) {
                    val path = if (listed++ % 2 == 0) "/etc" else "/usr"
                    val before = service.remoteDirectoryFlow().value[id]
                    val sent = System.nanoTime()
                    service.listSftpDirectory(id, path)
                    val answered = waitFor(ANSWER_LIMIT_MS) {
                        val now = service.remoteDirectoryFlow().value[id]
                        now != null && now !== before && now.complete && !now.fromCache && now.path.endsWith(path)
                    }
                    assertNeverDropped(service, id)
                    check(answered) {
                        "asked for $path after %.0f s and no listing came in %d s; log: %s"
                            .format(secondsSince(begin), ANSWER_LIMIT_MS / 1000, logs)
                    }
                    worst = maxOf(worst, secondsSince(sent))
                    if (secondsSince(begin) > 60 && logs.any { it.startsWith("Boost on") }) break
                    Thread.sleep(2_000)
                }
                note("listed $listed folders over %.0f s; slowest %.1f s".format(secondsSince(begin), worst))
                check(opened < OPEN_LIMIT_SECONDS) { "the file browser took %.1f s to open".format(opened) }
                check(waitFor(60_000) { logs.any { it.startsWith("Boost on") } }) { "Boost never came on; log: $logs" }
                note("boost: " + logs.last { it.startsWith("Boost on") } + " after %.0f s".format(secondsSince(begin)))
                assertNeverDropped(service, id)
            } finally {
                service.stopSession(id)
                Log.i("NetScenario", "== slow-link files\n" + results.joinToString("\n"))
            }
        }
    }

    @Test
    fun aCutOffHelperCopyCarriesOnWhereItStopped() {
        check(!helperOnServer()) { "the runner should have removed the helper" }
        withSessionService { service ->
            val first = "slow-cut"
            try {
                open(service, first, ConnectionMode.SCP)
                check(waitFor(120_000) { partOnServer() > 400_000 }) { "the copy never got going; log: $logs" }
            } finally {
                service.stopSession(first)
            }
            check(BoostHelper.lastCopyForTests?.startsWith("gzip from 0 of ") == true) {
                "the first copy went as: ${BoostHelper.lastCopyForTests}"
            }
            Thread.sleep(3_000)
            val left = partOnServer()
            check(left > 400_000 && !helperOnServer()) { "what the cut-off copy left: $left bytes" }
            note("the copy was cut off with $left bytes on the server")

            val second = "slow-cut-2"
            // Closing the first session on purpose left its goodbye in the log.
            logs.clear()
            try {
                open(service, second, ConnectionMode.SCP)
                val begin = System.nanoTime()
                check(waitFor(MAX_COPY_SECONDS.toLong() * 1000) { logs.any { it.startsWith("Boost on") } }) {
                    "Boost never came on; log: $logs"
                }
                val copy = BoostHelper.lastCopyForTests.orEmpty()
                note("second copy: $copy; Boost on after %.0f s".format(secondsSince(begin)))
                val startedAt = Regex("gzip from (\\d+) of \\d+").find(copy)?.groupValues?.get(1)?.toLong()
                check(startedAt != null && startedAt >= left) { "the second copy didn't carry on from $left: $copy" }
                check(helperOnServer() && partOnServer() == 0L) { "the helper isn't in place" }
                assertNeverDropped(service, second)
            } finally {
                service.stopSession(second)
                Log.i("NetScenario", "== slow-link resume\n" + results.joinToString("\n"))
            }
        }
    }

    @Test
    fun aServerWithoutGzipGetsTheHelperAsItIs() {
        check(!helperOnServer()) { "the runner should have removed the helper" }
        BoostHelper.plainCopyForTests = true
        val id = "slow-plain"
        withSessionService { service ->
            try {
                open(service, id, ConnectionMode.SCP)
                val begin = System.nanoTime()
                check(waitFor(MAX_COPY_SECONDS.toLong() * 1000) { logs.any { it.startsWith("Boost on") } }) {
                    "Boost never came on; log: $logs"
                }
                note("copy: ${BoostHelper.lastCopyForTests}; Boost on after %.0f s".format(secondsSince(begin)))
                check(BoostHelper.lastCopyForTests?.startsWith("plain from 0 of ") == true) { "sent as: ${BoostHelper.lastCopyForTests}" }
                check(helperOnServer()) { "the helper isn't in place" }
                assertNeverDropped(service, id)
            } finally {
                service.stopSession(id)
                BoostHelper.plainCopyForTests = false
                Log.i("NetScenario", "== slow-link plain\n" + results.joinToString("\n"))
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

    private companion object {
        /** A terminal or a file browser that isn't there within this long feels broken. */
        const val OPEN_LIMIT_SECONDS = 10.0
        /** So does a key press or a folder that takes longer than this. */
        const val ANSWER_LIMIT_MS = 6_000L
        /** The copy must be over by now on the test's link (64 KiB/s up, a helper of 7 MB: two minutes). */
        const val MAX_COPY_SECONDS = 300.0
        val TROUBLE = listOf("stopped responding", "connection lost", "reconnect", "disconnected", "socket closed")
    }
}
