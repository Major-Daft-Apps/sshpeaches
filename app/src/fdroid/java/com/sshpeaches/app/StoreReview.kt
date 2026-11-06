package com.majordaftapps.sshpeaches.app

import android.app.Activity

/** F-Droid edition: F-Droid has no in-app review, so the app never asks (Settings → Rate opens its page). */
internal object StoreReview {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    fun request(activity: Activity) = Unit
}
