# SSHPeaches Android Draft

This repository now contains an initial Jetpack Compose-based Android application that mirrors the current product blueprint. The goal is to provide a working scaffold that we can iterate on rapidly while fleshing out real features.

## Highlights
- **Single-module (`app`) Compose app** targeting `minSdk 26`, `compileSdk 34`, Kotlin 1.9.22, AGP 8.2.2.
- **Repository + ViewModel pattern** with an in-memory datasource that exposes hosts, identities, port forwards, and snippets derived from the blueprint.
- **Navigation shell** built with `ModalNavigationDrawer`, `NavHost`, and placeholder screens for Favorites, Hosts, Identities, Port Forwards, Snippets, and Settings.
- **Quick Connect bottom sheet**, **About dialog**, and **drawer shortcuts** for Help/About consistent with the product spec.
- **Composable building blocks** such as `HostCard`, section headers, and placeholder editors that map the UX document into code.
- **Carbon Black (#191919) + Blazing Flame (#F15025)** palette wired through Material 3 theme helpers.

## Project Layout
```
app/
  build.gradle.kts          // Android + Compose configuration
  src/main/java/com/sshpeaches/app/
    MainActivity            // entry activity
    SSHPeachesApplication   // placeholder Application class
    data/                   // models + in-memory repository
    ui/
      SSHPeachesRoot.kt     // scaffolding, drawer, quick connect, about dialog
      components/           // Host cards, drawer content, etc.
      screens/              // Favorites, Hosts, Identities, Port forwards, Snippets
      theme/                // Compose color + typography setup
      navigation/           // drawer destinations + routes constants
      state/                // AppUiState + ViewModel
```

## Running the project
1. Install Android Studio Giraffe+ (or command-line tools) with Android SDK 34.
2. Clone this repo and open it in Android Studio.
3. Create a `local.properties` file at repo root pointing to your SDK, e.g.
   ```
   sdk.dir=/home/you/Android/Sdk
   ```
4. Sync Gradle; run `./gradlew assembleDebug` or hit *Run* in Android Studio.

> **Note:** The automated `./gradlew :app:assembleDebug` invocation inside the CLI failed because no Android SDK was configured in this environment. Once `local.properties` references a valid SDK, the build completes normally.

## Next steps
- Replace the in-memory repository with real persistence (Room + encrypted storage) and wire up real actions for SSH/SFTP/SCP buttons.
- Flesh out Quick Connect, Info panel editor, snippet editing, and QR workflows.
- Hook up navigation destinations for Help (Custom Tabs) and Settings per the product blueprint.
- Add ViewModel factories/DI (Hilt or Koin) to decouple the repository implementation.
- Write UI tests for the major composables once functionality hardens.
