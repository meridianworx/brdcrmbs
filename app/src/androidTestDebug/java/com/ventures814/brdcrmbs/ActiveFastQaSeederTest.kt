package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.*
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.today.progressAt
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ActiveFastQaSeederTest {
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private val zone = ZoneId.of("America/Denver")
    private val clock = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), zone)
    private lateinit var seeder: ProgressQaSeeder
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db); seeder = ProgressQaSeeder(db, clock)
    }
    @After fun cleanup() { db.close() }
    @Test fun activeScenarioHasExactElapsedDurationProgressAndIdempotentOwnership() = runBlocking {
        seeder.seedActiveFast()
        val fast = repo.getFastingPlan(ActiveFastQaOwnership.ID) as FastingEvent
        assertEquals(clock.instant().minus(Duration.ofHours(10)), fast.start)
        assertEquals(clock.instant().plus(Duration.ofHours(6)), fast.end)
        assertEquals(Duration.ofHours(16), Duration.between(fast.start, fast.end))
        assertEquals(zone, fast.zone); assertEquals(FastingOutcome.PLANNED, fast.outcome)
        assertEquals(0.625f, fast.progressAt(clock.instant()).fraction, 0.0001f)
        assertEquals(62, fast.progressAt(clock.instant()).percent) // Accepted helper floors active percentages.
        seeder.seedActiveFast(); assertEquals(fast, repo.getFastingPlan(fast.id))
        seeder.reset(); assertNull(repo.getFastingPlan(fast.id))
    }
    @Test fun conflictRollsBackOwnedReplacementAndNeverDeletesGenuineDataOrLibraries() = runBlocking {
        seeder.seedActiveFast()
        val owned = repo.getFastingPlan(ActiveFastQaOwnership.ID) as FastingEvent
        val laterClock = Clock.offset(clock, Duration.ofDays(2))
        val user = FastingEvent(start = laterClock.instant(), end = laterClock.instant().plusSeconds(3600), zone = zone)
        repo.addFasting(user)
        repo.saveSavedMeal(SavedMeal(text = "Saved"), true)
        repo.saveSavedActivity(SavedActivity(name = "Saved", type = ActivityType.ACTIVITY), true)
        val before = repo.load(DateRange(LocalDate.now(clock).minusDays(2), LocalDate.now(clock).plusDays(5)), zone)
        assertTrue(runCatching { ProgressQaSeeder(db, laterClock).seedActiveFast() }.exceptionOrNull() is FastingConflictException)
        assertEquals(before, repo.load(DateRange(LocalDate.now(clock).minusDays(2), LocalDate.now(clock).plusDays(5)), zone))
        assertEquals(owned, repo.getFastingPlan(owned.id)); assertEquals(user, repo.getFastingPlan(user.id))
        seeder.reset(); assertEquals(user, repo.getFastingPlan(user.id))
        assertEquals(1, repo.savedMeals().size); assertEquals(1, repo.savedActivities().size)
    }
    @Test fun noFastAndUnrelatedProgressScenarioAreNotRemovedToSeedActiveFast() = runBlocking {
        seeder.seed(seeder.currentWeek())
        val before = repo.load(DateRange.weekContaining(LocalDate.now(clock)), zone)
        assertTrue(runCatching { seeder.seedActiveFast() }.exceptionOrNull() is FastingConflictException)
        assertEquals(before, repo.load(DateRange.weekContaining(LocalDate.now(clock)), zone))
        seeder.reset()
        val none = NoFastPlan(date = LocalDate.now(clock), zone = zone); repo.addFastingPlan(none)
        assertTrue(runCatching { seeder.seedActiveFast() }.exceptionOrNull() is FastingConflictException)
        assertEquals(none, repo.getFastingPlan(none.id)); assertNull(repo.getFastingPlan(ActiveFastQaOwnership.ID))
    }
    @Test fun ownershipLookalikesRemainGenuineAndCanonicalSeedAlsoRejectsConflictsAtomically() = runBlocking {
        val user = FastingEvent(id = ActiveFastQaOwnership.ID + "-other", start = clock.instant(), end = clock.instant().plusSeconds(3600), zone = zone)
        repo.addFasting(user)
        assertTrue(runCatching { seeder.seed(seeder.currentWeek()) }.exceptionOrNull() is FastingConflictException)
        assertTrue(repo.load(DateRange.weekContaining(LocalDate.now(clock)), zone).meals.isEmpty())
        seeder.reset(); assertEquals(user, repo.getFastingPlan(user.id))
    }
}
