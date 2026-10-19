package com.majordaftapps.sshpeaches.app.ui.components

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
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
}
