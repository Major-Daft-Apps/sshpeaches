package com.majordaftapps.sshpeaches.app.data.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveLoginResponderTest {

    @Test
    fun answersTheFirstHiddenPasswordQuestionWithThePassword() {
        val asked = mutableListOf<LoginChallenge>()
        val responder = InteractiveLoginResponder("secret") { asked += it; "again" }
        responder.init(null, "", "")

        assertArrayEquals("secret".toCharArray(), responder.getResponse("(tester@box) Password: ", false))
        // A second password question means the first was wrong; the person answers it.
        assertArrayEquals("again".toCharArray(), responder.getResponse("Password: ", false))
        assertEquals(listOf("Password:"), asked.map { it.prompt })
    }

    @Test
    fun asksThePersonForOtherQuestionsWithTheServersInstruction() {
        val asked = mutableListOf<LoginChallenge>()
        val responder = InteractiveLoginResponder("secret") { asked += it; "123456" }
        responder.init(null, "Duo", "Enter the code from your app")

        assertArrayEquals("123456".toCharArray(), responder.getResponse("Verification code: ", true))
        assertEquals(LoginChallenge("Verification code:", echo = true, instruction = "Duo\nEnter the code from your app"), asked.single())
    }

    @Test
    fun withoutAPersonOtherQuestionsGetAnEmptyAnswer() {
        val responder = InteractiveLoginResponder("secret", askUser = null)

        assertEquals(0, responder.getResponse("Verification code: ", true).size)
        assertFalse(responder.canceled)
    }

    @Test
    fun dismissingAQuestionCancels() {
        val responder = InteractiveLoginResponder("secret") { null }

        assertEquals(0, responder.getResponse("Verification code: ", true).size)
        assertTrue(responder.canceled)
    }
}
