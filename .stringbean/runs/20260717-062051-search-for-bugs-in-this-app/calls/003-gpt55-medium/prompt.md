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

You are the orchestrator. Advisor requested changes.

Task:
Search for bugs in this app only. Do not write or apply any bug fixes. Verify what is actually a bug before reporting it. Perform a read-only audit, reproduce or otherwise substantiate each finding where feasible, distinguish confirmed bugs from unverified suspicions, and report evidence with relevant file and line references plus verification steps.

Original plan:
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

Advisor feedback:
{
  "verdict": "revise",
  "severity": "none",
  "summary": "The plan is directionally sound for a read-only bug audit, but it should be tightened before approval because several verification steps may violate the stated create-only/read-only policy by producing Gradle build outputs, caches, reports, or local state. The plan should explicitly treat all build, test, lint, emulator, and dependency commands as optional and only runnable after confirming their write locations are limited to newly created allowed paths or after documenting that they are skipped.",
  "blockers": [],
  "concerns": [
    "The plan says build/test commands may be run if they are read-only in effect, but Gradle tasks, dependency reports, lint, and tests commonly write to .gradle, build directories, Android caches, test reports, or daemon state. Under the execution profile, pre-existing repository paths must not be modified, so these commands are not automatically safe.",
    "Task 6 depends on running verification commands after the audit, but the policy may make command execution impractical. The plan should not make runtime command verification a required dependency for producing the report.",
    "Task 2 recommends Gradle inspection commands, but dependency resolution may require network access or cache writes. These should be explicitly gated or replaced with static inspection by default.",
    "The plan should include an explicit check that no excluded paths are listed, searched, or traversed indirectly by broad commands. Commands like recursive find, rg, or tree need exclude filters aligned with the policy.",
    "The advisor questions are useful, but the plan should already encode a default prioritization: security-sensitive SSH, credential, import/export, and data-loss paths should come before general UI polish bugs."
  ],
  "recommendations": [
    "Revise task-6 to say: run only commands proven not to modify pre-existing paths; otherwise skip and document the reason. Static source review is sufficient when command execution would breach the policy.",
    "Add a preflight step to capture repository status and tree shape using allowed read-only commands while excluding protected paths. Do not use denied git operations.",
    "Use precise exclude patterns for all searches so .env*, secrets, credentials, keys, certificates, and .stringbean/runs are never read or transmitted.",
    "Make confirmed findings require reachability evidence, not just suspicious code. Keep theoretical risks in a separate unverified section.",
    "Prioritize SSH authentication, host key verification, credential storage/export, file transfer integrity, port forwarding lifecycle, and crash/data-loss paths before lower-impact UI issues."
  ]
}

Revise the plan and output ONLY a JSON object in this schema:
{
  "summary": "...",
  "assumptions": [],
  "tasks": [],
  "risks": [],
  "advisor_questions": []
}
