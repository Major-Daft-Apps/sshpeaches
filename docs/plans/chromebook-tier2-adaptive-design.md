# Chromebook Tier 2 Adaptive Design Plan

## Summary

Implement a Tier 2 large-screen design for the management surfaces of the app without redesigning the session/workbench flow in this phase.

The large-screen model is fixed as:
- Wide screens use a permanently expanded left navigation sidebar.
- There is no hamburger button, no rail-only mode, and no minimized sidebar state on wide screens.
- Each vertical has a primary pane that is always visible.
- A secondary pane opens only from explicit user actions such as `Details` or `Edit`.
- The secondary pane is closed by default and never auto-opens on initial load.
- Security-sensitive and blocking flows remain modal even on wide screens.
- Compact screens keep the current single-pane and modal-heavy behavior.
- `Connecting / Session` remains structurally unchanged in this phase.
- `Uptime` is phase-1 single-pane with improved spacing and sizing; no required detail pane in this phase.

## Key Changes

### Adaptive shell

Replace the current wide-screen drawer behavior with an adaptive shell that has:
- A permanent expanded sidebar on wide screens.
- A top bar with global actions only: search, quick connect, active session affordance, settings/context actions.
- No navigation reveal control on wide screens.
- A content area that supports a primary pane plus optional secondary pane.

Use the official adaptive Compose stack as the default implementation direction:
- `androidx.compose.material3.adaptive`
- `androidx.compose.material3.adaptive.navigation`
- `androidx.compose.material3.adaptive.layout`

Do not add a session-tab redesign, terminal-tab model, or utility-pane session refactor in this phase.

### Screen behaviors

Apply the following large-screen behavior by vertical:

- `Home`
  Primary pane shows dashboard content.
  Secondary pane may show item detail when opened from the dashboard.
  Keep QR share, passphrase, and destructive confirmation flows modal.

- `Hosts`
  Primary pane shows search, sort, grouped list, and selection state.
  Secondary pane shows host details and host editing.
  Password entry, QR scan/import decryption, and destructive confirmations stay modal.

- `Identities`
  Primary pane shows list and search.
  Secondary pane shows identity details and editing.
  Private key import prompts, passphrases, QR prompts, and destructive confirmations stay modal.

- `Port Forwards`
  Primary pane shows list and filters.
  Secondary pane shows forward details and editing.
  QR flows and destructive confirmations stay modal.

- `Snippets`
  Primary pane shows snippet list and search.
  Secondary pane shows snippet details and editing.
  QR flows and destructive confirmations stay modal.

- `Keyboard Editor`
  Primary pane shows keyboard layout overview and slot map.
  Secondary pane replaces the current `Modify key` modal/editor on wide screens.
  Reset and destructive confirmations stay modal.

- `Theme Editor`
  Primary pane shows theme/profile list and entry points.
  Secondary pane replaces the current `new/edit theme` and terminal profile editing flow on wide screens.
  Keep delete confirmations modal.

- `Settings`
  Primary pane shows grouped settings categories or sections.
  Secondary pane shows selected settings sub-editors where appropriate.
  PIN, password, biometric, restore-defaults, and QR-related flows remain modal.

- `Uptime`
  Keep single-pane in this phase.
  Improve wide-screen width, spacing, and content density only.
  Do not introduce a required detail pane unless a later phase specifically expands uptime editing.

- `Connecting / Session`
  Keep the current structure and interaction model unchanged in this phase.

- `Quick Connect`
  On wide screens, use a centered dialog rather than a bottom sheet.
  On compact screens, keep the current bottom-sheet or dialog behavior.

### Public interfaces and state changes

Introduce an explicit adaptive shell contract so the implementation does not improvise per screen:
- A shell-level layout mode: `compact` vs `wide`.
- A shell-level pane model: `secondary pane closed` vs `secondary pane open`.
- Per-screen pane targets for details/editors, for example `host detail`, `host edit`, `identity edit`, `theme edit`, `keyboard key edit`.
- A uniform unsaved-changes policy for pane editors: closing the pane or switching selection must require explicit save/discard handling when dirty.
- A uniform compact fallback policy: any wide-screen pane editor must map to an existing full-screen route or dialog on compact layouts.

Do not change persisted data schemas for this phase.
Do not add new backend/service interfaces for SSH, SFTP, SCP, or Mosh in this phase.

### Input and Chromebook Tier 2 polish

Plan for these Tier 2 input improvements after the adaptive shell and pane model are in place:
- App-level keyboard navigation outside the terminal.
- Standard keyboard shortcuts where applicable.
- Right-click context menus for list items and actionable cards.
- Hover states and pointer affordances for clickable/editable items.
- Drag-and-drop file ingress from ChromeOS Files where feasible.

These are part of the Tier 2 target, but the first implementation milestone should prioritize the shell and pane architecture before broad input polish.

## Test Plan

Validate both compact and wide-screen behavior using a Chromebook-class emulator profile, with `Resizable_Experimental` as the default primary review target.

Required scenarios:
- Wide-screen shell shows a permanent expanded sidebar with no hamburger control.
- Sidebar remains visible across all in-scope verticals.
- Primary pane state persists when the secondary pane opens and closes.
- Secondary pane is closed on first load for each vertical.
- Secondary pane opens only from explicit user action.
- Hosts, Identities, Port Forwards, Snippets, Keyboard Editor, Theme Editor, and Settings follow the pane rules on wide screens.
- Quick Connect opens as a centered dialog on wide screens.
- Security-sensitive flows remain modal on wide screens.
- Compact layouts preserve current behavior and remain functionally equivalent.
- Session screen behavior is unchanged from the current implementation.
- Unsaved changes in a pane editor require explicit discard/save resolution before pane close or selection change.

Review artifacts expected from the implementing host:
- One screenshot of the wide-screen shell with sidebar only.
- One screenshot with a secondary pane open.
- One compact-layout screenshot for fallback comparison.
- One short emulator recording demonstrating sidebar persistence and pane open/close behavior.

## Assumptions and defaults

- Wide-screen means a layout class large enough to justify a permanent sidebar and optional secondary pane; the implementation should use the adaptive Compose libraries to determine this rather than hard-coded ad hoc breakpoints.
- The wide-screen navigation pattern is a permanent expanded sidebar, not a rail and not a collapsible sidebar.
- `Uptime` is intentionally deferred from mandatory pane treatment in phase 1.
- `Connecting / Session` is explicitly out of scope for structural redesign in this plan.
- Passwords, passphrases, biometric prompts, delete confirmations, QR scan/import prompts, and similar blocking/security-sensitive interactions stay modal on wide screens.
