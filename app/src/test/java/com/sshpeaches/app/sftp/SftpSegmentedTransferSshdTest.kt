package com.majordaftapps.sshpeaches.app.sftp

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.ssh.SshClientProvider
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Chunked transfers over several real connections to an in-process SFTP server. */
class SftpSegmentedTransferSshdTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: SshServer
    private lateinit var host: HostConnection
    private val clients = mutableListOf<SSHClient>()

    @Before
    fun startServer() {
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
        host = HostConnection(
            id = "segmented", name = "segmented", host = "127.0.0.1", port = server.port,
            username = USER, preferredAuth = AuthMethod.PASSWORD
        )
    }

    @After
    fun stopServer() {
        clients.forEach { runCatching { it.disconnect() } }
        runCatching { server.stop(true) }
    }

    private fun connect(): SSHClient =
        SshClientProvider.createClientForTesting(knownHostsFile = temp.newFile(), host = host).apply {
            connect(host.host, host.port)
            authPassword(USER, PASSWORD)
            clients += this
        }

    /** Lends each of [connections] fresh connections to one worker at a time; a discarded one is replaced. */
    private inner class Pool(connections: Int) : SftpLeaseSource {
        private val lock = Object()
        private val idle = ArrayDeque<Pair<SSHClient, SFTPClient>>()
        val lent = AtomicInteger()
        val peakLent = AtomicInteger()
        val discarded = AtomicInteger()
        val sftpByClient = ConcurrentHashMap<SFTPClient, SSHClient>()

        init {
            repeat(connections) { idle.addLast(connect().let { it to it.newSFTPClient() }) }
        }

        override fun acquire(stop: () -> Boolean): SftpTransferLease? {
            var taken: Pair<SSHClient, SFTPClient>? = null
            synchronized(lock) {
                while (taken == null) {
                    if (stop()) return null
                    val next = idle.removeFirstOrNull()
                    if (next == null) {
                        lock.wait(20)
                    } else {
                        taken = if (next.first.isConnected) next else connect().let { it to it.newSFTPClient() }
                    }
                }
            }
            val (ssh, sftp) = taken!!
            sftpByClient[sftp] = ssh
            peakLent.accumulateAndGet(lent.incrementAndGet(), ::maxOf)
            return object : SftpTransferLease {
                override val sftp = sftp
                override fun release() = giveBack(keep = true)
                override fun discard() = giveBack(keep = false)
                private fun giveBack(keep: Boolean) {
                    lent.decrementAndGet()
                    if (!keep) {
                        discarded.incrementAndGet()
                        runCatching { ssh.disconnect() }
                    }
                    synchronized(lock) {
                        if (keep) {
                            idle.addLast(ssh to sftp)
                        } else {
                            val fresh = connect()
                            idle.addLast(fresh to fresh.newSFTPClient())
                        }
                        lock.notifyAll()
                    }
                }
            }
        }
    }

    private fun payload(size: Int, seed: Int = 7) = Random(seed).nextBytes(size)

    @Test
    fun downloadUsesEveryConnectionAndGetsEveryByte() {
        val content = payload(9 * 1024 * 1024 + 12_345)
        File(sandbox, "big.bin").writeBytes(content)
        val pool = Pool(4)
        val target = File(temp.root, "big.download")
        val partial = SftpPartialDownload.forDestination(temp.newFolder("state"), "k", "/big.bin", target.path, target)
        RandomAccessFile(target, "rw").use { raf ->
            SftpResumableDownloader.download(
                leases = pool, connections = 4, remotePath = "/big.bin", size = content.size.toLong(), mtime = 0L,
                partial = partial, channel = raf.channel, settings = SftpTransferSettings()
            )
        }
        assertArrayEquals(content, target.readBytes())
        assertEquals("all four connections worked at once", 4, pool.peakLent.get())
        assertEquals(0, pool.lent.get())
    }

    @Test
    fun aConnectionDroppedMidDownloadIsReplacedAndItsChunkFinished() {
        val content = payload(24 * 1024 * 1024)
        File(sandbox, "big.bin").writeBytes(content)
        val pool = Pool(3)
        val target = File(temp.root, "big.download")
        val killed = AtomicBoolean(false)
        RandomAccessFile(target, "rw").use { raf ->
            val sink = FileChannelOffsetSink(raf.channel)
            raf.setLength(content.size.toLong())
            val queue = SftpChunkQueue(SftpChunkQueue.plan(content.size.toLong(), 3))
            SftpSegmentedTransfer(
                queue = queue, workers = 3, leases = pool, requestSize = 65_536, inFlightBytes = { 1L shl 20 },
                openIo = { sftp, _ ->
                    val reader = SftpSegmentReader.open(sftp, "/big.bin", sink)
                    object : SftpSegmentIo by reader {
                        override fun issue(offset: Long, length: Int): SftpSegmentIo.Pending {
                            // Cut the first connection once the transfer is well under way.
                            if (offset > content.size / 3 && killed.compareAndSet(false, true)) {
                                pool.sftpByClient[sftp]?.disconnect()
                            }
                            Thread.sleep(1)
                            return reader.issue(offset, length)
                        }
                    }
                },
                retryDelayMs = { 10L }
            ).run()
        }
        assertTrue(killed.get())
        assertTrue("the dropped connection was discarded", pool.discarded.get() >= 1)
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun aRequestStuckOnADeadConnectionIsCutLooseAndTheTransferFinishes() {
        val content = payload(3 * 1024 * 1024, seed = 13)
        File(sandbox, "big.bin").writeBytes(content)
        val pool = Pool(1)
        val target = File(temp.root, "big.download")
        val stuckOnce = AtomicBoolean(false)
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(content.size.toLong())
            val sink = FileChannelOffsetSink(raf.channel)
            SftpSegmentedTransfer(
                queue = SftpChunkQueue(SftpChunkQueue.plan(content.size.toLong(), 1)),
                workers = 1, leases = pool, requestSize = 65_536, inFlightBytes = { 1L shl 20 },
                openIo = { sftp, _ ->
                    val reader = SftpSegmentReader.open(sftp, "/big.bin", sink)
                    val ssh = pool.sftpByClient.getValue(sftp)
                    object : SftpSegmentIo by reader {
                        override fun issue(offset: Long, length: Int): SftpSegmentIo.Pending {
                            // Like a write into a dead network: blocks, and no timeout of its own ends it.
                            if (offset >= 1_048_576 && stuckOnce.compareAndSet(false, true)) {
                                while (ssh.isConnected) Thread.sleep(10)
                                throw IOException("connection dropped")
                            }
                            return reader.issue(offset, length)
                        }
                    }
                },
                retryDelayMs = { 10L },
                stallTimeoutMs = 500L
            ).run()
        }
        assertTrue(stuckOnce.get())
        assertTrue(pool.discarded.get() >= 1)
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun uploadOverSeveralConnectionsWritesTheWholeFile() {
        val content = payload(5 * 1024 * 1024 + 777, seed = 3)
        val local = File(temp.root, "upload.bin").apply { writeBytes(content) }
        val pool = Pool(3)
        RandomAccessFile(local, "r").use { raf ->
            SftpSegmentedTransfer(
                queue = SftpChunkQueue(SftpChunkQueue.plan(content.size.toLong(), 3)),
                workers = 3, leases = pool, requestSize = SftpTransferSettings.UPLOAD_WRITE_SIZE,
                inFlightBytes = { 4L shl 20 },
                openIo = { sftp, first -> SftpSegmentWriter.open(sftp, "/up.bin", raf.channel, first, 0b110_100_100) }
            ).run()
        }
        assertArrayEquals(content, File(sandbox, "up.bin").readBytes())
        assertEquals(3, pool.peakLent.get())
    }

    @Test
    fun uploadReplacesALongerExistingFileAndCreatesEmptyOnes() {
        File(sandbox, "up.bin").writeBytes(ByteArray(3_000_000) { 9 })
        val content = payload(1_500_000, seed = 11)
        val local = File(temp.root, "upload.bin").apply { writeBytes(content) }
        val empty = File(temp.root, "empty.bin").apply { writeBytes(ByteArray(0)) }
        val pool = Pool(2)
        listOf(local to "/up.bin", empty to "/empty.bin").forEach { (file, remote) ->
            RandomAccessFile(file, "r").use { raf ->
                SftpSegmentedTransfer(
                    queue = SftpChunkQueue(SftpChunkQueue.plan(file.length(), 2)),
                    workers = 2, leases = pool, requestSize = SftpTransferSettings.UPLOAD_WRITE_SIZE,
                    inFlightBytes = { 1L shl 20 },
                    openIo = { sftp, first -> SftpSegmentWriter.open(sftp, remote, raf.channel, first, 0b110_100_100) }
                ).run()
            }
        }
        assertArrayEquals(content, File(sandbox, "up.bin").readBytes())
        assertTrue(File(sandbox, "empty.bin").exists())
        assertEquals(0L, File(sandbox, "empty.bin").length())
    }

    @Test
    fun aMissingFileFailsAtOnceInsteadOfRetrying() {
        val pool = Pool(2)
        val attempts = AtomicInteger()
        val started = System.currentTimeMillis()
        try {
            SftpSegmentedTransfer(
                queue = SftpChunkQueue(SftpChunkQueue.plan(1_000_000, 2)),
                workers = 2, leases = pool, requestSize = 65_536, inFlightBytes = { 1L shl 20 },
                openIo = { sftp, _ ->
                    attempts.incrementAndGet()
                    SftpSegmentReader.open(sftp, "/missing.bin", object : SftpOffsetSink {
                        override fun writeAt(offset: Long, data: ByteArray) = Unit
                    })
                }
            ).run()
            fail("a missing file must fail")
        } catch (error: IOException) {
            assertTrue(error.isPermanentSftpFailure())
        }
        assertTrue("no retry delays, took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 5_000)
        assertEquals(1, attempts.get())
    }

    @Test
    fun cancellingStopsPromptlyAndReturnsTheConnections() {
        val content = payload(32 * 1024 * 1024)
        File(sandbox, "big.bin").writeBytes(content)
        val pool = Pool(2)
        val cancel = AtomicBoolean(false)
        val target = File(temp.root, "big.download")
        val partial = SftpPartialDownload.forDestination(temp.newFolder("state"), "k", "/big.bin", target.path, target)
        val outcome = RandomAccessFile(target, "rw").use { raf ->
            runCatching {
                SftpResumableDownloader.download(
                    leases = pool, connections = 2, remotePath = "/big.bin", size = content.size.toLong(), mtime = 0L,
                    partial = partial, channel = raf.channel, settings = SftpTransferSettings(),
                    onBytesTransferred = { done -> if (done > 0) cancel.set(true) },
                    isCancelled = { cancel.get() }
                )
            }
        }
        assertTrue(outcome.exceptionOrNull() is SftpTransferCancelledException)
        assertFalse("progress dropped on cancel", partial.load("/big.bin", content.size.toLong(), 0L) != null)
        assertEquals("every connection handed back", 0, pool.lent.get())
    }

    @Test
    fun resumesFromChunksSavedByAnEarlierAttempt() {
        val content = payload(6 * 1024 * 1024, seed = 5)
        File(sandbox, "big.bin").writeBytes(content)
        val pool = Pool(2)
        val target = File(temp.root, "big.download")
        val partial = SftpPartialDownload.forDestination(temp.newFolder("state"), "k", "/big.bin", target.path, target)
        // An earlier attempt finished the first half of the chunks and left garbage in the rest.
        val chunks = SftpChunkQueue.plan(content.size.toLong(), 2)
        val saved = chunks.mapIndexed { index, chunk -> if (index < chunks.size / 2) chunk.copy(done = chunk.end) else chunk }
        val doneBytes = saved.sumOf { it.done - it.start }
        target.writeBytes(content.copyOf(doneBytes.toInt()) + ByteArray(content.size - doneBytes.toInt()) { 0x55 })
        partial.save("/big.bin", content.size.toLong(), 1L, saved)

        var resumedFrom = -1L
        var firstReport = -1L
        RandomAccessFile(target, "rw").use { raf ->
            SftpResumableDownloader.download(
                leases = pool, connections = 2, remotePath = "/big.bin", size = content.size.toLong(), mtime = 1L,
                partial = partial, channel = raf.channel, settings = SftpTransferSettings(),
                onBytesTransferred = { if (firstReport < 0) firstReport = it },
                onResume = { done, _ -> resumedFrom = done }
            )
        }
        assertEquals(doneBytes, resumedFrom)
        assertTrue("progress counts on from the saved bytes", firstReport > doneBytes)
        assertArrayEquals(content, target.readBytes())
        assertEquals("a finished download keeps no progress record", null, partial.load("/big.bin", content.size.toLong(), 1L))
    }

    @Test
    fun chunkPlanCoversTheFileExactly() {
        listOf(0L, 1L, 1_048_575L, 1_048_576L, 10_000_001L, 5L shl 30).forEach { size ->
            listOf(1, 4, 16).forEach { workers ->
                val chunks = SftpChunkQueue.plan(size, workers)
                assertEquals(size, chunks.sumOf { it.end - it.start })
                chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
                if (size > 0) assertEquals(0L, chunks.first().start)
                assertTrue(chunks.all { it.end - it.start <= SftpChunkQueue.MAX_CHUNK_BYTES })
            }
        }
    }

    private companion object {
        const val USER = "tester"
        const val PASSWORD = "peaches-password"
    }
}
