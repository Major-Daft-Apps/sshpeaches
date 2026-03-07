# SSHPeaches Android Draft

This repository now contains an initial Jetpack Compose-based Android application that mirrors the current product blueprint. The goal is to provide a working scaffold that we can iterate on rapidly while fleshing out real features.

## Using The App (F-Droid / Google Play)

If you installed SSHPeaches and came here to learn how to use it:

- [Documentation index](docs/README.md)
- [Getting started](docs/getting-started.md)
- [User guide](docs/user-guide.md)
- [Troubleshooting](docs/troubleshooting.md)

## Highlights
- **Single-module (`app`) Compose app** targeting `minSdk 26`, `compileSdk 34`, Kotlin 1.9.22, AGP 8.2.2.
- **Repository + ViewModel pattern** with Room-backed persistence for hosts, identities, port forwards, and snippets.
- **Navigation shell** built with `ModalNavigationDrawer`, `NavHost`, and feature screens for Favorites, Hosts, Identities, Port Forwards, Snippets, and Settings.
- **Quick Connect bottom sheet**, **About dialog**, and **drawer shortcuts** for Help/About consistent with the product spec.
- **Composable building blocks** such as `HostCard`, section headers, and editor surfaces mapped to the UX document.
- **Carbon Black (#191919) + Blazing Flame (#F15025)** palette wired through Material 3 theme helpers.

## Project Layout
```
app/
  build.gradle.kts          // Android + Compose configuration
  src/main/java/com/majordaftapps/sshpeaches/app/
    MainActivity            // entry activity
    SSHPeachesApplication   // app container initialization
    data/                   // models + Room repository
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
- Expand SFTP and SCP workflows (SFTP console ergonomics, SCP dual-pane file browsing).
- Continue refining Info panel editor and snippet automation workflows.
- Extend QR workflows beyond single payload export/import.
- Add ViewModel factories/DI (Hilt or Koin) to decouple the repository implementation.
- Write UI tests for the major composables once functionality hardens.

## License
This project is licensed under the GNU General Public License v3.0.
See `LICENSE`.

## License Notices
Open source notices are shown in-app from the About dialog.
The Maven dependency notice inventory is generated into:
`app/src/main/assets/licenses/maven_licenses.json`

To regenerate it:
`powershell -ExecutionPolicy Bypass -File scripts/generate_maven_license_notices.ps1`
