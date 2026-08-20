Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_write.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.
- Default workspace boundary: /home/zenulabidin/Documents/sshpeaches. Do not inspect unrelated parent or sibling paths. A path explicitly named by the user's task is in scope unless an excluded-path rule protects it.
- Ordered excluded-path rules (`!` means an allowed exception): .stringbean/runs, .stringbean/runs/**, **/.stringbean/runs, **/.stringbean/runs/**, .env*, **/.env*, !.env.example, !**/.env.example, !.env.sample, !**/.env.sample, !.env.template, !**/.env.template, .secrets, .secrets/**, **/.secrets, **/.secrets/**, secrets, secrets/**, **/secrets, **/secrets/**, credentials, credentials/**, **/credentials, **/credentials/**, credentials.json, **/credentials.json, service-account*.json, **/service-account*.json, *.pem, **/*.pem, *.key, **/*.key, *.p12, **/*.p12, *.pfx, **/*.pfx.
- Never read, list, search, summarize, modify, or transmit content from paths excluded by those rules.
- If an excluded path appears relevant, skip it and continue with the rest of the task. Do not retry access and do not ask another agent to inspect it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
Diagnose and fix this bug in the sshpeaches Android app: If an OpenSSH terminal session is open, the SSH connection closes in the background while the app is not foregrounded, and the app is later foregrounded via a notification, Activity Manager, launcher, or another resume path, it shows the SSH Terminal Open pane with a completely empty and unusable terminal because no backing terminal/session exists. The user currently has to press Back. On foreground/resume, automatically return to the previous screen before the stale terminal pane is drawn, with no fade, animation, brief flicker, or empty terminal frame. Inspect the existing architecture and lifecycle/navigation handling, implement the smallest robust fix across all relevant foreground entry paths, preserve valid live-session behavior, add or update regression tests, and run appropriate tests. Do not merely report a diagnosis: make the code changes and verify them.

Objective:
task-8 - Add stale open-session intent instrumentation coverage

Plan task:
{
  "id": "task-8",
  "title": "Add stale open-session intent instrumentation coverage",
  "description": "Add a test near OpenSessionIntentLaunchTest that creates a real session, captures its session id, closes/stops it, then launches MainActivity with SessionService.ACTION_OPEN_SESSION for that stale id. Assert the app shows Home or preserves the prior valid non-session route and never displays the terminal panel. Keep the existing valid openSessionIntentColdLaunch_restoresExistingLiveTerminal test unchanged.",
  "dependencies": [
    "task-5"
  ],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "Stale notification/open intent is consumed and does not show ConnectingScreen.",
    "Valid notification/open intent still restores the live terminal.",
    "Foreground stale notification does not disturb an already-visible valid non-session route unless recovery from a stale session route is required."
  ]
}

Required verification:
Stale notification/open intent is consumed and does not show ConnectingScreen.
Valid notification/open intent still restores the live terminal.
Foreground stale notification does not disturb an already-visible valid non-session route unless recovery from a stale session route is required.

Known constraints:
Stale notification/open intent is consumed and does not show ConnectingScreen.
Valid notification/open intent still restores the live terminal.
Foreground stale notification does not disturb an already-visible valid non-session route unless recovery from a stale session route is required.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-5

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
