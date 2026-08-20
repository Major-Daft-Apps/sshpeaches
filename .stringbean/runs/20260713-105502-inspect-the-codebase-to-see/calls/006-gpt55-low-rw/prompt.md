Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_write.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
inspect the codebase to see the description

Objective:
task-2 - Verify description against source structure

Plan task:
{
  "id": "task-2",
  "title": "Verify description against source structure",
  "description": "Inspect key source areas to confirm which documented or inferred capabilities have implementation support. Check SSH/session service code, persistence, security, settings, terminal UI/input, file transfer modules, QR components, snippets, keyboard layouts, widgets, and tests at a sampling level sufficient to back the repository description.",
  "dependencies": [
    "task-1"
  ],
  "recommended_role": "advisor",
  "permissions": "read_only",
  "verification": [
    "Map major features to concrete paths or classes where possible, such as SessionService.kt, AppViewModel.kt, SettingsStore.kt, SshClientProvider.kt, and terminal integration files.",
    "Label features as source-verified only when corresponding implementation paths are observed.",
    "Label features as inferred when only model, UI, or partial code references are observed.",
    "Keep Mosh support separate from README-documented SSH features unless committed docs clearly describe it."
  ]
}

Required verification:
Map major features to concrete paths or classes where possible, such as SessionService.kt, AppViewModel.kt, SettingsStore.kt, SshClientProvider.kt, and terminal integration files.
Label features as source-verified only when corresponding implementation paths are observed.
Label features as inferred when only model, UI, or partial code references are observed.
Keep Mosh support separate from README-documented SSH features unless committed docs clearly describe it.

Known constraints:
Map major features to concrete paths or classes where possible, such as SessionService.kt, AppViewModel.kt, SettingsStore.kt, SshClientProvider.kt, and terminal integration files.
Label features as source-verified only when corresponding implementation paths are observed.
Label features as inferred when only model, UI, or partial code references are observed.
Keep Mosh support separate from README-documented SSH features unless committed docs clearly describe it.

Prior constraints/advisor notes:
advisor

Allowed file scope:
task-1

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
