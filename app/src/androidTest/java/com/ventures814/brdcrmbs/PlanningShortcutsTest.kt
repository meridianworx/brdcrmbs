package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class PlanningShortcutsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "shortcuts-${UUID.randomUUID()}.db"
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private fun open() { database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build(); repo = RoomPlanningRepository(database) }
    @Before fun setup() = open()
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }

    @Test fun copyOriginalPlansWithNewIdentitiesAndKeepHistoryLibrariesAndDestination() = runBlocking {
        val day = LocalDate.of(2026, 12, 31)
        val target = day.plusDays(1)
        val meals = listOf(
            MealPlan(date = day, time = LocalTime.of(11, 0, 30, 123), text = "Original", label = "Lunch", notes = "Plan", outcome = MealOutcome.CHANGED, actual = ActualMeal("Actual")),
            MealPlan(date = day, text = "Second", outcome = MealOutcome.DONE))
        meals.forEach { repo.addMeal(it) }
        val missed = ActivityPlan(date = day, time = LocalTime.of(17, 0), name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT, outcome = ActivityOutcome.MISSED)
        val linked = ActivityPlan(date = day, name = "Walk", notes = "Outside", rescheduledFromId = missed.id)
        repo.addActivity(missed); repo.addActivity(linked)
        repo.addMeal(MealPlan(date = target, text = "Keep destination", outcome = MealOutcome.MISSED))
        repo.savePlannedMeal(MealPlan(date = day.minusDays(1), text = "Library meal"), true, true)
        repo.savePlannedActivity(ActivityPlan(date = day.minusDays(1), name = "Library activity"), true, true)
        val savedMeals = repo.savedMeals(); val savedActivities = repo.savedActivities()
        val before = repo.load(DateRange.day(day), testZone)
        assertEquals(CopyResult(4, 0), repo.copyDay(day, setOf(target), testZone))
        val result = repo.load(DateRange.day(target), testZone)
        val copies = result.meals.filter { it.outcome == MealOutcome.PLANNED }
        assertEquals(2, copies.size); assertTrue(copies.none { copy -> meals.any { it.id == copy.id } })
        assertEquals(meals[0].time, copies.single { it.text == "Original" }.time)
        assertEquals("Plan", copies.single { it.text == "Original" }.notes)
        assertTrue(copies.all { it.actual == null }); assertEquals(2, result.activities.size)
        assertTrue(result.activities.all { it.outcome == ActivityOutcome.PLANNED && it.rescheduledFromId == null })
        assertEquals(setOf(ActivityType.ACTIVITY, ActivityType.WORKOUT), result.activities.map { it.type }.toSet())
        assertEquals(1, result.meals.count { it.text == "Keep destination" && it.outcome == MealOutcome.MISSED })
        assertEquals(before, repo.load(DateRange.day(day), testZone))
        assertEquals(savedMeals, repo.savedMeals()); assertEquals(savedActivities, repo.savedActivities())
        database.close(); open()
        assertEquals(result, repo.load(DateRange.day(target), testZone))
    }

    @Test fun deterministicExactDuplicatesAreSkippedIncludingConcurrentCopyAndNoFast() = runBlocking {
        val source = testDate
        val target = source.plusDays(1)
        repo.addMeal(MealPlan(date = source, time = LocalTime.NOON, text = "Soup", notes = "Keep"))
        repo.addFastingPlan(NoFastPlan(date = source, zone = testZone, notes = "No fast"))
        val first = async(Dispatchers.IO) { repo.copyDay(source, setOf(target), testZone) }
        val second = async(Dispatchers.IO) { repo.copyDay(source, setOf(target), testZone) }
        assertEquals(2, listOf(first.await(), second.await()).sumOf { it.added })
        assertEquals(CopyResult(0, 2), repo.copyDay(source, setOf(target), testZone))
        assertEquals(1, repo.load(DateRange.day(target), testZone).noFast.size)
        // Notes and optional time are meaningful key fields; historical outcomes never suppress a new plan.
        repo.addMeal(MealPlan(date = target, time = LocalTime.NOON, text = "Soup", notes = "Different"))
        repo.addMeal(MealPlan(date = source, text = "Untimed", outcome = MealOutcome.MISSED))
        repo.addMeal(MealPlan(date = target, text = "Untimed", outcome = MealOutcome.DONE))
        assertEquals(CopyResult(1, 2), repo.copyDay(source, setOf(target), testZone))
        assertEquals(4, repo.load(DateRange.day(target), testZone).meals.size)
    }

    @Test fun fastingCopiesKeepOneFullEventAndDeduplicateWhenRepeated() = runBlocking {
        for ((index, hours) in listOf(2L, 16L, 24L, 36L, 48L, 72L, 211L).withIndex()) {
            val source = LocalDate.of(2026, 1, 1).plusDays(index * 60L)
            val target = source.plusDays(15)
            val start = source.atTime(19, 0).atZone(testZone).toInstant()
            val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(hours)), zone = testZone, preset = "Custom", notes = "Keep", outcome = FastingOutcome.MISSED)
            repo.addFasting(fast)
            assertEquals(CopyResult(1, 0), repo.copyDay(source, setOf(target), testZone))
            val copied = repo.load(DateRange.day(target), testZone).fasting.single()
            assertNotEquals(fast.id, copied.id); assertEquals(FastingOutcome.PLANNED, copied.outcome)
            assertEquals(Duration.ofHours(hours), Duration.between(copied.start, copied.end))
            assertEquals(target.atTime(19, 0), copied.start.atZone(testZone).toLocalDateTime())
            assertEquals(CopyResult(0, 1), repo.copyDay(source, setOf(target), testZone))
            assertEquals(fast, repo.getFastingPlan(fast.id))
        }
    }

    @Test fun multipleDestinationsEmptySourceAndSelfDestinationValidation() = runBlocking {
        assertEquals(CopyResult(0, 0), repo.copyDay(testDate, setOf(testDate.plusDays(1)), testZone))
        val plan = MealPlan(date = testDate, text = "Lunch")
        repo.addMeal(plan)
        val targets = setOf(testDate.plusDays(1), testDate.plusDays(2), testDate.plusDays(20))
        assertEquals(CopyResult(3, 0), repo.copyDay(testDate, targets, testZone))
        val copied = targets.map { repo.load(DateRange.day(it), testZone).meals.single() }
        assertEquals(3, copied.map { it.id }.toSet().size)
        try { repo.copyDay(testDate, setOf(testDate), testZone); fail("Source cannot be a destination") } catch (_: IllegalArgumentException) { }
        assertEquals(plan, repo.getMeal(plan.id))
    }

    @Test fun repeatMapsWeekdaysAcrossYearBoundaryAndCopiesLongOverlapOnlyOnce() = runBlocking {
        val previous = LocalDate.of(2026, 12, 28)
        for (offset in 0..6) repo.addMeal(MealPlan(date = previous.plusDays(offset.toLong()), time = LocalTime.NOON, text = "Day $offset", outcome = MealOutcome.MISSED))
        val start = previous.atTime(19, 0).atZone(testZone).toInstant()
        val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(72)), zone = testZone, outcome = FastingOutcome.DONE)
        repo.addFasting(fast)
        val target = previous.plusWeeks(1)
        val keep = MealPlan(date = target, text = "Keep")
        repo.addMeal(keep)
        assertEquals(CopyResult(8, 0), repo.repeatLastWeek(target.plusDays(3), testZone))
        assertEquals(CopyResult(0, 8), repo.repeatLastWeek(target, testZone))
        val current = repo.load(DateRange.weekContaining(target), testZone)
        assertEquals(8, current.meals.size); assertTrue(current.meals.contains(keep))
        (0..6).forEach { offset -> assertEquals(target.plusDays(offset.toLong()), current.meals.single { it.text == "Day $offset" }.date) }
        assertEquals(1, current.fasting.size)
        assertEquals(Duration.ofHours(72), Duration.between(current.fasting.single().start, current.fasting.single().end))
        assertNotEquals(fast.id, current.fasting.single().id)
        assertEquals(fast, repo.getFastingPlan(fast.id))
    }

    @Test fun ongoingEarlierFastsAreNotCopiedAgainFromOverlappingDayOrWeek() = runBlocking {
        val monday = testDate
        val start = monday.minusDays(1).atTime(19, 0).atZone(testZone).toInstant()
        val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(72)), zone = testZone)
        repo.addFasting(fast)
        assertEquals(1, repo.load(DateRange.day(monday), testZone).fasting.size)
        assertEquals(CopyResult(0, 0), repo.copyDay(monday, setOf(monday.plusDays(5)), testZone))
        assertEquals(CopyResult(0, 0), repo.repeatLastWeek(monday.plusWeeks(1), testZone))
        assertEquals(fast, repo.getFastingPlan(fast.id))
    }

    @Test fun dstErrorAndLateDatabaseFailureRollBackWholeMultiDayRequest() = runBlocking {
        val source = LocalDate.of(2026, 3, 6)
        val start = source.atTime(2, 30).atZone(testZone).toInstant()
        repo.addFasting(FastingEvent(start = start, end = start.plusSeconds(3600), zone = testZone))
        repo.addMeal(MealPlan(date = source, text = "Meal"))
        try { repo.copyDay(source, setOf(source.plusDays(1), source.plusDays(2)), testZone); fail("DST gap must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(PlanningData(), repo.load(DateRange.day(source.plusDays(1)), testZone))
        assertEquals(PlanningData(), repo.load(DateRange.day(source.plusDays(2)), testZone))
        // Force failure only on the second destination after the first has been inserted.
        val destination = source.plusDays(10)
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_copy BEFORE INSERT ON meal_plans
            WHEN NEW.dateEpochDay = ${destination.plusDays(1).toEpochDay()}
            BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        try { repo.copyDay(source, setOf(destination, destination.plusDays(1)), testZone); fail("Insert should fail") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(PlanningData(), repo.load(DateRange(destination, destination.plusDays(2)), testZone))
    }
}
