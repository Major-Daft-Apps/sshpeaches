plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
	id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

import java.util.Properties
import java.util.concurrent.TimeUnit
import org.gradle.internal.os.OperatingSystem

// The Firebase project currently has a release client only; skip non-release processing.
tasks.configureEach {
    if (
        name.startsWith("process") &&
        name.endsWith("GoogleServices") &&
        !name.contains("Release")
    ) {
        enabled = false
    }
}

android {
    namespace = "com.majordaftapps.sshpeaches.app"
    compileSdk = 36
    val liveSuiteAnnotation = "com.majordaftapps.sshpeaches.app.testutil.LiveTransportTest"
    val releaseLaneAnnotation = "com.majordaftapps.sshpeaches.app.testutil.ReleaseLaneTest"
    val liveSuiteRequested = gradle.startParameter.taskNames.any {
        it.contains("liveAndroidTest", ignoreCase = true)
    }
    val releaseLaneRequested = gradle.startParameter.taskNames.any {
        it.contains("releaseLane", ignoreCase = true)
    }
    val liveSshHost = (findProperty("liveSshHost") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: "10.0.2.2"
    val liveSshPort = ((findProperty("liveSshPort") as? String)?.toIntOrNull() ?: 56321).coerceAtLeast(1024)
    val liveSshUsername = (findProperty("liveSshUsername") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: "tester"
    val liveSshPassword = (findProperty("liveSshPassword") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: "peaches-password"

    val keystoreProperties = Properties().apply {
        val propsFile = rootProject.file(".keystore/keystore.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    fun keystoreProp(name: String): String? =
        (keystoreProperties[name] as? String)
            ?: (project.findProperty(name) as? String)
            ?: System.getenv(name)

    val diagnosticsEndpoint = (
        keystoreProp("SSHPEACHES_DIAGNOSTICS_ENDPOINT")
            ?: ""
        ).replace("\"", "\\\"")

    defaultConfig {
        applicationId = "com.majordaftapps.sshpeaches"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.9.7"
        buildConfigField("String", "DIAGNOSTICS_ENDPOINT", "\"$diagnosticsEndpoint\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        if (liveSuiteRequested) {
            testInstrumentationRunnerArguments["annotation"] = liveSuiteAnnotation
            testInstrumentationRunnerArguments["notAnnotation"] = releaseLaneAnnotation
            testInstrumentationRunnerArguments["liveSshHost"] = liveSshHost
            testInstrumentationRunnerArguments["liveSshPort"] = liveSshPort.toString()
            testInstrumentationRunnerArguments["liveSshUsername"] = liveSshUsername
            testInstrumentationRunnerArguments["liveSshPassword"] = liveSshPassword
            testInstrumentationRunnerArguments["liveSshKeyUsername"] = "tester-key"
        } else if (releaseLaneRequested) {
            testInstrumentationRunnerArguments["annotation"] = releaseLaneAnnotation
            testInstrumentationRunnerArguments["notAnnotation"] = liveSuiteAnnotation
        } else {
            testInstrumentationRunnerArguments["notAnnotation"] =
                "$liveSuiteAnnotation,$releaseLaneAnnotation"
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val storeFilePath = keystoreProp("SSHPEACHES_STORE_FILE")
        ?: keystoreProp("storeFile")
        ?: keystoreProp("STORE_FILE")
        ?: rootProject.file(".keystore/sshpeaches").takeIf { it.exists() }?.path
    val storePassword = keystoreProp("SSHPEACHES_STORE_PASSWORD")
        ?: keystoreProp("storePassword")
        ?: keystoreProp("STORE_PASSWORD")
    val keyAlias = keystoreProp("SSHPEACHES_KEY_ALIAS")
        ?: keystoreProp("keyAlias")
        ?: keystoreProp("KEY_ALIAS")
    val keyPassword = keystoreProp("SSHPEACHES_KEY_PASSWORD")
        ?: keystoreProp("keyPassword")
        ?: keystoreProp("KEY_PASSWORD")

    val releaseSigning = if (
        !storeFilePath.isNullOrBlank() &&
        !storePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyPassword.isNullOrBlank()
    ) {
        signingConfigs.create("release") {
            storeFile = file(storeFilePath)
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            } else {
                logger.warn("Release signing is not configured; set SSHPEACHES_* keystore properties.")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("benchmark") {
            java.srcDir("src/debug/java")
        }
    }
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api34") {
                    device = "Medium Phone"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
                create("nexus9Api34") {
                    device = "Nexus 9"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("com.google.zxing:core:3.5.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))
    implementation("com.google.guava:listenablefuture:1.0")
    debugRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    releaseRuntimeOnly("org.slf4j:slf4j-nop:2.0.7")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    val firebaseBom = platform("com.google.firebase:firebase-bom:33.7.0")
    releaseImplementation(firebaseBom)
    releaseImplementation("com.google.firebase:firebase-crashlytics")
    releaseImplementation("com.google.firebase:firebase-crashlytics-ndk")
    releaseImplementation("com.google.firebase:firebase-analytics")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val liveServerMainClass = "com.majordaftapps.sshpeaches.livetest.LiveSshServerKt"
val liveServerPort = ((findProperty("liveSshPort") as? String)?.toIntOrNull() ?: 56321).coerceAtLeast(1024)
val liveServerStateDir = rootProject.layout.buildDirectory.dir("live-ssh-server-state")
val liveServerMarkerFile = layout.buildDirectory.file("live-ssh-server/process.properties")
val liveServerLogFile = layout.buildDirectory.file("live-ssh-server/server.log")
val liveSshUseAdbReverse = (findProperty("liveSshUseAdbReverse") as? String)?.equals("true", ignoreCase = true) == true
val liveSshDeviceSerial = (findProperty("liveSshDeviceSerial") as? String)?.takeIf { it.isNotBlank() }

fun adbExecutablePath(): String {
    val adbName = if (OperatingSystem.current().isWindows) "adb.exe" else "adb"
    return File(android.sdkDirectory, "platform-tools/$adbName").absolutePath
}

fun Project.helperRuntimeClasspath(): String {
    val helperProject = project(":tools:live-ssh-server")
    val jarTask = helperProject.tasks.named("jar").get()
    val jarFile = jarTask.outputs.files.singleFile
    val runtimeClasspath = helperProject.configurations.getByName("runtimeClasspath").files
    return (runtimeClasspath + jarFile).joinToString(File.pathSeparator) { it.absolutePath }
}

tasks.register("startLiveSshServer") {
    dependsOn(":tools:live-ssh-server:jar")
    doLast {
        val markerFile = liveServerMarkerFile.get().asFile
        val logFile = liveServerLogFile.get().asFile
        markerFile.parentFile.mkdirs()
        logFile.parentFile.mkdirs()
        if (markerFile.exists()) {
            logger.lifecycle("Live SSH server marker exists; stopping stale process first.")
            val pid = markerFile.readLines()
                .firstOrNull { it.startsWith("pid=") }
                ?.substringAfter("=")
                ?.toLongOrNull()
            if (pid != null) {
                ProcessHandle.of(pid).ifPresent { handle ->
                    handle.destroy()
                    handle.onExit().get(5, TimeUnit.SECONDS)
                }
            }
            markerFile.delete()
        }

        val javaExecutable = File(
            System.getProperty("java.home"),
            if (OperatingSystem.current().isWindows) "bin/java.exe" else "bin/java"
        ).absolutePath
        val process = ProcessBuilder(
            javaExecutable,
            "-cp",
            helperRuntimeClasspath(),
            liveServerMainClass,
            "--port=$liveServerPort",
            "--stateDir=${liveServerStateDir.get().asFile.absolutePath}",
            "--keyProfile=primary"
        )
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()

        val reader = process.inputStream.bufferedReader()
        val readyLine = buildString {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < deadline) {
                val line = reader.readLine() ?: break
                logFile.appendText(line + System.lineSeparator())
                if (line.startsWith("LIVE_SSH_SERVER_READY")) {
                    append(line)
                    break
                }
            }
        }

        check(readyLine.isNotBlank()) {
            "Live SSH server failed to start. Check ${logFile.absolutePath}"
        }

        val pid = process.pid()
        markerFile.writeText(
            buildString {
                appendLine("pid=$pid")
                appendLine("ready=$readyLine")
            }
        )

        if (liveSshUseAdbReverse) {
            val adbArgs = buildList {
                add(adbExecutablePath())
                liveSshDeviceSerial?.let {
                    add("-s")
                    add(it)
                }
                add("reverse")
                add("tcp:$liveServerPort")
                add("tcp:$liveServerPort")
            }
            exec {
                commandLine(adbArgs)
            }
        }
    }
}

tasks.register("stopLiveSshServer") {
    doLast {
        if (liveSshUseAdbReverse) {
            val adbArgs = buildList {
                add(adbExecutablePath())
                liveSshDeviceSerial?.let {
                    add("-s")
                    add(it)
                }
                add("reverse")
                add("--remove")
                add("tcp:$liveServerPort")
            }
            exec {
                isIgnoreExitValue = true
                commandLine(adbArgs)
            }
        }

        val markerFile = liveServerMarkerFile.get().asFile
        if (!markerFile.exists()) {
            return@doLast
        }
        val pid = markerFile.readLines()
            .firstOrNull { it.startsWith("pid=") }
            ?.substringAfter("=")
            ?.toLongOrNull()
        if (pid != null) {
            ProcessHandle.of(pid).ifPresent { handle ->
                handle.destroy()
                runCatching { handle.onExit().get(10, TimeUnit.SECONDS) }
                if (handle.isAlive) {
                    handle.destroyForcibly()
                }
            }
        }
        markerFile.delete()
    }
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    doNotTrackState("UTP result locks on Windows can leave unreadable .lck files in task outputs.")
    if (gradle.startParameter.taskNames.any { it.contains("liveAndroidTest", ignoreCase = true) }) {
        dependsOn("startLiveSshServer")
        finalizedBy("stopLiveSshServer")
    }
}

tasks.register("liveAndroidTest") {
    group = "verification"
    description = "Runs only the live SSH/SFTP/SCP instrumentation suite against the sandboxed helper server."
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("releaseLaneAndroidTest") {
    group = "verification"
    description = "Runs the non-blocking release-lane instrumentation smoke suite on the connected device."
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("releaseLaneManagedDevicesCheck") {
    group = "verification"
    description = "Runs the release-lane smoke suite on the managed phone and tablet emulators."
    dependsOn("pixel2Api34DebugAndroidTest", "nexus9Api34DebugAndroidTest")
}
