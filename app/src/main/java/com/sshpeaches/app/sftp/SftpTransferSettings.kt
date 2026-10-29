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
    val parallelDownloads: Int = DEFAULT_PARALLEL_DOWNLOADS,
    /** Boost: browse, transfer and run the terminal through a helper the app runs on the server, over QUIC (UDP). */
    val boost: Boolean = false,
    /** Whether Boost also carries the terminal (SSH mode), not only the file browser. */
    val boostTerminal: Boolean = true,
    /** Lets Boost use TLS over TCP when UDP doesn't get through. */
    val boostTcpFallback: Boolean = true,
    /** The server ports Boost's helper listens on: "60000-61000", "4500", or blank for any port. */
    val boostPorts: String = DEFAULT_BOOST_PORTS
) {
    /** [boostPorts] as a range, or null for any port. */
    val boostPortRange: IntRange?
        get() = parseBoostPorts(boostPorts)

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

        /** mosh-server's range, so servers whose firewall lets mosh through let Boost through too. */
        const val DEFAULT_BOOST_PORTS = "60000-61000"

        private val BOOST_PORTS = Regex("""\s*(\d{1,5})\s*(?:-\s*(\d{1,5})\s*)?""")

        /** Whether [text] is a port, a range of ports ("low-high", 1024-65535), or blank (any port). */
        fun isValidBoostPorts(text: String): Boolean = text.isBlank() || parseBoostPorts(text) != null

        /** The range [text] names, or null when it's blank or not a valid one. */
        fun parseBoostPorts(text: String): IntRange? {
            val match = BOOST_PORTS.matchEntire(text) ?: return null
            val low = match.groupValues[1].toInt()
            val high = match.groupValues[2].ifEmpty { match.groupValues[1] }.toInt()
            return if (low in 1024..high && high <= 65535) low..high else null
        }
    }
}
