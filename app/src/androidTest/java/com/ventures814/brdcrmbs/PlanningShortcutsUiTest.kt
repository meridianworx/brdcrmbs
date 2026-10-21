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
import android.os.SystemClock
import android.view.KeyEvent
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.week.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PlanningShortcutsUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: WeekViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(database)
        runBlocking { repo.addMeal(MealPlan(date = testDate, time = LocalTime.NOON, text = "Copy lunch")) }
        model = WeekViewModel(repo, SavedStateHandle(), Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("shortcuts-ui", model)
        compose.setContent { BrdcrmbsTheme { Surface { WeekRoute(Modifier.fillMaxSize(), model) } } }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; database.close() }
    private fun open(label: String) {
        compose.onNodeWithTag("planningActionsButton").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithTag("planningActionsSheet").assertIsDisplayed()
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.onNodeWithTag("planningActionsSheet").assertDoesNotExist()
    }
    private fun confirm() {
        compose.onNodeWithTag("confirmPlanningCopy").performClick()
        compose.waitUntil(10_000) { !model.copyBusy.value && model.shortcut.value == null }
    }
    @Test fun actionSheetShowsAllChoicesAndBackPreservesSelectedDayWithoutWrites() {
        compose.onNodeWithContentDescription(model.state.value.days[2].description).performClick()
        compose.waitUntil(10_000) { model.state.value.selectedDay == 2 }
        compose.onNodeWithTag("planningActionsButton").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().performClick()
        compose.onNodeWithTag("planningActionsSheet").assertIsDisplayed()
        for (text in listOf("Copy Day", "Copy to Selected Days", "Repeat Last Week",
            "Copy this day's plan to another day", "Copy this day's plan to multiple days", "Add last week's plan to this week")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val keyTime = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(keyTime, keyTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(keyTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("planningActionsSheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("planningActionsSheet").assertDoesNotExist()
        assertEquals(2, model.state.value.selectedDay)
        assertNull(model.shortcut.value)
        runBlocking { assertEquals(1, repo.load(testRange, testZone).meals.size) }
    }

    @Test fun everySheetChoiceOpensExistingWorkflowForSelectedDayAndDisplayedWeek() {
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.days.first().number == "26" }
        compose.onNodeWithContentDescription(model.state.value.days[3].description).performClick()
        compose.waitUntil(10_000) { model.state.value.selectedDay == 3 }
        for ((label, mode) in listOf("Copy Day" to Shortcut.COPY_DAY,
            "Copy to Selected Days" to Shortcut.COPY_DAYS, "Repeat Last Week" to Shortcut.REPEAT_WEEK)) {
            open(label)
            assertEquals(mode, model.shortcut.value!!.mode)
            assertEquals(testDate.plusWeeks(1).plusDays(3), model.shortcut.value!!.source)
            assertEquals(testDate.plusWeeks(1), model.shortcut.value!!.weekStart)
            compose.onNodeWithTag("confirmPlanningCopy").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(3, model.state.value.selectedDay)
        }
        runBlocking { assertTrue(repo.load(DateRange.weekContaining(testDate.plusWeeks(1)), testZone).meals.isEmpty()) }
    }
    @Test fun copyDayCancelThenCopyAndReactiveDaySelection() {
        open("Copy Day")
        compose.onNodeWithTag("copyDestination-$testDate").assertIsNotEnabled()
        compose.onNodeWithTag("confirmPlanningCopy").assertIsNotEnabled()
        compose.onNodeWithTag("copyDestination-${testDate.plusDays(1)}").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertTrue(repo.load(DateRange.day(testDate.plusDays(1)), testZone).meals.isEmpty()) }
        open("Copy Day")
        compose.onNodeWithTag("copyDestination-${testDate.plusDays(1)}").performScrollTo().performClick()
        confirm()
        compose.onNodeWithContentDescription(model.state.value.days[1].description).performClick()
        compose.waitUntil(10_000) { model.state.value.selectedDay == 1 && model.state.value.meals.size == 1 }
        compose.onNodeWithText("Copy lunch").performScrollTo().assertIsDisplayed()
        runBlocking {
            val original = repo.load(DateRange.day(testDate), testZone).meals.single()
            val copy = repo.load(DateRange.day(testDate.plusDays(1)), testZone).meals.single()
            assertNotEquals(original.id, copy.id); assertEquals(LocalTime.NOON, copy.time)
        }
    }
    @Test fun emptySourceFeedbackAndDestinationWeekNavigation() {
        compose.onNodeWithContentDescription(model.state.value.days[1].description).performClick()
        compose.waitUntil(10_000) { model.state.value.selectedDay == 1 }
        open("Copy Day")
        compose.onNodeWithTag("copyDestination-${testDate.plusDays(2)}").performScrollTo().performClick()
        confirm()
        compose.onNodeWithText("Nothing to copy. No plans were added.").assertExists()
        compose.onNodeWithContentDescription(model.state.value.days[0].description).performClick()
        compose.waitUntil(10_000) { model.state.value.selectedDay == 0 }
        open("Copy Day")
        compose.onNodeWithTag("copyNextWeek").performClick()
        compose.onNodeWithTag("copyDestination-${testDate.plusWeeks(1)}").performScrollTo().performClick()
        confirm()
        runBlocking {
            assertEquals(1, repo.load(testRange, testZone).meals.size)
            assertEquals(1, repo.load(DateRange.day(testDate.plusWeeks(1)), testZone).meals.size)
        }
    }

    @Test fun multipleDestinationsAndRepeatConfirmationWithFutureWeekNavigation() {
        open("Copy to Selected Days")
        for (offset in listOf(1L, 2L)) compose.onNodeWithTag("copyDestination-${testDate.plusDays(offset)}").performScrollTo().performClick()
        confirm()
        runBlocking { assertEquals(3, repo.load(testRange, testZone).meals.size) }
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.days.first().number == "26" }
        open("Repeat Last Week")
        compose.onNodeWithText("Existing plans will not be deleted.", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertTrue(repo.load(DateRange.weekContaining(testDate.plusWeeks(1)), testZone).meals.isEmpty()) }
        open("Repeat Last Week"); confirm()
        compose.waitUntil(10_000) { model.state.value.meals.size == 1 }
        runBlocking { assertEquals(3, repo.load(DateRange.weekContaining(testDate.plusWeeks(1)), testZone).meals.size) }
        compose.onNodeWithContentDescription("Previous week").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.days.first().number == "19" }
        assertEquals(0, model.state.value.selectedDay)
        runBlocking { assertEquals(3, repo.load(testRange, testZone).meals.size) }
    }
}
