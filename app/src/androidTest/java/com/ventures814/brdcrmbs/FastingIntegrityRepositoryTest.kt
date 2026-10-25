package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class FastingIntegrityRepositoryTest {
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private val zone = ZoneId.of("America/Denver")
    private val day = LocalDate.of(2026, 10, 19)
    private fun fast(date: LocalDate = day, hours: Long = 16): FastingEvent {
        val start = date.atTime(19, 0).atZone(zone).toInstant()
        return FastingEvent(start = start, end = start.plus(Duration.ofHours(hours)), zone = zone)
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db)
    }
    @After fun cleanup() { db.close() }
    private suspend fun rejected(operation: suspend () -> Unit) {
        try { operation(); fail("Expected fasting conflict") } catch (_: FastingConflictException) { }
    }
    @Test fun allCreateApisRejectOverlapAndAllowBothExactAdjacencies() = runBlocking {
        val original = fast(); repo.addFasting(original)
        val before = repo.load(DateRange.weekContaining(day), zone)
        rejected { repo.addFasting(original.copy(id = "insert")) }
        rejected { repo.addFastingPlan(original.copy(id = "plan-insert", outcome = FastingOutcome.MISSED)) }
        assertNull(repo.getFastingPlan("insert")); assertNull(repo.getFastingPlan("plan-insert"))
        assertEquals(before, repo.load(DateRange.weekContaining(day), zone))
        repo.addFasting(original.copy(id = "before", start = original.start.minusSeconds(3600), end = original.start))
        repo.addFasting(original.copy(id = "after", start = original.end, end = original.end.plusSeconds(3600)))
        assertEquals(3, repo.load(DateRange.weekContaining(day), zone).fasting.size)
    }
    @Test fun editExcludesSelfButConflictAndKindConversionRollBack() = runBlocking {
        val first = fast(); val second = fast(day.plusDays(2))
        repo.addFasting(first); repo.addFasting(second)
        val edited = first.copy(start = first.start.plusSeconds(60))
        repo.updateFastingPlan(edited); assertEquals(edited, repo.getFastingPlan(first.id))
        rejected { repo.updateFastingPlan(edited.copy(end = second.start.plusSeconds(60))) }
        assertEquals(edited, repo.getFastingPlan(first.id)); assertEquals(second, repo.getFastingPlan(second.id))
        val none = NoFastPlan(date = day.plusDays(4), zone = zone)
        repo.addFastingPlan(none)
        rejected { repo.updateFastingPlan(first.copy(end = fast(day.plusDays(4)).end)) }
        rejected { repo.updateFastingPlan(none.copy(date = day)) }
        rejected { repo.updateFastingPlan(second.copy(id = none.id)) }
        assertEquals(none, repo.getFastingPlan(none.id)); assertEquals(edited, repo.getFastingPlan(first.id))
    }
    @Test fun noFastBothDirectionsMultiDayAndMidnightBoundaries() = runBlocking {
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val first = fast().copy(end = midnight)
        repo.addFasting(first)
        val none = NoFastPlan(date = day.plusDays(1), zone = zone)
        repo.addFastingPlan(none)
        rejected { repo.addFastingPlan(none.copy(id = "occupied", date = day)) }
        rejected { repo.addFasting(first.copy(id = "midnight", start = midnight, end = midnight.plusSeconds(60))) }
        rejected { repo.addFasting(first.copy(id = "multi", start = midnight.minus(Duration.ofDays(2)), end = midnight.plus(Duration.ofDays(2)))) }
        assertEquals(listOf(none), repo.load(DateRange.weekContaining(day), zone).noFast)
    }
    @Test fun concurrentRepositoryInstancesCannotBothInsertOverlaps() = runBlocking {
        val event = fast()
        val results = (0..7).map { index -> async(Dispatchers.IO) {
            runCatching { RoomPlanningRepository(db).addFasting(event.copy(id = "race-$index")) }
        } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(7, results.count { it.exceptionOrNull() is FastingConflictException })
        assertEquals(1, repo.load(DateRange.weekContaining(day), zone).fasting.size)
    }
    @Test fun copyDestinationsSkipConflictsIndependentlyAndRetainMealsActivitiesAndDuplicates() = runBlocking {
        val source = fast(); repo.addFasting(source)
        repo.addMeal(MealPlan(date = day, text = "Meal")); repo.addActivity(ActivityPlan(date = day, name = "Walk"))
        val target = day.plusDays(2); val free = day.plusDays(4)
        val occupied = fast(target).copy(outcome = FastingOutcome.DONE)
        repo.addFasting(occupied)
        assertEquals(CopyResult(2, 0, 1), repo.copyDay(day, setOf(target), zone))
        assertEquals(occupied, repo.getFastingPlan(occupied.id))
        assertEquals(CopyResult(3, 2, 1), repo.copyDay(day, setOf(target, free), zone))
        assertEquals(CopyResult(0, 3), repo.copyDay(day, setOf(free), zone))
        assertEquals(1, repo.load(DateRange.day(target), zone).meals.size)
        assertEquals(1, repo.load(DateRange.day(target), zone).activities.size)
    }
    @Test fun repeatWeekAndCopiedNoFastRespectConflictsWithoutOverwriting() = runBlocking {
        val source = fast(); repo.addFasting(source)
        repo.addMeal(MealPlan(date = day, text = "Meal")); repo.addActivity(ActivityPlan(date = day, name = "Walk"))
        val target = day.plusWeeks(1)
        val none = NoFastPlan(date = target, zone = zone); repo.addFastingPlan(none)
        assertEquals(CopyResult(2, 0, 1), repo.repeatLastWeek(target, zone))
        assertEquals(none, repo.getFastingPlan(none.id))
        assertEquals(CopyResult(2, 0, 1), repo.copyDay(target, setOf(day.plusDays(1)), zone))
        assertEquals(source, repo.getFastingPlan(source.id))
    }
    @Test fun historicalOverlapsRemainReadableAndNoRepairOccurs() = runBlocking {
        val first = fast(); val second = first.copy(id = "legacy", outcome = FastingOutcome.MISSED)
        db.insertHistoricalFasting(first); db.insertHistoricalFasting(second)
        assertEquals(setOf(first, second), repo.load(DateRange.day(day), zone).fasting.toSet())
        rejected { repo.addFasting(first.copy(id = "new")) }
        assertEquals(setOf(first, second), repo.load(DateRange.day(day), zone).fasting.toSet())
        assertEquals(4, db.openHelper.writableDatabase.version)
    }
}
