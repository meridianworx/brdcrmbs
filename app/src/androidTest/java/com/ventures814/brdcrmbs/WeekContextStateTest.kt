package com.ventures814.brdcrmbs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.week.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class WeekContextStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository
    private val store = ViewModelStore()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PlanningDatabase::class.java).build()
        repository = RoomPlanningRepository(database)
    }
    @After fun cleanup() { main { store.clear() }; database.close() }

    @Test fun resetReadsLocalDateAgainAcrossMidnightYearAndWeekBoundaryWithoutWrites() = runBlocking {
        val clock = object : Clock() {
            var now = Instant.parse("2027-01-01T02:00:00Z")
            override fun instant() = now
            override fun getZone() = testZone
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        }
        val saved = SavedStateHandle()
        val model = WeekViewModel(repository, saved, clock); store.put("dates", model)
        val meal = MealPlan(date = LocalDate.of(2027, 1, 5), text = "Keep future plan")
        repository.addMeal(meal)
        main { model.resetToToday() }
        assertEquals(LocalDate.of(2026, 12, 28).toEpochDay(), saved.get<Long>("weekStart"))
        assertEquals(3, saved.get<Int>("selectedDay")) // December 31 in Denver, January 1 in UTC.
        clock.now = Instant.parse("2027-01-01T08:00:00Z")
        main { model.resetToToday() }; assertEquals(4, saved.get<Int>("selectedDay"))
        clock.now = Instant.parse("2027-01-04T08:00:00Z")
        main { model.resetToToday() }
        assertEquals(LocalDate.of(2027, 1, 4).toEpochDay(), saved.get<Long>("weekStart"))
        assertEquals(0, saved.get<Int>("selectedDay"))
        assertEquals(meal, repository.getMeal(meal.id))
    }

    @Test fun lateCopyCannotRestoreClearedFeedbackAndSavedStateCannotRestoreResults() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val delayedRepository = object : PlanningRepository by repository {
            override suspend fun copyDay(source: LocalDate, destinations: Set<LocalDate>, zone: ZoneId): CopyResult {
                started.complete(Unit); finish.await()
                return repository.copyDay(source, destinations, zone)
            }
        }
        val saved = SavedStateHandle()
        val clock = Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone)
        val model = WeekViewModel(delayedRepository, saved, clock); store.put("late", model)
        repository.addMeal(MealPlan(date = testDate, text = "Keep copied data"))
        main {
            model.openShortcut(Shortcut.COPY_DAY)
            model.changeShortcut { it.toggle(testDate.plusDays(1)) }; model.copyPlans()
        }
        withTimeout(10_000) { started.await() }
        main { model.clearCopyFeedback() }
        finish.complete(Unit)
        withTimeout(10_000) { model.copyBusy.first { !it } }
        assertNull(model.copyMessage.value)
        assertEquals(1, repository.load(DateRange.day(testDate.plusDays(1)), testZone).meals.size)
        main {
            model.openShortcut(Shortcut.COPY_DAY)
            model.changeShortcut { it.toggle(testDate.plusDays(1)) }; model.copyPlans()
        }
        withTimeout(10_000) { model.copyBusy.first { !it } }
        assertNotNull(model.copyMessage.value)
        assertFalse(saved.keys().contains("copyMessage"))
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val recreated = WeekViewModel(repository, restored, clock); store.put("recreated", recreated)
        assertNull(recreated.copyMessage.value)
        assertEquals(1, repository.load(DateRange.day(testDate.plusDays(1)), testZone).meals.size)
    }

    @Test fun arrowsFromEveryWeekdayChooseMondayAcrossYearBoundaryWithoutWrites() = runBlocking {
        val saved = SavedStateHandle()
        val clock = Clock.fixed(Instant.parse("2027-01-01T20:00:00Z"), testZone)
        val model = WeekViewModel(repository, saved, clock); store.put("arrows", model)
        val range = DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 1))
        repository.addMeal(MealPlan(date = LocalDate.of(2026, 12, 30), text = "History"))
        repository.addMeal(MealPlan(date = LocalDate.of(2027, 1, 6), text = "Future"))
        val original = repository.load(range, testZone)
        for (day in 0..6) for (offset in listOf(-1L, 1L)) {
            main {
                model.resetToToday()
                model.selectDay(day)
                model.copyMessage.value = "Previous result"
                model.moveWeek(offset)
            }
            assertEquals(LocalDate.of(2026, 12, 28).plusWeeks(offset).toEpochDay(), saved.get<Long>("weekStart"))
            assertEquals(0, saved.get<Int>("selectedDay"))
            assertNull(model.copyMessage.value)
            main { model.moveWeek(-offset) }
            assertEquals(LocalDate.of(2026, 12, 28).toEpochDay(), saved.get<Long>("weekStart"))
            assertEquals(0, saved.get<Int>("selectedDay"))
        }
        main { model.resetToToday() }; assertEquals(4, saved.get<Int>("selectedDay"))
        assertEquals(original, repository.load(range, testZone))
    }
}
