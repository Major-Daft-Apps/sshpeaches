package com.majordaftapps.sshpeaches.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

fun encodeHostPayload(
    host: HostConnection,
    encryptedPasswordPayload: String?
): String {
    val json = JSONObject().apply {
        put("id", host.id)
        put("name", host.name)
        put("host", host.host)
        put("port", host.port)
        put("user", host.username)
        put("prefAuth", host.preferredAuth.name)
        put("mode", host.defaultMode.name)
        put("group", host.group ?: "")
        put("createdEpochMillis", host.createdEpochMillis ?: JSONObject.NULL)
        put("updatedEpochMillis", host.updatedEpochMillis ?: JSONObject.NULL)
        put("lastUsedEpochMillis", host.lastUsedEpochMillis ?: JSONObject.NULL)
        put("notes", host.notes)
        put("hasPassword", host.hasPassword)
        put("useMosh", host.useMosh)
        put("preferredIdentityId", host.preferredIdentityId ?: "")
        put("preferredForwardId", host.preferredForwardId ?: "")
        put("startupScript", host.startupScript)
        put("backgroundBehavior", host.backgroundBehavior.name)
        put("terminalProfileId", host.terminalProfileId ?: "")
        encryptedPasswordPayload?.let { put("pwdPayload", it) }
    }
    return Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}

/**
 * Builds the share QR off the main thread: with a passphrase it runs a 210k-iteration PBKDF2,
 * and rendering the bitmap touches every pixel.
 */
suspend fun generateHostQr(host: HostConnection, passphrase: String?): Bitmap? =
    withContext(Dispatchers.Default) { generateHostQrBlocking(host, passphrase) }

private fun generateHostQrBlocking(host: HostConnection, passphrase: String?): Bitmap? {
    val encrypted = if (host.hasPassword && !passphrase.isNullOrBlank()) {
        SecurityManager.exportHostPasswordPayload(host.id, passphrase) ?: return null
    } else {
        null
    }
    val payload = encodeHostPayload(host = host, encryptedPasswordPayload = encrypted)
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
