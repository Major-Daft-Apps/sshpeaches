package com.majordaftapps.sshpeaches.app.sftp

/**
 * Client-side SFTP transfer knobs. Names match the requested CLI-shaped settings:
 * `--sftp-read-size`, `--sftp-max-requests`, `--parallel-downloads`, `--sftp-fast`.
 * [parallelDownloads] is the number of connections one transfer uses, like aria2c's `-x`;
 * [sftpMaxRequests] is the number of reads each of them keeps in flight.
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
        // OpenSSH's sftp-server sends at most 255 KiB per read; asking for 256 KiB made every reply short and
        // cost a second request for the last 1 KiB.
        const val DEFAULT_SFTP_READ_SIZE = 261_120
        const val DEFAULT_SFTP_MAX_REQUESTS = 32
        const val DEFAULT_PARALLEL_DOWNLOADS = 4
        const val MIN_SFTP_READ_SIZE = 1
        const val MAX_SFTP_READ_SIZE = 261_120
        const val MIN_SFTP_MAX_REQUESTS = 1
        const val MAX_SFTP_MAX_REQUESTS = 256
        const val MIN_PARALLEL_DOWNLOADS = 1
        const val MAX_PARALLEL_DOWNLOADS = 16

        /** Bytes each upload request carries: what every SFTP server accepts, and enough to keep it busy. */
        const val UPLOAD_WRITE_SIZE = 64 * 1024

        /** Reads or writes one transfer connection keeps in flight, at most: about what OpenSSH's window allows. */
        const val MAX_IN_FLIGHT_BYTES_PER_CONNECTION = 8L shl 20

        /** In flight on the session's own connection, when transfers have to share it with browsing. */
        const val SHARED_CONNECTION_IN_FLIGHT_BYTES = 2L shl 20

        val FAST: SftpTransferSettings = SftpTransferSettings(
            sftpReadSize = DEFAULT_SFTP_READ_SIZE,
            sftpMaxRequests = DEFAULT_SFTP_MAX_REQUESTS,
            parallelDownloads = DEFAULT_PARALLEL_DOWNLOADS
        )

        fun fastPreset(): SftpTransferSettings = FAST
    }
}
