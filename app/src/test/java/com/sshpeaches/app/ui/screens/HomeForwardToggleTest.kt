package com.majordaftapps.sshpeaches.app.ui.screens

import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeForwardToggleTest {
    private fun forward(enabled: Boolean, needsReview: Boolean) = PortForward(
        id = "f", label = "f", type = PortForwardType.REMOTE, sourcePort = 1, enabled = enabled, needsReview = needsReview
    )

    @Test
    fun homeOffersToReviewAnImportedRemoteForwardRatherThanToEnableIt() {
        assertEquals("Review (imported, off)", forward(enabled = false, needsReview = true).homeToggleLabel())
        assertEquals("Enable", forward(enabled = false, needsReview = false).homeToggleLabel())
        assertEquals("Disable", forward(enabled = true, needsReview = false).homeToggleLabel())
    }
}
