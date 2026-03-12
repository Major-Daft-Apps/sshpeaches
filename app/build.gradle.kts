plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
	id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

import java.util.Properties

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
        versionCode = 4
        versionName = "0.9.4 (beta)"
        buildConfigField("String", "DIAGNOSTICS_ENDPOINT", "\"$diagnosticsEndpoint\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api34") {
                    device = "Pixel 2"
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
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
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
