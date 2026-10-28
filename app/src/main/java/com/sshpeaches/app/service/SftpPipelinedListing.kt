package com.majordaftapps.sshpeaches.app.service

import java.util.concurrent.TimeUnit
import net.schmizz.concurrent.Promise
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException

/**
 * Lists a directory with requests sent together rather than one reply at a time: resolving the path and opening the
 * directory go out at once, up to [MAX_READDIR_DEPTH] directory reads are kept in flight (OpenSSH sends 100 names per
 * read), the close is not waited for, and symbolic links are resolved and looked up over a window of
 * [MAX_LINK_REQUESTS_IN_FLIGHT] requests. A 20,000-entry folder took 50 round trips with 4 reads in flight; now about 5.
 *
 * [bulkLinkLookup], when given, looks every link up with one command on the server instead (see
 * [bulkLinkLookupCommand]); links it can't answer for are looked up over SFTP as before.
 *
 * [onPartial], when given, gets the entries read so far (links not yet resolved), first as soon as the first names
 * arrive and then every [PARTIAL_INTERVAL_MS] (each one sorts everything read so far), so a big folder shows up
 * before it is fully listed.
 */
internal fun loadSftpDirectoryPipelined(
    sftp: SFTPClient,
    requestedPath: String,
    resolveSymbolicLinks: Boolean,
    onPartial: ((LoadedSftpDirectory) -> Unit)? = null,
    bulkLinkLookup: ((directory: String, names: List<String>) -> Map<String, LinkInfo>?)? = null
): LoadedSftpDirectory {
    val engine = sftp.sftpEngine
    val charset = engine.subsystem.remoteCharset
    val timeoutMs = engine.timeoutMs.toLong().coerceAtLeast(1L)
    fun send(type: PacketType, path: String): Promise<Response, SFTPException> =
        engine.request(engine.newRequest(type).putString(path, charset))
    fun Promise<Response, SFTPException>.reply(): Response = retrieve(timeoutMs, TimeUnit.MILLISECONDS)

    val listingPath = requestedPath.trim().ifBlank { "." }
    val realPath = send(PacketType.REALPATH, listingPath)
    val openDir = send(PacketType.OPENDIR, listingPath)
    val resolvedPath = runCatching { readSingleName(realPath.reply(), charset) }.getOrDefault(listingPath)
    val handle = openDir.reply().ensurePacketTypeIs(PacketType.HANDLE).readBytes()

    val items = ArrayList<RemoteResourceInfo>()
    var lastPartialAt = 0L
    var partialCount = 0
    fun publishPartial() {
        val publish = onPartial ?: return
        val now = System.nanoTime()
        if (items.size == partialCount || (partialCount > 0 && now - lastPartialAt < PARTIAL_INTERVAL_MS * 1_000_000)) return
        lastPartialAt = now
        partialCount = items.size
        publish(
            LoadedSftpDirectory(
                path = resolvedPath,
                entries = items.map { remoteDirectoryEntry(it, false, null, null) }.sortedForBrowsing()
            )
        )
    }
    try {
        val reads = ArrayDeque<Promise<Response, SFTPException>>()
        fun readNext() {
            reads.addLast(engine.request(engine.newRequest(PacketType.READDIR).putString(handle)))
        }
        repeat(INITIAL_READDIR_DEPTH) { readNext() }
        var finished = false
        while (reads.isNotEmpty()) {
            val response = reads.removeFirst().reply()
            if (finished) continue
            when (response.type) {
                PacketType.NAME -> {
                    repeat(response.readUInt32AsInt()) {
                        val name = response.readString(charset)
                        response.readString()
                        val attributes = response.readFileAttributes()
                        if (name != "." && name != "..") {
                            items += RemoteResourceInfo(engine.pathHelper.getComponents(resolvedPath, name), attributes)
                        }
                    }
                    // Each reply that isn't the end suggests more: keep a growing number of reads in flight.
                    readNext()
                    repeat(2) { if (reads.size < MAX_READDIR_DEPTH) readNext() }
                    publishPartial()
                }
                PacketType.STATUS -> {
                    response.ensureStatusIs(Response.StatusCode.EOF)
                    finished = true
                }
                else -> throw SFTPException("Unexpected packet: ${response.type}")
            }
        }
    } finally {
        runCatching { engine.request(engine.newRequest(PacketType.CLOSE).putString(handle)) }
    }

    val linkTargets = HashMap<String, Pair<String?, FileAttributes?>>()
    if (resolveSymbolicLinks) {
        var links = items.filter { it.attributes.type == FileMode.Type.SYMLINK }
        if (links.isNotEmpty()) publishPartial()
        if (links.isNotEmpty() && bulkLinkLookup != null) {
            val answered = runCatching {
                bulkLinkLookup(resolvedPath, links.map { it.name }.filter(::canLookUpInBulk))
            }.getOrNull().orEmpty()
            links.forEach { link ->
                val info = answered[link.name] ?: return@forEach
                val type = if (info.isDirectory) FileMode.Type.DIRECTORY else FileMode.Type.REGULAR
                linkTargets[link.path] = if (info.broken) {
                    null to null
                } else {
                    info.target.orEmpty() to FileAttributes.Builder().withType(type).build()
                }
            }
            links = links.filter { it.path !in linkTargets }
        }
        val pending = ArrayDeque<Triple<String, Promise<Response, SFTPException>, Promise<Response, SFTPException>>>()
        fun finishOldest() {
            val (path, realPathReply, statReply) = pending.removeFirst()
            val target = runCatching { readSingleName(realPathReply.reply(), charset) }.getOrNull()
            val attributes = runCatching {
                statReply.reply().ensurePacketTypeIs(PacketType.ATTRS).readFileAttributes()
            }.getOrNull()
            linkTargets[path] = target to attributes?.takeIf { target != null }
        }
        for (link in links) {
            if (pending.size * 2 >= MAX_LINK_REQUESTS_IN_FLIGHT) finishOldest()
            pending.addLast(Triple(link.path, send(PacketType.REALPATH, link.path), send(PacketType.STAT, link.path)))
        }
        while (pending.isNotEmpty()) finishOldest()
    }
    val entries = items.map { item ->
        val (target, attributes) = linkTargets[item.path] ?: (null to null)
        remoteDirectoryEntry(item, resolveSymbolicLinks, target, attributes)
    }.sortedForBrowsing()
    return LoadedSftpDirectory(path = resolvedPath, entries = entries)
}

private fun readSingleName(response: Response, charset: java.nio.charset.Charset): String {
    response.ensurePacketTypeIs(PacketType.NAME)
    if (response.readUInt32AsInt() != 1) throw SFTPException("Unexpected data in ${response.type} packet")
    return response.readString(charset)
}

private const val INITIAL_READDIR_DEPTH = 2
private const val MAX_READDIR_DEPTH = 64
private const val MAX_LINK_REQUESTS_IN_FLIGHT = 2048
private const val PARTIAL_INTERVAL_MS = 1_000L
