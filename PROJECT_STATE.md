# PROJECT_STATE.md — brdcrmbs

> This file is the current source of truth for the brdcrmbs app.
> Update it throughout development as decisions are approved, features are completed, issues are discovered/resolved, and milestones change.

# Project Identity

- **App name:** brdcrmbs
- **Package name:** com.ventures814.brdcrmbs (namespace and application ID)
- **Platform:** Android
- **Repository:** https://github.com/meridianworx/brdcrmbs.git (origin; primary branch main)
- **Development stage:** Week, Today adherence, Progress, Settings foundation, Saved Meals / Saved Activities management and Reminders are physically accepted/locked. The focused Today fasting progress enhancement is implemented and validated; its physical-device acceptance remains pending. Overall Settings is not complete.

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

Finalized Week selection rules:
- Enter, re-enter or reselect the Week bottom-navigation tab: current local calendar week with today selected.
- Previous/next week arrows: destination calendar week with Monday selected, even when returning to the actual current week. Arrow navigation also clears transient Planning Action feedback.

# Architecture

## Data Storage

- Local-first.
- Core functionality should work offline.
- User-created plans and history must survive app restarts/process death.
- Persistent structured storage is required.
- Room over local SQLite is the durable planning source of truth. Initial database version: 1; current database version: 4 with explicit 1→2 migration for dated No fast plans, 2→3 for Saved Meals, and 3→4 for Saved Activities. All schema JSON versions are exported to app/schemas and tracked in Git. No destructive migration fallback; future schema changes require explicit, tested migrations.

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
- Four-destination app shell; Week, Today and Progress use the same repository-backed plans. Settings has grouped navigation and independent Saved Meals / Saved Activities library management.
- Approved dark charcoal / near-black theme, bright green accent, high-contrast typography, rounded cards, and simple line icons.
- Week persistence/read-side integration and fasting Add/Edit/Delete implemented; fasting CRUD passed physical-device Product Owner acceptance and is approved/locked.
- Meal CRUD and Saved Meal selection/persistence passed physical-device acceptance and are approved/locked. Workout / Activity CRUD, Saved Activity selection, and Bakal Gym textual planning passed physical-device acceptance and are approved/locked. Copy Day, Copy to Selected Days, Repeat Last Week, Planning Actions discoverability and finalized Week navigation/feedback passed acceptance and are approved/locked. Today adherence and Change Meal / Log Actual passed acceptance and are approved/locked. Corrected Progress calendar / Day Detail passed populated physical-device acceptance and is approved/locked. Settings foundation and both saved-library create/edit/delete workflows passed physical-device acceptance and are approved/locked.

## In Progress

- Today active fasting progress enhancement passed automated validation and emulator visual review; Product Owner physical-device acceptance of this enhancement remains pending. Reminders were physically tested and accepted by the Product Owner ("looks good") and are now LOCKED; earlier pending Reminders statements below are historical. Settings foundation and Saved Meals / Saved Activities management remain approved/locked.

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

- Reminders are physically accepted/LOCKED following Product Owner device testing and "looks good." This milestone does not modify reminder code or scheduling behavior; older pending Reminders notes describe the previous checkpoint.

- Settings foundation, Saved Meals management and Saved Activities management are physically accepted/locked following Product Owner testing and "everything looks good." This covers library CRUD/confirmations, editor → library → Settings Back behavior and the existing debug section. Overall Settings remains incomplete.

- Progress is approved/locked following Product Owner populated physical-device acceptance: compact summary rings, real resolved percentages/counts, calendar/category indicators, legend/date selection, consolidated Day Detail/daily score, expanded full-history details, multi-day fasting and existing aggregation/business rules. Do not redesign or change these behaviors during Settings work.

- Today adherence and Change Meal / Log Actual passed physical-device Product Owner acceptance and are approved/locked, as confirmed in the Progress request. Earlier pending Today milestone statements below are historical.

- Complete Week planning workflow passed physical-device Product Owner acceptance, as confirmed in the Today request: CRUD, reusable selection, shortcuts, Planning Actions UI, feedback lifecycle and finalized tab/arrow navigation are approved/locked. Earlier pending-acceptance milestone notes are historical.

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
- Fasting Add/Edit/Delete passed physical-device Product Owner acceptance and is approved/locked, as confirmed in the Meal workflow request on 2026-10-20.

- Meal CRUD and Saved Meal selection passed physical-device Product Owner acceptance and are approved/locked, as confirmed in the activity workflow request.
- Saved Meal deletion/management belongs in Settings → Saved Meals; Saved Activity deletion/management belongs in Settings → Saved Activities. Week editors only plan/select reusable items; Settings manages the independent reusable libraries.

- Workout / Activity CRUD, Saved Activity selection, and Bakal Gym textual planning passed physical-device Product Owner acceptance and are approved/locked, as confirmed in the planning-shortcuts request.

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

Progress Screen 3 visual source of truth: docs/design/progress-approved-screen-3.png, supplied as Image 3 in the correction request. Images 1 and 2 document the rejected current presentation and are not design references. The mockup's numerical values are examples only; all displayed scores/counts remain calculated from persisted data.

# Known Issues

- Week, Today and corrected Progress passed physical-device Product Owner acceptance and are approved/locked. The populated Progress review used the debug QA dataset, including mixed statuses, meal fractions, full Day Detail and overlapping fasting. Earlier visual divergence is resolved; the approved reference remains at docs/design/progress-approved-screen-3.png.
- Exact backup storage/destination implementation is not yet selected.

# Backlog

Possible future considerations, not part of the current MVP unless explicitly approved:
- Optional weight tracking
- Optional glucose tracking
- Additional reusable weekly templates
- Additional progress/trend views

Future health measurements must not automatically control the user's plan.

# Current Development Focus

Product Owner physical-device acceptance of the new Today active fasting progress presentation. Preserve locked Week, Today adherence, Progress, Settings foundation, saved libraries and Reminders. Backup & Restore, App Preferences, Data Management and About remain future Settings milestones.

Debug builds provide Settings → Developer / Testing → Progress Test Data for repeatable physical-device QA of Week, Today and Progress. This is development tooling, not product Settings functionality; source-set release isolation must be preserved.

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

## Week Fasting Add / Edit / Delete — 2026-10-19

- Implemented a focused Add Fast / Edit Fast screen using the existing charcoal/green Material 3 foundation. Week Fasting Add opens a new draft; its rows open the specific persisted ID, including when the same event overlaps multiple selected dates. Meals and Workout / Activity retain their layout and inert actions.
- Editor includes preset chips, explicit start/end date and time controls, zone/UTC offsets, calculated elapsed duration, Save, Back, and confirmed Delete for existing Planned records. The existing bottom navigation is hidden during the focused editor and restored on return. Back discards the draft without writing; SavedStateHandle retains the serializable draft through recreation. Busy operations prevent duplicate submission; persistence failures keep entries available.
- New drafts initialize at 7:00 PM on the Week-selected calendar date, with the 16:8 preset and an end 16 elapsed hours later (normally 11:00 AM next day). Nothing is persisted until Save.
- Duration presets: 12:12=12h, 14:10=14h, 16:8=16h, 18:6=18h, 20:4=20h, 24h, 36h, 48h, 72h. Selecting one recomputes the end from the start using elapsed time; changing its start recomputes the end. Manually changing its end switches to Custom. Custom retains explicit endpoints with no application-level duration cap. OMAD is a label only and retains the explicit schedule; no duration or health rule is invented.
- No fast is a separate NoFastPlan / no_fast_plans record with ID, local planned date, original zone, and optional preserved notes. It is distinguishable from an empty fasting section and never becomes a zero-duration FastingEvent. Its editor shows Planned day instead of start/end/duration. No outcome is fabricated for a non-fasting choice. Saving an edit that changes kind converts only the selected plan atomically while retaining its ID; it does not silently delete other plans or resolve conflicts with separate overlapping fasts.
- Database version 2 adds only the no_fast_plans table and its date index. MIGRATION_1_2 creates that table/index without rewriting version-1 events, meals/actuals, activities, or outcomes. Both version schemas are retained; production registers the explicit migration and has no destructive fallback.
- Repository additions: typed FastingPlan lookup, add, update, and delete. Existing event insertion API remains compatible. Updates preserve identity and do not alter stored outcomes. Only PLANNED fasting events may be edited, converted, or deleted; Done/Missed event plans are read-only in the editor and guarded again in persistence. No fast choices remain editable/deletable. All mutations use the existing repository/Room boundary and transactions; Week reacts through its existing Flow.
- Date/time representation retained: positive-duration fasting instants at millisecond precision plus original zone. Cross-midnight, month/year boundaries, and multi-day events stay one database row. Existing offsets and sub-minute precision survive unchanged edits. New ambiguous repeated-hour local input uses the earlier valid offset; existing repeated-hour offsets are retained. Nonexistent DST-gap times are rejected with inline validation rather than silently shifted. Preset hours are real elapsed hours across DST, with resulting local endpoints shown for inspection.
- Validation is non-destructive: zero/negative duration or nonexistent local time cannot be saved. No new runtime dependency, network, prescription logic, or unrelated feature workflow was added. Migration verification recreates the exported version-1 schema with Android SQLite and opens it through the production Room migration/validator; no new dependency is retained.
- Tests added: preset/default/duration/OMAD/Custom/No fast behavior, invalid schedules, DST gaps and repeated offsets, draft serialization, retained fields/precision, durable fasting reload, identity-preserving edit, selected-record deletion, long-fast single-record storage, No fast conversions, recorded-outcome protection, reactive Week updates, version-1-to-2 migration preserving prior history, and Compose Add→Save→Week→Edit→confirmed Delete plus unsaved Back/No fast coverage.
- Physical-device acceptance of fasting CRUD is pending. Automated validation does not approve/lock this workflow, and the complete Week feature is not marked finished.

### Resumed validation — 2026-10-20

- Resumed the existing uncommitted fasting implementation without resetting/replacing its UI, models, migration, or repository. No fasting milestone commit existed at resume time.
- The interrupted validation had passed the app build, JVM tests, lint, and 20 of 21 emulator tests. Its migration test failed inside MigrationTestHelper schema deserialization before running the migration: test JSON 1.8.1 and the app-constrained serialization core 1.7.3 had incompatible generated serializer behavior.
- Removed the newly introduced helper-only Room testing dependency rather than upgrading unrelated runtime dependencies. Migration coverage still uses both committed schema versions: version 1 is recreated from exported SQL, the actual registered 1→2 migration runs on Room open, Room validates all version-2 tables/indices/foreign keys, and the test checks exported identity hash, exact user-table set, integrity/foreign-key checks, preserved history, and No fast insertion. Original history assertions were retained and full planned/actual meal equality added.
- Retained the added picker-cancel regression test and asynchronous editor-load waits from the interrupted working tree. No approved Week layout or unrelated feature was redesigned, and no previous implementation was discarded or duplicated.
- The first resumed connected-test run also selected a newly connected physical Pixel 6 Pro. Its keyguard was confirmed showing; all seven Compose UI tests failed to expose/launch interactive content, while its fifteen non-UI tests (including the actual migration test) passed. No phone lock settings were changed. Final automated UI validation targets emulator-5554 explicitly; Product Owner physical-device acceptance remains pending.
- Final validation passed: :app:assembleDebug, :app:testDebugUnitTest (13 tests, 0 failures/errors), :app:lintDebug (0 errors, 11 existing warnings), and :app:assembleDebugAndroidTest. With ANDROID_SERIAL=emulator-5554, :app:connectedDebugAndroidTest completed successfully: 22 tests, 0 skipped/failed, including migration, persistence, fasting workflow/UI, and existing Week/app-shell regressions. No assertions were disabled.
- Remaining notices: existing redundant activity label, seven unused starter colors, three dependency update notices, and the non-blocking native-library stripping notice. No unresolved automated build/test failures remain on the emulator. Physical-device Product Owner acceptance is still pending; this workflow is not approved/locked and the remaining Week workflows are not implemented.

## Week Meal Planning Add / Edit / Delete — 2026-10-20

- Product Owner confirmed physical-device acceptance of Fasting CRUD in this task request; Fasting CRUD is now approved/locked. Earlier pending-acceptance statements above are historical. Existing fasting draft/editor behavior and tests are retained without changes.
- Added a focused text-only Add Meal / Edit Meal editor consistent with the existing charcoal/green Material 3 styling. Required meal text and date; optional local time, free-text type/label, and notes. Week-selected day supplies the initial date. No time is assigned unless the user confirms one; an assigned time can be removed. Date/time picker cancellation does not change the draft.
- Save persists a new PLANNED meal or updates the exact existing ID, then returns to Week. Confirmed Delete removes only that planned meal. Week retains its approved layout and text-row treatment, renders optional time/label, and reacts with the selected day's current meal count. Workout / Activity actions remain inert.
- Added SavedMeal / saved_meals with stable ID, text, optional label, and optional notes. A Saved Meal has no date/time/outcome and is not a scheduled plan. Choose Saved Meal opens a compact scrollable selection dialog and fills text/label/notes while retaining draft identity/date/time. The user can change populated fields before Save. Save for reuse is opt-in for either new or edited plans; the plan and reusable details are saved atomically, deduplicating exact text/label/notes matches. Changing a scheduled meal does not rewrite an existing Saved Meal; deleting a scheduled meal leaves Saved Meals intact. Settings management remains deferred.
- Reused existing meal_plans columns without alteration. Database version 3 adds only saved_meals via explicit MIGRATION_2_3; production registers the complete 1→2→3 chain. Version 1/2 schemas are unchanged and version 3 is exported. No destructive fallback or new dependencies.
- Repository additions: getMeal, savePlannedMeal (insert/update plus optional reusable save in one transaction), deletePlannedMeal, and savedMeals. Planning updates target planned columns only and guard PLANNED/outcome plus absent actual text in SQL. Done/Changed/Missed records are read-only in the editor and cannot be overwritten/deleted through planning CRUD, including a stale editor after an outcome changes. Original planned and actual Changed-meal details/outcomes remain intact; no adherence UI was implemented.
- Added a serializable MealDraft retained by SavedStateHandle, meal loading/busy/error state, and Week route integration using the existing Compose → ViewModel → repository → Room boundary. Bottom navigation is hidden during the focused editor and restored on return; Back discards unsaved entries without database writes. Save errors retain form entries; busy operations block duplicate submits and Back.
- Dates remain LocalDate/epoch-day and optional times remain LocalTime/nanoseconds-of-day, including existing sub-minute precision on unchanged edits. No zone conversion is added for local meal plans. Tests cover month/year boundaries, selected-day association, optional fields, reload durability, identity-preserving update, selected deletion, reactive counts, reusable selection/deduplication, draft recreation, historical/stale-edit protection, and both migration paths preserving fasting/history.
- Meal physical-device Product Owner acceptance remains pending; this workflow is not approved/locked and the complete Week feature is not finished. Remaining scope includes Workout / Activity CRUD, Change Meal / Log Actual, copying/repeat workflows, Today/Progress, reminders, Backup & Restore, and Settings Saved Meals management.
- Final validation passed using the repository wrapper: :app:assembleDebug, :app:testDebugUnitTest (17 tests, 0 failures/errors), :app:lintDebug (0 errors, 11 existing warnings), :app:assembleDebugAndroidTest, and :app:connectedDebugAndroidTest with ANDROID_SERIAL=emulator-5554 (28 tests, 0 failures/errors/skipped). Existing fasting, Week, and shell tests passed unchanged; migration coverage exercises both 1→2→3 and 2→3. No assertions were disabled or weakened.
- Validation environment: the first cold-boot emulator run passed 27/28 tests; the unchanged app-shell test briefly lacked Compose content amid heavy emulator frame delays. The final complete rerun passed after warm-up and proportionally reducing emulator pixel resolution/density while preserving logical layout size. Meal keyboard insets were included in that final build. Existing warnings remain the redundant activity label, seven unused starter colors, and three dependency update notices; no unresolved automated build/test failures remain. Physical-device acceptance of Meals is still pending.

## Week Workout / Activity Add / Edit / Delete — 2026-10-20

- Resumed nine existing uncommitted files (repository/DAO/database/entities/contract, SavedActivity, ActivityDraft/editor, and strings), with no activity milestone commit. Preserved this implementation and completed Week integration, tests, schema export, and documentation; no reset, duplicate implementation, or unrelated refactor.
- Product Owner confirmed physical-device acceptance and locking of Meal CRUD and Saved Meal selection. Saved Meal management/deletion is deferred to Settings → Saved Meals. Saved Activity management/deletion will likewise live in Settings → Saved Activities; neither management UI is part of Week planning.
- Added focused Add Activity / Edit Activity with required name/date, explicit Workout or Activity type, optional removable time, optional notes, Save for reuse, Save, Back, and confirmed Delete. Selected Week day supplies the initial date; no writes occur until Save. Serializable drafts survive recreation through SavedStateHandle, with repository-backed loading/busy/error state and retained entries after persistence errors. Bottom navigation hides during editing and returns afterward; Back discards unsaved edits.
- Planning entry choices are Bakal Gym, Saved Activity, and New Custom Activity. Bakal Gym selects Workout and lets the user enter the textual reference/name, for example Bakal Gym — Upper Body. Source is an editor entry method, not additional persisted provenance: the durable plan is name/type/date/time/notes. No deep linking, app integration, shared data, or detailed exercise/set/rep/weight logging.
- Reused activity_plans unchanged. Repository getActivity/savePlannedActivity/deletePlannedActivity/savedActivities use the existing Compose → ViewModel → repository → Room boundary. Save updates the same ID or inserts a new PLANNED record; optional reusable saving is atomic and deduplicates exact name/type/notes. Confirmed Delete targets only that scheduled ID and leaves reusable items intact. Multiple independent records per date are supported; Week rows show available time, name and type, and selected-day counts update reactively without redesigning the locked layout.
- SavedActivity / saved_activities stores stable ID, name, type and optional notes only: no scheduled date/time or outcome. Selecting one populates name/type/notes while preserving draft ID/date/time, without creating a scheduled record. All form fields remain editable before Save. No reusable-item management or delete action was added to Week editors.
- Database version 4 adds only saved_activities via explicit MIGRATION_3_4; the production chain registers 1→2→3→4. Earlier schemas remain unchanged, and version 4 is exported. No destructive fallback or dependency changes. Migration tests cover starts at 1, 2, and 3, preserving fasting, No fast, planned/actual meals, Saved Meals, activities, outcomes and historical linkage.
- Done/Missed records are read-only in the editor; planning update/delete SQL requires PLANNED. Updates cannot alter outcomes or rescheduledFromId. Existing linked plans retain their linkage on edit; creating a reschedule through the new planning API is rejected. The previous foundation representation remains supported, and the missed original survives edits/deletion of a later planned record. No rescheduling workflow implemented.
- Existing LocalDate/epoch-day and optional LocalTime/nanoseconds-of-day architecture is retained, including sub-minute precision on unchanged edits. Tests cover month/year dates, multiple daily records, optional time/notes, reload, identity-preserving edit, selected deletion, reusable persistence/deduplication/selection, reactive counts, draft restoration, and historical protection. Locked Fasting/Meal editor implementations and their tests remain unchanged.
- Workout / Activity physical-device Product Owner acceptance remains pending. This workflow is not approved/locked and the complete Week feature remains unfinished. Remaining scope: rescheduling, Change Meal / Log Actual, copying/repeating, Today adherence, Progress, reminders, Backup & Restore, and Settings reusable-item management.
- Final validation passed using the repository wrapper: :app:assembleDebug, :app:testDebugUnitTest (20 tests, 0 failures/errors), :app:lintDebug (0 errors, 11 existing warnings), :app:assembleDebugAndroidTest, and :app:connectedDebugAndroidTest with ANDROID_SERIAL=emulator-5554 (35 tests, 0 failures/errors/skipped). All activity, Fasting, Meal, persistence, migration, Week, and app-shell tests passed. Existing editor and regression assertions were retained.
- The first connected run passed 34/35 tests; the version-3 migration fixture failed before migration because Room omits the indices array for tables without indexes. Corrected the fixture to read the optional array as empty; all schema validation and history assertions remain. The full rerun then passed. Initial emulator setup also emitted non-blocking additional-test-output storage permission notices; final validation completed successfully. The temporary proportional emulator display override is restored after testing; no data wipe or dependency upgrade was used. Existing lint notices are the redundant activity label, seven unused starter colors, and three dependency update notices. No unresolved automated build/test failures remain; physical-device Product Owner acceptance is still pending.

## Week Planning Shortcuts — 2026-10-20

- Product Owner confirmed physical-device acceptance and locking of Workout / Activity CRUD, Saved Activity selection and Bakal Gym textual planning. All three core Week planning categories are now functional and approved. Existing Week cards, CRUD editors, historical rules, reusable selection, and four-tab navigation remain intact.
- Added one compact Planning actions control below the day selector: Copy Day, Copy to Selected Days, and Repeat Last Week. Copy dialogs identify the captured selected source date, allow single/multiple destination dates, disable the source as a destination, highlight selections in green, and allow destination-week navigation (including past/future weeks). Cancel/Back performs no write; busy submissions prevent duplicate taps/dismissal. Draft selections survive recreation via SavedStateHandle. Success reports added/skipped counts; an empty source adds nothing and reports Nothing to copy. Errors retain the dialog and report that nothing was copied.
- Repeat Last Week requires explicit confirmation identifying the previous/current Monday–Sunday ranges and stating existing plans will not be deleted. It copies each corresponding weekday with a seven-calendar-date shift. Week header arrows were already functional and are preserved: previous/next week reload the repository range and seven dates, retaining the selected weekday index. There is no current-week-only restriction.
- Product Owner explicitly chose start-day ownership for multi-day fasting: Copy Day only copies fasting events whose start date falls on the selected source day in the Week viewing zone; Repeat Last Week copies fasts whose start falls in the previous viewed week. An earlier-started fast may be visible by overlap but is not copied again. Each selected fast is translated once and remains one full continuous record, including its end beyond the source/destination day or week.
- Copies use original planning structure even when source outcomes are Done/Missed/Changed: fresh UUIDs, PLANNED outcomes, original planned meal fields only, no actual meal information, and no activity rescheduledFromId. Source history is never rewritten. Explicit No fast plans copy as independent dated NoFastPlan records. No Saved Meal / Saved Activity records are created, changed or deleted.
- Date/time translation: meal/activity dates shift by the calendar-day difference and retain exact optional LocalTime/nanosecond values, labels/types and notes. No time is invented. Fasting starts shift calendar dates in their original recorded zone while retaining local clock time and sub-minute precision; end = translated start + original elapsed duration. Across DST, the resulting end clock may differ to retain duration. If the original offset is valid in a repeated hour it is retained; otherwise the earlier valid offset is used. A nonexistent DST-gap start rejects the whole request rather than shifting silently. Month/year/week boundaries use LocalDate arithmetic.
- Deterministic duplicate rule: only an existing PLANNED record with exactly matching scheduled fields suppresses insertion. Meals compare date/time/text/label/notes; activities compare date/time/name/type/notes, ignoring historical linkage; fasts compare absolute start/end, original zone, preset and notes. No fast compares date/zone/notes. IDs, actuals and outcomes are not copied into keys; historical Done/Missed/Changed destination records do not suppress a new planned item. Case/whitespace, null versus empty, and precision are preserved, not normalized. Duplicates within a request and repeated/concurrent copies are skipped; differing plans coexist. No destination record is deleted/overwritten and No fast conflicts are not automatically resolved.
- Repository copyDay/repeatLastWeek wrap the source snapshot, candidate construction, duplicate checks and all inserts in one Room transaction. All destinations use the same pre-insert source snapshot. DST validation failures and later insert failures roll back the whole operation; Week reacts through the existing planning Flow. Domain translation lives outside Compose. Database version 4, all schemas, entities, migrations, dependencies and SDK configuration remain unchanged.
- Remaining Week work for this milestone is physical-device Product Owner acceptance of the shortcuts. They are not approved/locked and the complete Week feature is not marked finished. No adherence, activity rescheduling, copying of historical outcomes, reusable management, reminders, backup, detailed logging or network feature was added.

### Resumed shortcut validation — 2026-10-21

- Found and preserved 12 uncommitted milestone files with the shortcut implementation and tests already present; no shortcut commit existed. Reviewed the complete changes and finished validation/documentation without resetting, replacing or duplicating the implementation. Existing Fasting/Meal/Activity editor implementations and regression assertions remain unchanged.
- Final repository-wrapper validation passed: :app:assembleDebug, :app:testDebugUnitTest (28 tests, 0 failures/errors/skipped), :app:lintDebug (0 errors, 11 existing warnings), :app:assembleDebugAndroidTest, and :app:connectedDebugAndroidTest targeting emulator-5554 (45 tests, 0 failures/errors/skipped). Coverage includes original-plan copying, new identities/Planned outcomes, untouched history/libraries/destinations, exact and concurrent duplicate prevention, multiple destinations, empty sources, No fast, start-date ownership, continuous multi-day events, month/year boundaries, DST translation/rejection, transaction rollback, shortcut dialogs, week navigation, persistence/migrations and all locked CRUD regressions. No assertions were weakened or disabled.
- The first emulator run passed 44/45 tests, including every new shortcut test; an unchanged Activity UI test temporarily found no Compose hierarchy during startup. The full warmed-emulator rerun passed unchanged. Testing used a proportional display override preserving logical layout size; original emulator size/density were restored and the task-created emulator stopped afterward. No physical-device test was performed in this milestone.
- Remaining lint notices are one redundant activity label, seven unused starter colors and three dependency update notices. No unresolved automated build/test failure remains. Planning-shortcut physical-device Product Owner acceptance is still pending; shortcuts are not approved/locked and the complete Week feature is not marked finished.

## Week Planning Actions Discoverability — 2026-10-21

- Physical-device testing failed shortcut usability acceptance: the green Planning actions TextButton looked like a passive section label and the three shortcuts were not discoverable.
- Replaced that presentation with a compact rounded charcoal button, green outline/text, existing small planning icon and down chevron. It remains below the day selector and secondary to the planning cards; the approved Week layout and visual language are preserved.
- Tapping opens a charcoal Material 3 bottom sheet containing exactly Copy Day (Copy this day's plan to another day), Copy to Selected Days (Copy this day's plan to multiple days), and Repeat Last Week (Add last week's plan to this week). Rows have at least 72dp touch targets; the sheet dismisses normally without changing the selected day or writing plans. Selecting a row dismisses the sheet and invokes the existing shortcut dialog using the selected day/displayed week.
- Copy business logic, duplicate prevention, fasting start-date ownership, date/DST translation, repository transactions, Week navigation and locked Fasting/Meal/Activity CRUD are unchanged. Room stays version 4; no schema, migration, dependency or saved-library change.
- Added UI coverage for the visible clickable button, all actions/descriptions, dismissal without writes/selection loss, and each existing workflow with a selected future-week day. Existing shortcut and CRUD assertions are retained.
- Validation passed: :app:assembleDebug, :app:testDebugUnitTest (28 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest and :app:lintDebug (0 errors, 11 existing warnings). The full 47-test emulator run passed all 45 existing regressions and the new workflow-dispatch test; the new Back-dismissal test was blocked by an emulator System UI ANR overlay. Espresso root selection and accessibility/native Back delivery were investigated without changing app behavior or removing assertions. A screenshot confirmed the system dialog; clearing the unresponsive System UI restored normal input. The isolated dismissal test then passed, followed by the final complete PlanningShortcutsUiTest run: 5/5 passed, 0 failures/errors/skipped. The test now delivers a Back key to the focused window and waits for dismissal while retaining all visibility, selected-day and no-write assertions.
- Existing lint notices remain one redundant activity label, seven unused starter colors and three dependency update notices. No unresolved app build/test failure remains. The temporary proportional emulator display override was restored after validation; the task-created emulator was stopped. Physical-device usability acceptance requires Product Owner retesting.
- Planning-shortcut physical-device acceptance remains pending until the Product Owner retests this correction. Shortcuts are not approved/locked.

## Week Navigation and Feedback Lifecycle — 2026-10-21

- Physical-device testing identified indefinitely visible Planning Action results and retained browsed-week/day context after returning through bottom navigation.
- Planning Action result feedback remains visible while reviewing the same displayed week, including selecting another day. Starting a new action clears/replaces the previous feedback; previous/next week navigation, leaving Week, returning/resetting Week and activity/composition recreation clear it. There is no automatic timeout. Feedback is memory-only: never stored in Room, preferences, SavedStateHandle or restored state. A context generation guard prevents an in-flight copy from bringing feedback back after that context has been cleared; the copy transaction itself is not cancelled or changed.
- Entering/re-entering Week through bottom navigation resets to the current local calendar week with today's actual date selected. Tapping the already-selected Week tab performs the same return-to-today reset. The existing Monday week start and Clock/local-date approach are preserved; the current date is read on each reset rather than cached or hardcoded.
- Browsing past/future weeks, selecting other days, editing and Planning Actions remain available while staying in Week. Ordinary recomposition and recreation do not request a navigation reset; entering/reselecting Week consumes one explicit reset request.
- Reset changes only displayed week, selected day and transient feedback. Persisted historical/future plans, outcomes, reusable records and source/destination copy data are untouched. Copy logic, duplicate prevention, fasting start-date ownership, transactions, locked CRUD, Room version 4, schemas, migrations and dependencies remain unchanged.
- Added app-shell UI coverage for current-week/today entry and reselection, all three tab exits, past/future browsing, retained plans, successful/replaced results, week-change clearing and recreation. State tests cover local midnight/year/week boundaries, fresh-model restoration and late-copy completion without feedback resurrection.
- Resumed review confirmed six uncommitted correction files (app shell, Week route/ViewModel, two test files and this document), with no correction commit yet created. Preserved the existing implementation/tests without reset, replacement or duplicated work; finished validation and documentation.
- Wrapper validation passed: :app:assembleDebug, :app:testDebugUnitTest (28 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest and :app:lintDebug (0 errors, 11 existing warnings). The five new WeekContextUiTest/WeekContextStateTest tests passed in a focused run and again in the complete regression run. The full emulator suite completed 52 tests: 51 passed and the unchanged shortcut Back-dismissal test was blocked by a System UI ANR overlay, confirmed by screenshot. After closing the emulator's unresponsive System UI dialog and confirming app-window focus, the unchanged PlanningShortcutsUiTest group passed 5/5 with 0 failures/errors/skipped. All 52 distinct emulator tests therefore passed across the full run and targeted rerun; no assertion was disabled or weakened and no existing test was edited.
- Remaining notices are the same redundant activity label, seven unused starter colors and three dependency update warnings. The emulator System UI instability was an environment issue; no unresolved app build/test failure remains. The temporary proportional emulator display override was restored and the task-created emulator stopped after validation. No physical-device acceptance was performed by Codex.
- Physical-device acceptance remains pending final Product Owner retest; neither Planning Actions nor the complete Week feature is marked fully approved.

## Select Monday When Navigating Weeks — 2026-10-21

- Physical-device acceptance confirmed current-week/today tab entry is correct and approved, but found that arrow navigation carried the selected weekday into the destination week. Final rule: every previous/next arrow navigation selects Monday of the destination week. This supersedes the earlier weekday-retention behavior documented in historical milestones.
- Added only the selected-day reset to WeekViewModel.moveWeek; its existing feedback clearing and week-date calculation remain. Tab entry/re-entry and active Week-tab reselection still use the separate resetToToday behavior and select today's actual local date. Returning to the current week by arrows selects Monday; returning by the bottom tab selects today.
- No persisted plan is moved, deleted or recreated by navigation. Planning Actions UI/business logic, CRUD, reusable records, historical integrity, database version 4, schemas, migrations and dependencies remain unchanged.
- Updated prior navigation-test expectations to the newly approved Monday rule without removing assertions. Added repeated forward/backward UI navigation and every-source-weekday state coverage, including year boundaries, feedback clearing, tab-versus-arrow behavior and exact persisted-plan preservation.
- Validation passed: :app:assembleDebug, :app:testDebugUnitTest (28 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest and :app:lintDebug (0 errors, 11 existing warnings). The full emulator run completed 54 tests: 53 passed, including all seven Week context/state tests and shortcut, Fasting, Meal, persistence/migration and screen regressions. One unchanged Activity UI launch test temporarily lacked Compose content during recovery from a confirmed emulator System UI ANR. The complete unchanged ActivityUiTest rerun passed 3/3, 0 failures/errors/skipped, on the warmed emulator. All 54 distinct emulator tests passed across those runs; no existing CRUD test or assertion was changed to obtain a pass.
- Existing warnings remain one redundant activity label, seven unused starter colors and three dependency update notices. No unresolved app build/test failure remains. Emulator System UI instability was handled as an environment issue, and the temporary display override was restored and task-created emulator stopped after validation.
- Week physical-device acceptance remains pending one final Product Owner retest; the complete Week/Planning Actions feature is not marked approved.

## Today Adherence / Change Meal — 2026-10-21

- Product Owner confirmed complete Week planning physical-device acceptance and approval/locking, including CRUD, saved-item selection, shortcuts, discoverability, feedback lifecycle and final tab/today versus arrow/Monday navigation. Earlier pending Week statements above are historical; Week implementation remains unchanged.
- Replaced the Today placeholder with current local date, Fasting, Meals and Workout / Activity charcoal cards, neutral Nothing planned states, text-only meal details and compact outcome controls. Status treatment combines symbols/text and color: Planned, Done, Changed and Missed. There are no planning editors, date browsing, automatic outcomes or generated plans in Today.
- TodayViewModel/TodayRoute observe the same repository/Room records as Week. Local-date/zone queries use the established half-open DateRange.day; cross-midnight and multi-day fasts appear once per applicable Today view and remain one continuous stored event. Explicit No fast is neutral, with no adherence failure or fabricated outcome.
- Today refreshes on entry/re-entry and resume, and receives system date/time/time-zone changes while active so an open screen updates across local midnight. Restored actual drafts belong only to their original current day. Repository Today operations revalidate date/zone and record membership within the transaction; stale editors or unrelated-date records cannot rewrite historical/future outcomes through Today.
- Fasting and Workout / Activity support Done/Missed and current-context corrections while retaining IDs, schedules, notes, type and historical linkage. A fast has one event-level outcome, including when spanning multiple days. No rescheduling or duplicate records are created.
- Meals support Done/Missed/Changed. Change Meal / Log Actual shows original planned text/label/time/notes read-only and edits only actual text (required), optional label, optional removable time and optional notes. Save stores actual details separately, marks Changed and returns reactively to Today. Changed can be reopened to correct actual details. Back/picker cancellation does not write; drafts survive recreation, save failures retain entries and busy state blocks duplicate submits/dismissal.
- Correcting Changed to Done/Missed changes the outcome only and retains the last actual snapshot in separate actual columns. Today displays actual details only for Changed, so retained actual data never masquerades as planned content or as the current Done/Missed result. Reopening Changed can reuse/correct the retained snapshot. This is correction of the current record, not a new audit-log/history table; planned data is never overwritten.
- Added AdherenceDay and guarded repository outcome operations. SQL updates target outcome/actual columns only; saved libraries and activity rescheduledFromId remain untouched. Existing foundation recordChangedMeal remains compatible. Database stays version 4; entities, exported schemas, migrations, dependencies and SDK configuration are unchanged.
- Added JVM actual-draft validation/serialization/precision tests, durable outcome/history/library and current-date repository tests, reactive ViewModel restoration/date/error tests, and Today UI coverage for statuses, Change Meal, actual corrections, neutral empty/No fast states and tab re-entry at midnight. App-shell placeholder assertion now checks the functional Today heading; locked Week/CRUD/shortcut tests retain their assertions.
- Today physical-device Product Owner acceptance remains pending; Today is not approved/locked. Progress, Day Detail, rescheduling, reminders, Backup & Restore, reusable Settings management, detailed logging, health measurements, nutrition, photography, recommendations and cloud functionality remain outside this milestone.

### Resumed Today validation — 2026-10-22

- Found and reviewed 18 existing uncommitted Today milestone files, with no Today commit created. Preserved the implementation and tests rather than resetting, replacing or duplicating them; completed final diff review, validation and documentation. The final actual-correction UI assertion explicitly waits for the reactive corrected actual text before checking it.
- Test integration remains test-only: AppShellTest checks the functional Today heading instead of its removed placeholder. WeekContextUiTest preloads TodayViewModel alongside WeekViewModel in its custom ViewModel store with the same test repository/clock. Existing navigation, feedback and CRUD assertions remain intact; production Week navigation semantics were not changed.
- Repository-wrapper validation passed: :app:assembleDebug, :app:testDebugUnitTest (31 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest and :app:lintDebug (0 errors, 11 existing warnings). The focused Today emulator group passed 10/10. The final complete :app:connectedDebugAndroidTest run on emulator-5554 passed all 64 tests in one run, with 0 failures/errors/skipped: Today data/ViewModel/UI, all locked Week CRUD and reusable-item workflows, shortcuts, navigation/feedback, persistence and migrations.
- Confirmed the earlier startup ANR belonged to emulator com.android.systemui; clearing its dialog restored input before the passing Today-specific run. No production workaround or weakened assertion was introduced. The final full regression run passed without an affected-test rerun. Temporary proportional display settings were restored to 1440x3120 / density 560 and the task-created emulator was stopped after validation.
- Remaining lint notices are one redundant activity label, seven unused starter colors and three dependency update notices. Removed only the obsolete Today placeholder string. No unresolved automated build/test failure remains. Room remains version 4 with unchanged entities, schemas and migrations; dependencies, SDK setup and locked Week production files are unchanged.
- Current focus remains Product Owner physical-device acceptance of Today adherence and Change Meal / Log Actual. Automated validation is complete; Today is not approved/locked.

## Progress Review / Day Detail — 2026-10-22

- Product Owner confirmed complete Today adherence and Change Meal / Log Actual physical-device acceptance and approval/locking. Complete Week and Today production workflows remain unchanged. Previous pending Today milestone notes are historical.
- Replaced Progress placeholder with branding/title, three top summary cards in Fasting / Meals / Activity order, monthly calendar, selected/current-date treatment, Success / Changed / Missed / Planned legend, and read-only Day Detail directly below. Existing near-black/charcoal/green/off-white Material 3 styling and bottom navigation are retained. No Screen 3 image was found in the repository or available attachment images; implemented the explicitly specified structure, with exact-reference comparison pending.
- Calendar uses Monday-first weekdays consistent with Week, real YearMonth lengths/offsets including leap years, unrestricted previous/next month navigation, and selected-date outline/today accent. Month arrows select the first date of the destination month. Entering/re-entering/reselecting Progress resets to the current device-local month/today. In-place browsing survives ordinary recomposition/recreation; resume/system date/time/zone refresh updates the current-day marker and viewing zone without discarding browsed context.
- Every date has three separated F / M / A letter-and-status-symbol positions, not one daily color. Neutral is —; Planned is ○; Success is ✓; Changed is ◐; Missed is ✕. Calendar cells expose full category status/fraction and current-date semantics. Narrow/enlarged displays can scroll the calendar horizontally rather than compressing the indicators below the minimum calendar width.
- Deterministic per-day aggregation: Fasting and Activity use any Missed → Missed, otherwise all Done → Success, otherwise Planned; no events → neutral. Meals use any Missed → Missed, otherwise any Changed → Changed, otherwise all Done → Success, otherwise Planned; no meals → neutral. Meal fraction is Done / all planned meals for that date: Changed remains distinct and is not counted as Done. Explicit No fast is intentional and neutral, shown in Day Detail and explained below the legend; it is excluded from fasting success/failure denominators. If fasting events and No fast coexist, events determine the fasting indicator and both remain visible in detail; no conflict is silently resolved.
- Monthly summaries round Done / resolved × 100 to the nearest whole percent. Fasting resolved = Done + Missed events overlapping the displayed month, counted once per event even if it spans many dates or begins in an earlier month. Meals resolved = Done + Changed + Missed dated in the month; Changed is resolved but not full Success. Activity resolved = Done + Missed dated in the month. All Planned records are excluded, including future Planned records; nothing planned and No fast do not lower adherence. No resolved records displays —, while genuinely resolved all-unsuccessful records show 0%. Numerator/denominator are shown beneath each percentage.
- Continuous fasts retain one stored event and full original-zone endpoints. Half-open local-day overlap displays them on every applicable date, excluding a midnight end from the next day. Restrained ← F → continuation markers identify continuation across dates, including calendar rows/month boundaries, without creating daily records. Selected Day Detail retains the exact applicable event identity and schedule.
- Day Detail displays every selected-day fasting schedule/preset/notes/outcome, explicit No fast, planned meal text/time/label/notes/outcome, Changed actual text/time/label/notes separately, and every Workout / Activity name/type/time/notes/outcome. It has no adherence editors, writes, rescheduling or competing Today workflow. Retained actual snapshots for non-Changed corrected meals are not presented as the current actual outcome. No daily-note table exists; editable daily notes are deferred without a schema change.
- Added pure domain ProgressReview aggregation/read models and ProgressViewModel/ProgressRoute/ProgressScreen. Uses existing PlanningRepository.observe with one displayed-month range and fasting overlap, then aggregates in memory. Selecting a date reuses the snapshot with no per-cell/per-date database query. Lifecycle-aware state distinguishes loading/read failure/neutral empty success and supports retry. Compose never accesses DAOs. No repository write/API, entity, schema, migration, dependency or SDK change; Room remains version 4.
- Tests added for calendar/leap/weekday/month/year/DST boundaries, status precedence, fractions, resolved summaries, Changed separation, neutral No fast, overlapping single-event fasts, reactive monthly observation, query count on selection, restoration/reset and read-failure recovery; Compose coverage includes summaries, F/M/A, legend, continuation, read-only detail, month/date navigation and tab reset. Existing shell assertion now checks the functional Progress heading; custom Week navigation fixtures preload ProgressViewModel without changing their assertions.
- Progress physical-device Product Owner acceptance remains pending. Progress is not approved/locked. Daily notes, Progress outcome editing, rescheduling, reminders, Backup & Restore, reusable Settings management, detailed logging, health measurements, nutrition, photos, recommendations and cloud functionality remain outside this milestone.
- Validation passed using the repository wrapper: :app:assembleDebug, :app:testDebugUnitTest (38 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest, :app:lintDebug (0 errors, 11 existing warnings), and the final complete :app:connectedDebugAndroidTest run targeting emulator-5554 (70 tests, 0 failures/errors/skipped in one run). All 6 new Progress state/UI tests, all locked Week/Today workflows, shortcuts, saved-item workflows, persistence and migrations passed. Seven new pure aggregation/calendar tests are included in the JVM total.
- During UI validation, captured bounds/screenshot exposed weighted calendar cells collapsing to zero width under an unbounded horizontal scroll container. Gave the calendar a finite viewport-based width with a 336dp minimum and used clickable Material 3 date surfaces. The normal-click November 30 selection regression now also asserts a usable cell width; original selection/status/data assertions remain. New Progress UI waits allow 30 seconds for asynchronous state/frames on the slow emulator; a prior 10-second tab-reset timeout already had the expected state when captured. The final full suite passed without reruns.
- Cold boot encountered the previously observed emulator com.android.systemui ANR, confirmed through window diagnostics and a screenshot over the running app. Cleared its Close app dialog before validation; no production workaround or assertion removal was introduced. Existing lint warnings remain one redundant activity label, seven unused starter colors and three dependency update notices. No unresolved automated build/test failure remains.
- Visually checked the final Progress screen in the normal app shell: dark styling, top cards, readable seven-column F/M/A calendar, selected-today outline and bottom navigation. Test screenshots/logs stay in ignored build output. Restored emulator display to its original 1440x3120 / density 560 and stopped the task-created emulator after validation; the physical phone was not used for automated testing. Exact-reference comparison and Product Owner physical-device acceptance remain pending.

## Progress Screen 3 Presentation Correction — 2026-10-22

- Product Owner physical-device review confirmed functionality was not the problem, but found substantial visual/structural divergence from the approved Screen 3. Compared current-device Images 1/2 directly with target Image 3 before editing. Retained an exact, SHA-256-matching copy of Image 3 at docs/design/progress-approved-screen-3.png for future implementation/review. Earlier missing-reference notes describe the prior milestone only.
- Restored compact brdcrmbs / PROGRESS / month header with circular previous/next controls, three outlined charcoal scorecards with native determinate circular rings, prominent dynamic values and compact Done / resolved counts. No resolved records uses an unfilled neutral track and —; no mock percentages or production sample data are introduced. Progress-local mint green/amber/red styling follows the supplied reference without changing shared Week/Today theme or bottom navigation.
- Calendar now has compact rounded date tiles with a 48dp minimum height, smaller gaps/padding, compact selected-date outline and three 7dp marks in fixed left Fasting / middle Meals / right Activity order. Removed repeated literal F/M/A labels and dash rows. Success is green with a check treatment, Changed amber half-filled, Missed red with a cross, Planned light outline and empty/No fast a subdued neutral mark. Full category/status content descriptions remain. Multi-day fast continuity uses a restrained line instead of adding a text row; existing overlap/history logic is unchanged.
- Calendar weekday presentation now starts Sunday to match Image 3; this is a UI-only offset conversion from the existing read model, with all dates retained correctly. Week remains Monday-first. Progress month/date selection, browsing/reset semantics and shared calendar/aggregation functions are unchanged. Adjacent-month padding tiles are subdued and non-interactive; they do not claim queried outcomes outside the displayed month.
- One compact status legend follows the calendar, plus a single small Fasting · Meals · Activity position hint. Removed the prominent neutral-state explanatory sentence and multiple large legend rows.
- Day Detail is one rounded card with DAY DETAIL, compact selected-date header and a calculated daily adherence percentage. Three compact icon/title/subtitle/status rows summarize Fasting, Meals and Activity. Changed meals explicitly distinguish Planned from Actual in the summary. Review all details expands within this same card to show every record and its full schedule/zone, optional time/label/notes, raw outcome, and separate actual-meal metadata. No historical detail is discarded and no editor/write operation is added. Common overnight fasting summaries use times and +1 day, with exact endpoints still available in full detail; longer/earlier-started events show dates in the compact row.
- Added only a pure selected-day AdherenceSummary projection: total Done fasting events/meals/activities divided by total resolved applicable events/meals/activities, rounded by the existing summary rule. Planned and No fast are excluded; Changed is resolved but not successful; no resolved items displays —. One continuous fast counts once for that selected date. Existing monthly formulas, category precedence, fractions, repository reads/writes, state/navigation, Room version 4, schemas, migrations, dependencies and SDK setup are unchanged. Locked Week and Today production files remain untouched.
- Existing Progress domain tests are preserved; added daily-score coverage for 2/3→67%, Changed/non-success, Planned/No fast exclusion, neutral and resolved-zero states, and a single multi-day event. Updated UI checks retain navigation/reset/history assertions and now cover rings, real/neutral values, absence of F/M/A text, all compact status states, 48dp date height, Sunday-first presentation, one detail card, expansion, actual metadata and daily values. Added populated-screen visibility/capture coverage; an initial failure led to tighter spacing rather than removing the visibility assertion.
- Progress physical-device Product Owner acceptance remains pending; Progress is not approved/locked. No unrelated feature was added.
- Resumed final review found six modified milestone files plus the approved reference image, with no visual-correction commit yet created. Preserved all interrupted implementation/tests and reviewed the complete diff; remaining work was validation, final visual review, documentation and Git publication.
- Compared the unobstructed final Sunday-first emulator capture directly with Image 3: compact header, circular scorecards, dense date tiles, fixed-position status marks, selected-date outline, one primary legend, consolidated detail rows and right-aligned daily percentage are present. Populated-screen coverage confirms the three summary cards, full calendar, all three detail rows and details control are visible together without scrolling on the configured Pixel emulator. Capture data is test-only persisted fixtures, not hardcoded production content.
- Remaining visual differences from Image 3: native Android fonts/icons/system safe areas and the existing bottom navigation are preserved; date tiles retain 48dp minimum touch height; surfaces use solid native charcoal/teal fills rather than the illustration's lighting/gradients; a small category-position hint and Review all details disclosure provide access to complete real histories. Longer records, six-row months, small displays or larger fonts may require scrolling. This is a close native Compose reproduction, not a claim of pixel-perfect or physical-device acceptance.
- Final wrapper checks passed: :app:assembleDebug, :app:testDebugUnitTest (39 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest and :app:lintDebug (0 errors, 11 existing warnings). The focused Progress state/UI group passed 8/8. The final full 72-test emulator regression run passed 71/72, including all 8 Progress tests, Today, navigation, shortcuts, saved-item workflows and migrations; the unchanged ActivityUiTest.bakalGymWorkoutAndCustomActivityAppearAsSeparateRows timed out in its 10-second post-Save wait. The subsequent unchanged ActivityUiTest + final ProgressUiTest rerun passed 8/8 (Activity 3/3, Progress UI 5/5), with 0 failures/errors/skipped. All 72 distinct emulator tests passed across the full run and targeted rerun; the complete run is not represented as a single 72/72 pass.
- The Activity failure was investigated at ActivityUiTest.save line 43 / caller line 88. Activity editor, Week model/route, repository, DAO and Activity tests have no changes in this milestone; the unchanged group passed on the warmed emulator with no workaround or assertion change. This is consistent with transient emulator/input timing; no production regression was demonstrated. Separately, startup diagnostics and a captured overlay confirmed the recurring com.android.systemui ANR; its Close app dialog was dismissed before the unobstructed visual comparison and final rerun. No app code was changed to accommodate System UI.
- Existing lint notices remain one redundant activity label, seven unused starter colors and three dependency update notices. No unresolved app build/test failure remains. Restored the temporary proportional emulator display override to original 1440x3120 / density 560 and stopped the task-created emulator. Physical phone was not used for automated testing. Progress physical-device Product Owner acceptance remains pending and Progress is not approved/locked.

## Debug Progress QA Seeder — 2026-10-22

- Added development-only Settings → DEVELOPER / TESTING → Progress Test Data, with Seed Test Week / Reset Test Data confirmations, busy protection, transactional failure feedback and short-lived snackbars. Nothing seeds automatically. The confirmed week is revalidated before writes, and current week labels refresh on resume/action.
- Implementation and controls live in src/debug; src/release supplies an empty Settings extension. Main sources only expose the existing shared Room instance internally and call the variant Settings extension. No BuildConfig conditionals, new dependencies, product Settings behavior or changes to locked Week/Today workflows.
- Scenario uses the device's current local Monday–Sunday. Exactly 21 owned rows: 5 continuous fasting events, 1 explicit NoFastPlan, 9 meals and 6 activities. Monday: Done 16:8, three Done meals and Done Upper Body workout. Tuesday: explicit No fast, Done Oatmeal, Changed Pork + cabbage with separate actual Chicken + rice / Dinner / 18:30 / requested notes, and Done walk. Wednesday: Done 18:6, two Done meals plus Missed dinner, Missed Lower Body workout. Thursday: Planned 16:8, shake and mobility. Friday: Missed 24h, Done walk, no meals. Saturday: one Planned 48h and Done Full Body workout; Sunday has no new rows, meals or activities.
- Saturday's single fast overlaps Sunday and following Monday. Presets retain real elapsed duration, including across DST; its Monday end can be 18:00/20:00 rather than 19:00 on clock-change weeks. Existing overlap/aggregation is deliberately unchanged: Monday's Done fast also determines Tuesday's fasting indicator despite the separate neutral No fast record; Friday's Missed fast overlaps Saturday and takes precedence over Saturday's new Planned fast. QA must assess these existing rules rather than expect fabricated daily outcomes.
- Ownership is the exact reserved ID grammar brdcrmbs-debug-progress-v1|<ISO Monday>|<one of 21 canonical slots>. Ordinary UUIDs, prefix lookalikes, unknown slots and invalid dates are not owned. Seed atomically replaces only this seeder's target-week rows, restoring canonical outcomes on repeated use. Reset atomically removes owned rows across all seeded weeks; it never clears a database/date range or touches Saved Meals/Activities. Genuine plans/history remain, including same-week records; copied QA plans with fresh normal IDs remain genuine independent records. A genuine historical activity link to a QA row causes a safe foreign-key rollback rather than deleting linked history.
- Uses production-compatible domain records and existing repository insert APIs within a Room transaction. Debug-only exact-ID deletion permits replacement/reset of owned resolved records without relaxing production historical CRUD guards. Changed planned/actual snapshots remain separate. Existing repository observation drives Week, Today and Progress; no fake UI state or debug aggregation branches.
- Room remains version 4, with all entities, schemas and migrations unchanged. This is QA tooling only. Progress physical-device Product Owner acceptance remains pending; Progress is not approved/locked.
- Final wrapper validation passed: :app:assembleDebug, :app:assembleRelease (unsigned), :app:testDebugUnitTest (44 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest, :app:lintDebug and :app:lintRelease (each 0 errors, 11 existing warnings). No dependency/SDK upgrade or schema change. APK DEX inspection confirmed seeder/scenario/ownership/ViewModel symbols, all developer control labels and the reserved ID prefix present in debug and absent from release; release requires only its empty Settings extension and has no QA generation UI path.
- Focused QA emulator validation passed 9/9, including the final rerun after applying the existing charcoal surface to the developer card. Coverage includes canonical storage, Changed snapshots, 48h overlap, database reopen, idempotent canonical restoration, preserved real history and saved libraries, exact ownership, other weeks, transactional rollback, foreign-key protection, repository and all three ViewModels reacting to seed/reset, and UI confirmation/cancel/feedback. Five new JVM tests cover scenario, ownership, current-local-week/year/zone and DST behavior, plus unchanged real aggregation rules.
- Full emulator regression finished 80/81 passed, 0 errors/skipped. The sole failure was the unchanged ActivityUiTest.bakalGymWorkoutAndCustomActivityAppearAsSeparateRows 10-second post-Save wait (helper line 43, caller line 88), matching the prior milestone's timing failure. The unchanged complete ActivityUiTest group rerun passed 3/3. All 81 distinct Android tests passed across the full run and focused rerun; this is not a claim of a single 81/81 full-run pass. Existing test assertions and locked production behavior were not weakened or modified.
- Manual emulator verification used the actual Settings entry point: confirmed Seed produced persisted Progress summaries Fasting 67% (2/3), Meals 75% (6/8), Activity 80% (4/5) for the October test week; confirmed Reset returned Progress to neutral 0/0 summaries and Nothing planned detail. Automated ViewModel coverage separately verifies Week and Today updates. No physical phone was used for automated testing or seeded by Codex.
- Environment: the recurring cold-boot emulator com.android.systemui ANR was confirmed and dismissed before tests. Slow combined lint/build validation was cancelled and rerun with fewer workers; final checks passed. Remaining lint notices are one redundant activity label, seven unused starter colors and three dependency update notices; the existing native-library stripping notice is non-blocking. Progress physical-device Product Owner acceptance remains pending and Progress is not approved/locked.
- Final visual review confirmed the debug section uses the existing charcoal/green theme. Restored emulator display to 1440x3120 / density 560 and stopped the task-created emulator; temporary captures, test logs and APK isolation evidence remain in ignored build output.

## Settings Foundation / Saved Libraries — 2026-10-22

- Product Owner tested the corrected, populated Progress screen on a physical Android device using the QA dataset and said "looks good". Summary rings/resolved scores, mixed category indicators and fractions, neutral/Planned states, Day Detail/daily percentage, expanded history and overlapping fasting are accepted. Progress presentation and existing business rules are now approved/locked; earlier pending Progress notes are historical.
- Replaced the Settings placeholder with compact charcoal/green grouped rows: Planning (Saved Meals / Saved Activities), Reminders, Data (Backup & Restore / Data Management), App (App Preferences / About). Future workflows are intentionally inactive and labelled Coming in a later milestone; none is represented as implemented.
- Saved Meals and Saved Activities have reactive library lists, useful empty states, optional direct Add, focused edit screens and confirmed Delete. Meals store name, optional label and notes; activities store name, Workout/Activity type and optional notes. No scheduling date/time, adherence state, photos, Bakal Gym integration or automatic plan creation. Bakal Gym remains ordinary user-entered text.
- Saved templates and scheduled plans are independent snapshots. Repository create/update/delete APIs target only saved_meals or saved_activities; existing planning/adherence mutation paths are unchanged. Updates retain template identity; missing-row updates fail instead of recreating deleted items, and inserts reject duplicate IDs. Template changes/deletion cannot rewrite scheduled plans, Changed actual meals, outcomes or activity historical links.
- Compose → SettingsViewModel / SavedStateHandle → PlanningRepository → Room. Added observation and minimal CRUD APIs for the existing reusable tables. Lists react to library changes; loading/read errors are distinct from empty success and support retry. Drafts survive recreation, Back discards unsaved edits, save failures retain entries, and busy state prevents duplicate submits or navigation during writes.
- Saved library lists/editors are focused Settings child screens with bottom navigation hidden, consistent with Week editors. Back returns editor → library → Settings main; main preserves Today / Week / Progress / Settings order. No Week/Today/Progress navigation/reset rule was changed. The shell regression now asserts Saved Meals instead of the removed placeholder text, retaining its existing tab/restoration assertions.
- Preserved the debug-only Progress QA section at the end of Settings main through the existing variant extension. Debug implementation and empty release counterpart are unchanged; no runtime debug checks or production seeding path added. Room remains version 4; entities, schemas, migrations, SDK/dependencies and locked feature screens/editors/calculations are unchanged.
- Settings is not complete: Reminders, Backup & Restore, App Preferences, Data Management and About remain future milestones. Saved Meals / Saved Activities management physical-device Product Owner acceptance remains pending.
- Final resumed validation completed 2026-10-23: wrapper :app:assembleDebug, :app:assembleRelease (unsigned), :app:testDebugUnitTest (47 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest, :app:lintDebug and :app:lintRelease passed. Each lint variant reports 0 errors and 11 existing warnings (one redundant activity label, seven unused starter colors, three dependency update notices). No dependency upgrade was made.
- Final full emulator regression passed 91/91 with 0 failures/errors/skipped on Pixel_6_Pro, API 37. This includes all 10 new saved-library/Settings/debug Settings tests, all 9 existing QA seeder tests, migration/storage/history coverage, Planning Actions and locked Week/Today/Progress regressions. Integrity tests schedule actual template snapshots, edit/delete templates, reopen Room and compare full plans/history unchanged, including original Changed meal plus actual snapshot, Missed/rescheduled activity links and continuous multi-day fasting. Direct template creation creates no plans.
- Interrupted validation was inspected rather than assumed successful: its full run finished 88/91, with three test-fixture failures when custom ViewModelStoreOwners entered Settings without supplying SettingsViewModel/SavedStateHandle. Added that ViewModel to the existing ProgressUiTest and WeekContextUiTest fixtures, matching their other tab fixtures; assertions and locked production behavior are unchanged. An earlier new SavedLibraryTest had a non-void inferred JUnit return; corrected only its final return to Unit, then its complete group passed 5/5. All 19 distinct focused Settings/library/QA tests passed across focused runs before the final clean 91/91 full run.
- Emulator cold boot had the recurring com.android.systemui ANR; its system dialog was dismissed before validation. No test assertions or production code were changed for emulator timing. The final complete run had no failures, including the historically slow Activity UI test. Final APK DEX checks confirmed QA seeder/scenario/ownership/model classes, developer labels, seed/reset labels and reserved ID prefix present in debug and absent in release; source-set implementation remains unchanged.
- Manual emulator visual review through the real app verified all Settings groups, both useful empty states, compact populated library cards, meal/activity editors and type controls, clear confirmed-delete safeguards, editor → library → main Back behavior, hidden child bottom navigation and restored main navigation. Scrolled to fully visible Seed/Reset controls; no oversized or clipped controls were found. Temporary review templates were deleted. No physical phone was targeted or seeded. Captures/reports remain in ignored build output; emulator display was restored to 1440x3120 / density 560 and the task-created emulator stopped. Saved-library Product Owner physical-device acceptance remains pending; this milestone does not approve all Settings.

## Plan Reminders — 2026-10-23

- Product Owner physically tested Settings main, Saved Meal/Activity create/edit/confirmed-delete, editor → library → Settings Back navigation and Developer / Testing, and said "everything looks good." Settings foundation and both saved-library management workflows are now physically accepted/locked. Overall Settings remains incomplete; older pending statements above are historical.
- Settings → Reminders provides independent Fasting start, Fasting end, Meals and Workout / Activity switches, all OFF by default. Lightweight checked SharedPreferences writes persist the four choices through restart; no DataStore dependency already existed and none was added. UI uses RemindersViewModel/store rather than owning durable writes. Enabling a category never creates plans or changes adherence/history.
- API 33+ POST_NOTIFICATIONS is requested only following an explicit first enable action and explanation. Cancel/denial leaves that attempted category OFF; the request state avoids repeated automatic prompts. An explicit notification action supports retry or system notification settings as appropriate, including the older-API app-details fallback. Existing ON preferences are retained if system permission/channel access is later blocked, with clear blocked status; reconciliation cancels pending alarms and delivery checks access again. No prompt on launch.
- Uses native one-shot AlarmManager RTC_WAKEUP / setAndAllowWhileIdle with immutable explicit PendingIntents. Triggers are requested at the stored plan time without offsets; Android may delay them under batching/idle/battery restrictions. This follows the [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms), avoids exact-alarm special access and adds no scheduling dependency or continuous service. Already-scheduled due alarms may deliver up to one hour late; older alarms are discarded. This limit and inexact timing are disclosed on the screen. Neither exact delivery nor delivery while Android force-stops/restricts the app is promised; opening the app reconciles again.
- Repository exposes a read-only snapshot/observation of unresolved fasting events and timed meals/activities using existing tables. No scheduling horizon or daily-fast duplication is introduced. Fasting start/end use persisted instants and independent switches: a 48h/72h event yields at most one start and one end. NoFastPlan never participates. Meals/activities require explicit times. Done/Missed/Changed outcomes remove future reminders without changing raw outcomes or planned/actual snapshots.
- Reconciliation observes committed planning and preference changes, so create/edit/delete and all three Copy/Repeat workflows naturally update alarms without changing existing mutation/copy semantics. Stable identities combine reminder type with the full plan ID in the PendingIntent data URI; changed schedules replace the same identity, removed/ineligible categories cancel it. Receiver rereads persisted plans, preferences and permission before posting; stale/replaced/deleted/resolved requests are suppressed. A device-local journal records pending requests and consumed trigger instants for idempotency across recreation/repeated reconciliation; notification tags use full keys, avoiding hash/list-position collisions. Runtime alarm state is excluded from Android backup/transfer and reconstructed from plans/preferences.
- Startup observation plus a narrow JobScheduler recovery job rebuild future schedules. Manifest receivers queue that job after unlocked boot, package replacement, wall-clock changes and time-zone changes; no long work runs in those receivers. Delivery uses bounded asynchronous receiver work. Recovery reinstalls future eligible alarms only and cancels past ones, never firing a backlog. Process death does not remove AlarmManager PendingIntents. An injected clock and serialized coordinator support deterministic tests and prevent concurrent reconciliation/delivery races.
- Fasting never reinterprets its zone/instants. Meal/activity date + explicit local time uses the current device planning zone, consistent with the rest of the app's local-day semantics. DST gaps are skipped instead of silently shifting a plan; repeated times use the earlier valid offset, matching new fasting input. System zone/time broadcasts and foreground refresh reconcile these projections; plans themselves are never rewritten.
- One neutral Plan reminders channel uses a monochrome icon and private lock-screen visibility. Notifications name only the user-created plan, without coaching/guilt/recommendations. Tap opens a fresh MainActivity to Today using a direct immutable activity PendingIntent; no general deep-link infrastructure was added. Debug-only Reminder diagnostics reports pending count/types/times and provides Reconcile reminders now; canonical QA seed/reset content and ownership are unchanged. Release has no diagnostics or QA seeder UI.
- Room remains version 4; no entity, table, schema JSON, migration, destructive fallback, SDK or dependency change. Locked Week/Today/Progress, planning CRUD, Saved Meal/Activity editing, Change Meal and copy logic remain unchanged. Backup & Restore, App Preferences, Data Management and About remain deferred. Reminders physical-device Product Owner acceptance remains pending.
- Native registration uses a rolling queue of the nearest 128 eligible requests to stay below Android's per-app alarm limit. All other future plans remain in Room and fill the queue after delivery/reconciliation; there is no calendar horizon or per-day duplicate event. Pending diagnostics counts native registrations. Device permission-request history is stored separately and excluded from backup/transfer alongside the runtime journal; the four category choices remain ordinary durable preferences.
- Resume review preserved the existing 25-file implementation; remaining work was final permission/native-notification coverage, complete validation, emulator visual review, documentation and Git publication. No locked workflow or scheduling architecture was replaced. Existing Settings regression now expects four deferred rows because Reminders is active; its library/navigation assertions remain intact.
- Final wrapper validation passed :app:assembleDebug, :app:assembleRelease (unsigned), :app:testDebugUnitTest (59 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest, :app:lintDebug and :app:lintRelease. Both lint reports have 0 errors and 11 existing warnings: one redundant activity label, seven unused starter colors and three dependency-update notices. Intermediate new API/style notices and a SARIF quick-fix reporting issue cleared on the final-source rerun; no SDK/dependency upgrade was made.
- Final full emulator regression passed 100/100, 0 failures/errors/skipped, on Pixel_6_Pro API 37. Includes all nine new Android reminder tests, all existing Week/Today/Progress/Settings/saved-library/history/migration/QA tests. Twelve new JVM tests cover default OFF/category independence, timing eligibility, editing/deletion/outcomes, No fast, one continuous 48h fast, repeated recovery, permission loss, bounded/idempotent delivery, stale/late/backlog suppression, DST gaps/overlaps, zone/year boundaries and rolling-queue refill through all 131 fixture requests.
- Focused reminder validation passed 8/8 before the final grant/routing additions; the complete final permission/UI group passed 4/4. These overlapping runs cover all nine distinct new Android tests; all nine also passed in the final full run. Permission tests exercise the real system Deny and Allow dialogs, OFF-on-denial, an explicit later attempt routing to system notification settings, preserved choices, enabling only the requested category after grant and switching it OFF again. Native coverage checks actual AlarmManager PendingIntent replacement/cancellation and journal reopening, a real posted notification's text and its actual activity token opening Today. Temporary test-only permission identity/app-op changes are restored; test flags were reset only on the emulator between validation runs.
- Early new native-test attempts failed because posting and Activity launch are asynchronous and because retained notification permission/app-op state interfered with repeated native/permission tests. The test fixtures now await real publication/startup, isolate and restore app-op state, and use the real posted activity token rather than depend on notification-shade animations. A test-only reference to a non-public SDK constant was corrected. No existing assertion was weakened, and no locked production behavior changed to obtain a pass. Final focused and full runs passed. The recurring cold-boot com.android.systemui ANR was confirmed and dismissed before testing; no app workaround was added.
- Final APK DEX inspection verified 11 QA/diagnostics markers (seeder/scenario/ownership/model classes, developer and seed/reset labels, reserved ID prefix and reminder diagnostics symbols/labels) present in debug and absent from release. Canonical Progress QA scenario and release empty Settings extension are unchanged. Production reminder scheduling remains included in release as intended.
- Manual review through Settings → Reminders confirmed charcoal/green rounded surfaces, four clear OFF toggles, blocked-permission explanation/action, fully scrollable timing/DST text, usable controls without clipping, hardware Back returning to Settings and restored four-item bottom navigation. Developer / Testing seed/reset remains intact; the separate reminder diagnostics card and Reconcile control are reachable, with pending count 0 when all categories are OFF. No physical phone was targeted or seeded. Physical-device Reminders acceptance remains pending; Backup & Restore, App Preferences, Data Management and About remain deferred.
- Final review found only the 25 scoped milestone files and no whitespace errors. Restored the task-created emulator to its original 1440x3120 / density 560 and stopped it. Test reports, APKs and review captures remain in ignored build output; no local/generated files are included in the checkpoint.

## Today Active Fasting Progress — 2026-10-23

- Product Owner physically tested Reminders and said "looks good." Reminders are physically accepted/LOCKED. Earlier pending Reminders statements are historical; no reminder implementation or scheduling behavior changes in this milestone.
- Added a compact horizontal bright-green progress bar, numeric percentage, concise remaining time and explicit end label inside the existing Today fasting card. Existing preset, schedule, duration/time-zone metadata, notes, adherence status and Done/Missed controls remain present. Meals, Workout / Activity and bottom navigation are unchanged.
- A small pure presentation helper derives UPCOMING / ACTIVE / ENDED, clamped fraction/percentage and elapsed/remaining durations from the existing persisted start/end Instants. It uses actual elapsed time for cross-midnight, 24h/36h/48h/72h/custom multi-day events and DST, never calendar-day fractions or independent daily fasting records. Whole percentages round down so an active event does not display 100% early.
- Only an unresolved Planned event with start <= now < end gets the live treatment. If multiple unresolved events overlap, the latest start wins, with stable ID as a deterministic tie-breaker; all existing visible records remain. Upcoming records remain simple. Ended unresolved records show neutral "Planned time complete · 100%" without an active bar/countdown. Time completion never writes an adherence outcome; Done/Missed stay user-controlled and retain their existing presentation. Done, Missed and explicit No fast have no live bar.
- Remaining time uses compact day/hour/minute units (for example 5h 55m, 45m, 1d 6h, 2d 3h), with <1m for the final partial minute and no displayed seconds. End labels use the event's persisted zone: today, tomorrow or a short date, including year when needed. Existing full schedule/zone metadata remains visible.
- Reuses Today's injectable java.time.Clock. A minute-level cold flow is lifecycle-collected only on Today outside the Actual Meal editor, stops immediately when no longer collected, and rereads the clock on subscription/re-entry, foreground resume and existing date/time/zone refresh. No high-frequency timer, alarm, service, background task, persisted percentage or new dependency. The horizontal indicator has native determinate progress semantics and an accessible label, plus readable percentage/remaining/end text.
- Room remains version 4. No entity, schema JSON, migration, repository, stored plan or history change. Locked Week, Today adherence/Change Meal, Progress, Settings/libraries, Reminders, CRUD and planning shortcuts are preserved. Canonical debug QA data and release/debug isolation are unchanged.
- Final wrapper validation passed :app:assembleDebug, :app:assembleRelease (unsigned), :app:testDebugUnitTest (66 tests, 0 failures/errors/skipped), :app:assembleDebugAndroidTest, :app:lintDebug and :app:lintRelease. Both lint reports contain 0 errors and the same 11 existing warnings: one redundant activity label, seven unused starter colors and three dependency-update notices. No SDK/dependency upgrade or schema change.
- Seven new JVM tests cover state boundaries/clamping, elapsed percentages and near-end handling, cross-midnight/all requested durations including 36h and custom multi-day, both DST transitions, concise remaining duration, end labels across dates/years and deterministic single-active emphasis. Eight new Android tests cover live injected-clock updates, exact start/end, unchanged stored plans/outcomes at time completion, working Done/Missed controls, No fast/resolved/future states, continuous 72h fasting, overlapping visible plans, tab re-entry, explicit clock refresh, lifecycle STOP/resume/destruction and cancellation of hidden clock collection. Existing tests/assertions were not weakened or changed.
- Focused Today validation passed 18/18, 0 failures/errors/skipped (eight new UI tests plus all ten existing Today UI/adherence tests). Final-source full emulator regression finished 106/108 passed, 0 errors/skipped, on Pixel_6_Pro API 37. The two failures were unchanged 10-second waits: MealUiTest.save (line 43, caller line 64) after editing a meal, and ProgressQaToolsUiTest.confirmedSeedAndResetOperateOnRealRoomData (line 52) awaiting the final removed-data text. Both fixtures render isolated unchanged Week/QA UI without Today or its ticker. Inspection found no current ANR during those timeouts. The complete unchanged MealUiTest and ProgressQaToolsUiTest groups reran successfully 4/4, 0 failures/errors/skipped. All 108 distinct Android tests passed across the full run and affected-group rerun; this is not a claim of a single 108/108 full-run pass. All new progress tests and locked reminder, Week, Today adherence, Progress, Settings/library, storage/history/migration and other regressions passed, accounting for the two successful reruns.
- Emulator visual review used real Room test fixtures and an injected clock for active 16:8, upcoming, ended-unresolved, Missed/Done/No fast and active 72h scenarios. Confirmed readable percentage/remaining/end text, compact horizontal treatment, preserved schedule/zone/notes and obvious outcome controls, understandable multiple rows, working scrolling and unchanged bottom navigation. Final 72h capture shows 29%, 2d 3h remaining and Ends Oct 25 at 10:00 AM. A first multi-day capture caught a blank compositor transition; added only a test capture idle/render wait and verified the final image. A test-only capture helper reference to an unavailable UI Automator library was removed before final validation; no dependency was added.
- Environment: recurring cold-boot com.android.systemui ANR was inspected and dismissed before focused testing. Full-run timeout evidence and successful unchanged reruns were preserved separately in ignored build output. No production workaround was introduced. Restored the task-created emulator to 1440x3120 / density 560 and stopped it after tests/review. No physical phone was targeted or seeded; canonical QA seeder data is unchanged. Final diff has seven scoped files and no whitespace errors; generated reports/APKs/captures remain ignored.
- Physical-device Product Owner acceptance of the new Today fasting progress presentation remains pending; this enhancement is not locked. Existing Today adherence and all previously accepted workflows remain locked.
