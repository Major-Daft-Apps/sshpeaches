package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Needs a person or script to restart the live test server while it runs: after logging
 * [READY_MARKER] it waits for the session to drop and come back on its own.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveReconnectTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    @Test
    fun sshSessionReconnectsInPlaceAfterTheServerDropsIt() {
        val sessionId = "reconnect-test"
        val host = HostConnection(
            id = "reconnect-host",
            name = "reconnect",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD
        )
        withSessionService { service ->
            service.startSession(
                requestedSessionId = sessionId,
                host = host,
                mode = ConnectionMode.SSH,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false
            )
            try {
                waitForStatus(service, sessionId, SessionStatus.ACTIVE, timeoutMillis = 60_000)
                val emulator = checkNotNull(service.resolveTerminalEmulator(sessionId))
                Log.i(TAG, READY_MARKER)

                waitForStatus(service, sessionId, SessionStatus.CONNECTING, timeoutMillis = 120_000)
                waitForStatus(service, sessionId, SessionStatus.ACTIVE, timeoutMillis = 150_000)

                // The same terminal (and so the same screen contents) carries over.
                assertSame(emulator, service.resolveTerminalEmulator(sessionId))
            } finally {
                service.stopSession(sessionId)
            }
        }
    }

    private fun waitForStatus(
        service: SessionService,
        sessionId: String,
        status: SessionStatus,
        timeoutMillis: Long
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val snapshot = service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }
            checkNotNull(snapshot) { "Session $sessionId disappeared instead of reconnecting" }
            check(snapshot.status != SessionStatus.ERROR) { "Session failed: ${snapshot.statusMessage}" }
            if (snapshot.status == status) return
            Thread.sleep(200)
        }
        error("Session $sessionId did not reach $status within ${timeoutMillis}ms")
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService()
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(10, TimeUnit.SECONDS)) { "Timed out binding SessionService" }
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        const val TAG = "LiveReconnectTest"
        const val READY_MARKER = "RECONNECT_TEST_READY"
    }
}
