package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.service.loadSftpDirectory
import com.majordaftapps.sshpeaches.app.sftp.SftpPartialDownload
import com.majordaftapps.sshpeaches.app.sftp.SftpPipelinedDownloader
import com.majordaftapps.sshpeaches.app.sftp.SftpResumableDownloader
import com.majordaftapps.sshpeaches.app.sftp.SftpTransferSettings
import com.majordaftapps.sshpeaches.app.ui.screens.RemoteFileSortField
import com.majordaftapps.sshpeaches.app.ui.screens.sortRemoteDirectoryEntries
import java.io.File
import java.io.IOException
import kotlin.random.Random
import net.schmizz.sshj.SSHClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A real sshj client against an in-process server that lies about names, reads and disconnects. */
class HostileSftpServerFuzzTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sandbox: File
    private lateinit var server: HostileSftpServer
    private lateinit var client: SSHClient

    @Before
    fun setUp() {
        sandbox = temp.newFolder("sandbox")
        server = HostileSftpServer(sandbox, temp.newFile("hostkey"), temp.newFile("known_hosts"))
        client = server.connect()
    }

    @After
    fun tearDown() {
        runCatching { client.close() }
        server.close()
    }

    @Test
    fun hostileNamesInListingsNeverBreakListingOrSorting() {
        val random = Random(Fuzz.SEED + 50)
        val dir = File(sandbox, "dir").apply { mkdirs() }
        val realNames = List(40) { "f$it" }
        realNames.forEachIndexed { index, name ->
            if (index % 3 == 0) File(dir, name).mkdirs() else File(dir, name).writeText("x".repeat(index))
        }
        client.newSFTPClient().use { sftp ->
            repeat(15) { round ->
                server.listingNames = realNames.associateWith { Fuzz.string(random, 40) }
                val loaded = Fuzz.withinMillis(10_000, "list#$round") {
                    loadSftpDirectory(
                        requestedPath = "/dir",
                        resolveSymbolicLinks = true,
                        canonicalize = sftp::canonicalize,
                        listDirectory = sftp::ls,
                        stat = sftp::stat
                    )
                }
                val reported = server.listingNames.values.filter { it != "." && it != ".." }
                assertEquals("round $round", reported.size, loaded.entries.size)
                RemoteFileSortField.values().forEach { field ->
                    sortRemoteDirectoryEntries(loaded.entries, field, ascending = random.nextBoolean(), foldersFirst = random.nextBoolean())
                }
            }
        }
    }

    /**
     * Zero-length DATA replies are re-requested forever; through a real SFTP session the download
     * spins at network speed and never fails.
     */
    @Test
    fun emptyDataRepliesFailTheDownloadInsteadOfSpinning() {
        File(sandbox, "file.bin").writeBytes(ByteArray(64 * 1024) { it.toByte() })
        server.readBehaviour = HostileSftpServer.ReadBehaviour.EmptyData
        val local = File(temp.root, "download.bin")
        val sftp = client.newSFTPClient()
        try {
            val error = runCatching {
                Fuzz.withinMillis(8_000, "empty-data-download") {
                    SftpPipelinedDownloader.downloadFromSftp(sftp, "/file.bin", local, SftpTransferSettings(parallelDownloads = 1))
                }
            }.exceptionOrNull()
            assertTrue("expected an IOException, got ${error?.javaClass?.name}: ${error?.message?.lineSequence()?.firstOrNull()}", error is IOException)
        } finally {
            // Unblocks a spinning download thread.
            runCatching { sftp.close() }
        }
    }

    /** A file that ends before its stat size downloads "successfully", zero-padded to that size. */
    @Test
    fun earlyEofIsReportedInsteadOfSavingAZeroPaddedFile() {
        val content = ByteArray(64 * 1024) { (it % 251).toByte() }
        File(sandbox, "short.bin").writeBytes(content)
        server.readBehaviour = HostileSftpServer.ReadBehaviour.EofAt(10_000)
        val partial = SftpPartialDownload.forRemote(temp.newFolder("partials"), "fuzzer@127.0.0.1:22", "/short.bin")
        val outcome = client.newSFTPClient().use { sftp ->
            runCatching {
                Fuzz.withinMillis(15_000, "early-eof-download") {
                    SftpResumableDownloader.download(sftp, "/short.bin", partial, SftpTransferSettings.fastPreset())
                }
            }
        }
        if (outcome.isSuccess) {
            val saved = partial.dataFile.readBytes()
            val zeroTail = saved.drop(10_000).all { it == 0.toByte() }
            fail("download succeeded with ${saved.size} bytes; bytes after the 10000 the server sent are ${if (zeroTail) "all zero padding" else "garbage"}")
        }
    }
}
