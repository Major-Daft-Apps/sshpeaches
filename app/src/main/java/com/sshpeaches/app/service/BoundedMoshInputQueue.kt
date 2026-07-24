package com.majordaftapps.sshpeaches.app.service

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * A byte-bounded, lossless FIFO for terminal input waiting on a Mosh process.
 *
 * Each submission is accepted whole or rejected without blocking the caller. Calls are
 * serialized across chunking so a paste cannot interleave with another key event. Input is
 * intentionally kept in memory instead of being spooled to disk because it can contain passwords
 * and other secrets.
 */
internal class BoundedMoshInputQueue(
    private val capacityBytes: Int,
    private val chunkBytes: Int
) {
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val pending = ArrayDeque<ByteArray>()
    private var pendingBytes = 0
    private var closed = false

    init {
        require(chunkBytes > 0) { "chunkBytes must be positive" }
        require(capacityBytes >= chunkBytes) { "capacityBytes must fit at least one chunk" }
    }

    fun tryEnqueue(payload: ByteArray, reservedCapacityBytes: Int = 0): Boolean {
        if (payload.isEmpty()) return true
        return lock.withLock {
            if (closed) return false
            val reserved = reservedCapacityBytes.coerceIn(0, capacityBytes)
            val usableCapacity = capacityBytes - reserved
            if (payload.size > usableCapacity - pendingBytes) return false
            var offset = 0
            while (offset < payload.size) {
                val count = min(chunkBytes, payload.size - offset)
                pending.addLast(payload.copyOfRange(offset, offset + count))
                pendingBytes += count
                offset += count
            }
            notEmpty.signal()
            true
        }
    }

    fun take(): ByteArray? = lock.withLock {
        while (!closed && pending.isEmpty()) {
            notEmpty.await()
        }
        if (pending.isEmpty()) return null
        pending.removeFirst().also { payload ->
            pendingBytes -= payload.size
        }
    }

    fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            pending.clear()
            pendingBytes = 0
            notEmpty.signalAll()
        }
    }

    internal fun queuedByteCount(): Int = lock.withLock { pendingBytes }
}
