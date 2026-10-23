package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.*
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import com.ventures814.brdcrmbs.ui.progress.ProgressViewModel
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class ProgressQaSeederTest {
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2026, 10, 22)
    private val clock = Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone)
    private lateinit var db: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var seeder: ProgressQaSeeder
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db)
        seeder = ProgressQaSeeder(db, clock)
    }
    @After fun cleanup() { db.close() }
    private suspend fun snapshot() = repo.load(DateRange(date.minusMonths(1), date.plusMonths(1)), zone)
    @Test fun seededRecordsSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-durability-${java.util.UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build()
        try {
            val diskSeeder = ProgressQaSeeder(disk, clock)
            diskSeeder.seed(diskSeeder.currentWeek())
            val range = DateRange.weekContaining(date)
            val before = RoomPlanningRepository(disk).load(range, zone)
            disk.close()
            disk = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build()
            assertEquals(before, RoomPlanningRepository(disk).load(range, zone))
            ProgressQaSeeder(disk, clock).reset()
            assertTrue(RoomPlanningRepository(disk).load(range, zone).meals.isEmpty())
        } finally { disk.close(); context.deleteDatabase(name) }
    }
    @Test fun canonicalPersistenceIdempotencyAndOverlap() = runBlocking {
        val week = seeder.currentWeek()
        seeder.seed(week)
        val first = snapshot()
        val expected = progressQaScenario(week)
        assertEquals(expected.fasting.toSet(), first.fasting.toSet())
        assertEquals(expected.noFast.toSet(), first.noFast.toSet())
        assertEquals(expected.meals.toSet(), first.meals.toSet())
        assertEquals(expected.activities.toSet(), first.activities.toSet())
        repo.recordChangedMeal(QaOwnership.id(week, "meal-thu-1"), ActualMeal("QA correction"))
        seeder.seed(week)
        assertEquals(first, snapshot())
        val fast = expected.fasting.single { it.preset == "48h" }
        assertEquals(listOf(fast), repo.load(DateRange.day(week.endInclusive), zone).fasting)
        assertEquals(listOf(fast), repo.load(DateRange.day(week.monday.plusWeeks(1)), zone).fasting)
        assertEquals(4, db.openHelper.writableDatabase.version)
    }
    @Test fun resetPreservesGenuineHistorySavedItemsAndLookalikes() = runBlocking {
        val genuine = MealPlan(date = date, text = "Genuine planned dinner")
        repo.savePlannedMeal(genuine, true, true)
        repo.recordChangedMeal(genuine.id, ActualMeal("Genuine actual dinner"))
        val activity = ActivityPlan(date = date.plusWeeks(2), name = "Genuine saved walk", type = ActivityType.ACTIVITY)
        repo.savePlannedActivity(activity, true, true)
        val lookalike = MealPlan(id = QaOwnership.PREFIX + "2026-10-19|unknown", date = date, text = "Not owned")
        repo.addMeal(lookalike)
        val baseline = snapshot()
        val meals = repo.savedMeals(); val activities = repo.savedActivities()
        seeder.seed(seeder.currentWeek()); seeder.reset(); seeder.reset()
        assertEquals(baseline, snapshot())
        assertEquals(meals, repo.savedMeals()); assertEquals(activities, repo.savedActivities())
    }
    @Test fun resetNotifiesExistingRepositoryObservers() = runBlocking {
        val range = DateRange.weekContaining(date)
        val emissions = mutableListOf<PlanningData>()
        val job = launch { repo.observe(range, zone).collect { emissions.add(it) } }
        withTimeout(10_000) { while (emissions.isEmpty()) delay(20) }
        seeder.seed(seeder.currentWeek())
        withTimeout(10_000) { while (emissions.last().meals.size != 9) delay(20) }
        seeder.reset()
        withTimeout(10_000) { while (emissions.last().meals.isNotEmpty()) delay(20) }
        assertTrue(emissions.last().fasting.isEmpty())
        job.cancelAndJoin()
    }
    @Test fun weekTodayAndProgressObserveSeedAndResetWithoutInjectedUiState() = runBlocking {
        val store = ViewModelStore()
        val week = WeekViewModel(repo, SavedStateHandle(), clock)
        val today = TodayViewModel(repo, SavedStateHandle(), clock)
        val progress = ProgressViewModel(repo, SavedStateHandle(), clock)
        store.put("week", week); store.put("today", today); store.put("progress", progress)
        withContext(Dispatchers.Main) { week.resetToToday() }
        val jobs = listOf(launch { week.state.collect() }, launch { today.state.collect() }, launch { progress.state.collect() })
        try {
            withTimeout(30_000) { while (week.state.value.loading || today.state.value.loading || progress.state.value.loading) delay(20) }
            seeder.seed(seeder.currentWeek())
            withTimeout(30_000) { while (week.state.value.meals.size != 1 || today.state.value.plans.meals.size != 1 || progress.state.value.review?.meals?.resolved != 8) delay(20) }
            assertEquals(75, progress.state.value.review?.meals?.percent)
            seeder.reset()
            withTimeout(30_000) { while (week.state.value.meals.isNotEmpty() || today.state.value.plans.meals.isNotEmpty() || progress.state.value.review?.meals?.resolved != 0) delay(20) }
        } finally {
            jobs.forEach { it.cancelAndJoin() }
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
    @Test fun foreignKeyHistoryMakesResetFailAtomically() = runBlocking {
        val week = seeder.currentWeek(); seeder.seed(week)
        repo.addActivity(ActivityPlan(date = date, name = "Genuine linked history", type = ActivityType.WORKOUT,
            rescheduledFromId = QaOwnership.id(week, "activity-wed")))
        val before = snapshot()
        assertTrue(runCatching { seeder.reset() }.isFailure)
        assertEquals(before, snapshot())
    }
    @Test fun seedFailureRollsBackAndOtherSeededWeeksRemainIndependent() = runBlocking {
        val week = seeder.currentWeek()
        val nextClock = Clock.offset(clock, Duration.ofDays(7))
        val nextSeeder = ProgressQaSeeder(db, nextClock)
        nextSeeder.seed(nextSeeder.currentWeek())
        val nextOnly = snapshot()
        val failedId = QaOwnership.id(week, "meal-mon-1")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_qa BEFORE INSERT ON meal_plans WHEN NEW.id = '$failedId' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(runCatching { seeder.seed(week) }.isFailure)
        assertEquals(nextOnly, snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_qa")
        seeder.seed(week); seeder.seed(week)
        assertEquals(18, snapshot().meals.size)
        assertTrue(runCatching { seeder.seed(nextSeeder.currentWeek()) }.isFailure)
        seeder.reset()
        assertTrue(snapshot().meals.isEmpty()); assertTrue(snapshot().fasting.isEmpty())
    }
}
