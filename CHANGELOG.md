# Changelog

All notable SSHPeaches release notes should be tracked here.

## Unreleased

## 0.10.18 (1018)

Release date: 2026-08-22

- Fixed SSH terminals reverting to the system monospace font after the app returned to the foreground.
- Added Android lifecycle regression coverage to verify the selected bundled terminal font is restored after background and foreground transitions.

## 0.10.17 (1017)

Release date: 2026-08-21

- Fixed large SFTP transfers failing when packet lengths were decoded incorrectly.
- Closed unusable SFTP sessions after transport failures so later operations can reconnect cleanly.

## 0.10.16 (1016)

Release date: 2026-08-21

- Improved SSH, terminal, and SFTP session reliability during concurrent activity, rotation, background/foreground transitions, and connection churn.
- Fixed SFTP transfers for remote filenames containing leading spaces, Unicode, emoji, and punctuation.
- Added hidden-file controls to the remote file browser and safer transfer cancellation and retry behavior.
- Simplified terminal special keys to two rows, replacing Alt with a remappable Fn key that opens fixed Back, Shift, and F1-F12 rows.
- Improved active-session handling, connection and authentication feedback, and Android home-screen widgets.

## 0.10.14 (1014)

Release date: 2026-07-28

- Made network-related connection failures explicit on the connection screen.
- Restored detailed SSH session diagnostics during connection and refined the bounded, auto-scrolling debug output.
- Sped up SFTP directory listings by removing per-symlink network requests and batching large console updates.
- Fixed unchanged SFTP refreshes and listing failures leaving the command controls stuck on `Working…`.

## 0.10.13 (1013)

Release date: 2026-07-28

- Restored useful SSH transport diagnostics while sampling repetitive packet/window messages to keep the connection screen responsive.
- Fixed transfer sizes ending in zero being displayed too small, such as 600 KB appearing as 6 KB.
- Improved SCP and SFTP throughput by enlarging SSH receive flow control and reducing progress-update overhead.
- Fixed file-transfer UI correctness issues around remote operations, progress, results, and immediate document-picker callbacks.
- Improved Android widget clarity, sizing, file-transfer shortcuts, and launch reliability across activity recreation.
- Fixed terminal copy actions on older Android versions.
- Marked terminal copies as sensitive and suppressed Android emulator clipboard overlays while preserving system paste behavior.

## 0.10.12 (1012)

Release date: 2026-07-24

- Fixed notification Open actions while another SSHPeaches terminal is already visible, so the selected active session replaces the on-screen terminal.
- Fixed SSH connections that could remain on the Connecting screen after the interactive shell was ready until the user left and reopened the session.
- Reduced connection-screen stalls by filtering high-frequency SSH packet diagnostics and coalescing session-log scrolling.

## 0.10.11 (1011)

Release date: 2026-07-23

- Added a polished one-time welcome message before permission onboarding, highlighting that SSHPeaches is free, open source, ad-free, and has no paid features.
- Added an optional link to follow the developer on X for project updates.

## 0.10.10 (1010)

Release date: 2026-07-23

- Fixed per-session notification taps and Open actions so each opens its own terminal, including before the connection reaches a shell, instead of reopening the last-used session.
- A single-session summary now opens that terminal, while a multi-session summary opens the session list.
- Fixed stale notification opens after disconnect so they no longer display the previously used terminal.
- Active sessions now disconnect immediately when the default network is lost, changes route, or switches into or out of a VPN.
- Adopted the four-digit `MMpp` version-code convention for future 0.x releases.

## 0.10.9 (109)

Release date: 2026-07-23

- Fixed Ctrl-modified input after terminal copy or paste, and restored reliable routing for physical, virtual, and built-in keyboard events.
- Fixed IME composition, deletion, and rapid text bursts so committed input is delivered once without dropped or duplicated characters.
- Added an optional built-in terminal keyboard with an Fn layer, function keys, numpad shortcuts, and reachable Ctrl, Alt, and Shift modifiers.
- Improved SSH and Mosh session reliability during concurrent output, terminal resizing, burst input, and Mosh reconnects.
- Fixed stale or blank terminal screens after background shutdown, launcher relaunch, and expired open-session requests.
- Expanded terminal profiles with per-session 16-color ANSI palettes, reset-safe cursor settings, and contrast correction for unreadable foreground colors.
- Refined the app's light and dark palettes with warmer, higher-contrast surfaces and controls.
- Improved SSH compatibility on Android devices where some Diffie-Hellman algorithms are unavailable.

## 0.10.8 (108)

Release date: 2026-07-13

- Fixed dropped or reordered terminal keystrokes across IME bursts, service rebinding, and Mosh reconnects.
- Fixed printable compact keys, virtual numpad output, Fn layers, and modifier handling in the built-in keyboard.
- Fixed IME composing text being transmitted before commit or duplicated on commit.
- Kept SCP Forward navigation reachable on narrow screens and disabled uploads during directory refreshes.

## 0.10.7 (107)

Release date: 2026-06-29

- Bumped app version metadata to `0.10.7` / code `107` for release preparation.

## 0.9.5 (beta)

Release date: 2026-03-14

- Added release-only diagnostics, Crashlytics, Analytics, and App Check wiring with opt-in controls.
- Wired release signing/configuration for local AAB generation and Play publishing prep.
- Rebuilt and uploaded a signed release AAB for internal Play testing with the current 0.9.5 beta line.
- Updated the About dialog to append `(debug)` automatically in debug builds.
- Tightened Play policy readiness by removing ad-related permissions and keeping the foreground service scoped to `dataSync`.
- Rotated the exposed Firebase API key, removed `google-services.json` from tracked source, and kept Firebase config local-only.
- Bundled GPLv3 license text and continued documenting Play publishing readiness locally.
- Verified instrumented smoke coverage on emulators:
  - `SmokeNavigationTest` passed on `Pixel_9` and `Nexus_9`
  - `QrImportUiSmokeTest` passed on `Pixel_9` and `Nexus_9`
  - `HostsCrudTest` passed on `Nexus_9`; on `Pixel_9` the tests ran but instrumentation crashed during teardown
  - `SettingsSmokeTest` is currently failing on both `Pixel_9` and `Nexus_9` because the background-session switch stays `On`
