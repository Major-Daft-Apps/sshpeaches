package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import com.majordaftapps.sshpeaches.app.data.model.asImported
import com.majordaftapps.sshpeaches.app.data.model.stillNeedsReviewWhen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferImportPolicyTest {

    private val saved = HostConnection(
        id = "prod",
        name = "prod",
        host = "prod.example.com",
        port = 22,
        username = "deploy",
        preferredAuth = AuthMethod.PASSWORD,
        hasPassword = true
    )

    @Test
    fun aHostThatReusesAnIdForAnotherServerIsAddedNotOverwritten() {
        assertEquals("new", importHostId("prod", "attacker.example", 22, "deploy", listOf(saved)) { "new" })
        assertEquals("new", importHostId("prod", "prod.example.com", 2222, "deploy", listOf(saved)) { "new" })
        assertEquals("new", importHostId("prod", "prod.example.com", 22, "root", listOf(saved)) { "new" })
    }

    @Test
    fun reImportingYourOwnHostUpdatesIt() {
        assertEquals("prod", importHostId("prod", "PROD.example.com ", 22, "deploy", listOf(saved)) { "new" })
        assertEquals("other", importHostId("other", "x", 22, "u", listOf(saved)) { "new" })
        assertEquals("new", importHostId("  ", "x", 22, "u", listOf(saved)) { "new" })
    }

    @Test
    fun importedItemsNeverTakeAnIdInUse() {
        assertEquals("new", importItemId("snippet-1", setOf("snippet-1")) { "new" })
        assertEquals("snippet-2", importItemId("snippet-2", setOf("snippet-1")) { "new" })
        assertEquals("new", importItemId("", emptySet()) { "new" })
    }

    @Test
    fun copiesAreMarkedSoTheyDontPassForYourOwn() {
        assertEquals("deploy", importedItemName("deploy", listOf("backup")))
        assertEquals("deploy (imported)", importedItemName("deploy", listOf("Deploy")))
        assertEquals("deploy (imported 2)", importedItemName("deploy", listOf("deploy", "deploy (imported)")))
    }

    @Test
    fun summaryCountsWhatTheExportHolds() {
        val summary = summarizeTransferPayload(
            JSONObject(
                """{"v":2,"hosts":[{"id":"a"},{"id":"b","pwdPayload":"x"}],"identities":[{"fingerprint":"f"}],
                "snippets":[{}],"portForwards":[],"settings":{"keyboardLayout":[],"terminalProfiles":[{},{}]}}"""
            )
        )
        assertEquals(2, summary.hosts)
        assertEquals(1, summary.identities)
        assertEquals(1, summary.snippets)
        assertEquals(0, summary.portForwards)
        assertEquals(2, summary.terminalProfiles)
        assertTrue(summary.hasSettings)
        assertTrue(summary.hasKeyboardLayout)
        assertTrue(summary.hasProtectedSecrets)

        val bare = summarizeTransferPayload(JSONObject("""{"v":2,"identities":[{"fingerprint":"f"}]}"""))
        assertFalse(bare.hasSettings)
        assertFalse(bare.hasKeyboardLayout)
        assertFalse(bare.hasProtectedSecrets)
    }

    @Test
    fun summaryCountsTheRemoteForwardsApart() {
        val summary = summarizeTransferPayload(
            JSONObject(
                """{"v":2,"portForwards":[{"type":"LOCAL"},{"type":"REMOTE"},{"type":"DYNAMIC"},{"type":"REMOTE"},{}]}"""
            )
        )
        assertEquals(5, summary.portForwards)
        assertEquals(2, summary.remoteForwards)
    }

    @Test
    fun anImportedRemoteForwardIsSwitchedOffAndMarkedForReviewWhateverTheImportSaid() {
        fun forward(type: PortForwardType, enabled: Boolean) = PortForward(
            id = "f", label = "f", type = type, sourcePort = 8080, destinationHost = "10.0.0.5", destinationPort = 80,
            enabled = enabled, needsReview = false
        )
        val remote = forward(PortForwardType.REMOTE, enabled = true).asImported()
        assertFalse(remote.enabled)
        assertTrue(remote.needsReview)
        // The others keep what they came with, and never carry the mark (not even when the import claims it).
        val local = forward(PortForwardType.LOCAL, enabled = true).copy(needsReview = true).asImported()
        assertTrue(local.enabled)
        assertFalse(local.needsReview)
        assertFalse(forward(PortForwardType.DYNAMIC, enabled = false).asImported().needsReview)
    }

    @Test
    fun theMarkStaysUntilTheUserTurnsTheForwardOn() {
        val marked = PortForward(id = "f", label = "f", type = PortForwardType.REMOTE, sourcePort = 1, needsReview = true)
        assertTrue(marked.stillNeedsReviewWhen(enabled = false))
        assertFalse(marked.stillNeedsReviewWhen(enabled = true))
        assertFalse(marked.copy(needsReview = false).stillNeedsReviewWhen(enabled = false))
    }
}
