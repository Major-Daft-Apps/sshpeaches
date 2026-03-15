# SSHPeaches User Guide

This document explains each major screen in SSHPeaches 0.1.x.

## Navigation overview

Use the left drawer to access:

- Favorites
- Hosts
- Identities
- Port Forwards
- Snippets
- Keyboard Editor
- Theme Editor
- Settings
- Help
- About

The drawer also includes a dedicated **Quick Connect** button for ad-hoc sessions.

## Favorites

Favorites is the default landing screen.

- Shows favorited hosts, identities, and forwards.
- Use star actions in Hosts, Identities, and Port Forwards to add/remove favorites.
- Host favorites keep the same action buttons as Hosts (`SSH`, `SFTP`, `SCP`, info, QR).

## Hosts

Use this screen to manage saved servers.

### Host list actions

- Search hosts by name.
- Sort menu: `Last Used` or `Alphabetical`.
- Open sessions list appears at top with `Open` and `Disconnect`.
- Add new host via **Add host**.
- Import host via **Import QR**.

### Host card actions

- `SSH` starts terminal mode.
- `SFTP` opens SFTP browser mode.
- `SCP` opens dual-pane transfer mode.
- `Info` manages/runs info snippets for that host.
- `QR` shares host config.

If a host has a saved password, QR export prompts for a passphrase and encrypts that password payload.

### Host fields

- Required: name, host/IP, port, username
- Auth: password, identity, or both
- Optional: group, notes, transport (SSH/Mosh), terminal profile, preferred local forward, startup snippet, background behavior

## Connecting Screen

After a successful connection, behavior depends on mode:

- `SSH`: terminal view with compact custom key row
- `SFTP`: file browsing and upload/download controls
- `SCP`: local/remote panes with copy operations

Common behaviors:

- Connection logs are shown in real time.
- You can close/retry from the top bar.
- Session notifications support quick open/disconnect while running in background.

## Identities

Use identities for SSH key authentication.

- Add/Edit/Delete identities
- Import private keys from file
- Generate/share identity QR
- Import identity via QR
- Mark favorites

When exporting an identity with a private key, SSHPeaches requires an export passphrase and encrypts key data inside the QR payload.

## Port Forwards

SSHPeaches currently supports local forwards only (`-L`).

- Add/Edit/Delete forwards
- Enable/disable a forward
- Associate forwards with hosts for auto-start
- Share/import via QR
- Mark favorites

Remote (`-R`) and dynamic (`-D`) forwarding are not in the current app scope.

## Snippets

Use snippets for reusable shell commands.

- Add snippet
- Edit/delete snippet
- Import snippet via QR
- Run snippet

Current run behavior: snippets run against the first active session. If no session is active, run is rejected.

## Keyboard Editor

Customize the compact terminal key row.

- Tap any slot to assign or change key action
- Choose from modifiers, letters, function keys, navigation keys, and sequences
- Assign icons and modifier combos
- Reset layout anytime

## Theme Editor

Manage terminal themes/profiles.

- Select default terminal profile
- Duplicate built-in profiles
- Create custom profiles
- Edit custom profile name, font size, colors, cursor style/blink
- Delete custom profiles

Hosts referencing a deleted profile fall back to the app default profile.

## Settings

Settings includes:

- Theme mode (System/Light/Dark)
- Background session toggle
- Terminal emulation (`xterm` or `vt100`)
- Selection mode (`Natural` or `Block`)
- Security:
  - PIN
  - biometric unlock (requires PIN)
  - lock timeout
  - host key prompt controls
- Port forward auto-start
- Diagnostics/privacy toggles
- Restore defaults
- Transfer data via QR (include identities/settings options)

## About, Help, and licenses

- **Help** opens official support documentation in browser/custom tab.
- **About** shows version, website, privacy policy, support links, and license entry points.
- Open source license notices are available from About.
