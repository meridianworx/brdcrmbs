package com.ventures814.brdcrmbs

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
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.today.*
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TodayUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 21)
    private val clock = object : Clock() {
        var now = day.atTime(12, 0).atZone(testZone).toInstant()
        override fun instant() = now
        override fun getZone() = testZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: TodayViewModel
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(database, clock)
        model = TodayViewModel(repo, SavedStateHandle(), clock)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TodayViewModel::class.java.name}", model)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${WeekViewModel::class.java.name}",
            WeekViewModel(repo, SavedStateHandle(), clock))
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; database.close() }
    private fun show(app: Boolean = false) {
        compose.setContent { BrdcrmbsTheme {
            if (app) CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { BrdcrmbsApp() }
            else TodayRoute(model = model)
        } }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun idle() { compose.waitUntil(10_000) { !model.busy.value } }
    private fun status(tag: String, text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(tag).assertTextEquals(text)
    }

    @Test fun fastingAndIndependentWorkoutActivityOutcomesUpdateReactively() {
        val start = day.minusDays(1).atTime(19, 0).atZone(testZone).toInstant()
        val fast = FastingEvent("fast", start, start.plus(Duration.ofHours(72)), testZone, "72h")
        runBlocking {
            repo.addFasting(fast)
            repo.addActivity(ActivityPlan("workout", day, name = "Gym", type = ActivityType.WORKOUT))
            repo.addActivity(ActivityPlan("walk", day, name = "Walk"))
        }
        show()
        click("done-fast-fast"); idle()
        status("status-fast-fast", "✓ Done")
        click("missed-fast-fast"); idle()
        status("status-fast-fast", "✕ Missed")
        for (id in listOf("workout", "walk")) {
            click("done-activity-$id"); idle()
            status("status-activity-$id", "✓ Done")
            click("missed-activity-$id"); idle()
            status("status-activity-$id", "✕ Missed")
        }
        runBlocking {
            assertEquals(fast.copy(outcome = FastingOutcome.MISSED), repo.getFastingPlan("fast"))
            assertEquals(1, repo.load(DateRange.day(day), testZone).fasting.size)
        }
    }

    @Test fun mealsDoneMissedChangedActualSaveCorrectionAndBackKeepOriginalPlan() {
        val planned = MealPlan("meal", day, LocalTime.of(18, 0), "Planned salmon", "Dinner", "Original notes")
        runBlocking { repo.addMeal(planned) }
        show()
        click("done-meal-meal"); idle(); status("status-meal-meal", "✓ Done")
        click("missed-meal-meal"); idle(); status("status-meal-meal", "✕ Missed")
        click("changed-meal-meal")
        compose.waitUntil(10_000) { model.draft.value != null && model.original.value != null }
        compose.onNodeWithTag("originalMeal").assertTextEquals("Planned salmon").assert(hasSetTextAction().not())
        compose.onNodeWithTag("saveActual").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("actualText").performScrollTo().performTextInput("Actual soup")
        compose.onNodeWithTag("actualLabel").performScrollTo().performTextInput("Supper")
        click("actualTime"); compose.onNodeWithText("Cancel").performClick()
        assertNull(model.draft.value!!.time)
        click("actualTime"); compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("actualNotes").performScrollTo().performTextInput("Actual notes")
        click("saveActual"); idle()
        compose.waitUntil(10_000) { model.draft.value == null && model.state.value.plans.meals.single().outcome == MealOutcome.CHANGED }
        compose.onNodeWithTag("status-meal-meal").assertTextEquals("◐ Changed")
        compose.onNodeWithTag("actual-meal").assertTextEquals("Actual: Actual soup")
        val first = runBlocking { repo.getMeal("meal")!! }
        assertEquals(planned.copy(outcome = MealOutcome.CHANGED, actual = first.actual), first)
        assertEquals(LocalTime.NOON, first.actual!!.time)
        click("changed-meal-meal")
        compose.waitUntil(10_000) { model.draft.value != null && model.original.value != null }
        compose.onNodeWithTag("actualText").performScrollTo().performTextReplacement("Discard this")
        compose.onNodeWithText("Back to Today").performScrollTo().performClick()
        assertEquals(first, runBlocking { repo.getMeal("meal") })
        click("changed-meal-meal")
        compose.waitUntil(10_000) { model.draft.value != null && model.original.value != null }
        compose.onNodeWithTag("actualText").performScrollTo().performTextReplacement("Corrected soup")
        click("saveActual"); idle()
        compose.waitUntil(10_000) { model.draft.value == null && model.state.value.plans.meals.single().actual?.text == "Corrected soup" }
        compose.onNodeWithTag("actual-meal").assertTextEquals("Actual: Corrected soup")
        click("done-meal-meal"); idle()
        compose.waitUntil(10_000) { model.state.value.plans.meals.single().outcome == MealOutcome.DONE }
        compose.onNodeWithTag("actual-meal").assertDoesNotExist()
        assertEquals("Planned salmon", runBlocking { repo.getMeal("meal")!!.text })
        assertEquals("Corrected soup", runBlocking { repo.getMeal("meal")!!.actual!!.text })
    }

    @Test fun emptyCategoriesAreNeutralAndExplicitNoFastHasNoFailureControls() {
        show()
        compose.onAllNodesWithText("Nothing planned").assertCountEquals(3)
        compose.onAllNodesWithText("+ Add").assertCountEquals(0)
        compose.onAllNodesWithText("✕ Missed").assertCountEquals(0)
        runBlocking { repo.addFastingPlan(NoFastPlan(date = day, zone = testZone)) }
        compose.waitUntil(10_000) { model.state.value.plans.noFast.size == 1 }
        compose.onNodeWithText("— No fast").assertIsDisplayed()
        compose.onNodeWithText("Intentionally planned as No fast").assertExists()
        compose.onAllNodesWithText("Nothing planned").assertCountEquals(2)
        compose.onAllNodesWithText("Done").assertCountEquals(0)
    }

    @Test fun returningToTodayRefreshesLocalDateAndFiltersOtherDates() {
        runBlocking {
            repo.addMeal(MealPlan(date = day, text = "Today's meal"))
            repo.addMeal(MealPlan(date = day.plusDays(1), text = "Tomorrow's meal"))
            repo.addActivity(ActivityPlan(date = day.minusDays(1), name = "Yesterday's activity"))
        }
        show(app = true)
        compose.onNodeWithText("Today's meal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tomorrow's meal").assertDoesNotExist()
        compose.onNodeWithText("Yesterday's activity").assertDoesNotExist()
        compose.onNode(isSelectable() and hasText("Week")).performClick()
        clock.now = day.plusDays(1).atStartOfDay(testZone).toInstant()
        compose.onNode(isSelectable() and hasText("Today")).performClick()
        compose.waitUntil(10_000) { !model.state.value.loading && model.state.value.day.date == day.plusDays(1) }
        compose.onNodeWithText("Tomorrow's meal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Today's meal").assertDoesNotExist()
        assertEquals(1, runBlocking { repo.load(DateRange.day(day), testZone).meals.size })
    }
}
