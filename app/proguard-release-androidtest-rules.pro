# Release instrumentation executes test APK bytecode against the target app
# classloader. Preserve the names and members the test runner and release smoke
# tests invoke across that APK boundary.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.tracing.** { *; }
-keep class androidx.work.** { *; }
-keep class com.majordaftapps.sshpeaches.app.** { *; }
