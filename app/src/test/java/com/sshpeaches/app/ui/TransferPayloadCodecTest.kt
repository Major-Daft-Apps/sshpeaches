package com.majordaftapps.sshpeaches.app.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPayloadCodecTest {

    @Test
    fun decodeTransferPayloadEnvelope_acceptsLegacyUncompressedPayloads() {
        val payload = """{"v":2,"hosts":[{"id":"host-1"}]}"""
        val legacyPayload = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))

        assertEquals(payload, decodeTransferPayloadEnvelope(legacyPayload))
    }

    @Test
    fun encodeTransferPayloadEnvelope_compressesLargePayloadsAndRoundTrips() {
        val payload = buildString {
            append("""{"v":2,"notes":[""")
            repeat(18_000) { append('x') }
            append(""""]}""")
        }
        val legacyPayload = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val encodedPayload = encodeTransferPayloadEnvelope(payload)

        assertEquals(payload, decodeTransferPayloadEnvelope(encodedPayload))
        assertTrue(encodedPayload.length < legacyPayload.length)
        assertTrue(
            runCatching {
                QRCodeWriter().encode(encodedPayload, BarcodeFormat.QR_CODE, 640, 640)
            }.isSuccess
        )
        assertTrue(
            runCatching {
                QRCodeWriter().encode(legacyPayload, BarcodeFormat.QR_CODE, 640, 640)
            }.isFailure
        )
    }
}
