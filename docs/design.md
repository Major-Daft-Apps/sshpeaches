# SSHPeaches UI Design Notes

This document captures first-pass layouts for key SSHPeaches screens. Use it as a blueprint when building mockups (Figma/Penpot) and implementing Compose UI.

---

## 1. Favorites (Landing) Screen
```
┌──────────────── App Bar ───────────────┐
│ ☰  SSHPeaches                         ⦿│  (⦿ = Quick Connect icon)
└───────────────────────────────────────┘
```
- `Quick Connect` icon opens ad-hoc bottom sheet. Drawer also includes Help (external site via Custom Tab) and About (modal moved out of Settings; shows icon, version, build info, license links). Global theme uses Carbon Black `#191919` backgrounds with Blazing Flame `#F15025` highlight accents.
- Body uses vertically stacked sections with sticky headers:
  - **Hosts** – list of favorited hosts. Each card mirrors Hosts layout (OS icon, name, tags, buttons). Include inline ★ toggle to unfavorite.
  - **Identities** – compact rows showing key alias, fingerprint, tag, and actions (Edit/Delete/Use).
  - **Port Forwards** – cards summarizing local ↔ remote mapping, protocol, associated host; inline ★ toggle.
- Empty state copy if a section has no favorites.

## 2. Hosts Screen
- Same app bar.
- Secondary toolbar directly beneath header:
  ```
  ┌──────────── Toolbar ─────────────┐
  │ Edit / Done           New        │
  └──────────────────────────────────┘
  ```
  - Left button toggles edit mode (reveals pencil/dash overlays).
  - Right button opens New Connection form.
- Content:
  - Search bar with filter pill for Groups and triple-dot menu for sorting (Last Used / A→Z).
- Card layout mirrors Favorites view. OS icon: white glyph on colored background tied to distro (e.g., Ubuntu orange, Debian red, Fedora blue, SUSE green, Mint teal, macOS gray, BSD maroon, Arch cyan). Default desktop icon used until detection runs.

## 3. Quick Connect Sheet
```
┌────────────── Quick Connect ──────────────┐
│ Host/IP        [_______________________]  │
│ Port (22)      [__]   Username [______]   │
│ Auth           (•) Password ( ) Identity  │
│                ( ) Both (identity first)  │
│ Password/Input field or Identity picker   │
│ Forwarded Port [Dropdown of saved forwards]│
│ Optional Script [Inline editor + file btn]│
│ Toggles: [ ] Mosh   [ ] Pin to Favorites  │
│ Sticky chips for “Last used hosts/users”  │
│ [Cancel]                  [Connect]       │
└───────────────────────────────────────────┘
```

## 4. New Connection Flow
- Modal or dedicated screen with tabs **Basic** / **Advanced**.
- **Basic**: Name, Host, Username, Password field, Identity picker (allow both). “Add to Group” button.
- **Advanced**:
  - Port input (default 22).
  - Forwarded port selector (dropdown of configured forwards).
  - Optional script editor (multi-line text field) + “Choose file” button.
  - Checkbox for Mosh fallback.
  - Background behavior toggle (inherit global / always allow / never allow).
  - Info panel preset link (“Edit Info Commands”).
- Footer buttons: Cancel / Save / Connect Now.

## 5. Info Panel & Editor
### Info Panel Modal
```
┌───────────── Host Info ─────────────┐
│ Command label   [Run on demand ▷]   │
│ Output (monospace box, collapsible) │
│ ... (one block per command)         │
│ [Edit Commands]                     │
└─────────────────────────────────────┘
```
### Editor Sheet
- Reorderable list of cards, each containing:
  - Label text field.
  - Command textarea.
  - Switch: Auto-run on open.
  - Drag handle (≡) and delete icon.
- Buttons: `+ Add Command`, `Reset to defaults`, `Preview`.

## 6. Identities Screen
- Toolbar with search.
- Rows show key alias, short fingerprint (truncate SHA-256 to 8–10 chars or colon-separated hex), last used, and icons for edit, export, delete.
- FAB (`+`) opens menu: “Enter manually”, “Import from file”, “Generate keypair”.
- Each identity row has a star icon for Favorites.

## 7. Snippet Manager
- Accessible from drawer and from session toolbar.
- Main view:
  - Filter chips for tags (Diagnostics, Deploy, Maintenance, etc.).
  - Snippet cards show title, short description, favorite icon, edit/delete buttons, and quick “Insert” action.
  - Toggle “Auto-run on connect” per snippet; indicator showing attached hosts/groups.
- Editor view:
  - Fields: Name, Description, Command (multi-line with syntax highlighting), Tags, Variables (placeholder chips like `{host}`, `{user}`, `{custom}`).
  - Options: “Require confirmation before run,” “Auto-run when host connects,” “Share via QR.”
  - Buttons: Save, Run Now (select target session), Share.
- In-session UI:
  - “Snippets” button in toolbar opens bottom sheet listing favorites + recents with search, variable prompts, and quick run buttons.
  - Auto-run snippets display inline confirmation chips when triggered.

## 8. Port Forwards Screen
- Mirrors JuiceSSH Pro behavior.
- Tabs for Local, Remote, Dynamic.
- Each forward entry includes:
  - Title (e.g., “Local 9000 → 10.0.0.5:5432”).
  - Bind address input (default 127.0.0.1 for local/dynamic, remote host for remote).
  - Associated host dropdown (multi-select) to auto-start when specified hosts connect.
  - Manual enable/disable toggle (works even when not connected).
  - Icons: star (favorite), edit (pencil), delete (trash).
- FAB opens “Add Port Forward” sheet with fields:
  - Type selector (Local/Remote/Dynamic).
  - Source port + bind address.
  - Destination host/port (for Local and Remote; dynamic only needs local bind/port).
  - Host association multi-select.
  - Notes/description field.
- During sessions, toolbar indicator shows active forwards with toggles/status badges; disabling stops the tunnel but keeps SSH session alive. Toast/notification confirms success/failure.

## 9. Keyboard Editor
```
 ┌──────── Key Palette (scrollable) ────────┐
 │ Row 1: core toggles (ESC, TAB, CTRL, ALT,│
 │        SHIFT, Swipe→Arrow, Swipe→Scroll) │
 │ Dropdown groups: [Fn ▾] [a ▾] [A ▾] [1 ▾]│
 │ [# ▾] for F1–F12, lowercase, uppercase,  │
 │ numbers, and symbols. Drag options out   │
 │ of the dropdown onto rows.               │
 └──────────────────────────────────────────┘
┌────── Row Controls ─────┐   ┌────── Row 1 ───────┐
│ Row 1  [ + Add Row ]    │   │ ESC TAB CTRL ...    │
│ Compact keys [ ]        │   └─────────────────────┘
└─────────────────────────┘   (drag destinations)
```
- Users drag from palette to any row; keys behave like movable blocks within each row (free placement along the row’s width, no strict grid).
- Sticky swipe-to-arrow toggle is always present (cannot remove, but can reposition).
- Keyboard Editor shortcut key available in palette.

## 10. QR Share / Export UI
- **Share Host**: bottom sheet shows QR preview with pagination dots, “Frame X/Y,” checksum badge, `Pause`, `Resume`, `Save as PNG`.
- **Export All**: similar but includes progress bar and “Include identities / settings” checkboxes. Scanner UI for import shows live feed with progress counter and error recovery panel.

## 11. Security & Background Settings
- Section includes:
  - Toggle: “Allow shells to run in background.”
  - Dropdown: “Default per-connection behavior” (Inherit / Always allow / Always stop).
  - Toggle: “Require biometric unlock” with timeout selector (Immediate, 1 min, 5 min, 15 min, Custom). Shows preview of biometric prompt + fallback PIN flow.
  - Info copy about battery/network usage and privacy.
- Connection-specific override (background behavior) appears in Advanced tab as described above.

## 12. App Lock Screen
```
┌─────────────── SSHPeaches Lock ───────────────┐
│   (Peach logo)                                │
│   SSHPeaches is locked                        │
│   Last unlocked: 2m ago                       │
│                                               │
│ [Fingerprint icon] Touch to unlock            │
│ [PIN fallback button]                         │
│                                               │
│ “Use different account” / “Switch identity”   │
└───────────────────────────────────────────────┘
```
- Triggered after inactivity timeout or manual lock.
- Shows branding, last-unlocked timestamp, and optional message if a session is running in background.
- Primary action: biometric prompt (fingerprint/face). If unavailable/fails, fallback button opens PIN/password entry sheet.
- Device’s back button exits app (sessions paused until unlock). If background sessions allowed, notification indicates “Locked – session running”.
- Settings link (gear icon) appears if user needs to adjust timeout once authenticated.

---

### Next Steps
- Create Figma frames for each screen with the black-and-orange palette.
- Define shared components (cards, buttons, toggles) as reusable variants.
- Link frames to blueprint requirements and note interactions (Edit mode, Info editor, QR scanner).
