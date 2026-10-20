# PROJECT_STATE.md — brdcrmbs

> This file is the current source of truth for the brdcrmbs app.
> Update it throughout development as decisions are approved, features are completed, issues are discovered/resolved, and milestones change.

# Project Identity

- **App name:** brdcrmbs
- **Package name:** com.ventures814.brdcrmbs (namespace and application ID)
- **Platform:** Android
- **Repository:** https://github.com/meridianworx/brdcrmbs.git (origin; primary branch main)
- **Development stage:** Week UI physically accepted and locked; Week data/persistence foundation implemented. Add/Edit and complete Week workflows remain unimplemented.

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
- Room over local SQLite is the durable planning source of truth. Initial database version: 1; schema JSON exported to app/schemas and tracked in Git. No destructive migration fallback; future schema changes require explicit, tested migrations.

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
- Four-destination app shell; TodayScreen, ProgressScreen, and SettingsScreen remain placeholders. The approved My Week layout now loads repository-backed planning data.
- Approved dark charcoal / near-black theme, bright green accent, high-contrast typography, rounded cards, and simple line icons.
- Week persistence and read-side integration implemented; no Add/Edit, adherence UI, Progress calendar, or Settings behavior implemented.

## In Progress

- Future controlled Add/Edit workflows remain to be scoped; Week persistence/read-side foundation is implemented and validated. The Week feature itself is not complete.

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
- Week My Week UI baseline passed physical-device acceptance and is approved/locked by the Product Owner. Data wiring must preserve its layout/theme.

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

Week data/persistence foundation established behind the locked UI. Next controlled focus: an explicitly scoped Add/Edit planning workflow, without changing the approved layout. Add/Edit, Copy Day, Repeat Last Week, rescheduling, Today adherence, Progress, reminders, and Backup & Restore remain unimplemented. The complete Week feature is not finished.

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

## Week UI Baseline — 2026-10-19

- Replaced only the Week placeholder with My Week: branding/title/subtitle, fixed October 19–25, 2026 range, and Mon 19 through Sun 25 day selector with Monday initially selected.
- Static sample cards: Fasting (7:00 PM → 11:00 AM; 16:8 · 16 hour fast), Meals (3: Eggs + avocado, Chicken + vegetables, Salmon + salad), and Workout / Activity (2: Bakal Gym — Upper Body, 20-minute walk), with the requested times and labels.
- Added small Week-only presentation components: SampleWeekSelector, WeekDaySelector, PlanningSectionCard, PlanningRow, and dividers. Existing charcoal/green theme and four-item navigation preserved; other three destinations were not redesigned.
- Day selection changes only the highlight; all days show the same sample content. Week arrows, Add buttons, and planning-row edit affordances intentionally do nothing. No dialogs, real forms, copy logic, calculations, reminders, or data infrastructure added.
- Vertical scrolling respects the existing scaffold insets and bottom navigation. Day tiles have practical touch targets and horizontal overflow for narrow/large-text layouts; row text can wrap.
- No dependencies introduced. Added Week UI tests for day selection/restoration and reaching every sample row; updated shell regression test for the new Week heading.
- Physical-device visual acceptance remains pending. This completes the static UI implementation milestone only, not the Week feature or its product acceptance.
- Validation passed: :app:assembleDebug, :app:testDebugUnitTest (1 test), :app:lintDebug (0 errors, 11 existing baseline warnings), and :app:assembleDebugAndroidTest. Connected emulator suite passed all 4 tests (shell navigation/restoration, package identity, Week day selection/restoration, sample-row scrolling). No physical-device acceptance was performed.

## Week Data / Persistence Foundation — 2026-10-19

- Product Owner confirmed physical-device acceptance of the Week UI baseline; that baseline is now locked. Historical pending-acceptance notes above describe the earlier milestone only.
- Architecture: Compose WeekScreen renders WeekUiState; WeekRoute obtains a lifecycle-aware WeekViewModel; the ViewModel uses PlanningRepository; RoomPlanningRepository is the sole database boundary. BrdcrmbsApplication owns one lazy process-wide repository/database instance. No DI framework or network dependency introduced.
- Database: Room 2.8.5, database file brdcrmbs-planning.db, version 1, exported schemas. Tables: fasting_events, meal_plans, activity_plans. Primary keys are stable UUID strings by default. Date and overlap columns indexed. Activity rescheduledFromId is an optional indexed self-reference with a non-cascading foreign key.
- Plain domain models: FastingEvent, MealPlan, ActualMeal, ActivityPlan, typed outcomes, ActivityType, DateRange, and PlanningData. Storage entities/mapping remain in data/local, outside Compose.
- Dates are LocalDate stored as epoch-day INTEGERs. Optional times are LocalTime stored as nullable nanoseconds-of-day INTEGERs; floating local plans do not shift with device time-zone changes. Fasting endpoints are Instant stored as epoch-millisecond INTEGERs plus original IANA ZoneId text. Sub-millisecond endpoints are rejected instead of silently truncated. One fast remains one row for any duration; only positive duration is required.
- Query ranges are start-inclusive/end-exclusive. Fasts overlap when start < rangeEnd AND end > rangeStart; a fast ending at midnight does not appear on the following date. Calendar range boundaries use atStartOfDay(viewingZone), not fixed 24-hour arithmetic, so 23/25-hour DST dates are handled correctly. Existing instants are unambiguous; resolving ambiguous/nonexistent local input is reserved for the future Add/Edit workflow. Fasting labels retain the event's original zone and show dates when endpoints cross calendar dates.
- Repository: suspend insert-only saves (duplicate IDs abort), transactional range snapshots, and a Flow invalidated by relevant table changes. Supports arbitrary date ranges, day/week queries, and later month queries. Meals/activities use planned calendar dates; multi-day/earlier-started fasts are queried by overlap and appear on each overlapping day without duplication in storage.
- Historical integrity: separate actual meal text/time/label/notes columns; recordChangedMeal updates only actual columns and CHANGED outcome, preserving original planned data. Changed without actual text is rejected. Raw outcome names are stored explicitly. No generic replace/upsert that can silently erase history. Rescheduled activity linkage supports a new record while the original remains MISSED; no rescheduling operation/UI was implemented.
- State: ViewModel observes Room through the repository, filters the chosen day, and exposes StateFlow collected with collectAsStateWithLifecycle. SavedStateHandle retains Monday week start and selected day across recreation; the initial week uses the current local date. Existing week arrows load adjacent ranges; day selection shows that date's plans. No business/date conversion logic lives in Compose.
- UI wiring only: approved card structure, typography, colors, day strip, navigation, and edit affordances preserved. Counts/rows are data-driven; empty sections retain Add and show Nothing planned. Loading/failure text avoids presenting read failures as successful empty results. Add/edit rows remain inert.
- Production database starts empty: no sample seeds or data copied from mockups. Sample plans live only in androidTest; previews use an empty presentation state. Saved Meals/Saved Activities persistence is deferred because it is not necessary for this planning foundation and can be added through a future migration.
- Build additions: Room runtime/compiler and schema plugin, KSP 2.3.9, lifecycle ViewModel/Compose collection artifacts aligned to existing lifecycle 2.11.0, and JDK library desugaring 2.1.5 for java.time on minSdk 24/25. Existing Android SDK, AGP, Kotlin/Compose, and navigation configuration retained.
- Tests added: database file close/reopen durability, planned and Changed meal fields, every outcome, activities and missed-original linkage, same-day/cross-midnight/24h/36h/48h/72h/240h fasts, overlap/midnight boundaries, year/month/week boundaries, DST dates, explicit viewing zones, reactive repository/ViewModel updates, duplicate-ID protection, day filtering/restoration, and empty UI sections. Existing shell regression checks retained.
- Intentionally unimplemented: Add/Edit forms, Copy Day, Repeat Last Week, saved-item UI/storage, actual-meal UI, rescheduling behavior, reminders, Today/Progress/Settings logic, Backup & Restore, accounts/cloud/analytics. The complete Week feature is not marked finished.
- Validation: debug build, Android test APK compilation, and all 5 JVM tests passed; all 14 connected Pixel 6 Pro emulator tests passed (0 skipped/failed). An invalid UI-test import was corrected during validation without removing assertions. The new data wiring has not been tested on a physical device; the previously accepted Week visual baseline remains locked.
- Final verification after the Compose API parameter-order correction: :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest succeeded. Lint: 0 errors, 12 warnings (11 baseline notices plus a newer-KSP-version notice). Existing native-library stripping notice remains non-blocking. No unresolved build/test failures.
