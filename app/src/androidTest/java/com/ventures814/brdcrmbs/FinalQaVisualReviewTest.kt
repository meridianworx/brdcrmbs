package com.ventures814.brdcrmbs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.progress.ProgressViewModel
import com.ventures814.brdcrmbs.ui.settings.SettingsViewModel
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Production shell/screens with isolated persisted fixtures; captures are review evidence, not assets. */
class FinalQaVisualReviewTest {
    @get:Rule val compose = createComposeRule()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private lateinit var week: WeekViewModel
    private lateinit var today: TodayViewModel
    private lateinit var settings: SettingsViewModel
    private lateinit var clock: Clock
    private var large by mutableStateOf(false)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        clock = Clock.fixed(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())
        val date = LocalDate.now(clock)
        repo = RoomPlanningRepository(db, clock)
        runBlocking {
            val start = clock.instant().minus(Duration.ofHours(10))
            repo.addFasting(FastingEvent("active", start, start.plus(Duration.ofHours(16)), clock.zone, "16:8", "Chosen fasting plan"))
            repo.addFasting(FastingEvent("past", start.minus(Duration.ofDays(2)), start.minus(Duration.ofHours(32)), clock.zone, "16:8", outcome = FastingOutcome.DONE))
            repo.savePlannedMeal(MealPlan("planned", date, LocalTime.NOON, "Planned soup", "Lunch"), true, true)
            repo.addMeal(MealPlan("changed", date, LocalTime.of(18, 0), "Planned chicken", outcome = MealOutcome.CHANGED,
                actual = ActualMeal("Actual fish", LocalTime.of(19, 0), "Dinner", "Actual note")))
            repo.addMeal(MealPlan("done", date, text = "Eggs", outcome = MealOutcome.DONE))
            repo.savePlannedActivity(ActivityPlan("walk", date, LocalTime.of(18, 30), "Evening walk"), true, true)
            repo.addActivity(ActivityPlan("missed", date, name = "Morning workout", type = ActivityType.WORKOUT, outcome = ActivityOutcome.MISSED))
        }
        week = WeekViewModel(repo, SavedStateHandle(), clock)
        today = TodayViewModel(repo, SavedStateHandle(), clock)
        settings = SettingsViewModel(repo, SavedStateHandle())
        fun put(value: ViewModel) = owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${value.javaClass.name}", value)
        put(week); put(today); put(settings); put(ProgressViewModel(repo, SavedStateHandle(), clock))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner,
                LocalDensity provides Density(density.density, if (large) 1.5f else density.fontScale)) {
                BrdcrmbsTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Surface propagates its minimum bounds: an outer Box releases those minima
                    // so the review viewport is actually narrow/short, not coerced to phone size.
                    Box(Modifier.fillMaxSize()) {
                        Box((if (large) Modifier.width(320.dp).height(640.dp) else Modifier.fillMaxSize())
                            .clipToBounds().testTag("reviewViewport")) { BrdcrmbsApp() }
                    }
                } }
            }
        }
        compose.waitUntil(30_000) { !today.state.value.loading }
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; db.close() }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
    private fun tab(text: String) = compose.onNode(isSelectable() and hasText(text)).assertIsDisplayed().performClick()
    private fun noTabs() { compose.onNode(isSelectable() and hasText("Settings")).assertDoesNotExist() }
    private fun confirmationScrolls() {
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
    private fun scoreStaysWhole(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("summary-$label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertEquals("$label percentage must stay on one line", 1, it.lineCount)
            // Native paragraph/container width can exceed the rounded size even when every glyph fits.
            assertEquals(it.layoutInput.text.length, it.getLineEnd(0))
            assertFalse(it.isLineEllipsized(0))
            assertTrue("$label left edge", it.getLineLeft(0) >= 0f)
            assertTrue("$label right edge", it.getLineRight(0) <= it.size.width.toFloat())
            assertFalse(it.didOverflowHeight)
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/brdcrmbs-final-qa-$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }
    private fun editorBack() = compose.onNodeWithContentDescription("Back to Week").performScrollTo().performClick()

    @Test fun acceptedScreensEditorsAndNarrowLargeFontNavigationRemainUsable() {
        val range = DateRange(LocalDate.now(clock).minusDays(7), LocalDate.now(clock).plusDays(7))
        val before = runBlocking { repo.load(range, clock.zone) }
        compose.onNodeWithTag("fastingProgress-active").performScrollTo().assertIsDisplayed()
        capture("01-today")
        compose.onNodeWithTag("changed-meal-planned").performScrollTo().performClick()
        compose.waitUntil(30_000) { today.draft.value != null && !today.editorLoading.value }
        noTabs(); compose.onNodeWithTag("originalMeal").assertIsDisplayed(); capture("02-actual-meal")
        click("Back to Today")
        tab("Week"); compose.waitUntil(30_000) { !week.state.value.loading }
        capture("03-week")
        compose.onAllNodesWithText("+ Add")[0].performScrollTo().performClick()
        noTabs(); compose.onNodeWithText("Add Fast").assertIsDisplayed(); capture("04-add-fast"); editorBack()
        compose.runOnIdle { week.editFast("active") }
        compose.waitUntil(30_000) { week.editor.value != null && !week.editorLoading.value }
        noTabs(); compose.onNodeWithText("Edit Fast").assertIsDisplayed(); capture("05-edit-fast"); editorBack()
        compose.onAllNodesWithText("+ Add")[1].performScrollTo().performClick()
        noTabs(); compose.onNodeWithText("Add Meal").assertIsDisplayed(); capture("06-add-meal"); editorBack()
        compose.runOnIdle { week.editMeal("planned") }
        compose.waitUntil(30_000) { week.mealEditor.value != null && !week.mealLoading.value }
        noTabs(); compose.onNodeWithText("Edit Meal").assertIsDisplayed(); capture("07-edit-meal"); editorBack()
        compose.onAllNodesWithText("+ Add")[2].performScrollTo().performClick()
        noTabs(); compose.onNodeWithText("Add Activity").assertIsDisplayed(); capture("08-add-activity"); editorBack()
        compose.runOnIdle { week.editActivity("walk") }
        compose.waitUntil(30_000) { week.activityEditor.value != null && !week.activityLoading.value }
        noTabs(); compose.onNodeWithText("Edit Activity").assertIsDisplayed(); capture("09-edit-activity"); editorBack()
        tab("Progress"); compose.onNodeWithText("DAY DETAIL").performScrollTo().assertIsDisplayed(); capture("10-progress")
        tab("Settings"); capture("11-settings")
        click("Saved Meals"); noTabs(); click("Planned soup"); capture("12-saved-meal-editor")
        compose.onNodeWithContentDescription("Back").performClick(); capture("13-saved-meals")
        compose.onNodeWithContentDescription("Back").performClick()
        click("Saved Activities"); noTabs(); click("Evening walk"); capture("14-saved-activity-editor")
        compose.onNodeWithContentDescription("Back").performClick(); capture("15-saved-activities")
        compose.onNodeWithContentDescription("Back").performClick()
        click("Reminders"); noTabs(); compose.onNodeWithText("Fasting start").assertExists(); capture("16-reminders"); click("Back")
        click("Backup & Restore"); noTabs(); compose.onNodeWithTag("createBackup").assertIsDisplayed(); capture("17-backup")
        compose.onNodeWithTag("backupBack").performClick()
        click("App Preferences"); noTabs(); compose.onNodeWithText("Time format").assertIsDisplayed(); capture("18-preferences")
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        click("About"); noTabs(); compose.onNodeWithText("Version", substring = true).assertIsDisplayed(); capture("19-about")
        compose.onNodeWithContentDescription("Back").performClick()
        click("Data Management"); noTabs(); capture("20-data-management")
        compose.onNodeWithTag("clearPlanning").performClick(); confirmationScrolls(); capture("21-clear-confirmation"); compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("resetAppData").performClick(); confirmationScrolls(); capture("22-reset-review")
        compose.onNodeWithTag("confirmDataAction").performClick(); confirmationScrolls(); capture("23-reset-final"); compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.runOnIdle { large = true }
        compose.onNodeWithTag("reviewViewport").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(640.dp)
        for ((index, destination) in listOf("Today", "Week", "Progress", "Settings").withIndex()) {
            tab(destination)
            capture("${24 + index}-large-${destination.lowercase()}")
            if (destination == "Progress") listOf("Fasting", "Meals", "Activity").forEach(::scoreStaysWhole)
        }
        click("Data Management")
        compose.onNodeWithTag("resetAppData").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("confirmDataAction").assertIsDisplayed(); capture("28-large-reset-review")
        compose.onNodeWithTag("confirmDataAction").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed(); capture("29-large-reset-final")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Back to Settings").performScrollTo().performClick()
        for (label in listOf("DEVELOPER / TESTING", "Seed Test Week", "Seed Active Fast", "Reset Test Data", "Reminder diagnostics")) {
            compose.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }
        assertEquals(before, runBlocking { repo.load(range, clock.zone) })
    }
}
