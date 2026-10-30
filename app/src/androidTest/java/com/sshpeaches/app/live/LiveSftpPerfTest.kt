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
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.service.FileTransferStatus
import com.majordaftapps.sshpeaches.app.service.SessionService
import com.majordaftapps.sshpeaches.app.service.SessionService.SessionStatus
import com.majordaftapps.sshpeaches.app.testutil.AppStateSeeder
import com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest
import com.majordaftapps.sshpeaches.app.testutil.TransferSettingsOverride
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
            now != null && now !== before && now.complete && !now.fromCache && now.path.trimEnd('/').endsWith(path.substringAfterLast('/')) &&
                now.entries.size >= minEntries
        }) { "listing of $path never arrived" }
        return secondsSince(start)
    }

    /** Seconds until the first entries of [path] show, which for a big folder is before it is fully listed. */
    private fun timeFirstEntries(service: SessionService, id: String, path: String): Double {
        val before = service.remoteDirectoryFlow().value[id]
        val start = System.nanoTime()
        service.listSftpDirectory(id, path)
        check(waitFor(120_000) {
            val now = service.remoteDirectoryFlow().value[id]
            now != null && now !== before && !now.fromCache && now.entries.isNotEmpty() &&
                now.path.trimEnd('/').endsWith(path.substringAfterLast('/'))
        }) { "listing of $path never arrived" }
        val seconds = secondsSince(start)
        check(waitFor(120_000) { service.remoteDirectoryFlow().value[id]?.complete == true }) { "$path never finished" }
        return seconds
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

    private fun linkRate(kBps: Int) {
        gen++
        Log.i("NetCtl", JSONObject().put("mode", "pass").put("new_conn", "pass").put("delay_ms", 20)
            .put("rate_kBps", kBps).put("gen", gen).toString())
        Thread.sleep(1_500)
    }

    /** Waits for the transfer like [waitTransfer], noting every retry message it shows. */
    private fun waitTransferNotingRetries(service: SessionService, id: String, timeoutMs: Long, retries: MutableList<String>): Double {
        val start = System.nanoTime()
        var last: String? = null
        check(waitFor(timeoutMs) {
            val p = service.fileTransferProgressFlow().value[id]
            check(p?.status != FileTransferStatus.FAILED) { "transfer failed: ${p?.errorMessage}; retries: $retries" }
            if (p?.retryMessage != null && p.retryMessage != last) retries += "%.0fs %s".format(secondsSince(start), p.retryMessage)
            last = p?.retryMessage
            p?.status == FileTransferStatus.SUCCEEDED
        }) { "transfer did not finish within ${timeoutMs / 1000}s; retries: $retries" }
        return secondsSince(start)
    }

    /**
     * A download over a slow link (300 KB/s, shared by every connection), at full settings, long enough for a starved
     * keepalive (60 s) to show. No retries are expected: the link is slow, not broken. Uploads are covered by
     * SftpSlowLinkTest: an upload held back by a slow link kills the test emulator's Wi-Fi.
     */
    /**
     * Going back, forward or up to a folder seen lately shows it at once from the browser's recent listings, then
     * the fresh listing replaces it; a folder not seen yet lists as usual.
     */
    @Test
    fun backForwardAndUpShowRecentFoldersAtOnce() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val id = "perf-recent-${System.nanoTime()}"
        withSessionService { service ->
            try {
                openSftp(service, id, ConnectionMode.SCP)
                val many = "$dir/many"
                val sub = "$dir/sub1"
                timeListing(service, id, many)
                timeListing(service, id, sub)
                fun revisit(path: String, label: String) {
                    val before = service.remoteDirectoryFlow().value[id]
                    val start = System.nanoTime()
                    service.listSftpDirectory(id, path)
                    val shown = service.remoteDirectoryFlow().value[id]
                    check(shown != null && shown !== before && shown.fromCache && shown.complete &&
                        shown.path.trimEnd('/') == path) { "$label: $path wasn't shown from the recent listings: $shown" }
                    val cachedSeconds = secondsSince(start)
                    check(waitFor(60_000) {
                        val now = service.remoteDirectoryFlow().value[id]
                        now != null && !now.fromCache && now.complete && now.path.trimEnd('/') == path
                    }) { "$label: the fresh listing of $path never replaced the cached one" }
                    val fresh = service.remoteDirectoryFlow().value.getValue(id)
                    check(fresh.entries.map { it.name }.sorted() == shown.entries.map { it.name }.sorted()) {
                        "$label: the cached listing of $path differs from the fresh one"
                    }
                    note("recent: $label to ${path.substringAfterLast('/')} shown in %.3f s, refreshed in %.2f s".format(cachedSeconds, secondsSince(start)))
                }
                revisit(many, "back")
                revisit(sub, "forward")
                // Up to a folder not listed yet: no cached listing, the usual one.
                val before = service.remoteDirectoryFlow().value[id]
                service.listSftpDirectory(id, dir)
                check(service.remoteDirectoryFlow().value[id] === before) { "a folder never listed was shown from the cache" }
                timeListing(service, id, dir)
                revisit(many, "into a folder seen before")
                // Home stays however many folders were opened since.
                (1..3).forEach { timeListing(service, id, "$dir/sub$it") }
                timeListing(service, id, "$dir/huge")
                timeListing(service, id, "$dir/links")
                val home = service.remoteDirectoryFlow().value.let { _ ->
                    service.listSftpDirectory(id, ".")
                    service.remoteDirectoryFlow().value.getValue(id)
                }
                check(home.fromCache) { "Home wasn't shown at once after seven other folders: ${home.path}" }
                note("recent: home shown at once after seven other folders (${home.path})")
            } finally {
                service.stopSession(id)
            }
        }
        Log.i("NetScenario", "== recent listings\n" + results.joinToString("\n"))
    }

    /**
     * The transfers pane's queue: transfers listed running first, then waiting in order; one cancelled while it
     * waits leaves the others going and can be retried; finished ones can be cleared.
     */
    @Test
    fun transferQueueCancelsRetriesAndClearsOneByOne() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val id = "perf-queue-${System.nanoTime()}"
        val context = instrumentation.targetContext
        val targets = (1..3).map { java.io.File(context.cacheDir, "queue-$it.bin").apply { delete() } }
        withSessionService { service ->
            try {
                openSftp(service, id, ConnectionMode.SCP)
                targets.forEach { service.scpDownloadFile(id, "$dir/mid.bin", it.absolutePath) }
                check(waitFor(10_000) { service.fileTransfersFlow().value[id].orEmpty().size == 3 }) { "three transfers weren't listed" }
                val listed = service.fileTransfersFlow().value.getValue(id)
                check(!listed[0].waiting && listed[1].waiting && listed[2].waiting) {
                    "not running first, then waiting: ${listed.map { it.fileName to it.waiting }}"
                }
                val second = listed[1].operationId
                check(service.cancelFileTransfer(id, second)) { "the waiting transfer couldn't be cancelled" }
                check(waitFor(120_000) { service.fileTransfersFlow().value[id].orEmpty().none { it.isActive } }) {
                    // The test runs in the app's process: its threads show where a stuck queue waits.
                    Thread.getAllStackTraces().filter { (_, stack) -> stack.any { "sshpeaches" in it.className } }
                        .forEach { (thread, stack) ->
                            Log.e("QueueDump", "${thread.name} ${thread.state}\n" + stack.take(25).joinToString("\n") { "    at $it" })
                        }
                    "the queue never emptied: ${service.fileTransfersFlow().value[id]?.map { "${it.fileName} ${it.status} waiting=${it.waiting} ${it.bytesTransferred}" }}"
                }
                val finished = service.fileTransfersFlow().value.getValue(id).associateBy { it.operationId }
                check(finished.getValue(second).status == FileTransferStatus.CANCELLED && finished.getValue(second).canRetry) {
                    "the cancelled transfer: ${finished[second]}"
                }
                check(finished.values.count { it.status == FileTransferStatus.SUCCEEDED } == 2) { "the others didn't finish: $finished" }
                check(targets[0].length() == targets[2].length() && targets[0].length() > 0) { "downloads incomplete" }

                check(service.retryFileTransfer(id, second)) { "the cancelled transfer couldn't be retried" }
                check(waitFor(120_000) {
                    val list = service.fileTransfersFlow().value[id].orEmpty()
                    list.none { it.isActive } && list.count { it.status == FileTransferStatus.SUCCEEDED } == 3
                }) { "the retried transfer didn't finish: ${service.fileTransfersFlow().value[id]}" }
                check(targets[1].length() == targets[0].length()) { "the retried download is incomplete" }
                note("queue: 3 downloads, the waiting one cancelled, retried and finished")

                service.clearFinishedTransfers(id)
                check(service.fileTransfersFlow().value[id].isNullOrEmpty()) { "finished transfers weren't cleared" }
            } finally {
                service.stopSession(id)
                targets.forEach { it.delete() }
            }
        }
        Log.i("NetScenario", "== transfer queue\n" + results.joinToString("\n"))
    }

    @Test
    fun slowLinkDownloadDoesntDrop() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val local = File(instrumentation.targetContext.cacheDir, "slow-down.bin")
        withSessionService { service ->
            val id = "slow-link"
            try {
                latency(0)
                openSftp(service, id, ConnectionMode.SCP)
                val retries = mutableListOf<String>()
                linkRate(300)
                local.delete()
                service.scpDownloadFile(id, "$dir/mid32.bin", local.absolutePath)
                Thread.sleep(100)
                val down = waitTransferNotingRetries(service, id, 600_000, retries)
                note("slow link: download 32 MiB in %.0f s = %.0f KB/s; retries: $retries".format(down, local.length() / 1e3 / down))
                check(retries.isEmpty()) { "retries on a link that works: $retries" }
            } finally {
                linkRate(0)
                latency(0)
                service.stopSession(id)
                local.delete()
            }
        }
        Log.i("NetScenario", "== slow-link\n" + results.joinToString("\n"))
    }

    /** A file browser left alone for 100 s must stay connected: no status change, and it still lists. */
    @Test
    fun idleBrowserStaysConnected() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        withSessionService { service ->
            latency(0)
            val id = "idle-browser"
            try {
                openSftp(service, id, ConnectionMode.SCP)
                check(waitFor(30_000) { service.sessionsFlow().value.firstOrNull { it.hostId == id }?.status == SessionStatus.ACTIVE })
                val changes = mutableListOf<String>()
                var last = ""
                val start = System.nanoTime()
                while (secondsSince(start) < 100) {
                    val s = service.sessionsFlow().value.firstOrNull { it.hostId == id }
                    val now = "${s?.status} ${s?.statusMessage}"
                    if (now != last) {
                        changes += "%.1fs %s".format(secondsSince(start), now)
                        last = now
                    }
                    Thread.sleep(100)
                }
                note("idle 100 s, status changes: ${changes.joinToString(" | ")}")
                note("list after idling %.2f s".format(timeListing(service, id, "$dir/sub1")))
                check(changes.size == 1) { "the idle session changed state: $changes" }
            } finally {
                service.stopSession(id)
            }
        }
        Log.i("NetScenario", "== idle-browser\n" + results.joinToString("\n"))
    }

    /**
     * One short download (mid32.bin, 32 MiB) and upload (16 MiB) at full settings, short enough for the test
     * emulator's network to survive. Ignores the emulator throttle.
     */
    @Test
    fun shortTransferTimings() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        val context = instrumentation.targetContext
        val local = File(context.cacheDir, "perf-mid32.bin")
        val upload = File(context.cacheDir, "perf-up16.bin").apply {
            outputStream().use { out -> val chunk = ByteArray(1 shl 20) { it.toByte() }; repeat(16) { out.write(chunk) } }
        }
        withSessionService { service ->
            latency(0)
            val id = "short-transfer"
            try {
                openSftp(service, id, ConnectionMode.SCP)
                local.delete()
                val downloadStart = System.nanoTime()
                service.scpDownloadFile(id, "$dir/mid32.bin", local.absolutePath)
                Thread.sleep(100)
                waitTransfer(service, id, 300_000)
                val down = secondsSince(downloadStart)
                note("download 32 MiB in %.2f s = %.1f MB/s".format(down, local.length() / 1e6 / down))
                val uploadStart = System.nanoTime()
                service.scpUploadFile(id, upload.absolutePath, "$dir/up16.bin")
                Thread.sleep(100)
                waitTransfer(service, id, 300_000)
                val up = secondsSince(uploadStart)
                note("upload 16 MiB in %.2f s = %.1f MB/s".format(up, upload.length() / 1e6 / up))
            } finally {
                service.stopSession(id)
                local.delete()
                upload.delete()
            }
        }
        Log.i("NetScenario", "== short-transfer\n" + results.joinToString("\n"))
    }

    /**
     * Listing big folders: huge/ (20,000 files) and links/ (3,000 symbolic links: to files, to folders, broken), in
     * the file browser (which resolves links) and the console, at 0 and 150 ms round trip. Light on traffic, unlike
     * the transfer timings.
     */
    @Test
    fun largeFolderListingTimings() {
        assumeTrue("needs -e netHost", args.getString("netHost") != null)
        withSessionService { service ->
            for (oneWay in listOf(0, 75)) {
                latency(oneWay)
                for (mode in listOf(ConnectionMode.SCP, ConnectionMode.SFTP)) {
                    val id = "list-perf-$oneWay-${mode.name}"
                    try {
                        openSftp(service, id, mode)
                        val label = if (mode == ConnectionMode.SCP) "browser" else "console"
                        note("rtt ${oneWay * 2} ms $label: list huge/ (20000) %.2f s".format(timeListing(service, id, "$dir/huge", 20_000)))
                        note("rtt ${oneWay * 2} ms $label: list links/ (3000 links) %.2f s".format(timeListing(service, id, "$dir/links", 3_000)))
                        if (mode == ConnectionMode.SCP) {
                            note("rtt ${oneWay * 2} ms $label: huge/ first entries shown %.2f s".format(timeFirstEntries(service, id, "$dir/huge")))
                            note("rtt ${oneWay * 2} ms $label: links/ first entries shown %.2f s".format(timeFirstEntries(service, id, "$dir/links")))
                        }
                    } finally {
                        service.stopSession(id)
                        Thread.sleep(1_000)
                    }
                }
            }
            latency(0)
        }
        Log.i("NetScenario", "== listing-perf\n" + results.joinToString("\n"))
    }

    /**
     * Lists folders on another server, only lists: -e homeHost/homePort/homeUser, -e homeKeyB64 (a base64 private
     * key the server accepts) and -e homePaths (comma-separated absolute paths). Each folder is listed twice in the
     * file browser and in the console; only entry counts and times are logged.
     */
    @Test
    fun otherServerListingTimings() {
        assumeTrue("needs -e homeHost", args.getString("homeHost") != null)
        val privateKey = String(android.util.Base64.decode(args.getString("homeKeyB64")!!, android.util.Base64.DEFAULT))
        val identity = Identity(
            id = "listing-key", label = "Listing test key", fingerprint = "", username = args.getString("homeUser")!!,
            createdEpochMillis = System.currentTimeMillis(), hasPrivateKey = true
        )
        AppStateSeeder.seedIdentity(identity, privateKey)
        val paths = args.getString("homePaths")!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        withSessionService { service ->
            for (mode in listOf(ConnectionMode.SCP, ConnectionMode.SFTP)) {
                val id = "other-${mode.name}"
                val label = if (mode == ConnectionMode.SCP) "browser" else "console"
                val start = System.nanoTime()
                service.startSession(
                    requestedSessionId = id,
                    host = HostConnection(
                        id = id, name = id, host = args.getString("homeHost")!!,
                        port = args.getString("homePort")!!.toInt(), username = identity.username!!,
                        preferredAuth = AuthMethod.IDENTITY, preferredIdentityId = identity.id
                    ),
                    mode = mode, autoTrustUnknownHostKey = true, hostKeyPromptEnabled = false
                )
                try {
                    check(waitFor(120_000) {
                        val s = service.sessionsFlow().value.firstOrNull { it.hostId == id }
                        check(s?.status != SessionStatus.ERROR) { "session failed: ${s?.statusMessage}" }
                        service.remoteDirectoryFlow().value[id] != null
                    }) { "no first listing" }
                    note("$label: connect -> first listing %.2f s".format(secondsSince(start)))
                    for (path in paths) {
                        repeat(2) { round ->
                            val seconds = timeListing(service, id, path)
                            val count = service.remoteDirectoryFlow().value[id]?.entries?.size ?: -1
                            val links = service.remoteDirectoryFlow().value[id]?.entries?.count { it.isSymbolicLink } ?: -1
                            note("$label: $path ($count entries, $links links) #${round + 1} %.2f s".format(seconds))
                        }
                    }
                } finally {
                    service.stopSession(id)
                    Thread.sleep(1_000)
                }
            }
        }
        Log.i("NetScenario", "== other-server-listing\n" + results.joinToString("\n"))
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
        // These measure SFTP itself.
        TransferSettingsOverride.withoutBoost(InstrumentationRegistry.getInstrumentation().targetContext)
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
