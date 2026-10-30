# SSHPeaches User Guide

This document explains each major screen in SSHPeaches 0.1.x.

## Navigation overview

Use the left drawer to access:

- Home
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

## Home

Home is the default landing screen.

- Shows open sessions when any are active.
- Shows favorites grouped by type: hosts, identities, port forwards, and snippets.
- Shows a single recents list across all four resource types.
- If the app has no saved resources yet, Home shows a welcome screen with buttons to add your first host, identity, port forward, or snippet.
- Open session rows use icons for reopen and disconnect actions.

## Hosts

Use this screen to manage saved servers.

### Host list actions

- Search hosts by name.
- Sort menu: `Last Used` or `Alphabetical`.
- Hosts are organized into collapsible groups.
- Use the top app bar icons to add a host, scan a host QR, or import hosts from an OpenSSH config file.
- Use the three-dot menu on a host card for `Edit` and `Delete`.

### Host card actions

- `SSH` starts terminal mode.
- `Upload` opens the remote file browser with upload guidance.
- `Download` opens the same remote file browser with download guidance.
- `Info` manages/runs info snippets for that host.
- `QR` shares host config.

If a host has a saved password, QR export prompts for a passphrase and encrypts that password payload.

### Host fields

- Required: name, host/IP, port, username
- Auth: password, identity, or both
- In the add/edit form, the authentication block sits directly below `Username`
- Optional: group, notes, transport (SSH/Mosh), terminal profile, preferred local forward, startup snippet, background behavior

## Connecting Screen

After a successful connection, behavior depends on mode:

- `SSH`: terminal view with compact custom key row
- `File transfer`: a remote file browser with upload and download controls available together

In the file browser, tap folders to open them and tap a file to select it. Uploads show
the remote destination for review and require confirmation before replacing an existing
file. Deleting a folder requires a separate confirmation that states its contents will
also be deleted.

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
- Organize identities into collapsible groups
- Use the three-dot menu for `Edit` and `Delete`

When exporting an identity with a private key, SSHPeaches requires an export passphrase and encrypts key data inside the QR payload.

## Port Forwards

SSHPeaches currently supports local forwards only (`-L`).

- Add/Edit/Delete forwards
- Enable/disable a forward
- Associate forwards with hosts for auto-start
- Share/import via QR
- Mark favorites
- Organize forwards into collapsible groups
- Use the three-dot menu for `Edit` and `Delete`

Remote (`-R`) and dynamic (`-D`) forwarding are not in the current app scope.

## Snippets

Use snippets for reusable shell commands.

- Add snippet
- Edit/delete snippet
- Import snippet via QR
- Run snippet
- Organize snippets into collapsible groups
- Use the three-dot menu for `Edit` and `Delete`

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
- Boost (see below)
- Diagnostics/privacy toggles (Send usage reports includes anonymous connection performance: connect times,
  Boost results, transfer speeds, listing times)
- Advanced -> Show connection latency: each session's round trip to the server in its top bar
- Restore defaults
- Transfer data via QR (include identities/settings options)

### Boost

**Settings** -> **Boost** -> **Boost** (on by default; the info button explains it) moves the file browser's
listings, downloads and uploads, and SSH terminals, off SSH onto QUIC, the UDP protocol behind HTTP/3. With it off,
nothing changes.

When a session opens, the app copies a small helper (about 6 MB, once per app version) into
`~/.cache/sshpeaches/boost` on the server over your SSH login, checks its SHA-256, and starts it. It needs a Linux
server with an x86_64 or ARM64 processor. The helper runs as your user and answers only this app (a one-time key and a
certificate fingerprint handed over through SSH).

- **File browser:** the helper lists folders and reads or writes only the files you're transferring, and quits when
  the session ends or after 10 idle minutes. A download that fails over Boost carries on over SFTP from where it
  stopped.
- **Terminal** (**Advanced** -> **Boost terminal**, on by default): the helper runs your login shell, like Mosh
  without installing anything. When the network drops or changes, the terminal reconnects by itself and the same
  shell carries on, with what it printed meanwhile; keys typed while it's away are sent once it's back. The SSH
  connection (still used for port forwards) reconnects in the background while the terminal stays usable. The shell
  lasts until you close the session, or 12 hours without the app. Hosts set to attach tmux keep using SSH.

The helper listens on one port in **Settings** -> **Advanced** -> **Boost ports** (default `60000-61000`, the range
mosh uses, so a firewall open for mosh lets it through; leave it blank for any port). If UDP doesn't get through, it is
reached over TCP on the same port (**Boost TCP fallback**). If neither works, or the server isn't supported, the app
uses SFTP and SSH and the session log says why.

## About, Help, and licenses

- **Help** shows an in-app guide for connecting, identities, terminal input, file transfers, QR sharing, and common troubleshooting. The guide also links to the external support site for longer notes.
- **About** shows version, website, privacy policy, support links, and license entry points.
- Open source license notices are available from About.
