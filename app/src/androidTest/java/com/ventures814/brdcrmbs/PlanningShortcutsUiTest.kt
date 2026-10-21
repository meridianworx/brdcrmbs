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
        compose.onNodeWithText("Planning actions").performScrollTo().performClick()
        compose.onNodeWithText(label).performClick()
    }
    private fun confirm() {
        compose.onNodeWithTag("confirmPlanningCopy").performClick()
        compose.waitUntil(10_000) { !model.copyBusy.value && model.shortcut.value == null }
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
