package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlanningPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "planning-test-${UUID.randomUUID()}.db"
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository

    private fun open() {
        database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build()
        repository = RoomPlanningRepository(database)
    }
    @Before fun setup() = open()
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }

    @Test fun mealAndActivitySurviveDatabaseReopen() = runBlocking {
        val meal = MealPlan(date = testDate, text = "Lunch", notes = "Bring leftovers")
        val activity = ActivityPlan(date = testDate, time = LocalTime.of(17, 15, 30), name = "Gym", type = ActivityType.WORKOUT)
        repository.addMeal(meal)
        repository.addActivity(activity)
        database.close()
        open()
        val loaded = repository.load(testRange, testZone)
        assertEquals(listOf(meal), loaded.meals)
        assertEquals(listOf(activity), loaded.activities)
    }

    @Test fun changedMealKeepsOriginalAndAllActualFields() = runBlocking {
        val original = MealPlan(date = testDate, time = LocalTime.of(12, 0), text = "Planned lunch", label = "Lunch", notes = "Original notes")
        repository.addMeal(original)
        val actual = ActualMeal("Actual lunch", LocalTime.of(13, 20, 45, 123), "Late lunch", "Actual notes")
        repository.recordChangedMeal(original.id, actual)
        val loaded = repository.load(testRange, testZone).meals.single()
        assertEquals(original.copy(outcome = MealOutcome.CHANGED, actual = actual), loaded)
        database.close(); open()
        assertEquals(loaded, repository.load(testRange, testZone).meals.single())
    }

    @Test fun sameDayCrossMidnightAndLongFastsAreSingleOverlappingEvents() = runBlocking {
        val start = testDate.atTime(19, 0).atZone(testZone).toInstant()
        for (hours in listOf(2L, 16L, 24L, 36L, 48L, 72L, 240L)) {
            val event = FastingEvent(id = "fast-$hours", start = start, end = start.plus(Duration.ofHours(hours)), zone = testZone, notes = "One event")
            repository.addFasting(event)
            val wideRange = DateRange(testDate.minusDays(1), testDate.plusDays(12))
            assertEquals(listOf(event), repository.load(wideRange, testZone).fasting.filter { it.id == event.id })
            var day = testDate
            while (day.atStartOfDay(testZone).toInstant() < event.end) {
                assertTrue(repository.load(DateRange.day(day), testZone).fasting.contains(event))
                day = day.plusDays(1)
            }
            assertFalse(repository.load(DateRange.day(day), testZone).fasting.contains(event))
        }
    }

    @Test fun midnightEndpointsAndWeekYearBoundariesAreHalfOpen() = runBlocking {
        val newYear = LocalDate.of(2027, 1, 1)
        val end = newYear.atStartOfDay(testZone).toInstant()
        val event = FastingEvent(start = end.minus(Duration.ofHours(24)), end = end, zone = testZone)
        repository.addFasting(event)
        repository.addMeal(MealPlan(date = newYear.minusDays(1), text = "December"))
        repository.addMeal(MealPlan(date = newYear, text = "January"))
        assertTrue(repository.load(DateRange.day(newYear), testZone).fasting.isEmpty())
        assertEquals(listOf(event), repository.load(DateRange.day(newYear.minusDays(1)), testZone).fasting)
        assertEquals(2, repository.load(DateRange.weekContaining(newYear), testZone).meals.size)
        assertEquals(listOf("December"), repository.load(DateRange(newYear.minusDays(1), newYear), testZone).meals.map { it.text })
    }

    @Test fun dstDaysQueryTheirTrueInstantBoundaries() = runBlocking {
        for ((day, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val range = DateRange.day(day)
            val event = FastingEvent(start = range.startInstant(testZone), end = range.endInstant(testZone), zone = testZone)
            assertEquals(hours, Duration.between(event.start, event.end).toHours())
            repository.addFasting(event)
            assertEquals(listOf(event), repository.load(range, testZone).fasting)
            assertTrue(repository.load(DateRange.day(day.plusDays(1)), testZone).fasting.isEmpty())
        }
    }

    @Test fun dateQueriesUseRequestedZoneRatherThanDeviceDefault() = runBlocking {
        val event = FastingEvent(start = Instant.parse("2026-10-20T02:00:00Z"), end = Instant.parse("2026-10-20T03:00:00Z"), zone = testZone)
        repository.addFasting(event)
        assertEquals(listOf(event), repository.load(DateRange.day(testDate), testZone).fasting)
        assertTrue(repository.load(DateRange.day(testDate), ZoneOffset.UTC).fasting.isEmpty())
        assertEquals(listOf(event), repository.load(DateRange.day(testDate.plusDays(1)), ZoneOffset.UTC).fasting)
    }

    @Test fun viewModelLoadsEmptyDatabaseThenReactsToPersistedPlansAndDateSelection() = runBlocking {
        val store = ViewModelStore()
        val saved = SavedStateHandle()
        val model = WeekViewModel(repository, saved, Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("week-test", model)
        try {
            assertTrue(withTimeout(10_000) { model.state.first { !it.loading } }.meals.isEmpty())
            repository.addMeal(MealPlan(date = testDate, text = "Persisted lunch"))
            assertEquals("Persisted lunch", withTimeout(10_000) { model.state.first { it.meals.isNotEmpty() } }.meals.single().title)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.selectDay(1) }
            assertTrue(withTimeout(10_000) { model.state.first { it.selectedDay == 1 } }.meals.isEmpty())
            assertEquals(1, saved.get<Int>("selectedDay"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.moveWeek(1) }
            val nextLabel = withTimeout(10_000) { model.state.first { !it.loading && it.days.first().number == "26" } }.weekLabel
            assertTrue(nextLabel.contains("2026"))
            assertEquals(testDate.plusWeeks(1).toEpochDay(), saved.get<Long>("weekStart"))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        }
    }

    @Test fun everyOutcomeRoundTripsAndMissedActivityCanRemainLinked() = runBlocking {
        MealOutcome.entries.forEach { outcome -> repository.addMeal(MealPlan(id = outcome.name, date = testDate, text = "Planned", outcome = outcome, actual = if (outcome == MealOutcome.CHANGED) ActualMeal("Actual") else null)) }
        FastingOutcome.entries.forEach { outcome -> repository.addFasting(FastingEvent(id = outcome.name, start = testRange.startInstant(testZone), end = testRange.endInstant(testZone), zone = testZone, outcome = outcome)) }
        ActivityOutcome.entries.forEach { outcome -> repository.addActivity(ActivityPlan(id = outcome.name, date = testDate, name = "Activity", outcome = outcome)) }
        repository.addActivity(ActivityPlan(id = "new", date = testDate.plusDays(1), name = "Later plan", rescheduledFromId = "MISSED"))
        val loaded = repository.load(testRange, testZone)
        assertEquals(MealOutcome.entries.toSet(), loaded.meals.map { it.outcome }.toSet())
        assertEquals(FastingOutcome.entries.toSet(), loaded.fasting.map { it.outcome }.toSet())
        assertEquals(ActivityOutcome.entries.toSet(), loaded.activities.map { it.outcome }.toSet())
        assertEquals(ActivityOutcome.MISSED, loaded.activities.single { it.id == "MISSED" }.outcome)
        assertEquals("MISSED", loaded.activities.single { it.id == "new" }.rescheduledFromId)
    }

    @Test fun observerEmitsPersistedChangesAndDuplicateIdsCannotOverwriteHistory() = runBlocking {
        assertEquals(PlanningData(), repository.observe(testRange, testZone).first())
        val meal = MealPlan(id = "immutable", date = testDate, text = "Original")
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) { repository.observe(testRange, testZone).first { it.meals.isNotEmpty() } }
        }
        repository.addMeal(meal)
        assertEquals(listOf(meal), observed.await().meals)
        try {
            repository.addMeal(meal.copy(text = "Replacement"))
            fail("Duplicate inserts must fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(listOf(meal), repository.load(testRange, testZone).meals)
    }
}
