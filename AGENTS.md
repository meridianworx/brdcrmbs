# AGENTS.md — brdcrmbs

> This file is generated for the brdcrmbs repository from the App Development Playbook.
> The standard rules below apply to all meaningful implementation work. App-specific durable engineering constraints follow them.

## Required Reading

Before meaningful implementation work:
1. Read `PROJECT_STATE.md`.
2. Follow `APP_DEVELOPMENT_PLAYBOOK.md`.
3. Inspect the existing implementation relevant to the task.

## Standard Engineering Rules

1. Implement only the requested task.
2. Do not invent product requirements.
3. Do not redesign approved UI unless explicitly requested.
4. Do not alter locked behavior unless explicitly requested.
5. Avoid unrelated refactoring.
6. Preserve existing functionality.
7. Follow the existing architecture unless an architecture change is approved.
8. Prefer incremental changes over broad rewrites.
9. Do not silently remove, replace, or rename working functionality.
10. If the request conflicts with a locked decision, report the conflict instead of overriding it.
11. Keep new dependencies to the minimum necessary.
12. Preserve data compatibility unless a migration is explicitly approved.
13. Build/test after meaningful changes when practical.
14. Report every file changed.
15. Report build/test results.
16. Report errors, warnings, assumptions, and unresolved issues.
17. Do not modify unrelated files merely for cleanup.
18. Update `PROJECT_STATE.md` when the approved task requires a state update or records a completed milestone.

## Completion Report

At the end of a task, report:

1. Files changed
2. What was implemented
3. Build/test result
4. Any warnings or unresolved issues
5. Whether `PROJECT_STATE.md` was updated

## App-Specific Engineering Rules

These are durable rules for brdcrmbs.

### Product Boundaries

- The core philosophy is: **You make the plan. The app helps you follow it.**
- Do not add recommendation/prescription logic for fasting, meals, workouts, weight, glucose, or other health data.
- Core planning categories are only Fasting, Meals, and Workout / Activity unless the Product Owner explicitly changes scope.
- Detailed exercise set/rep/weight logging belongs outside brdcrmbs.
- Do not add meal photography.

### Historical Integrity

- Never silently rewrite historical outcomes.
- A Changed meal must preserve both the original planned meal and the actual meal.
- `Changed` must remain a distinct stored meal outcome.
- A missed workout/activity remains missed if the user later reschedules it.
- Rescheduling must not make the original historical miss disappear.
- A multi-day fast is one continuous fasting event spanning dates, not independent daily records.

### Local-First / Persistence

- Core functionality must remain usable offline.
- User-created plans and history must persist across app restarts/process death.
- UI code must not directly own durable persistence.
- Maintain a clear repository/data boundary.
- Preserve data compatibility; use safe migrations when schemas change.
- Do not introduce unnecessary network/cloud dependencies.

### Date and Time

Date/time behavior is critical. Meaningful changes involving scheduling must consider and, where practical, test:
- Week boundaries
- Month boundaries
- Year boundaries
- Cross-midnight fasting
- Multi-day fasting
- Future plans
- Historical plans
- Rescheduling
- Daylight-saving transitions where applicable

Do not model 48h/72h fasting as separate daily fasting records.

### Adherence States

Preserve these domain distinctions:

Fasting:
- Planned
- Done
- Missed

Meals:
- Planned
- Done
- Changed
- Missed

Workout / Activity:
- Planned
- Done
- Missed

Progress calculations may summarize outcomes, but raw stored outcomes must remain available.

### Approved UI

- Preserve the approved dark charcoal / near-black visual direction with bright green accent.
- Preserve high-contrast typography, rounded cards, clear status states, and four-item bottom navigation.
- Meals are text-based; do not introduce food thumbnails/photos.
- Do not use implementation work as an opportunity to redesign approved screens.
- If a minor visual detail is unspecified, choose the smallest implementation consistent with `PROJECT_STATE.md`.

### Backup / Restore

Backup & Restore is a required product capability.

When implemented, a valid backup must preserve enough data to reconstruct relevant user-created state, including:
- Plans
- Outcomes/history
- Saved meals
- Saved activities
- Required preferences
- Other user-created records introduced later

Restore must validate input and fail safely rather than partially corrupting current data.

### Scope Discipline

Do not add features merely because they are common in health, fasting, nutrition, or fitness apps.

If the codebase and project documents do not answer a material product question, surface the ambiguity rather than inventing a large new behavior.

Current product state, milestones, known issues, and backlog belong in `PROJECT_STATE.md`, not in this file.
