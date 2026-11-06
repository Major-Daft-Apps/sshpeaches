package com.majordaftapps.sshpeaches.app

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/** Google Play edition: Play's in-app review sheet (Play decides whether it shows). */
internal object StoreReview {
    const val available = true

    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            if (!activity.isFinishing) manager.launchReviewFlow(activity, info)
        }
    }
}
