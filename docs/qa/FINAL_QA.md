# Final Cleanup & QA / V1.0 Complete — 2026-10-27–30

Status: FINAL QA PASSED — BRDCRMBS V1.0 COMPLETE. All planned V1 features are accepted. This personal app continues through the existing Android Studio development-phone installation workflow; no distribution release is claimed.

## Scope and checkpoint

Started from clean `main`, `origin/main`, and independently queried GitHub main at
`f40eff15a31b34cae1f51428eac0e3818ccc6862` (`Implement About screen`). Preserved
the interrupted changes. About is physically APPROVED / ACCEPTED / LOCKED; all
planned user-facing MVP features have Product Owner acceptance. No feature
expansion, accepted-layout redesign, schema, backup format, dependency,
versioning, signing, or distribution change belongs to this milestone.

## Production cleanup and retained items

* Removed MainActivity's redundant label; it inherits the unchanged `brdcrmbs`
  application label. Concrete initial lint finding, no identity change.
* Removed the seven unused Android starter color resources and their otherwise
  empty file. Source/resource/source-set/dynamic-lookup inspection found no
  references. The accepted Compose palette is unchanged.
* Removed SettingsRow's unreachable nullable-action/deferred branch and its
  obsolete later-milestone string. All seven existing callers already supply
  actions. No destination, visible content, or layout was redesigned.
* Native Android 2x system-font review at 560x1000 / density 280 demonstrated
  clipped explanatory text in the Data Management Clear confirmation. Wrapped
  the shared confirmation body in vertical scrolling, retaining its exact text,
  title, buttons and operations for Clear and both Reset stages. No data or
  confirmation policy change. Added scroll-action/cancel visibility assertions.
* The corrected 320x640dp / 1.5x-font capture demonstrated Progress splitting
  `100%` into `10` / `0%`. Changed only each summary's ring/value Row to FlowRow:
  the complete value may move below the ring when space requires it, retaining
  its normal font, data and normal-width arrangement. Added single-line and
  no-overflow text-layout assertions for all three scores. No aggregation,
  calendar, selection, history, or navigation change.
* Retained compatibility repository APIs, migrations and exported schemas,
  baseline arithmetic/package smoke tests, previews, approved design references,
  icon derivation utility, and hidden QA tooling. A fixed date in a Compose
  authoring preview is not runtime test data. No mechanical dead-code deletion.

## Audit findings

| Area | Finding / preserved contract |
| --- | --- |
| Copy | Reviewed production strings and screen copy. User chooses plans; no medical/prescriptive logic, food photography, or developer terminology added. Contextual Activity / Workout / Activity labels remain accepted. |
| Visual | Existing charcoal/green theme, rounded cards and readable statuses retained. Progress's approved Screen 3 mint accents and dense calendar are intentional. Native capture review results follow validation below. |
| Navigation | Four ordered tabs; child/editor navigation hides tabs; Back restores parent. Existing Week context/reselection and Progress reset tests retained. Destructive completion clears stale drafts through existing activity refresh. |
| Room | Six entities, version 4, explicit migrations and exported schemas unchanged. Repository owns persistence; Room transactions guard mutations and fasting conflicts. No destructive fallback introduced. |
| History | Changed meals retain original planned and separate actual snapshot, including after outcome changes. Missed activity parent and rescheduled child link remain. Continuous multi-day fast is one record; historical overlaps remain readable. |
| Fasting | Half-open interval conflict checks allow adjacency; edits exclude self; No Fast uses owned local date/zone; shortcuts retain start-date ownership and conflict skipping. Elapsed progress never automatically resolves adherence. Existing DST/time-zone/boundary tests retained. |
| Today | Local-date projection and explicit outcomes retained. Changed is distinct; actual meal metadata survives later Done/Missed. No future/yesterday leakage intended. |
| Progress | Real persisted aggregation, Changed resolved but not successful, Planned excluded from resolved denominator, No Fast neutral, monthly continuous-fast ownership, daily projection and planned/actual details retained. |
| Preferences | Checked local SharedPreferences; System/12/24 formatting; new-draft-only preset; meal count default 3 without generated meal slots. Backup compatibility and reset defaults retained. |
| About | Branding, exact purpose and philosophy, installed PackageManager version, scrolling, hidden tabs and Back retained. No icon duplication; launcher resources unchanged. |
| Backup | Format 1 strict JSON/UTF-8, bounded input, invalid/unknown/incompatible input fails before replacement. SAF user-selected documents; no storage permission or upload. QA ownership exclusions remain. |
| Recovery | Serialized reminder boundary, Room transaction, checked preference writes with compensation, AtomicFile before/after digest journal for interrupted cross-store recovery. No partial success reported as complete. |
| Data Management | Clear removes plan/history tables, preserves libraries/preferences/reminder choices; Reset also clears libraries and restores defaults/all four choices OFF. Runtime alarms/delivered state cleared; OS permission and external SAF files unchanged. Confirmation/cancellation and reactive refresh retained. |
| Reminders | Four OFF-by-default choices; notification permission only after explicit enable. Inexact AlarmManager, immutable PendingIntents, no exact-alarm permission, future-only reconciliation and bounded queue. Untimed/No Fast/resolved plans excluded; one continuous start/end; boot/update/time/zone job recovery retained. |
| Privacy | No account, analytics, ads, crash-upload SDK, networking feature, or data/backup upload found in source/dependencies/manifest. No INTERNET permission in release. Platform backup disabled and all local data domains excluded; portable backup is explicit SAF. |
| Dependencies | Room, AndroidX lifecycle/core/activity, Compose Material3/UI and desugaring; Kotlin coroutines/AndroidX transitive support. KSP/Room compiler build-time; JUnit/Espresso/Compose tests test-only; UI tooling/test manifest debug-only. No upgrades. Resolved release graph review recorded below. |
| Suite | Existing assertions retained. No ignored tests found; API/state assumptions in native SAF/reminder tests are explicit and reviewed. Final API 37 run reports zero skips. New combined journey uses isolated persistence and an in-memory alarm ledger; existing native tests cover Android alarms/permission/SAF. |

## Release manifest: every permission and component

Permissions: `POST_NOTIFICATIONS` for opted-in reminders;
`RECEIVE_BOOT_COMPLETED` for recovery;
`com.ventures814.brdcrmbs.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, signature
protection, generated/used by AndroidX for safe dynamic receivers. No network,
storage or exact-alarm permission.

Components: exported MainActivity has MAIN/LAUNCHER; ReminderReceiver and
ReminderRecoveryReceiver are not exported (boot/package/time/zone filters);
exported ReminderRecoveryJob requires system `BIND_JOB_SERVICE`.
AndroidX Startup provider is not exported and initializes EmojiCompat,
ProcessLifecycle and ProfileInstaller; Room MultiInstanceInvalidationService is
not exported. ProfileInstallReceiver is exported but protected by system `DUMP`.
CoreComponentFactory is the AndroidX factory. Optional window extensions/sidecar
libraries are platform integration, not cloud/network services. Application has
no debuggable/testOnly flag in release; backup disabled, normal/round launcher
icons reference the approved resources.

## Lint disposition (each initial warning)

| Initial warning | Location | Category / disposition |
| --- | --- | --- |
| RedundantLabel | AndroidManifest.xml / MainActivity | A — safely removed redundant label. |
| UnusedResources: purple_200 | values/colors.xml | A — verified unused, removed. |
| UnusedResources: purple_500 | values/colors.xml | A — verified unused, removed. |
| UnusedResources: purple_700 | values/colors.xml | A — verified unused, removed. |
| UnusedResources: teal_200 | values/colors.xml | A — verified unused, removed. |
| UnusedResources: teal_700 | values/colors.xml | A — verified unused, removed. |
| UnusedResources: black | values/colors.xml | A — verified unused, removed. |
| UnusedResources: white | values/colors.xml | A — verified unused, removed. |
| AndroidGradlePluginVersion | libs.versions.toml / AGP 9.3.3 | D — routine update deferred during accepted-behavior freeze. |
| GradleDependency | libs.versions.toml / Compose BOM 2026.02.01 | D — routine update deferred; no demonstrated defect. |
| NewerVersionAvailable | libs.versions.toml / Compose compiler 2.2.10 | D — routine update deferred; no demonstrated defect. |

Existing checked SharedPreferences commit suppression is intentional (B): success
is checked for recovery guarantees. Existing Android test framework deprecation
for `unsafeCheckOpNoThrow` is tooling/API noise (C). The build-tools message about
retaining `libandroidx.graphics.path.so` unstripped is packaging/toolchain noise
(C), not a build failure. No new blanket suppressions.

## Validation record

* Clean wrapper run: `:app:clean :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest :app:test :app:lintDebug :app:lintRelease` passed
  in 19m 2s, 137 tasks executed / one up-to-date. All 84 JVM tests across 15
  suites passed, zero failures/errors/skips. Both lints: zero errors, three
  deferred update notices each, exactly as classified above.
* Initial focused journey + visual/navigation run passed 2/2, zero
  failures/errors/skips, 86.223s execution / wrapper 2m 18s. No assertions removed.
* After the demonstrated accessibility correction, a second clean build/test/lint
  run passed in 7m 28s (137 executed / one up-to-date). Final-source follow-up
  including the bounded/clipped review fixture passed in 1m 36s (23 executed /
  114 up-to-date). Both APK builds, Android test compilation, all 84 JVM tests and
  both lints passed; lint remains zero errors / three deferred notices per
  variant. Debug is the only configured JVM-test variant. Bytecode confirms the
  confirmation scroller and per-stage scroll reset in both production variants.
* Fresh app data was cleared only on `emulator-5554`, then launched through
  MAIN/LAUNCHER. Empty Today/Week, neutral Progress, working Settings, empty saved
  libraries, four OFF reminder choices, System default / No default / 3
  preferences, installed About Version 1.0 and hardware Back were verified using
  native hierarchy/captures. No QA data, developer UI or unsolicited permission
  prompt. Physical phones were not targeted.
* Initial emulator cold boot showed a System UI ANR before app testing. Launch
  while clean builds were active subsequently showed a job-start app ANR; logs
  record 94% CPU pressure, 93% total CPU and system-wide dispatch stalls. Preserved
  diagnostics, stopped the task emulator until build completion, released idle
  Gradle daemons and restarted without changing production code. Repeat cold
  launch succeeded (23.311s on the slow emulator); a delayed System UI dialog was
  captured/dismissed before the successful native review. A native helper missed
  a tab-label tap, timed out, then resumed using observed clickable parent bounds.
  These manual/environment failures are separate from instrumentation results.
* Recovered the interrupted complete run from XML plus UTP exit-code file:
  171/171, zero failures/errors/skips, 752.981s execution, wrapper 12m 47s,
  exit code 0. This predates the strengthened narrow fixture and the subsequently
  demonstrated confirmation scrolling fix; preserved at
  `build/final-qa-review/full-first-171-passed`. It was not rerun merely because
  the conversation was interrupted.
* Focused post-confirmation-fix journey/visual/Data Management UI coverage passed
  5/5, zero failures/errors/skips, 87.350s execution / wrapper 2m 6s. The bounded
  narrow captures from that pass exposed the Progress number-wrap issue above;
  the final clean/focused/full checks below validate its minimal correction.
* Final clean source including both accessibility corrections passed all required
  builds, Android test compilation, all 84 JVM tests, debug lint and release lint
  in 10m 54s (137 executed / one up-to-date). Each lint has zero errors and only
  the same three individually reviewed/deferred update notices. Final Android
  focused/full results are recorded below.
* The first final focused run finished 4/5 (zero errors/skips): journey and all
  three Data Management UI tests passed; the visual test's new coarse
  `didOverflowWidth` assertion failed. An isolated diagnostic reproduced 0/1:
  Fasting `100%` occupied one complete line, all four characters, glyph bounds
  0..99.565216px inside a 101px layout, no height overflow. Its actual capture is
  fully readable. Corrected this new test assumption to assert single line,
  complete text, no ellipsis, exact visible glyph bounds and no height overflow;
  no meaningful clipping assertion or production behavior was weakened.
  Failed reports are retained separately. Corrected focused rerun passed 5/5,
  zero failures/errors/skips, 86.390s execution / wrapper 1m 57s, with all
  single-line/full-text/glyph-bound checks intact. The subsequent complete
  regression passed against this source.
* A repeated cold-boot System UI ANR coincided with the first final focused
  startup. System event logs identify `com.android.systemui/.SystemUIService`
  waiting 60078ms; the confirmed system dialog was dismissed. Its compositor
  capture was black during recovery. The width-assertion failure above was
  investigated independently and is not attributed to that system event. No
  production workaround was added; the final full run passed after recovery
  without manual UI intervention during that run.
* Viewed all 29 focused captures: primary screens, all planning Add/Edit forms,
  actual-meal editor, saved-library editors, Settings children, Clear and both
  Reset confirmations, and enlarged-font screens. Layout, contrast, status
  treatment, hidden child navigation, cancellation and returning Back remained
  usable. Long forms scroll. Capture inspection exposed Surface minimum bounds
  expanding the test-only narrow viewport; strengthened that new fixture with an
  outer Box, clipping boundary and explicit 320x640dp assertions. Final focused
  and complete runs passed with those assertions.
  Native system-font review then exposed the confirmation-copy clipping above
  (a composition font override does not substitute for a separate dialog window).
  Manual-helper retries corrected tall-viewport gestures, non-clickable heading
  selection and waits for an off-screen parent heading; these were not failed
  instrumentation tests. Post-fix native review passed at 560x1000 / density
  280, Android system font_scale 2.0. Viewed confirmation top/bottom captures:
  all retained/deleted data, permission and exported-backup statements are
  reachable by scrolling, with Cancel/Continue/final action visible. Final Reset
  starts at its first sentence rather than retaining the previous scroll offset.
  All destructive reviews cancelled without writes. About's complete purpose,
  philosophy, installed Version 1.0 and hardware Back passed with scrolling.
  Restored font_scale 1.0 and the 720x1560 test viewport before instrumentation.
* Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`,
  9,061,416 bytes, SHA-256
  `e1314edac1739c92685d56227c25f73df7f9f2da49e5ab306973777208e0d2b7`.
  aapt2 confirms package `com.ventures814.brdcrmbs`, label `brdcrmbs`, version
  1.0/code 1, min 24/target/compile 37; compiled manifest has no debuggable/testOnly
  flag. apksigner correctly reports unsigned/missing signing manifest; no key,
  signing configuration or distribution was created.
* Thirteen QA class/label/ownership/scenario markers are present in debug and
  absent from all release DEX files. Release code/resources contain no tested
  local workspace paths. Resolved release runtime graph has no analytics, ads,
  networking, crash-upload SDK, Compose test manifest or runtime UI tooling;
  tooling-preview annotations are intentional authoring metadata.
* Approved icon reference hash matches the derivation utility. Read-only asset
  inspection confirms 432px adaptive layers, nontransparent symbol radius
  31.49dp within 33dp safe radius, no clipping under circle/rounded-square/squircle
  masks, and normal/round legacy assets at 48/72/96/144/192px. All 12 packaged
  adaptive-layer/legacy raster resources match source pixels, resolved through
  aapt2's resource table because release filenames are shortened. No assets
  regenerated.
* Repository/source/release configuration secret scans found no credential,
  private-key or token candidate. URL hits are Android XML namespace declarations.
  local.properties, caches, reports/builds, signing material and emulator evidence
  are ignored/untracked; approved design references remain tracked.

### Final verified result — recovered 2026-10-30

The interrupted final wrapper completed successfully and left trustworthy XML,
logs and UTP exit code 0. Recovered those results instead of rerunning solely
because the conversation was interrupted:

* **171/171 Android tests across 44 suites, zero failures/errors/skips, ONE
  uninterrupted final-source run.** Execution: 769.741 seconds; complete wrapper:
  **15m 6s**, 5 tasks executed / 133 up-to-date. All 169 pre-existing cases and
  both new Final QA cases are represented. No manual UI action interrupted this
  final regression.
* The same wrapper passed debug/release builds, Android test APK compilation,
  `:app:test`, debug lint and release lint. **84/84 JVM tests across 15 suites**,
  zero failures/errors/skips; Debug is the only configured unit-test variant.
  Final debug/release lint: **zero errors / three category-D notices each**.
* FinalQaJourneyTest **1/1** and FinalQaVisualReviewTest **1/1** passed again in
  the full run. It also includes all Week/CRUD/copy/navigation, Today/adherence,
  Progress, fasting integrity, Settings/libraries, About, reminders, backup,
  Data Management, preferences, migrations/history and direct QA tooling tests.
  Existing assertions remain intact; final skips are zero.
* Combined journey verifies plan/copy/repeat, explicit outcomes and retained
  Changed actual snapshot, missed/rescheduled linkage, continuous fasting/No
  Fast, real Progress scores, preferences, libraries, reminder reconciliation,
  repeated Clear/Reset, immutable exported bytes and exact restoration. Its alarm
  ledger is in-memory; existing native integration and SAF tests separately
  verify real Android notifications/permissions/picker and Backup → Reset →
  Restore with immediate UI refresh.
* Final normal-width Progress capture retains approved Screen 3 hierarchy and
  information density. At actual 320x640dp / 1.5x font, complete `100%`, `50%`
  and `0%` values are readable; the ring/value flow wraps only when required.
  Calendar/day-strip horizontal overflow and vertical page scrolling remain
  intentional. Large fonts can wrap tab labels; labels/actions remain present
  and usable rather than being shrunk or removed.
* All audits above are complete. Source/entity/schema/migration/dependency,
  backup format 1, approved assets, versionName 1.0 and versionCode 1 remain
  unchanged outside the documented safe cleanup and two minimal UI fixes.
  There is no unresolved app defect demonstrated in this QA scope.
* Restored the task emulator's original 1440x3120 / density 560 / font scale 1.0
  and stopped it. No physical phone was targeted. Only completion documentation
  changed after the final passing source/test run.

Final evidence: ignored `build/final-qa-review/final-full-171-passed`,
`focused-verified-5-passed`, `freeze-jvm-passed`, lint XML, artifact checks and
normal/narrow/native captures. Earlier failures are retained separately. These
scripts/reports/images are not runtime dependencies or source-control artifacts.

## Known limits and personal-use completion boundary

Inexact reminders remain subject to Android/OEM delivery and power policies;
emulator coverage does not substitute for every supported Android/OEM device.
Backups are user-managed portable JSON, not encrypted cloud backup. Default meal
count is retained preference metadata and creates no slots. Detailed exercise
logging, recommendations, photos, accounts and cloud sync remain outside scope.
Per Product Owner direction on 2026-10-30, **BRDCRMBS V1.0 COMPLETE / MVP FEATURE
DEVELOPMENT COMPLETE / FINAL QA PASSED / ALL PLANNED V1 FEATURES ACCEPTED** is
the finish line. About remains APPROVED / ACCEPTED / LOCKED; Developer / Testing
remains HIDDEN BUT RETAINED. There is no required Release Candidate, Google Play,
AAB packaging, signing-key setup, store listing or distribution phase. The
unsigned release APK is QA evidence only. No versioning, signing or distribution
work was performed. Future feature work is V1.1 or later and awaits Product Owner
direction; it has not begun.
