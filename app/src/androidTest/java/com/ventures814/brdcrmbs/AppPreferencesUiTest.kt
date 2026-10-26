package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.settings.*
import com.ventures814.brdcrmbs.ui.fasting.*
import com.ventures814.brdcrmbs.ui.meals.*
import com.ventures814.brdcrmbs.ui.activities.*
import com.ventures814.brdcrmbs.ui.today.*
import com.ventures814.brdcrmbs.ui.week.*
import com.ventures814.brdcrmbs.ui.progress.*
import com.ventures814.brdcrmbs.ui.screens.TodayScreen
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AppPreferencesUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as BrdcrmbsApplication
    private lateinit var before: AppPreferences
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2030, 1, 7)
    private val now = date.atTime(12, 0).atZone(zone).toInstant()
    private val fast = FastingDraft.new(date, zone).changeStart(date.atTime(2, 0)).toPlan() as FastingEvent
    private val meal = MealPlan("format-meal", date, LocalTime.of(19, 0), "Original meal", outcome = MealOutcome.CHANGED, actual = ActualMeal("Actual meal", LocalTime.of(20, 0)))
    private val activity = ActivityPlan("format-activity", date, LocalTime.of(8, 30), "Walk")
    private val data get() = PlanningData(fasting = listOf(fast), meals = listOf(meal), activities = listOf(activity))
    @Before fun setup() {
        before = app.appPreferences.state.value
        runBlocking { app.appPreferences.replace(AppPreferences()) }
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db)
    }
    @After fun cleanup() {
        compose.runOnIdle { owner.viewModelStore.clear() }
        runBlocking { app.appPreferences.replace(before) }; db.close()
    }
    private fun waitText(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) { waitText(text); compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun showApp() {
        val clock = Clock.fixed(now, zone)
        fun put(v: ViewModel) = owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${v.javaClass.name}", v)
        put(SettingsViewModel(repo, SavedStateHandle())); put(TodayViewModel(repo, SavedStateHandle(), clock))
        put(ProgressViewModel(repo, SavedStateHandle(), clock)); put(WeekViewModel(repo, SavedStateHandle(), clock, { app.appPreferences.state.value.fastingPreset }))
        put(AppPreferencesViewModel(app.appPreferences))
        compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { Surface { BrdcrmbsApp() } } } }
    }
    @Test fun threeSettingsPersistNavigateAndNeverCreateMealsOrPlans() {
        showApp(); compose.onNode(isSelectable() and hasText("Settings")).performClick(); click("App Preferences")
        compose.onNode(isSelectable() and hasText("Settings")).assertDoesNotExist()
        compose.onNodeWithText("System default").assertIsDisplayed(); compose.onNodeWithText("No default").assertIsDisplayed()
        click("Time format"); click("24-hour")
        compose.waitUntil(30_000) { app.appPreferences.state.value.timeFormat == TimeFormat.TWENTY_FOUR }
        click("Default fasting preset"); click("48h")
        compose.waitUntil(30_000) { app.appPreferences.state.value.fastingPreset == FastPreset.H48 }
        click("Default meal count"); click("6")
        compose.waitUntil(30_000) { app.appPreferences.state.value.mealCount == 6 }
        assertEquals(AppPreferences(TimeFormat.TWENTY_FOUR, FastPreset.H48, 6), AppPreferencesStore(context).state.value)
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNode(isSelectable() and hasText("Settings")).assertIsSelected()
        compose.onNode(isSelectable() and hasText("Week")).performClick()
        assertEquals(PlanningData(), runBlocking { repo.load(DateRange.weekContaining(date), zone) })
        assertTrue(runBlocking { repo.savedMeals() }.isEmpty())
    }
    @Test fun persistedTimeChoiceReRendersTodayWeekAndProgressWithoutChangingHistory() {
        runBlocking { repo.addFasting(fast); repo.addMeal(meal); repo.addActivity(activity); app.appPreferences.replace(AppPreferences(TimeFormat.TWELVE)) }
        showApp(); waitText("7:00 PM")
        runBlocking { app.appPreferences.replace(AppPreferences(TimeFormat.TWENTY_FOUR)) }
        waitText("19:00"); compose.onNodeWithTag("fastingEnd-${fast.id}").assertTextEquals("Ends at 18:00")
        compose.onNode(isSelectable() and hasText("Week")).performClick(); waitText("19:00")
        compose.onNode(isSelectable() and hasText("Progress")).performClick(); waitText("Review all details")
        compose.onNodeWithTag("reviewDetails").assertTextEquals("Review all details ▾").performScrollTo().performClick()
        waitText("19:00"); waitText("20:00"); waitText("08:30")
        runBlocking { app.appPreferences.replace(AppPreferences(TimeFormat.TWELVE)) }
        waitText("7:00 PM"); waitText("8:00 PM"); waitText("8:30 AM")
        val result = runBlocking { repo.load(DateRange.day(date), zone) }
        assertEquals(fast, result.fasting.single()); assertEquals(meal, result.meals.single()); assertEquals(activity, result.activities.single())
        runBlocking { app.appPreferences.replace(AppPreferences()) }
        val expected = if (android.text.format.DateFormat.is24HourFormat(context)) "19:00" else "7:00 PM"
        waitText(expected)
    }
    @Test fun editorsAndActiveEndLabelUseTheSharedFormatReactively() {
        var screen by mutableIntStateOf(0)
        var format by mutableStateOf(ClockFormat())
        compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalClockFormat provides format) { Surface {
            when (screen) {
                0 -> FastingEditorScreen(FastingDraft.new(date, zone), false, null, {}, {}, {}, {})
                1 -> MealEditorScreen(MealDraft(date = date, time = LocalTime.of(19, 0)), emptyList(), false, null, {}, {}, {}, {})
                2 -> ActivityEditorScreen(ActivityDraft(date = date, time = LocalTime.of(19, 0)), emptyList(), false, null, {}, {}, {}, {})
                3 -> ActualMealEditor(ActualMealDraft.from(meal), meal, false, null, {}, {}, {})
                else -> TodayScreen(TodayState(AdherenceDay(date, zone), data), false, null, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, now)
            }
        } } } }
        for (index in 0..3) {
            compose.runOnIdle { screen = index; format = ClockFormat() }; waitText("7:00 PM")
            compose.runOnIdle { format = ClockFormat(true) }; waitText("19:00")
        }
        compose.runOnIdle { screen = 4 }
        compose.onNodeWithTag("fastingEnd-${fast.id}").assertTextEquals("Ends at 18:00")
        compose.runOnIdle { format = ClockFormat() }
        compose.onNodeWithTag("fastingEnd-${fast.id}").assertTextEquals("Ends at 6:00 PM")
    }
}
