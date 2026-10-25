package com.ventures814.brdcrmbs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.*
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.screens.ProgressQaTools
import com.ventures814.brdcrmbs.ui.screens.QaToolsModel
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.today.TodayRoute
import com.ventures814.brdcrmbs.ui.today.TodayViewModel
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ActiveFastQaUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private lateinit var seeder: ProgressQaSeeder
    private val store = ViewModelStore()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db); seeder = ProgressQaSeeder(db)
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    @Test fun debugControlConfirmsSeedsAndResetRemovesOnlyOwnedScenario() {
        val model = QaToolsModel(seeder); store.put("qa", model)
        compose.setContent { BrdcrmbsTheme { ProgressQaTools(model) } }
        compose.onNodeWithText("Seed Active Fast").assertHasClickAction().performClick()
        compose.onNodeWithText("Seed active test fast?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(runBlocking { repo.getFastingPlan(ActiveFastQaOwnership.ID) })
        compose.onNodeWithText("Seed Active Fast").performClick()
        compose.onAllNodesWithText("Seed Active Fast").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.waitUntil(30_000) { !model.busy && model.pending == null }
        assertNotNull(runBlocking { repo.getFastingPlan(ActiveFastQaOwnership.ID) })
        compose.onNodeWithText("Reset Test Data").performClick()
        compose.onNodeWithText("Reset", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { !model.busy && model.pending == null }
        assertNull(runBlocking { repo.getFastingPlan(ActiveFastQaOwnership.ID) })
    }
    @Test fun currentInstantSeederDrivesExactlyOneApprovedLiveTreatmentAndWorkingOutcomes() {
        runBlocking { seeder.seedActiveFast() }
        val model = TodayViewModel(repo, SavedStateHandle()); store.put("today", model)
        compose.setContent { BrdcrmbsTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TodayRoute(model = model) }
        } }
        compose.waitUntil(30_000) { !model.state.value.loading && model.state.value.plans.fasting.size == 1 }
        val id = ActiveFastQaOwnership.ID
        compose.onNodeWithTag("fastingProgress-$id").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fastingPercent-$id").assertTextEquals("62%")
        val remaining = compose.onNodeWithTag("fastingRemaining-$id").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertTrue(remaining == "6h remaining" || remaining == "5h 59m remaining")
        compose.onNodeWithText("Ends", substring = true).assertExists()
        compose.onNodeWithTag("status-fast-$id").assertTextEquals("○ Planned")
        compose.onNodeWithTag("done-fast-$id").assertExists()
        compose.onNodeWithTag("missed-fast-$id").assertExists()
        compose.onAllNodes(hasContentDescription("Fasting time progress")).assertCountEquals(1)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(750)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/active-fast-qa-today.png").close()
        compose.onNodeWithTag("done-fast-$id").performScrollTo().performClick()
        compose.waitUntil(30_000) { model.state.value.plans.fasting.single().outcome == FastingOutcome.DONE }
        compose.onNodeWithTag("fastingProgress-$id").assertDoesNotExist()
        compose.onNodeWithTag("missed-fast-$id").performScrollTo().performClick()
        compose.waitUntil(30_000) { model.state.value.plans.fasting.single().outcome == FastingOutcome.MISSED }
        compose.onNodeWithTag("status-fast-$id").assertTextEquals("✕ Missed")
        assertEquals(Clock.systemDefaultZone().zone, (runBlocking { repo.getFastingPlan(id) } as FastingEvent).zone)
    }
}
