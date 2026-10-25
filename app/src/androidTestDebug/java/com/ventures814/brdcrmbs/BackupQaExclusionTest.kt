package com.ventures814.brdcrmbs

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.*
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.ReminderSettingsStore
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BackupQaExclusionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PlanningDatabase
    private lateinit var store: RoomBackupStore
    private lateinit var repo: RoomPlanningRepository
    private val name = "backup-qa-${java.util.UUID.randomUUID()}"
    private val clock = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneId.of("America/Denver"))
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db); store = RoomBackupStore(db, ReminderSettingsStore(context, name))
    }
    @After fun cleanup() { db.close(); context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun canonicalAndActiveOwnedRecordsAreExcludedWithoutChangingTheDatabase() = runBlocking {
        val seeder = ProgressQaSeeder(db, clock); seeder.seed(seeder.currentWeek())
        val genuine = MealPlan(QaOwnership.PREFIX + "2026-10-19|unknown", LocalDate.now(clock), text = "Genuine lookalike")
        repo.addMeal(genuine)
        repo.saveSavedMeal(SavedMeal(text = "Saved genuine"), true)
        repo.saveSavedActivity(SavedActivity(name = "Saved genuine", type = ActivityType.ACTIVITY), true)
        val before = repo.load(DateRange.weekContaining(LocalDate.now(clock)), clock.zone)
        val backup = store.snapshot(clock.instant())
        assertEquals(listOf(genuine), backup.plans.meals)
        assertTrue(backup.plans.fasting.isEmpty()); assertTrue(backup.plans.noFast.isEmpty()); assertTrue(backup.plans.activities.isEmpty())
        assertEquals(1, backup.savedMeals.size); assertEquals(1, backup.savedActivities.size)
        assertEquals(before, repo.load(DateRange.weekContaining(LocalDate.now(clock)), clock.zone))
        seeder.reset(); seeder.seedActiveFast()
        assertTrue(store.snapshot(clock.instant()).plans.fasting.isEmpty())
        assertNotNull(repo.getFastingPlan(ActiveFastQaOwnership.ID))
        seeder.reset()
        val start = clock.instant()
        val lookalike = FastingEvent(ActiveFastQaOwnership.ID + "-copy", start, start.plusSeconds(3600), clock.zone)
        repo.addFasting(lookalike)
        assertEquals(listOf(lookalike), store.snapshot(clock.instant()).plans.fasting)
    }
    @Test fun genuineLinkToExcludedQaHistoryRejectsExportWithoutDroppingOrRewritingIt() = runBlocking {
        val seeder = ProgressQaSeeder(db, clock); seeder.seed(seeder.currentWeek())
        val linked = ActivityPlan(date = LocalDate.now(clock), name = "Genuine linked activity", rescheduledFromId = QaOwnership.id(seeder.currentWeek(), "activity-wed"))
        repo.addActivity(linked)
        val before = repo.load(DateRange.weekContaining(LocalDate.now(clock)), clock.zone)
        assertTrue(runCatching { store.snapshot(clock.instant()) }.isFailure)
        assertEquals(before, repo.load(DateRange.weekContaining(LocalDate.now(clock)), clock.zone))
        assertEquals(linked, repo.getActivity(linked.id))
    }
}
