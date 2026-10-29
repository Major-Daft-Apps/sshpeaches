package com.majordaftapps.sshpeaches.app.service

import android.content.Context
import com.majordaftapps.sshpeaches.app.util.posixShellQuote
import com.majordaftapps.sshpeaches.boost.boostclient.Boostclient
import com.majordaftapps.sshpeaches.boost.boostclient.Progress
import com.majordaftapps.sshpeaches.boost.boostclient.Session as BoostSession
import com.majordaftapps.sshpeaches.boost.boostclient.Shell as BoostShell
import com.majordaftapps.sshpeaches.boost.boostclient.ShellListener
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import org.json.JSONObject

/** Boost couldn't start on this server; the reason says why, and the app uses SFTP or SSH. */
internal class BoostUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Boost for one session: the sshpeaches-boost helper (tools/boost) running on the server, started over the
 * session's SSH login, and the app's QUIC (or TLS-over-TCP) connection to it.
 *
 * The helper lives exactly as long as the SSH channel it was started on: closing this object closes that channel,
 * and the helper exits. Everything security-relevant crosses SSH: the one-time token the helper requires on every
 * request, the certificate fingerprint the app pins, and each file the helper may read or write.
 */
internal class BoostHelper private constructor(
    private val channel: Session,
    private val command: Session.Command,
    private val client: BoostSession,
    private val answers: LinkedBlockingQueue<JSONObject>,
    private val reader: Thread
) : AutoCloseable {

    /** "udp" or "tcp". */
    val transport: String = client.transport()

    @Volatile
    var broken = false
        private set

    private val pending = ConcurrentHashMap<Long, CompletableFuture<JSONObject>>()
    private val nextId = AtomicLong()
    private val controlLock = Any()

    init {
        Thread({
            while (true) {
                val answer = runCatching { answers.take() }.getOrNull() ?: break
                if (answer.optBoolean("eof")) {
                    broken = true
                    pending.values.forEach { it.completeExceptionally(IOException("The boost helper ended")) }
                    break
                }
                pending.remove(answer.optLong("id"))?.complete(answer)
            }
        }, "file-boost-answers").apply { isDaemon = true }.start()
    }

    /** Marks this boost unusable (its connection failed): the session starts a new one, and uses SFTP meanwhile. */
    fun markBroken() {
        broken = true
    }

    private fun control(request: JSONObject): JSONObject {
        val id = nextId.incrementAndGet()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        synchronized(controlLock) {
            command.outputStream.write((request.put("id", id).toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            command.outputStream.flush()
        }
        val answer = try {
            future.get(CONTROL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            pending.remove(id)
            throw IOException("The boost helper didn't answer", error)
        }
        answer.optString("error").takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
        return answer
    }

    /** Lets the helper read [path]; returns the path it resolved and the file's size. */
    fun allowRead(path: String): Pair<String, Long> {
        val answer = control(JSONObject().put("op", "allow-read").put("path", path))
        return answer.getString("path") to answer.optLong("size")
    }

    /** Lets the helper write [path], creating or truncating it with [permissions]; returns the path it resolved. */
    fun allowWrite(path: String, permissions: Int): String =
        control(JSONObject().put("op", "allow-write").put("path", path).put("truncate", true).put("perm", permissions))
            .getString("path")

    fun forget(path: String) {
        runCatching { control(JSONObject().put("op", "forget").put("path", path)) }
    }

    /** The folder at [path] as the helper's listing JSON. */
    fun list(path: String): String = client.list(path)

    /**
     * Starts the user's login shell in the helper (which must have been started with shells allowed). The shell
     * reattaches by itself whenever its connection breaks; see [BoostTerminalChannel].
     */
    fun startShell(rows: Int, columns: Int, term: String, listener: ShellListener): BoostShell =
        client.startShell(rows.toLong(), columns.toLong(), term, listener)

    /**
     * Downloads [path] (allowed with [allowRead]) into [fd], fetching the parts of [chunksJson] still missing with
     * [streams] parallel streams. [onChunk] hears each chunk's progress; [isCancelled] is polled.
     */
    fun download(path: String, fd: Int, chunksJson: String, streams: Int, onChunk: (Int, Long) -> Unit, isCancelled: () -> Boolean) =
        run(client.startDownload(path, fd.toLong(), chunksJson, streams.toLong(), progressOf(onChunk)), isCancelled)

    /** Uploads [fd] to [path] (allowed with [allowWrite]), the same way. */
    fun upload(path: String, fd: Int, chunksJson: String, streams: Int, onChunk: (Int, Long) -> Unit, isCancelled: () -> Boolean) =
        run(client.startUpload(path, fd.toLong(), chunksJson, streams.toLong(), progressOf(onChunk)), isCancelled)

    private fun progressOf(onChunk: (Int, Long) -> Unit) = object : Progress {
        override fun onChunk(index: Long, done: Long) = onChunk(index.toInt(), done)
    }

    private fun run(transfer: com.majordaftapps.sshpeaches.boost.boostclient.Transfer, isCancelled: () -> Boolean) {
        var failure: Throwable? = null
        val waiter = Thread({
            try {
                transfer.await()
            } catch (error: Throwable) {
                failure = error
            }
        }, "file-boost-transfer").apply { start() }
        while (waiter.isAlive) {
            if (isCancelled()) transfer.cancel()
            waiter.join(100)
        }
        failure?.let { throw it }
    }

    override fun close() {
        broken = true
        runCatching { client.close() }
        // The helper exits when this channel closes.
        runCatching { command.outputStream.close() }
        runCatching { channel.close() }
        reader.interrupt()
    }

    companion object {
        private const val CONTROL_TIMEOUT_MS = 15_000L
        private const val READY_TIMEOUT_MS = 15_000L
        private const val DIAL_TIMEOUT_MS = 4_000L
        private const val IDLE_SECONDS = 600
        private const val ASSET_DIR = "boost"
        private const val REMOTE_DIR = ".cache/sshpeaches/boost"
        private const val UNANSWERED_UDP_PORT = 9L
        private val HELPER_DIR_NAME = Regex("[0-9a-f]{16}")

        /** Tests set this to send UDP to a port nobody answers, as a firewall that drops UDP would. */
        @Volatile
        internal var dropUdpForTests = false

        /**
         * Tests that can't reach the helper's TCP side directly (the managed emulator only connects to a few host
         * ports) set the port it listens on and the address to reach it at, an adb reverse on the device.
         */
        @Volatile
        internal var tcpPortForTests = 0

        @Volatile
        internal var dialHostForTests: String? = null

        /** Requests in flight per transfer, for test devices whose network can't take the usual number. */
        @Volatile
        internal var streamsForTests = 0

        /** Whether this build of the app carries the boost client (it ships for arm64-v8a and x86_64). */
        val clientAvailable: Boolean by lazy {
            runCatching { Boostclient.touch(); true }.getOrDefault(false)
        }

        /**
         * Starts Boost over [ssh]: uploads the helper with [sftp] when the server doesn't have this version,
         * starts it on a port in [ports] (null for any), and connects to it at the address SSH is connected to.
         * Throws [BoostUnavailable] saying why it can't.
         */
        fun start(
            context: Context,
            ssh: SSHClient,
            sftp: SFTPClient,
            allowTcp: Boolean,
            ports: IntRange?,
            shells: Boolean = false
        ): BoostHelper {
            if (!clientAvailable) throw BoostUnavailable("this phone's processor isn't supported")
            // The address SSH reached, so UDP goes to the same machine (and Go needs no DNS of its own on Android).
            val host = dialHostForTests ?: ssh.remoteAddress?.hostAddress ?: throw BoostUnavailable("the session isn't connected")
            val system = runCommand(ssh, "uname -sm")?.trim().orEmpty()
            val arch = when {
                !system.startsWith("Linux") -> throw BoostUnavailable("the server isn't Linux (${system.ifBlank { "unknown" }})")
                system.endsWith("x86_64") || system.endsWith("amd64") -> "amd64"
                system.endsWith("aarch64") || system.endsWith("arm64") -> "arm64"
                else -> throw BoostUnavailable("the server's processor isn't supported ($system)")
            }
            val manifest = JSONObject(context.assets.open("$ASSET_DIR/manifest.json").bufferedReader().use { it.readText() })
            val expected = manifest.getJSONObject(arch)
            val sha256 = expected.getString("sha256")
            val size = expected.getLong("size")
            val dir = "$REMOTE_DIR/${sha256.take(16)}"
            val helper = "$dir/sshpeaches-boost"
            if (!helperPresent(ssh, sftp, helper, sha256, size)) {
                install(context, ssh, sftp, arch, dir, helper, sha256)
            }
            val token = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
            val channel = ssh.startSession()
            try {
                val command = channel.exec("exec ${posixShellQuote(helper)} serve")
                command.outputStream.write(
                    (JSONObject().put("token", token).put("idle", IDLE_SECONDS).put("tcp", allowTcp).put("tcpPort", tcpPortForTests)
                        .put("portMin", ports?.first ?: 0).put("portMax", ports?.last ?: 0)
                        // Only a terminal's helper may run a shell; the file browser's never can.
                        .put("shell", shells).toString() + "\n")
                        .toByteArray(StandardCharsets.UTF_8)
                )
                command.outputStream.flush()
                val answers = LinkedBlockingQueue<JSONObject>()
                val ready = CompletableFuture<JSONObject>()
                val reader = Thread({
                    val lines = BufferedReader(InputStreamReader(command.inputStream, StandardCharsets.UTF_8))
                    runCatching {
                        while (true) {
                            val line = lines.readLine() ?: break
                            val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                            if (json.has("event")) ready.complete(json) else answers.offer(json)
                        }
                    }
                    ready.completeExceptionally(IOException("The boost helper ended before it was ready"))
                    // offer, not put: close() interrupts this thread, and the queue is unbounded anyway.
                    answers.offer(JSONObject().put("eof", true))
                }, "file-boost-helper").apply { isDaemon = true; start() }
                val event = try {
                    ready.get(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (error: Exception) {
                    throw BoostUnavailable("the helper didn't start", error)
                }
                if (event.optString("event") != "ready") {
                    throw BoostUnavailable("the helper failed: ${event.optString("error")}")
                }
                val client = try {
                    Boostclient.dial(
                        host, if (dropUdpForTests) UNANSWERED_UDP_PORT else event.optLong("udp"), event.optLong("tcp"),
                        event.getString("cert"), token, allowTcp,
                        DIAL_TIMEOUT_MS
                    )
                } catch (error: Exception) {
                    throw BoostUnavailable(
                        if (allowTcp) "the server's firewall lets neither UDP nor TCP through to the helper" else "the server's firewall doesn't let UDP through",
                        error
                    )
                }
                return BoostHelper(channel, command, client, answers, reader)
            } catch (error: Throwable) {
                runCatching { channel.close() }
                throw error
            }
        }

        private fun helperPresent(ssh: SSHClient, sftp: SFTPClient, path: String, sha256: String, size: Long): Boolean {
            val attributes = runCatching { sftp.stat(path) }.getOrNull() ?: return false
            if (attributes.size != size) return false
            // The directory is the user's own (0700) and named by the hash; the check also catches a damaged copy.
            val sum = runCommand(ssh, "sha256sum ${posixShellQuote(path)} 2>/dev/null")?.trim()?.substringBefore(' ')
            return sum.isNullOrEmpty() || sum == sha256
        }

        private fun install(context: Context, ssh: SSHClient, sftp: SFTPClient, arch: String, dir: String, path: String, sha256: String) {
            sftp.mkdirs(dir)
            for (own in listOf(".cache/sshpeaches", REMOTE_DIR, dir)) {
                runCatching { sftp.chmod(own, 0b111_000_000) }
            }
            val temp = "$path.part"
            val digest = MessageDigest.getInstance("SHA-256")
            sftp.open(
                temp,
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC),
                FileAttributes.Builder().withPermissions(0b111_000_000).build()
            ).use { remote ->
                remote.RemoteFileOutputStream(0, 16).use { out ->
                    context.assets.open("$ASSET_DIR/sshpeaches-boost-linux-$arch").use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            val written = digest.digest().joinToString("") { "%02x".format(it) }
            if (written != sha256) {
                runCatching { sftp.rm(temp) }
                throw BoostUnavailable("the helper in this app is damaged")
            }
            runCatching { sftp.rm(path) }
            sftp.rename(temp, path)
            sftp.chmod(path, 0b111_000_000)
            val sum = runCommand(ssh, "sha256sum ${posixShellQuote(path)} 2>/dev/null")?.trim()?.substringBefore(' ')
            if (!sum.isNullOrEmpty() && sum != sha256) throw BoostUnavailable("the helper was damaged on its way to the server")
            removeOlderHelpers(sftp, dir.substringAfterLast('/'))
        }

        /** Deletes other versions' helper folders (a helper still running from one keeps working without its file). */
        private fun removeOlderHelpers(sftp: SFTPClient, keep: String) {
            val versions = runCatching { sftp.ls(REMOTE_DIR) }.getOrDefault(emptyList())
            for (version in versions) {
                if (!version.isDirectory || version.name == keep || !HELPER_DIR_NAME.matches(version.name)) continue
                runCatching {
                    sftp.ls(version.path).filter { it.isRegularFile }.forEach { sftp.rm(it.path) }
                    sftp.rmdir(version.path)
                }
            }
        }

        private fun runCommand(ssh: SSHClient, command: String): String? = runCatching {
            ssh.startSession().use { session ->
                val cmd = session.exec(command)
                cmd.join(10, TimeUnit.SECONDS)
                collectCommandOutput(cmd, cmd.inputStream, 2_000L)
            }
        }.getOrNull()
    }
}
