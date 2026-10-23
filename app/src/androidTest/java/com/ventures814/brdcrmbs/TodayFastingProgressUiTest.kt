package com.ventures814.brdcrmbs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TodayFastingProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 23)
    private val planningZone = ZoneId.of("America/Denver")
    private val clock = object : Clock() {
        @Volatile var now = day.atTime(7, 0).atZone(planningZone).toInstant()
        override fun instant() = now
        override fun getZone() = planningZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var database: PlanningDatabase
    private lateinit var repo: PlanningRepository
    private lateinit var model: TodayViewModel
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(database, clock)
        model = TodayViewModel(repo, SavedStateHandle(), clock, ticks)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TodayViewModel::class.java.name}", model)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${WeekViewModel::class.java.name}", WeekViewModel(repo, SavedStateHandle(), clock))
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; database.close() }
    private fun fast(hours: Long = 16, elapsed: Long = 12, id: String = "fast", outcome: FastingOutcome = FastingOutcome.PLANNED): FastingEvent {
        val start = clock.now.minus(Duration.ofHours(elapsed))
        return FastingEvent(id, start, start.plus(Duration.ofHours(hours)), planningZone, if (hours == 16L) "16:8" else "${hours}h", "Keep schedule and notes", outcome)
    }
    private fun add(vararg events: FastingEvent) = runBlocking { events.forEach { repo.addFasting(it) } }
    private fun show() {
        compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { BrdcrmbsApp() } } }
        compose.waitUntil(15_000) { !model.state.value.loading && ticks.subscriptionCount.value == 1 }
    }
    private fun bar(id: String = "fast") = compose.onNodeWithTag("fastingProgress-$id")
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Compose semantics can settle before the emulator compositor presents its frame.
        android.os.SystemClock.sleep(750)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/today-progress-$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
    }
    private fun tickAt(now: Instant) {
        clock.now = now
        assertTrue(ticks.tryEmit(Unit))
        compose.waitUntil(15_000) { model.fastingTime.value == now }
    }

    @Test fun activeUpdatesEndsWithoutWritingAndOutcomeControlsStillWork() {
        val event = fast(); add(event); show()
        bar().performScrollTo().assertIsDisplayed()
        assertEquals(0.75f, bar().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)
        compose.onNodeWithTag("fastingPercent-fast").assertTextEquals("75%")
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("4h remaining")
        compose.onNodeWithText("Ends at 11:00 AM").assertExists()
        screenshot("active-16-8")
        tickAt(clock.now.plusSeconds(60))
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("3h 59m remaining")
        tickAt(event.end)
        bar().assertDoesNotExist()
        compose.onNodeWithText("Planned time complete · 100%").assertExists()
        assertEquals(event, runBlocking { repo.getFastingPlan(event.id) })
        screenshot("ended")
        compose.onNodeWithTag("done-fast-fast").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.plans.fasting.single().outcome == FastingOutcome.DONE }
        compose.onNodeWithTag("status-fast-fast").assertTextEquals("✓ Done")
        compose.onNodeWithTag("missed-fast-fast").performScrollTo().performClick()
        compose.waitUntil(15_000) { model.state.value.plans.fasting.single().outcome == FastingOutcome.MISSED }
        compose.onNodeWithTag("status-fast-fast").assertTextEquals("✕ Missed")
        assertEquals(event.copy(outcome = FastingOutcome.MISSED), runBlocking { repo.getFastingPlan(event.id) })
    }

    @Test fun upcomingIsSimpleAndStartsWhenClockReachesStart() {
        val event = fast(elapsed = -3); add(event); show()
        bar().assertDoesNotExist()
        compose.onNodeWithTag("status-fast-fast").assertTextEquals("○ Planned")
        screenshot("upcoming")
        tickAt(event.start)
        bar().assertExists()
        compose.onNodeWithTag("fastingPercent-fast").assertTextEquals("0%")
        assertEquals(event, runBlocking { repo.getFastingPlan(event.id) })
    }

    @Test fun resolvedAndNoFastNeverGetLiveTreatment() {
        add(fast(outcome = FastingOutcome.MISSED), fast(id = "done", outcome = FastingOutcome.DONE))
        runBlocking { repo.addFastingPlan(NoFastPlan(date = day, zone = planningZone, notes = "Intentional")) }
        show(); bar().assertDoesNotExist(); bar("done").assertDoesNotExist()
        compose.onNodeWithTag("status-fast-fast").assertTextEquals("✕ Missed")
        compose.onNodeWithTag("status-fast-done").assertTextEquals("✓ Done")
        screenshot("missed")
        compose.onNodeWithText("— No fast").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Intentionally planned as No fast").assertExists()
    }

    @Test fun continuousMultiDayFastKeepsOneEventAndClearEndDate() {
        val event = fast(hours = 72, elapsed = 21); add(event); show()
        bar().performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fastingPercent-fast").assertTextEquals("29%")
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("2d 3h remaining")
        compose.onNodeWithText("Ends Oct 25 at 10:00 AM").assertExists()
        screenshot("multi-day")
        assertEquals(listOf(event), runBlocking { repo.load(DateRange.day(day), planningZone).fasting })
    }

    @Test fun overlappingVisibleRecordsHaveOnlyOneLiveBar() {
        add(fast(id = "older"), fast(elapsed = 8, id = "latest"), fast(elapsed = -2, id = "future"), fast(hours = 4, elapsed = 6, id = "ended"))
        show()
        bar("latest").assertExists(); bar("older").assertDoesNotExist(); bar("future").assertDoesNotExist(); bar("ended").assertDoesNotExist()
        assertEquals(4, model.state.value.plans.fasting.size)
    }

    @Test fun leavingStopsClockCollectionAndReentryRecalculatesWithoutTick() {
        add(fast()); show()
        compose.onNode(isSelectable() and hasText("Week")).performClick()
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 0 }
        clock.now = clock.now.plus(Duration.ofHours(2))
        compose.onNode(isSelectable() and hasText("Today")).performClick()
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 1 && model.fastingTime.value == clock.now }
        compose.onNodeWithTag("fastingPercent-fast").assertTextEquals("87%")
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("2h remaining")
    }

    @Test fun foregroundRefreshReadsClockWithoutWaitingForMinuteTick() {
        add(fast()); show()
        clock.now = clock.now.plus(Duration.ofMinutes(30))
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(15_000) { model.fastingTime.value == clock.now && !model.state.value.loading }
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("3h 30m remaining")
    }

    @Test fun lifecycleStopCancelsTickerAndResumeImmediatelyReadsClock() {
        add(fast())
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent { BrdcrmbsTheme {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) { TodayRoute(model = model) }
        } }
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 1 && !model.state.value.loading }
        compose.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 0 }
        clock.now = clock.now.plus(Duration.ofHours(1))
        compose.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 1 && model.fastingTime.value == clock.now && !model.state.value.loading }
        compose.onNodeWithTag("fastingRemaining-fast").assertTextEquals("3h remaining")
        compose.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED }
        compose.waitUntil(15_000) { ticks.subscriptionCount.value == 0 }
    }
}
