Stringbean execution policy:
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
describe this proejct, please

Objective:
task-1 - Inventory repository structure

Plan task:
{
  "id": "task-1",
  "title": "Inventory repository structure",
  "description": "Read top-level files and directory layout to identify the Gradle project, Android app module, source sets, documentation folders, test folders, and configuration files.",
  "dependencies": [],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "Root Gradle files and app module layout are identified.",
    "Documentation, Android source, unit tests, and instrumentation tests are separately described.",
    "Tooling/context directories such as .codex, .agents, or .stringbean are not treated as product source unless directly relevant."
  ]
}

Required verification:
Root Gradle files and app module layout are identified.
Documentation, Android source, unit tests, and instrumentation tests are separately described.
Tooling/context directories such as .codex, .agents, or .stringbean are not treated as product source unless directly relevant.

Known constraints:
Root Gradle files and app module layout are identified.
Documentation, Android source, unit tests, and instrumentation tests are separately described.
Tooling/context directories such as .codex, .agents, or .stringbean are not treated as product source unless directly relevant.

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
