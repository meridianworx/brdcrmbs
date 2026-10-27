package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.ReminderSettingsStore
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.settings.*
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class DataManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var writes = 0
    private var backs = 0
    private var resetMessage: String? = null
    private var gate: CompletableDeferred<Unit>? = null
    private val operations = object : DataManagementOperations {
        override suspend fun manageData(action: DataAction): DataManagementResult {
            writes++; gate?.await(); return DataManagementResult(true)
        }
    }
    private val model = DataManagementViewModel(operations)
    private fun show() { compose.setContent { BrdcrmbsTheme { Surface {
        DataManagementScreen(back = { backs++ }, resetComplete = { resetMessage = it }, model = model)
    } } } }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag != "confirmDataAction") node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun cancel() = compose.onNodeWithText("Cancel").performClick()

    @Test fun cancellationsWriteNothingAndConfirmedClearProtectsControlsWhileBusy() {
        show(); click("clearPlanning")
        compose.onNodeWithText("Clear Planning & History?").assertIsDisplayed(); cancel()
        click("resetAppData"); cancel()
        click("resetAppData"); click("confirmDataAction")
        compose.onNodeWithText("Permanently reset app data?").assertIsDisplayed(); cancel()
        assertEquals(0, writes)
        gate = CompletableDeferred()
        click("clearPlanning"); click("confirmDataAction")
        compose.waitUntil(30_000) { model.state.value.busy }
        compose.onNodeWithTag("clearPlanning").assertIsNotEnabled()
        compose.onNodeWithTag("resetAppData").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back to Settings").assertIsNotEnabled()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(0, backs)
        assertEquals(1, writes)
        compose.runOnIdle { gate!!.complete(Unit) }
        compose.waitUntil(30_000) { !model.state.value.busy }
        compose.onNodeWithText("Planning and history cleared.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Settings").performClick(); assertEquals(1, backs)
    }

    @Test fun resetRequiresBothConfirmationsAndDispatchesSuccessOnlyAfterCompletion() {
        show(); click("resetAppData"); assertEquals(0, writes)
        compose.onNodeWithText("Reset App Data?").assertIsDisplayed()
        click("confirmDataAction"); assertEquals(0, writes)
        compose.onNodeWithText("Permanently reset app data?").assertIsDisplayed()
        compose.onNodeWithText("Exported backups will NOT be deleted.", substring = true).assertIsDisplayed()
        click("confirmDataAction")
        compose.waitUntil(30_000) { resetMessage != null }
        assertEquals(1, writes); assertEquals("App data reset.", resetMessage)
    }

    @Test fun realSettingsChildHidesNavigationAndResetReturnsToFreshTodayWithoutRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "data-ui-${UUID.randomUUID()}"
        val db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        val repo = RoomPlanningRepository(db)
        val settings = ReminderSettingsStore(context, name)
        val store = RoomBackupStore(db, settings)
        val file = File(context.cacheDir, "$name.json")
        val service = BackupService(store, settings, context.contentResolver, file, {})
        val owner = compose.activity
        var cleared = false
        fun put(value: ViewModel) = owner.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${value.javaClass.name}", value)
        put(SettingsViewModel(repo, SavedStateHandle())); put(DataManagementViewModel(service))
        owner.viewModelStore.put("stale-editor", object : ViewModel() { override fun onCleared() { cleared = true } })
        runBlocking { repo.addMeal(MealPlan(date = LocalDate.now(), text = "Must be removed")) }
        try {
            compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { Surface { BrdcrmbsApp() } } } }
            compose.onNode(isSelectable() and hasText("Settings")).performClick()
            compose.onNodeWithText("Data Management").performScrollTo().performClick()
            compose.onNodeWithTag("clearPlanning").assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Settings")).assertDoesNotExist()
            compose.onNodeWithContentDescription("Back to Settings").performClick()
            compose.onNode(isSelectable() and hasText("Settings")).assertIsSelected()
            compose.onNodeWithText("Data Management").performScrollTo().performClick()
            click("resetAppData"); click("confirmDataAction"); click("confirmDataAction")
            compose.waitUntil(30_000) { cleared && compose.onAllNodes(isSelectable() and hasText("Today")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(isSelectable() and hasText("Today")).assertIsSelected()
            assertEquals(PlanningData(), runBlocking { repo.load(DateRange.day(LocalDate.now()), java.time.ZoneId.systemDefault()) })
            compose.onNodeWithText("App data reset.").assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Settings")).performClick()
            compose.onNodeWithText("Saved Meals").assertIsDisplayed()
        } finally {
            compose.runOnIdle { owner.viewModelStore.clear() }; db.close(); file.delete()
            listOf(name, "${name}_permission").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        }
    }
}
