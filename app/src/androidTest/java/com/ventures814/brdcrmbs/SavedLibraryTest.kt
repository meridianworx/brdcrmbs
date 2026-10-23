package com.ventures814.brdcrmbs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.meals.MealDraft
import com.ventures814.brdcrmbs.ui.activities.ActivityDraft
import com.ventures814.brdcrmbs.ui.settings.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class SavedLibraryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val filename = "saved-library-${UUID.randomUUID()}.db"
    private lateinit var db: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private val store = ViewModelStore()
    private fun open() { db = Room.databaseBuilder(context, PlanningDatabase::class.java, filename).build(); repo = RoomPlanningRepository(db) }
    @Before fun setup() = open()
    @After fun cleanup() { instrumentation.runOnMainSync { store.clear() }; db.close(); context.deleteDatabase(filename) }

    @Test fun templateEditDeleteAndReopenLeaveScheduledSnapshotsAndAllHistoryIntact() = runBlocking {
        val meal = SavedMeal(text = "Chicken + vegetables", label = "Lunch", notes = "Original")
        val activity = SavedActivity(name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT, notes = "Reference")
        repo.saveSavedMeal(meal, true); repo.saveSavedActivity(activity, true)
        val scheduledMeal = MealDraft(date = testDate, time = LocalTime.of(12, 0, 5, 123)).select(meal).toPlan()
        val scheduledActivity = ActivityDraft(date = testDate).select(activity).toPlan()
        repo.savePlannedMeal(scheduledMeal, true)
        repo.recordChangedMeal(scheduledMeal.id, ActualMeal("Actual fish", LocalTime.of(13, 0), "Actual", "Actual notes"))
        repo.addMeal(scheduledMeal.copy(id = "planned-meal", date = testDate.plusDays(1)))
        repo.addActivity(scheduledActivity.copy(outcome = ActivityOutcome.MISSED))
        repo.addActivity(scheduledActivity.copy(id = "rescheduled", date = testDate.plusDays(1), rescheduledFromId = scheduledActivity.id))
        val fast = FastingEvent(start = testDate.atTime(19, 0).atZone(testZone).toInstant(), end = testDate.plusDays(2).atTime(19, 0).atZone(testZone).toInstant(), zone = testZone, outcome = FastingOutcome.DONE)
        repo.addFasting(fast); repo.addFastingPlan(NoFastPlan(date = testDate, zone = testZone))
        val history = repo.load(testRange, testZone)
        val editedMeal = meal.copy(text = "Chicken + broccoli", label = null, notes = "New reusable note")
        val editedActivity = activity.copy(name = "Mobility", type = ActivityType.ACTIVITY, notes = null)
        repo.saveSavedMeal(editedMeal, false); repo.saveSavedActivity(editedActivity, false)
        assertEquals(history, repo.load(testRange, testZone))
        db.close(); open()
        assertEquals(listOf(editedMeal), repo.savedMeals()); assertEquals(listOf(editedActivity), repo.savedActivities())
        assertEquals(history, repo.load(testRange, testZone))
        repo.deleteSavedMeal(meal.id); repo.deleteSavedActivity(activity.id)
        assertTrue(repo.savedMeals().isEmpty()); assertTrue(repo.savedActivities().isEmpty())
        assertEquals(history, repo.load(testRange, testZone))
        assertEquals(4, db.openHelper.writableDatabase.version)
    }

    @Test fun libraryFlowsReactToCreateEditDeleteWithoutSchedulingAnything() = runBlocking {
        val mealStates = mutableListOf<List<SavedMeal>>()
        val activityStates = mutableListOf<List<SavedActivity>>()
        val jobs = listOf(launch { repo.observeSavedMeals().collect { mealStates.add(it) } }, launch { repo.observeSavedActivities().collect { activityStates.add(it) } })
        suspend fun await(predicate: () -> Boolean) = withTimeout(20_000) { while (!predicate()) delay(20) }
        try {
            await { mealStates.isNotEmpty() && activityStates.isNotEmpty() }
            val meal = SavedMeal(text = "Soup"); val activity = SavedActivity(name = "Walk", type = ActivityType.ACTIVITY)
            repo.saveSavedMeal(meal, true); repo.saveSavedActivity(activity, true)
            await { mealStates.last() == listOf(meal) && activityStates.last() == listOf(activity) }
            val changed = meal.copy(text = "Salad"); val workout = activity.copy(type = ActivityType.WORKOUT)
            repo.saveSavedMeal(changed, false); repo.saveSavedActivity(workout, false)
            await { mealStates.last() == listOf(changed) && activityStates.last() == listOf(workout) }
            repo.deleteSavedMeal(meal.id); repo.deleteSavedActivity(activity.id)
            await { mealStates.last().isEmpty() && activityStates.last().isEmpty() }
            assertEquals(PlanningData(), repo.load(testRange, testZone))
        } finally { jobs.forEach { it.cancelAndJoin() } }
    }

    @Test fun staleAndDuplicateWritesNeverRecreateOrOverwriteAnotherItem() = runBlocking {
        val meal = SavedMeal(text = "Original"); val other = SavedMeal(text = "Other")
        val activity = SavedActivity(name = "Original", type = ActivityType.WORKOUT)
        repo.saveSavedMeal(meal, true); repo.saveSavedMeal(other, true); repo.saveSavedActivity(activity, true)
        assertTrue(runCatching { repo.saveSavedMeal(meal.copy(text = "Overwrite"), true) }.isFailure)
        assertTrue(runCatching { repo.saveSavedActivity(activity.copy(name = "Overwrite"), true) }.isFailure)
        repo.deleteSavedMeal(meal.id); repo.deleteSavedActivity(activity.id)
        assertTrue(runCatching { repo.saveSavedMeal(meal, false) }.isFailure)
        assertTrue(runCatching { repo.saveSavedActivity(activity, false) }.isFailure)
        assertEquals(listOf(other), repo.savedMeals()); assertTrue(repo.savedActivities().isEmpty())
    }

    @Test fun draftRestorationBackAndFailedSaveRetainUserEntries() = runBlocking {
        val template = SavedMeal(text = "Original")
        repo.saveSavedMeal(template, true)
        val failing = object : PlanningRepository by repo {
            override suspend fun saveSavedMeal(meal: SavedMeal, isNew: Boolean) { error("Storage unavailable") }
        }
        val saved = SavedStateHandle()
        val model = SettingsViewModel(failing, saved); store.put("settings", model)
        instrumentation.runOnMainSync { model.open(SettingsPage.MEALS); model.edit(template); model.change { it.copy(name = "Unsaved", notes = "Keep") }; model.save() }
        withTimeout(20_000) { model.busy.first { !it } }
        assertNotNull(model.error.value); assertEquals("Unsaved", model.draft.value!!.name)
        assertEquals(listOf(template), repo.savedMeals())
        val restored = SettingsViewModel(repo, SavedStateHandle(mapOf("settingsPage" to saved.get<SettingsPage>("settingsPage"), "savedItemDraft" to saved.get<SavedItemDraft>("savedItemDraft"))))
        store.put("restored", restored)
        assertEquals(model.draft.value, restored.draft.value)
        instrumentation.runOnMainSync { restored.back() }
        assertNull(restored.draft.value); assertEquals(SettingsPage.MEALS, restored.page.value)
        instrumentation.runOnMainSync { restored.back() }
        assertEquals(SettingsPage.HOME, restored.page.value)
    }

    @Test fun settingsReadFailureIsExplicitAndRetryRestoresReactiveLists() = runBlocking {
        var broken = true
        val flaky = object : PlanningRepository by repo {
            override fun observeSavedMeals(): Flow<List<SavedMeal>> = flow { if (broken) error("Unavailable") else emitAll(repo.observeSavedMeals()) }
        }
        val model = SettingsViewModel(flaky, SavedStateHandle()); store.put("settings", model)
        val job = launch { model.library.collect() }
        try {
            withTimeout(20_000) { model.library.first { it.failed } }
            broken = false
            instrumentation.runOnMainSync { model.retry() }
            withTimeout(20_000) { model.library.first { !it.loading && !it.failed } }
            val item = SavedActivity(name = "External creation", type = ActivityType.ACTIVITY)
            repo.saveSavedActivity(item, true)
            withTimeout(20_000) { model.library.first { it.activities == listOf(item) } }
        } finally { job.cancelAndJoin() }
        Unit // JUnit requires a void method; the final Flow.first result is not a test return value.
    }
}
