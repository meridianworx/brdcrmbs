package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.settings.*
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import com.ventures814.brdcrmbs.ui.progress.ProgressViewModel
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: SettingsViewModel
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db); model = SettingsViewModel(repo, SavedStateHandle())
        val clock = Clock.fixed(testDate.atTime(12, 0).atZone(testZone).toInstant(), testZone)
        fun put(value: ViewModel) = owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${value.javaClass.name}", value)
        put(model); put(TodayViewModel(repo, SavedStateHandle(), clock)); put(WeekViewModel(repo, SavedStateHandle(), clock)); put(ProgressViewModel(repo, SavedStateHandle(), clock))
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; db.close() }
    private fun show(app: Boolean = false) {
        compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            Surface { if (app) BrdcrmbsApp() else SettingsScreen(model = model) }
        } } }
        if (app) compose.onNode(isSelectable() and hasText("Settings")).performClick()
        compose.waitUntil(30_000) { !model.library.value.loading && !model.library.value.failed }
    }
    private fun text(value: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(value: String) { text(value); compose.onNodeWithText(value).performScrollTo().performClick() }
    private fun save() { click("Save"); compose.waitUntil(30_000) { model.draft.value == null && !model.busy.value } }
    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    @Test fun settingsGroupsEmptyLibrariesBackAndBottomNavigationWork() {
        show(app = true)
        for (tab in listOf("Today", "Week", "Progress", "Settings")) compose.onNode(isSelectable() and hasText(tab)).assertExists()
        compose.onNodeWithText("Saved Meals").assertIsDisplayed()
        click("Saved Meals")
        compose.onNodeWithText("No saved meals yet.").assertIsDisplayed()
        compose.onNode(isSelectable() and hasText("Settings")).assertDoesNotExist()
        back()
        compose.onNode(isSelectable() and hasText("Settings")).assertIsSelected()
        click("Saved Activities")
        compose.onNodeWithText("No saved activities yet.").assertIsDisplayed()
        back()
        compose.onNodeWithText("Backup & Restore").performScrollTo().assertExists()
        compose.onAllNodesWithText("Coming in a later milestone").assertCountEquals(5)
        compose.onNodeWithText("About").performScrollTo().assertHasNoClickAction()
        assertEquals(SettingsPage.HOME, model.page.value)
    }

    @Test fun savedMealEditConfirmedDeleteAndCancelPreserveChangedScheduledMeal() {
        val planned = MealPlan(date = testDate, text = "Chicken + vegetables", label = "Lunch", notes = "Original note")
        runBlocking { repo.savePlannedMeal(planned, true, true); repo.recordChangedMeal(planned.id, ActualMeal("Actual fish")) }
        val history = runBlocking { repo.load(testRange, testZone) }
        show(); click("Saved Meals"); click("Chicken + vegetables")
        compose.onNodeWithTag("savedLabel").assertTextContains("Lunch")
        compose.onNodeWithTag("savedName").performTextReplacement("Chicken + broccoli")
        compose.onNodeWithTag("savedNotes").performTextReplacement("Reusable note")
        compose.onNodeWithTag("mealDate").assertDoesNotExist()
        save(); text("Chicken + broccoli")
        assertEquals(history, runBlocking { repo.load(testRange, testZone) })
        click("Chicken + broccoli"); click("Delete")
        compose.onNodeWithText("Delete saved meal?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, runBlocking { repo.savedMeals().size })
        click("Delete"); compose.onNodeWithTag("confirmDeleteSavedItem").performClick()
        compose.waitUntil(30_000) { model.draft.value == null && model.library.value.meals.isEmpty() }
        compose.onNodeWithText("No saved meals yet.").assertExists()
        assertEquals(history, runBlocking { repo.load(testRange, testZone) })
    }

    @Test fun savedActivityCreateTypeEditAndDeleteNeverScheduleAnything() {
        show(); click("Saved Activities"); click("Add Saved Activity")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithTag("savedName").performTextInput("Bakal Gym — Upper Body")
        click("Workout")
        compose.onNodeWithTag("savedNotes").performTextInput("Text reference only")
        compose.onNodeWithTag("activityDate").assertDoesNotExist()
        save(); text("Bakal Gym — Upper Body")
        val stored = runBlocking { repo.savedActivities().single() }
        assertEquals(ActivityType.WORKOUT, stored.type)
        click("Bakal Gym — Upper Body")
        compose.onNodeWithTag("savedName").performTextReplacement("Mobility")
        click("Activity"); save(); text("Mobility")
        assertEquals(stored.id, runBlocking { repo.savedActivities().single().id })
        click("Mobility"); click("Delete")
        compose.onNodeWithText("Delete saved activity?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick(); assertEquals(1, runBlocking { repo.savedActivities().size })
        click("Delete"); compose.onNodeWithTag("confirmDeleteSavedItem").performClick()
        compose.waitUntil(30_000) { model.draft.value == null && model.library.value.activities.isEmpty() }
        assertEquals(PlanningData(), runBlocking { repo.load(testRange, testZone) })
    }

    @Test fun externalLibraryChangesAppearAndBackDiscardsUnsavedCreationAndEdit() {
        show(); click("Saved Meals"); click("Add Saved Meal")
        compose.onNodeWithTag("savedName").performTextInput("Discard me")
        back(); assertTrue(runBlocking { repo.savedMeals().isEmpty() })
        val meal = SavedMeal(text = "External soup", notes = "Library note")
        runBlocking { repo.saveSavedMeal(meal, true) }
        text("External soup"); click("External soup")
        compose.onNodeWithTag("savedName").performTextReplacement("Discarded edit")
        back(); assertEquals(listOf(meal), runBlocking { repo.savedMeals() })
        runBlocking { repo.saveSavedMeal(meal.copy(text = "External salad"), false) }
        text("External salad")
        runBlocking { repo.deleteSavedMeal(meal.id) }
        text("No saved meals yet.")
        back(); click("Saved Activities")
        val activity = SavedActivity(name = "External walk", type = ActivityType.ACTIVITY)
        runBlocking { repo.saveSavedActivity(activity, true) }
        text("External walk")
        runBlocking { repo.saveSavedActivity(activity.copy(name = "External swim"), false) }
        text("External swim")
        runBlocking { repo.deleteSavedActivity(activity.id) }
        text("No saved activities yet.")
    }
}
