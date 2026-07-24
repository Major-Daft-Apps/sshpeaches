package com.majordaftapps.sshpeaches.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalOutputProcessorTest {

    @Test
    fun parserFailureDoesNotStopLaterOutput() = runBlocking {
        val rendered = mutableListOf<String>()
        val failures = mutableListOf<Exception>()
        var attempts = 0
        val processor = TerminalOutputProcessor(
            appendToTerminal = { payload ->
                attempts += 1
                if (attempts == 1) error("simulated emulator failure")
                rendered += payload.decodeToString()
            },
            onParserFailure = failures::add
        )

        processor.process("first".encodeToByteArray())
        processor.process("second".encodeToByteArray())

        assertEquals(listOf("second"), rendered)
        assertEquals(listOf("simulated emulator failure"), failures.map { it.message })
    }

    @Test
    fun cancellationStillStopsProcessing() {
        val processor = TerminalOutputProcessor(
            appendToTerminal = { throw CancellationException("session stopped") },
            onParserFailure = { error("Cancellation must not be reported as a parser failure") }
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                processor.process("output".encodeToByteArray())
            }
        }
    }
}
