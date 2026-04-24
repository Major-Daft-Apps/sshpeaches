package com.majordaftapps.sshpeaches.app.ui.qr

import com.majordaftapps.sshpeaches.app.ui.adaptive.ShellLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QrScanOptionsTest {

    @Test
    fun wideOptionsUseAdaptiveCaptureWithoutPortraitLock() {
        val options = buildQrScanOptions(
            shellLayoutMode = ShellLayoutMode.WIDE,
            prompt = "Scan Chromebook QR"
        )

        assertEquals(AdaptiveCaptureActivity::class.java, options.captureActivity)
        assertEquals(false, options.moreExtras[ORIENTATION_LOCKED_EXTRA])
    }

    @Test
    fun compactOptionsPreservePortraitCaptureLock() {
        val options = buildQrScanOptions(
            shellLayoutMode = ShellLayoutMode.COMPACT,
            prompt = "Scan phone QR"
        )

        assertEquals(PortraitCaptureActivity::class.java, options.captureActivity)
        assertEquals(true, options.moreExtras[ORIENTATION_LOCKED_EXTRA])
    }

    private companion object {
        const val ORIENTATION_LOCKED_EXTRA = "SCAN_ORIENTATION_LOCKED"
    }
}
