package com.majordaftapps.sshpeaches.app.release

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
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.testutil.LiveBackendConfig
import com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest
import com.majordaftapps.sshpeaches.app.testutil.TransferSettingsOverride
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the minified release build, a file uploads and downloads through the file browser's transfer path against the
 * live test server. The chunked transfers reach sshj members by name, and R8 renamed some of them once: every
 * transfer then failed in release builds only.
 */
@RunWith(AndroidJUnit4::class)
@ReleaseLaneTest
class ReleaseFileTransferTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fileUploadsAndDownloadsBackIntact() {
        TransferSettingsOverride.apply(context)
        val content = Random(42).nextBytes(3 * 1024 * 1024 + 12_345)
        val local = File(context.cacheDir, "release-transfer-up.bin").apply { writeBytes(content) }
        val back = File(context.cacheDir, "release-transfer-down.bin").apply { delete() }
        val remote = "/uploads/release-${UUID.randomUUID()}.bin"
        val id = "release-transfer-${UUID.randomUUID()}"
        withSessionService { service ->
            service.startSession(
                requestedSessionId = id,
                host = HostConnection(
                    id = id, name = id, host = LiveBackendConfig.host, port = LiveBackendConfig.port,
                    username = LiveBackendConfig.username, preferredAuth = AuthMethod.PASSWORD
                ),
                mode = ConnectionMode.SCP,
                passwordOverride = LiveBackendConfig.password,
                autoTrustUnknownHostKey = true,
                hostKeyPromptEnabled = false
            )
            try {
                waitFor("the first listing") { service.remoteDirectoryFlow().value[id] != null }
                service.scpUploadFile(id, local.absolutePath, remote)
                waitForSuccess(service, id)
                service.scpDownloadFile(id, remote, back.absolutePath)
                waitForSuccess(service, id)
            } finally {
                service.stopSession(id)
            }
        }
        assertArrayEquals(content, back.readBytes())
        local.delete()
        back.delete()
    }

    private fun waitForSuccess(service: SessionService, id: String) {
        Thread.sleep(200)
        waitFor("the transfer") {
            val progress = service.fileTransferProgressFlow().value[id]
            check(progress?.status != FileTransferStatus.FAILED) { "transfer failed: ${progress?.errorMessage}" }
            progress?.status == FileTransferStatus.SUCCEEDED
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 120_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(50)
        }
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
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
            check(connected.await(10, TimeUnit.SECONDS))
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }
}
