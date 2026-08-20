Stringbean execution policy:
- Execution profile: rw. Agents with read_write permission may modify files in service of the task. Agents with read_only permission must not modify files; Stringbean will treat modifications as a policy violation.
- Effective permission for this call: read_only.
- Do not run these denied commands: rm, rmdir, sudo, su, dd, mkfs, mount, umount, shutdown, reboot, poweroff, halt, systemctl, service, kill, killall, pkill, chown, chgrp, setfacl, shred, wipefs.
- Do not run these denied git operations: git reset, git clean, git checkout, git restore, git switch, git rebase, git merge, git commit, git push, git pull.
- If a denied operation appears necessary, stop and report it instead of running it.

You are the orchestrator for this repository change.
Output only a JSON block with the schema below.

Task:
inspect the codebase to see the description

Repository context:
{
  "cwd": "/home/zenulabidin/Documents/sshpeaches",
  "git_root": "/home/zenulabidin/Documents/sshpeaches",
  "git_available": true,
  "current_branch": "main",
  "git_status": " M CHANGELOG.md\n M app/build.gradle.kts\n M app/src/androidTest/java/com/sshpeaches/app/live/LiveTransportTest.kt\n M app/src/androidTest/java/com/sshpeaches/app/session_ui/ConnectingScreenTest.kt\n M app/src/androidTest/java/com/sshpeaches/app/testutil/AppStateSeeder.kt\n M app/src/androidTest/java/com/sshpeaches/app/testutil/ComposeTestHelpers.kt\n M app/src/main/java/com/sshpeaches/app/MainActivity.kt\n M app/src/main/java/com/sshpeaches/app/data/settings/SettingsStore.kt\n M app/src/main/java/com/sshpeaches/app/data/ssh/SshClientProvider.kt\n M app/src/main/java/com/sshpeaches/app/service/SessionService.kt\n M app/src/main/java/com/sshpeaches/app/ui/SSHPeachesRoot.kt\n M app/src/main/java/com/sshpeaches/app/ui/keyboard/KeyboardIconPack.kt\n M app/src/main/java/com/sshpeaches/app/ui/keyboard/KeyboardLayoutDefaults.kt\n M app/src/main/java/com/sshpeaches/app/ui/screens/ConnectingScreen.kt\n M app/src/main/java/com/sshpeaches/app/ui/screens/SettingsScreen.kt\n M app/src/main/java/com/sshpeaches/app/ui/state/AppUiState.kt\n M app/src/main/java/com/sshpeaches/app/ui/state/AppViewModel.kt\n M app/src/main/java/com/sshpeaches/app/ui/terminal/TerminalInputRouter.kt\n M app/src/main/java/com/sshpeaches/app/ui/testing/UiTestTags.kt\n M app/src/test/java/com/sshpeaches/app/data/ssh/SshClientProviderTest.kt\n M app/src/test/java/com/sshpeaches/app/ui/keyboard/KeyboardLayoutDefaultsTest.kt\n M app/src/test/java/com/sshpeaches/app/ui/terminal/TerminalInputRouterTest.kt\n?? .codex\n?? .stringbean/\n?? app/src/main/java/com/sshpeaches/app/service/BoundedMoshInputQueue.kt\n?? app/src/test/java/com/sshpeaches/app/service/BoundedMoshInputQueueTest.kt\n?? app/src/test/java/com/sshpeaches/app/ui/keyboard/KeyboardIconPackTest.kt\n?? docs/task-2-summary.json\n?? docs/task-3-summary.json\n",
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
  ".codex": "/home/zenulabidin/Documents/sshpeaches/.codex",
  ".claude": "/home/zenulabidin/Documents/sshpeaches/.claude"
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
