# SSHPeaches Getting Started

This guide is for users installing SSHPeaches from F-Droid or Google Play.

## What SSHPeaches does

SSHPeaches is an Android SSH client with support for:

- SSH terminal sessions
- SFTP browsing/transfers
- SCP transfers
- Identity key management
- Local port forwarding (`-L`)
- QR-based import/export for hosts, identities, and forwards

Minimum Android version: Android 8.0 (API 26).

## First launch checklist (5 minutes)

1. Open SSHPeaches.
2. If the app is empty, **Home** shows a welcome screen with buttons for adding your first resource.
3. Open the drawer and tap **Quick Connect**.
4. Enter `Host / IP`, `Port`, `Username`, and authentication settings.
5. Tap **Connect**.
6. If prompted about a server key, review the fingerprint before trusting it.
7. After success, return to **Hosts** and save the server for one-tap reuse.

## Add your first saved host

1. Open **Hosts**.
2. Tap the **Add** icon in the top app bar.
3. Fill in name, host/IP, port, and username.
4. Right under `Username`, choose the authentication method and any related password or identity settings.
5. Set optional extras:
   - transport (`SSH` or `Mosh`)
   - group
   - notes
   - startup snippet
   - default local forward
   - terminal theme profile
6. Tap **Add**.

From each host card you can launch `SSH`, `SFTP`, or `SCP`.

## Set up key authentication

1. Open **Identities**.
2. Tap the **Add** icon in the top app bar.
3. Add a private key (paste or import file), then confirm the generated fingerprint.
4. Save.
5. Go back to **Hosts** and assign that identity to the host.

Note: if the app is locked, key import is blocked until you unlock it.

## Recommended security settings

Open **Settings** and configure:

- Set a PIN (`Set PIN`)
- Enable biometric unlock (after PIN is set)
- Set **Lock timeout**
- Keep **Host key prompts** enabled
- Disable **Automatically trust host key** if you want manual trust decisions

## Transfer data with QR

- **Hosts**: open host card -> QR icon
- **Identities**: identity row -> QR icon
- **Port Forwards**: forward row -> QR icon
- **Import**: use the QR icon in the top app bar on each management screen
- **Export all (single QR payload)**: **Settings** -> **Transfer Data** -> **Export via QR**

If a host password or private key is included, you will be prompted for an export passphrase.

## Where to get help

- In-app drawer -> **Help**
- Support page: <https://majordaftapps.com/sshpeaches-support>
- GitHub issues for bugs/feature requests in this repository
