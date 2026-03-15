import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "SSHPeaches"
include(":app")
include(":macrobenchmark")
include(":terminal-emulator")
include(":terminal-view")
include(":tools:live-ssh-server")

project(":terminal-emulator").projectDir = file("third_party/termux-playstore/terminal-emulator")
project(":terminal-view").projectDir = file("third_party/termux-playstore/terminal-view")
