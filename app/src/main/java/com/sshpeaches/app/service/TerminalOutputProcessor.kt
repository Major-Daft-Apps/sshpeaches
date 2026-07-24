package com.majordaftapps.sshpeaches.app.service

import kotlinx.coroutines.CancellationException

internal class TerminalOutputProcessor(
    private val appendToTerminal: suspend (ByteArray) -> Unit,
    private val onParserFailure: (Exception) -> Unit
) {
    suspend fun process(payload: ByteArray) {
        if (payload.isEmpty()) return
        try {
            appendToTerminal(payload)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onParserFailure(error)
        }
    }
}
