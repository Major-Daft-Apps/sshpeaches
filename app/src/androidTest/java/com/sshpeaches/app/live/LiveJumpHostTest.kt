package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.SSHPeachesApplication
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sessions through a jump host, against a real OpenSSH server (AllowTcpForwarding on), given with the same
 * -e liveMoshHost/liveMoshPort/liveMoshUsername/liveMoshPassword as [LiveMoshTest]. The server is its own jump host:
 * the target is "127.0.0.1" at the server's sshd port, an address that from this device is the device itself, so a
 * session that opens can only have gone through the jump host.
 */
@OptIn(DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveJumpHostTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val sshHost: String? get() = arguments.getString("liveMoshHost")
    private val sshPort: Int get() = arguments.getString("liveMoshPort")?.toIntOrNull() ?: 22
    private val sshUser: String get() = arguments.getString("liveMoshUsername") ?: "mosher"
    private val sshPassword: String? get() = arguments.getString("liveMoshPassword")

    private val repository get() = (context.applicationContext as SSHPeachesApplication).container.repository

    private val jump
        get() = HostConnection(
            id = "jump-1", name = "Gate", host = sshHost!!, port = sshPort, username = sshUser,
            preferredAuth = AuthMethod.PASSWORD
        )

    private fun target(id: String, jumpHostId: String? = "jump-1", useMosh: Boolean = false) = HostConnection(
        id = id, name = "behind the gate", host = "127.0.0.1", port = sshPort, username = sshUser,
        preferredAuth = AuthMethod.PASSWORD, jumpHostId = jumpHostId, useMosh = useMosh
    )

    @Before
    fun saveTheJumpHost() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        SecurityManager.init(context)
        SecurityManager.unlock()
        runBlocking { repository.addHost(jump.copy(hasPassword = true)) }
        SecurityManager.storeHostPassword(jump.id, sshPassword!!)
    }

    @Test
    fun aTerminalRunsThroughTheJumpHost() {
        withSession(target("jump-term"), ConnectionMode.SSH) { service, id, log ->
            typeAndExpect(service, id, "echo J-\$((40+2))", "J-42")
            check(log.any { it == "Connected through Gate." }) { "$log" }
        }
    }

    @Test
    fun aMoshHostBehindAJumpHostUsesSsh() {
        withSession(target("jump-mosh", useMosh = true), ConnectionMode.SSH) { service, id, log ->
            typeAndExpect(service, id, "echo M-\$((40+2))", "M-42")
            check(log.any { it.startsWith("Mosh can't go through a jump host") }) { "$log" }
        }
    }

    @Test
    fun filesMoveThroughTheJumpHost() {
        val content = Random(7).nextBytes(1024 * 1024 + 321)
        val local = File(context.cacheDir, "jump-up.bin").apply { writeBytes(content) }
        val back = File(context.cacheDir, "jump-down.bin").apply { delete() }
        val remote = "/tmp/jump-${UUID.randomUUID()}.bin"
        withSession(target("jump-files"), ConnectionMode.SCP) { service, id, log ->
            waitFor("the first listing") { service.remoteDirectoryFlow().value[id]?.complete == true }
            service.scpUploadFile(id, local.absolutePath, remote)
            waitForTransfer(service, id)
            service.scpDownloadFile(id, remote, back.absolutePath)
            waitForTransfer(service, id)
            assertArrayEquals(content, back.readBytes())
            // Extra transfer connections ride on the jump connection; none of them had to give up.
            check(log.none { it.contains("a second login failed") }) { "$log" }
            check(log.none { it.startsWith("Boost on") }) { "Boost must stay off through a jump host: $log" }
        }
        local.delete()
        back.delete()
    }

    @Test
    fun aLocalForwardWorksBehindTheJumpHost() {
        val forward = PortForward(
            id = "fwd-jump", label = "behind", type = PortForwardType.LOCAL, sourceHost = "127.0.0.1", sourcePort = 19083,
            destinationHost = "127.0.0.1", destinationPort = sshPort, associatedHosts = listOf("jump-fwd"), enabled = true
        )
        withSession(target("jump-fwd"), ConnectionMode.SSH, listOf(forward)) { _, _, _ ->
            val banner = Socket().use { socket ->
                socket.soTimeout = 20_000
                socket.connect(InetSocketAddress("127.0.0.1", 19083), 10_000)
                socket.getInputStream().bufferedReader().readLine().orEmpty()
            }
            check(banner.startsWith("SSH-2.0-")) { "The forward did not reach the server: $banner" }
        }
    }

    @Test
    fun theJumpHostsPasswordIsAskedForByName() {
        SecurityManager.clearHostPassword(jump.id)
        val id = "jump-ask"
        withSessionService { service ->
            service.startSession(
                requestedSessionId = id, host = target(id), mode = ConnectionMode.SSH, passwordOverride = sshPassword,
                autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
            )
            try {
                waitFor("the jump host's password prompt") { service.passwordPromptsFlow().value.isNotEmpty() }
                val prompt = service.passwordPromptsFlow().value.first()
                check(prompt.host == sshHost && prompt.username == sshUser) { "The prompt is for ${prompt.username}@${prompt.host}" }
                service.respondToPasswordPrompt(prompt.id, sshPassword, savePassword = false)
                waitForActive(service, id)
                typeAndExpect(service, id, "echo A-\$((40+2))", "A-42")
            } finally {
                service.stopSession(id)
            }
        }
    }

    @Test
    fun aJumpHostThatIsGoneIsSaidSo() {
        val id = "jump-gone"
        withSessionService { service ->
            service.startSession(
                requestedSessionId = id, host = target(id, jumpHostId = "deleted-host"), mode = ConnectionMode.SSH,
                passwordOverride = sshPassword, autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
            )
            try {
                waitFor("the session to fail") {
                    service.sessionsFlow().value.firstOrNull { it.hostId == id }?.status == SessionStatus.ERROR
                }
                val message = service.sessionsFlow().value.first { it.hostId == id }.statusMessage.orEmpty()
                check(message.contains("no longer saved")) { "The session failed with: $message" }
            } finally {
                service.stopSession(id)
            }
        }
    }

    private fun typeAndExpect(service: SessionService, id: String, command: String, expected: String) {
        service.sendShellInput(id, "$command\n")
        waitFor("$expected in the terminal") { service.shellOutputFlow().value[id].orEmpty().contains(expected) }
    }

    private fun waitForTransfer(service: SessionService, id: String) {
        Thread.sleep(200)
        waitFor("the transfer") {
            val progress = service.fileTransferProgressFlow().value[id]
            check(progress?.status != FileTransferStatus.FAILED) { "transfer failed: ${progress?.errorMessage}" }
            progress?.status == FileTransferStatus.SUCCEEDED
        }
    }

    private fun withSession(
        host: HostConnection,
        mode: ConnectionMode,
        forwards: List<PortForward> = emptyList(),
        block: (SessionService, String, List<String>) -> Unit
    ) {
        val id = host.id
        val log = CopyOnWriteArrayList<String>()
        val collector = GlobalScope.launch(Dispatchers.Default) {
            SessionLogBus.entries.collect { if (it.hostId == id) log += it.message }
        }
        try {
            withSessionService { service ->
                service.startSession(
                    requestedSessionId = id, host = host, mode = mode, passwordOverride = sshPassword,
                    availableForwards = forwards, autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
                )
                try {
                    waitForActive(service, id)
                    block(service, id, log)
                } finally {
                    service.stopSession(id)
                }
            }
        } finally {
            collector.cancel()
        }
    }

    private fun waitForActive(service: SessionService, sessionId: String) {
        waitFor("the session to open") {
            val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
            check(snapshot?.status != SessionStatus.ERROR) { "Session failed: ${snapshot?.statusMessage}" }
            snapshot?.status == SessionStatus.ACTIVE
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 90_000
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
