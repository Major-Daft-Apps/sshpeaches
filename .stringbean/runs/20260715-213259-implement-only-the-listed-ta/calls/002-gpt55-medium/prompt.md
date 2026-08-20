Stringbean execution policy:
- Ordinary remote processing of task text and non-excluded, in-scope workspace context by Stringbean's configured hosted providers is inherent to this requested run; do not pause for separate provider-use approval.
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.
- Default workspace boundary: /home/zenulabidin/Documents/sshpeaches. Do not inspect unrelated parent or sibling paths. A path explicitly named by the user's task is in scope unless an excluded-path rule protects it.
- Ordered excluded-path rules (`!` means an allowed exception): .stringbean/runs, .stringbean/runs/**, **/.stringbean/runs, **/.stringbean/runs/**, .env*, **/.env*, !.env.example, !**/.env.example, !.env.sample, !**/.env.sample, !.env.template, !**/.env.template, .secrets, .secrets/**, **/.secrets, **/.secrets/**, secrets, secrets/**, **/secrets, **/secrets/**, credentials, credentials/**, **/credentials, **/credentials/**, credentials.json, **/credentials.json, service-account*.json, **/service-account*.json, *.pem, **/*.pem, *.key, **/*.key, *.p12, **/*.p12, *.pfx, **/*.pfx. Built-in credential exclusions are mandatory: a project or configured `!` rule can only re-include paths excluded by other project-added rules.
- Never read, list, search, summarize, modify, or transmit content from paths excluded by those rules.
- If an excluded path appears relevant, skip it and continue with the rest of the task. Do not retry access, ask the user for permission to inspect it, or ask another agent to inspect it.

You are the architecture advisor.
Review this plan and provide one structured response.

Task:
Implement only the listed task and avoid unrelated changes.

Task:
Implement the saved plan in docs/plans/ctrl-ime-copy-paste-fix.md for the Ctrl/IME modifier-loss regression after terminal Copy or Paste. Work directly in the current repository and do not stop at analysis: make the implementation and test changes, then validate them. Follow the plan's regression-first phases, including reliable forced selection dismissal, IME bridge focus restoration without changing the user's keyboard visibility choice, modifier-complete unified Android key routing, standalone-emulator codepoint routing, and the focused exact-byte/selection/focus/router tests. Preserve all existing dirty-worktree changes and carefully integrate with overlapping edits; do not discard or overwrite unrelated user work. Keep the hidden-editor sentinel cleanup separate/optional if it raises patch risk. Run the strongest relevant focused tests available in this environment and report exact results, remaining limitations, modified artifacts, task count, and review rounds.

Objective:
task-7 - Route standalone-emulator codepoints to the client

Plan task:
{
  "id": "task-7",
  "title": "Route standalone-emulator codepoints to the client",
  "description": "Update third_party/termux-playstore/terminal-view/src/main/java/com/termux/view/TerminalView.java inputCodePoint() so it computes effective Ctrl and Alt state before the TerminalSession null check, offers the codepoint to mClient.onCodePoint() even when mTermSession is null, returns when the client handles it, and only requires a real TerminalSession for the default Termux writeCodePoint fallback. Add a focused terminal-view or instrumentation test proving commitText(\"a\") reaches the client with a standalone TerminalEmulator attached and no TerminalSession.",
  "dependencies": [
    "task-6"
  ],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "Run the closest focused terminal-view test task available.",
    "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'"
  ]
}

Required verification:
Run the closest focused terminal-view test task available.
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'

Known constraints:
Run the closest focused terminal-view test task available.
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-6

Return JSON:
{
  "status": "completed",
  "summary": "...",
  "files_changed": [],
  "commands_run": [],
  "tests": [],
  "remaining_issues": [],
  "handoff_notes": []
}

Plan:
{
  "summary": "Implement task-7 by first adding focused regression coverage for standalone TerminalEmulator codepoint delivery without a TerminalSession, then update TerminalView.inputCodePoint() so client codepoint routing happens before the TerminalSession fallback requirement while preserving modifier semantics from task-6.",
  "assumptions": [
    "task-6 changes are already present or will be preserved as the baseline for modifier-complete Android key routing.",
    "The relevant implementation target is third_party/termux-playstore/terminal-view/src/main/java/com/termux/view/TerminalView.java.",
    "A terminal-view JVM or Android test source set already exists or can accept a focused test with minimal local scaffolding.",
    "No hidden-editor sentinel cleanup is required for task-7 unless touched code makes it unavoidable."
  ],
  "tasks": [
    {
      "id": "task-1",
      "title": "Inspect Existing Terminal Input Flow",
      "description": "Read docs/plans/ctrl-ime-copy-paste-fix.md, TerminalView.inputCodePoint(), TerminalViewClient.onCodePoint(), and existing terminal-view tests to identify the smallest safe test location and preserve overlapping dirty-worktree edits.",
      "dependencies": [],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": []
    },
    {
      "id": "task-2",
      "title": "Add Standalone Emulator Regression Test",
      "description": "Add a focused regression test proving IME commitText(\"a\") or the closest direct inputCodePoint path reaches a TerminalViewClient when TerminalView has a standalone TerminalEmulator attached and no TerminalSession. Assert the client receives codepoint 97 with the effective Ctrl/Alt state and that no session write fallback is required.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_write",
      "verification": [
        "Run the closest focused terminal-view test task available and confirm the new test fails before implementation if practical."
      ]
    },
    {
      "id": "task-3",
      "title": "Route Client Codepoints Before Session Fallback",
      "description": "Update TerminalView.inputCodePoint() to compute effective Ctrl and Alt state before checking mTermSession, call mClient.onCodePoint(codePoint, ctrlDown, altDown) whenever a client exists even if mTermSession is null, return immediately when the client handles the codepoint, and require a non-null TerminalSession only for the default writeCodePoint fallback.",
      "dependencies": [
        "task-2"
      ],
      "recommended_role": "implementer",
      "permissions": "read_write",
      "verification": []
    },
    {
      "id": "task-4",
      "title": "Preserve Modifier Routing Semantics",
      "description": "Confirm the task-7 change does not regress task-6 behavior: effective Ctrl/Alt must include forced modifier state, hardware/meta state, and IME bridge state exactly as existing task-6 code expects. Avoid changing keyboard visibility, selection dismissal, focus restoration, or hidden-editor cleanup paths unless required by compilation.",
      "dependencies": [
        "task-3"
      ],
      "recommended_role": "implementer",
      "permissions": "read_write",
      "verification": []
    },
    {
      "id": "task-5",
      "title": "Run Focused Verification",
      "description": "Run the strongest focused terminal-view test command available for the modified module, then run the required app instrumentation command.",
      "dependencies": [
        "task-4"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Run the closest focused terminal-view test task available.",
        "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest'"
      ]
    },
    {
      "id": "task-6",
      "title": "Report Results",
      "description": "Return exact modified artifacts, commands, test outcomes, remaining limitations, task count, and review rounds. Explicitly note if any required instrumentation test cannot run because no emulator/device is available.",
      "dependencies": [
        "task-5"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": []
    }
  ],
  "risks": [
    "Existing dirty-worktree edits may overlap TerminalView.java or terminal-view tests; implementation must inspect and integrate without reverting unrelated changes.",
    "The terminal-view module may not have a convenient JVM test harness for TerminalView, requiring a focused Android test or narrowly-scoped test fixture.",
    "The required connectedDebugAndroidTest command depends on an attached emulator/device and may fail for environment reasons unrelated to the patch.",
    "Calling mClient.onCodePoint before the TerminalSession fallback could expose null-session assumptions in client implementations; the test should use a minimal client and the implementation should keep null checks explicit."
  ],
  "advisor_questions": []
}

Return JSON:
{
  "verdict": "approve|revise|block",
  "severity": "none",
  "summary": "...",
  "blockers": [],
  "concerns": [],
  "recommendations": []
}
