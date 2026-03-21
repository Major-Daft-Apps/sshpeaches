package com.majordaftapps.sshpeaches.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import org.json.JSONObject
import java.util.Base64

fun encodePortForwardPayload(forward: PortForward): String {
    val json = JSONObject().apply {
        put("id", forward.id)
        put("label", forward.label)
        forward.group?.let { put("group", it) }
        put("createdEpochMillis", forward.createdEpochMillis ?: JSONObject.NULL)
        put("updatedEpochMillis", forward.updatedEpochMillis ?: JSONObject.NULL)
        put("lastUsedEpochMillis", forward.lastUsedEpochMillis ?: JSONObject.NULL)
        put("type", forward.type.name)
        put("bind", forward.sourceHost)
        put("srcPort", forward.sourcePort)
        put("dstHost", forward.destinationHost)
        put("dstPort", forward.destinationPort)
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}

fun generateForwardQr(forward: PortForward): Bitmap? {
    val payload = encodePortForwardPayload(forward)
    return runCatching {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}
