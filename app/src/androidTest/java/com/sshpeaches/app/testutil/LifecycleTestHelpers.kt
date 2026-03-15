package com.majordaftapps.sshpeaches.app.testutil

import android.content.pm.ActivityInfo

fun MainActivityComposeRule.rotateLandscape() {
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    waitForIdle()
}

fun MainActivityComposeRule.rotatePortrait() {
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    waitForIdle()
}

fun MainActivityComposeRule.recreateActivity() {
    activityRule.scenario.recreate()
    waitForIdle()
}
