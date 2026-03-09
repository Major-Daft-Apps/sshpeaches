plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
	id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

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

    defaultConfig {
        applicationId = "com.majordaftapps.sshpeaches"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
