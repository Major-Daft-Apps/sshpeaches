package com.majordaftapps.sshpeaches.app.sftp

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SftpPipelinedDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun fillsReadWindowBeforeTheFirstWriteWaitCycle() {
        val chunk = 8
        val window = 3
        val payload = ByteArray(chunk * 6) { index -> (index + 3).toByte() }
        val source = ControllableReadSource(payload)
        val sink = RecordingOffsetSink()
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = window,
            parallelDownloads = 4
        )

        val finished = CountDownLatch(1)
        val worker = Thread {
            try {
                SftpPipelinedDownloader.download(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    source = source,
                    sink = sink
                )
            } finally {
                finished.countDown()
            }
        }
        worker.start()
        source.waitForIssued(window)
        assertEquals("window should fill before any completion", window, source.issued.size)
        assertEquals(window, source.peakInflight.get())
        assertTrue(sink.writes.isEmpty())
        assertEquals(0, sink.forceCount.get())
        source.completeAll()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertArrayEquals(payload, sink.assembled(payload.size))
        assertEquals(0, sink.forceCount.get())
    }

    @Test
    fun writesCompletedChunksAtResponseOffsetOutOfOrder() {
        val chunk = 8
        val window = 3
        val payload = ByteArray(chunk * 5) { index -> (index * 7).toByte() }
        val source = ControllableReadSource(payload)
        val local = temp.newFile("out-of-order.bin")
        RandomAccessSink(local).use { fileSink ->
            val sink = CountingSink(fileSink)
            val settings = SftpTransferSettings(
                sftpReadSize = chunk,
                sftpMaxRequests = window,
                parallelDownloads = 4
            )
            val finished = CountDownLatch(1)
            Thread {
                try {
                    SftpPipelinedDownloader.download(
                        fileSize = payload.size.toLong(),
                        settings = settings,
                        source = source,
                        sink = sink
                    )
                } finally {
                    finished.countDown()
                }
            }.start()
            source.waitForIssued(window)
            source.complete(2)
            source.waitForIssued(window + 1)
            source.complete(0)
            source.complete(1)
            source.completeAll()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(0, sink.forceCount.get())
        }
        assertArrayEquals(payload, local.readBytes())
    }

    @Test
    fun refillsPipelineWhenAReadCompletesAndBytesRemain() {
        val chunk = 8
        val window = 3
        val payload = ByteArray(chunk * 6) { index -> index.toByte() }
        val source = ControllableReadSource(payload)
        val sink = RecordingOffsetSink()
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = window,
            parallelDownloads = 4
        )
        val finished = CountDownLatch(1)
        Thread {
            try {
                SftpPipelinedDownloader.download(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    source = source,
                    sink = sink
                )
            } finally {
                finished.countDown()
            }
        }.start()
        source.waitForIssued(window)
        val issuedBefore = source.issued.map { it.offset to it.length }
        source.complete(0)
        source.waitForIssued(window + 1)
        val refilled = source.issued.last()
        assertEquals(chunk.toLong() * window, refilled.offset)
        assertEquals(chunk, refilled.length)
        assertTrue(source.issued.size > issuedBefore.size)
        source.completeAll()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertArrayEquals(payload, sink.assembled(payload.size))
        assertEquals(0, sink.forceCount.get())
    }

    @Test
    fun neverForcesOrFsyncsTheSinkPerChunk() {
        val payload = ByteArray(64) { it.toByte() }
        val source = ControllableReadSource(payload)
        val sink = RecordingOffsetSink()
        val worker = Thread {
            SftpPipelinedDownloader.download(
                fileSize = payload.size.toLong(),
                settings = SftpTransferSettings(sftpReadSize = 16, sftpMaxRequests = 4),
                source = source,
                sink = sink
            )
        }
        worker.start()
        source.waitForIssued(4)
        source.completeAll()
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals(0, sink.forceCount.get())
        assertTrue(sink.writes.isNotEmpty())
        sink.writes.forEach { write ->
            assertFalse("per-chunk force must not run", write.forced)
        }
    }

    @Test
    fun defaultsMatchRequestedKnobs() {
        val defaults = SftpTransferSettings()
        assertEquals(262144, defaults.sftpReadSize)
        assertEquals(128, defaults.sftpMaxRequests)
        assertEquals(4, defaults.parallelDownloads)
        assertTrue(defaults.sftpFast)
    }

    @Test
    fun sftpFastPresetSelectsDefaultKnobValues() {
        val fast = SftpTransferSettings.fastPreset()
        assertEquals(262144, fast.sftpReadSize)
        assertEquals(128, fast.sftpMaxRequests)
        assertEquals(4, fast.parallelDownloads)
        val custom = SftpTransferSettings(
            sftpReadSize = 32,
            sftpMaxRequests = 2,
            parallelDownloads = 1
        )
        assertFalse(custom.sftpFast)
        assertEquals(fast, custom.applyFastPreset())
    }

    @Test
    fun shortDataRepliesKeepTheRequestWindowAndStillAssembleTheFile() {
        val chunk = 16
        val window = 3
        val payload = ByteArray(chunk * 8) { index -> (index * 13).toByte() }
        val source = ControllableReadSource(payload) { requested ->
            (requested / 2).coerceAtLeast(1)
        }
        val sink = RecordingOffsetSink()
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = window,
            parallelDownloads = 4
        )
        val finished = CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        Thread {
            try {
                SftpPipelinedDownloader.download(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    source = source,
                    sink = sink
                )
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }.start()
        source.waitForIssued(window)
        assertEquals(window, source.issued.size)
        assertEquals(window, source.peakInflight.get())
        assertTrue(sink.writes.isEmpty())
        source.completeAll()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
        assertTrue(
            "short DATA replies must produce follow-up remainder READs: ${source.issued.map { it.length }}",
            source.issued.any { it.length < chunk }
        )
        assertEquals(
            "short replies must not grow the READ window",
            window,
            source.peakInflight.get()
        )
        assertArrayEquals(payload, sink.assembled(payload.size))
        assertEquals(0, sink.forceCount.get())
    }

    @Test
    fun rangedDownloadOnlyIssuesReadsInsideTheRequestedOffsets() {
        val chunk = 8
        val payload = ByteArray(chunk * 8) { index -> index.toByte() }
        val source = ControllableReadSource(payload)
        val sink = RecordingOffsetSink()
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = 2,
            parallelDownloads = 4
        )
        val finished = CountDownLatch(1)
        Thread {
            try {
                SftpPipelinedDownloader.download(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    source = source,
                    sink = sink,
                    rangeStart = 16L,
                    rangeEnd = 40L
                )
            } finally {
                finished.countDown()
            }
        }.start()
        source.waitForIssued(2)
        source.completeAll()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(source.issued.isNotEmpty())
        source.issued.forEach { issued ->
            assertTrue(
                "READ offset ${issued.offset} length ${issued.length} left the range [16, 40)",
                issued.offset >= 16L && issued.offset + issued.length <= 40L
            )
        }
        val expected = payload.copyOfRange(16, 40)
        val assembled = sink.assembled(payload.size).copyOfRange(16, 40)
        assertArrayEquals(expected, assembled)
    }

    @Test
    fun downloadsOneLargeFileAsDisjointRangedWorkers() {
        val chunk = 8
        val window = 2
        val parallel = 4
        val payload = ByteArray(chunk * 16) { index -> (index * 5).toByte() }
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = window,
            parallelDownloads = parallel
        )
        assertEquals(parallel, SftpPipelinedDownloader.segmentCount(payload.size.toLong(), settings))
        val ranges = SftpPipelinedDownloader.splitRanges(payload.size.toLong(), settings)
        assertEquals(parallel, ranges.size)
        assertEquals(0L, ranges.first().start)
        assertEquals(payload.size.toLong(), ranges.last().endExclusive)
        ranges.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endExclusive, right.start)
        }

        val started = CountDownLatch(parallel)
        val release = CountDownLatch(1)
        val peakSegments = AtomicInteger(0)
        val liveSegments = AtomicInteger(0)
        val sources = java.util.concurrent.ConcurrentHashMap<SftpByteRange, ControllableReadSource>()
        val sink = RecordingOffsetSink()
        val finished = CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        Thread {
            try {
                SftpPipelinedDownloader.downloadRanges(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    sink = sink,
                    sourceForRange = { range ->
                        val source = ControllableReadSource(payload)
                        sources[range] = source
                        HoldFirstReadSource(source, started, release)
                    },
                    onSegmentStarted = {
                        peakSegments.updateAndGet { current ->
                            maxOf(current, liveSegments.incrementAndGet())
                        }
                    }
                )
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }.start()
        val reached = started.await(5, TimeUnit.SECONDS)
        release.countDown()
        assertTrue("expected $parallel ranged workers in flight together", reached)
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
        assertTrue(peakSegments.get() >= parallel)
        assertEquals(parallel, sources.size)
        sources.forEach { (range, source) ->
            assertTrue(source.issued.isNotEmpty())
            source.issued.forEach { issued ->
                assertTrue(
                    "segment [${range.start}, ${range.endExclusive}) issued ${issued.offset}+${issued.length}",
                    issued.offset >= range.start && issued.offset < range.endExclusive
                )
            }
        }
        assertArrayEquals(payload, sink.assembled(payload.size))
        assertEquals(0, sink.forceCount.get())
    }

    @Test
    fun customReadSizeAndMaxRequestsAreTheSizesTheEngineUses() {
        val chunk = 16
        val window = 2
        val payload = ByteArray(chunk * 5) { index -> (255 - index).toByte() }
        val source = ControllableReadSource(payload)
        val sink = RecordingOffsetSink()
        val settings = SftpTransferSettings(
            sftpReadSize = chunk,
            sftpMaxRequests = window,
            parallelDownloads = 3
        )
        val finished = CountDownLatch(1)
        Thread {
            try {
                SftpPipelinedDownloader.download(
                    fileSize = payload.size.toLong(),
                    settings = settings,
                    source = source,
                    sink = sink
                )
            } finally {
                finished.countDown()
            }
        }.start()
        source.waitForIssued(window)
        assertTrue(source.issued.all { it.length == chunk })
        assertEquals(window, source.peakInflight.get())
        source.completeAll()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(source.issued.all { issued ->
            issued.length == chunk || issued.offset + issued.length == payload.size.toLong()
        })
        assertEquals(window, source.peakInflight.get())
        assertArrayEquals(payload, sink.assembled(payload.size))
        assertEquals(0, sink.forceCount.get())
    }

    private class HoldFirstReadSource(
        private val inner: ControllableReadSource,
        private val started: CountDownLatch,
        private val release: CountDownLatch
    ) : SftpAsyncReadSource {
        private val first = AtomicBoolean(true)

        override fun issueRead(offset: Long, length: Int): OutstandingSftpRead {
            val outstanding = inner.issueRead(offset, length)
            if (first.compareAndSet(true, false)) {
                started.countDown()
                release.await()
                inner.completeAll()
            }
            return outstanding
        }
    }

    private class ControllableReadSource(
        private val payload: ByteArray,
        private val replyLength: (requested: Int) -> Int = { requested -> requested }
    ) : SftpAsyncReadSource {
        inner class Issued(
            override val offset: Long,
            override val length: Int
        ) : OutstandingSftpRead {
            private val gate = CountDownLatch(1)
            private val completed = AtomicBoolean(false)

            override val isComplete: Boolean
                get() = completed.get()

            override fun await(): ByteArray? {
                gate.await()
                val from = offset.toInt()
                if (from >= payload.size) return null
                val allowed = replyLength(length).coerceIn(0, length)
                val to = minOf(payload.size, from + allowed)
                return payload.copyOfRange(from, to)
            }

            fun release() {
                completed.set(true)
                gate.countDown()
            }
        }

        val issued = CopyOnWriteArrayList<Issued>()
        val peakInflight = AtomicInteger(0)
        private val inflight = AtomicInteger(0)
        private val autoComplete = AtomicBoolean(false)

        override fun issueRead(offset: Long, length: Int): OutstandingSftpRead {
            val item = Issued(offset, length)
            issued += item
            peakInflight.updateAndGet { current -> maxOf(current, inflight.incrementAndGet()) }
            if (autoComplete.get()) {
                item.release()
            }
            return object : OutstandingSftpRead {
                override val offset: Long = item.offset
                override val length: Int = item.length
                override val isComplete: Boolean
                    get() = item.isComplete

                override fun await(): ByteArray? {
                    try {
                        return item.await()
                    } finally {
                        inflight.decrementAndGet()
                    }
                }
            }
        }

        fun complete(index: Int) {
            issued[index].release()
        }

        fun completeAll() {
            autoComplete.set(true)
            issued.forEach { it.release() }
        }

        fun waitForIssued(count: Int, timeoutMs: Long = 5_000) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (issued.size < count) {
                if (System.nanoTime() >= deadline) {
                    fail("Timed out waiting for $count issued READs; had ${issued.size}")
                }
                Thread.sleep(5)
            }
        }
    }

    private class RecordingOffsetSink : SftpOffsetSink {
        data class Write(val offset: Long, val data: ByteArray, val forced: Boolean = false)

        val writes = CopyOnWriteArrayList<Write>()
        val forceCount = AtomicInteger(0)

        override fun writeAt(offset: Long, data: ByteArray) {
            writes += Write(offset, data.copyOf())
        }

        override fun force() {
            forceCount.incrementAndGet()
        }

        fun assembled(size: Int): ByteArray {
            val out = ByteArray(size)
            writes.forEach { write ->
                System.arraycopy(write.data, 0, out, write.offset.toInt(), write.data.size)
            }
            return out
        }
    }

    private class CountingSink(
        private val inner: SftpOffsetSink
    ) : SftpOffsetSink {
        val forceCount = AtomicInteger(0)

        override fun writeAt(offset: Long, data: ByteArray) {
            inner.writeAt(offset, data)
        }

        override fun force() {
            forceCount.incrementAndGet()
            inner.force()
        }
    }

    private class RandomAccessSink(
        file: File
    ) : SftpOffsetSink, AutoCloseable {
        private val raf = java.io.RandomAccessFile(file, "rw")
        private val sink = FileChannelOffsetSink(raf.channel)

        override fun writeAt(offset: Long, data: ByteArray) {
            sink.writeAt(offset, data)
        }

        override fun close() {
            raf.close()
        }
    }
}
