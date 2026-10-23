package com.ventures814.brdcrmbs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.progress.*
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import com.ventures814.brdcrmbs.ui.settings.SettingsViewModel
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 22)
    private val clock = Clock.fixed(date.atTime(12, 0).atZone(testZone).toInstant(), testZone)
    private lateinit var db: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: ProgressViewModel
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db, clock)
        model = ProgressViewModel(repo, SavedStateHandle(), clock)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${SettingsViewModel::class.java.name}", SettingsViewModel(repo, SavedStateHandle()))
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${ProgressViewModel::class.java.name}", model)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TodayViewModel::class.java.name}", TodayViewModel(repo, SavedStateHandle(), clock))
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${WeekViewModel::class.java.name}", WeekViewModel(repo, SavedStateHandle(), clock))
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; db.close() }
    private fun show(app: Boolean = false) {
        compose.setContent { BrdcrmbsTheme { if (app) CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { BrdcrmbsApp() }
            else ProgressRoute(model = model) } }
        if (!app) await(date)
    }
    private fun await(selected: LocalDate) {
        try { compose.waitUntil(30_000) { !model.state.value.loading && !model.state.value.failed && model.state.value.context.selected == selected } }
        catch (failure: Throwable) {
            compose.onNodeWithTag("progressDate-$selected").printToLog("ProgressDate")
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/progress-failure.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            throw AssertionError("Expected $selected; actual context=${model.state.value.context}, loading=${model.state.value.loading}, failed=${model.state.value.failed}", failure)
        }
    }
    private fun tab(name: String) { compose.onNode(isSelectable() and hasText(name)).performClick() }

    @Test fun indicatorsSummariesLegendAndReadOnlyChangedDetailUseRealRecords() {
        val start = date.minusDays(1).atTime(19, 0).atZone(testZone).toInstant()
        val fast = FastingEvent("span", start, start.plus(Duration.ofHours(72)), testZone, "72h", outcome = FastingOutcome.DONE)
        val meal = MealPlan("changed", date, LocalTime.NOON, "Original salmon", "Lunch", "Planned note", MealOutcome.CHANGED,
            ActualMeal("Actual soup", LocalTime.of(13, 0), "Late lunch", "Actual note"))
        runBlocking {
            repo.addFasting(fast); repo.addMeal(meal)
            repo.addMeal(MealPlan(date = date, text = "Done meal", outcome = MealOutcome.DONE))
            repo.addActivity(ActivityPlan("walk", date, name = "Walk", outcome = ActivityOutcome.MISSED))
        }
        val before = runBlocking { repo.load(DateRange.day(date), testZone) }
        show()
        compose.onNodeWithTag("summary-Fasting").assertTextEquals("100%")
        compose.onNodeWithTag("summary-Meals").assertTextEquals("50%")
        compose.onNodeWithTag("summary-Activity").assertTextEquals("0%")
        for (label in listOf("Fasting", "Meals", "Activity")) compose.onNodeWithTag("summaryCard-$label").assertExists()
        compose.onNodeWithTag("ring-Fasting").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        compose.onNodeWithTag("ring-Meals").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.onNodeWithTag("dailyAdherence").performScrollTo().assertTextEquals("50%")
        compose.onAllNodesWithTag("dayDetailCard").assertCountEquals(1)
        for (letter in listOf("F", "M", "A")) compose.onAllNodesWithText(letter).assertCountEquals(0)
        compose.onNodeWithTag("indicator-F-$date", useUnmergedTree = true).assertContentDescriptionEquals("F Success")
        compose.onNodeWithTag("indicator-M-$date", useUnmergedTree = true).assertContentDescriptionEquals("M Changed")
        compose.onNodeWithTag("indicator-A-$date", useUnmergedTree = true).assertContentDescriptionEquals("A Missed")
        for (category in listOf("F", "M", "A")) compose.onNodeWithTag("indicator-$category-$date", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("progressDate-$date").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("fastSpan-$date", useUnmergedTree = true).assertExists()
        for (status in listOf("SUCCESS", "CHANGED", "MISSED", "PLANNED")) compose.onNodeWithTag("legend-$status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reviewDetails").performScrollTo().performClick()
        compose.onNodeWithTag("detail-fast-span").performScrollTo().assertTextEquals("72h")
        compose.onNodeWithTag("detail-planned-changed").performScrollTo().assertTextEquals("Planned: Original salmon").assert(hasSetTextAction().not())
        compose.onNodeWithTag("detail-actual-changed").performScrollTo().assertTextEquals("Actual: Actual soup")
        compose.onNodeWithText("Late lunch · 1:00 PM · Actual note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Meals · 1 / 2 followed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-activity-walk").performScrollTo().assertTextEquals("Walk")
        compose.onAllNodesWithText("Save").assertCountEquals(0)
        assertEquals(before, runBlocking { repo.load(DateRange.day(date), testZone) })
    }
    @Test fun monthNavigationDateSelectionAndNeutralDetails() {
        show()
        assertTrue(compose.onNodeWithText("Sun").getUnclippedBoundsInRoot().left < compose.onNodeWithText("Mon").getUnclippedBoundsInRoot().left)
        compose.onNodeWithTag("progressMonth").assertTextEquals("October 2026")
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick(); await(LocalDate.of(2026, 11, 1))
        compose.onNodeWithTag("progressMonth").assertTextEquals("November 2026")
        val selected = LocalDate.of(2026, 11, 30)
        compose.onNodeWithTag("progressDate-$selected").performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(40.dp).performClick(); await(selected)
        compose.onNodeWithTag("progressDate-$selected").assertIsSelected()
        compose.onNodeWithTag("detailDate").performScrollTo().assertTextEquals("Monday, Nov 30")
        compose.onAllNodesWithText("Nothing planned").assertCountEquals(3)
        compose.onNodeWithTag("dailyAdherence").assertTextEquals("—")
        compose.onNodeWithTag("progressDate-$selected").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("indicator-M-$selected", useUnmergedTree = true).assertContentDescriptionEquals("M Nothing planned")
        compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick(); await(LocalDate.of(2026, 10, 1))
        compose.onNodeWithTag("summary-Meals").assertTextEquals("—")
        compose.onNodeWithTag("ring-Meals").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    }
    @Test fun compactPlannedIndicatorsAndIntentionalNoFastRemainNeutral() {
        runBlocking {
            repo.addMeal(MealPlan(date = date, text = "Planned lunch"))
            repo.addFastingPlan(NoFastPlan(date = date, zone = testZone))
        }
        show()
        compose.onNodeWithTag("indicator-M-$date", useUnmergedTree = true).assertContentDescriptionEquals("M Planned")
        compose.onNodeWithTag("indicator-F-$date", useUnmergedTree = true).assertContentDescriptionEquals("F Nothing planned")
        compose.onNodeWithText("No fast · Intentional plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dailyAdherence").assertTextEquals("—")
        compose.onAllNodesWithText("— — —").assertCountEquals(0)
        for (letter in listOf("F", "M", "A")) compose.onAllNodesWithText(letter).assertCountEquals(0)
    }
    @Test fun populatedReferenceStructureKeepsCompactRowsVisibleAndCapturesVisualReview() {
        val start = date.atTime(19, 0).atZone(testZone).toInstant()
        runBlocking {
            repo.addFasting(FastingEvent(start = start, end = start.plus(Duration.ofHours(16)), zone = testZone, preset = "16:8", outcome = FastingOutcome.DONE))
            repo.addMeal(MealPlan(date = date, text = "Eggs", outcome = MealOutcome.DONE))
            repo.addMeal(MealPlan(date = date, text = "Chicken", outcome = MealOutcome.DONE))
            repo.addMeal(MealPlan(date = date, text = "Salmon", outcome = MealOutcome.CHANGED, actual = ActualMeal("Soup")))
            repo.addActivity(ActivityPlan(date = date, name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT, outcome = ActivityOutcome.MISSED))
        }
        show(app = true); tab("Progress"); await(date)
        compose.onNodeWithTag("summary-Fasting").assertTextEquals("100%")
        compose.onNodeWithTag("summary-Meals").assertTextEquals("67%")
        compose.onNodeWithTag("dailyAdherence").assertTextEquals("60%")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/progress-screen3-populated.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        compose.onNodeWithTag("dayDetailCard").assertIsDisplayed()
        compose.onNodeWithTag("reviewDetails").assertIsDisplayed()
        compose.onNodeWithText("Bakal Gym — Upper Body").assertIsDisplayed()
    }
    @Test fun progressTabEntryReentryAndReselectionResetToCurrentMonthToday() {
        show(app = true); tab("Progress"); await(date)
        for (destination in listOf("Today", "Week", "Settings", "Progress")) {
            compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick(); await(LocalDate.of(2026, 9, 1))
            if (destination != "Progress") tab(destination)
            tab("Progress"); await(date)
            compose.onNodeWithTag("progressDate-$date").performScrollTo().assertIsSelected()
        }
    }
}
