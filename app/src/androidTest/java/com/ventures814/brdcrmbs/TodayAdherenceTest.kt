package com.ventures814.brdcrmbs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.today.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class TodayAdherenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "today-${UUID.randomUUID()}.db"
    private val day = LocalDate.of(2026, 10, 21)
    private val clock = object : Clock() {
        var now = day.atTime(12, 0).atZone(testZone).toInstant()
        override fun instant() = now
        override fun getZone() = testZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private val store = ViewModelStore()
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private val today get() = AdherenceDay(day, testZone)
    private fun open() { database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build(); repo = RoomPlanningRepository(database, clock) }
    @Before fun setup() = open()
    @After fun cleanup() { main { store.clear() }; database.close(); context.deleteDatabase(name) }

    @Test fun todayFiltersDatesAndLoadsCrossMidnightAndContinuousFastsAndNoFast() = runBlocking {
        repo.addMeal(MealPlan(date = day, text = "Today")); repo.addMeal(MealPlan(date = day.plusDays(1), text = "Tomorrow"))
        repo.addActivity(ActivityPlan(date = day.minusDays(1), name = "Yesterday"))
        val start = day.minusDays(1).atTime(19, 0).atZone(testZone).toInstant()
        val cross = FastingEvent(start = start, end = start.plus(Duration.ofHours(16)), zone = testZone)
        val multi = FastingEvent(start = start, end = start.plus(Duration.ofHours(72)), zone = testZone)
        repo.addFasting(cross); repo.addFasting(multi); repo.addFastingPlan(NoFastPlan(date = day, zone = testZone))
        val model = TodayViewModel(repo, SavedStateHandle(), clock); store.put("today", model)
        val loaded = withTimeout(10_000) { model.state.first { !it.loading } }
        assertEquals(today, loaded.day); assertEquals(listOf("Today"), loaded.plans.meals.map { it.text })
        assertTrue(loaded.plans.activities.isEmpty()); assertEquals(setOf(cross, multi), loaded.plans.fasting.toSet())
        assertEquals(1, loaded.plans.noFast.size)
        assertEquals(1, loaded.plans.fasting.count { it.id == multi.id })
    }

    @Test fun outcomesPreservePlansIdentitiesActualSeparationLinkageAndLibrariesAfterReopen() = runBlocking {
        repo.savePlannedMeal(MealPlan(date = day.plusDays(1), text = "Reusable"), true, true)
        repo.savePlannedActivity(ActivityPlan(date = day.plusDays(1), name = "Reusable activity"), true, true)
        val savedMeals = repo.savedMeals(); val savedActivities = repo.savedActivities()
        for (hours in listOf(16L, 24L, 48L, 72L, 211L)) {
            val start = day.atTime(19, 0).atZone(testZone).toInstant()
            val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(hours)), zone = testZone, preset = "Custom", notes = "Keep")
            repo.addFasting(fast)
            for (outcome in listOf(FastingOutcome.DONE, FastingOutcome.MISSED, FastingOutcome.DONE)) {
                repo.recordTodayFasting(fast.id, outcome, today)
                assertEquals(fast.copy(outcome = outcome), repo.getFastingPlan(fast.id))
            }
        }
        val meal = MealPlan(date = day, text = "Planned salmon", time = LocalTime.of(18, 0, 10, 123), label = "Dinner", notes = "Original notes")
        val other = MealPlan(date = day, text = "Untimed")
        repo.addMeal(meal); repo.addMeal(other)
        repo.recordTodayMeal(meal.id, MealOutcome.DONE, today)
        repo.recordTodayMeal(other.id, MealOutcome.MISSED, today)
        assertEquals(other.copy(outcome = MealOutcome.MISSED), repo.getMeal(other.id))
        val actual = ActualMeal("Pizza", LocalTime.of(19, 30, 15, 456), "Dinner", "Actual notes")
        repo.recordTodayMeal(meal.id, MealOutcome.CHANGED, today, actual)
        assertEquals(meal.copy(outcome = MealOutcome.CHANGED, actual = actual), repo.getMeal(meal.id))
        val corrected = actual.copy(text = "Soup", time = null, label = null)
        repo.recordTodayMeal(meal.id, MealOutcome.CHANGED, today, corrected)
        repo.recordTodayMeal(meal.id, MealOutcome.DONE, today)
        assertEquals(meal.copy(outcome = MealOutcome.DONE, actual = corrected), repo.getMeal(meal.id))
        repo.recordTodayMeal(meal.id, MealOutcome.MISSED, today)
        assertEquals(meal.copy(outcome = MealOutcome.MISSED, actual = corrected), repo.getMeal(meal.id))
        repo.recordTodayMeal(meal.id, MealOutcome.CHANGED, today, corrected)
        val missed = ActivityPlan(date = day.minusDays(1), name = "Original missed", outcome = ActivityOutcome.MISSED)
        repo.addActivity(missed)
        for (type in ActivityType.entries) {
            val activity = ActivityPlan(date = day, name = type.name, type = type, time = LocalTime.NOON, notes = "Keep", rescheduledFromId = missed.id)
            repo.addActivity(activity)
            for (outcome in listOf(ActivityOutcome.DONE, ActivityOutcome.MISSED)) {
                repo.recordTodayActivity(activity.id, outcome, today)
                assertEquals(activity.copy(outcome = outcome), repo.getActivity(activity.id))
            }
        }
        assertEquals(missed, repo.getActivity(missed.id))
        val snapshot = repo.load(DateRange.day(day), testZone)
        database.close(); open()
        assertEquals(snapshot, repo.load(DateRange.day(day), testZone))
        assertEquals(savedMeals, repo.savedMeals()); assertEquals(savedActivities, repo.savedActivities())
        assertEquals(corrected, repo.getMeal(meal.id)!!.actual)
    }

    @Test fun staleUnrelatedAndInvalidTransitionsFailWithoutChangingData() = runBlocking {
        val meal = MealPlan(date = day.plusDays(1), text = "Future")
        val activity = ActivityPlan(date = day.minusDays(1), name = "Past")
        val end = day.atStartOfDay(testZone).toInstant()
        val fast = FastingEvent(start = end.minusSeconds(3600), end = end, zone = testZone)
        repo.addMeal(meal); repo.addActivity(activity); repo.addFasting(fast)
        suspend fun rejected(block: suspend () -> Unit) {
            try { block(); fail("Invalid context/transition must fail") } catch (_: IllegalArgumentException) { }
        }
        rejected { repo.recordTodayMeal(meal.id, MealOutcome.DONE, today) }
        rejected { repo.recordTodayActivity(activity.id, ActivityOutcome.DONE, today) }
        rejected { repo.recordTodayFasting(fast.id, FastingOutcome.DONE, today) }
        val current = MealPlan(date = day, text = "Current"); repo.addMeal(current)
        rejected { repo.recordTodayMeal(current.id, MealOutcome.CHANGED, today) }
        rejected { repo.recordTodayMeal(current.id, MealOutcome.PLANNED, today) }
        rejected { repo.recordTodayMeal(current.id, MealOutcome.DONE, today, ActualMeal("Invalid")) }
        clock.now = day.plusDays(1).atStartOfDay(testZone).toInstant()
        rejected { repo.recordTodayMeal(current.id, MealOutcome.DONE, today) }
        assertEquals(current, repo.getMeal(current.id)); assertEquals(meal, repo.getMeal(meal.id))
        assertEquals(activity, repo.getActivity(activity.id)); assertEquals(fast, repo.getFastingPlan(fast.id))
    }

    @Test fun viewModelIsReactiveRestoresActualDraftAndRefreshesAtLocalMidnight() = runBlocking {
        val meal = MealPlan(date = day, text = "Original")
        repo.addMeal(meal); repo.addMeal(MealPlan(date = day.plusDays(1), text = "Tomorrow"))
        val saved = SavedStateHandle()
        val model = TodayViewModel(repo, saved, clock); store.put("model", model)
        withTimeout(10_000) { model.state.first { !it.loading } }
        main { model.meal(meal.id, MealOutcome.DONE) }
        withTimeout(10_000) { model.state.first { it.plans.meals.singleOrNull()?.outcome == MealOutcome.DONE } }
        main { model.openActual(meal.id) }
        withTimeout(10_000) { model.draft.first { it != null } }
        main { model.saveActual() }; assertNotNull(model.error.value)
        assertEquals(MealOutcome.DONE, repo.getMeal(meal.id)!!.outcome)
        main { model.changeActual { it.copy(text = "Actual", notes = "Notes") }; model.saveActual() }
        withTimeout(10_000) { model.busy.first { !it } }
        val changed = withTimeout(10_000) { model.state.first { it.plans.meals.singleOrNull()?.outcome == MealOutcome.CHANGED } }
        assertEquals("Actual", changed.plans.meals.single().actual!!.text)
        main { model.openActual(meal.id) }
        withTimeout(10_000) { model.draft.first { it != null } }
        main { model.changeActual { it.copy(text = "Unsaved correction") } }
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated = TodayViewModel(repo, restored, clock); store.put("restored", recreated)
        withTimeout(10_000) { recreated.original.first { it != null } }
        assertEquals("Unsaved correction", recreated.draft.value!!.text)
        assertEquals("Original", recreated.original.value!!.text)
        assertEquals("Actual", repo.getMeal(meal.id)!!.actual!!.text)
        clock.now = day.plusDays(1).atStartOfDay(testZone).toInstant()
        main { recreated.refresh() }
        val tomorrow = withTimeout(10_000) { recreated.state.first { !it.loading && it.day.date == day.plusDays(1) } }
        assertEquals(listOf("Tomorrow"), tomorrow.plans.meals.map { it.text }); assertNull(recreated.draft.value)
    }

    @Test fun dstAndYearBoundaryContextsUseTheRealLocalDay() = runBlocking {
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))) {
            clock.now = date.atTime(12, 0).atZone(testZone).toInstant()
            val context = AdherenceDay(date, testZone)
            val range = DateRange.day(date)
            val fast = FastingEvent(start = range.startInstant(testZone), end = range.endInstant(testZone), zone = testZone)
            val meal = MealPlan(date = date, text = "Local meal")
            repo.addFasting(fast); repo.addMeal(meal)
            repo.recordTodayFasting(fast.id, FastingOutcome.DONE, context)
            repo.recordTodayMeal(meal.id, MealOutcome.DONE, context)
            assertEquals(fast.copy(outcome = FastingOutcome.DONE), repo.getFastingPlan(fast.id))
            assertEquals(listOf(meal.copy(outcome = MealOutcome.DONE)), repo.load(range, testZone).meals)
        }
    }

    @Test fun persistenceFailureRetainsActualEntriesAndOriginalPlan() = runBlocking {
        val meal = MealPlan(date = day, text = "Keep original"); repo.addMeal(meal)
        val failing = object : PlanningRepository by repo {
            override suspend fun recordTodayMeal(id: String, outcome: MealOutcome, day: AdherenceDay, actual: ActualMeal?) {
                throw android.database.sqlite.SQLiteException("Test write failure")
            }
        }
        val model = TodayViewModel(failing, SavedStateHandle(), clock); store.put("failure", model)
        main { model.openActual(meal.id) }
        withTimeout(10_000) { model.draft.first { it != null } }
        main { model.changeActual { it.copy(text = "Keep entries", notes = "Notes") }; model.saveActual() }
        withTimeout(10_000) { model.busy.first { !it } }
        assertNotNull(model.error.value); assertEquals("Keep entries", model.draft.value!!.text)
        assertEquals(meal, repo.getMeal(meal.id))
    }
}
