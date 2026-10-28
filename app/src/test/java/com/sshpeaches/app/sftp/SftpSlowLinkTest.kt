package com.majordaftapps.sshpeaches.app.sftp

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.random.Random
import net.schmizz.keepalive.KeepAliveRunner
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Transfers over a slow link that works fine: every connection shares [RATE] bytes a second each way. Keepalives and
 * request timeouts are shortened (10 s and 15 s, from 2 minutes and 30 s) so that queueing too much data ahead of
 * them, which on a phone took a minute to show, shows here in seconds. Nothing may fail or retry.
 */
class SftpSlowLinkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: SshServer
    private lateinit var proxy: ThrottledProxy
    private lateinit var host: HostConnection
    private val clients = Collections.synchronizedList(mutableListOf<SSHClient>())

    @Before
    fun start() {
        sandbox = temp.newFolder("sandbox")
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("hostkey").toPath())
            fileSystemFactory = VirtualFileSystemFactory(sandbox.toPath())
            passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
            subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
            start()
        }
        proxy = ThrottledProxy(server.port, RATE)
        host = HostConnection(
            id = "slow", name = "slow", host = "127.0.0.1", port = proxy.port, username = USER,
            preferredAuth = AuthMethod.PASSWORD
        )
    }

    @After
    fun stop() {
        clients.forEach { runCatching { it.disconnect() } }
        proxy.close()
        runCatching { server.stop(true) }
    }

    private fun connect(): Pair<SSHClient, SFTPClient> {
        val ssh = SshClientProvider.createClientForTesting(knownHostsFile = temp.newFile(), host = host).apply {
            connection.keepAlive.keepAliveInterval = 2
            (connection.keepAlive as KeepAliveRunner).maxAliveCount = 5
            connect(host.host, host.port)
            authPassword(USER, PASSWORD)
        }
        clients += ssh
        val sftp = ssh.newSFTPClient().apply { sftpEngine.timeoutMs = 15_000 }
        return ssh to sftp
    }

    /** Four connections, each lent to one worker at a time. */
    private inner class Pool : SftpLeaseSource {
        private val lock = Object()
        private val idle = ArrayDeque((1..4).map { connect() })

        override fun acquire(stop: () -> Boolean): SftpTransferLease? {
            var taken: Pair<SSHClient, SFTPClient>? = null
            synchronized(lock) {
                while (taken == null) {
                    if (stop()) return null
                    val next = idle.removeFirstOrNull()
                    if (next == null) lock.wait(20) else taken = if (next.first.isConnected) next else connect()
                }
            }
            val (ssh, sftp) = taken!!
            return object : SftpTransferLease {
                override val sftp = sftp
                override fun release() = giveBack(keep = true)
                override fun discard() = giveBack(keep = false)
                private fun giveBack(keep: Boolean) {
                    if (!keep) runCatching { ssh.disconnect() }
                    synchronized(lock) {
                        idle.addLast(if (keep) ssh to sftp else connect())
                        lock.notifyAll()
                    }
                }
            }
        }
    }

    @Test
    fun downloadOverASlowLinkNeverRetries() {
        val content = Random(1).nextBytes(8 * 1024 * 1024)
        File(sandbox, "slow.bin").writeBytes(content)
        val target = File(temp.root, "slow.download")
        val retries = Collections.synchronizedList(mutableListOf<String>())
        val partial = SftpPartialDownload.forDestination(temp.newFolder("state"), "k", "/slow.bin", target.path, target)
        RandomAccessFile(target, "rw").use { raf ->
            SftpResumableDownloader.download(
                leases = Pool(), connections = 4, remotePath = "/slow.bin", size = content.size.toLong(), mtime = 0L,
                partial = partial, channel = raf.channel, settings = SftpTransferSettings(),
                onRetrying = { it?.let(retries::add) }
            )
        }
        assertEquals("retries on a link that works: $retries", emptyList<String>(), retries.toList())
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun uploadOverASlowLinkNeverRetries() {
        val content = Random(2).nextBytes(8 * 1024 * 1024)
        val local = File(temp.root, "slow-up.bin").apply { writeBytes(content) }
        val retries = Collections.synchronizedList(mutableListOf<String>())
        RandomAccessFile(local, "r").use { raf ->
            SftpSegmentedTransfer(
                queue = SftpChunkQueue(SftpChunkQueue.plan(content.size.toLong(), 4)),
                workers = 4, leases = Pool(), requestSize = SftpTransferSettings.UPLOAD_WRITE_SIZE,
                inFlightBytes = { SftpTransferSettings.MAX_IN_FLIGHT_BYTES_PER_CONNECTION },
                openIo = { sftp, first -> SftpSegmentWriter.open(sftp, "/up.bin", raf.channel, first, 0b110_100_100) },
                onRetrying = { it?.let(retries::add) }
            ).run()
        }
        assertEquals("retries on a link that works: $retries", emptyList<String>(), retries.toList())
        assertArrayEquals(content, File(sandbox, "up.bin").readBytes())
    }

    /** A TCP proxy whose connections share [bytesPerSecond] each way, holding the sender back like a slow link. */
    private class ThrottledProxy(targetPort: Int, private val bytesPerSecond: Long) : AutoCloseable {
        private val listener = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val port: Int = listener.localPort
        private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
        private val buckets = mapOf(true to Bucket(), false to Bucket())

        private inner class Bucket {
            private var tokens = 0.0
            private var at = System.nanoTime()

            @Synchronized
            fun take(n: Int) {
                while (true) {
                    val now = System.nanoTime()
                    tokens = minOf(bytesPerSecond / 4.0, tokens + (now - at) / 1e9 * bytesPerSecond)
                    at = now
                    if (tokens >= n) {
                        tokens -= n
                        return
                    }
                    Thread.sleep(((n - tokens) / bytesPerSecond * 1000).toLong().coerceAtLeast(1))
                }
            }
        }

        init {
            Thread({
                while (!listener.isClosed) {
                    val client = runCatching { listener.accept() }.getOrNull() ?: break
                    val upstream = Socket("127.0.0.1", targetPort)
                    sockets += client
                    sockets += upstream
                    pump(client, upstream, buckets.getValue(false))
                    pump(upstream, client, buckets.getValue(true))
                }
            }, "throttled-proxy").apply { isDaemon = true }.start()
        }

        private fun pump(from: Socket, to: Socket, bucket: Bucket) {
            Thread({
                val buffer = ByteArray(8 * 1024)
                runCatching {
                    while (true) {
                        val read = from.getInputStream().read(buffer)
                        if (read < 0) break
                        bucket.take(read)
                        to.getOutputStream().write(buffer, 0, read)
                    }
                }
                runCatching { to.shutdownOutput() }
            }, "throttled-pump").apply { isDaemon = true }.start()
        }

        override fun close() {
            runCatching { listener.close() }
            sockets.toList().forEach { runCatching { it.close() } }
        }
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
        const val RATE = 400_000L
    }
}
