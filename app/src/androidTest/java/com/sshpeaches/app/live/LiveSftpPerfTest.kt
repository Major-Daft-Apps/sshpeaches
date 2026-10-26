package com.majordaftapps.sshpeaches.app.live

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SFTP timings against a real OpenSSH server through tools/network-impairment/netproxy.py, at a few round-trip times:
 * connecting until the browser shows a listing, listing a big directory, download and upload throughput, and listing
 * while a download runs. Needs -e netHost/netPort/netUser/netPassword plus -e perfDir (a server directory holding
 * many/ with 500 files, sub1/..sub3/, big.bin (128 MiB) and mid.bin (6 MiB)); skipped otherwise. Results are logged with tag NetScenario.
 */
@RunWith(AndroidJUnit4::class)
@LiveTransportTest
class LiveSftpPerfTest {
    private val args = InstrumentationRegistry.getArguments()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val results = mutableListOf<String>()
    private var gen = 1_000

    private val dir get() = args.getString("perfDir") ?: "/home/mosher/perf"

    private fun latency(oneWayMs: Int) {
        gen++
        Log.i("NetCtl", JSONObject().put("mode", "pass").put("new_conn", "pass").put("delay_ms", oneWayMs).put("gen", gen).toString())
        Thread.sleep(1_500)
    }

    private fun note(text: String) {
        results += text
        Log.i("NetScenario", text)
    }

    private fun host(id: String) = HostConnection(
        id = id, name = id, host = args.getString("netHost")!!, port = args.getString("netPort")!!.toInt(),
        username = args.getString("netUser")!!, preferredAuth = AuthMethod.PASSWORD
    )

    private fun secondsSince(start: Long) = (System.nanoTime() - start) / 1e9

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return false
    }

    /** Opens an SFTP session and returns the seconds until it was active and until its first listing arrived. */
    private fun openSftp(service: SessionService, id: String, mode: ConnectionMode = ConnectionMode.SFTP): Pair<Double, Double> {
        val start = System.nanoTime()
        service.startSession(
            requestedSessionId = id, host = host(id), mode = mode,
            passwordOverride = args.getString("netPassword"), autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
        )
        var active = -1.0
        val listed = waitFor(120_000) {
            val s = service.sessionsFlow().value.firstOrNull { it.hostId == id }
            check(s?.status != SessionStatus.ERROR) { "SFTP session failed: ${s?.statusMessage}" }
            if (active < 0 && s?.status == SessionStatus.ACTIVE) active = secondsSince(start)
            service.remoteDirectoryFlow().value[id] != null
        }
        check(listed) { "no listing within 120 s" }
        return active to secondsSince(start)
    }

    /** Lists [path] and returns the seconds until its snapshot arrived. */
    private fun timeListing(service: SessionService, id: String, path: String, minEntries: Int = 0): Double {
        val before = service.remoteDirectoryFlow().value[id]
        val start = System.nanoTime()
        service.listSftpDirectory(id, path)
        check(waitFor(120_000) {
            val now = service.remoteDirectoryFlow().value[id]
            now != null && now !== before && now.path.trimEnd('/').endsWith(path.substringAfterLast('/')) &&
                now.entries.size >= minEntries
        }) { "listing of $path never arrived" }
        return secondsSince(start)
    }

    private fun waitTransfer(service: SessionService, id: String, timeoutMs: Long): Double {
        val start = System.nanoTime()
        check(waitFor(timeoutMs) {
            val p = service.fileTransferProgressFlow().value[id]
            check(p?.status != FileTransferStatus.FAILED) { "transfer failed: ${p?.errorMessage}" }
            p?.status == FileTransferStatus.SUCCEEDED
        }) { "transfer did not finish within ${timeoutMs / 1000}s" }
        return secondsSince(start)
    }

    @Test
    fun sftpTimings() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val context = instrumentation.targetContext
        val bigLocal = File(context.cacheDir, "perf-big.bin")
        val uploadLocal = File(context.cacheDir, "perf-upload.bin").apply {
            outputStream().use { out -> val chunk = ByteArray(1 shl 20) { it.toByte() }; repeat(32) { out.write(chunk) } }
        }
        withSessionService { service ->
            for (oneWay in listOf(0, 75)) {
                latency(oneWay)
                val id = "sftp-perf-$oneWay"
                try {
                    val (active, firstListing) = openSftp(service, id)
                    note("rtt ${oneWay * 2} ms: connect -> active %.2f s, -> first listing %.2f s".format(active, firstListing))
                    note("rtt ${oneWay * 2} ms: list many/ (500 files) %.2f s".format(timeListing(service, id, "$dir/many", 500)))
                    note("rtt ${oneWay * 2} ms: list sub1/ %.2f s".format(timeListing(service, id, "$dir/sub1")))

                    bigLocal.delete()
                    val downloadStart = System.nanoTime()
                    service.sftpDownloadFile(id, "$dir/big.bin", bigLocal.absolutePath)
                    check(waitFor(60_000) { service.fileTransferProgressFlow().value[id]?.bytesTransferred ?: 0 > 0 })
                    val during = timeListing(service, id, "$dir/sub2")
                    note("rtt ${oneWay * 2} ms: list sub2/ while downloading %.2f s".format(during))
                    waitTransfer(service, id, 600_000)
                    val downloadSeconds = secondsSince(downloadStart)
                    note("rtt ${oneWay * 2} ms: download big.bin %.1f MB in %.1f s = %.1f MB/s".format(
                        bigLocal.length() / 1e6, downloadSeconds, bigLocal.length() / 1e6 / downloadSeconds))

                    val uploadStart = System.nanoTime()
                    service.sftpUploadFile(id, uploadLocal.absolutePath, "$dir/up-$oneWay.bin")
                    Thread.sleep(200)
                    waitTransfer(service, id, 600_000)
                    val uploadSeconds = secondsSince(uploadStart)
                    note("rtt ${oneWay * 2} ms: upload 33.6 MB in %.1f s = %.1f MB/s".format(uploadSeconds, uploadLocal.length() / 1e6 / uploadSeconds))
                } finally {
                    service.stopSession(id)
                    Thread.sleep(1_000)
                }
            }
            latency(0)
        }
        bigLocal.delete(); uploadLocal.delete()
        Log.i("NetScenario", "== sftp-perf\n" + results.joinToString("\n"))
    }

    private fun sha256(input: InputStream): String = input.use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * The file browser's path: downloads into and uploads from content:// documents, as the system file picker gives
     * them, written in place at any offset. Checks every byte by round-tripping mid.bin (6 MiB; big enough for several
     * chunks, small enough not to strain the emulator's network) through the server.
     */
    @Test
    fun browserTransfersThroughContentUris() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val created = mutableListOf<Uri>()
        fun newDownload(name: String): Uri = checkNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name) }
            )
        ).also { created += it }
        val roundTrip = File(context.cacheDir, "perf-roundtrip.bin")
        withSessionService { service ->
            latency(0)
            val id = "browser-perf"
            try {
                openSftp(service, id, ConnectionMode.SCP)
                val downloaded = newDownload("perf-mid-${System.nanoTime()}.bin")
                service.scpDownloadFile(id, "$dir/mid.bin", downloaded.toString())
                check(waitFor(60_000) { service.fileTransferProgressFlow().value[id]?.bytesTransferred ?: 0 > 0 })
                note("browser: list sub3/ while downloading %.2f s".format(timeListing(service, id, "$dir/sub3")))
                waitTransfer(service, id, 600_000)
                service.scpUploadFile(id, downloaded.toString(), "$dir/up-uri.bin")
                Thread.sleep(200)
                waitTransfer(service, id, 600_000)
                roundTrip.delete()
                service.scpDownloadFile(id, "$dir/up-uri.bin", roundTrip.absolutePath)
                Thread.sleep(200)
                waitTransfer(service, id, 600_000)
                val viaUri = sha256(checkNotNull(resolver.openInputStream(downloaded)))
                val viaFile = sha256(roundTrip.inputStream())
                note("browser: content:// download, upload and file download agree: ${viaUri == viaFile}")
                check(viaUri == viaFile) { "round trip through content:// changed the file" }
                check(roundTrip.length() == 6L shl 20) { "round trip has ${roundTrip.length()} bytes" }
            } finally {
                service.stopSession(id)
                created.forEach { runCatching { resolver.delete(it, null, null) } }
                roundTrip.delete()
            }
        }
    }

    private fun withSessionService(block: (SessionService) -> Unit) {
        val connected = CountDownLatch(1)
        var service: SessionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as SessionService.SessionBinder).getService(); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        val context = instrumentation.targetContext
        check(context.bindService(Intent(context, SessionService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(10, TimeUnit.SECONDS))
            block(checkNotNull(service))
        } finally {
            context.unbindService(connection)
        }
    }
}
