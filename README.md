# SSHPeaches for Android

SSHPeaches is an Android SSH client for connecting to servers, managing saved hosts and keys, transferring files, and reusing common connection settings from one place.

If you installed SSHPeaches from F-Droid or Google Play, this repository includes the guides you need to get started and troubleshoot common issues.

## Start Here

- [User wiki home](docs/user-wiki/Home.md)
- [Getting started](docs/user-wiki/Getting-Started.md)
- [User guide](docs/user-wiki/User-Guide.md)
- [Troubleshooting](docs/user-wiki/Troubleshooting.md)
- [Documentation index](docs/README.md)

## What You Can Do With SSHPeaches

- Open SSH terminal sessions
- Browse and transfer files with SFTP
- Copy files with SCP
- Save hosts for one-tap reuse
- Manage SSH identities and private keys
- Set up local port forwards
- Share or import hosts, identities, and forwards with QR codes
- Save snippets for repeated commands

Minimum supported Android version: Android 8.0 (API 26).

## Quick Start

1. Open SSHPeaches.
2. If this is your first launch, the **Home** screen gives you one-tap buttons to add a host, identity, port forward, or snippet.
3. Use **Quick Connect** for a fast one-time connection, or open **Hosts** to save a server.
4. Enter your host name or IP address, port, username, and authentication settings.
5. Connect and verify the server fingerprint if SSHPeaches prompts you.
6. Save the host if you want to reuse it later for SSH, SFTP, or SCP.

## Main Areas Of The App

- **Home**: open sessions, favorites, and a mixed recents list in one place
- **Hosts**: save servers and launch SSH, SFTP, or SCP
- **Identities**: import and manage SSH keys
- **Port Forwards**: create and manage local forwards
- **Snippets**: store reusable commands
- **Settings**: security, theme, terminal, and transfer preferences

The management screens for hosts, identities, port forwards, and snippets all use grouped sections. Each group can be collapsed, and item management actions live in the three-dot menu on the right side of each card.

## Security Notes

- Review host key fingerprints before trusting a server.
- Set a PIN and enable biometric unlock in **Settings** if you want extra protection.
- Private-key and password data exported through QR can be protected with an export passphrase.

## Need Help?

- Open the in-app drawer and tap **Help**
- Read the [troubleshooting guide](docs/user-wiki/Troubleshooting.md)
- Visit the support page: <https://majordaftapps.com/sshpeaches-support>
- Report bugs or request features through this repository's GitHub issues

## License

SSHPeaches is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

## Open Source Notices

Open source license notices are available in the app from **About**.
