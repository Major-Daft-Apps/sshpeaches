package com.majordaftapps.sshpeaches.app

import android.app.Activity
import com.majordaftapps.sshpeaches.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Asks Google Play for its in-app review sheet once, at a good moment: back on Home after a session, once someone
 * has connected [MIN_SESSIONS] times over [MIN_DAYS] days. Play decides whether the sheet actually shows (it has
 * its own quota), so this is asked at most once; Settings → Rate SSHPeaches opens the store page any time.
 */
object ReviewPrompt {
    const val MIN_SESSIONS = 5
    const val MIN_DAYS = 3

    fun shouldAsk(successfulSessions: Int, firstUseAtMillis: Long, nowMillis: Long, alreadyAsked: Boolean): Boolean =
        !alreadyAsked && firstUseAtMillis > 0L && successfulSessions >= MIN_SESSIONS &&
            nowMillis - firstUseAtMillis >= MIN_DAYS * 24L * 60 * 60 * 1000

    suspend fun maybeAsk(activity: Activity) {
        val sessions = SettingsStore.successfulSessions.first()
        val firstUse = SettingsStore.firstUseAt.first()
        val asked = SettingsStore.reviewPrompted.first()
        if (!shouldAsk(sessions, firstUse, System.currentTimeMillis(), asked)) return
        if (!StoreReview.available) return
        SettingsStore.setReviewPrompted()
        StoreReview.request(activity)
    }
}
