package com.sshpeaches.app.ui.logging

import android.util.Log
import com.sshpeaches.app.BuildConfig
import com.sshpeaches.app.ui.state.AppUiState

object UiDebugLog {
    private const val TAG = "SSHPeachesUI"

    fun action(name: String, details: String? = null) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, buildLine("ACTION $name", details))
    }

    fun result(name: String, success: Boolean, details: String? = null) {
        if (!BuildConfig.DEBUG) return
        val status = if (success) "OK" else "FAIL"
        Log.d(TAG, buildLine("RESULT $name -> $status", details))
    }

    fun state(origin: String, state: AppUiState) {
        if (!BuildConfig.DEBUG) return
        val filledSlots = state.keyboardSlots.count { it.isNotBlank() }
        val summary = "hosts=${state.hosts.size}, identities=${state.identities.size}, " +
            "forwards=${state.portForwards.size}, snippets=${state.snippets.size}, " +
            "sort=${state.sortMode}, theme=${state.themeMode}, locked=${state.isLocked}, " +
            "pinConfigured=${state.pinConfigured}, background=${state.allowBackgroundSessions}, " +
            "biometric=${state.biometricLockEnabled}, diagnostics=${state.diagnosticsLoggingEnabled}, " +
            "keyboardFilled=$filledSlots/${state.keyboardSlots.size}"
        Log.d(TAG, "STATE $origin | $summary")
    }

    fun error(name: String, throwable: Throwable, details: String? = null) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, buildLine("ERROR $name", details), throwable)
    }

    private fun buildLine(prefix: String, details: String?): String {
        if (details.isNullOrBlank()) return prefix
        return "$prefix | $details"
    }
}
