package com.majordaftapps.sshpeaches.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.data.model.Identity
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import org.json.JSONObject
import java.util.Base64

fun encodeIdentityPayload(identity: Identity, encryptedKeyPayload: String?): String {
    val json = JSONObject().apply {
        put("id", identity.id)
        put("label", identity.label)
        put("fingerprint", identity.fingerprint)
        put("hasKey", identity.hasPrivateKey)
        identity.group?.let { put("group", it) }
        identity.username?.let { put("user", it) }
        put("createdEpochMillis", identity.createdEpochMillis)
        put("updatedEpochMillis", identity.updatedEpochMillis ?: JSONObject.NULL)
        put("lastUsedEpochMillis", identity.lastUsedEpochMillis ?: JSONObject.NULL)
        encryptedKeyPayload?.let { put("keyPayload", it) }
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}

fun generateIdentityQr(identity: Identity, passphrase: String?): Bitmap? {
    val encrypted = if (identity.hasPrivateKey) {
        if (passphrase.isNullOrBlank()) return null
        SecurityManager.exportIdentityKeyPayload(identity.id, passphrase) ?: return null
    } else null
    val payload = encodeIdentityPayload(identity, encrypted)
    return runCatching {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bmp
    }.getOrNull()
}
