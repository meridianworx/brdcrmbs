# PROJECT_STATE.md — brdcrmbs

> This file is the current source of truth for the brdcrmbs app.
> Update it throughout development as decisions are approved, features are completed, issues are discovered/resolved, and milestones change.

# Project Identity

- **App name:** brdcrmbs
- **Package name:** com.ventures814.brdcrmbs (namespace and application ID)
- **Platform:** Android
- **Repository:** https://github.com/meridianworx/brdcrmbs.git (origin; primary branch main)
- **Development stage:** Week UI, Fasting CRUD, Meal CRUD, and Saved Meal selection are physically accepted and approved/locked. Workout / Activity CRUD and Saved Activity reuse implemented; activity physical-device acceptance pending. Complete Week workflows remain unfinished.

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
- Four-destination app shell; TodayScreen, ProgressScreen, and SettingsScreen remain placeholders. The approved My Week layout now loads repository-backed planning data.
- Approved dark charcoal / near-black theme, bright green accent, high-contrast typography, rounded cards, and simple line icons.
- Week persistence/read-side integration and fasting Add/Edit/Delete implemented; fasting CRUD passed physical-device Product Owner acceptance and is approved/locked.
- Meal CRUD and Saved Meal selection/persistence passed physical-device acceptance and are approved/locked. Workout / Activity CRUD and Saved Activity selection/persistence are implemented; adherence UI, Progress calendar, and Settings behavior remain unimplemented.

## In Progress

- Workout / Activity CRUD physical-device acceptance pending. This workflow is not approved/locked; the complete Week feature remains unfinished.

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
- Fasting Add/Edit/Delete passed physical-device Product Owner acceptance and is approved/locked, as confirmed in the Meal workflow request on 2026-10-20.

- Meal CRUD and Saved Meal selection passed physical-device Product Owner acceptance and are approved/locked, as confirmed in the activity workflow request.
- Saved Meal deletion/management belongs in Settings → Saved Meals; Saved Activity deletion/management belongs in Settings → Saved Activities. Week editors only plan/select reusable items; management is deferred.

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

Product Owner physical-device acceptance of Week Workout / Activity CRUD and Saved Activity reuse. Preserve locked Week, Fasting, and Meal behavior. Change Meal / Log Actual, Copy Day, Copy to Selected Days, Repeat Last Week, rescheduling, Today adherence, Progress, reminders, Backup & Restore, and Settings Saved Meals / Saved Activities management remain unimplemented. The complete Week feature is not finished.

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
