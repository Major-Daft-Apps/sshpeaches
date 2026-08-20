Implement only the listed task and avoid unrelated changes.

Task:
Review only the existing Ctrl/IME changes using Opus 4.8; do not modify files.

Objective:
task-1 - Identify Ctrl/IME Change Scope

Plan task:
{
  "id": "task-1",
  "title": "Identify Ctrl/IME Change Scope",
  "description": "Use only targeted read-only git commands against explicitly known, non-excluded paths that are clearly related to Ctrl key handling, IME input, soft keyboard behavior, terminal input dispatch, or associated tests. Do not run broad discovery commands such as `git status --short`, `git diff --name-only`, recursive file listings, or repository-wide searches because they may reveal excluded path names. Acceptable commands are limited to targeted forms such as `git diff -- <known-safe-path>` or `git diff -- <known-safe-directory>/` after verifying the target path is not excluded by policy. If no explicit known-safe Ctrl/IME-related paths are available, stop without inspecting files and report that scope identification cannot proceed safely.",
  "dependencies": [],
  "recommended_role": "reviewer",
  "permissions": "read_only",
  "verification": [
    "Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.",
    "Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.",
    "Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.",
    "Confirm no repository files were modified.",
    "Confirm Opus 4.8 is the requested review model/provider target.",
    "Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths."
  ]
}

Required verification:
Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.
Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.
Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.
Confirm no repository files were modified.
Confirm Opus 4.8 is the requested review model/provider target.
Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths.

Known constraints:
Confirm the reviewed file list is limited to explicitly known, non-excluded Ctrl/IME-related paths.
Confirm no broad changed-path listing, recursive listing, or repository-wide search was run.
Confirm no excluded paths were listed, searched, opened, summarized, transmitted, created, or modified.
Confirm no repository files were modified.
Confirm Opus 4.8 is the requested review model/provider target.
Report reviewed paths, commands run, tests run if any, skipped safely known ambiguous paths if any, and whether scope identification was stopped due to lack of safe target paths.

Prior constraints/advisor notes:
reviewer

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