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

class MealWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "meal-workflow-${UUID.randomUUID()}.db"
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository
    private val store = ViewModelStore()
    private fun open() {
        database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).build()
        repository = RoomPlanningRepository(database)
    }
    @Before fun setup() = open()
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        database.close(); context.deleteDatabase(name)
    }

    @Test fun createReloadEditWithoutDuplicateDeleteOnlySelectedAndKeepReusableMeal() = runBlocking {
        val meal = MealPlan(date = LocalDate.of(2026, 12, 31), text = "Soup", label = "First meal", notes = "Bring spoon")
        val other = meal.copy(id = "other", date = LocalDate.of(2027, 1, 1), time = LocalTime.of(13, 20, 30, 123))
        repository.savePlannedMeal(meal, true, true)
        repository.savePlannedMeal(other, true, true)
        assertEquals(1, repository.savedMeals().size)
        database.close(); open()
        assertEquals(meal, repository.getMeal(meal.id)); assertEquals(other, repository.getMeal(other.id))
        assertNull(repository.getMeal(meal.id)!!.time)
        assertEquals(listOf(meal), repository.load(DateRange.day(meal.date), testZone).meals)
        assertEquals(listOf(other), repository.load(DateRange.day(other.date), testZone).meals)
        val edited = meal.copy(text = "Stew", date = other.date, time = LocalTime.of(18, 0), label = null, notes = null)
        repository.savePlannedMeal(edited, false)
        assertEquals(edited, repository.getMeal(meal.id))
        assertEquals(2, repository.load(DateRange.day(other.date), testZone).meals.size)
        repository.deletePlannedMeal(meal.id)
        assertNull(repository.getMeal(meal.id)); assertEquals(other, repository.getMeal(other.id))
        assertEquals(SavedMeal(repository.savedMeals().single().id, "Soup", "First meal", "Bring spoon"), repository.savedMeals().single())
    }

    @Test fun recordedMealsAndActualDetailsCannotBeOverwrittenOrDeletedByPlanningCrud() = runBlocking {
        for (outcome in MealOutcome.entries.filter { it != MealOutcome.PLANNED }) {
            val original = MealPlan(date = testDate, text = "Original", label = "Lunch", notes = "Plan",
                outcome = outcome, actual = if (outcome == MealOutcome.CHANGED) ActualMeal("Actual", LocalTime.NOON, "Actual label", "Actual notes") else null)
            repository.addMeal(original)
            for (operation in listOf<suspend () -> Unit>(
                { repository.savePlannedMeal(original.copy(text = "Rewrite", outcome = MealOutcome.PLANNED, actual = null), false, true) },
                { repository.deletePlannedMeal(original.id) }
            )) {
                try { operation(); fail("Historical record must be protected") } catch (_: IllegalStateException) { }
                assertEquals(original, repository.getMeal(original.id))
                assertTrue(repository.savedMeals().isEmpty())
            }
        }
        // An editor opened while Planned must also fail safely if history is recorded before Save.
        val stale = MealPlan(date = testDate, text = "Stale plan")
        repository.addMeal(stale)
        repository.recordChangedMeal(stale.id, ActualMeal("Actual later"))
        try { repository.savePlannedMeal(stale, false, true); fail("Stale edit must fail") } catch (_: IllegalStateException) { }
        assertEquals("Actual later", repository.getMeal(stale.id)!!.actual!!.text)
    }

    @Test fun selectedDayCountsAndReusableSelectionStayReactiveWithoutImplicitWrites() = runBlocking {
        val handle = SavedStateHandle()
        val clock = Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone)
        val model = WeekViewModel(repository, handle, clock)
        store.put("meal", model)
        withTimeout(10_000) { model.state.first { !it.loading } }
        instrumentation.runOnMainSync { model.selectDay(2); model.addMeal() }
        withTimeout(10_000) { model.mealLoading.first { !it } }
        assertEquals(testDate.plusDays(2), model.mealEditor.value!!.date)
        instrumentation.runOnMainSync { model.saveMeal() }
        assertNotNull(model.mealError.value)
        assertTrue(repository.load(testRange, testZone).meals.isEmpty())
        instrumentation.runOnMainSync { model.updateMealDraft { it.copy(text = "Eggs", label = "Lunch", notes = "Greens", saveForReuse = true) }; model.saveMeal() }
        val row = withTimeout(10_000) { model.state.first { it.meals.size == 1 } }.meals.single()
        withTimeout(10_000) { model.mealBusy.first { !it } }
        assertNull(row.time)
        assertEquals(MealOutcome.PLANNED, repository.getMeal(row.planId!!)!!.outcome)
        instrumentation.runOnMainSync { model.addMeal() }
        withTimeout(10_000) { model.mealLoading.first { !it } }
        val date = model.mealEditor.value!!.date
        instrumentation.runOnMainSync { model.updateMealDraft { it.select(model.savedMeals.value.single()).copy(time = LocalTime.NOON) } }
        assertEquals(date, model.mealEditor.value!!.date)
        assertEquals(1, repository.load(testRange, testZone).meals.size)
        // A recreated editor keeps entered values; reusable choices reload through the repository.
        val restored = WeekViewModel(repository, SavedStateHandle(mapOf("mealDraft" to model.mealEditor.value)), clock)
        store.put("restored", restored)
        withTimeout(10_000) { restored.mealLoading.first { !it } }
        assertEquals(model.mealEditor.value, restored.mealEditor.value)
        assertEquals(1, restored.savedMeals.value.size)
        instrumentation.runOnMainSync { model.closeMealEditor(); model.editMeal(row.planId!!) }
        withTimeout(10_000) { model.mealLoading.first { !it } }
        instrumentation.runOnMainSync { model.updateMealDraft { it.copy(text = "Edited") }; model.saveMeal() }
        withTimeout(10_000) { model.state.first { it.meals.singleOrNull()?.title == "Edited" } }
        withTimeout(10_000) { model.mealBusy.first { !it } }
        assertEquals(1, repository.load(testRange, testZone).meals.size)
        instrumentation.runOnMainSync { model.editMeal(row.planId!!) }
        withTimeout(10_000) { model.mealLoading.first { !it } }
        instrumentation.runOnMainSync { model.deleteMeal() }
        withTimeout(10_000) { model.state.first { it.meals.isEmpty() } }
        assertNull(repository.getMeal(row.planId!!))
    }
}
