# Changelog

All notable SSHPeaches release notes should be tracked here.

## 0.14.1 (1401)

Not released yet.

- **F-Droid edition** (`com.majordaftapps.sshpeaches.fdroid`, built with `-Pdistribution=fdroid`): no Google libraries at all, so no crash reports, analytics, App Check or Play's review prompt; "Report a problem" copies the report for pasting instead of sending it. It installs next to the Play app and is signed with its own key; F-Droid rebuilds it from source and checks its build matches the published APK byte for byte. Recipe in `tools/fdroid/`, signing with `tools/fdroid/sign.sh`.
- The bundled mosh-client is built from source (`tools/mosh-client/build.sh`: mosh 1.4.0, protobuf-lite, the libcrypto of OpenSSL 3.5.9, the long-term support release, in place of the end-of-life 3.0 series, and ncurses) as one binary that loads only Android's own libraries, in place of the Termux package and its 57 shared libraries. It's 16 KB-aligned, so it runs on phones with 16 KB memory pages.
- Fixed a crash of the session service when a session ended at the same moment the session notification was refreshed (for example as a file transfer finished): the list of open sessions could change while it was being copied.
- libtermux is compiled from its C source during the build instead of shipping a prebuilt copy (the app module now names its NDK, without which release builds silently skipped stripping it), and the Boost client library builds to the same bytes wherever it's built.
- Fixed an occasional crash after cancelling a Boost download (or any Boost folder listing that happened while memory was being cleaned up): Go's cgo, before Go 1.26, could hand the Boost library's results to the app through an unaligned stack address, and the Go runtime then stopped the app. The library is now built with Go 1.26.10 and the current golang.org/x/mobile (with newer x/crypto, x/net and x/sys), and `tools/boost/build.sh` refuses an older Go.
- **Old SSH algorithms are off by default**, as in OpenSSH's own client: SHA-1 signatures (`ssh-rsa`) and DSA host keys, SHA-1 key exchange and CBC ciphers. RSA keys keep working through `rsa-sha2-256/512`. A server that offers nothing else now fails with "No secure connection method in common", and a new per-host option, **Allow legacy algorithms**, turns them back on for that host. An imported file can carry the option for a new host but never turns it on for a host you already have; a scanned host QR code never turns it on, since a QR code can come from anyone.
- Text copied from the terminal is cleared from the clipboard after a minute, unless something else was copied since. (In the background Android hides the clipboard, so the check then waits until you're back in SSHPeaches.)
- Passphrase-protected exports use 600,000 PBKDF2 iterations (was 210,000), derived once per export instead of once per saved secret, so exporting many passwords is faster than before. Older exports still import; any other iteration count is refused. The key derived from the export passphrase is kept only while that export or import runs and is wiped from memory as soon as it finishes (a single QR code's key is used once and wiped).

## 0.14.0 (1400)

Release date: 2026-11-04

- The session service declares the foreground service type that fits it, `specialUse` (with the explanation Google Play asks for), instead of `connectedDevice`, which is for Bluetooth, USB and NFC hardware. Play rejected the 0.13.0 update over it. Nothing changes in use: the service runs only while a session you opened is connected, with its notification and Disconnect action.
- **Saved secrets are tied to this phone's secure hardware.** With a PIN set, the key that opens the vault of saved passwords, private keys and passphrases is now derived through a key that never leaves Android Keystore (the phone's secure hardware where it has one), not from the PIN alone. A copy of the app's data is useless anywhere else, because every PIN guess has to run on this phone; on Android 9 and later the key also works only while the phone is unlocked. Existing vaults switch over the next time the right PIN is entered; biometric unlock is unchanged.
- New **Settings** -> **Security** -> **Require this phone's screen lock** (off by default, needs a PIN and a screen lock on the phone): saved secrets then also need the phone's screen lock or a strong fingerprint used in the last 5 minutes; otherwise Android asks for it before the PIN is checked. Guesses at the screen lock are limited by the phone's secure hardware. Removing the phone's screen lock destroys the key and with it every saved secret (the setting warns before turning on); changing the SSHPeaches PIN turns it off.

## 0.13.1 (1301)

Release date: 2026-11-02

- The Boost client library is built with 16 KB-aligned segments, as Google Play requires: phones with 16 KB memory pages could fail to install or start the app (every other native library already was). `tools/boost/build.sh` now refuses a library that isn't.

## 0.13.0 (1300)

Release date: 2026-11-02

- Jetpack Compose updated to 1.7.8 and Material 3 to 1.3.1 (the app ran Compose 1.7.0); among other fixes, an occasional crash inside Compose when the screen rotated.
- New **Settings** -> **Security** -> **Lock when idle** (off by default): with a PIN set, the app also locks after the lock timeout (a minute at least) without a tap, key press or typed text while it's open, not only after being in the background.
- New PINs need at least 6 digits; PINs already set keep working.
- Hosts connected to for the first time prefer ECDSA and Ed25519 host keys over RSA keys signed with SHA-1 (plain ssh-rsa); RSA keys with SHA-2 still come first, and hosts already known keep their saved key.
- Snippet exports no longer carry two settings that never did anything ("auto-run on hosts" and "require confirmation"); a snippet runs on connect when it's chosen as a host's startup command.

## 0.12.4 (1204)

Release date: 2026-10-31

Security fixes from a review of the whole app:

- **Lock screen:** after a rotation, theme or font change while the app was locked, a file editor, rename or delete dialog, or the Transfers pane that had been open could come back above the PIN screen and be used. The session screen's dialogs now stay closed while locked, and the lock window opens after the screen's own windows.
- **Lock screen:** tapping a host on the home screen widget while the app is locked waits for the PIN instead of connecting (and running the host's startup command and forwards) for whoever holds the phone.
- **Biometric unlock:** after a fingerprint was added or removed, unlocking with the PIN quietly trusted the new set of fingerprints again. Biometric unlock now turns itself off until you turn it back on in Settings.
- **PIN:** PIN checks run one at a time, so guesses started together (for example around a rotation) can't get around the lockout.
- **Imports:** an import naming one of your public-key-only identities could store someone else's private key in it, which "Install key on host" would then put on your servers. A key for an identity you already have must now prove it's that identity's key. New hosts from an import no longer point at your own identities, forwards or snippets, a host QR can't add a second host whose name differs from yours only by spaces or case, and deleting a snippet clears host startup commands that pointed at it.
- **Paste:** text a keyboard inserts from its clipboard bar went to the terminal as typed, so several lines ran one after another. Multi-line or escape-carrying text now takes the paste path (bracketed paste, control characters stripped) like the Paste button.
- **Boost:** the helper on the server read up to 64 KiB of a request before checking its token and set no time limits, so anyone who could reach its port could tie up memory and connections in your account. It now checks the token first, gives a peer 10 s to send it, and serves at most 32 connections that haven't. quic-go updated to 0.49.1 (CVE-2025-59530). A helper already on the server is only run when its SHA-256 checks out (sha256sum, shasum or openssl).
- **Privacy:** crashes are scrubbed like other reports before they reach Crashlytics, file names with spaces are removed whole, and host names and Boost errors stay out of release logs.
- **Wi-Fi transfer** only offers itself on Wi-Fi, Ethernet or hotspot addresses, not on a carrier's private mobile-data address or a VPN.
- **Install key on host** refuses a public key that spans more than one line.
- **Build supply chain:** every Gradle dependency is checked against recorded SHA-256 checksums (gradle/verification-metadata.xml), the unused JitPack repository is gone, and the Gradle download is pinned by its checksum.

Also:

- Hosts' OS logos are drawn directly instead of loaded in the background, so a load that failed when the app started cold no longer leaves the coloured circle empty until you connect to the host. Saving a host from an older copy (for example marking it used) also keeps an OS that was detected meanwhile.

## 0.12.3 (1203)

Release date: 2026-10-30

- Setting, entering or checking the PIN could fail with "PBKDF2WithHmacSHA256 SecretKeyFactory not available" while the app swapped in its bundled Bouncy Castle provider (when the first SSH connection is prepared, often right at startup): the swap now builds the new provider before removing the old one, and the PIN code falls back to the bundled provider. Seen in crash reports from 0.10.20 to 0.11.1 and in the test suite.
- The extra keys' **Fn** layer has **Ctrl** and **Shift**, so Ctrl+F5, Shift+F1 or Ctrl+Shift+F9 can be typed there (two rows of eight: Ctrl, F1-F6, Back / Shift, F7-F12, keyboard). Back sits where Fn is on the main rows, and a Ctrl or Shift latched on either layer still applies after switching.
- A terminal or file browser session's long name in the top bar now shrinks its font a little to fit, and only ends in "…" when it would get too small to read (it was cut off at full size).

## 0.12.2 (1202)

Release date: 2026-10-30

- Going back, forward or up to one of the last five folders the file browser opened shows it at once, then refreshes it in the background (it used to load it again from scratch). Measured on the test emulator: under 5 ms instead of a full listing.
- New **Transfers** pane, like a browser's downloads page: the file browser's toolbar (and its ⋮ menu) opens a list of the session's transfers, the running one with its speed and time left, those waiting their turn in order, and the finished ones. Each can be cancelled, a failed or cancelled one tried again, a finished download opened, and the finished list cleared. The transfer card no longer ends with "(1 more queued)": it has a View all link, and its Cancel stops that transfer only.
- The transfer card shows the speed and time left.
- The sort button in the file browser has the usual sort icon (lines), not up and down arrows. On a narrow screen the toolbar's buttons shrink to fit.
- New **Advanced** -> **Show connection latency** (off by default): terminal and file browser sessions show their round trip to the server in the top bar ("42 ms", green under 100 ms, amber under 300, red above or with no answer), checked every 5 seconds; tap it for the last minute's fastest, average, slowest and jitter. SSH sessions are timed with an SSH keepalive, Boost terminals with their own pings (the path the keys take).
- **Send usage reports** now also carries connection performance: time to connect, whether Boost worked or why not (as a category), transfer speeds, folder listing times and, when the latency badge is on, round trips. Counts and quantiles only, no host names, addresses or file names; collected only while usage reports are on, and dropped when they're turned off.
- Laggy networks: a terminal's quick check after typing (no output 3 s after a key) gives the server 10 s to answer instead of 5, and counts output that arrives meanwhile as an answer. On networks whose round trips spike past 5 s it closed connections that were only slow, which then failed with "Socket closed".
- sshj failing inside its own transport (seen once on a bad mobile network: ArrayIndexOutOfBoundsException in its packet encoder while a reconnecting file browser listed a folder) is handled like a dropped connection, a new one, instead of ending the session.
- Network issues are reported like crashes when Crash reports is on, so they can be investigated: a dropped connection, a reconnect that gave up, a quick check the server didn't answer, a transfer that had to retry or failed, Boost failing. Each carries the session's mode, route (SSH, Boost, Mosh), the phone's network type and recent round trip; scrubbed like every report, and limited to one of a kind every 10 minutes. sshj's and BouncyCastle's class names stay readable in reports.
- From the closed test's feedback: **Settings** -> **Rate & share** opens SSHPeaches' Play Store page to rate it, or shares a link to it. After a few good sessions (five, over three days) the app asks Google Play once for its in-app review sheet, back on Home after a session; Play decides whether it shows.
- Back on Home asks before leaving the app, and says whether open sessions keep running in the background.
- The app's text no longer uses em dashes.
- The connecting and reconnecting screen shows a transfer only while it runs; a finished or cancelled one's result stayed there, which looked like part of the reconnect.

## 0.12.1 (1201)

Release date: 2026-10-30

- **Boost** is now on by default (**Settings** -> **Boost**). It falls back to SFTP and SSH where it can't work, so nothing changes on servers it doesn't reach.
- Boost transfers show progress as the data arrives. The bar used to sit still until whole 1 MiB pieces finished, 16 of them moving at once, which on a slow or distant connection looked like a stuck transfer for half a minute or more.
- A slow connection no longer makes a Boost transfer give up: a piece now fails only after 60 seconds with no data at all, not when it takes longer than 60 seconds. Below about 280 KB/s every piece used to fail that way and the transfer went back to SFTP.
- A Boost upload whose piece failed or was cancelled no longer cuts the helper's whole connection, which failed the next transfer.
- Closing a session ends its Boost terminal's shell on the server reliably. The hang-up could be lost when the connection closed right after it, leaving the shell running for up to 12 hours.
- The connecting screen's log shows new lines again. 0.12.0 showed only the lines that were there when the screen opened.
- Choosing an app icon switches the launcher icon at once and no longer closes the app. Android removes a task started from a launcher icon when that icon's entry is disabled, which is how the icon is switched; the launcher icons now open the app in a task of its own. Recent apps shows the chosen icon.

## 0.12.0 (1200)

Release date: 2026-10-29

Faster, sturdier file transfers and browsing:

- New **Boost** (**Settings** -> **Boost**, off by default; the info button explains it). A small helper the app starts on the server over your SSH login carries the file browser and the terminal over QUIC (UDP) instead of SSH. With it off, nothing changes.
- Boosted file browsing and transfers: listings, downloads and uploads go through the helper, the way download managers move files. On the test emulator a 20,000-file folder lists in 0.9 s instead of 1.8, a folder of 3,000 links in 0.2 s instead of 1.0, and a download runs at about 32 MB/s instead of 16. A download that fails over Boost carries on over SFTP from where it stopped; an upload starts again over SFTP.
- Boosted terminal (**Advanced** -> **Boost terminal**, on with Boost): SSH terminals run your login shell in the helper, like Mosh without installing anything. When the network drops or changes (Wi-Fi to mobile data, a dead link), the terminal reconnects by itself over a new connection, and the same shell, with its running programs, carries on: typed keys are kept until the shell confirms them and output picks up where it left off. While the SSH connection reconnects under it (port forwards still use SSH) the terminal stays usable; in the test, a command typed right after SSH dropped answered in 0.05 s from the same shell. The shell lasts until you close the session, or 12 hours without the app. Hosts set to attach tmux keep using SSH.
- The helper (about 6 MB, for Linux on x86_64 or ARM64) is copied to `~/.cache/sshpeaches/boost` once per app version and checked by SHA-256; older versions are removed. It runs as your user and answers only this app: a one-time key and a pinned certificate, both handed over through SSH. For the file browser it lists folders and reads or writes only the files being transferred, and quits with the session or after 10 idle minutes; only a terminal's helper may run a shell.
- The helper listens on a port in 60000-61000, the range mosh uses, so servers whose firewall lets mosh through let it through too (**Advanced** -> **Boost ports**). When UDP is blocked it is reached over TLS on TCP on the same port (**Advanced** -> **Boost TCP fallback**). When neither gets through, or the server isn't supported, the app uses SFTP and SSH and the session log says why.
- Downloads and uploads split the file into chunks and move them over up to four connections at once, like a download manager (aria2c, IDM). Measured on the test emulator, a 134 MB download went from about 5 MB/s to 15 MB/s, and uploads from 6.5 to 17 MB/s; at 150 ms latency uploads went from 3 to 12 MB/s. The number of connections is under **Advanced** -> **Connections per transfer**.
- Connections now prefer AES-GCM, which runs in Android's own native crypto (like HTTPS), over ChaCha20-Poly1305, which the SSH library runs in Java: on the test emulator that encrypts about 200 MB/s instead of 4, which had capped every transfer at a few MB/s per connection. ChaCha20 is still used with servers that offer no AES mode.
- You can browse, open folders, rename, delete and edit while a file transfers: transfers run on connections of their own. Listing a folder during a download used to wait for the download to finish (27 seconds in the test); now it takes under half a second. In the SFTP console, commands keep working during `get` and `put`.
- Starting another download or upload while one runs queues it; the progress card says how many are waiting. Cancel stops the running transfer and the queued ones.
- The file browser downloads and uploads with SFTP instead of SCP, so its transfers resume and use several connections too. A download writes straight into the chosen file instead of the app's cache, so it no longer sits at 100% while the file is copied.
- Transfers keep going like a download manager's: a dropped connection is replaced and its chunk carried on, the wait between attempts grows to 15 seconds, and a transfer gives up only after 15 minutes without moving a byte. When the network comes back or changes, or the file browser reconnects, waiting transfers retry at once. The card shows why it's waiting ("Connection lost …, retrying in 8 s"). A connection that stops answering without closing (a dead network) is noticed within 30 seconds instead of hanging the transfer. Before, a transfer gave up after about 15 seconds of failures.
- Transfers no longer drop by themselves on a link that works but isn't fast. Each connection used to keep up to 8 MiB of requests waiting, so its keepalive reply queued behind over a minute of data and the healthy connection was declared dead ("Connection lost (Socket closed)"). Now each keeps about 3 seconds' worth for its measured speed (256 KiB to 8 MiB), only a connection that is really stuck is cut, a "network losing" notice (sent by Samsung's Wi-Fi switching while Wi-Fi works) no longer cuts anything, and retry waits grow per connection instead of once for every connection that failed together.
- The file browser reconnects in place when the network drops or changes, like the terminal, and comes back to the folder it was showing; transfers carry on over their own connections meanwhile. It used to close, which also cancelled its transfers. Opening a folder, editing or starting a transfer while it reconnects waits for it instead of failing.
- Folders list faster: the app sends its requests together instead of waiting for each reply, with up to 64 directory reads in flight. A big folder shows its first entries at once and fills in as the rest arrives. At 150 ms latency, a 20,000-file folder lists in under 2 s instead of 9, and a 500-file one in 0.7 s instead of 1.7.
- Symbolic links in the file browser are looked up with one command on the server instead of two requests per link: a folder of 3,000 links lists in about 2 s instead of 12 at 150 ms latency. Servers that don't allow commands (SFTP-only accounts) are looked up per link as before. A link's "→" now shows its target as written in the link, like `ls -l`.
- Connecting is faster: the first connection after the app starts no longer spends over a second loading encryption code before contacting the server, a known server OS is no longer looked up again before the session opens, and connection logging stops once you're logged in.
- Reads ask for 255 KiB, the most OpenSSH's server sends; asking for 256 KiB made every reply come back short and cost a second request.
- The progress display redraws the screen 4 times a second instead of 10, and the file list is no longer re-sorted on every redraw.
- If the server refuses a second login (it allows one connection per user, or asks for a one-time code), transfers share the session's connection as before, and the session log says so.

## 0.11.9 (1109)

Release date: 2026-10-23

- A reconnect that times out waiting for the server (a slow or sleeping network, often in the background) tries again instead of ending the session after one or two attempts, and is no longer sent as a crash report.
- A reconnect whose connection is closed before the server answers ("Server closed connection during identification exchange", common on mobile networks and while a server restarts) tries again instead of failing at once.
- A connection that dies silently is noticed within seconds of typing: when nothing comes back from the server for 3 seconds after a key, the app checks the connection and reconnects if it's gone. It used to take over a minute, with every key going nowhere.
- Closing a Mosh session, or one whose Mosh client stopped, ends its mosh-server again on servers whose `ps` has no `-p` option (busybox: Alpine, many routers and NAS boxes). 0.11.8's check that the process was still mosh-server always failed there, so the server was left running.

## 0.11.8 (1108)

Release date: 2026-10-19

Security fixes from a review of the app's own code:

- SCP downloads and uploads quote file names correctly. A file name containing a `'` could run a command on the server.
- Importing an export, QR code or Wi-Fi transfer can no longer replace the key of an identity you already have, and any key it does add must match the identity's fingerprint. "Install key on host" always installs the public half of the key the app logs in with.
- A server whose saved host key is of a different type (an RSA key where an Ed25519 one was saved) now gets the "Host key changed" warning instead of the new-host prompt, and is never trusted automatically. Accepting the change replaces the old key. Revoked (`@revoked`) known-hosts entries are refused.
- Imports are shown for review before anything is applied (QR code, Wi-Fi, file or shared file), and they only add: an imported host, snippet, forward or identity never overwrites one you have, and a copy whose name clashes is marked "(imported)". Imports no longer change the app lock, crash reports, analytics, diagnostics or the password-insert confirmation, and replace the keyboard layout only when you tick it.
- A scanned host QR code no longer brings a startup command, or links to your keys or port forwards.
- Imported port forwards (export, QR code, OpenSSH config) only listen on this phone; the forward editor warns when a forward listens on the network.
- The PIN lockout after wrong guesses can't be skipped by changing the phone's clock or restarting it.
- A server can no longer freeze the app by switching the terminal between 80 and 132 columns over and over, flood the log with terminal capability requests, or put hidden line breaks into text you copy.
- A Wi-Fi transfer is only reported as sent once the other phone has decrypted it; another device connecting first no longer ends it. The transfer also stops when you leave Settings or the app locks.
- Private keys are loaded in memory for logging in, instead of being written to a temporary file.
- Diagnostics logs in release builds no longer include what you type (which could be a password).
- The in-app editor only opens regular files and stops reading at its 1 MB limit; background commands (OS detection, startup scripts) stop reading output after 64 KB or a short wait.
- A malformed SFTP packet ends that connection instead of crashing the app.
- Restarting or closing a Mosh session only stops the Mosh server it started.
- Weak algorithms that OpenSSH also no longer offers are turned off: RC4, 3DES, Blowfish, CAST, IDEA, MD5 and truncated MACs, and Diffie-Hellman group 1. A server that offers nothing better can't be connected to.
- Android's backup and device-to-device transfer rules now exclude the app's database, files and settings explicitly, not just its top folder.
- Only `content://` files are accepted for import; key files and export files are size-limited.
- Bouncy Castle updated to 1.86.

Other fixes:

- Making the terminal font smaller (or rotating to a wider screen) no longer moves the prompt into the line above. The line being typed on keeps its rows when the screen is reflowed, so bash and zsh redraw it in the right place: they used to draw the prompt one row too high, over earlier output, and zsh's `%` end-of-output marker was joined to the prompt.

- The home-screen widget no longer crashes if its labels can't be formatted. Right after the update to 0.11.7, one phone's widget crashed on every update with open sessions until the update settled.

## 0.11.7 (1107)

Release date: 2026-10-13

- After a session reconnects (network change, dropped or dead connection), the new shell starts at the screen's real size instead of 120×40. tmux in it no longer pushes the screen up a line every second or so.
- A reconnect that loses the connection again before logging in tries again instead of ending with "Not connected".
- A host key or password prompt that times out is no longer sent as a crash report.
- The connecting screen's log reads like `ssh -vv` instead of showing hexdumps: one line per SSH packet with what it negotiates (each side's algorithm proposals, the host key type, services, which login methods the server still accepts, channels and their requests). Packets carrying your password, keyboard-interactive answers or terminal data aren't logged at all.
- Importing a host that's already saved (from a QR code, an OpenSSH config, an export or a Wi-Fi transfer) no longer resets its OS icon to Unknown when the import doesn't say which OS the host runs.
- Widget: open connections show just the host's name and `user@host`, without the "Terminal · Active" line; the icon shows whether it's a terminal or a file session.
- New app icons: Settings > Appearance > App Icon is now a grid of 10 flat icons, all the same peach in different colours (Orange on cream, Orange Peach, White Peach, White on orange, Coral on blush, Peach on plum, Black on peach, Mint on charcoal, Orange on black, Terminal green). Orange on cream is the new default app icon and splash screen, and the logo on the home, lock, welcome and About screens is the same flat orange peach; the 3D icon is gone from the list, and anyone using it moves to Orange on cream. Orange Peach and White Peach users keep theirs. The launcher icon changes when you leave the app: switching it while the app is open would close the app.
- Themed app icons (One UI icon themes, Android 13+ themed icons) show the peach with its segments again instead of a plain white curve, for every app icon.

## 0.11.6 (1106)

Release date: 2026-10-13

- Mosh fixes, tested end to end against a real mosh-server:
  - Hosts saved by name work with Mosh (mosh-client only takes an IP address; it failed with "Bad IP address").
  - Typing `exit` ends the session instead of showing a dead screen marked "reconnected".
  - Switching networks (Wi-Fi to mobile data, a VPN) no longer closes Mosh sessions; Mosh roams by itself.
  - Closing a session stops mosh-server and the remote shell; they used to keep running on the server forever.
  - If the Mosh client is killed, a new mosh-server is started over SSH. Before, the restarted client reused the old session key and froze.
  - A Mosh client that exits during startup is reported instead of leaving a dead session marked ready; a missing mosh-server is reported at once instead of after 15 seconds; a possible crash from reading the screen off the main thread is gone; the default server command asks for 256 colours.
- A session left open in the background no longer stops taking input until it is killed. A connection that died silently while the phone slept (no network change) was never noticed: keepalives now expect a reply, so a dead connection reconnects within about a minute, and on returning to the app each open session is checked and reconnected within seconds if the server doesn't answer. Input that stalls for 10 seconds now reconnects too, instead of trying to close the shell over the dead link.
- The connecting screen streams a fuller log, like JuiceSSH: each SSH packet of the handshake as a hexdump. Packets that carry your password, keyboard-interactive answers, commands or terminal data show only their type and size, and the dumps are left out of Send report.
- Built-in keyboard: a single tap on the terminal shows or hides only the top bar again; it no longer opens the keyboard as well. Double-tap or the keyboard key opens it.

Fixes from a fuzz-testing sweep:

- Terminal: output a server (or a file you `cat`) can send no longer freezes or crashes the app. Huge scroll and repeat counts (`CSI 9999 S`, `CSI 9999 b`) with side margins took seconds to minutes on the main thread; OSC 104 colour resets with a bad number, attribute changes in origin mode and a wide character right of the margin threw (over SSH that dropped the rest of the output, over Mosh it crashed). Long-pressing a word in one very long wrapped line no longer stalls, and scrolling inside side margins over wide (CJK) text is fast again.
- Find no longer crashes on Turkish İ in the terminal output.
- Code editor: a line the highlighter can't parse (an emoji right before a quote, an unusual heredoc) is shown without colors instead of ending highlighting for the rest of the file, and a very long run of `<<<` no longer crashes the app.
- Deleting an emoji with keyboards that delete text they already typed now removes just the emoji, not the character before it too.
- Hardware keyboards: AltGr characters (@ [ { ~ on German and other layouts) no longer get an ESC in front, dead keys (´ ^ `) compose instead of disappearing, and Ctrl+@ / Ctrl+? send NUL and DEL like other terminals.
- Hosts and port forwards accept IPv6 addresses (::1, fe80::1) and single-word host names (raspberrypi, db).
- OpenSSH config import works out options the way ssh does: the first value wins, options from several matching Host blocks (including wildcards like `Host *.corp`) are combined, quotes are removed (`User "bob"`), and an invalid Port is reported instead of silently becoming 22.
- Importing a malformed private key no longer crashes the identity editor, and huge or malformed key files no longer freeze it.
- Scanning a host QR no longer overwrites an existing host with the same id, rejects invalid ports, and keeps "attach to tmux"; identity QRs without a fingerprint are rejected.
- SFTP downloads fail with a clear error instead of looping forever on empty server replies or saving a zero-padded file when the file ends early.
- Recursive delete refuses a server listing that would point outside the folder being deleted.
- Mosh startup no longer hangs past its timeout on a prompt without a newline (such as a sudo password prompt).
- "Send report" no longer freezes on long server messages, and reports scrub more addresses and paths.
- Smaller fixes: the system info panel stays fast on odd command output and no longer shows overflowed sizes; LAN transfer has an overall deadline and can always be cancelled; very large transfer files are refused; YAML, XML and Markdown files now get syntax highlighting (their grammars failed to load; YAML uses the TextMate YAML grammar now); OS version labels with a colon survive export; emoji in short key labels are no longer cut in half; tags and info commands with a comma in them survive import.

## 0.11.5 (1105)

Release date: 2026-10-12

- Fixed Enter sometimes doing nothing with the phone's keyboard. Gboard types letters directly into the terminal but sends Enter as a key event, which went to whichever part of the screen had focus at that moment; tapping the terminal briefly moves focus, so an Enter pressed then was lost while letters still worked. Keys from the phone's keyboard now always go to the terminal. Also fixed for keyboards that hold the last word back until you press Enter (Enter could be sent before the word) and keyboards that send Enter as an editor action (focus moved off the terminal).
- New Paste item in the terminal's ⋮ menu.
- Built-in keyboard: a tap that slides slightly off a key, or lands in the gap between two keys, now types that key instead of being ignored.
- Built-in keyboard: opening a running session from its notification no longer brings up the phone's keyboard (such as Gboard) underneath the built-in one. The terminal no longer presents itself to Android as a text field when the built-in keyboard is in use, so Android has nothing to open the system keyboard for.

## 0.11.4 (1104)

Release date: 2026-10-08

- Built-in keyboard mode now draws SSHPeaches' own full keyboard (letters, numbers, two symbol pages, Shift and caps lock, held Backspace repeat, long-press the top row for digits) under the Esc/Ctrl/Fn rows, instead of relying on the phone's keyboard, which on some devices never opened for the terminal. Latched Ctrl, Alt, and Shift from the extra rows apply to its keys; the keyboard key, a tap on the terminal, or Back shows and hides it. Hardware keyboards still work.
- The keyboard editor offers a curated list in five groups: Keys (now with Shift-Tab, Delete and Insert), Shortcuts (Ctrl-C/D/Z/L/R/A/B/E/K/U/W, Ctrl-\, Ctrl-← and Ctrl-→ word jumps, Alt-B, Alt-F, Alt-. and Alt-Backspace), Symbols (30 shell symbols that are awkward on phone keyboards), Actions (keyboard, Paste, Snippets, Insert password, Swipe arrows, Find, Settings), and Custom text with an optional "Press Enter after". Letters, digits, the numpad, and lock/system keys that send nothing to a terminal are no longer offered; layouts that already use them keep working.
- The keyboard editor's key rows and keyboard picture are now the same width on every screen; on tablets the keys no longer squeeze into the left half.
- The snippet editor and the file browser's Edit now use a real code editor (Sora Editor) with Visual Studio Code's syntax grammars: highlighting for shell, JSON, YAML, config/INI, Python, JavaScript, XML, Markdown, and Dockerfile (picked from the file name or shebang), line numbers, auto-indent, bracket matching, undo/redo, find, word wrap, and a row of symbols that are hard to reach on phone keyboards. Colors follow the app's light and dark themes.
- Quick Connect (▶) is now always the rightmost button in the top bar.
- Less-used settings moved to Advanced settings: terminal emulation mode, selection mode, terminal margin, Mosh server command, and snippet run timeout.
- Fixed Redo staying disabled after Undo in the editor.

## 0.11.3 (1103)

Release date: 2026-10-05

- Keyboards saved before this update that still had the swipe-arrows key now show Fn in its place, and a Fn key elsewhere becomes Alt. Swiping for arrow keys is switched on from the session ⋮ menu. A Swipe Nav key added back in the Keyboard Editor stays.
- The ⋮ menu item "Arrow keys" is now "Swipe for arrow keys".
- Fixed an occasional freeze at app start (ANR) caused by Firebase Performance Monitoring reading its config on the main thread; Performance Monitoring is removed.
- With Crash reports on, crash reports now include the screens and session steps leading up to them, and unexpected errors in sessions, file transfers, imports, and the editor are reported without crashing. Host names, addresses, usernames, and file paths are removed first.
- A failed connection offers Send to developer: it shows exactly what will be sent (app version, the error, and the connection log with host names, addresses, usernames, and paths removed), plus an optional note, and sends only when you tap Send. Session logs otherwise stay on the phone.
- Data-collection switches now each do exactly what they say: Usage analytics alone controls Firebase Analytics (Send usage reports no longer kept it on), turning off Crash reports also discards reports not yet sent, and debug builds no longer switch them all back on at launch. Session diagnostics now says what it does: detailed SSH and terminal logs kept on the phone.
- Cancelled background work (for example a setting saved while its screen closed) is no longer reported as an error.
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
