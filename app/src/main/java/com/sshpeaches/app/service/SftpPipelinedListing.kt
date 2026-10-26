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
 * directory go out at once, a few directory reads are kept in flight, the close is not waited for, and every
 * symbolic link is resolved and looked up in one batch. A listing that took 5 + (entries / 100) + 2 × links round
 * trips now takes about 2 + entries / 300, plus 1 for links.
 */
internal fun loadSftpDirectoryPipelined(
    sftp: SFTPClient,
    requestedPath: String,
    resolveSymbolicLinks: Boolean
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
                    readNext()
                    if (reads.size < MAX_READDIR_DEPTH) readNext()
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

    val links = if (resolveSymbolicLinks) items.filter { it.attributes.type == FileMode.Type.SYMLINK } else emptyList()
    val linkTargets = HashMap<String, Pair<String?, FileAttributes?>>()
    links.chunked(MAX_LINK_REQUESTS_IN_FLIGHT / 2).forEach { batch ->
        val sent = batch.map { item -> item.path to (send(PacketType.REALPATH, item.path) to send(PacketType.STAT, item.path)) }
        sent.forEach { (path, requests) ->
            val target = runCatching { readSingleName(requests.first.reply(), charset) }.getOrNull()
            val attributes = runCatching {
                requests.second.reply().ensurePacketTypeIs(PacketType.ATTRS).readFileAttributes()
            }.getOrNull()
            linkTargets[path] = target to attributes?.takeIf { target != null }
        }
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
private const val MAX_READDIR_DEPTH = 4
private const val MAX_LINK_REQUESTS_IN_FLIGHT = 128
