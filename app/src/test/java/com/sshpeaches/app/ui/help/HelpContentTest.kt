package com.majordaftapps.sshpeaches.app.ui.help

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {

    /** Screen labels the help text tells people to look for. Each must still exist in the app. */
    private val referencedLabels = listOf(
        "Quick Connect", "Host / IP", "Port", "Username", "Authentication", "Identity key",
        "Generate keypair", "Install Key To Host", "Clear stored host key", "Import hosts", "OpenSSH config",
        "Use volume buttons to adjust font size", "Run shells in background", "Background connection timeout",
        "Local port", "Destination host", "Destination port", "Associated host", "Enable now",
        "Export", "Import", "Wi-Fi", "QR code", "File", "Share",
        "Include passwords and private keys", "Export passphrase", "Startup snippet", "Terminal profile",
        "Font Size", "Mosh server command", "Swipe for arrow keys", "Insert password", "Change theme", "Find",
        "Snippets", "Reset", "Keyboard Editor", "Theme Editor", "Port Forwards", "Identities",
        "Reconnect automatically", "Attach to tmux", "Edit", "Permissions", "Continue"
    )

    private val helpText = (helpTopics.flatMap { listOf(it.question) + it.steps } +
        listOf(
            com.majordaftapps.sshpeaches.app.service.ConnectionFailureKind.entries.toList(),
            listOf(null)
        ).flatten().flatMap { kind ->
            connectionFailureHelp(kind, "192.168.1.2", 22, "alice", isSavedHost = true).let { listOf(it.title) + it.tips }
        }).joinToString("\n")

    private val appSource: String by lazy {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        root.walk()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "help" }
            .joinToString("\n") { it.readText() }
    }

    @Test
    fun everyReferencedLabelIsUsedByHelpAndStillExistsInTheApp() {
        referencedLabels.forEach { label ->
            assertTrue("help no longer mentions \"$label\"; drop it from this list", label in helpText)
            assertTrue("help mentions \"$label\" but no screen shows it", "\"$label\"" in appSource)
        }
    }

    @Test
    fun topicsHaveUniqueIdsAndShortSteps() {
        assertEquals(helpTopics.size, helpTopics.map { it.id }.toSet().size)
        helpTopics.forEach { topic ->
            assertTrue(topic.id, topic.question.endsWith("?"))
            assertTrue(topic.id, topic.steps.size in 2..4)
        }
    }

    @Test
    fun searchMatchesQuestionsStepsAndKeywords() {
        fun ids(query: String) = helpTopics.filter { it.matches(query) }.map { it.id }
        assertEquals(helpTopics.size, ids("  ").size)
        assertTrue("key-login" in ids("authorized_keys"))
        assertTrue("copy-paste" in ids("PASTE"))
        assertTrue("port-forward" in ids("tunnel postgres"))
        assertTrue(ids("zzz-nothing").isEmpty())
    }
}
