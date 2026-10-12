package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.HostSystemInfoCollector
import com.majordaftapps.sshpeaches.app.data.ssh.InteractiveLoginResponder
import com.majordaftapps.sshpeaches.app.data.ssh.LoginChallenge
import com.majordaftapps.sshpeaches.app.diagnostics.ProblemReport
import com.majordaftapps.sshpeaches.app.service.BoundedMoshInputQueue
import com.majordaftapps.sshpeaches.app.service.ConnectionFailure
import com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.connectionFailureKind
import com.majordaftapps.sshpeaches.app.service.isFatalSftpTransportFailure
import com.majordaftapps.sshpeaches.app.service.removeSessionSnapshot
import com.majordaftapps.sshpeaches.app.service.upsertSessionSnapshot
import com.majordaftapps.sshpeaches.app.ui.screens.fileExtensionKey
import com.majordaftapps.sshpeaches.app.ui.screens.remoteFileKind
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.ArrayDeque
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remote command output, login prompts, error chains and the small queues fed by the network. */
class ServerOutputParsersFuzzTest {

    private val keys = listOf("UNAME_S", "UNAME_R", "UNAME_M", "HOSTNAME", "PRETTY_NAME", "VERSION_ID", "ID", "CPU", "CPU_CORES",
        "MEM_TOTAL_KB", "MEM_AVAIL_KB", "MEM_TOTAL_B", "DISK", "UPTIME", "lower", "", "=")
    private val tokens = listOf("BEGIN_FREE", "END_FREE", "BEGIN_DF", "END_DF")

    private fun systemInfoOutput(random: Random): String = buildString {
        repeat(random.nextInt(0, 40)) {
            when (random.nextInt(5)) {
                0 -> append(tokens.random(random))
                1 -> append(keys.random(random)).append('=').append(
                    when (random.nextInt(4)) {
                        0 -> random.nextLong().toString()
                        1 -> "${random.nextLong()} ${random.nextLong()} ${random.nextLong()} ${random.nextInt()}%"
                        2 -> Long.MAX_VALUE.toString()
                        else -> Fuzz.string(random, 30)
                    }
                )
                2 -> append("Filesystem Size Used Avail Use% Mounted on\n/dev/sda1 10G 5G 5G 50% /")
                3 -> append(Fuzz.string(random, 60))
                else -> Unit
            }
            append(listOf("\n", "\r\n", "", " ").random(random))
        }
    }

    @Test
    fun systemInfoParserAcceptsAnyOutput() {
        val random = Random(Fuzz.SEED + 40)
        repeat(4_000) { iteration ->
            val raw = systemInfoOutput(random)
            val info = Fuzz.withinMillis(2_000, "parse#$iteration") { HostSystemInfoCollector.parse(raw, nowEpochMillis = 1L) }
            assertEquals(1L, info.collectedAtEpochMillis)
        }
    }

    /**
     * A server that prints BEGIN_FREE many times without END_FREE makes each lazy
     * `BEGIN_FREE[\s\S]*?END_FREE` attempt scan to the end of the output: quadratic. The output is
     * only bounded by the SSH channel window (about 2 MiB in sshj).
     */
    @Test
    fun systemInfoParserIsLinearInTheOutputSize() {
        val raw = "BEGIN_FREE\n".repeat(24 * 1024 / 11)
        val elapsed = Fuzz.elapsedMillis {
            Fuzz.withinMillis(60_000, "parse-repeated-begin") { HostSystemInfoCollector.parse(raw) }
        }
        assertTrue("parse of ${raw.length} chars of repeated BEGIN_FREE took ${elapsed}ms", elapsed < 500)
    }

    @Test
    fun formattedSizesAreNeverNegativeOrGarbage() {
        val info = HostSystemInfoCollector.parse("DISK=9223372036854775807 9223372036854775807 0 100%\nMEM_TOTAL_KB=9223372036854775807")
        // usedKb * 1024 overflows; a nonsensical value must not be shown as a real size.
        assertFalse("disk shows ${info.disk}", info.disk.orEmpty().startsWith("0 B"))
    }

    @Test
    fun keyboardInteractivePasswordGoesOnlyToOneHiddenPasswordPrompt() {
        val random = Random(Fuzz.SEED + 41)
        val prompts = listOf("Password: ", "password for alice:", "PASSWORD", "Verification code: ", "OTP:", "Passcode", "", "\u0000",
            "New password:", "Enter PIN", "pass word")
        repeat(3_000) {
            val password = if (random.nextInt(5) == 0) null else Fuzz.string(random, 12)
            val asked = mutableListOf<LoginChallenge>()
            val askerPresent = random.nextBoolean()
            val responder = InteractiveLoginResponder(password, if (askerPresent) { challenge ->
                asked += challenge
                if (random.nextInt(8) == 0) null else "answer"
            } else null)
            responder.init(null, if (random.nextBoolean()) null else Fuzz.string(random, 10), if (random.nextBoolean()) null else Fuzz.string(random, 40))
            var passwordReplies = 0
            repeat(random.nextInt(1, 6)) {
                val prompt = if (random.nextBoolean()) prompts.random(random) else Fuzz.string(random, 20)
                val echo = random.nextBoolean()
                val wasCanceled = responder.canceled
                val reply = String(responder.getResponse(prompt, echo))
                if (!password.isNullOrEmpty() && reply == password && reply != "answer") {
                    passwordReplies++
                    assertFalse("password sent to an echoed prompt", echo)
                    assertTrue("password sent to ${prompt.trim()}", prompt.contains("password", ignoreCase = true))
                }
                if (wasCanceled) assertEquals("", reply)
            }
            assertTrue("password sent $passwordReplies times", passwordReplies <= 1)
            assertFalse(responder.shouldRetry())
        }
    }

    @Test
    fun failureClassificationSurvivesCyclesDeepChainsAndOddMessages() {
        val random = Random(Fuzz.SEED + 42)
        fun leaf(): Throwable = when (random.nextInt(9)) {
            0 -> ConnectException(Fuzz.string(random, 20))
            1 -> SocketTimeoutException(null)
            2 -> UnknownHostException(Fuzz.string(random, 20))
            3 -> UserAuthException(Fuzz.string(random, 20))
            4 -> TransportException(DisconnectReason.values().random(random), Fuzz.string(random, 20))
            5 -> ConnectionFailure(Fuzz.string(random, 20), ConnectionFailureKind.values().random(random))
            6 -> IOException(null as String?)
            7 -> IllegalStateException(Fuzz.string(random, 20))
            else -> RuntimeException()
        }
        repeat(3_000) {
            val chain = List(random.nextInt(1, 40)) { leaf() }
            chain.zipWithNext().forEach { (outer, inner) -> runCatching { outer.initCause(inner) } }
            if (random.nextBoolean() && chain.size > 1) runCatching { chain.last().initCause(chain.first()) }
            Fuzz.withinMillis(1_000, "classify") {
                chain.first().connectionFailureKind()
                chain.first().isFatalSftpTransportFailure()
            }
        }
    }

    @Test
    fun moshInputQueueIsLosslessBoundedAndOrdered() {
        val random = Random(Fuzz.SEED + 43)
        repeat(300) {
            val chunk = random.nextInt(1, 64)
            val capacity = chunk + random.nextInt(0, 512)
            val queue = BoundedMoshInputQueue(capacity, chunk)
            val model = ArrayDeque<Byte>()
            repeat(random.nextInt(1, 200)) {
                if (random.nextBoolean()) {
                    val payload = ByteArray(random.nextInt(0, capacity + 8)).also(random::nextBytes)
                    val reserved = listOf(Int.MIN_VALUE, -1, 0, random.nextInt(0, capacity + 1), Int.MAX_VALUE).random(random)
                    val accepted = queue.tryEnqueue(payload, reserved)
                    val usable = capacity - reserved.coerceIn(0, capacity)
                    assertEquals(payload.isEmpty() || payload.size <= usable - model.size, accepted)
                    if (accepted) payload.forEach(model::addLast)
                } else if (model.isNotEmpty()) {
                    val taken = queue.take()!!
                    assertTrue(taken.size in 1..chunk)
                    assertArrayEquals(ByteArray(taken.size) { model.removeFirst() }, taken)
                }
                assertEquals(model.size, queue.queuedByteCount())
                assertTrue(queue.queuedByteCount() <= capacity)
            }
            queue.close()
            assertNull(queue.take())
            assertFalse(queue.tryEnqueue(byteArrayOf(1)))
        }
    }

    @Test
    fun sessionSnapshotsBehaveLikeAMapKeyedByHostId() {
        val random = Random(Fuzz.SEED + 44)
        val state = MutableStateFlow<List<SessionService.SessionSnapshot>>(emptyList())
        val model = LinkedHashMap<String, SessionService.SessionSnapshot>()
        repeat(5_000) {
            val id = listOf("a", "b", "c", "", " ", "\u0000", Fuzz.string(random, 4)).random(random)
            if (random.nextInt(3) == 0) {
                state.removeSessionSnapshot(id)
                model.remove(id)
            } else {
                val snapshot = SessionService.SessionSnapshot(
                    hostId = id,
                    host = HostConnection(id = id, name = id, host = "h", port = 22, username = "u", preferredAuth = AuthMethod.PASSWORD),
                    mode = ConnectionMode.values().random(random),
                    status = SessionService.SessionStatus.values().random(random),
                    statusMessage = Fuzz.string(random, 10)
                )
                state.upsertSessionSnapshot(snapshot)
                model[id] = snapshot
            }
            assertEquals(model.values.toList(), state.value)
        }
    }

    @Test
    fun problemReportFieldsStayWithinTheirLimits() {
        val random = Random(Fuzz.SEED + 45)
        repeat(40) {
            val log = List(random.nextInt(0, 700)) { Fuzz.string(random, 150) }
            val report = Fuzz.withinMillis(5_000, "ProblemReport.create") {
                ProblemReport.create(
                    summary = Fuzz.string(random, 100),
                    details = mapOf("Message" to Fuzz.string(random, 150)),
                    log = log,
                    note = Fuzz.string(random, 150) + Fuzz.string(random, 150).repeat(10),
                    secrets = listOf(Fuzz.string(random, 8), null)
                )
            }
            assertTrue(report.logLines.size <= ProblemReport.MAX_LOG_LINES)
            assertTrue(report.logLines.all { it.length <= 400 })
            assertTrue(report.note.length <= 2_000)
        }
    }

    /**
     * The report is built on the main thread when "Send report" is tapped. Each line is scrubbed
     * before it is cut to 400 characters, so one long line (for example a long server disconnect
     * message in the connection log) costs the full quadratic scrub.
     */
    @Test
    fun problemReportWithOneLongLogLineIsBuiltQuickly() {
        val line = "Disconnected: " + "x".repeat(8 * 1024)
        val elapsed = Fuzz.elapsedMillis {
            Fuzz.withinMillis(60_000, "ProblemReport.create-long") {
                ProblemReport.create("Connection failed", mapOf("Message" to line), listOf(line))
            }
        }
        assertTrue("ProblemReport.create with one ${line.length}-char line took ${elapsed}ms", elapsed < 500)
    }

    @Test
    fun fileKindClassificationAcceptsAnyName() {
        val random = Random(Fuzz.SEED + 46)
        repeat(20_000) {
            val name = Fuzz.string(random, 40)
            fileExtensionKey(name)
            remoteFileKind(name, random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), listOf(true, false, null).random(random))
        }
    }
}
