package com.ventures814.brdcrmbs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.*
import com.ventures814.brdcrmbs.domain.DateRange
import com.ventures814.brdcrmbs.ui.screens.*
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ProgressQaToolsUiTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Denver")
    private val clock = Clock.fixed(Instant.parse("2026-10-22T18:00:00Z"), zone)
    private lateinit var db: PlanningDatabase
    private lateinit var model: QaToolsModel
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        model = QaToolsModel(ProgressQaSeeder(db, clock))
        compose.setContent { BrdcrmbsTheme { ProgressQaTools(model) } }
    }
    @After fun cleanup() { db.close() }
    private fun count() = runBlocking { RoomPlanningRepository(db).load(DateRange.weekContaining(LocalDate.now(clock)), zone).meals.size }
    @Test fun controlsRequireConfirmationAndCancelDoesNotWrite() {
        compose.onNodeWithText("Progress Test Data").assertIsDisplayed()
        compose.onNodeWithText("Seed Test Week").assertHasClickAction().performClick()
        compose.onNodeWithText("Seed Progress test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, count())
        compose.onNodeWithText("Reset Test Data").performClick()
        compose.onNodeWithText("Remove seeded test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, count())
    }
    @Test fun confirmedSeedAndResetOperateOnRealRoomData() {
        compose.onNodeWithText("Seed Test Week").performClick()
        compose.onNodeWithText("Seed", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { !model.busy && model.pending == null }
        compose.onNodeWithText("Test week seeded.").assertIsDisplayed()
        assertEquals(9, count())
        compose.onNodeWithText("Reset Test Data").performClick()
        compose.onNodeWithText("Reset", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { !model.busy && model.pending == null }
        assertEquals(0, count())
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Test data removed.").fetchSemanticsNodes().isNotEmpty() }
    }
}
