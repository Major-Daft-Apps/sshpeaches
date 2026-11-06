plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 34
    // libtermux.so is compiled from src/main/jni (it used to be a prebuilt copy), so every build, F-Droid's included,
    // makes it from source with the same NDK.
    ndkVersion = "27.0.11902837"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
        if (findProperty("distribution") == "fdroid") {
            // The GNU build ID hashes the debug info, which holds the build directory; F-Droid builds elsewhere and
            // must get the same bytes. (Play keeps it: its native crash symbolication matches on it.)
            externalNativeBuild {
                ndkBuild {
                    arguments += "SSHPEACHES_NO_BUILD_ID=1"
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    testImplementation("junit:junit:4.13.2")
}
