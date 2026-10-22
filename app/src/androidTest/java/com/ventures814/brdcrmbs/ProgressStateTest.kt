package com.ventures814.brdcrmbs

import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.progress.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class ProgressStateTest {
    private val date = LocalDate.of(2026, 12, 31)
    private val clock = object : Clock() {
        var now = date.atTime(12, 0).atZone(testZone).toInstant()
        override fun instant() = now
        override fun getZone() = testZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
    private lateinit var db: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private val store = ViewModelStore()
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build(); repo = RoomPlanningRepository(db, clock) }
    @After fun cleanup() { main { store.clear() }; db.close() }

    @Test fun oneMonthlyObservationSupportsDateSelectionReactiveOutcomesAndOriginalDetails() = runBlocking {
        var observations = 0
        val counted = object : PlanningRepository by repo {
            override fun observe(range: DateRange, zone: ZoneId): Flow<PlanningData> {
                observations++; assertEquals(DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)), range)
                return repo.observe(range, zone)
            }
        }
        val meal = MealPlan(date = date, text = "Original"); repo.addMeal(meal)
        val model = ProgressViewModel(counted, SavedStateHandle(), clock); store.put("model", model)
        val collector = launch { model.state.collect() }
        try {
            withTimeout(10_000) { model.state.first { !it.loading } }
            main { model.selectDate(date.minusDays(1)) }
            withTimeout(10_000) { model.state.first { it.context.selected == date.minusDays(1) } }
            main { model.selectDate(date) }
            withTimeout(10_000) { model.state.first { it.selectedDay?.plans?.meals?.size == 1 } }
            assertEquals(1, observations)
            repo.recordTodayMeal(meal.id, MealOutcome.CHANGED, AdherenceDay(date, testZone), ActualMeal("Actual"))
            val changed = withTimeout(10_000) { model.state.first { it.selectedDay?.meals == ReviewStatus.CHANGED } }
            assertEquals("Original", changed.selectedDay!!.plans.meals.single().text)
            assertEquals("Actual", changed.selectedDay!!.plans.meals.single().actual!!.text)
            assertEquals(0, changed.review!!.meals.percent); assertEquals(1, observations)
        } finally { collector.cancelAndJoin() }
    }
    @Test fun browsingRestorationYearBoundaryAndResetReadFreshDateWithoutWriting() = runBlocking {
        val start = date.minusDays(1).atTime(19, 0).atZone(testZone).toInstant()
        val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(72)), zone = testZone)
        repo.addFasting(fast)
        val saved = SavedStateHandle(); val model = ProgressViewModel(repo, saved, clock); store.put("model", model)
        assertEquals(date, withTimeout(10_000) { model.state.first { !it.loading } }.context.selected)
        main { model.moveMonth(1) }
        val january = withTimeout(10_000) { model.state.first { !it.loading && it.context.month == YearMonth.of(2027, 1) } }
        assertEquals(listOf(fast), january.selectedDay!!.plans.fasting)
        main { model.selectDate(LocalDate.of(2027, 1, 2)) }
        val recreated = ProgressViewModel(repo, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }), clock); store.put("recreated", recreated)
        assertEquals(LocalDate.of(2027, 1, 2), withTimeout(10_000) { recreated.state.first { !it.loading } }.context.selected)
        clock.now = LocalDate.of(2027, 1, 1).atTime(12, 0).atZone(testZone).toInstant()
        main { recreated.resetToToday() }
        assertEquals(LocalDate.of(2027, 1, 1), withTimeout(10_000) { recreated.state.first { !it.loading && it.context.selected == LocalDate.of(2027, 1, 1) } }.context.selected)
        assertEquals(fast, repo.getFastingPlan(fast.id))
    }
    @Test fun readFailureIsNotAnEmptySuccessAndRetryRecovers() = runBlocking {
        var fail = true
        val flaky = object : PlanningRepository by repo {
            override fun observe(range: DateRange, zone: ZoneId): Flow<PlanningData> = if (fail) flow { throw IllegalStateException("Read failure") } else repo.observe(range, zone)
        }
        val model = ProgressViewModel(flaky, SavedStateHandle(), clock); store.put("failure", model)
        assertTrue(withTimeout(10_000) { model.state.first { it.failed } }.failed)
        fail = false; main { model.refresh() }
        val loaded = withTimeout(10_000) { model.state.first { !it.failed && !it.loading } }
        assertNull(loaded.review!!.meals.percent)
    }
}
