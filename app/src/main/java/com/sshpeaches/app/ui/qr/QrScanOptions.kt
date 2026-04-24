package com.majordaftapps.sshpeaches.app.ui.qr

import com.journeyapps.barcodescanner.ScanOptions
import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode

fun buildQrScanOptions(
    shellLayoutMode: ShellLayoutMode,
    prompt: String
): ScanOptions {
    return ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt(prompt)
        setBeepEnabled(false)
        if (shellLayoutMode == ShellLayoutMode.WIDE) {
            setCaptureActivity(AdaptiveCaptureActivity::class.java)
            setOrientationLocked(false)
        } else {
            setCaptureActivity(PortraitCaptureActivity::class.java)
            setOrientationLocked(true)
        }
    }
}
