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
task-3 - Add pure stale-session decision helper

Plan task:
{
  "id": "task-3",
  "title": "Add pure stale-session decision helper",
  "description": "Create a small pure helper that decides whether the current session route or requested open-session id is stale. Inputs should include runtime readiness, current route, requestedOpenSessionId, quickConnectRequest.sessionId, pending new-connection state, session snapshots, terminal-emulator availability when needed, and routeBeforeConnecting. Outputs should include whether to gate terminal content, whether to consume requestedOpenSessionId, and the validated recovery route. Validate routeBeforeConnecting so session routes or invalid routes fall back to Routes.HOME.",
  "dependencies": [
    "task-2"
  ],
  "recommended_role": "implementer",
  "permissions": "read_write",
  "verification": [
    "Unit tests cover service not ready with empty snapshots does not redirect.",
    "Unit tests cover service ready with missing requested session redirects or consumes stale request.",
    "Unit tests cover service ready with matching live snapshot preserves terminal route.",
    "Unit tests cover stale routeBeforeConnecting values fall back to Routes.HOME.",
    "Unit tests cover foreground stale open-session request on an existing non-session route consumes the request without forcing unnecessary navigation."
  ]
}

Required verification:
Unit tests cover service not ready with empty snapshots does not redirect.
Unit tests cover service ready with missing requested session redirects or consumes stale request.
Unit tests cover service ready with matching live snapshot preserves terminal route.
Unit tests cover stale routeBeforeConnecting values fall back to Routes.HOME.
Unit tests cover foreground stale open-session request on an existing non-session route consumes the request without forcing unnecessary navigation.

Known constraints:
Unit tests cover service not ready with empty snapshots does not redirect.
Unit tests cover service ready with missing requested session redirects or consumes stale request.
Unit tests cover service ready with matching live snapshot preserves terminal route.
Unit tests cover stale routeBeforeConnecting values fall back to Routes.HOME.
Unit tests cover foreground stale open-session request on an existing non-session route consumes the request without forcing unnecessary navigation.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-2

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
