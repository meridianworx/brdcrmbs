package com.ventures814.brdcrmbs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TodayFastingPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Boise")
    private val day = LocalDate.of(2026, 10, 23)
    private val clock = Clock.fixed(day.atTime(5, 5).atZone(zone).toInstant(), zone)
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private lateinit var database: PlanningDatabase
    private lateinit var model: TodayViewModel
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        val repo = RoomPlanningRepository(database, clock)
        runBlocking {
            repo.addFasting(FastingEvent("active", day.minusDays(1).atTime(19, 0).atZone(zone).toInstant(),
                day.atTime(11, 0).atZone(zone).toInstant(), zone, "16:8", "Progress QA · fast-thu"))
            repo.addFasting(FastingEvent("secondary", day.atTime(19, 0).atZone(zone).toInstant(),
                day.plusDays(1).atTime(19, 0).atZone(zone).toInstant(), zone, "24h", "Progress QA · fast-fri", FastingOutcome.MISSED))
        }
        model = TodayViewModel(repo, SavedStateHandle(), clock)
        owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TodayViewModel::class.java.name}", model)
    }
    @After fun cleanup() { compose.runOnIdle { owner.viewModelStore.clear() }; database.close() }
    private fun show(width: Dp, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalViewModelStoreOwner provides owner) {
                BrdcrmbsTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { Box(Modifier.width(width).fillMaxHeight()) { BrdcrmbsApp() } }
                } }
            }
        }
        compose.waitUntil(15_000) { !model.state.value.loading }
        compose.onNodeWithTag("fastingRemaining-active").performScrollTo().assertIsDisplayed()
    }
    private fun textFits(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout for $tag", layouts.isNotEmpty())
        // A shrink-wrapped Text may retain a wider paragraph container. Check the actual
        // laid-out lines and full text coverage rather than that container's width flag.
        layouts.forEach { result ->
            assertFalse("$tag height overflow", result.didOverflowHeight)
            assertEquals("$tag truncated text", result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
            for (line in 0 until result.lineCount) {
                assertFalse("$tag ellipsis", result.isLineEllipsized(line))
                assertTrue("$tag left clip", result.getLineLeft(line) >= -1f)
                assertTrue("$tag right clip", result.getLineRight(line) <= result.size.width + 1f)
                assertTrue("$tag bottom clip", result.getLineBottom(line) <= result.size.height + 1f)
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(750)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/today-aligned-$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
    }
    @Test fun approvedHierarchyAndOneActiveTreatmentWithCompactSecondaryRecord() {
        show(411.dp)
        val bar = compose.onNodeWithTag("fastingProgress-active").assertIsDisplayed().fetchSemanticsNode()
        val percentage = compose.onNodeWithTag("fastingPercent-active").assertTextEquals("63%").fetchSemanticsNode()
        val remaining = compose.onNodeWithTag("fastingRemaining-active").assertTextEquals("5h 55m remaining").fetchSemanticsNode()
        val end = compose.onNodeWithTag("fastingEnd-active").assertTextEquals("Ends at 11:00 AM").fetchSemanticsNode()
        val notes = compose.onNodeWithText("Progress QA · fast-thu").fetchSemanticsNode()
        assertEquals(0.6302083f, bar.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.0001f)
        assertTrue(percentage.boundsInRoot.left > bar.boundsInRoot.right)
        assertTrue(bar.boundsInRoot.center.y in percentage.boundsInRoot.top..percentage.boundsInRoot.bottom)
        assertTrue(remaining.boundsInRoot.top >= bar.boundsInRoot.bottom)
        assertTrue(end.boundsInRoot.left >= remaining.boundsInRoot.right)
        assertTrue(end.boundsInRoot.center.y in remaining.boundsInRoot.top..remaining.boundsInRoot.bottom)
        assertTrue(notes.boundsInRoot.top >= maxOf(remaining.boundsInRoot.bottom, end.boundsInRoot.bottom))
        compose.onNodeWithTag("status-fast-active").assertTextEquals("○ Planned")
        compose.onNodeWithTag("done-fast-active").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("missed-fast-active").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("fastingProgress-secondary").assertDoesNotExist()
        capture("approved")
        compose.onNodeWithTag("status-fast-secondary").performScrollTo().assertTextEquals("✕ Missed").assertIsDisplayed()
        compose.onNodeWithText("Meals").performScrollTo().assertIsDisplayed()
    }
    @Test fun narrowWidthWrapsEndLabelBelowRemainingWithoutClipping() {
        show(320.dp)
        val remaining = compose.onNodeWithTag("fastingRemaining-active").fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithTag("fastingEnd-active").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(end.top >= remaining.bottom)
        capture("narrow")
        textFits("fastingRemaining-active"); textFits("fastingEnd-active"); textFits("fastingPercent-active")
        compose.onNodeWithTag("missed-fast-secondary").performScrollTo().assertIsDisplayed()
    }
    @Test fun enlargedTextRetainsProgressAndReadableResponsiveEndLabel() {
        show(360.dp, fontScale = 1.5f)
        compose.onNodeWithTag("fastingPercent-active").assertTextEquals("63%")
        compose.onNodeWithTag("fastingRemaining-active").assertTextEquals("5h 55m remaining")
        val remaining = compose.onNodeWithTag("fastingRemaining-active").fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithTag("fastingEnd-active").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(end.top >= remaining.bottom)
        capture("large-text")
        textFits("fastingRemaining-active"); textFits("fastingEnd-active"); textFits("fastingPercent-active")
        compose.onNodeWithTag("done-fast-active").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }
}
