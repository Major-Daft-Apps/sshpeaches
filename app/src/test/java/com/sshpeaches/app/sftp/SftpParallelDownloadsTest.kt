package com.majordaftapps.sshpeaches.app.sftp

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpParallelDownloadsTest {

    @Test
    fun runsUpToParallelDownloadsOnOneAlreadyConnectedSession() {
        val parallel = 4
        val jobCount = 6
        val settings = SftpTransferSettings(
            sftpReadSize = 8,
            sftpMaxRequests = 2,
            parallelDownloads = parallel
        )
        val payloads = List(jobCount) { index ->
            ByteArray(32) { byteIndex -> (index * 31 + byteIndex).toByte() }
        }
        val connectCount = AtomicInteger(1)
        val authCount = AtomicInteger(1)
        val startedParallel = CountDownLatch(1)
        val release = CountDownLatch(1)
        val observed = CopyOnWriteArrayList<Int>()
        val sinks = payloads.map { RecordingSink() }
        val startedFiles = AtomicInteger(0)
        val jobs = payloads.mapIndexed { index, payload ->
            SftpPipelinedJob(
                fileSize = payload.size.toLong(),
                source = HoldOnFirstReadSource(
                    payload = payload,
                    startedParallel = startedParallel,
                    release = release,
                    parallel = parallel,
                    startedFiles = startedFiles
                ),
                sink = sinks[index]
            )
        }
        val admission = SftpDownloadAdmission.fromSettings(settings)
        val finished = CountDownLatch(1)
        Thread {
            try {
                SftpParallelDownloads.downloadAll(
                    jobs = jobs,
                    settings = settings,
                    admission = admission,
                    observer = { inflight ->
                        observed += inflight
                        if (inflight >= parallel) {
                            startedParallel.countDown()
                        }
                    }
                )
            } finally {
                finished.countDown()
            }
        }.start()

        val reached = startedParallel.await(5, TimeUnit.SECONDS)
        release.countDown()
        assertTrue("expected $parallel files in flight together (peak=${admission.peakInflight})", reached)
        assertTrue(admission.peakInflight >= parallel)
        assertEquals("must reuse the already-connected session", 1, connectCount.get())
        assertEquals(1, authCount.get())
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertTrue(admission.peakInflight >= parallel)
        assertTrue(observed.any { it >= parallel })
        payloads.forEachIndexed { index, payload ->
            assertArrayEquals(payload, sinks[index].assembled(payload.size))
        }
        assertEquals(1, connectCount.get())
        assertEquals(1, authCount.get())
    }

    private class HoldOnFirstReadSource(
        private val payload: ByteArray,
        private val startedParallel: CountDownLatch,
        private val release: CountDownLatch,
        private val parallel: Int,
        private val startedFiles: AtomicInteger
    ) : SftpAsyncReadSource {
        private val first = AtomicBoolean(true)

        override fun issueRead(offset: Long, length: Int): OutstandingSftpRead {
            if (first.compareAndSet(true, false)) {
                if (startedFiles.incrementAndGet() >= parallel) {
                    startedParallel.countDown()
                }
                release.await()
            }
            val from = offset.toInt()
            val data = if (from >= payload.size) {
                null
            } else {
                payload.copyOfRange(from, minOf(payload.size, from + length))
            }
            return object : OutstandingSftpRead {
                override val offset: Long = offset
                override val length: Int = length
                override val isComplete: Boolean = true
                override fun await(): ByteArray? = data
            }
        }
    }

    private class RecordingSink : SftpOffsetSink {
        private val writes = CopyOnWriteArrayList<Pair<Long, ByteArray>>()
        val forceCount = AtomicInteger(0)

        override fun writeAt(offset: Long, data: ByteArray) {
            writes += offset to data.copyOf()
        }

        override fun force() {
            forceCount.incrementAndGet()
        }

        fun assembled(size: Int): ByteArray {
            val out = ByteArray(size)
            writes.forEach { (offset, data) ->
                System.arraycopy(data, 0, out, offset.toInt(), data.size)
            }
            return out
        }
    }
}
