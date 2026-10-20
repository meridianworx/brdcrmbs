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
import com.ventures814.brdcrmbs.ui.week.WeekRoute
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class FastingUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository
    private lateinit var model: WeekViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PlanningDatabase::class.java).build()
        repository = RoomPlanningRepository(database)
        model = WeekViewModel(repository, SavedStateHandle(), Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("ui", model)
        compose.setContent { BrdcrmbsTheme { Surface { WeekRoute(Modifier.fillMaxSize(), model) } } }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        database.close()
    }

    private fun add() { compose.onAllNodesWithText("+ Add")[0].performClick() }
    private fun save() {
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.editor.value == null && !model.editorBusy.value }
    }
    private fun edit(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitUntil(10_000) { model.editor.value != null && !model.editorLoading.value }
    }

    @Test fun backDoesNotSaveThenAddEditAndConfirmedDeleteReturnToWeek() {
        add()
        compose.onNodeWithText("Add Fast").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Week").performClick()
        compose.onNodeWithText("My Week").assertIsDisplayed()
        runBlocking { assertTrue(repository.load(testRange, testZone).fasting.isEmpty()) }
        add(); save()
        compose.waitUntil(10_000) { model.state.value.fasting.size == 1 }
        edit("16:8 · 16 hour fast")
        compose.onNodeWithText("Edit Fast").assertIsDisplayed()
        compose.onNodeWithText("24h").performScrollTo().performClick()
        save()
        compose.waitUntil(10_000) { model.state.value.fasting.singleOrNull()?.subtitle == "24h · 24 hour fast" }
        edit("24h · 24 hour fast")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete fasting plan?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertEquals(1, repository.load(testRange, testZone).fasting.size) }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithTag("confirmDeleteFast").performClick()
        compose.waitUntil(10_000) { model.editor.value == null && model.state.value.fasting.isEmpty() }
        compose.onNodeWithText("My Week").assertIsDisplayed()
        runBlocking { assertTrue(repository.load(testRange, testZone).fasting.isEmpty()) }
    }

    @Test fun noFastDisplaysAndOpensItsOwnEditorWithoutCreatingAnEvent() {
        add()
        compose.onNodeWithText("No fast").performScrollTo().performClick()
        compose.onNodeWithText("Planned day").assertExists()
        compose.onNodeWithText("End time").assertDoesNotExist()
        save()
        compose.waitUntil(10_000) { model.state.value.fasting.singleOrNull()?.title == "No fast" }
        edit("No fast")
        compose.onNodeWithText("Edit Fast").assertIsDisplayed()
        runBlocking {
            val data = repository.load(testRange, testZone)
            assertTrue(data.fasting.isEmpty()); assertEquals(1, data.noFast.size)
        }
    }

    @Test fun dateAndTimePickerCancelKeepDraftAndDatabaseUnchanged() {
        add()
        val original = model.editor.value
        compose.onNodeWithTag("fastStartDate").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(original, model.editor.value)
        compose.onNodeWithTag("fastStartTime").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(original, model.editor.value)
        runBlocking { assertEquals(PlanningData(), repository.load(testRange, testZone)) }
    }
}
