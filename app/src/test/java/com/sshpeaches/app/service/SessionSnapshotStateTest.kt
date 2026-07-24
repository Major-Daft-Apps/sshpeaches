package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSnapshotStateTest {

    @Test
    fun concurrentUpsertsRetainEverySession() {
        val sessionCount = 64
        val state = MutableStateFlow<List<SessionService.SessionSnapshot>>(emptyList())
        val ready = CountDownLatch(sessionCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(sessionCount)
        val failures = ConcurrentLinkedQueue<Throwable>()

        repeat(sessionCount) { index ->
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    state.upsertSessionSnapshot(snapshot(index))
                } catch (error: Throwable) {
                    failures += error
                } finally {
                    done.countDown()
                }
            }.start()
        }

        assertTrue("Workers did not become ready", ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue("Workers did not finish", done.await(5, TimeUnit.SECONDS))
        assertTrue("Concurrent update failed: $failures", failures.isEmpty())
        assertEquals(sessionCount, state.value.size)
        assertEquals(sessionCount, state.value.map { it.hostId }.toSet().size)
    }

    @Test
    fun removeOnlyDropsTheRequestedSession() {
        val state = MutableStateFlow(listOf(snapshot(1), snapshot(2), snapshot(3)))

        state.removeSessionSnapshot("session-2")

        assertEquals(listOf("session-1", "session-3"), state.value.map { it.hostId })
    }

    private fun snapshot(index: Int): SessionService.SessionSnapshot =
        SessionService.SessionSnapshot(
            hostId = "session-$index",
            host = HostConnection(
                id = "host-$index",
                name = "Host $index",
                host = "192.0.2.${index + 1}",
                username = "tester",
                preferredAuth = AuthMethod.PASSWORD
            ),
            mode = ConnectionMode.SSH,
            status = SessionService.SessionStatus.CONNECTING,
            statusMessage = "Connecting"
        )
}
