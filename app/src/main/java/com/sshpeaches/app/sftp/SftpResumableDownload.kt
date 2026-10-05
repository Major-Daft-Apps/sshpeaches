package com.majordaftapps.sshpeaches.app.sftp

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLongArray
import net.schmizz.sshj.sftp.SFTPClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * A download that survives failures: the bytes received so far stay in [dataFile], and
 * [stateFile] records how far each range got without gaps. Downloading the same remote file
 * again (same size and modification time) fetches only what is missing.
 */
class SftpPartialDownload(val dataFile: File, private val stateFile: File) {

    data class RangeState(val start: Long, val end: Long, val done: Long)

    fun load(remotePath: String, size: Long, mtime: Long): List<RangeState>? = runCatching {
        if (!dataFile.exists() || !stateFile.exists()) return null
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
        dataFile.delete()
        stateFile.delete()
    }

    companion object {
        /** One partial per host and remote path, under [directory]. */
        fun forRemote(directory: File, hostKey: String, remotePath: String): SftpPartialDownload {
            directory.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$hostKey\n$remotePath".toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(32)
            return SftpPartialDownload(File(directory, "$digest.part"), File(directory, "$digest.json"))
        }

        /** Deletes partials untouched for [maxAgeMillis], so abandoned downloads don't fill the cache. */
        fun deleteStale(directory: File, maxAgeMillis: Long, now: Long = System.currentTimeMillis()) {
            directory.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) file.delete()
            }
        }
    }
}

object SftpResumableDownloader {
    private const val STATE_SAVE_INTERVAL_MS = 500L

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
        val size = attributes.size.coerceAtLeast(0L)
        val mtime = attributes.mtime
        val saved = partial.load(remotePath, size, mtime)
        if (saved == null) partial.delete()
        val ranges = saved ?: SftpPipelinedDownloader.splitRanges(size, settings)
            .map { SftpPartialDownload.RangeState(it.start, it.endExclusive, it.start) }
        val already = ranges.sumOf { it.done - it.start }
        if (already > 0L) onResume(already, size)
        val done = AtomicLongArray(ranges.size).also { array -> ranges.forEachIndexed { i, r -> array.set(i, r.done) } }
        fun currentState() = ranges.mapIndexed { i, r -> r.copy(done = done.get(i)) }
        var lastSave = 0L
        fun saveThrottled(force: Boolean) {
            val now = System.currentTimeMillis()
            synchronized(done) {
                if (!force && now - lastSave < STATE_SAVE_INTERVAL_MS) return
                lastSave = now
            }
            partial.save(remotePath, size, mtime, currentState())
        }
        partial.dataFile.parentFile?.mkdirs()
        val pending = ranges.withIndex().filter { (_, r) -> r.done < r.end }
        try {
            RandomAccessFile(partial.dataFile, "rw").use { raf ->
                if (raf.length() != size) raf.setLength(size)
                saveThrottled(force = true)
                val timeoutMs = sftp.sftpEngine.timeoutMs
                SftpPipelinedDownloader.downloadRanges(
                    fileSize = size,
                    settings = settings,
                    sink = FileChannelOffsetSink(raf.channel),
                    sourceForRange = { SshjAsyncReadSource(remoteFile = sftp.open(remotePath), timeoutMs = timeoutMs) },
                    onBytesTransferred = { transferred -> onBytesTransferred(already + transferred) },
                    isCancelled = isCancelled,
                    waitTimeoutMs = timeoutMs.toLong().coerceAtLeast(1L),
                    ranges = pending.map { (_, r) -> SftpByteRange(r.done, r.end) },
                    onRangeProgress = { pendingIndex, contiguousEnd ->
                        done.set(pending[pendingIndex].index, contiguousEnd)
                        saveThrottled(force = false)
                    }
                )
            }
        } catch (error: Throwable) {
            if (error is SftpDownloadCancelledException || isCancelled()) {
                partial.delete()
            } else {
                saveThrottled(force = true)
            }
            throw error
        }
        return already
    }
}
