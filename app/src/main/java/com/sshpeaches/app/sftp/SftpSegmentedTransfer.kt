package com.majordaftapps.sshpeaches.app.sftp

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.EnumSet
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import net.schmizz.concurrent.Promise
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteResource
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPEngine
import net.schmizz.sshj.sftp.SFTPException

/**
 * One SFTP connection lent to a transfer. Transfers run on connections of their own, so the file browser's
 * connection never waits behind file data.
 */
interface SftpTransferLease {
    val sftp: SFTPClient

    /** Whether this is the session's own connection rather than one opened for transfers. */
    val shared: Boolean get() = false

    /** Hands the connection back for the next transfer. */
    fun release()

    /** Drops a connection that failed or was abandoned mid-request; a fresh one is opened when needed. */
    fun discard()
}

fun interface SftpLeaseSource {
    /** Blocks until a connection is free. Returns null once [stop] says the caller no longer needs one. */
    fun acquire(stop: () -> Boolean): SftpTransferLease?
}

/** Lends the same client to every caller, one at a time: tests and single-connection callers. */
class SingleClientLeaseSource(private val client: SFTPClient) : SftpLeaseSource {
    private val lock = Object()
    private var lent = false

    override fun acquire(stop: () -> Boolean): SftpTransferLease? {
        synchronized(lock) {
            while (lent) {
                if (stop()) return null
                lock.wait(50)
            }
            if (stop()) return null
            lent = true
        }
        return object : SftpTransferLease {
            override val sftp: SFTPClient = client
            override fun release() = giveBack()
            override fun discard() = giveBack()
        }
    }

    private fun giveBack() = synchronized(lock) {
        lent = false
        lock.notifyAll()
    }
}

/**
 * The chunks of one file. Workers take the next chunk when they finish one, so a faster connection simply moves
 * more of the file, and a chunk whose connection failed goes back for another worker to finish.
 */
class SftpChunkQueue(chunks: List<SftpPartialDownload.RangeState>) {
    private val starts = LongArray(chunks.size) { chunks[it].start }
    private val ends = LongArray(chunks.size) { chunks[it].end }
    private val done = LongArray(chunks.size) { chunks[it].done.coerceIn(chunks[it].start, chunks[it].end) }
    private val pending = ArrayDeque<Int>().apply { chunks.indices.filter { done[it] < ends[it] }.forEach(::addLast) }
    private var completedBytes = done.indices.sumOf { done[it] - starts[it] }

    val size: Int get() = starts.size

    @Synchronized
    fun take(): Int? = pending.removeFirstOrNull()

    @Synchronized
    fun doneOf(index: Int): Long = done[index]

    fun endOf(index: Int): Long = ends[index]

    /** Records that [index] has every byte below [contiguousEnd]. */
    @Synchronized
    fun advance(index: Int, contiguousEnd: Long) {
        val clamped = contiguousEnd.coerceAtMost(ends[index])
        if (clamped > done[index]) {
            completedBytes += clamped - done[index]
            done[index] = clamped
        }
    }

    /** Returns a chunk its worker could not finish, so another worker picks up from where it stopped. */
    @Synchronized
    fun giveBack(index: Int) {
        if (done[index] < ends[index] && index !in pending) pending.addFirst(index)
    }

    @Synchronized
    fun isDrained(): Boolean = pending.isEmpty()

    @Synchronized
    fun isComplete(): Boolean = done.indices.all { done[it] >= ends[it] }

    @Synchronized
    fun bytesDone(): Long = completedBytes

    @Synchronized
    fun snapshot(): List<SftpPartialDownload.RangeState> =
        starts.indices.map { SftpPartialDownload.RangeState(starts[it], ends[it], done[it]) }

    companion object {
        const val MIN_CHUNK_BYTES = 1L shl 20
        const val MAX_CHUNK_BYTES = 16L shl 20

        /**
         * Chunks for a file of [size] bytes: about four per worker, so the last few chunks finish close together,
         * between 1 MiB and 16 MiB each.
         */
        fun plan(size: Long, workers: Int): List<SftpPartialDownload.RangeState> {
            if (size <= 0L) return emptyList()
            val target = size / (workers.coerceAtLeast(1) * 4L)
            val chunk = target.coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES)
            val count = ((size + chunk - 1) / chunk).toInt()
            return List(count) { index ->
                val start = index * chunk
                SftpPartialDownload.RangeState(start, minOf(size, start + chunk), start)
            }
        }
    }
}

/** Requests against one open remote file, issued without waiting so many are in flight at once. */
interface SftpSegmentIo : AutoCloseable {
    fun issue(offset: Long, length: Int): Pending

    interface Pending {
        val offset: Long
        val length: Int

        /** Waits for the reply and returns how many bytes it moved, or -1 at the end of the file. */
        fun await(): Int
    }
}

class SftpTransferCancelledException : IOException("SFTP transfer cancelled")

/** The server's replies don't add up to the file it described; another connection would get the same. */
class SftpTransferIntegrityException(message: String) : IOException(message)

/**
 * Moves the chunks of [queue] with up to [workers] connections from [leases], each keeping several requests in
 * flight. A connection that fails is dropped and its chunk is finished on another one; the transfer fails only after
 * [maxAttempts] failures in a row with nothing moved in between, or at once on an error the server reports (no such
 * file, permission denied).
 */
class SftpSegmentedTransfer(
    private val queue: SftpChunkQueue,
    private val workers: Int,
    private val leases: SftpLeaseSource,
    private val requestSize: Int,
    private val inFlightBytes: (SftpTransferLease) -> Long,
    private val openIo: (sftp: SFTPClient, first: Boolean) -> SftpSegmentIo,
    private val isCancelled: () -> Boolean = { false },
    private val onProgress: (bytesDone: Long) -> Unit = {},
    private val onCheckpoint: (List<SftpPartialDownload.RangeState>) -> Unit = {},
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelayMs: (attempt: Int) -> Long = { attempt -> 1_000L shl (attempt - 1).coerceIn(0, 3) },
    private val progressIntervalMs: Long = 100L,
    private val checkpointIntervalMs: Long = 500L,
    private val stallTimeoutMs: Long = STALL_TIMEOUT_MS
) {
    private val failure = AtomicReference<Throwable?>(null)
    private val cancelled = AtomicBoolean(false)
    private val firstOpened = AtomicBoolean(false)
    private val firstOpenLock = Any()
    private val activeLeases = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<SftpTransferLease, Boolean>())
    private val failureLock = Any()
    private var consecutiveFailures = 0
    private var bytesAtLastFailure = -1L

    private fun stopped(): Boolean = cancelled.get() || failure.get() != null || isCancelled().also { if (it) cancelled.set(true) }

    private val progressLock = Any()
    private var reportedBytes = -1L

    /**
     * Reports progress when it has changed, one caller at a time, so the first report already shows bytes moved by
     * this attempt. A callback that throws (the user cancelled) stops the transfer with that error.
     */
    private fun reportProgress() {
        synchronized(progressLock) {
            val now = queue.bytesDone()
            if (now == reportedBytes) return
            reportedBytes = now
            try {
                onProgress(now)
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }
    }

    fun run() {
        val workerCount = workers.coerceAtLeast(1).coerceAtMost(maxOf(1, queue.size))
        var lastCheckpoint = 0L
        var lastMoved = queue.bytesDone()
        var lastMovedAt = System.currentTimeMillis()
        reportedBytes = queue.bytesDone()
        var rounds = 0
        try {
            while (true) {
                rounds += 1
                val threads = List(workerCount) { index -> Thread({ workerLoop() }, "sftp-transfer-$index") }
                threads.forEach { it.start() }
                try {
                    while (threads.any { it.isAlive }) {
                        threads.firstOrNull { it.isAlive }?.join(progressIntervalMs)
                        if (isCancelled()) cancelled.set(true)
                        if (cancelled.get()) {
                            awaitOrAbort(threads)
                            break
                        }
                        reportProgress()
                        val now = System.currentTimeMillis()
                        val moved = queue.bytesDone()
                        if (moved != lastMoved || activeLeases.isEmpty()) {
                            lastMoved = moved
                            lastMovedAt = now
                        } else if (now - lastMovedAt >= stallTimeoutMs) {
                            // Nothing moved for a long while: a request is stuck on a connection that died without
                            // saying so. Dropping the connections fails those requests, and the workers retry.
                            lastMovedAt = now
                            activeLeases.toList().forEach { runCatching { it.discard() } }
                        }
                        if (now - lastCheckpoint >= checkpointIntervalMs) {
                            lastCheckpoint = now
                            onCheckpoint(queue.snapshot())
                        }
                    }
                } catch (error: Throwable) {
                    // Interrupted, or a progress callback said the transfer was cancelled.
                    cancelled.set(true)
                    awaitOrAbort(threads)
                    throw if (error is InterruptedException) SftpTransferCancelledException() else error
                }
                if (cancelled.get()) throw SftpTransferCancelledException()
                failure.get()?.let { throw it }
                if (queue.isComplete() && firstOpened.get()) break
                // Every worker stopped with chunks left: the last of them failed at about the same time.
                if (rounds >= maxAttempts) throw IOException("The transfer stopped before the whole file was moved")
            }
        } finally {
            runCatching { reportProgress() }
            runCatching { onCheckpoint(queue.snapshot()) }
        }
    }

    /** Gives cancelled workers a moment to finish their in-flight requests, then cuts their connections. */
    private fun awaitOrAbort(threads: List<Thread>) {
        val deadline = System.currentTimeMillis() + CANCEL_DRAIN_MS
        threads.forEach { thread ->
            val left = deadline - System.currentTimeMillis()
            if (left > 0) runCatching { thread.join(left) }
        }
        if (threads.any { it.isAlive }) {
            activeLeases.toList().forEach { runCatching { it.discard() } }
            threads.forEach { runCatching { it.join(CANCEL_DRAIN_MS) } }
        }
    }

    private fun workerLoop() {
        while (!stopped()) {
            if (firstOpened.get() && queue.isDrained()) return
            val lease = try {
                leases.acquire { stopped() || (firstOpened.get() && queue.isDrained()) } ?: return
            } catch (error: Throwable) {
                if (!recordFailure(error)) return
                continue
            }
            activeLeases += lease
            var healthy = false
            try {
                openWorkerIo(lease.sftp).use { io ->
                    transferChunks(io, lease)
                }
                healthy = true
            } catch (error: Throwable) {
                recordFailure(error)
            } finally {
                activeLeases -= lease
                if (healthy) lease.release() else lease.discard()
            }
        }
    }

    private fun openWorkerIo(sftp: SFTPClient): SftpSegmentIo {
        if (firstOpened.get()) return openIo(sftp, false)
        synchronized(firstOpenLock) {
            if (firstOpened.get()) return openIo(sftp, false)
            val io = openIo(sftp, true)
            firstOpened.set(true)
            return io
        }
    }

    /** Returns whether the worker should try again. */
    private fun recordFailure(error: Throwable): Boolean {
        if (stopped()) return false
        if (error.isPermanentSftpFailure()) {
            failure.compareAndSet(null, error)
            return false
        }
        val attempt = synchronized(failureLock) {
            val moved = queue.bytesDone()
            if (moved != bytesAtLastFailure) {
                bytesAtLastFailure = moved
                consecutiveFailures = 0
            }
            consecutiveFailures += 1
            consecutiveFailures
        }
        if (attempt >= maxAttempts) {
            failure.compareAndSet(null, error)
            return false
        }
        val wakeAt = System.currentTimeMillis() + retryDelayMs(attempt)
        while (System.currentTimeMillis() < wakeAt) {
            if (stopped()) return false
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                cancelled.set(true)
                return false
            }
        }
        return true
    }

    private class Cursor(val index: Int, var next: Long, val end: Long) {
        val spans = TreeMap<Long, Long>()
        var contiguous = next
    }

    private fun transferChunks(io: SftpSegmentIo, lease: SftpTransferLease) {
        val maxRequests = (inFlightBytes(lease) / requestSize.coerceAtLeast(1)).toInt().coerceIn(1, MAX_REQUESTS)
        val inflight = ArrayDeque<Pair<SftpSegmentIo.Pending, Cursor>>()
        val owned = LinkedHashMap<Int, Cursor>()
        var cursor: Cursor? = null
        try {
            while (true) {
                if (stopped()) {
                    // Let the requests already sent finish so the connection can be reused, then stop.
                    while (inflight.isNotEmpty()) inflight.removeFirst().first.await()
                    return
                }
                while (inflight.size < maxRequests) {
                    var current = cursor
                    if (current == null || current.next >= current.end) {
                        val index = queue.take() ?: break
                        current = Cursor(index, queue.doneOf(index), queue.endOf(index))
                        owned[index] = current
                        cursor = current
                        if (current.next >= current.end) {
                            owned.remove(index)
                            continue
                        }
                    }
                    val length = minOf(requestSize.toLong(), current.end - current.next).toInt()
                    inflight.addLast(io.issue(current.next, length) to current)
                    current.next += length
                }
                if (inflight.isEmpty()) return
                val (request, chunk) = inflight.removeFirst()
                val moved = request.await()
                if (moved < 0) {
                    throw SftpTransferIntegrityException("The remote file ended at ${request.offset} bytes, before its expected size")
                }
                if (moved == 0) {
                    // A reply must move bytes; asking again for the same offset after empty replies looped forever.
                    throw SftpTransferIntegrityException("The SFTP server sent an empty reply at offset ${request.offset}")
                }
                chunk.spans[request.offset] = request.offset + moved
                while (true) {
                    chunk.contiguous = chunk.spans.remove(chunk.contiguous) ?: break
                }
                queue.advance(chunk.index, chunk.contiguous)
                reportProgress()
                if (moved < request.length) {
                    // A short read: ask for the rest of that request.
                    inflight.addLast(io.issue(request.offset + moved, request.length - moved) to chunk)
                }
                if (chunk.contiguous >= chunk.end) owned.remove(chunk.index)
            }
        } finally {
            owned.keys.forEach(queue::giveBack)
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val MAX_REQUESTS = 256
        const val STALL_TIMEOUT_MS = 30_000L
        private const val CANCEL_DRAIN_MS = 3_000L
    }
}

/** An error the server reported about the file itself (it is missing, or not ours to read); retrying won't help. */
fun Throwable.isPermanentSftpFailure(): Boolean {
    if (this is SftpTransferIntegrityException) return true
    val status = generateSequence(this) { it.cause }.take(6).filterIsInstance<SFTPException>().firstOrNull()?.statusCode
    return when (status) {
        null, Response.StatusCode.UNKNOWN, Response.StatusCode.CONNECITON_LOST, Response.StatusCode.NO_CONNECTION -> false
        else -> true
    }
}

/** Reads [remotePath] into [sink]. */
class SftpSegmentReader(
    private val remoteFile: RemoteFile,
    private val sink: SftpOffsetSink,
    private val timeoutMs: Long
) : SftpSegmentIo {
    override fun issue(offset: Long, length: Int): SftpSegmentIo.Pending {
        val promise = SshjRemoteFileAccess.asyncRead(remoteFile, offset, length)
        return object : SftpSegmentIo.Pending {
            override val offset = offset
            override val length = length
            override fun await(): Int {
                val response = promise.retrieve(timeoutMs, TimeUnit.MILLISECONDS)
                val buffer = ByteArray(length)
                val received = SshjRemoteFileAccess.checkReadResponse(remoteFile, response, buffer)
                if (received > 0) sink.writeAt(offset, buffer, received)
                return received
            }
        }
    }

    override fun close() {
        SshjRemoteFileAccess.closeWithoutWaiting(remoteFile)
    }

    companion object {
        fun open(sftp: SFTPClient, remotePath: String, sink: SftpOffsetSink): SftpSegmentReader =
            SftpSegmentReader(sftp.open(remotePath), sink, sftp.sftpEngine.timeoutMs.toLong().coerceAtLeast(1L))
    }
}

/** Writes [source] to a remote file. */
class SftpSegmentWriter(
    private val remoteFile: RemoteFile,
    private val source: FileChannel,
    private val timeoutMs: Long
) : SftpSegmentIo {
    override fun issue(offset: Long, length: Int): SftpSegmentIo.Pending {
        val data = ByteArray(length)
        val buffer = ByteBuffer.wrap(data)
        var position = offset
        while (buffer.hasRemaining()) {
            val read = source.read(buffer, position)
            if (read < 0) throw SftpTransferIntegrityException("The local file ended at $position bytes, before its expected size")
            position += read
        }
        val promise = SshjRemoteFileAccess.asyncWrite(remoteFile, offset, data, length)
        return object : SftpSegmentIo.Pending {
            override val offset = offset
            override val length = length
            override fun await(): Int {
                promise.retrieve(timeoutMs, TimeUnit.MILLISECONDS).ensureStatusPacketIsOK()
                return length
            }
        }
    }

    override fun close() {
        remoteFile.close()
    }

    companion object {
        /** Opens [remotePath] for writing; [create] truncates or creates it, which only the first worker does. */
        fun open(
            sftp: SFTPClient,
            remotePath: String,
            source: FileChannel,
            create: Boolean,
            permissions: Int
        ): SftpSegmentWriter {
            val modes = if (create) {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            } else {
                EnumSet.of(OpenMode.WRITE)
            }
            val attributes = FileAttributes.Builder().withPermissions(permissions).build()
            val file = sftp.sftpEngine.open(remotePath, modes, if (create) attributes else FileAttributes.EMPTY)
            return SftpSegmentWriter(file, source, sftp.sftpEngine.timeoutMs.toLong().coerceAtLeast(1L))
        }
    }
}

/** sshj keeps its asynchronous read and write calls protected; these reach them. */
internal object SshjRemoteFileAccess {
    private val asyncRead = RemoteFile::class.java.getDeclaredMethod(
        "asyncRead", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType
    ).apply { isAccessible = true }

    private val checkReadResponse = RemoteFile::class.java.getDeclaredMethod(
        "checkReadResponse", Response::class.java, ByteArray::class.java, Int::class.javaPrimitiveType
    ).apply { isAccessible = true }

    private val asyncWrite = RemoteFile::class.java.getDeclaredMethod(
        "asyncWrite", Long::class.javaPrimitiveType, ByteArray::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
    ).apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    fun asyncRead(file: RemoteFile, offset: Long, length: Int): Promise<Response, SFTPException> =
        unwrap { asyncRead.invoke(file, offset, length) } as Promise<Response, SFTPException>

    fun checkReadResponse(file: RemoteFile, response: Response, dest: ByteArray): Int =
        unwrap { checkReadResponse.invoke(file, response, dest, 0) } as Int

    @Suppress("UNCHECKED_CAST")
    fun asyncWrite(file: RemoteFile, offset: Long, data: ByteArray, length: Int): Promise<Response, SFTPException> =
        unwrap { asyncWrite.invoke(file, offset, data, 0, length) } as Promise<Response, SFTPException>

    /** Sends CLOSE without waiting for the reply: nothing depends on it, and it would cost a round trip. */
    fun closeWithoutWaiting(file: RemoteFile) {
        runCatching {
            val engine = requesterField.get(file) as SFTPEngine
            val handle = handleField.get(file) as ByteArray
            engine.request(engine.newRequest(PacketType.CLOSE).putString(handle))
        }.onFailure { runCatching { file.close() } }
    }

    private val requesterField = RemoteResource::class.java.getDeclaredField("requester").apply { isAccessible = true }
    private val handleField = RemoteResource::class.java.getDeclaredField("handle").apply { isAccessible = true }

    private inline fun unwrap(call: () -> Any?): Any? = try {
        call()
    } catch (error: java.lang.reflect.InvocationTargetException) {
        throw error.targetException ?: error
    }
}
