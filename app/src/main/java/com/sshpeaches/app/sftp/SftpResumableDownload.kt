package com.majordaftapps.sshpeaches.app.sftp

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.security.MessageDigest
import net.schmizz.sshj.sftp.SFTPClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * A download that survives failures: the bytes received so far stay in [dataFile], and
 * [stateFile] records how far each range got without gaps. Downloading the same remote file
 * again (same size and modification time) fetches only what is missing.
 */
class SftpPartialDownload(val dataFile: File?, private val stateFile: File) {

    data class RangeState(val start: Long, val end: Long, val done: Long)

    fun load(remotePath: String, size: Long, mtime: Long): List<RangeState>? = runCatching {
        if (dataFile?.exists() == false || !stateFile.exists()) return null
        val json = JSONObject(stateFile.readText())
        if (json.getString("remotePath") != remotePath || json.getLong("size") != size || json.getLong("mtime") != mtime) {
            return null
        }
        val ranges = json.getJSONArray("ranges")
        List(ranges.length()) { index ->
            val item = ranges.getJSONArray(index)
            RangeState(item.getLong(0), item.getLong(1), item.getLong(2))
        }.takeIf { list -> list.all { it.start <= it.done && it.done <= it.end } }
    }.getOrNull()

    @Synchronized
    fun save(remotePath: String, size: Long, mtime: Long, ranges: List<RangeState>) {
        val json = JSONObject()
            .put("remotePath", remotePath)
            .put("size", size)
            .put("mtime", mtime)
            .put("ranges", JSONArray(ranges.map { JSONArray(listOf(it.start, it.end, it.done)) }))
        val temp = File(stateFile.path + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(stateFile)) {
            stateFile.writeText(json.toString())
            temp.delete()
        }
    }

    fun delete() {
        dataFile?.delete()
        deleteState()
    }

    fun deleteState() {
        stateFile.delete()
    }

    companion object {
        /** One partial per host and remote path, under [directory]. */
        fun forRemote(directory: File, hostKey: String, remotePath: String): SftpPartialDownload {
            val digest = digest("$hostKey\n$remotePath")
            directory.mkdirs()
            return SftpPartialDownload(File(directory, "$digest.part"), File(directory, "$digest.json"))
        }

        /**
         * Progress of a download written straight into [destination] ([destinationKey] names it, a path or a
         * content URI), recorded in [directory].
         */
        fun forDestination(
            directory: File,
            hostKey: String,
            remotePath: String,
            destinationKey: String,
            destination: File?
        ): SftpPartialDownload {
            val digest = digest("$hostKey\n$remotePath\n$destinationKey")
            directory.mkdirs()
            return SftpPartialDownload(destination, File(directory, "$digest.json"))
        }

        private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

        /** Deletes partials untouched for [maxAgeMillis], so abandoned downloads don't fill the cache. */
        fun deleteStale(directory: File, maxAgeMillis: Long, now: Long = System.currentTimeMillis()) {
            directory.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) file.delete()
            }
        }
    }
}

object SftpResumableDownloader {
    /**
     * Downloads [remotePath] into [partial]'s data file, continuing an earlier attempt when one
     * matches. On failure the partial is kept for the next attempt; on cancellation it is deleted.
     *
     * @return bytes that were already present from an earlier attempt (0 for a fresh download)
     */
    fun download(
        sftp: SFTPClient,
        remotePath: String,
        partial: SftpPartialDownload,
        settings: SftpTransferSettings,
        onBytesTransferred: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        onResume: (alreadyDownloaded: Long, size: Long) -> Unit = { _, _ -> }
    ): Long {
        val attributes = sftp.stat(remotePath)
        val dataFile = checkNotNull(partial.dataFile) { "A partial download needs a data file" }
        dataFile.parentFile?.mkdirs()
        return RandomAccessFile(dataFile, "rw").use { raf ->
            download(
                leases = SingleClientLeaseSource(sftp),
                connections = 1,
                remotePath = remotePath,
                size = attributes.size.coerceAtLeast(0L),
                mtime = attributes.mtime,
                partial = partial,
                channel = raf.channel,
                settings = settings,
                onBytesTransferred = onBytesTransferred,
                isCancelled = isCancelled,
                onResume = onResume
            )
        }
    }

    /**
     * Downloads [remotePath] ([size] bytes, modified at [mtime]) into [channel] over up to [connections] connections
     * from [leases], each fetching chunks of the file. Progress is recorded in [partial] as it goes, so downloading
     * the same file into the same place again fetches only what is missing. On failure the progress is kept; on
     * cancellation it is deleted.
     *
     * @return bytes that were already present from an earlier attempt (0 for a fresh download)
     */
    fun download(
        leases: SftpLeaseSource,
        connections: Int,
        remotePath: String,
        size: Long,
        mtime: Long,
        partial: SftpPartialDownload,
        channel: FileChannel,
        settings: SftpTransferSettings,
        onBytesTransferred: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        onResume: (alreadyDownloaded: Long, size: Long) -> Unit = { _, _ -> }
    ): Long {
        val resolved = settings.sanitized()
        val saved = partial.load(remotePath, size, mtime)
        // The data file is already open as [channel]; a fresh start rewrites every byte of it.
        if (saved == null) partial.deleteState()
        val queue = SftpChunkQueue(saved ?: SftpChunkQueue.plan(size, connections))
        val already = queue.bytesDone()
        if (already > 0L) onResume(already, size)
        val perConnection = minOf(
            resolved.sftpReadSize.toLong() * resolved.sftpMaxRequests,
            SftpTransferSettings.MAX_IN_FLIGHT_BYTES_PER_CONNECTION
        )
        try {
            val current = channel.size()
            if (current > size) channel.truncate(size) else if (current < size) extend(channel, size)
            partial.save(remotePath, size, mtime, queue.snapshot())
            val sink = FileChannelOffsetSink(channel)
            SftpSegmentedTransfer(
                queue = queue,
                workers = connections,
                leases = leases,
                requestSize = resolved.sftpReadSize,
                inFlightBytes = { lease ->
                    if (lease.shared) minOf(perConnection, SftpTransferSettings.SHARED_CONNECTION_IN_FLIGHT_BYTES) else perConnection
                },
                openIo = { sftp, _ -> SftpSegmentReader.open(sftp, remotePath, sink) },
                isCancelled = isCancelled,
                onProgress = onBytesTransferred,
                onCheckpoint = { chunks -> partial.save(remotePath, size, mtime, chunks) }
            ).run()
        } catch (error: Throwable) {
            if (error is SftpTransferCancelledException || error is SftpDownloadCancelledException || isCancelled()) {
                partial.delete()
            }
            throw error
        }
        partial.deleteState()
        return already
    }

    /** Grows [channel] to [size] bytes, which truncate cannot do. */
    private fun extend(channel: FileChannel, size: Long) {
        if (size > 0L) channel.write(java.nio.ByteBuffer.allocate(1), size - 1)
    }
}
