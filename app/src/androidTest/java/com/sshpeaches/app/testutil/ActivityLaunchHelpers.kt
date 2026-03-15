package com.majordaftapps.sshpeaches.app.testutil

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry

fun launchMainActivityThroughFramework(intent: Intent) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val launchIntent = Intent(intent).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
    }
    instrumentation.targetContext.startActivity(launchIntent)
    instrumentation.waitForIdleSync()
}
