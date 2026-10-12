package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.sftp.OutstandingSftpRead
import com.majordaftapps.sshpeaches.app.sftp.SftpAsyncReadSource
import com.majordaftapps.sshpeaches.app.sftp.SftpOffsetSink
import com.majordaftapps.sshpeaches.app.sftp.SftpPipelinedDownloader
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferSettings
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Feeds [SftpPipelinedDownloader] READ replies the way a hostile or odd server might: short reads,
 * out-of-order completion, zero-length DATA, and EOF before the size the server reported.
 */
class SftpDownloaderFuzzTest {

    /** A fake remote file. [reply] decides what a READ at (offset, length) returns. */
    private class FakeSource(
        private val random: Random,
        private val reply: (offset: Long, length: Int) -> ByteArray?
    ) : SftpAsyncReadSource {
        val reads = AtomicInteger()
        override fun issueRead(offset: Long, length: Int): OutstandingSftpRead {
            reads.incrementAndGet()
            val polls = synchronized(random) { random.nextInt(0, 3) }
            return object : OutstandingSftpRead {
                private var remainingPolls = polls
                override val offset: Long = offset
                override val length: Int = length
                override val isComplete: Boolean
                    get() = synchronized(this) { remainingPolls-- <= 0 }
                override fun await(): ByteArray? = reply(offset, length)
            }
        }
    }

    private class MemorySink(size: Int) : SftpOffsetSink {
        val bytes = ByteArray(size)
        override fun writeAt(offset: Long, data: ByteArray) {
            synchronized(this) { data.copyInto(bytes, offset.toInt()) }
        }
    }

    private fun settings(random: Random) = SftpTransferSettings(
        sftpReadSize = listOf(1, 7, 512, 4096, 32 * 1024, 262_144).random(random),
        sftpMaxRequests = random.nextInt(1, 17),
        parallelDownloads = random.nextInt(1, 5)
    )

    @Test
    fun shortAndReorderedRepliesStillReproduceTheFileExactly() {
        val random = Random(Fuzz.SEED + 30)
        repeat(120) { iteration ->
            val settings = settings(random)
            val content = ByteArray(random.nextInt(0, minOf(200_000, settings.sftpReadSize * 400 + 1))).also(random::nextBytes)
            val sink = MemorySink(content.size)
            val source = FakeSource(random) { offset, length ->
                if (offset >= content.size) {
                    null
                } else {
                    val available = (content.size - offset).toInt().coerceAtMost(length)
                    val count = synchronized(random) { if (random.nextInt(4) == 0) random.nextInt(1, available + 1) else available }
                    content.copyOfRange(offset.toInt(), offset.toInt() + count)
                }
            }
            Fuzz.withinMillis(20_000, "download#$iteration") {
                SftpPipelinedDownloader.downloadRanges(
                    fileSize = content.size.toLong(),
                    settings = settings,
                    sink = sink,
                    sourceForRange = { source },
                    waitTimeoutMs = 5_000
                )
            }
            assertArrayEquals("iteration $iteration settings=$settings", content, sink.bytes)
        }
    }

    /**
     * A DATA reply with zero bytes is treated as "short read, ask again for the same offset" with
     * no limit, so a server that keeps answering empty DATA spins the download forever.
     */
    @Test
    fun aServerAnsweringEmptyDataCannotSpinTheDownloadForever() {
        val random = Random(Fuzz.SEED + 31)
        val source = FakeSource(random) { _, _ -> ByteArray(0) }
        val outcome = runCatching {
            Fuzz.withinMillis(5_000, "empty-data") {
                SftpPipelinedDownloader.download(
                    fileSize = 4096,
                    settings = SftpTransferSettings(sftpReadSize = 1024, sftpMaxRequests = 4, parallelDownloads = 1),
                    source = source,
                    sink = MemorySink(4096),
                    waitTimeoutMs = 1_000
                )
            }
        }
        val error = outcome.exceptionOrNull()
        assertTrue(
            "download did not stop after ${source.reads.get()} empty replies: ${error?.message?.lineSequence()?.firstOrNull()}",
            error is IOException
        )
    }

    /**
     * When the file ends before the size from stat (a file truncated while downloading, or /proc
     * and /sys files whose stat size is 0 or 4096), EOF replies are ignored and the download
     * "succeeds" with the missing tail left as zero bytes.
     */
    @Test
    fun eofBeforeTheReportedSizeIsAFailureNotASilentlyPaddedFile() {
        val random = Random(Fuzz.SEED + 32)
        val reportedSize = 64 * 1024
        val realSize = 10_000
        val content = ByteArray(realSize).also(random::nextBytes)
        val source = FakeSource(random) { offset, length ->
            if (offset >= realSize) null else content.copyOfRange(offset.toInt(), (offset + length).coerceAtMost(realSize.toLong()).toInt())
        }
        val sink = MemorySink(reportedSize)
        val outcome = runCatching {
            Fuzz.withinMillis(10_000, "early-eof") {
                SftpPipelinedDownloader.download(
                    fileSize = reportedSize.toLong(),
                    settings = SftpTransferSettings(sftpReadSize = 4096, sftpMaxRequests = 8, parallelDownloads = 1),
                    source = source,
                    sink = sink,
                    waitTimeoutMs = 1_000
                )
            }
        }
        if (outcome.isSuccess) {
            fail(
                "download reported success although the server hit EOF at $realSize of $reportedSize bytes; " +
                    "${reportedSize - realSize} trailing bytes are zero padding"
            )
        }
    }

    @Test
    fun anyHostileReplyMixEndsWithinTheTimeoutBudget() {
        val random = Random(Fuzz.SEED + 33)
        repeat(60) { iteration ->
            val size = random.nextInt(1, 50_000)
            val content = ByteArray(size).also(random::nextBytes)
            val sink = MemorySink(size)
            val source = FakeSource(random) { offset, length ->
                when (random.nextInt(10)) {
                    0 -> throw IOException("server error")
                    1 -> null
                    else -> if (offset >= size) null else content.copyOfRange(
                        offset.toInt(),
                        (offset + random.nextInt(1, length + 1)).coerceAtMost(size.toLong()).toInt()
                    )
                }
            }
            val error = runCatching {
                Fuzz.withinMillis(10_000, "mix#$iteration") {
                    SftpPipelinedDownloader.download(
                        fileSize = size.toLong(),
                        settings = SftpTransferSettings(sftpReadSize = random.nextInt(1, 8192), sftpMaxRequests = random.nextInt(1, 8)),
                        source = source,
                        sink = sink,
                        waitTimeoutMs = 1_000
                    )
                }
            }.exceptionOrNull()
            if (error is AssertionError) throw error
            assertTrue("iteration $iteration: ${error?.javaClass?.name}", error == null || error is IOException)
        }
    }
}
