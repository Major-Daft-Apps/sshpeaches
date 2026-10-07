# Changelog

All notable SSHPeaches release notes should be tracked here.

## Unreleased

## 0.11.3 (1103)

Release date: 2026-10-05

- Keyboards saved before this update that still had the swipe-arrows key now show Fn in its place, and a Fn key elsewhere becomes Alt. Swiping for arrow keys is switched on from the session ⋮ menu. A Swipe Nav key added back in the Keyboard Editor stays.
- The ⋮ menu item "Arrow keys" is now "Swipe for arrow keys".
- Fixed an occasional freeze at app start (ANR) caused by Firebase Performance Monitoring reading its config on the main thread; Performance Monitoring is removed.
- With Crash reports on, crash reports now include the screens and session steps leading up to them, and unexpected errors in sessions, file transfers, imports, and the editor are reported without crashing. Host names, addresses, usernames, and file paths are removed first.
- A failed connection offers Send to developer: it shows exactly what will be sent (app version, the error, and the connection log with host names, addresses, usernames, and paths removed), plus an optional note, and sends only when you tap Send. Session logs otherwise stay on the phone.
- Data-collection switches now each do exactly what they say: Usage analytics alone controls Firebase Analytics (Send usage reports no longer kept it on), turning off Crash reports also discards reports not yet sent, and debug builds no longer switch them all back on at launch. Session diagnostics now says what it does: detailed SSH and terminal logs kept on the phone.
- Servers that ask extra login questions (keyboard-interactive, such as PAM or a 2FA verification code) now show the question in a dialog. The saved password still answers the password question, and a mistyped password now counts as one failed attempt instead of two.
- SSH terminals reconnect on their own when the network drops or changes (for example Wi-Fi to mobile data), keeping the screen. Turn it off in Settings → Background Sessions → Reconnect automatically. Mosh sessions and the file browser behave as before.
- New per-host Attach to tmux option opens tmux session "sshpeaches" when the shell starts (falling back to the normal shell if tmux isn't installed), so a reconnect returns to the same shell.
- File browser: Edit small text files in an in-app editor that saves back to the server, change Permissions (chmod), jump to a parent folder from the clickable path, and resume a download that failed partway by downloading the same file again.
- Settings now has one Export and one Import button. Export offers File, QR code, Wi-Fi, or Share (Bluetooth, Quick Share, email, or any app); Import offers File, QR code, or Wi-Fi, and tapping a received export file opens SSHPeaches and asks before importing it. Wi-Fi sends to another phone on the same local network only, encrypted with a one-time code shown on the sending phone; the receiving phone finds it automatically or scans its QR code.

## 0.11.2 (1102)

Release date: 2026-10-04

- Fixed "Couldn't save PIN" (PBKDF2WithHmacSHA256 not available) in release builds after any SSH connection. The bundled Bouncy Castle provider that SSH connections install was missing classes removed by release shrinking, which also broke PIN unlock and encrypted export, import, and QR sharing in that state.
- The About dialog now links to the source code on GitHub.
- Fixed generating an Ed25519 key (the default type) failing with "Failed to generate keypair" until an SSH connection had been made in the session.
- Fixed a spurious "session is not connected" error when opening the file browser.
- The Fn row is now Back, F1-F12, and a keyboard key. Shift is no longer on the Fn row.
- Fn takes the place of the arrows (swipe navigation) key on the default keyboard, and Alt returns to the second slot. Keyboards still on an earlier default switch to the new one automatically; customized layouts are kept.
- The terminal top bar is now back, connection name, ⋮, and close. The ⋮ menu holds Arrow keys, Insert password, Change theme, Find, Snippets, and Reset (sends `reset` to the shell).
- Insert password asks for confirmation first, with a "Don't show this again" option. Turn the prompt back on in Settings.
- When a connection fails, the connection screen says what went wrong (host not found, port closed, server unreachable, password or key rejected, host key not accepted, mosh-server missing) and lists specific things to try, with Edit host, Identities, and Copy log buttons.
- Help is now a searchable list of common questions (key logins, special keys, copy and paste, port forwarding, moving to a new phone, forgotten PIN, and more), each with steps that use the app's own labels and a button that opens the right screen.

## 0.11.1 (1101)

Release date: 2026-10-01

- Host Info shows live `free -h` and physical `df -h` output (no loop/tmpfs clutter), and drops the old address/user/auth/transport fields from that panel.
- Fixed Set PIN silently doing nothing when the encrypted secure store could no longer be decrypted (for example after its Keystore key was lost). The unreadable store is now reset with a notice so a PIN can be set again, and PIN save, disable, and unlock errors are shown instead of swallowed.
- The Set PIN dialog now says when the PIN is too short or the two entries don't match.
- Built-in keyboard mode opens the system keyboard under the extra keys when a terminal session starts and when you return to the app.
- The terminal keyboard key now reopens the keyboard with one press after it was dismissed with Back, instead of needing two presses.
- PIN lock now locks based on real time spent in the background, including after the app is swiped away or the phone sleeps, and no longer briefly shows unlocked content on launch.
- The lock screen now covers dialogs and prompts that were open when the app locked, and Back sends the app to the background instead of reaching the content underneath.
- Repeated wrong PINs now add an increasing delay, and PINs are checked with the slow key derivation only (no fast PIN hash is stored).
- With a PIN set, app contents are hidden from screenshots and the recent-apps view.
- Editing an identity no longer erases its saved key passphrase.
- Export, import, key generation, and QR sharing no longer freeze the app while encrypting.
- Re-importing a backup no longer overwrites newer local host edits or startup scripts, and saved-password flags stay accurate after imports, the connect prompt's "save password", and storage resets.
- Deleting an identity, forward, or host now removes references to it from other items.
- The SFTP console no longer gets stuck on "A command is already running", and rejected remote operations now report an error.
- Find, Add, and QR-scan dialogs no longer reopen by themselves, and session screens keep typed input and open dialogs across rotation.
- Background-session timeouts now also apply after the app is swiped away or the phone sleeps.
- "Copy key to host" and import errors now say what went wrong.

## 0.11.0 (1100)

Release date: 2026-09-28

- Fixed the built-in terminal keyboard so tapping the keyboard key or the terminal shows the typing keyboard.
- Removed the Uptime section, drawer entry, monitors, and background checks.
- Simplified Find status to a match counter such as `1/2`, and highlighted matches with a dedicated yellow fill and magenta outline instead of reverse video.
- Fixed setting a PIN lock crashing the app and leaving it stuck locked. PIN crypto now runs off the UI thread, vault metadata is written in one commit, and a successful set keeps the app unlocked.
- Added file export and import for all connections, alongside the existing QR transfer buttons.
- Removed the top-bar back arrow that jumped from other screens into the active terminal.
- Removed the Advanced VT100/xterm sequence picker from the Keyboard Editor.
- Removed the Actions section (Snippet Picker and Inject Password) from the Keyboard Editor.
- Replaced the host Info panel snippet list with live system details: distro, kernel, CPU, memory, disk, and uptime.
- Fixed snippets so Run sends the command into the active SSH terminal with Enter and opens that session.
- Fixed Home in the sidebar/drawer so it returns from Hosts and other screens.
- Added a color-profile button next to Find in the terminal so you can switch themes for the current session.
- Shortened in-app Help to a few short steps per topic.
- File browser toolbar keeps Forward, Home, and Refresh on one row, moves hidden files into the actions menu, and adds a sort menu with a folders-first toggle.
- File browser rows use distinct icons for images, video, audio, documents, archives, code, and other types.
- Home keeps the previous screen's editor state, so add dialogs stay closed after you leave and come back.
- Session notifications label file-transfer connections as SFTP.

## 0.10.20 (1020)

Release date: 2026-08-29

- Sped up SFTP downloads with pipelined `SSH_FXP_READ`s, 256 KiB read size, a 128-request window, up to four parallel files, and ranged workers for large files.
- Prefer fast SSH ciphers (ChaCha20 / AES-GCM) and keep compression disabled for transfer traffic.
- Moved SFTP transfer knobs into a dedicated Advanced settings screen.
- Printed connecting-session logs from the top of the pane.

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
