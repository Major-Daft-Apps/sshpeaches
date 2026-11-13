package com.majordaftapps.sshpeaches.app.live

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
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Remote (ssh -R) and dynamic (ssh -D, SOCKS) forwards against a real OpenSSH server with AllowTcpForwarding on,
 * given with the same -e liveMoshHost/liveMoshPort/liveMoshUsername/liveMoshPassword as [LiveMoshTest].
 * "127.0.0.1" as a destination opened from the server is the server itself, so its own sshd port stands in for
 * "a service on the server's side".
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LivePortForwardTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    private val arguments = InstrumentationRegistry.getArguments()
    private val sshHost: String? get() = arguments.getString("liveMoshHost")
    private val sshPort: Int get() = arguments.getString("liveMoshPort")?.toIntOrNull() ?: 22
    private val sshUser: String get() = arguments.getString("liveMoshUsername") ?: "mosher"
    private val sshPassword: String? get() = arguments.getString("liveMoshPassword")

    private val host
        get() = HostConnection(
            id = "forward-host",
            name = "forwards",
            host = sshHost!!,
            port = sshPort,
            username = sshUser,
            preferredAuth = AuthMethod.PASSWORD
        )

    private fun forward(type: PortForwardType, sourcePort: Int, destinationHost: String = "", destinationPort: Int = 0) =
        PortForward(
            id = "fwd-${type.name.lowercase()}-$sourcePort",
            label = "${type.name.lowercase()} $sourcePort",
            type = type,
            sourceHost = "127.0.0.1",
            sourcePort = sourcePort,
            destinationHost = destinationHost,
            destinationPort = destinationPort,
            associatedHosts = listOf(host.id),
            enabled = true
        )

    @Test
    fun dynamicForward_isASocksProxyThroughTheServer() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        val socksPort = 19080
        withSession("fwd-dynamic", listOf(forward(PortForwardType.DYNAMIC, socksPort))) { _, _ ->
            // SOCKS5 by address and by name (resolved by the server), then SOCKS4a: each reaches the server's sshd.
            check(socks5Connect(socksPort, addressType = 1, host = "127.0.0.1", port = sshPort).banner().startsWith("SSH-2.0-"))
            check(socks5Connect(socksPort, addressType = 3, host = "localhost", port = sshPort).banner().startsWith("SSH-2.0-"))
            check(socks4aConnect(socksPort, "localhost", sshPort).banner().startsWith("SSH-2.0-"))

            // Nothing listens on the server's port 1: the proxy says so and stays up for the next client.
            val refused = socks5Request(socksPort, addressType = 1, host = "127.0.0.1", port = 1)
            check(refused.reply != 0) { "A closed port was reported as connected" }
            refused.socket.close()
            // Several clients at once, like a browser.
            val results = Collections.synchronizedList(mutableListOf<String>())
            (1..6).map {
                thread { results += socks5Connect(socksPort, 3, "localhost", sshPort).banner() }
            }.forEach { it.join(30_000) }
            check(results.size == 6 && results.all { it.startsWith("SSH-2.0-") }) { "Parallel clients: $results" }
        }
    }

    @Test
    fun remoteForward_aPortOnTheServerReachesThisPhone() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        phoneService("PHONE_OK").use { phone ->
            val forwards = listOf(
                forward(PortForwardType.REMOTE, SERVER_PORT, "127.0.0.1", phone.localPort),
                // The way back in for the test: a local forward to the server's end of the remote forward.
                forward(PortForwardType.LOCAL, 19081, "127.0.0.1", SERVER_PORT)
            )
            withSession("fwd-remote", forwards) { _, log ->
                repeat(3) {
                    check(readLineFrom(19081) == "PHONE_OK") { "Remote forward did not reach the phone: $log" }
                }
                check(log.any { it.contains("Remote forward active: server 127.0.0.1:$SERVER_PORT") }) { "$log" }
            }
        }
    }

    @Test
    fun remoteAndDynamicForwardsComeBackAfterAReconnect() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        phoneService("PHONE_AGAIN").use { phone ->
            val socksPort = 19082
            val forwards = listOf(
                forward(PortForwardType.REMOTE, SERVER_PORT + 1, "127.0.0.1", phone.localPort),
                forward(PortForwardType.DYNAMIC, socksPort)
            )
            withSession("fwd-reconnect", forwards) { service, log ->
                // Through the proxy to the server's end of the remote forward, and so back to this phone.
                fun roundTrip() = socks5Connect(socksPort, 1, "127.0.0.1", SERVER_PORT + 1).banner()
                check(roundTrip() == "PHONE_AGAIN")

                service.dropSshConnectionForTests("fwd-reconnect")
                val deadline = System.currentTimeMillis() + 90_000
                var last: Any? = null
                while (System.currentTimeMillis() < deadline) {
                    last = runCatching { roundTrip() }.getOrElse { it }
                    if (last == "PHONE_AGAIN") break
                    Thread.sleep(1_000)
                }
                check(last == "PHONE_AGAIN") { "Forwards did not come back: $last; $log" }
                check(log.count { it.contains("Remote forward active") } >= 2) { "$log" }
            }
        }
    }

    @Test
    fun remoteForwardTheServerRefuses_saysWhatToCheckAndKeepsTheSession() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        // An ordinary user may not listen on a privileged port.
        withSession("fwd-refused", listOf(forward(PortForwardType.REMOTE, 80, "127.0.0.1", 9))) { service, log ->
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && log.none { it.contains("Failed to start Remote forward") }) {
                Thread.sleep(200)
            }
            val failure = log.firstOrNull { it.contains("Failed to start Remote forward") }
            check(failure != null && failure.contains("AllowTcpForwarding")) { "No helpful refusal in: $log" }
            check(service.sessionsFlow().value.any { it.hostId == "fwd-refused" && it.status == SessionStatus.ACTIVE })
        }
    }

    // --- helpers ---

    /** A one-line service on this phone, standing in for an app the server should reach. */
    private fun phoneService(line: String): ServerSocket {
        val server = ServerSocket()
        server.bind(InetSocketAddress("127.0.0.1", 0))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { it.getOutputStream().apply { write("$line\n".toByteArray()); flush() } }
                }
            }
        }
        return server
    }

    private fun readLineFrom(port: Int): String = Socket().use { socket ->
        socket.soTimeout = 20_000
        socket.connect(InetSocketAddress("127.0.0.1", port), 10_000)
        socket.banner()
    }

    /**
     * Each forwarded connection holds threads. They used to come from the pool everything else in the service runs
     * on, so enough open connections (a browser's, or clients that connect and say nothing) left none to answer the
     * terminal or even to close the session.
     */
    @Test
    fun manyIdleClientsNeitherStallTheSessionNorStayForever() {
        assumeTrue("Needs -e liveMoshHost", sshHost != null)
        val socksPort = 19086
        val sessionId = "fwd-many"
        withSession(sessionId, listOf(forward(PortForwardType.DYNAMIC, socksPort))) { service, log ->
            // More than the limit, none of them saying a word.
            val idle = (1..140).map {
                Socket().apply { connect(InetSocketAddress("127.0.0.1", socksPort), 5_000) }
            }
            try {
                service.sendShellInput(sessionId, "echo MANY-\$((40+2))\n")
                val answered = System.currentTimeMillis() + 15_000
                while (!service.shellOutputFlow().value[sessionId].orEmpty().contains("MANY-42")) {
                    check(System.currentTimeMillis() < answered) { "The terminal didn't answer with 140 idle clients on the proxy" }
                    Thread.sleep(50)
                }
                check(log.any { it.contains("forwarded connections are open at once") }) { "Nothing said the limit was reached: $log" }
                // Those over the limit were closed at once, the others when the handshake's time ran out.
                idle.forEach { socket ->
                    socket.soTimeout = 40_000
                    val read = runCatching { socket.getInputStream().read() }.getOrElse { err ->
                        check(err !is java.net.SocketTimeoutException) { "An idle client was kept for over 40 seconds" }
                        -1
                    }
                    check(read == -1) { "An idle client was sent data: $read" }
                }
                // With them gone the proxy serves again.
                check(socks5Connect(socksPort, addressType = 1, host = "127.0.0.1", port = sshPort).banner().startsWith("SSH-2.0-"))
            } finally {
                idle.forEach { runCatching { it.close() } }
            }
        }
    }

    private fun Socket.banner(): String = use {
        soTimeout = 20_000
        getInputStream().bufferedReader().readLine().orEmpty()
    }

    private class SocksReply(val socket: Socket, val reply: Int)

    private fun socks5Request(proxyPort: Int, addressType: Int, host: String, port: Int): SocksReply {
        val socket = Socket()
        socket.soTimeout = 20_000
        socket.connect(InetSocketAddress("127.0.0.1", proxyPort), 10_000)
        val output = socket.getOutputStream()
        val input = DataInputStream(socket.getInputStream())
        output.write(byteArrayOf(5, 1, 0))
        output.flush()
        check(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0) { "SOCKS5 method not accepted" }
        val address = if (addressType == 1) {
            host.split('.').map { it.toInt().toByte() }.toByteArray()
        } else {
            byteArrayOf(host.length.toByte()) + host.toByteArray()
        }
        output.write(byteArrayOf(5, 1, 0, addressType.toByte()) + address + byteArrayOf((port shr 8).toByte(), port.toByte()))
        output.flush()
        check(input.readUnsignedByte() == 5)
        val reply = input.readUnsignedByte()
        input.readFully(ByteArray(8))
        return SocksReply(socket, reply)
    }

    private fun socks5Connect(proxyPort: Int, addressType: Int, host: String, port: Int): Socket {
        val result = socks5Request(proxyPort, addressType, host, port)
        check(result.reply == 0) { "SOCKS5 connect to $host:$port failed with reply ${result.reply}" }
        return result.socket
    }

    private fun socks4aConnect(proxyPort: Int, host: String, port: Int): Socket {
        val socket = Socket()
        socket.soTimeout = 20_000
        socket.connect(InetSocketAddress("127.0.0.1", proxyPort), 10_000)
        val output = socket.getOutputStream()
        output.write(
            byteArrayOf(4, 1, (port shr 8).toByte(), port.toByte(), 0, 0, 0, 1, 0) + host.toByteArray() + byteArrayOf(0)
        )
        output.flush()
        val reply = ByteArray(8)
        DataInputStream(socket.getInputStream()).readFully(reply)
        check(reply[1].toInt() == 0x5A) { "SOCKS4a connect failed with ${reply[1]}" }
        return socket
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun withSession(
        sessionId: String,
        forwards: List<PortForward>,
        block: (SessionService, List<String>) -> Unit
    ) {
        // Read while the collector appends: a copy-on-write list is safe to iterate.
        val log = CopyOnWriteArrayList<String>()
        val collector = GlobalScope.launch(Dispatchers.Default) {
            SessionLogBus.entries.collect { if (it.hostId == sessionId) log += it.message }
        }
        try {
            withSessionService { service ->
                service.startSession(
                    requestedSessionId = sessionId,
                    host = host,
                    mode = ConnectionMode.SSH,
                    passwordOverride = sshPassword,
                    availableForwards = forwards,
                    autoTrustUnknownHostKey = true,
                    hostKeyPromptEnabled = false
                )
                try {
                    waitForActive(service, sessionId)
                    block(service, log)
                } finally {
                    service.stopSession(sessionId)
                }
            }
        } finally {
            collector.cancel()
        }
    }

    private fun waitForActive(service: SessionService, sessionId: String) {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
            check(snapshot?.status != SessionStatus.ERROR) { "Session failed: ${snapshot?.statusMessage}" }
            if (snapshot?.status == SessionStatus.ACTIVE) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not become active")
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
        /** Ports the server listens on for the remote forwards (its loopback only). */
        const val SERVER_PORT = 47631
    }
}
