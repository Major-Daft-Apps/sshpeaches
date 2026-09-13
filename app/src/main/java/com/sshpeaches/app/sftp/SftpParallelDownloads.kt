package com.majordaftapps.sshpeaches.app.sftp

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import net.schmizz.sshj.sftp.SFTPClient

class SftpDownloadAdmission(
    maxParallel: Int
) {
    val maxParallel: Int = maxParallel.coerceIn(
        SftpTransferSettings.MIN_PARALLEL_DOWNLOADS,
        SftpTransferSettings.MAX_PARALLEL_DOWNLOADS
    )
    private val semaphore = Semaphore(this.maxParallel)
    private val inflight = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    val currentInflight: Int
        get() = inflight.get()

    val peakInflight: Int
        get() = peak.get()

    fun <T> withPermit(block: () -> T): T {
        semaphore.acquire()
        val now = inflight.incrementAndGet()
        peak.updateAndGet { current -> maxOf(current, now) }
        try {
            return block()
        } finally {
            inflight.decrementAndGet()
            semaphore.release()
        }
    }

    companion object {
        fun fromSettings(settings: SftpTransferSettings): SftpDownloadAdmission {
            return SftpDownloadAdmission(settings.sanitized().parallelDownloads)
        }
    }
}

data class SftpPipelinedJob(
    val fileSize: Long,
    val source: SftpAsyncReadSource,
    val sink: SftpOffsetSink,
    val isCancelled: () -> Boolean = { false }
)

data class SftpFileDownloadJob(
    val remotePath: String,
    val localFile: File
)

fun interface SftpFileInflightObserver {
    fun onInflightChanged(inflight: Int)
}

object SftpParallelDownloads {
    fun downloadAll(
        jobs: List<SftpPipelinedJob>,
        settings: SftpTransferSettings,
        admission: SftpDownloadAdmission = SftpDownloadAdmission.fromSettings(settings),
        observer: SftpFileInflightObserver = SftpFileInflightObserver { }
    ) {
        if (jobs.isEmpty()) return
        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = jobs.mapIndexed { index, job ->
            Thread({
                try {
                    admission.withPermit {
                        observer.onInflightChanged(admission.currentInflight)
                        SftpPipelinedDownloader.download(
                            fileSize = job.fileSize,
                            settings = settings,
                            source = job.source,
                            sink = job.sink,
                            isCancelled = job.isCancelled
                        )
                    }
                } catch (error: Throwable) {
                    errors.add(error)
                }
            }, "sftp-parallel-$index")
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        errors.firstOrNull()?.let { throw it }
    }

    fun downloadAllFromSftp(
        sftp: SFTPClient,
        jobs: List<SftpFileDownloadJob>,
        settings: SftpTransferSettings,
        admission: SftpDownloadAdmission = SftpDownloadAdmission.fromSettings(settings),
        observer: SftpFileInflightObserver = SftpFileInflightObserver { },
        onBytesTransferred: (SftpFileDownloadJob, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        if (jobs.isEmpty()) return
        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = jobs.mapIndexed { index, job ->
            Thread({
                try {
                    admission.withPermit {
                        observer.onInflightChanged(admission.currentInflight)
                        SftpPipelinedDownloader.downloadFromSftp(
                            sftp = sftp,
                            remotePath = job.remotePath,
                            localFile = job.localFile,
                            settings = settings,
                            onBytesTransferred = { transferred ->
                                onBytesTransferred(job, transferred)
                            },
                            isCancelled = isCancelled
                        )
                    }
                } catch (error: Throwable) {
                    errors.add(error)
                }
            }, "sftp-sftp-parallel-$index")
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        errors.firstOrNull()?.let { throw it }
    }
}
