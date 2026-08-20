Stringbean execution policy:
- Ordinary remote processing of task text and non-excluded, in-scope workspace context by Stringbean's configured hosted providers is inherent to this requested run; do not pause for separate provider-use approval.
- Execution profile: ro. Treat this run as create-only. You may create new files or new directories, but you must not modify, delete, rename, move, or type-change pre-existing repository paths. Stringbean will treat forbidden changes as policy violations, even for agents whose configured role is read_write.
- Effective permission for this call: read_only.
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
Search for bugs in this app only. Do not write or apply any bug fixes. Verify what is actually a bug before reporting it. Perform a read-only audit, reproduce or otherwise substantiate each finding where feasible, distinguish confirmed bugs from unverified suspicions, and report evidence with relevant file and line references plus verification steps.

Objective:
task-0 - Read-only preflight and safety boundaries

Plan task:
{
  "id": "task-0",
  "title": "Read-only preflight and safety boundaries",
  "description": "Establish the repository shape, policy-safe search filters, and current working-tree context without reading excluded paths or modifying existing files. Use only read-only commands such as pwd, git status --short, rg --files with explicit glob exclusions, and targeted file reads. Do not use denied git operations or broad traversal that can enter excluded paths.",
  "dependencies": [],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "Record the repository root and whether git status indicates a dirty tree, without changing anything.",
    "Define and use exclude filters for .stringbean/runs, .env*, secrets, credentials, service-account files, private keys, certificates, and other protected patterns.",
    "Confirm no excluded paths were listed, searched, read, summarized, or transmitted."
  ]
}

Required verification:
Record the repository root and whether git status indicates a dirty tree, without changing anything.
Define and use exclude filters for .stringbean/runs, .env*, secrets, credentials, service-account files, private keys, certificates, and other protected patterns.
Confirm no excluded paths were listed, searched, read, summarized, or transmitted.

Known constraints:
Record the repository root and whether git status indicates a dirty tree, without changing anything.
Define and use exclude filters for .stringbean/runs, .env*, secrets, credentials, service-account files, private keys, certificates, and other protected patterns.
Confirm no excluded paths were listed, searched, read, summarized, or transmitted.

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
