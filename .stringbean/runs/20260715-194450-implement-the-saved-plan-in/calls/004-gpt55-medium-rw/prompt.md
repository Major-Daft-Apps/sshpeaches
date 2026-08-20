Stringbean execution policy:
- Ordinary remote processing of task text and non-excluded, in-scope workspace context by Stringbean's configured hosted providers is inherent to this requested run; do not pause for separate provider-use approval.
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_write.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.
- Default workspace boundary: /home/zenulabidin/Documents/sshpeaches. Do not inspect unrelated parent or sibling paths. A path explicitly named by the user's task is in scope unless an excluded-path rule protects it.
- Ordered excluded-path rules (`!` means an allowed exception): .stringbean/runs, .stringbean/runs/**, **/.stringbean/runs, **/.stringbean/runs/**, .env*, **/.env*, !.env.example, !**/.env.example, !.env.sample, !**/.env.sample, !.env.template, !**/.env.template, .secrets, .secrets/**, **/.secrets, **/.secrets/**, secrets, secrets/**, **/secrets, **/secrets/**, credentials, credentials/**, **/credentials, **/credentials/**, credentials.json, **/credentials.json, service-account*.json, **/service-account*.json, *.pem, **/*.pem, *.key, **/*.key, *.p12, **/*.p12, *.pfx, **/*.pfx. Built-in credential exclusions are mandatory: a project or configured `!` rule can only re-include paths excluded by other project-added rules.
- Never read, list, search, summarize, modify, or transmit content from paths excluded by those rules.
- If an excluded path appears relevant, skip it and continue with the rest of the task. Do not retry access, ask the user for permission to inspect it, or ask another agent to inspect it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
Implement the saved plan in docs/plans/ctrl-ime-copy-paste-fix.md for the Ctrl/IME modifier-loss regression after terminal Copy or Paste. Work directly in the current repository and do not stop at analysis: make the implementation and test changes, then validate them. Follow the plan's regression-first phases, including reliable forced selection dismissal, IME bridge focus restoration without changing the user's keyboard visibility choice, modifier-complete unified Android key routing, standalone-emulator codepoint routing, and the focused exact-byte/selection/focus/router tests. Preserve all existing dirty-worktree changes and carefully integrate with overlapping edits; do not discard or overwrite unrelated user work. Keep the hidden-editor sentinel cleanup separate/optional if it raises patch risk. Run the strongest relevant focused tests available in this environment and report exact results, remaining limitations, modified artifacts, task count, and review rounds.

Objective:
task-1 - Add regression-first exact-byte tests for terminal Copy and Paste

Plan task:
{
  "id": "task-1",
  "title": "Add regression-first exact-byte tests for terminal Copy and Paste",
  "description": "Extend app/src/androidTest/java/com/sshpeaches/app/session_ui/ConnectingScreenTest.kt with focused tests named terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce and terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce. Each test should start terminal selection, invoke Copy or Paste immediately without sleeping, clear setup and paste bytes from the captured sink, tap compact Ctrl, dispatch an unmodified virtual KEYCODE_A through the TerminalView path, dispatch a second identical A, and assert the exact ordered payload bytes are [0x01, 0x61]. Replace or narrow the existing compactModifierWorksAfterTerminalSelectionCopyAndPaste coverage so it no longer hides the race with SystemClock.sleep(350) or bypasses the affected Android key route.",
  "dependencies": [],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce'",
    "./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce'",
    "Before the implementation tasks, these tests should reproduce the regression as [0x61, 0x61] or equivalent focus/routing failure."
  ]
}

Required verification:
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce'
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce'
Before the implementation tasks, these tests should reproduce the regression as [0x61, 0x61] or equivalent focus/routing failure.

Known constraints:
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionCopy_virtualImeKeyConsumesLatchedCtrlOnce'
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.majordaftapps.sshpeaches.app.session_ui.ConnectingScreenTest#terminalSelectionPaste_virtualImeKeyConsumesLatchedCtrlOnce'
Before the implementation tasks, these tests should reproduce the regression as [0x61, 0x61] or equivalent focus/routing failure.

Prior constraints/advisor notes:
implementer

Allowed file scope:


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
