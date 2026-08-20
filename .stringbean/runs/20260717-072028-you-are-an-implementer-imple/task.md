You are an implementer.
Implement only the listed task and avoid unrelated changes.

Task:
Search for bugs in this app only. Do not write or apply any bug fixes. Verify what is actually a bug before reporting it. Perform a read-only audit, reproduce or otherwise substantiate each finding where feasible, distinguish confirmed bugs from unverified suspicions, and report evidence with relevant file and line references plus verification steps.

Objective:
task-1 - Map app structure and entry points

Plan task:
{
  "id": "task-1",
  "title": "Map app structure and entry points",
  "description": "Inspect non-excluded Gradle settings, Android manifests, source roots, package layout, navigation entry points, services, receivers, activities, repositories, and major feature areas to identify runtime surfaces and likely bug-prone paths.",
  "dependencies": [
    "task-0"
  ],
  "recommended_role": "implementer",
  "permissions": "read_only",
  "verification": [
    "List inspected modules and key source roots.",
    "Identify main app entry points and user-reachable feature areas.",
    "Record any skipped excluded paths without reading or listing their contents."
  ]
}

Required verification:
List inspected modules and key source roots.
Identify main app entry points and user-reachable feature areas.
Record any skipped excluded paths without reading or listing their contents.

Known constraints:
List inspected modules and key source roots.
Identify main app entry points and user-reachable feature areas.
Record any skipped excluded paths without reading or listing their contents.

Prior constraints/advisor notes:
implementer

Allowed file scope:
task-0

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