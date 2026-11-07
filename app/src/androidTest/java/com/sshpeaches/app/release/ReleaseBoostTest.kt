package com.majordaftapps.sshpeaches.app.release

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.testutil.TransferSettingsOverride
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the minified release build, Boost starts, lists and moves files, and runs a terminal. The Go client calls into
 * the app's classes by name through JNI (Progress, ShellListener), so R8 renaming or removing one would break Boost
 * in release builds only. Needs the -e boostHost/boostPort/boostUser/boostPassword/perfDir arguments
 * LiveFileBoostTest takes; skipped without them.
 */
@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class ReleaseBoostTest {

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun boostListsAndMovesFiles() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        TransferSettingsOverride.apply(context)
        val dir = args.getString("perfDir") ?: "/home/mosher/perf"
        val content = Random(7).nextBytes(1024 * 1024 + 4321)
        val local = File(context.cacheDir, "release-boost-up.bin").apply { writeBytes(content) }
        val back = File(context.cacheDir, "release-boost-down.bin").apply { delete() }
        val remote = "$dir/release-boost-${UUID.randomUUID()}.bin"
        val id = "release-boost-${UUID.randomUUID()}"
        val logs = CopyOnWriteArrayList<String>()
        val logScope = CoroutineScope(Dispatchers.Default)
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) logs += it.message } }
        runBlocking { SettingsStore.setBoost(true) }
        try {
            withSessionService { service ->
                service.startSession(
                    requestedSessionId = id,
                    host = HostConnection(
                        id = id, name = id, host = args.getString("boostHost")!!, port = args.getString("boostPort")!!.toInt(),
                        username = args.getString("boostUser")!!, preferredAuth = AuthMethod.PASSWORD
                    ),
                    mode = ConnectionMode.SCP,
                    passwordOverride = args.getString("boostPassword"),
                    autoTrustUnknownHostKey = true,
                    hostKeyPromptEnabled = false
                )
                try {
                    waitFor("file boost") { logs.any { it.startsWith("Boost") } }
                    val line = logs.first { it.startsWith("Boost") }
                    check(line.startsWith("Boost on")) { line }
                    service.listSftpDirectory(id, "$dir/huge")
                    waitFor("the boost listing") {
                        val snapshot = service.remoteDirectoryFlow().value[id]
                        snapshot != null && snapshot.complete && snapshot.path.endsWith("/huge")
                    }
                    assertEquals(20_000, service.remoteDirectoryFlow().value.getValue(id).entries.size)
                    service.scpUploadFile(id, local.absolutePath, remote)
                    waitForSuccess(service, id)
                    service.scpDownloadFile(id, remote, back.absolutePath)
                    waitForSuccess(service, id)
                    check(logs.none { it.startsWith("Boost") && "failed" in it }) { logs.joinToString("\n") }
                } finally {
                    service.stopSession(id)
                }
            }
        } finally {
            runBlocking { SettingsStore.setBoost(false) }
            logScope.cancel()
        }
        assertArrayEquals(content, back.readBytes())
        local.delete()
        back.delete()
    }

    @Test
    fun boostRunsTheTerminal() {
        assumeTrue("needs -e boostHost", args.getString("boostHost") != null)
        val id = "release-boost-term-${UUID.randomUUID()}"
        val logs = CopyOnWriteArrayList<String>()
        val logScope = CoroutineScope(Dispatchers.Default)
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == id) logs += it.message } }
        runBlocking { SettingsStore.setBoost(true) }
        try {
            withSessionService { service ->
                service.startSession(
                    requestedSessionId = id,
                    host = HostConnection(
                        id = id, name = id, host = args.getString("boostHost")!!, port = args.getString("boostPort")!!.toInt(),
                        username = args.getString("boostUser")!!, preferredAuth = AuthMethod.PASSWORD
                    ),
                    mode = ConnectionMode.SSH,
                    passwordOverride = args.getString("boostPassword"),
                    autoTrustUnknownHostKey = true,
                    hostKeyPromptEnabled = false
                )
                try {
                    waitFor("the terminal") { logs.any { it.startsWith("Terminal on Boost") || "Using SSH" in it } }
                    val line = logs.first { it.startsWith("Terminal on Boost") || "Using SSH" in it }
                    check(line.startsWith("Terminal on Boost")) { line }
                    // That line comes just before the session takes the shell, and input sent before then is
                    // dropped: wait until the session is active, as LiveBoostTerminalTest does.
                    waitFor("the session") {
                        service.sessionsFlow().value.firstOrNull { it.hostId == id }?.status ==
                            com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus.ACTIVE
                    }
                    service.sendShellInput(id, "echo release-\$((40+2))\n")
                    waitFor("the shell's answer") { service.shellOutputFlow().value[id].orEmpty().contains("release-42") }
                } finally {
                    service.stopSession(id)
                }
            }
        } finally {
            runBlocking { SettingsStore.setBoost(false) }
            logScope.cancel()
        }
    }

    private fun waitForSuccess(service: SessionService, id: String) {
        Thread.sleep(200)
        waitFor("the transfer") {
            val progress = service.fileTransferProgressFlow().value[id]
            check(progress?.status != FileTransferStatus.FAILED) { "transfer failed: ${progress?.errorMessage}" }
            progress?.status == FileTransferStatus.SUCCEEDED
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 120_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(50)
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
