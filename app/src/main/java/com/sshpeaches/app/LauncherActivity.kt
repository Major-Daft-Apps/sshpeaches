package com.majordaftapps.sshpeaches.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What every launcher icon opens. The icons are activity-aliases, one per app icon, and choosing an icon disables
 * the others; Android removes a task started through an alias when that alias is disabled, which closed the app
 * whenever the icon changed. So the aliases point here, in a task of its own that ends at once, and the app runs in
 * a task started directly: switching icons never touches it.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The same explicit intent every time: Android brings the app's existing task to the front, as a launcher
        // tap did, instead of starting another screen on top.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
