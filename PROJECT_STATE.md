# PROJECT_STATE.md — brdcrmbs

> This file is the current source of truth for the brdcrmbs app.
> Update it throughout development as decisions are approved, features are completed, issues are discovered/resolved, and milestones change.

# Project Identity

- **App name:** brdcrmbs
- **Package name:** com.ventures814.brdcrmbs (namespace and application ID)
- **Platform:** Android
- **Repository:** https://github.com/meridianworx/brdcrmbs.git (origin; primary branch main)
- **Development stage:** App shell/navigation and approved visual foundation implemented; real feature workflows have not begun.

# Purpose

## What the App Does

brdcrmbs is a simple weekly planning and adherence app. The user creates their own fasting, meal, and workout/activity plan, follows it day by day, records what actually happened, and reviews adherence over time.

Core loop:

**PLAN → FOLLOW → TRACK → REVIEW**

## Intended User

A user who wants a simple, self-directed way to plan and review fasting, meals, and workouts/activities without turning the app into a calorie counter, detailed workout logger, or prescriptive health coach.

## Problem Being Solved

Plans for fasting, meals, and activity are easy to make but difficult to follow consistently. brdcrmbs puts the weekly plan, today's commitments, and historical success/failure in one focused interface so the user can stay accountable.

# Product Philosophy

- **You make the plan. The app helps you follow it.**
- The user remains in control of fasting, meals, and workouts/activities.
- The app records adherence; it does not prescribe the plan.
- Prefer simple flows over unnecessary complexity.
- Preserve historical truth instead of rewriting past results.
- Make success, changed behavior, missed commitments, and upcoming plans easy to see.
- Keep the core experience useful without calorie/macro tracking or detailed workout programming.

## Explicit Exclusions

For the MVP, do not add:
- AI-generated diets or meal prescriptions
- AI-generated fasting prescriptions
- Plans driven by glucose, weight, or other health measurements
- Calorie or macro tracking
- Barcode scanning
- Mandatory food weights
- Detailed exercise set/rep/weight logging
- Social/community features
- Punitive streak mechanics
- Meal photography
- A cloud account system unless later required by an explicitly approved backup strategy
- Medical advice or diagnostic functionality

Weight and glucose tracking may be considered later as optional modules, but they do not control the planning engine.

# Core User Flow

Open App  
→ Review **Today** or plan in **Week**  
→ Create fasting, meal, and workout/activity commitments  
→ Follow the plan  
→ Mark items Done, Changed where applicable, or Missed  
→ Review results in **Progress** calendar/history  
→ Adjust future plans as desired

# Navigation

Bottom navigation contains exactly four primary destinations:

1. **Today**
2. **Week**
3. **Progress**
4. **Settings**

# Architecture

## Data Storage

- Local-first.
- Core functionality should work offline.
- User-created plans and history must survive app restarts/process death.
- Persistent structured storage is required.
- Exact Android persistence technology will be finalized during implementation.

## Data Models

Conceptual domain models:

### WeekPlan
- Week identifier/start date
- Day plans

### DayPlan
- Date
- Fasting events
- Meal plans
- Activity plans
- Optional day note

### FastingEvent
- ID
- Planned start date/time
- Planned end date/time
- Preset/custom type
- Status
- Optional actual start/end if later approved

### MealPlan
- ID
- Planned date/time
- Planned meal text
- Meal label/type
- Optional notes
- Outcome: Planned / Done / Changed / Missed
- Actual meal text when Changed
- Optional actual notes

### ActivityPlan
- ID
- Planned date/time
- Name
- Source/type
- Optional notes
- Outcome: Planned / Done / Missed
- Optional relationship to a rescheduled entry

### SavedMeal
Reusable meal details.

### SavedActivity
Reusable custom activity details.

## Repository / Persistence Layer

- UI must not directly own durable persistence logic.
- Use a clear repository/data boundary.
- Prefer simple, maintainable architecture over unnecessary abstraction.
- Schema changes must preserve user data through safe migrations.

## Media / File Storage

- Meal photography is not part of the product.
- No core media storage requirement is currently approved.
- Backup/export files may be introduced as part of Backup & Restore.

## Services / Integrations

- No required network service is currently approved for core functionality.
- Android notification/reminder scheduling will be required.
- Bakal Gym is a planning/reference source for workout names; brdcrmbs does not duplicate detailed workout logging.

## Backup / Restore

Backup & Restore is required.

It must preserve enough information to reconstruct relevant user-created app state, including:
- Plans
- Outcomes/history
- Saved meals
- Saved activities
- Required preferences
- Other user-created records added later

Exact backup destination/storage strategy remains to be decided during implementation.

## Privacy / Security

- Keep the MVP local-first.
- Do not introduce unnecessary collection or transmission of user data.
- Destructive data actions require confirmation.
- Restore must validate input and fail safely.

## Other Important Architecture Decisions

Date/time behavior is critical.

Implementation and testing must account for:
- Week/month/year boundaries
- Cross-midnight fasting
- Multi-day fasting
- Future plans
- Historical plans
- Rescheduling
- Daylight-saving transitions where applicable

A multi-day fast is one continuous event spanning dates, not separate daily fast records.

# Features

## Completed

Product/planning work completed:
- Product purpose and philosophy approved
- MVP scope approved
- Core navigation approved
- Core user flow approved
- Weekly planning behavior approved
- Today adherence behavior approved
- Progress/calendar behavior approved
- Add/Edit flows approved
- Settings scope approved
- Baseline visual direction approved
- 12-screen UI/UX baseline approved

Android foundation implemented:
- Four-destination app shell with TodayScreen, WeekScreen, ProgressScreen, and SettingsScreen placeholders.
- Approved dark charcoal / near-black theme, bright green accent, high-contrast typography, rounded cards, and simple line icons.
- No real planning, adherence, calendar, settings, or persistence behavior implemented.

## In Progress

- No product implementation task is currently in progress.

## Planned

### Week — Plan My Week

The primary planning surface.

User can plan Monday–Sunday with:
- Fasting
- Meals
- Workout / Activity

Required planning behaviors:
- Select day
- Add/edit/delete items
- Copy Day
- Copy to Selected Days
- Repeat Last Week
- Future-week planning
- Historical-week retention

#### Fasting Presets
- 12:12
- 14:10
- 16:8
- 18:6
- 20:4
- OMAD
- 24h
- 36h
- 48h
- 72h
- Custom
- No fast

Custom fasting supports explicit start and end date/time.

#### Meals
A meal may contain:
- Name/description
- Optional time
- Optional label/type
- Optional notes
- Saved/reusable status

#### Workout / Activity
Sources:
- Bakal Gym workout/reference
- Saved custom activity
- New custom activity

A custom activity may contain:
- Name
- Optional time
- Type
- Optional notes
- Saved/reusable status

Multiple activities may exist on one day.

### Today

Shows the current day's plan and records outcomes.

Fasting states:
- Planned
- Done
- Missed

Meal states:
- Planned
- Done
- Changed
- Missed

A Changed meal preserves both:
- Original planned meal
- Actual meal eaten

Workout/activity states:
- Planned
- Done
- Missed

If a missed workout is moved to another day, the original missed history remains intact.

### Progress

Monthly calendar/accountability view.

Calendar indicators:
- **F** = Fasting
- **M** = Meals
- **A** = Activity

Status language:
- ✓ Success
- ◐ Changed
- ✕ Missed
- ○ Planned
- — Nothing planned where applicable

Meals may display fractional adherence such as 2/3 or 3/3.

Multi-day fasting should be visually represented as a continuous event across dates.

Tapping a date opens Day Detail.

Weekly/monthly category and overall adherence summaries may be shown.

Do not use punitive broken-streak framing.

### Day Detail

Shows historical truth for a selected date:
- Planned fasting and outcome
- Planned meals and changed actual meals
- Planned workouts/activities and outcomes
- Daily adherence summary
- Optional day note

### Settings

Baseline Settings areas:
- Reminders
- Backup & Restore
- App Preferences
- Saved Meals
- Saved Activities
- Data Management
- About

### Reminders

Support user-configured reminders for:
- Fasting start
- Fasting end
- Planned meals
- Planned workouts/activities

### App Preferences

Keep practical and minimal.

Potential baseline preferences:
- Appearance
- Time format
- Week start
- Default fasting preset
- Default meal count
- In-app notifications

Do not add measurement/unit settings unless an implemented feature actually needs them.

# Locked Decisions

The following have Product Owner approval and must not be changed casually:

- App name: **brdcrmbs**
- Product philosophy: **You make the plan. The app helps you follow it.**
- Core loop: **PLAN → FOLLOW → TRACK → REVIEW**
- Four bottom navigation destinations: Today / Week / Progress / Settings
- Three core planning categories: Fasting / Meals / Workout-Activity
- The app does not prescribe plans from weight/glucose/health data.
- Meal changes preserve planned and actual meals separately.
- Changed is a distinct meal outcome, not silently converted to Done or Missed.
- Historical misses are not erased by rescheduling.
- Multi-day fasting is one continuous event.
- Progress uses a monthly calendar as the primary accountability/history view.
- Backup & Restore is required.
- Meal photography is excluded.
- Detailed workout programming/logging remains outside brdcrmbs.
- Approved UI must not be casually redesigned during implementation.

# Approved UI / UX

Approved baseline visual direction:
- Dark charcoal / near-black interface
- Bright green accent
- High-contrast white/off-white typography
- Rounded cards
- Clean Android mobile layout
- Simple line/glyph icons
- Four-item bottom navigation
- Clear Success / Changed / Missed / Planned states
- Useful information density without clutter
- Meals displayed as text only; no food photos

Approved baseline screens:
1. Week
2. Today
3. Progress
4. Add/Edit Fasting
5. Add/Edit Meal
6. Change Meal / Log Actual Meal
7. Add/Edit Workout/Activity
8. Day Detail
9. Settings
10. Reminders
11. Backup & Restore
12. App Preferences

The approved visual mockups establish the baseline design language. Implementation should preserve that direction rather than reinterpret it.

# Known Issues

- None currently documented.
- Exact backup storage/destination implementation is not yet selected.

# Backlog

Possible future considerations, not part of the current MVP unless explicitly approved:
- Optional weight tracking
- Optional glucose tracking
- Additional reusable weekly templates
- Additional progress/trend views

Future health measurements must not automatically control the user's plan.

# Current Development Focus

App shell/navigation and visual foundation milestone complete. Next focus: the first real feature workflow, to be specified in a separate controlled task by the Product Owner. Feature behavior and persistence remain unimplemented. Physical-device review of the shell remains pending.

# Recent Milestones

- brdcrmbs product concept approved.
- Core PLAN → FOLLOW → TRACK → REVIEW workflow approved.
- Week / Today / Progress UX approved.
- Supporting add/edit/settings flows approved.
- 12-screen visual baseline established.
- Meal photography explicitly removed from the design.
- Phase 0 project documentation generated from the standard templates.

## Android Baseline — 2026-10-19

- Existing Android Studio starter preserved: MainActivity with Hello Android, Compose theme, launcher resources, manifest, backup XML templates, local unit test, and instrumented test.
- Single app module; namespace/application ID com.ventures814.brdcrmbs; versionCode 1 / versionName 1.0.
- minSdk 24; compileSdk 37; targetSdk 37; Java source/target compatibility 11.
- Android Gradle Plugin 9.3.3 with built-in Kotlin; Compose compiler plugin 2.2.10; Compose BOM 2026.02.01; Material 3.
- Gradle wrapper 9.5.0 with distribution checksum; daemon JVM criteria Java 25; repositories Google, Maven Central, and Gradle Plugin Portal.
- Baseline verification succeeded: :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug, using Android Studio bundled Java as JAVA_HOME. No SDK or dependency changes were necessary.
- Git initialized on main; origin configured to the repository above. Local IDE files, caches, build outputs, local.properties, and common signing/secret files excluded.
- Build warning: libandroidx.graphics.path.so could not be stripped and was packaged unchanged. Device/instrumented testing was not run; release output is unsigned.
- Baseline milestone: project structure and all three root control documents verified; no brdcrmbs product features implemented. GitHub publication is verified separately after the baseline commit.
- Lint completed with 0 errors and 11 warnings: redundant activity label, seven unused starter colors, and three dependency update notices. The single starter unit test passed.

## App Shell and Visual Foundation — 2026-10-19

- Replaced Hello Android with a Material 3 scaffold and four bottom destinations in the exact order Today / Week / Progress / Settings.
- Today is the initial destination. Selected-tab UI state uses rememberSaveable for activity recreation; no durable product persistence was added.
- Added TodayScreen, WeekScreen, ProgressScreen, and SettingsScreen as static placeholders sharing a small presentational layout.
- Implemented near-black background, charcoal cards, bright green accents, off-white typography, rounded shapes, line icons, and dark system-bar/launch presentation. System wallpaper colors do not override the approved palette.
- Retained the existing Material 3 typography baseline. Content scrolls for smaller screens/larger text; navigation remains in the scaffold bottom bar with system insets.
- Added a Compose instrumentation test for all four destinations and selected-tab restoration; compiled successfully. No connected Android device/emulator was available, so UI tests and visual/device acceptance were not run.
- Validation: debug build, existing unit test, debug lint, and debug Android test APK build passed. No new dependencies or product infrastructure added.
- Scope remains shell only: no planning, adherence, calendar, forms, settings actions, reminders, backup/restore, databases, repositories, or meal photos.
- Debug lint reports 0 errors and the same 11 baseline warnings (redundant label, unused starter colors, dependency update notices); no dependency upgrades or unrelated cleanup performed.
