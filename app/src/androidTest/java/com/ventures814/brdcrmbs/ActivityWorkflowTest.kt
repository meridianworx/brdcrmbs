package com.ventures814.brdcrmbs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class ActivityWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "activity-workflow-${UUID.randomUUID()}.db"
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private val store = ViewModelStore()
    private fun open() {
        database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build()
        repo = RoomPlanningRepository(database)
    }
    @Before fun setup() = open()
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        database.close(); context.deleteDatabase(name)
    }
    @Test fun multipleTypesReloadIdentityEditDeleteAndSavedDeduplication() = runBlocking {
        val date = LocalDate.of(2026, 12, 31)
        val workout = ActivityPlan(date = date, time = LocalTime.of(17, 0), name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT)
        val walk = ActivityPlan(date = date, name = "20-minute walk", notes = "Outside")
        repo.savePlannedActivity(workout, true)
        repo.savePlannedActivity(walk, true, true)
        repo.savePlannedActivity(walk.copy(id = "tomorrow", date = date.plusDays(1), time = LocalTime.of(20, 0)), true, true)
        database.close(); open()
        assertEquals(workout, repo.getActivity(workout.id)); assertEquals(walk, repo.getActivity(walk.id))
        assertEquals(2, repo.load(DateRange.day(date), testZone).activities.size)
        assertEquals(1, repo.load(DateRange.day(date.plusDays(1)), testZone).activities.size)
        assertEquals(1, repo.savedActivities().size)
        val edited = walk.copy(time = LocalTime.of(20, 0, 30, 123), notes = "Keep precision")
        repo.savePlannedActivity(edited, false)
        assertEquals(edited, repo.getActivity(walk.id))
        assertEquals(2, repo.load(DateRange.day(date), testZone).activities.size)
        repo.deletePlannedActivity(walk.id)
        assertEquals(listOf(workout), repo.load(DateRange.day(date), testZone).activities)
        assertEquals("Outside", repo.savedActivities().single().notes)
    }
    @Test fun historicalOutcomesAndReschedulingLinksRemainProtected() = runBlocking {
        for (outcome in listOf(ActivityOutcome.DONE, ActivityOutcome.MISSED)) {
            val history = ActivityPlan(date = testDate, name = "Historical", outcome = outcome)
            repo.addActivity(history)
            for (operation in listOf<suspend () -> Unit>(
                { repo.savePlannedActivity(history.copy(outcome = ActivityOutcome.PLANNED, name = "Rewrite"), false, true) },
                { repo.deletePlannedActivity(history.id) }
            )) {
                try { operation(); fail("History must be protected") } catch (_: IllegalStateException) { }
                assertEquals(history, repo.getActivity(history.id)); assertTrue(repo.savedActivities().isEmpty())
            }
        }
        val missed = ActivityPlan(date = testDate, name = "Wednesday", outcome = ActivityOutcome.MISSED)
        repo.addActivity(missed)
        val later = ActivityPlan(date = testDate.plusDays(1), name = "Thursday", rescheduledFromId = missed.id)
        repo.addActivity(later) // Existing foundation API can represent future rescheduling; no new reschedule operation.
        repo.savePlannedActivity(later.copy(notes = "New planned notes"), false)
        assertEquals(missed, repo.getActivity(missed.id)); assertEquals(missed.id, repo.getActivity(later.id)!!.rescheduledFromId)
        try { repo.savePlannedActivity(later.copy(rescheduledFromId = null), false); fail("Link cannot be removed") } catch (_: IllegalStateException) { }
        repo.deletePlannedActivity(later.id)
        assertEquals(missed, repo.getActivity(missed.id))
    }
    @Test fun selectedDayReactiveCountsAndSavedSelectionDoNotImplicitlySchedule() = runBlocking {
        val handle = SavedStateHandle()
        val clock = Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone)
        val model = WeekViewModel(repo, handle, clock)
        store.put("activity", model)
        withTimeout(10_000) { model.state.first { !it.loading } }
        instrumentation.runOnMainSync { model.selectDay(2); model.addActivity() }
        withTimeout(10_000) { model.activityLoading.first { !it } }
        assertEquals(testDate.plusDays(2), model.activityEditor.value!!.date)
        instrumentation.runOnMainSync { model.saveActivity() }
        assertNotNull(model.activityError.value)
        assertTrue(repo.load(testRange, testZone).activities.isEmpty())
        instrumentation.runOnMainSync { model.updateActivityDraft { it.copy(name = "Walk", saveForReuse = true) }; model.saveActivity() }
        val first = withTimeout(10_000) { model.state.first { it.activities.size == 1 } }.activities.single()
        withTimeout(10_000) { model.activityBusy.first { !it } }
        assertNull(first.time); assertEquals(ActivityOutcome.PLANNED, repo.getActivity(first.planId!!)!!.outcome)
        instrumentation.runOnMainSync { model.addActivity() }
        withTimeout(10_000) { model.activityLoading.first { !it } }
        instrumentation.runOnMainSync { model.updateActivityDraft { it.select(model.savedActivities.value.single()).copy(name = "Second walk", time = LocalTime.NOON) } }
        assertEquals(1, repo.load(testRange, testZone).activities.size)
        val restored = WeekViewModel(repo, SavedStateHandle(mapOf("activityDraft" to model.activityEditor.value)), clock)
        store.put("restored", restored)
        withTimeout(10_000) { restored.activityLoading.first { !it } }
        assertEquals(model.activityEditor.value, restored.activityEditor.value)
        instrumentation.runOnMainSync { model.saveActivity() }
        withTimeout(10_000) { model.state.first { it.activities.size == 2 } }
        withTimeout(10_000) { model.activityBusy.first { !it } }
        instrumentation.runOnMainSync { model.editActivity(first.planId!!) }
        withTimeout(10_000) { model.activityLoading.first { !it } }
        instrumentation.runOnMainSync { model.updateActivityDraft { it.copy(name = "Edited") }; model.saveActivity() }
        withTimeout(10_000) { model.activityBusy.first { !it } }
        assertEquals("Edited", repo.getActivity(first.planId!!)!!.name)
        assertEquals(2, repo.load(testRange, testZone).activities.size)
        instrumentation.runOnMainSync { model.editActivity(first.planId!!) }
        withTimeout(10_000) { model.activityLoading.first { !it } }
        instrumentation.runOnMainSync { model.deleteActivity() }
        withTimeout(10_000) { model.state.first { it.activities.size == 1 } }
        assertNull(repo.getActivity(first.planId!!))
    }
}
