# SSHPeaches Product Blueprint

> **Last updated:** 2026-01-30 · See `docs/design.md` for corresponding UI layouts.

## Vision & Goals
- Provide a trustworthy, modern Android-phone/tablet SSH/Mosh client that feels tailored for touch interaction yet powerful enough for professional administrators.
- Ship a Play Store-ready app within 6–8 months, launching as *SSHPeaches* with all features available for free at launch.
- Differentiate through first-class key management, offline-friendly sharing, and polished UX (Material You, accessibility, gesture support).

## Target Users & Personas
1. **On-call SRE (“Nadia”)** – needs dependable connections, multi-factor auth, fast host switching, audit trail.
2. **Indie developer (“Leo”)** – values customization, fast remote access, simple offline backups (QR export, USB).
3. **IT consultant (“Priya”)** – jumps between client environments, wants per-client folders, shared settings, and quick macros.

## Constraints & Principles
- Android 8.0+ (API 26) baseline on phones/tablets; Kotlin-first, Jetpack Compose UI; minimize native code.
- Respect user security: private keys never leave device unencrypted; biometric unlock required for sensitive actions.
- Offline-first: core SSH must work without network services; QR-based peer sync avoids recurring cloud costs.
- Transparent roadmap: launch fully free; revisit optional donations or support packages only after stable release.
- Scope boundary: no Telnet or on-device local shell modes to maintain focus on SSH/SFTP/SCP/Mosh.

## Feature Set
- Favorites tab is default landing view, aggregating favorited hosts, identities, and forwarded ports; cards appear in a three-section list (Hosts, Identities, Ports) with section headers and inline unfavorite icons. Hamburger menu opens other sections (Quick Connect, Hosts, Identities, Port Forwards, Keyboard Editor, Settings, Help, About). Help launches the official support website; About opens an in-app sheet with logo, version, build info, and links to licenses.
- Multi-mode sessions: per-host quick-launch icon buttons (large icon + label such as “SSH”, “SFTP”, “SCP”, “Info”, “Move”) covering terminal, full two-way SFTP browser (FileZilla-style), SCP “Quick Transfer”, and informational panels; supports multiple simultaneous sessions, reconnect, keep-alive, and agent forwarding (where possible via ssh-agent emulation).
- SFTP explorer: dual-pane (local/remote) view with drag-to-queue transfers, resumable progress, bookmarks, and transfer history.
- Credential vault: password + key pair auth managed in an Identities screen with FAB (“+”) in bottom-right to add via manual entry, file import, or on-device key generation; defaults to last-used sort with alphabetical option, plus secure notes and tagging. Fingerprints display in short mobile-friendly form (e.g., SHA-256 truncated to 8–10 chars or classic colon-separated hex).
- Connection manager: folders, search, favorites, quick actions, import/export (JSON/URI), inline icon buttons (SSH, SFTP, SCP, Info, Move), configurable default mode, and a secondary toolbar just below the header with “Edit/Done” on the left and “New” on the right. Sorting (Last Used / A→Z) moves into a triple-dot menu beside the search bar. Tapping Edit reveals pencil/delete icons on cards; “New” opens the connection form. Hosts screen accessible from drawer; cards show OS icon (left), host/group labels, last-used timestamp, and large action buttons underneath.
- Quick Connect panel: drawer shortcut opens a bottom sheet with Host/IP, Port (default 22), Username, auth selector (password, identity, both), Mosh toggle, forwarded-port dropdown, and optional script picker; includes “Connect” + “Pin to Favorites” buttons and retains last-used values for fast reuse.
- New Connection sheet: Basic section (Name, Host, Username, Password/Identity selection supporting both simultaneously—identity attempted first). Advanced section adds Port, “Use forwarded port” selector (pulls from configured forwards), optional shell script (inline Bourne shell editor or file picker), checkbox for Mosh, and “Add to Group” control similar to JuiceSSH. No local shell or Telnet modes.
- Port forwards manager: tabs for Local/Remote/Dynamic entries; each forward has bind/destination fields, host association (auto-start when that host connects), manual enable toggle, favorite icon, and edit/delete controls. Session toolbar shows active forward indicator for toggling on/off mid-session with toast notifications on success/failure.
- Per-connection Info panel: “Info” button runs editable command presets (default: `uname -a`, `uptime`, `who`, `free -h`, `df -h` in that order) to show host diagnostics; presets configurable per connection.
- Info panel editor: accessible from connection settings or within the Info panel via “Edit Commands,” presenting ordered list of shell snippets (fields for label + command). Users can add/remove/reorder commands, toggle “Run automatically” vs “Run on demand,” and preview output formatting.
- Snippet manager: global library of reusable commands/scripts grouped by tags; snippets can insert variables (e.g., `{host}`, `{user}`), be pinned to hosts/groups for auto-run, shared via QR/export, and triggered from the session toolbar.
- OS detection & branding: on each successful connect we parse `uname`/`/etc/os-release` (if present) to assign distro/OS icons (Ubuntu, Debian, RHEL, CentOS, Linux Mint, Arch, Gentoo, Fedora, SUSE/openSUSE, Pop!_OS, Manjaro, elementaryOS, Peppermint OS, Linux Lite, Zorin, Rocky, Alma, Asahi, NixOS, macOS, BSD variants). Icons use white glyphs on distro-colored backgrounds (e.g., Ubuntu orange, Fedora blue, SUSE green). Unknown systems fall back to a generic Linux icon; never-connected hosts show a default desktop icon.
- QR export/import flows: Move icon triggers single-host share via QR; “Export All” (hosts, keys metadata, settings) splits into multi-frame codes with checksum/resume support. Scanner UI shows frame counter, checksum indicator, and pause/resume controls.
- Default keyboard row: ESC, TAB, CTRL, HOME, END, `/`, `-`, `_`, sticky swipe-to-arrow toggle (always available; control only switches sticky vs one-shot), swipe-to-scroll toggle, and Keyboard Editor shortcut. Users can replace/extend this via the Keyboard Editor pane, which provides a tiered key palette (modifiers, navigation toggles, plus dropdown families Fn, lowercase letters, uppercase letters, numbers, symbols). Drag & drop behaves like a freeform canvas per row—keys can be positioned anywhere without grid constraints; up to three rows (default single row, “+” adds more), plus optional compact-key mode.
- Logging & diagnostics: session history with timestamps, crash reporting, optional anonymized analytics.
- Background session control: global setting (“Run shells in background”) determines whether SSH/Mosh sessions can continue running when the app is backgrounded; per-connection override available.
- Security lock: Settings include “Require biometric unlock after X minutes of inactivity” (configurable timeout, immediate lock option). App returns to lock screen when triggered, protecting identities and sessions.

### Differentiators
- Offline QR sync: encode connection/credential bundles (serialized via Kotlinx JSON, minified + Deflate-compressed before encryption) into QR frames with multi-frame playback plus LAN discovery option.
- Port forwarding manager modeled after JuiceSSH Pro: define Local/Remote/Dynamic forwards globally, attach them to hosts for auto-activation, and toggle them per session (with notifications on success/failure).
- Automation snippets/macros triggered on connect; quick buttons for frequently used commands.
- Monitoring widgets/tiles (e.g., home screen quick connect).
- Biometric + hardware key (Passkey/YubiKey via NFC/BLE) unlock for key usage.

### Future Enhancements
- EC2/Linode/Vultr discovery integrations.
- Team sharing once we introduce a backend sync service.
- Real-time collaboration (pair terminal view).
- Configurable terminal rendering backend (GPU-accelerated).
- Plugin marketplace (long-term).

## Technical Architecture
### Client Layers
- **Presentation**: Jetpack Compose, Material 3, Navigation Component, ViewModels.
- **Domain**: Kotlin coroutines + Flow, use case classes orchestrating SSH actions, connection state, and preferences.
- **Data**: Repository pattern storing metadata in Room database, secrets in Android Keystore–backed encrypted storage; optional sync service interfacing with backend APIs.

### Terminal & Protocol Stack
- Utilize `sshj` or `JSCH` fork for SSH; evaluate open-source terminal emulator (e.g., `blink-caret/term`) for rendering; wrap in Kotlin module with state reducers.
- Mosh support through native library compiled with NDK (Phase 2).
- File transfers via SFTP module (sshj) powering dual-pane explorer plus SCP helper for single-shot transfers.

### Security
- All private keys stored with AES-256 keys derived from Android Keystore; optional user-supplied passphrase; biometric gate.
- Zero-knowledge QR/LAN sync: secrets encrypted locally before encoding/transit; metadata minimized.
- Crash/analytics opt-in; logs scrub sensitive data.
- Automated security testing: static analysis (Detekt, Android Lint), dependency scanning (OWASP Dependency-Check or Gradle plugins).
- Strict host-key checks: surface host key fingerprints on first connect, prompt on mismatches before proceeding, and refresh OS identity icons only after trust is established.

### Backend (Optional Purchase Verification)
- No backend required at launch; QR sync keeps everything local/offline.
- Add a lightweight HTTPS endpoint later only to verify optional Play Store donations/support purchases (dummy handler until monetization exists).
- Infra TBD (Cloud Functions, Firebase HTTPS endpoint) but should be stateless and low-cost.

## UX & Visual Design
- **Information Architecture**: Main screen opens on Favorites tab (favorited hosts, identities, forwards) split into sections with inline unfavorite actions. Drawer entries navigate to Quick Connect, Hosts, Identities, Port Forwards, Keyboard Editor, Settings, Help, About. Help item launches the external support site in a Custom Tab; About opens a modal (moved out of Settings) showing logo, version/build, acknowledgments. Quick Connect mirrors JuiceSSH behavior (ad-hoc host entry without saving) via a persistent toolbar shortcut. Floating action button for “New Host” appears on Hosts view.
- **Connection List**: card rows with OS/distro icons (default desktop until OS detected), environment labels, status indicators, secondary toolbar under the app bar (“Edit/Done” on left, “New” on right), large icon buttons with text labels (SSH, SFTP, SCP, Info, Move) along the bottom, default “Last Used” sorting with alphabetical toggle. Info panel uses a modal showing command list, outputs, and Edit Commands entry point.
- **Info Panel Editor**: sheet with reorderable list of command cards (label + shell snippet + auto-run toggle). Includes buttons for Add Command, Reset to defaults, and Preview output; drag handles for ordering and delete icons for removal.
- **Quick Connect Sheet**: bottom sheet with Host/IP, Port, Username, auth toggle (password/identity/both), forwarded-port selector, optional script picker, and Mosh toggle; includes sticky “last used” chip row and buttons for Connect + Pin to Favorites.
- **Terminal Screen**: edge-to-edge black canvas, custom toolbar (latency, toggle keep-awake, keyboard row, background toggle reflecting the global “Run shells in background” setting with per-connection overrides) with default row (ESC, TAB, CTRL, HOME, END, `/`, `-`, `_`, sticky swipe-to-arrow toggle, swipe-to-scroll toggle, Keyboard Editor shortcut). Users can redesign via the Keyboard Editor (drag from palette, add rows, compact mode). Gestures: pinch zoom, two-finger pan, hold for selection.
- **Key Vault**: identities tab with floating “+” button (bottom-right) offering manual entry, file import, or keypair generation; defaults to “Last Used” sorting with alphabetical toggle, visual strength indicators, and tagging for all secrets.
- **Theming**: Material You dynamic colors + curated Carbon Black (`#191919`) + Blazing Flame (`#F15025`) palette distinct from JuiceSSH’s black/yellow, emphasizing high-contrast terminals; typography optimized for monospace readability; accessibility guidelines (min 4.5:1 contrast, adjustable font scaling).
- **Empty States & Guidance**: friendly copy, quick-start cards, in-app tutorials (coach marks).
- **Branding**: playful “peach” accent with black-and-orange palette, but professional tone; simple peach pit logo adaptable for launcher icon + marketing.

## Roadmap (Tentative)
1. **Phase 0 – Research & Technical Spikes (Month 0-1)**: Evaluate SSH libraries, prototype terminal rendering, confirm key storage approach, survey competitors.
2. **Phase 1 – Core MVP Build (Month 1-4)**: Implement architecture skeleton, connection manager, SSH sessions, keyboard, settings. Internal dogfooding.
3. **Phase 2 – Beta & QR Polish (Month 4-6)**: Add QR sync flow, port forwarding, automation, analytics, polish UX, prepare Play Store listing, closed beta.
4. **Phase 3 – Public Launch (Month 6-8)**: Harden security, finalize marketing site, documentation, open beta → production rollout.
5. **Phase 4 – Post-Launch (ongoing)**: Iterate on Mosh and enterprise-requested features; explore optional donations/supporter perks without gating functionality.

## Open Questions & Next Steps
- Decide licensing of 3rd-party SSH/terminal libs (compatibility with Play Store + monetization).
- Decide if/when to add optional donations/support tiers while keeping all functionality free.
- Determine minimal infra for any future purchase-verification endpoint.
- Begin brand asset production (logos, palette) for SSHPeaches.
- Define QR sync technical approach (chunking, encryption, error correction) and add to spike list.
- Next actions: create detailed PRD, wireframes (Figma), technical spike tickets, and initial repository scaffolding (Gradle/Kotlin setup).
