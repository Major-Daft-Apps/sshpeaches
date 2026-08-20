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

You are the architecture advisor.
Review this plan and provide one structured response.

Task:
Search for bugs in this app only. Do not write or apply any bug fixes. Verify what is actually a bug before reporting it. Perform a read-only audit, reproduce or otherwise substantiate each finding where feasible, distinguish confirmed bugs from unverified suspicions, and report evidence with relevant file and line references plus verification steps.

Plan:
{
  "summary": "Perform a read-only bug audit of the Android app, substantiating only real defects with file and line evidence, reproduction or verification steps, and a clear separation between confirmed bugs and unverified suspicions. No fixes, file modifications, destructive commands, or excluded-path access are permitted.",
  "assumptions": [
    "The audit scope is limited to /home/zenulabidin/Documents/sshpeaches and non-excluded paths only.",
    "The repository may contain existing uncommitted changes; the audit must not modify or normalize them.",
    "Build/test commands may be run only if they are read-only in effect and do not require forbidden operations.",
    "Findings should prioritize user-visible correctness, crashes, data loss, security-sensitive behavior, and broken documented workflows."
  ],
  "tasks": [
    {
      "id": "task-1",
      "title": "Map the app structure and entry points",
      "description": "Inspect the non-excluded repository tree, Gradle modules, Android manifests, package layout, and major feature areas to identify the app's runtime surfaces and likely bug-prone paths.",
      "dependencies": [],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "List inspected modules and key source roots.",
        "Record any skipped excluded paths without reading them.",
        "Confirm no files were modified."
      ]
    },
    {
      "id": "task-2",
      "title": "Review build configuration and dependency-sensitive behavior",
      "description": "Read Gradle configuration, Android SDK settings, manifest declarations, ProGuard/R8 settings, Firebase configuration references, and dependency usage for defects that could break builds, runtime permissions, packaging, or platform compatibility.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Run read-only Gradle inspection commands where feasible, such as tasks or dependency reports, if they do not require network or writes outside allowed caches.",
        "Substantiate each reported issue with exact file and line references.",
        "Classify unsupported concerns as unverified suspicions instead of confirmed bugs."
      ]
    },
    {
      "id": "task-3",
      "title": "Audit core SSH, SFTP, SCP, and port-forward flows",
      "description": "Inspect connection setup, authentication selection, host key verification, session lifecycle, SFTP/SCP transfer logic, port forwarding, reconnect handling, cancellation paths, and error propagation for confirmed functional or security bugs.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Trace relevant call paths from UI action to backend operation.",
        "Use existing tests or static reasoning to reproduce control-flow defects where runtime reproduction is impractical.",
        "Provide minimal reproduction steps for each confirmed issue."
      ]
    },
    {
      "id": "task-4",
      "title": "Audit persistence, import/export, and QR sharing paths",
      "description": "Review saved hosts, identities, snippets, forwards, settings, encrypted or sensitive storage, import/export parsing, QR payload handling, and migration logic for data corruption, credential leakage, validation bypasses, or crashes.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Inspect serializers, validators, database/entities, repositories, and migration code.",
        "Check whether malformed or missing fields can be reproduced from public UI or import paths.",
        "Report only defects that are reachable and supported by code evidence."
      ]
    },
    {
      "id": "task-5",
      "title": "Audit Android UI state and lifecycle behavior",
      "description": "Review Compose or View UI screens, navigation, ViewModels, lifecycle-aware collection, permission prompts, dialogs, menus, and background task interactions for crashes, stale state, lost user input, broken navigation, or inaccessible documented actions.",
      "dependencies": [
        "task-1"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Trace state ownership and lifecycle boundaries for each suspected issue.",
        "Where feasible, run existing unit tests or static checks that do not mutate repository files.",
        "Include exact UI path reproduction steps for confirmed user-facing defects."
      ]
    },
    {
      "id": "task-6",
      "title": "Run available read-only verification commands",
      "description": "Run the safest available verification commands, prioritizing existing tests, static analysis, lint, or Gradle checks, while respecting the read-only/create-only policy and denied command list.",
      "dependencies": [
        "task-2",
        "task-3",
        "task-4",
        "task-5"
      ],
      "recommended_role": "implementer",
      "permissions": "read_only",
      "verification": [
        "Capture command, exit status, and concise relevant output.",
        "Do not run commands that require denied git operations or destructive system commands.",
        "If a command cannot be run safely, document why and continue with static verification."
      ]
    },
    {
      "id": "task-7",
      "title": "Prepare the bug audit report",
      "description": "Produce a findings-first report containing only confirmed bugs unless clearly labeled as unverified suspicions. Each confirmed bug must include severity, affected behavior, evidence, file and line references, reproduction or verification steps, and residual uncertainty.",
      "dependencies": [
        "task-6"
      ],
      "recommended_role": "reviewer",
      "permissions": "read_only",
      "verification": [
        "Ensure every confirmed finding has concrete evidence and line references.",
        "Ensure suspicions are separated from confirmed bugs.",
        "Confirm no fixes were proposed as applied changes and no repository files were modified."
      ]
    }
  ],
  "risks": [
    "Some runtime defects may require an Android emulator, device, network service, or SSH server to reproduce; these should be marked with the level of verification achieved.",
    "Gradle or Android tooling may write build artifacts or caches; commands must be selected carefully under the read-only/create-only policy.",
    "Existing uncommitted changes may affect observed behavior; the report should note the repository was dirty if relevant.",
    "Excluded credential and secret paths may hide configuration needed for full runtime reproduction; they must still be skipped."
  ],
  "advisor_questions": [
    "Should the audit prioritize security-sensitive SSH and credential flows over general UI defects if time is limited?",
    "Are emulator/device-based verification steps available, or should the audit rely on source review and existing tests only?",
    "Should findings be limited to high-confidence confirmed bugs, or should the final report include a separate section for well-supported but unverified suspicions?"
  ]
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
