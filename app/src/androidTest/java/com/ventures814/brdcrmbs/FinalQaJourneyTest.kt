package com.ventures814.brdcrmbs

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.reminders.*
import com.ventures814.brdcrmbs.ui.fasting.FastPreset
import com.ventures814.brdcrmbs.ui.today.progressAt
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** One combined journey through production persistence/services; UI/picker paths have separate coverage. */
class FinalQaJourneyTest {
    private class Ledger : ReminderBackend {
        val alarms = mutableMapOf<String, PlanReminder>()
        override fun pending() = alarms.toMap()
        override fun delivered() = emptyMap<String, Instant>()
        override fun schedule(reminder: PlanReminder) { alarms[reminder.key] = reminder }
        override fun cancel(key: String) { alarms.remove(key) }
        override fun post(reminder: PlanReminder) = Unit
        override fun markDelivered(reminder: PlanReminder) = Unit
    }

    @Test fun planFollowReviewBackupClearRestoreAndResetPreserveHistoricalTruth() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "final-journey-${UUID.randomUUID()}"
        val zone = ZoneId.of("America/Denver")
        val date = LocalDate.of(2026, 10, 27)
        val clock = Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone)
        val db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        val repo = RoomPlanningRepository(db, clock)
        val settings = ReminderSettingsStore(context, "$name-reminders")
        val preferences = AppPreferencesStore(context, "$name-preferences")
        val store = RoomBackupStore(db, settings, preferences)
        val backend = Ledger()
        val coordinator = ReminderCoordinator(repo, settings, backend, { true }, { clock })
        val journal = File(context.cacheDir, "$name-journal.json")
        val document = File(context.cacheDir, "$name-export.json")
        val service = BackupService(store, settings, context.contentResolver, journal, {}, clock = clock,
            restoreBoundary = coordinator::restoreAndReconcile,
            cleanupDataRuntime = { reset -> backend.alarms.clear(); if (reset) settings.resetPermissionHistory() })
        suspend fun snapshot() = store.snapshot(Instant.EPOCH, excludeQa = false)
        try {
            assertEquals(BackupSnapshot(Instant.EPOCH), snapshot())
            val start = date.atStartOfDay(zone).toInstant()
            val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(16)), zone = zone, preset = "16:8")
            repo.addFasting(fast)
            assertEquals(75, fast.progressAt(clock.instant()).percent)
            assertEquals(FastingOutcome.PLANNED, (repo.getFastingPlan(fast.id) as FastingEvent).outcome)
            val longStart = date.plusDays(3).atTime(19, 0).atZone(zone).toInstant()
            val multi = FastingEvent(start = longStart, end = longStart.plus(Duration.ofHours(48)), zone = zone, preset = "48h")
            repo.addFasting(multi)
            val noFast = NoFastPlan(date = date.plusDays(6), zone = zone)
            repo.addFastingPlan(noFast)
            val meal = MealPlan(date = date, time = LocalTime.NOON, text = "Soup", label = "Lunch")
            val changed = MealPlan(date = date, time = LocalTime.of(18, 0), text = "Planned chicken", notes = "Original")
            val activity = ActivityPlan(date = date, time = LocalTime.of(18, 30), name = "Evening walk")
            repo.savePlannedMeal(meal, true, true); repo.savePlannedMeal(changed, true)
            repo.savePlannedActivity(activity, true, true)
            assertEquals(8, repo.copyDay(date, setOf(date.plusDays(1), date.plusDays(2)), zone).added)
            val today = AdherenceDay(date, zone)
            repo.recordTodayFasting(fast.id, FastingOutcome.DONE, today)
            repo.recordTodayMeal(meal.id, MealOutcome.DONE, today)
            val actual = ActualMeal("Actual fish", LocalTime.of(19, 0), "Dinner", "Actual notes")
            repo.recordTodayMeal(changed.id, MealOutcome.CHANGED, today, actual)
            repo.recordTodayMeal(changed.id, MealOutcome.DONE, today)
            assertEquals(actual, repo.getMeal(changed.id)!!.actual)
            repo.recordTodayMeal(changed.id, MealOutcome.CHANGED, today, actual)
            repo.recordTodayActivity(activity.id, ActivityOutcome.MISSED, today)
            val rescheduled = activity.copy(id = UUID.randomUUID().toString(), date = date.plusDays(3),
                outcome = ActivityOutcome.PLANNED, rescheduledFromId = activity.id)
            // Supported history relationship, not a new rescheduling UI or planning-CRUD bypass.
            repo.addActivity(rescheduled)
            assertTrue(repo.repeatLastWeek(date.plusWeeks(1), zone).added > 0)
            val review = snapshot().plans.reviewMonth(YearMonth.from(date), zone)
            assertEquals(50, review.days.single { it.date == date }.adherence.percent)
            assertEquals(ReviewStatus.CHANGED, review.days.single { it.date == date }.meals)
            assertEquals(ReviewStatus.NONE, snapshot().plans.reviewMonth(YearMonth.from(noFast.date), zone)
                .days.single { it.date == noFast.date }.fasting)
            assertEquals(48, Duration.between(multi.start, multi.end).toHours().toInt())
            preferences.replace(AppPreferences(TimeFormat.TWENTY_FOUR, FastPreset.H48, 6))
            ReminderType.entries.forEach { settings.set(it, true) }
            coordinator.reconcile()
            assertTrue(backend.pending().isNotEmpty())
            val original = snapshot()
            val prepared = service.create()
            service.write(Uri.fromFile(document), prepared)
            val exported = document.readBytes()
            repo.saveSavedMeal(original.savedMeals.single().copy(text = "Mutated library"), false)
            val mutatedMeals = snapshot().savedMeals
            service.manageData(DataAction.CLEAR_PLANNING)
            assertEquals(PlanningData(), snapshot().plans)
            assertEquals(mutatedMeals, snapshot().savedMeals)
            assertEquals(original.savedActivities, snapshot().savedActivities)
            assertEquals(original.appPreferences, snapshot().appPreferences)
            assertEquals(original.reminders, snapshot().reminders)
            assertTrue(backend.pending().isEmpty())
            assertArrayEquals(exported, document.readBytes())
            service.restore(service.read(Uri.fromFile(document)))
            assertEquals(BackupCodec.encode(original), BackupCodec.encode(snapshot()))
            assertEquals(changed.text, repo.getMeal(changed.id)!!.text)
            assertEquals(actual, repo.getMeal(changed.id)!!.actual)
            assertEquals(ActivityOutcome.MISSED, repo.getActivity(activity.id)!!.outcome)
            assertEquals(activity.id, repo.getActivity(rescheduled.id)!!.rescheduledFromId)
            service.manageData(DataAction.RESET_APP)
            service.manageData(DataAction.RESET_APP)
            assertEquals(BackupSnapshot(Instant.EPOCH), snapshot())
            assertEquals(AppPreferences(), AppPreferencesStore(context, "$name-preferences").state.value)
            assertEquals(ReminderPreferences(), ReminderSettingsStore(context, "$name-reminders").state.value)
            assertArrayEquals(exported, document.readBytes())
            service.restore(service.read(Uri.fromFile(document)))
            assertEquals(BackupCodec.encode(original), BackupCodec.encode(snapshot()))
            service.manageData(DataAction.CLEAR_PLANNING); service.manageData(DataAction.CLEAR_PLANNING)
            assertEquals(original.copy(plans = PlanningData()), snapshot())
            service.manageData(DataAction.RESET_APP)
            repo.addMeal(MealPlan(date = date, text = "Immediately usable"))
            assertEquals(1, snapshot().plans.meals.size)
            assertEquals(4, db.openHelper.writableDatabase.version)
        } finally {
            coordinator.close(); db.close(); document.delete(); journal.delete()
            listOf("$name-preferences", "$name-reminders", "$name-reminders_permission").forEach {
                context.getSharedPreferences(it, 0).edit().clear().commit()
            }
        }
    }
}
