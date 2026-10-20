# APP DEVELOPMENT PLAYBOOK

## Purpose

This file is the master operating process for developing future Android applications.

It defines:
- who is responsible for each part of development,
- how a new project is initialized,
- how product decisions become implementation tasks,
- how Codex should be guided,
- how builds and testing are handled,
- how Git and GitHub are used,
- how approved work is locked,
- how project documentation is created and maintained,
- and how lessons from one project improve future projects.

This playbook is universal. It must not contain app-specific product decisions.

---

# 1. Roles

## Product Owner — User

The Product Owner owns the product vision and final decisions.

Responsibilities:
- Define the problem to solve.
- Describe desired features and behavior.
- Review proposed UX and product flows.
- Approve or reject designs and implementations.
- Test meaningful changes on a physical device.
- Decide when a feature is approved and locked.
- Set priorities and determine what is intentionally excluded.

The Product Owner does not need to translate product ideas into Kotlin or implementation details.

## Product / UX / Architecture / Engineering Lead — ChatGPT

ChatGPT translates product intent into an implementable development plan.

Responsibilities:
- Clarify product requirements.
- Design user flows and screen behavior.
- Help define architecture and data behavior.
- Identify edge cases before implementation.
- Reuse proven patterns when appropriate without blindly copying old projects.
- Break work into small, controlled implementation tasks.
- Create concise Codex instructions.
- Help diagnose failures.
- Protect approved and locked decisions from unnecessary redesign.
- Maintain the distinction between universal process rules and app-specific decisions.

## Implementation Engineer — Codex

Codex works directly with the repository and implements approved tasks.

Responsibilities:
- Read project instructions before editing.
- Inspect existing code before making changes.
- Modify only what is required for the current task.
- Preserve existing approved behavior.
- Avoid unrelated refactors.
- Build/test when appropriate.
- Report files changed, implementation details, and errors.

## Android Studio

Android Studio is the primary Android build/debug environment.

Uses include:
- Android SDK and Gradle builds,
- Jetpack Compose tooling,
- emulator testing,
- Logcat/debugging,
- APK/application generation,
- running the app on physical devices.

## Git

Git is the version-control safety system.

It is used to:
- establish known-good checkpoints,
- inspect changes,
- recover from bad changes,
- preserve development history.

## GitHub

GitHub is the remote home of the Git repository.

It is used to:
- preserve the repository remotely,
- provide an off-computer copy of project history,
- support portability and collaboration.

## Physical Android Device

A physical device is the primary acceptance-testing environment.

A successful build is not automatically a product approval.

---

# 2. Documentation Hierarchy

Every project should use three documentation layers.

## APP_DEVELOPMENT_PLAYBOOK.md

Purpose: Defines how all apps are developed.

Characteristics:
- Universal.
- Reused across projects.
- Changes rarely.
- Updated only when a lesson is broadly useful to future development.

## AGENTS.md

Purpose: Defines how Codex should work inside the specific repository.

Characteristics:
- Generated during project initialization.
- Begins with standard engineering rules.
- May include app-specific engineering constraints.
- Should remain concise and actionable.

## PROJECT_STATE.md

Purpose: Defines what the specific app is and its current development state.

Characteristics:
- Unique to each app.
- Created during project initialization.
- Updated throughout development.
- Contains product decisions, architecture, completed work, locked decisions, current focus, known issues, and backlog.

Hierarchy:

APP_DEVELOPMENT_PLAYBOOK.md
→ tells us how to initialize and develop
→ AGENTS.md tells Codex how to work
→ PROJECT_STATE.md tells the team what this app is
→ code implements the approved state

---

# 3. Phase 0 — New Project Initialization

Feature development should not begin until the initial product direction is sufficiently defined.

## Step 1 — Define the Product

Establish:
- App name or working name.
- Problem being solved.
- Intended user.
- Primary purpose.
- Core value proposition.
- Core user experience.
- Minimum viable feature set.
- Important exclusions.

Do not add features simply because they are technically possible.

## Step 2 — Define the Core UX

Establish:
- Primary user flow.
- Major screens.
- Navigation structure.
- What the user does first.
- What data is created.
- What happens after data is saved.
- How users retrieve, edit, or revisit information when applicable.

Prefer simple flows over unnecessary interface complexity.

## Step 3 — Establish Initial Architecture

Determine only what is needed to begin safely.

Consider:
- local versus cloud storage,
- data models,
- repositories,
- persistence,
- media/file storage,
- backup/restore requirements,
- permissions,
- offline requirements,
- privacy/security requirements,
- reusable proven patterns from previous projects.

Reuse proven architecture selectively. Do not blindly clone an older project.

## Step 4 — Define MVP Scope

Separate:
- Required for first usable version.
- Useful later.
- Explicitly out of scope.

This protects development from uncontrolled feature growth.

## Step 5 — Approve Initial Direction

Before coding begins, the Product Owner reviews:
- product purpose,
- core flow,
- initial screens,
- MVP scope,
- major architectural decisions.

## Step 6 — Create the Android Project

Create the project in Android Studio with the approved:
- application name,
- package structure,
- minimum SDK and relevant platform choices,
- initial Compose/project configuration.

## Step 7 — Initialize Git and GitHub

- Initialize Git.
- Create the remote GitHub repository.
- Connect the local repository.
- Confirm the project builds before establishing the baseline.

## Step 8 — Add APP_DEVELOPMENT_PLAYBOOK.md

Place a copy of the current master playbook in the repository root.

## Step 9 — Generate AGENTS.md

Create AGENTS.md using the standard rules in this playbook plus any necessary app-specific engineering constraints.

Review it before implementation begins.

## Step 10 — Generate PROJECT_STATE.md

Create PROJECT_STATE.md from the approved product-planning decisions.

Review it before implementation begins.

## Step 11 — Baseline Build and Commit

Before feature development:
- confirm the project builds,
- resolve setup problems,
- create a baseline Git commit,
- push the baseline to GitHub.

Only then begin feature implementation.

---

# 4. Standard AGENTS.md Requirements

Every generated AGENTS.md should contain, at minimum, these principles:

1. Read PROJECT_STATE.md before making meaningful changes.
2. Follow APP_DEVELOPMENT_PLAYBOOK.md for process rules.
3. Inspect existing code before editing.
4. Change only what is required for the current task.
5. Do not redesign approved UI unless explicitly requested.
6. Do not alter locked behavior unless explicitly requested.
7. Avoid unrelated refactoring.
8. Preserve existing functionality.
9. Follow the existing architecture unless a change is approved.
10. Prefer incremental changes over large rewrites.
11. Do not silently remove or replace functionality.
12. Build/test after meaningful implementation changes when practical.
13. Report all files changed.
14. Report build/test results.
15. Report errors, warnings, assumptions, and unresolved issues.
16. Do not invent product requirements.
17. If a requested change conflicts with a locked decision, surface the conflict rather than silently overriding it.
18. Update PROJECT_STATE.md when the task explicitly includes a project-state update or when the agreed workflow requires documenting an approved milestone.

App-specific technical constraints may be added below these standard rules.

---

# 5. Standard PROJECT_STATE.md Structure

Every new PROJECT_STATE.md should begin with this structure and be adapted to the app.

## Project Identity
- App name
- Package name
- Platform
- Repository
- Current version/development stage

## Purpose
- What the app does
- Who it is for
- Problem being solved

## Product Philosophy
- Core principles
- Simplicity rules
- Important exclusions

## Core User Flow
Document the approved primary flow.

## Navigation
Document the approved navigation structure.

## Architecture
Document important architecture decisions:
- storage,
- models,
- repositories,
- media/files,
- services,
- backup/restore,
- privacy/security,
- other major components.

## Features

### Completed
Approved working features.

### In Progress
Current implementation work.

### Planned
Approved future work.

## Locked Decisions
Decisions that must not be changed casually.

## Approved UI / UX
Approved screen/layout/behavior decisions.

## Known Issues
Confirmed problems that remain unresolved.

## Backlog
Non-current work that may be considered later.

## Current Development Focus
The next controlled objective.

## Recent Milestones
Important approved development checkpoints.

---

# 6. Standard Development Loop

For each feature or controlled change:

1. Product Owner describes the desired change.
2. ChatGPT clarifies behavior and scope.
3. Product Owner approves the direction when necessary.
4. ChatGPT creates a tightly scoped Codex task.
5. Codex reads the relevant project documentation and code.
6. Codex implements the requested change.
7. Codex builds/tests at the appropriate level.
8. Product Owner tests meaningful user-facing changes on the physical device.
9. If the result fails, diagnose and correct it.
10. If the result passes, Product Owner approves it.
11. Approved behavior is treated as locked unless later explicitly reopened.
12. Update PROJECT_STATE.md when the approved milestone changes project state.
13. Commit the known-good change to Git.
14. Push the checkpoint to GitHub.
15. Begin the next controlled task.

Master loop:

DEFINE
→ DESIGN
→ APPROVE DIRECTION
→ IMPLEMENT
→ BUILD
→ TEST
→ PHYSICAL DEVICE TEST
→ APPROVE
→ LOCK
→ UPDATE PROJECT STATE
→ GIT COMMIT
→ GITHUB PUSH
→ NEXT TASK

---

# 7. Codex Task Design

Codex tasks should be narrow enough that failures are easy to identify.

A good task should state:
- objective,
- relevant existing behavior,
- exact requested change,
- behavior that must remain unchanged,
- files/components to inspect if known,
- acceptance criteria,
- build/test expectations,
- required completion report.

Avoid prompts such as:
- "Improve the whole app."
- "Clean everything up."
- "Refactor the project."
- "Make the UI better."

Prefer:
- one feature,
- one bug,
- one screen behavior,
- one architecture change,
- one controlled migration.

Large features should be divided into milestones.

---

# 8. Locked Asset and Behavior Rule

Once the Product Owner approves a screen, component, flow, asset, or behavior:

- Treat it as locked.
- Change only the requested portion.
- Do not use a new task as an excuse to redesign surrounding approved work.
- Preserve approved visual identity.
- Preserve approved interaction behavior.
- Reopen locked decisions only when the Product Owner explicitly requests reconsideration.

This rule reduces regression and design drift.

---

# 9. Testing Hierarchy

Use the cheapest sufficient test during implementation, but increase confidence before approval.

## Level 1 — Static / Local Reasoning
Use for very small changes when appropriate.

## Level 2 — Targeted Build/Test
Compile or test the affected component/module when available.

## Level 3 — Full App Build
Use for meaningful integrations and before important milestones.

## Level 4 — Emulator
Useful for rapid UI and behavior checks.

## Level 5 — Physical Device
Primary acceptance test for meaningful user-facing behavior.

Physical-device testing is especially important for:
- audio,
- notifications,
- permissions,
- file handling,
- performance,
- device lifecycle,
- touch interaction,
- real layout behavior.

A build passing does not equal Product Owner approval.

---

# 10. Git and GitHub Discipline

## Baseline

Start from a clean, building baseline.

## Controlled Changes

Prefer one logical change per development task.

## Commit Known-Good Work

Do not intentionally make broken states the normal checkpoint.

Useful commit categories include:
- feature,
- fix,
- UI,
- architecture,
- documentation,
- backup/restore,
- release/milestone.

## Push Approved Checkpoints

Push stable checkpoints to GitHub regularly.

## Before Risky Work

Commit the current known-good state before:
- migrations,
- architecture changes,
- large dependency changes,
- storage changes,
- significant UI restructuring.

---

# 11. Debugging Process

When something fails:

1. Reproduce the problem.
2. Identify the last known-good state.
3. Determine what changed.
4. Inspect logs/build errors.
5. Narrow the failure to the smallest likely cause.
6. Fix the cause rather than masking symptoms.
7. Rebuild.
8. Retest.
9. Confirm unrelated approved behavior still works.
10. Commit only after returning to a known-good state.

Avoid stacking speculative fixes.

---

# 12. Scope and Efficiency Rules

- One controlled change at a time.
- Do not over-engineer early versions.
- Build the usable product before optional sophistication.
- Reuse proven patterns when they genuinely fit.
- Do not carry old architecture into a new app merely because it exists.
- Avoid unnecessary dependencies.
- Keep UI complexity proportional to user value.
- Keep Codex prompts concise and testable.
- Avoid repeatedly changing approved work.
- Separate current work from backlog ideas.
- Prefer working software over speculative infrastructure.

---

# 13. Documentation Maintenance

## Update PROJECT_STATE.md when:
- a feature is approved,
- a decision becomes locked,
- architecture materially changes,
- a known issue is discovered/resolved,
- development focus changes,
- a milestone is completed,
- backlog priorities materially change.

## Update AGENTS.md when:
- Codex needs a durable new repository-specific rule,
- architecture creates a new engineering constraint,
- a recurring implementation mistake needs to be prevented.

Do not turn AGENTS.md into a project diary.

## Update APP_DEVELOPMENT_PLAYBOOK.md when:
A lesson is broadly useful across future projects.

Ask:
"Is this specific to this app, or should every future app benefit from this lesson?"

If specific → PROJECT_STATE.md or AGENTS.md.
If universal → APP_DEVELOPMENT_PLAYBOOK.md.

---

# 14. Milestone Completion Checklist

Before declaring a meaningful milestone complete:

- [ ] Requested behavior is implemented.
- [ ] Project builds successfully.
- [ ] Relevant automated/targeted tests pass where available.
- [ ] Meaningful user-facing behavior is tested on a physical device.
- [ ] No known regression was introduced.
- [ ] Product Owner approves the result.
- [ ] Approved behavior is recorded as locked when appropriate.
- [ ] PROJECT_STATE.md reflects the new state.
- [ ] Git commit created.
- [ ] Stable checkpoint pushed to GitHub.

---

# 15. New Project Starter Checklist

When beginning any future app:

- [ ] Define problem and intended user.
- [ ] Define product purpose.
- [ ] Define core user flow.
- [ ] Define MVP.
- [ ] Define exclusions.
- [ ] Define initial screens/navigation.
- [ ] Decide initial architecture.
- [ ] Identify reusable proven patterns.
- [ ] Approve initial direction.
- [ ] Create Android Studio project.
- [ ] Initialize Git.
- [ ] Create/connect GitHub repository.
- [ ] Add APP_DEVELOPMENT_PLAYBOOK.md.
- [ ] Generate AGENTS.md.
- [ ] Generate PROJECT_STATE.md.
- [ ] Review AGENTS.md and PROJECT_STATE.md.
- [ ] Confirm baseline build.
- [ ] Create baseline commit.
- [ ] Push baseline to GitHub.
- [ ] Begin first controlled feature.

---

# 16. Continuous Improvement

Every completed project should improve the process.

At meaningful milestones or project completion, review:
- What repeatedly caused problems?
- What saved significant time?
- Which architecture patterns were genuinely reusable?
- Which testing steps caught real problems?
- Which documentation prevented mistakes?
- Which Codex instructions produced better results?
- Which process steps were unnecessary?

Universal lessons should be incorporated into the master playbook.

The goal is not to freeze the process. The goal is to make each future project benefit from everything learned before it.

---

# 17. Core Principle

The development system is:

PRODUCT OWNER decides WHAT
→ CHATGPT defines HOW IT SHOULD WORK
→ CODEX implements the approved task
→ ANDROID STUDIO builds/debugs it
→ PHYSICAL DEVICE proves the experience
→ PRODUCT OWNER approves it
→ PROJECT STATE records it
→ GIT preserves it
→ GITHUB protects the checkpoint
→ the team moves to the NEXT CONTROLLED CHANGE.

Approved work should become increasingly stable as the project grows.
