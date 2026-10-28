package com.majordaftapps.sshpeaches.app.service

import com.majordaftapps.sshpeaches.app.util.posixShellQuote
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient

/** What a symbolic link points at: its target as written in the link, and whether that is a folder or missing. */
internal data class LinkInfo(val target: String?, val isDirectory: Boolean, val broken: Boolean)

/**
 * The shell command that looks up every link of [directory] at once. SFTP reports a link only as a link, and has no
 * bulk lookup, so finding what each one points at took two requests per link.
 *
 * With GNU find (most Linux servers) one process reports each link's target type and target. Elsewhere (busybox,
 * BSD, macOS) a loop of shell built-ins reports only whether each target is a folder, something else, or missing:
 * running `readlink` once per link took seconds for a few thousand. Lines are `type<TAB>name<TAB>target`.
 *
 * The command is constant apart from the quoted directory; the loop reads link names from standard input, so
 * nothing the server named can become part of a command.
 */
internal fun bulkLinkLookupCommand(directory: String): String =
    "cd -- ${posixShellQuote(directory)} || exit 3; " +
        "if find . -maxdepth 0 -printf '' >/dev/null 2>&1; then " +
        "exec find . -mindepth 1 -maxdepth 1 -type l -printf '%Y\\t%f\\t%l\\n'; " +
        "fi; " +
        "while IFS= read -r f; do " +
        "if [ -d \"./\$f\" ]; then t=d; elif [ -e \"./\$f\" ]; then t=f; else t=x; fi; " +
        "printf '%s\\t%s\\t\\n' \"\$t\" \"\$f\"; " +
        "done"

/** Names the command can carry: one per line, split on tabs. */
internal fun canLookUpInBulk(name: String): Boolean = name.none { it == '\n' || it == '\r' || it == '\t' || it == '\u0000' }

internal fun parseBulkLinkLookup(output: String): Map<String, LinkInfo> {
    val result = HashMap<String, LinkInfo>()
    output.split('\n').forEach { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return@forEach
        val (type, name, target) = parts
        // find's %Y: d folder, N missing, L a loop of links; the shell loop: d, f, x (missing).
        when (type) {
            "d" -> result[name] = LinkInfo(target, isDirectory = true, broken = false)
            "N", "L", "x" -> result[name] = LinkInfo(null, isDirectory = false, broken = true)
            "f", "l", "s", "p", "c", "b", "D" -> result[name] = LinkInfo(target, isDirectory = false, broken = false)
        }
    }
    return result
}

/**
 * Runs [bulkLinkLookupCommand] on [client] for [names] in [directory]. Null when the server doesn't run commands
 * (an SFTP-only account), or doesn't answer in time; the caller then looks the links up over SFTP.
 */
internal fun lookUpLinksInBulk(
    client: SSHClient,
    directory: String,
    names: List<String>,
    timeoutMs: Long = BULK_LINK_LOOKUP_TIMEOUT_MS,
    outputLimit: Int = BULK_LINK_LOOKUP_OUTPUT_LIMIT
): Map<String, LinkInfo>? {
    if (names.isEmpty()) return emptyMap()
    if (directory.any { it == '\n' || it == '\u0000' }) return null
    return client.startSession().use { session ->
        val command = session.exec(bulkLinkLookupCommand(directory))
        // Written apart from the reading below: the server's replies would otherwise wait on our full send window.
        val writer = Thread({
            runCatching {
                command.outputStream.use { input ->
                    names.forEach { input.write((it + "\n").toByteArray(StandardCharsets.UTF_8)) }
                }
            }
        }, "bulk-link-lookup-input").apply { start() }
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = Thread({
            try {
                Thread.sleep(timeoutMs)
                timedOut.set(true)
                runCatching { command.close() }
            } catch (_: InterruptedException) {
            }
        }, "bulk-link-lookup-timeout").apply {
            isDaemon = true
            start()
        }
        val output = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(16 * 1024)
            val stream = command.inputStream
            while (true) {
                val read = runCatching { stream.read(buffer) }.getOrDefault(-1)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > outputLimit) return@use null
            }
        } finally {
            watchdog.interrupt()
        }
        if (timedOut.get()) return@use null
        writer.join(1_000)
        command.join(1, TimeUnit.SECONDS)
        if ((command.exitStatus ?: -1) != 0) return@use null
        parseBulkLinkLookup(output.toString(StandardCharsets.UTF_8.name()))
    }
}

private const val BULK_LINK_LOOKUP_TIMEOUT_MS = 15_000L
private const val BULK_LINK_LOOKUP_OUTPUT_LIMIT = 8 shl 20
