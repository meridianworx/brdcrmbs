package com.ventures814.brdcrmbs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.week.*
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class MealUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: WeekViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(database)
        model = WeekViewModel(repo, SavedStateHandle(), Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("meal-ui", model)
        compose.setContent { BrdcrmbsTheme { Surface { WeekRoute(Modifier.fillMaxSize(), model) } } }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; database.close() }
    private fun add() {
        compose.onAllNodesWithText("+ Add")[1].performScrollTo().performClick()
        compose.waitUntil(10_000) { model.mealEditor.value != null && !model.mealLoading.value }
    }
    private fun save() {
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.mealEditor.value == null && !model.mealBusy.value }
    }
    private fun edit(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitUntil(10_000) { model.mealEditor.value != null && !model.mealLoading.value }
    }

    @Test fun addSaveEditConfirmedDeleteAndSelectReusableMeal() {
        add()
        compose.onNodeWithText("Add Meal").assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mealText").performScrollTo().performTextInput("Soup")
        compose.onNodeWithTag("mealLabel").performScrollTo().performTextInput("First meal")
        compose.onNodeWithTag("mealNotes").performScrollTo().performTextInput("Bring spoon")
        compose.onNodeWithTag("mealSaveForReuse").performScrollTo().performClick()
        save()
        compose.waitUntil(10_000) { model.state.value.meals.size == 1 }
        compose.onNodeWithText("Meals (1)").assertExists()
        edit("Soup")
        compose.onNodeWithText("Edit Meal").assertIsDisplayed()
        compose.onNodeWithTag("mealText").performScrollTo().performTextReplacement("Stew")
        save()
        compose.waitUntil(10_000) { model.state.value.meals.singleOrNull()?.title == "Stew" }
        edit("Stew")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertEquals(1, repo.load(testRange, testZone).meals.size) }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithTag("confirmDeleteMeal").performClick()
        compose.waitUntil(10_000) { model.mealEditor.value == null && model.state.value.meals.isEmpty() }
        add()
        compose.onNodeWithText("Choose Saved Meal").performClick()
        compose.onNodeWithText("Soup").performClick()
        compose.onNodeWithTag("mealText").assertTextContains("Soup")
        compose.onNodeWithTag("mealLabel").assertTextContains("First meal")
        compose.onNodeWithTag("mealNotes").assertTextContains("Bring spoon")
        runBlocking { assertTrue(repo.load(testRange, testZone).meals.isEmpty()) }
        save()
        compose.waitUntil(10_000) { model.state.value.meals.size == 1 }
        runBlocking { assertEquals(1, repo.savedMeals().size) }
    }

    @Test fun backAndPickerCancelDoNotSaveAndOptionalTimeCanBeAddedAndRemoved() {
        add()
        val original = model.mealEditor.value
        compose.onNodeWithTag("mealDate").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(original, model.mealEditor.value)
        compose.onNodeWithTag("mealTime").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(model.mealEditor.value!!.time)
        compose.onNodeWithTag("mealTime").performClick()
        compose.onNodeWithText("OK").performClick()
        assertNotNull(model.mealEditor.value!!.time)
        compose.onNodeWithText("Remove time").performScrollTo().performClick()
        assertNull(model.mealEditor.value!!.time)
        compose.onNodeWithTag("mealText").performScrollTo().performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Back to Week").performScrollTo().performClick()
        compose.onNodeWithText("My Week").assertIsDisplayed()
        runBlocking { assertTrue(repo.load(testRange, testZone).meals.isEmpty()); assertTrue(repo.savedMeals().isEmpty()) }
    }
}
