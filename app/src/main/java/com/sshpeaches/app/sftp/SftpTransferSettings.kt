package com.majordaftapps.sshpeaches.app.sftp

/**
 * Client-side SFTP download knobs. Names match the requested CLI-shaped settings:
 * `--sftp-read-size`, `--sftp-max-requests`, `--parallel-downloads`, `--sftp-fast`.
 */
data class SftpTransferSettings(
    val sftpReadSize: Int = DEFAULT_SFTP_READ_SIZE,
    val sftpMaxRequests: Int = DEFAULT_SFTP_MAX_REQUESTS,
    val parallelDownloads: Int = DEFAULT_PARALLEL_DOWNLOADS
) {
    val sftpFast: Boolean
        get() = sftpReadSize == DEFAULT_SFTP_READ_SIZE &&
            sftpMaxRequests == DEFAULT_SFTP_MAX_REQUESTS &&
            parallelDownloads == DEFAULT_PARALLEL_DOWNLOADS

    fun sanitized(): SftpTransferSettings = copy(
        sftpReadSize = sftpReadSize.coerceIn(MIN_SFTP_READ_SIZE, MAX_SFTP_READ_SIZE),
        sftpMaxRequests = sftpMaxRequests.coerceIn(MIN_SFTP_MAX_REQUESTS, MAX_SFTP_MAX_REQUESTS),
        parallelDownloads = parallelDownloads.coerceIn(MIN_PARALLEL_DOWNLOADS, MAX_PARALLEL_DOWNLOADS)
    )

    fun applyFastPreset(): SftpTransferSettings = FAST

    companion object {
        const val DEFAULT_SFTP_READ_SIZE = 262_144
        const val DEFAULT_SFTP_MAX_REQUESTS = 128
        const val DEFAULT_PARALLEL_DOWNLOADS = 4
        const val MIN_SFTP_READ_SIZE = 1
        const val MAX_SFTP_READ_SIZE = 262_144
        const val MIN_SFTP_MAX_REQUESTS = 1
        const val MAX_SFTP_MAX_REQUESTS = 256
        const val MIN_PARALLEL_DOWNLOADS = 1
        const val MAX_PARALLEL_DOWNLOADS = 16

        val FAST: SftpTransferSettings = SftpTransferSettings(
            sftpReadSize = DEFAULT_SFTP_READ_SIZE,
            sftpMaxRequests = DEFAULT_SFTP_MAX_REQUESTS,
            parallelDownloads = DEFAULT_PARALLEL_DOWNLOADS
        )

        fun fastPreset(): SftpTransferSettings = FAST
    }
}
