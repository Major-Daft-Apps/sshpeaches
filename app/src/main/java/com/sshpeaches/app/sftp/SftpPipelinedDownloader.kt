package com.majordaftapps.sshpeaches.app.sftp

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import net.schmizz.concurrent.Promise
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException

fun interface SftpAsyncReadSource {
    fun issueRead(offset: Long, length: Int): OutstandingSftpRead
}

interface OutstandingSftpRead {
    val offset: Long
    val length: Int
    val isComplete: Boolean
    fun await(): ByteArray?
}

interface SftpOffsetSink {
    fun writeAt(offset: Long, data: ByteArray)
    fun force() {}
}

class SftpDownloadCancelledException(message: String = "SFTP download cancelled") : IOException(message)

data class SftpByteRange(
    val start: Long,
    val endExclusive: Long
) {
    val length: Long
        get() = (endExclusive - start).coerceAtLeast(0L)
}

internal class FileChannelOffsetSink(
    private val channel: FileChannel
) : SftpOffsetSink {
    private val writeLock = Any()

    override fun writeAt(offset: Long, data: ByteArray) {
        if (data.isEmpty()) return
        val buffer = ByteBuffer.wrap(data)
        var position = offset
        synchronized(writeLock) {
            while (buffer.hasRemaining()) {
                val written = channel.write(buffer, position)
                if (written < 0) {
                    throw IOException("FileChannel rejected a pipelined SFTP write at offset $position")
                }
                position += written.toLong()
            }
        }
    }
}

internal class SshjAsyncReadSource(
    private val remoteFile: RemoteFile,
    private val timeoutMs: Int
) : SftpAsyncReadSource, AutoCloseable {
    override fun close() {
        remoteFile.close()
    }

    override fun issueRead(offset: Long, length: Int): OutstandingSftpRead {
        val promise = issueAsyncRead(remoteFile, offset, length)
        return SshjOutstandingRead(
            offset = offset,
            length = length,
            promise = promise,
            remoteFile = remoteFile,
            timeoutMs = timeoutMs
        )
    }

    private class SshjOutstandingRead(
        override val offset: Long,
        override val length: Int,
        private val promise: Promise<Response, SFTPException>,
        private val remoteFile: RemoteFile,
        private val timeoutMs: Int
    ) : OutstandingSftpRead {
        override val isComplete: Boolean
            get() = promise.isDelivered || promise.inError()

        override fun await(): ByteArray? {
            val response = promise.retrieve(timeoutMs.toLong().coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            val buffer = ByteArray(length)
            val received = parseReadResponse(remoteFile, response, buffer)
            return when {
                received < 0 -> null
                received == 0 -> ByteArray(0)
                received == buffer.size -> buffer
                else -> buffer.copyOf(received)
            }
        }
    }

    companion object {
        private val asyncRead = RemoteFile::class.java.getDeclaredMethod(
            "asyncRead",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        private val checkReadResponse = RemoteFile::class.java.getDeclaredMethod(
            "checkReadResponse",
            Response::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        private fun issueAsyncRead(
            remoteFile: RemoteFile,
            offset: Long,
            length: Int
        ): Promise<Response, SFTPException> {
            return asyncRead.invoke(remoteFile, offset, length) as Promise<Response, SFTPException>
        }

        private fun parseReadResponse(
            remoteFile: RemoteFile,
            response: Response,
            dest: ByteArray
        ): Int {
            return checkReadResponse.invoke(remoteFile, response, dest, 0) as Int
        }
    }
}

object SftpPipelinedDownloader {
    private val pollNanos = TimeUnit.MICROSECONDS.toNanos(200)

    fun download(
        fileSize: Long,
        settings: SftpTransferSettings,
        source: SftpAsyncReadSource,
        sink: SftpOffsetSink,
        onBytesTransferred: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        waitTimeoutMs: Long = DEFAULT_READ_WAIT_TIMEOUT_MS,
        rangeStart: Long = 0L,
        rangeEnd: Long = fileSize
    ) {
        val resolved = settings.sanitized()
        val readSize = resolved.sftpReadSize
        val maxRequests = resolved.sftpMaxRequests
        val start = rangeStart.coerceAtLeast(0L)
        val end = rangeEnd.coerceAtMost(fileSize).coerceAtLeast(start)
        if (fileSize <= 0L || end <= start) {
            onBytesTransferred(0L)
            return
        }

        var nextOffset = start
        val inflight = ArrayList<OutstandingSftpRead>(maxRequests.coerceAtMost(64))
        var transferred = 0L

        fun throwIfCancelled() {
            if (isCancelled()) throw SftpDownloadCancelledException()
        }

        fun issueReadAt(offset: Long, length: Int) {
            throwIfCancelled()
            if (length <= 0) return
            check(inflight.size < maxRequests) {
                "SFTP READ window exceeded: inflight=${inflight.size} max=$maxRequests"
            }
            inflight += source.issueRead(offset, length)
        }

        fun issueNextWindowRead() {
            if (nextOffset >= end || inflight.size >= maxRequests) return
            val remaining = end - nextOffset
            val length = minOf(readSize.toLong(), remaining).toInt()
            issueReadAt(nextOffset, length)
            nextOffset += length.toLong()
        }

        while (nextOffset < end && inflight.size < maxRequests) {
            issueNextWindowRead()
        }

        while (inflight.isNotEmpty()) {
            throwIfCancelled()
            val completed = awaitAnyCompleted(inflight, isCancelled, waitTimeoutMs)
            val data = completed.await()
            // One completion frees one slot. A short DATA reply re-reads the
            // remainder of that request; otherwise the slot is used for the
            // next sequential offset. Never both, never above the window.
            if (data != null) {
                if (data.isNotEmpty()) {
                    sink.writeAt(completed.offset, data)
                    transferred += data.size.toLong()
                    onBytesTransferred(transferred)
                }
                val receivedEnd = completed.offset + data.size
                val requestedEnd = completed.offset + completed.length
                val shortAndNotEof = receivedEnd < requestedEnd && receivedEnd < end
                if (shortAndNotEof) {
                    val gap = minOf(
                        readSize.toLong(),
                        requestedEnd - receivedEnd,
                        end - receivedEnd
                    ).toInt()
                    if (inflight.size < maxRequests) {
                        issueReadAt(receivedEnd, gap)
                    }
                } else if (nextOffset < end && inflight.size < maxRequests) {
                    issueNextWindowRead()
                }
            } else if (nextOffset < end && inflight.size < maxRequests) {
                issueNextWindowRead()
            }
        }
        onBytesTransferred(transferred)
    }

    fun segmentCount(fileSize: Long, settings: SftpTransferSettings): Int {
        val resolved = settings.sanitized()
        val parallel = resolved.parallelDownloads
        if (parallel <= 1 || fileSize <= 0L) return 1
        val minSegmentBytes = resolved.sftpReadSize.toLong() * 2L
        if (fileSize < minSegmentBytes * 2L) return 1
        val bySize = (fileSize / minSegmentBytes).toInt().coerceAtLeast(1)
        return minOf(parallel, bySize)
    }

    fun splitRanges(fileSize: Long, settings: SftpTransferSettings): List<SftpByteRange> {
        val count = segmentCount(fileSize, settings)
        if (fileSize <= 0L || count <= 1) {
            return listOf(SftpByteRange(0L, fileSize.coerceAtLeast(0L)))
        }
        val base = fileSize / count
        return (0 until count).map { index ->
            val start = index * base
            val end = if (index == count - 1) fileSize else (index + 1) * base
            SftpByteRange(start, end)
        }
    }

    fun downloadRanges(
        fileSize: Long,
        settings: SftpTransferSettings,
        sink: SftpOffsetSink,
        sourceForRange: (SftpByteRange) -> SftpAsyncReadSource,
        onBytesTransferred: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        waitTimeoutMs: Long = DEFAULT_READ_WAIT_TIMEOUT_MS,
        onSegmentStarted: () -> Unit = {}
    ) {
        val ranges = splitRanges(fileSize, settings)
        if (ranges.size <= 1) {
            val range = ranges.first()
            val source = sourceForRange(range)
            try {
                onSegmentStarted()
                download(
                    fileSize = fileSize,
                    settings = settings,
                    source = source,
                    sink = sink,
                    onBytesTransferred = onBytesTransferred,
                    isCancelled = isCancelled,
                    waitTimeoutMs = waitTimeoutMs,
                    rangeStart = range.start,
                    rangeEnd = range.endExclusive
                )
            } finally {
                (source as? AutoCloseable)?.close()
            }
            return
        }
        val total = AtomicLong(0L)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = ranges.mapIndexed { index, range ->
            Thread({
                val source = sourceForRange(range)
                try {
                    onSegmentStarted()
                    var last = 0L
                    download(
                        fileSize = fileSize,
                        settings = settings,
                        source = source,
                        sink = sink,
                        onBytesTransferred = { absolute ->
                            val delta = absolute - last
                            last = absolute
                            if (delta > 0L) {
                                onBytesTransferred(total.addAndGet(delta))
                            }
                        },
                        isCancelled = isCancelled,
                        waitTimeoutMs = waitTimeoutMs,
                        rangeStart = range.start,
                        rangeEnd = range.endExclusive
                    )
                } catch (error: Throwable) {
                    errors.add(error)
                } finally {
                    runCatching { (source as? AutoCloseable)?.close() }
                }
            }, "sftp-range-$index")
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        errors.firstOrNull()?.let { throw it }
    }

    fun downloadFromSftp(
        sftp: SFTPClient,
        remotePath: String,
        localFile: File,
        settings: SftpTransferSettings,
        onBytesTransferred: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ) {
        localFile.parentFile?.mkdirs()
        val fileSize = runCatching { sftp.stat(remotePath).size }.getOrElse {
            sftp.open(remotePath).use { it.length() }
        }
        RandomAccessFile(localFile, "rw").use { raf ->
            if (fileSize >= 0L) {
                raf.setLength(fileSize)
            }
            val sink = FileChannelOffsetSink(raf.channel)
            val timeoutMs = sftp.sftpEngine.timeoutMs.toLong().coerceAtLeast(1L)
            downloadRanges(
                fileSize = fileSize.coerceAtLeast(0L),
                settings = settings,
                sink = sink,
                sourceForRange = {
                    SshjAsyncReadSource(
                        remoteFile = sftp.open(remotePath),
                        timeoutMs = sftp.sftpEngine.timeoutMs
                    )
                },
                onBytesTransferred = onBytesTransferred,
                isCancelled = isCancelled,
                waitTimeoutMs = timeoutMs
            )
        }
    }

    private fun awaitAnyCompleted(
        inflight: MutableList<OutstandingSftpRead>,
        isCancelled: () -> Boolean,
        timeoutMs: Long
    ): OutstandingSftpRead {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
        while (true) {
            if (isCancelled()) throw SftpDownloadCancelledException()
            val readyIndex = inflight.indexOfFirst { it.isComplete }
            if (readyIndex >= 0) {
                return inflight.removeAt(readyIndex)
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw IOException("Timed out waiting for pipelined SFTP READ")
            }
            LockSupport.parkNanos(pollNanos)
        }
    }

    const val DEFAULT_READ_WAIT_TIMEOUT_MS = 60_000L
}
