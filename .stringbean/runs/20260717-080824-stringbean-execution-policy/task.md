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

You are the orchestrator for this repository change.
Output only a JSON block with the schema below.

Task:
Implement only the listed task and avoid unrelated changes.

Task:
Search for bugs in this app only. Do not write or apply any bug fixes. Verify what is actually a bug before reporting it. Perform a read-only audit, reproduce or otherwise substantiate each finding where feasible, distinguish confirmed bugs from unverified suspicions, and report evidence with relevant file and line references plus verification steps.

Objective:
task-2 - Review build configuration statically

Plan task:
{
  "id": "task-2",
  "title": "Review build configuration statically",
  "description": "Read non-excluded Gradle configuration, Android SDK settings, manifest declarations, ProGuard/R8 settings, packaging rules, dependency declarations, and configuration references for defects that could break builds, runtime permissions, packaging, or platform compatibility. Treat Gradle dependency reports, build tasks, lint, tests, and dependency resolution as optional and run them only after proving their write locations are limited to newly created allowed paths and they will not touch pre-existing repository paths, excluded paths, network state, or external caches.",
  "dependencies": [
    "task-1"
  ],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "Substantiate each confirmed build/config issue with exact file and line references.",
    "If Gradle or Android tooling is skipped, document that it was skipped because it commonly writes .gradle, build outputs, reports, daemon state, or caches.",
    "Classify unsupported concerns as unverified suspicions instead of confirmed bugs."
  ]
}

Required verification:
Substantiate each confirmed build/config issue with exact file and line references.
If Gradle or Android tooling is skipped, document that it was skipped because it commonly writes .gradle, build outputs, reports, daemon state, or caches.
Classify unsupported concerns as unverified suspicions instead of confirmed bugs.

Known constraints:
Substantiate each confirmed build/config issue with exact file and line references.
If Gradle or Android tooling is skipped, document that it was skipped because it commonly writes .gradle, build outputs, reports, daemon state, or caches.
Classify unsupported concerns as unverified suspicions instead of confirmed bugs.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-1

Repository context:
{
  "cwd": "/home/zenulabidin/Documents/sshpeaches",
  "workspace_root": "/home/zenulabidin/Documents/sshpeaches",
  "workspace_type": "git-worktree",
  "git_root": "/home/zenulabidin/Documents/sshpeaches",
  "git_available": true,
  "git_repository": true,
  "current_branch": "main",
  "git_status": "38 changed entries; paths omitted from provider context",
  "top_level_files": [
    "CHANGELOG.md",
    "LICENSE",
    "README.md",
    "build.gradle.kts",
    "firebase.json",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "lint.xml",
    "local.properties",
    "prompt_instructions.txt",
    "prompt_state_snapshot.txt",
    "settings.gradle.kts"
  ],
  "AGENTS.md": "",
  "CLAUDE.md": "",
  "README.md": "# SSHPeaches for Android\n\nSSHPeaches is an Android SSH client for connecting to servers, managing saved hosts and keys, transferring files, and reusing common connection settings from one place.\n\nIf you installed SSHPeaches from F-Droid or Google Play, this repository includes the guides you need to get started and troubleshoot common issues.\n\n## Start Here\n\n- [User wiki home](docs/user-wiki/Home.md)\n- [Getting started](docs/user-wiki/Getting-Started.md)\n- [User guide](docs/user-wiki/User-Guide.md)\n- [Troubleshooting](docs/user-wiki/Troubleshooting.md)\n- [Documentation index](docs/README.md)\n\n## What You Can Do With SSHPeaches\n\n- Open SSH terminal sessions\n- Browse and transfer files with SFTP\n- Copy files with SCP\n- Save hosts for one-tap reuse\n- Manage SSH identities and private keys\n- Set up local port forwards\n- Share or import hosts, identities, and forwards with QR codes\n- Save snippets for repeated commands\n\nMinimum supported Android version: Android 8.0 (API 26).\n\n## Quick Start\n\n1. Open SSHPeaches.\n2. If this is your first launch, the **Home** screen gives you one-tap buttons to add a host, identity, port forward, or snippet.\n3. Use **Quick Connect** for a fast one-time connection, or open **Hosts** to save a server.\n4. Enter your host name or IP address, port, username, and authentication settings.\n5. Connect and verify the server fingerprint if SSHPeaches prompts you.\n6. Save the host if you want to reuse it later for SSH, SFTP, or SCP.\n\n## Main Areas Of The App\n\n- **Home**: open sessions, favorites, and a mixed recents list in one place\n- **Hosts**: save servers and launch SSH, SFTP, or SCP\n- **Identities**: import and manage SSH keys\n- **Port Forwards**: create and manage local forwards\n- **Snippets**: store reusable commands\n- **Settings**: security, theme, terminal, and transfer preferences\n\nThe management screens for hosts, identities, port forwards, and snippets all use grouped sections. Each group can be collapsed, and item management actions live in the three-dot menu on the right side of each card.\n\n## Security Notes\n\n- Review host key fingerprints before trusting a server.\n- Set a PIN and enable biometric unlock in **Settings** if you want extra protection.\n- Private-key and password data exported through QR can be protected with an export passphrase.\n\n## Need Help?\n\n- Open the in-app drawer and tap **Help**\n- Read the [troubleshooting guide](docs/user-wiki/Troubleshooting.md)\n- Visit the support page: <https://majordaftapps.com/sshpeaches-support>\n- Report bugs or request features through this repository's GitHub issues\n\n## License\n\nSSHPeaches is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).\n\n## Open Source Notices\n\nOpen source license notices are available in the app from **About**.\n",
  "excluded_path_patterns": [
    ".stringbean/runs",
    ".stringbean/runs/**",
    "**/.stringbean/runs",
    "**/.stringbean/runs/**",
    ".env*",
    "**/.env*",
    "!.env.example",
    "!**/.env.example",
    "!.env.sample",
    "!**/.env.sample",
    "!.env.template",
    "!**/.env.template",
    ".secrets",
    ".secrets/**",
    "**/.secrets",
    "**/.secrets/**",
    "secrets",
    "secrets/**",
    "**/secrets",
    "**/secrets/**",
    "credentials",
    "credentials/**",
    "**/credentials",
    "**/credentials/**",
    "credentials.json",
    "**/credentials.json",
    "service-account*.json",
    "**/service-account*.json",
    "*.pem",
    "**/*.pem",
    "*.key",
    "**/*.key",
    "*.p12",
    "**/*.p12",
    "*.pfx",
    "**/*.pfx"
  ],
  "excluded_nested_repositories": [],
  "scope_note": "Use workspace_root as the default scope. Explicit user-named paths are also in scope, but never read excluded paths; skip an excluded path without retrying it."
}

Current repository root:
/home/zenulabidin/Documents/sshpeaches

Create a robust implementation plan with explicit tasks.

Contract:
{
  "summary": "...",
  "assumptions": [],
  "tasks": [
    {
      "id": "task-1",
      "title": "Implement ...",
      "description": "...",
      "dependencies": [],
      "recommended_role": "implementer",
      "permissions": "read_write",
      "verification": []
    }
  ],
  "risks": [],
  "advisor_questions": []
}