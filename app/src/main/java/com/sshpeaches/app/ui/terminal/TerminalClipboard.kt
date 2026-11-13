package com.majordaftapps.sshpeaches.app.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock

/**
 * Copies from the terminal: marked sensitive (Android hides the preview) and cleared from the clipboard after
 * [CLEAR_AFTER_MS], as long as it's still our copy. Only a clip we can see is ours gets cleared: Android 10 and later
 * hide the clipboard from apps in the background, so a check that falls due then waits until the app's window has
 * focus again ([onWindowFocusChanged]), rather than risk wiping something copied elsewhere since. Nothing polls
 * meanwhile.
 */
object TerminalClipboard {
    const val CLEAR_AFTER_MS = 60_000L
    internal const val LABEL = "SSHPeaches terminal"
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    // AOSP SystemUI honors this only on emulators, where the clipboard overlay otherwise covers instrumentation.
    private const val EXTRA_SUPPRESS_OVERLAY = "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY"

    /** [timestamp] is null when the clip couldn't be read back; the label alone then says it's ours. */
    private class Pending(val timestamp: Long?, val copiedAtMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Pending? = null
    private var appContext: Context? = null
    private var windowFocused = false

    /** Whether a check is waiting on the timer; false while idle or waiting for the window's focus. */
    @Volatile
    internal var checkScheduledForTests = false
        private set

    private fun checkIn(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        checkScheduledForTests = true
        handler.postDelayed({
            checkScheduledForTests = false
            clearIfStillOurs()
        }, delayMs)
    }

    /** Shortens the wait for tests. */
    @Volatile
    internal var clearAfterMsForTests: Long? = null

    private val clearAfterMs get() = clearAfterMsForTests ?: CLEAR_AFTER_MS

    fun copy(context: Context, text: CharSequence) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(LABEL, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
                putBoolean(EXTRA_SUPPRESS_OVERLAY, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        // The app is in front while copying, so it can normally read back the timestamp Android gave this clip.
        val timestamp = clipboard.primaryClipDescription?.takeIf { it.label == LABEL }?.timestamp
        appContext = context.applicationContext
        pending = Pending(timestamp, SystemClock.elapsedRealtime())
        checkIn(clearAfterMs)
    }

    /** From the activity: the clipboard is readable only while the app's window has focus. */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        windowFocused = hasFocus
        if (hasFocus) clearIfStillOurs()
    }

    private fun clearIfStillOurs() {
        val due = pending ?: return
        val context = appContext ?: return
        val left = clearAfterMs - (SystemClock.elapsedRealtime() - due.copiedAtMs)
        if (left > 0) {
            checkIn(left)
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        // Null while the app can't see the clipboard (Android 10+, without window focus): the check is made again
        // when the focus comes back. Null with focus means the clipboard is empty: nothing left to clear.
        val description = clipboard.primaryClipDescription ?: run {
            if (windowFocused) pending = null
            return
        }
        pending = null
        if (description.label == LABEL && (due.timestamp == null || description.timestamp == due.timestamp)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
