package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionLogBus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateResetRule
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The file browser's editor, chmod, and download against the live test server's sandbox. */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveFileToolsTest {

    @get:Rule
    val appStateResetRule = AppStateResetRule()

    @Test
    fun editChmodAndDownloadThroughTheService() {
        val sessionId = "file-tools-test"
        val host = HostConnection(
            id = "file-tools-host",
            name = "file tools",
            host = LiveBackendConfig.host,
            port = LiveBackendConfig.port,
            username = LiveBackendConfig.username,
            preferredAuth = AuthMethod.PASSWORD
        )
        val logs = CopyOnWriteArrayList<String>()
        val logScope = CoroutineScope(Dispatchers.Default)
        logScope.launch { SessionLogBus.entries.collect { if (it.hostId == sessionId) logs += it.message } }
        withSessionService { service ->
            service.startSession(
                requestedSessionId = sessionId,
                host = host,
                mode = ConnectionMode.SCP,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false
            )
            try {
                waitUntil(60_000, "session active") {
                    service.sessionsFlow().value.firstOrNull { it.hostId == sessionId }?.status == SessionStatus.ACTIVE
                }

                val path = "/notes.md"
                val original = runBlocking { service.readRemoteTextFile(sessionId, path) }.getOrThrow()
                val edited = original + "\nedited from the release build\n"
                runBlocking { service.writeRemoteTextFile(sessionId, path, edited) }.getOrThrow()
                assertEquals(edited, runBlocking { service.readRemoteTextFile(sessionId, path) }.getOrThrow())

                service.manageRemotePath(sessionId, "chmod", path, "600")
                waitUntil(30_000, "chmod completed: $logs") { logs.any { it.startsWith("Remote chmod completed: $path") } }

                val local = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "demo-download.mp4")
                local.delete()
                service.sftpDownloadFile(sessionId, "/demo.mp4", local.absolutePath)
                waitUntil(120_000, "download finished") {
                    service.fileTransferProgressFlow().value[sessionId]?.status?.let { it != FileTransferStatus.ACTIVE } == true
                }
                val transfer = service.fileTransferProgressFlow().value.getValue(sessionId)
                assertEquals(transfer.errorMessage, FileTransferStatus.SUCCEEDED, transfer.status)
                assertTrue("downloaded ${local.length()} bytes", local.length() > 1_000_000)
                assertEquals(transfer.totalBytes, local.length())
            } finally {
                service.stopSession(sessionId)
                logScope.cancel()
            }
        }
    }

    private fun waitUntil(timeoutMillis: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        error("Timed out waiting for $what")
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
}
