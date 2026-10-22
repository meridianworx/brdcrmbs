package com.ventures814.brdcrmbs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class WeekContextUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)
    private val monday = DateRange.weekContaining(today).start
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository
    private lateinit var model: WeekViewModel
    private lateinit var restoration: StateRestorationTester

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repository = RoomPlanningRepository(database)
        runBlocking {
            for (offset in listOf(-7L, 0L, 7L)) repository.addMeal(MealPlan(date = today.plusDays(offset), text = "Lunch $offset"))
        }
        model = WeekViewModel(repository, SavedStateHandle(), Clock.fixed(today.atTime(12, 0).atZone(testZone).toInstant(), testZone))
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${WeekViewModel::class.java.name}", model)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TodayViewModel::class.java.name}",
            TodayViewModel(repository, SavedStateHandle(), Clock.fixed(today.atTime(12, 0).atZone(testZone).toInstant(), testZone)))
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { BrdcrmbsTheme { BrdcrmbsApp() } }
        }
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; database.close() }
    private fun tab(label: String) { compose.onNode(isSelectable() and hasText(label)).performClick() }
    private fun awaitDay(start: LocalDate, index: Int) {
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.selectedDay == index &&
            model.state.value.days.first().number == start.dayOfMonth.toString() }
        compose.onNodeWithContentDescription(model.state.value.days[index].description).assertIsSelected()
    }
    private fun enter() { tab("Week"); awaitDay(monday, 2) }
    private fun copyToTomorrow() {
        compose.onNodeWithTag("planningActionsButton").performScrollTo().performClick()
        compose.onNodeWithText("Copy Day").performClick()
        compose.onNodeWithTag("copyDestination-${today.plusDays(1)}").performScrollTo().performClick()
        compose.onNodeWithTag("confirmPlanningCopy").performClick()
        compose.waitUntil(10_000) { !model.copyBusy.value && model.shortcut.value == null }
        assertNotNull(model.copyMessage.value)
        compose.onNodeWithText(model.copyMessage.value!!).assertExists()
    }

    @Test fun tabEntryAndReselectionResetTodayWithoutChangingBrowsedPlans() {
        val range = DateRange(monday.minusWeeks(1), monday.plusWeeks(2))
        val original = runBlocking { repository.load(range, testZone) }
        enter()
        for ((offset, destination) in listOf(1L to "Progress", -1L to "Settings", 0L to "Today")) {
            if (offset != 0L) compose.onNodeWithContentDescription(if (offset > 0) "Next week" else "Previous week").performScrollTo().performClick()
            awaitDay(monday.plusWeeks(offset), if (offset == 0L) 2 else 0)
            compose.onNodeWithContentDescription(model.state.value.days[0].description).performClick()
            awaitDay(monday.plusWeeks(offset), 0)
            tab(destination); enter()
        }
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        awaitDay(monday.plusWeeks(1), 0)
        tab("Week"); awaitDay(monday, 2)
        assertNull(model.copyMessage.value)
        assertEquals(original, runBlocking { repository.load(range, testZone) })
    }

    @Test fun feedbackStaysWithinWeekAndIsReplacedThenClearedByWeekNavigation() {
        enter(); copyToTomorrow()
        val added = model.copyMessage.value!!
        assertTrue(added.contains("Added 1"))
        compose.onNodeWithContentDescription(model.state.value.days[0].description).performClick()
        awaitDay(monday, 0)
        compose.onNodeWithText(added).assertExists()
        compose.onNodeWithContentDescription(model.state.value.days[2].description).performClick()
        awaitDay(monday, 2); copyToTomorrow()
        val skipped = model.copyMessage.value!!
        assertTrue(skipped.contains("skipped 1")); assertNotEquals(added, skipped)
        compose.onNodeWithText(added).assertDoesNotExist()
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        awaitDay(monday.plusWeeks(1), 0)
        assertNull(model.copyMessage.value); compose.onNodeWithText(skipped).assertDoesNotExist()
        compose.runOnIdle { model.openShortcut(com.ventures814.brdcrmbs.ui.week.Shortcut.REPEAT_WEEK); model.copyPlans() }
        compose.waitUntil(10_000) { !model.copyBusy.value && model.shortcut.value == null }
        assertNotNull(model.copyMessage.value)
        compose.onNodeWithContentDescription("Previous week").performScrollTo().performClick()
        awaitDay(monday, 0); assertNull(model.copyMessage.value)
    }

    @Test fun leavingEveryTabAndRecreationClearFeedbackWithoutResettingActiveBrowsing() {
        enter()
        for (destination in listOf("Today", "Progress", "Settings")) {
            copyToTomorrow()
            val result = model.copyMessage.value!!
            tab(destination)
            compose.waitUntil(10_000) { model.copyMessage.value == null }
            enter(); compose.onNodeWithText(result).assertDoesNotExist()
        }
        copyToTomorrow()
        restoration.emulateSavedInstanceStateRestore()
        awaitDay(monday, 2); assertNull(model.copyMessage.value)
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        awaitDay(monday.plusWeeks(1), 0)
        restoration.emulateSavedInstanceStateRestore()
        awaitDay(monday.plusWeeks(1), 0)
    }

    @Test fun arrowsSelectMondayAcrossRepeatedWeeksButTabStillSelectsToday() {
        val range = DateRange(monday.minusWeeks(2), monday.plusWeeks(3))
        val original = runBlocking { repository.load(range, testZone) }
        enter() // Current Wednesday.
        var week = monday
        for (offset in listOf(1L, 1L, -1L, -1L, -1L, 1L)) {
            compose.onNodeWithContentDescription(if (offset > 0) "Next week" else "Previous week").performScrollTo().performClick()
            week = week.plusWeeks(offset)
            awaitDay(week, 0)
            // A different selection must not carry into the next destination week.
            compose.onNodeWithContentDescription(model.state.value.days[6].description).performClick()
            awaitDay(week, 6)
        }
        tab("Week"); awaitDay(monday, 2)
        compose.onNodeWithContentDescription("Previous week").performScrollTo().performClick()
        awaitDay(monday.minusWeeks(1), 0)
        tab("Progress"); enter()
        assertEquals(original, runBlocking { repository.load(range, testZone) })
    }
}
