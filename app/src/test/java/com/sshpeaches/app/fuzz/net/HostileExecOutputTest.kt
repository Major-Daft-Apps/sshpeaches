package com.majordaftapps.sshpeaches.app.fuzz.net

import android.app.Application
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import com.majordaftapps.sshpeaches.app.diagnostics.ProblemReport
import com.majordaftapps.sshpeaches.app.service.SessionService
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exec-channel output from a hostile or odd server, read by SessionService's private helpers:
 * the mosh-server bootstrap and the remote-command runner used for OS / system info.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HostileExecOutputTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var server: HostileSftpServer? = null
    private var client: SSHClient? = null

    @After
    fun tearDown() {
        runCatching { client?.close() }
        server?.close()
    }

    private fun connect(handler: (String, OutputStream, OutputStream) -> Unit): Pair<SessionService, SSHClient> {
        SettingsStore.init(RuntimeEnvironment.getApplication())
        val hostile = HostileSftpServer(temp.newFolder("sandbox"), temp.newFile("hostkey"), temp.newFile("known_hosts"), execHandler = handler)
        server = hostile
        val ssh = hostile.connect()
        client = ssh
        return Robolectric.buildService(SessionService::class.java).get() to ssh
    }

    /** Calls `private suspend fun startMoshServer(hostId, client)` and returns what it threw. */
    private fun startMoshServer(service: SessionService, ssh: SSHClient): Throwable? {
        val method = SessionService::class.java.declaredMethods.first { it.name == "startMoshServer" }
        method.isAccessible = true
        return runCatching {
            runBlocking {
                suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                    try {
                        method.invoke(service, "fuzz", ssh, continuation)
                    } catch (error: InvocationTargetException) {
                        throw error.targetException
                    }
                }.takeUnless { it === COROUTINE_SUSPENDED }
            }
        }.exceptionOrNull()
    }

    /**
     * readLineIfReady() checks ready() and then calls readLine(), which blocks until a newline. A
     * prompt without a newline (e.g. "[sudo] password for alice: " from a sudo'd mosh-server
     * command, or anything a login script prints) leaves it blocked forever, past the 15 s
     * bootstrap deadline and the 60 s connection deadline.
     */
    @Test
    fun aPromptWithoutNewlineCannotHangTheMoshBootstrapPastItsDeadline() {
        val (service, ssh) = connect { _, _, stderr ->
            stderr.write("[sudo] password for fuzzer: ".toByteArray())
            stderr.flush()
            Thread.sleep(120_000)
        }
        val error = Fuzz.withinMillis(25_000, "startMoshServer-partial-line") { startMoshServer(service, ssh) }
        assertTrue("expected a mosh-server failure, got $error", error?.message.orEmpty().contains("mosh-server"))
    }

    /**
     * Everything mosh-server printed goes into the failure message unbounded; that message is the
     * "Message" detail scrubbed on the main thread when the user taps Send report.
     */
    @Test
    fun moshServerOutputInTheFailureMessageIsBoundedAndCheapToReport() {
        val line = "Error:" + "x".repeat(8 * 1024)
        val (service, ssh) = connect { _, stdout, _ ->
            stdout.write((line + "\n").toByteArray())
            stdout.flush()
        }
        val error = Fuzz.withinMillis(25_000, "startMoshServer-long-line") { startMoshServer(service, ssh) }
        val message = error?.message.orEmpty()
        println("mosh failure message length=${message.length}")
        val elapsed = Fuzz.elapsedMillis {
            Fuzz.withinMillis(60_000, "report") {
                ProblemReport.create("Connection failed", mapOf("Message" to message), message.lines(), secrets = listOf("127.0.0.1", "fuzzer"))
            }
        }
        assertTrue("report for a ${message.length}-char mosh failure took ${elapsed}ms on the caller's (main) thread", elapsed < 500)
    }

    /** runRemoteCommand reads the whole exec output with readText(); size it before parsing. */
    @Test
    fun remoteCommandOutputReachesTheParserInFull() {
        val chunk = "BEGIN_FREE\n".repeat(1024).toByteArray()
        val (service, ssh) = connect { _, stdout, _ ->
            repeat(96) { stdout.write(chunk) }
            stdout.flush()
        }
        val method = SessionService::class.java.getDeclaredMethod(
            "runRemoteCommand", SSHClient::class.java, String::class.java, Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        val raw = Fuzz.withinMillis(20_000, "runRemoteCommand") { method.invoke(service, ssh, "info", 8L) as String? }
        assertEquals(96 * chunk.size, raw?.length ?: -1)
    }
}
