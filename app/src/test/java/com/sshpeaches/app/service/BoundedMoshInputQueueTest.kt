package com.majordaftapps.sshpeaches.app.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedMoshInputQueueTest {

    @Test
    fun enqueue_chunksPayloadAndPreservesFifo() {
        val queue = BoundedMoshInputQueue(capacityBytes = 16, chunkBytes = 4)

        assertTrue(queue.tryEnqueue("abcdef".encodeToByteArray()))
        assertTrue(queue.tryEnqueue("gh".encodeToByteArray()))

        assertArrayEquals("abcd".encodeToByteArray(), queue.take())
        assertArrayEquals("ef".encodeToByteArray(), queue.take())
        assertArrayEquals("gh".encodeToByteArray(), queue.take())
        assertEquals(0, queue.queuedByteCount())
    }

    @Test
    fun tryEnqueue_rejectsWholePayloadWithoutBlockingOrPartialWrites() {
        val queue = BoundedMoshInputQueue(capacityBytes = 4, chunkBytes = 4)
        assertTrue(queue.tryEnqueue("abcd".encodeToByteArray()))
        assertFalse(queue.tryEnqueue("efgh".encodeToByteArray()))
        assertEquals(4, queue.queuedByteCount())
        assertArrayEquals("abcd".encodeToByteArray(), queue.take())
        assertTrue(queue.tryEnqueue("efgh".encodeToByteArray()))
        assertArrayEquals("efgh".encodeToByteArray(), queue.take())
    }

    @Test
    fun close_unblocksConsumerAndRejectsFurtherInput() {
        val emptyQueue = BoundedMoshInputQueue(capacityBytes = 4, chunkBytes = 4)
        val consumerFinished = CountDownLatch(1)
        var consumed: ByteArray? = byteArrayOf(1)
        Thread {
            consumed = emptyQueue.take()
            consumerFinished.countDown()
        }.start()

        emptyQueue.close()

        assertTrue(consumerFinished.await(1, TimeUnit.SECONDS))
        assertNull(consumed)
        assertFalse(emptyQueue.tryEnqueue("x".encodeToByteArray()))
        assertEquals(0, emptyQueue.queuedByteCount())
    }

    @Test
    fun reservedCapacity_remainsAvailableForInteractiveInput() {
        val queue = BoundedMoshInputQueue(capacityBytes = 8, chunkBytes = 2)

        assertTrue(queue.tryEnqueue("bulk".encodeToByteArray(), reservedCapacityBytes = 4))
        assertFalse(queue.tryEnqueue("x".encodeToByteArray(), reservedCapacityBytes = 4))
        assertTrue(queue.tryEnqueue("keys".encodeToByteArray()))

        assertEquals(8, queue.queuedByteCount())
    }

    @Test
    fun concurrentCallsDoNotInterleaveChunks() {
        val queue = BoundedMoshInputQueue(capacityBytes = 32, chunkBytes = 2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        listOf("AAAAAA", "BBBBBB").forEach { text ->
            Thread {
                ready.countDown()
                start.await()
                queue.tryEnqueue(text.encodeToByteArray())
                done.countDown()
            }.start()
        }
        assertTrue(ready.await(1, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(1, TimeUnit.SECONDS))

        val chunks = List(6) { String(queue.take()!!) }
        assertTrue(chunks == listOf("AA", "AA", "AA", "BB", "BB", "BB") ||
            chunks == listOf("BB", "BB", "BB", "AA", "AA", "AA"))
    }
}
