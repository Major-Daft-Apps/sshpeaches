package com.majordaftapps.sshpeaches.app.fuzz.net

import com.majordaftapps.sshpeaches.app.service.PROTECTED_REMOTE_SYSTEM_ROOTS
import com.majordaftapps.sshpeaches.app.service.SessionService.RemoteDirectoryEntry
import com.majordaftapps.sshpeaches.app.service.loadSftpDirectory
import com.majordaftapps.sshpeaches.app.service.normalizeRemotePathLexically
import com.majordaftapps.sshpeaches.app.service.validateRemotePathMutation
import com.majordaftapps.sshpeaches.app.ui.screens.RemoteFileSortField
import com.majordaftapps.sshpeaches.app.ui.screens.formatMode
import com.majordaftapps.sshpeaches.app.ui.screens.parseMode
import com.majordaftapps.sshpeaches.app.ui.screens.permissionSummaryToMode
import com.majordaftapps.sshpeaches.app.ui.screens.remoteBreadcrumbs
import com.majordaftapps.sshpeaches.app.ui.screens.sortRemoteDirectoryEntries
import kotlin.random.Random
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.PathComponents
import net.schmizz.sshj.sftp.RemoteResourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Hostile remote names and attributes through listing, sorting, breadcrumbs and path policy. */
class RemoteFileNameFuzzTest {

    private val types = FileMode.Type.values().toList()

    private fun attributes(random: Random): FileAttributes {
        val builder = FileAttributes.Builder()
        if (random.nextInt(5) != 0) {
            builder.withType(types.random(random)).withPermissions(random.nextInt(0, 0x1000))
        }
        if (random.nextBoolean()) builder.withSize(if (random.nextBoolean()) random.nextLong(0, Long.MAX_VALUE) else random.nextLong(0, 1 shl 20))
        if (random.nextBoolean()) builder.withAtimeMtime(random.nextLong(0, 0xFFFFFFFFL), random.nextLong(0, 0xFFFFFFFFL))
        return builder.build()
    }

    private fun entry(random: Random): RemoteDirectoryEntry = RemoteDirectoryEntry(
        name = Fuzz.string(random, 24),
        isDirectory = random.nextBoolean(),
        sizeBytes = when (random.nextInt(4)) { 0 -> 0L; 1 -> -1L; 2 -> Long.MAX_VALUE; else -> random.nextLong(0, 4096) },
        modifiedAtEpochMillis = when (random.nextInt(4)) { 0 -> null; 1 -> Long.MIN_VALUE; 2 -> Long.MAX_VALUE; else -> random.nextLong(0, 4096) }
    )

    @Test
    fun listingAcceptsAnyNameAndAttributesAndDropsOnlyDotEntries() {
        val random = Random(Fuzz.SEED + 20)
        repeat(300) { iteration ->
            val parent = if (random.nextBoolean()) "/srv/files" else Fuzz.string(random, 16)
            val resources = List(random.nextInt(0, 60)) {
                RemoteResourceInfo(PathComponents(parent, Fuzz.string(random, 32), "/"), attributes(random))
            }
            val loaded = try {
                Fuzz.withinMillis(5_000, "loadSftpDirectory#$iteration") {
                    loadSftpDirectory(
                        requestedPath = Fuzz.string(random, 16),
                        resolveSymbolicLinks = random.nextBoolean(),
                        canonicalize = { path -> if (random.nextInt(4) == 0) error("no such file") else path },
                        listDirectory = { resources },
                        stat = { if (random.nextBoolean()) throw java.io.IOException("stat failed") else attributes(random) }
                    )
                }
            } catch (error: AssertionError) {
                throw error
            } catch (error: Throwable) {
                fail("iteration $iteration: ${error.javaClass.name}: ${error.message}")
                return
            }
            val expected = resources.count { it.name != "." && it.name != ".." }
            assertEquals(expected, loaded.entries.size)
            assertFalse(loaded.entries.any { it.name == "." || it.name == ".." })
            loaded.entries.forEach { entry ->
                if (entry.permissionSummary.isNotEmpty()) assertEquals(10, entry.permissionSummary.length)
                assertFalse(entry.isBrokenLink && !entry.isSymbolicLink)
            }
        }
    }

    @Test
    fun sortingIsConsistentForEveryFieldAndDirection() {
        val random = Random(Fuzz.SEED + 21)
        repeat(400) { iteration ->
            val base = List(random.nextInt(0, 200)) { entry(random) }
            // Duplicate a few entries so equal keys are exercised.
            val entries = (base + base.shuffled(random).take(random.nextInt(0, 10))).shuffled(random)
            for (field in RemoteFileSortField.values()) for (ascending in listOf(true, false)) for (foldersFirst in listOf(true, false)) {
                val sorted = try {
                    sortRemoteDirectoryEntries(entries, field, ascending, foldersFirst)
                } catch (error: IllegalArgumentException) {
                    fail("iteration $iteration $field asc=$ascending folders=$foldersFirst: ${error.message}")
                    return
                }
                assertEquals(entries.size, sorted.size)
                val again = sortRemoteDirectoryEntries(entries.shuffled(random), field, ascending, foldersFirst)
                fun key(entry: RemoteDirectoryEntry): List<Any?> = listOf(
                    if (foldersFirst) entry.isDirectory else null,
                    when (field) {
                        RemoteFileSortField.NAME -> null
                        RemoteFileSortField.SIZE -> entry.sizeBytes
                        RemoteFileSortField.DATE -> entry.modifiedAtEpochMillis ?: Long.MIN_VALUE
                    },
                    entry.name
                )
                assertEquals(
                    "sort keys must not depend on input order ($field asc=$ascending folders=$foldersFirst)",
                    sorted.map(::key),
                    again.map(::key)
                )
                if (foldersFirst) {
                    val firstFile = sorted.indexOfFirst { !it.isDirectory }
                    if (firstFile >= 0) assertTrue(sorted.drop(firstFile).none { it.isDirectory })
                }
            }
        }
    }

    @Test
    fun breadcrumbsAreAlwaysPrefixesOfThePath() {
        val random = Random(Fuzz.SEED + 22)
        repeat(5_000) {
            val path = (if (random.nextBoolean()) "/" else "") + Fuzz.string(random, 40)
            val crumbs = remoteBreadcrumbs(path)
            if (!path.trim().startsWith("/")) {
                assertTrue(crumbs.isEmpty())
            } else {
                assertEquals("/" to "/", crumbs.first())
                crumbs.drop(1).forEach { (label, target) ->
                    assertFalse(label.isEmpty())
                    assertFalse(label.contains('/'))
                    assertTrue(target.startsWith("/") && target.endsWith(label))
                }
            }
        }
    }

    @Test
    fun permissionAndModeParsersNeverThrowAndRoundTrip() {
        val random = Random(Fuzz.SEED + 23)
        repeat(20_000) {
            val text = if (random.nextBoolean()) Fuzz.string(random, 12) else buildString {
                append("-dlbcps".random(random))
                repeat(9) { append("rwxsStT-?".random(random)) }
            }
            permissionSummaryToMode(text)?.let { mode -> assertTrue(mode in 0..0xFFF) }
            parseMode(text)?.let { mode -> assertEquals(mode, parseMode(formatMode(mode))) }
        }
        for (mode in 0..0xFFF) assertEquals(mode, parseMode(formatMode(mode)))
    }

    @Test
    fun lexicalNormalisationIsIdempotentAndNeverClimbsAboveRoot() {
        val random = Random(Fuzz.SEED + 24)
        repeat(20_000) {
            val path = buildString {
                if (random.nextBoolean()) append('/')
                repeat(random.nextInt(0, 8)) {
                    append(listOf("..", ".", "", "etc", "home", "a", "_", "x y", "..."). random(random)).append('/')
                }
                if (random.nextBoolean()) append(Fuzz.string(random, 8).trim())
            }
            val normalized = normalizeRemotePathLexically(path)
            // A last component ending in whitespace is trimmed on a second pass; that is harmless.
            if (normalized != normalized.trim()) return@repeat
            assertEquals("idempotent for ${path.quoted()}", normalized, normalizeRemotePathLexically(normalized))
            if (path.trim().startsWith("/")) {
                assertTrue(normalized.startsWith("/"))
                assertFalse("absolute path kept '..': ${path.quoted()} -> $normalized", normalized.split('/').contains(".."))
            }
        }
    }

    /** Any absolute spelling of a protected root must be refused for every mutating operation. */
    @Test
    fun protectedRootsCannotBeMutatedUnderAnyAbsoluteSpelling() {
        val random = Random(Fuzz.SEED + 25)
        val operations = listOf("delete", "delete_recursive", "rm", "rm_recursive", "delete_file", "move", "rename", "chmod", " DELETE ", "Rm")
        repeat(5_000) {
            val root = PROTECTED_REMOTE_SYSTEM_ROOTS.random(random)
            val spelled = buildString {
                append(listOf("", " ", "\t").random(random))
                append("/")
                repeat(random.nextInt(0, 3)) { append(listOf("/", "./", "x/../", "etc/../").random(random)) }
                append(root.removePrefix("/"))
                repeat(random.nextInt(0, 3)) { append(listOf("/", "/.", "/x/..", "//").random(random)) }
                append(listOf("", " ", "\n").random(random))
            }
            val operation = operations.random(random)
            val asDestination = operation.trim().lowercase() in setOf("move", "rename") && random.nextBoolean()
            val error = runCatching {
                if (asDestination) {
                    validateRemotePathMutation(operation, "/srv/data/file", spelled)
                } else {
                    validateRemotePathMutation(operation, spelled, if (operation.trim().lowercase() == "chmod") "644" else "/srv/data/x")
                }
            }.exceptionOrNull()
            assertNotNull("$operation accepted protected ${spelled.quoted()} (dest=$asDestination)", error)
            assertTrue(error is IllegalArgumentException)
        }
    }

    private fun String.quoted(): String = buildString {
        append('"')
        this@quoted.forEach { c -> if (c.code < 0x20 || c.code > 0x7e) append("\\u%04x".format(c.code)) else append(c) }
        append('"')
    }
}
