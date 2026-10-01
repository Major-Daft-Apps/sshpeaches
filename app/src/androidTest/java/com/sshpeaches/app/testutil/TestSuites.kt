package com.majordaftapps.sshpeaches.app.testutil

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LiveTransportTest

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReleaseLaneTest

/** Generates Play Store screenshots; run explicitly, never part of regular or CI test runs. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StoreScreenshotTest
