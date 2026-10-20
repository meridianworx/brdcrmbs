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

class ActivityUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: WeekViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(database)
        model = WeekViewModel(repo, SavedStateHandle(), Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("activity-ui", model)
        compose.setContent { BrdcrmbsTheme { Surface { WeekRoute(Modifier.fillMaxSize(), model) } } }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; database.close() }
    private fun add() {
        compose.onAllNodesWithText("+ Add")[2].performScrollTo().performClick()
        compose.waitUntil(10_000) { model.activityEditor.value != null && !model.activityLoading.value }
    }
    private fun save() {
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.activityEditor.value == null && !model.activityBusy.value }
    }
    private fun edit(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitUntil(10_000) { model.activityEditor.value != null && !model.activityLoading.value }
    }

    @Test fun addSaveEditConfirmedDeleteAndSelectReusableActivity() {
        add()
        compose.onNodeWithText("Add Activity").assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("activityText").performScrollTo().performTextInput("20-minute walk")
        compose.onNodeWithTag("activityNotes").performScrollTo().performTextInput("Outside")
        compose.onNodeWithTag("activitySaveForReuse").performScrollTo().performClick()
        save()
        compose.waitUntil(10_000) { model.state.value.activities.size == 1 }
        compose.onNodeWithText("Workout / Activity (1)").assertExists()
        edit("20-minute walk")
        compose.onNodeWithText("Edit Activity").assertIsDisplayed()
        compose.onNodeWithTag("activityText").performScrollTo().performTextReplacement("Morning walk")
        save()
        compose.waitUntil(10_000) { model.state.value.activities.singleOrNull()?.title == "Morning walk" }
        edit("Morning walk")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertEquals(1, repo.load(testRange, testZone).activities.size) }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithTag("confirmDeleteActivity").performClick()
        compose.waitUntil(10_000) { model.activityEditor.value == null && model.state.value.activities.isEmpty() }
        add()
        compose.onNodeWithText("Saved Activity").performClick()
        compose.onNodeWithText("20-minute walk").performClick()
        compose.onNodeWithTag("activityText").assertTextContains("20-minute walk")
        compose.onNodeWithTag("activityNotes").assertTextContains("Outside")
        runBlocking { assertTrue(repo.load(testRange, testZone).activities.isEmpty()) }
        save()
        compose.waitUntil(10_000) { model.state.value.activities.size == 1 }
        runBlocking { assertEquals(1, repo.savedActivities().size) }
    }

    @Test fun bakalGymWorkoutAndCustomActivityAppearAsSeparateRows() {
        add()
        compose.onNodeWithText("Bakal Gym").performClick()
        assertEquals(ActivityType.WORKOUT, model.activityEditor.value!!.type)
        compose.onNodeWithTag("activityText").performScrollTo().performTextInput("Bakal Gym — Upper Body")
        save()
        compose.waitUntil(10_000) { model.state.value.activities.size == 1 }
        add()
        compose.onNodeWithTag("activityText").performScrollTo().performTextInput("Basketball")
        save()
        compose.waitUntil(10_000) { model.state.value.activities.size == 2 }
        compose.onNodeWithText("Workout / Activity (2)").assertExists()
        compose.onNodeWithText("Bakal Gym — Upper Body").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Basketball").performScrollTo().assertIsDisplayed()
        runBlocking {
            val plans = repo.load(testRange, testZone).activities
            assertEquals(2, plans.size)
            assertEquals(setOf(ActivityType.WORKOUT, ActivityType.ACTIVITY), plans.map { it.type }.toSet())
        }
    }

    @Test fun backAndPickerCancelDoNotSaveAndOptionalTimeCanBeAddedAndRemoved() {
        add()
        val original = model.activityEditor.value
        compose.onNodeWithTag("activityDate").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(original, model.activityEditor.value)
        compose.onNodeWithTag("activityTime").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(model.activityEditor.value!!.time)
        compose.onNodeWithTag("activityTime").performClick()
        compose.onNodeWithText("OK").performClick()
        assertNotNull(model.activityEditor.value!!.time)
        compose.onNodeWithText("Remove time").performScrollTo().performClick()
        assertNull(model.activityEditor.value!!.time)
        compose.onNodeWithTag("activityText").performScrollTo().performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Back to Week").performScrollTo().performClick()
        compose.onNodeWithText("My Week").assertIsDisplayed()
        runBlocking { assertTrue(repo.load(testRange, testZone).activities.isEmpty()); assertTrue(repo.savedActivities().isEmpty()) }
    }
}
