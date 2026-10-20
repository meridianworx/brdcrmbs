package com.ventures814.brdcrmbs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.fasting.*
import com.ventures814.brdcrmbs.ui.week.WeekViewModel
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class FastingWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "fasting-crud-${UUID.randomUUID()}.db"
    private lateinit var database: PlanningDatabase
    private lateinit var repository: PlanningRepository
    private val store = ViewModelStore()
    @Before fun setup() { open() }
    private fun open() {
        database = Room.databaseBuilder(context, PlanningDatabase::class.java, name).addMigrations(PlanningDatabase.MIGRATION_1_2).build()
        repository = RoomPlanningRepository(database)
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        database.close(); context.deleteDatabase(name)
    }

    @Test fun addReloadEditWithoutDuplicateAndDeleteOnlySelectedRecord() = runBlocking {
        val first = FastingDraft.new(testDate, testZone).toPlan() as FastingEvent
        val other = first.copy(id = "other")
        repository.addFastingPlan(first); repository.addFastingPlan(other)
        database.close(); open()
        assertEquals(first, repository.getFastingPlan(first.id))
        val changed = first.copy(end = first.end.plusSeconds(3600), preset = "Custom")
        repository.updateFastingPlan(changed)
        assertEquals(changed, repository.getFastingPlan(first.id))
        assertEquals(2, repository.load(testRange, testZone).fasting.size)
        repository.deleteFastingPlan(first.id)
        assertNull(repository.getFastingPlan(first.id))
        assertEquals(listOf(other), repository.load(testRange, testZone).fasting)
    }

    @Test fun longFastsStayOneRecordAndNoFastCanConvertWithoutChangingIdentity() = runBlocking {
        for (hours in listOf(24L, 48L, 72L, 211L)) {
            val start = testDate.atTime(19, 0).atZone(testZone).toInstant()
            val event = FastingEvent(start = start, end = start.plus(Duration.ofHours(hours)), zone = testZone, preset = "Custom")
            repository.addFastingPlan(event)
            assertEquals(listOf(event), repository.load(DateRange(testDate, testDate.plusDays(20)), testZone).fasting)
            assertEquals(event.id, repository.load(DateRange.day(testDate.plusDays(1)), testZone).fasting.single().id)
            val noFast = NoFastPlan(event.id, testDate, testZone)
            repository.updateFastingPlan(noFast)
            val loaded = repository.load(testRange, testZone)
            assertTrue(loaded.fasting.isEmpty()); assertEquals(listOf(noFast), loaded.noFast)
            database.close(); open()
            assertEquals(noFast, repository.getFastingPlan(event.id))
            repository.updateFastingPlan(event)
            assertEquals(listOf(event), repository.load(DateRange(testDate, testDate.plusDays(20)), testZone).fasting)
            assertTrue(repository.load(testRange, testZone).noFast.isEmpty())
            repository.deleteFastingPlan(event.id)
        }
        assertEquals(PlanningData(), repository.load(testRange, testZone))
        val explicit = NoFastPlan(date = testDate, zone = testZone)
        repository.addFastingPlan(explicit)
        assertEquals(listOf(explicit), repository.load(DateRange.day(testDate), testZone).noFast)
        assertTrue(repository.load(DateRange.day(testDate.plusDays(1)), testZone).noFast.isEmpty())
        repository.deleteFastingPlan(explicit.id)
        assertEquals(PlanningData(), repository.load(testRange, testZone))
    }

    @Test fun recordedOutcomesCannotBeEditedConvertedOrDeleted() = runBlocking {
        val event = (FastingDraft.new(testDate, testZone).toPlan() as FastingEvent).copy(outcome = FastingOutcome.MISSED)
        repository.addFasting(event)
        for (operation in listOf<suspend () -> Unit>(
            { repository.updateFastingPlan(event.copy(outcome = FastingOutcome.PLANNED)) },
            { repository.updateFastingPlan(NoFastPlan(event.id, testDate, testZone)) },
            { repository.deleteFastingPlan(event.id) }
        )) {
            try { operation(); fail("Recorded history must not change") } catch (_: IllegalStateException) { }
            assertEquals(event, repository.getFastingPlan(event.id))
        }
    }

    @Test fun viewModelRejectsInvalidRangeAndWeekReactsToSaveEditAndDelete() = runBlocking {
        val model = WeekViewModel(repository, SavedStateHandle(), Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone))
        store.put("workflow", model)
        withTimeout(10_000) { model.state.first { !it.loading } }
        instrumentation.runOnMainSync {
            model.addFast()
            model.updateDraft { it.changeEnd(it.start.minusMinutes(1)) }
            model.saveFast()
        }
        assertNotNull(model.editorError.value)
        assertTrue(repository.load(testRange, testZone).fasting.isEmpty())
        instrumentation.runOnMainSync {
            model.updateDraft { it.selectPreset(FastPreset.H16) }
            model.saveFast()
        }
        val row = withTimeout(10_000) { model.state.first { it.fasting.isNotEmpty() } }.fasting.single()
        withTimeout(10_000) { model.editorBusy.first { !it } }
        assertNull(model.editor.value)
        instrumentation.runOnMainSync { model.editFast(row.planId!!) }
        withTimeout(10_000) { model.editor.first { it != null } }
        instrumentation.runOnMainSync { model.updateDraft { it.selectPreset(FastPreset.H48) }; model.saveFast() }
        withTimeout(10_000) { model.editorBusy.first { !it } }
        assertEquals(1, repository.load(testRange, testZone).fasting.size)
        assertEquals(row.planId, repository.load(testRange, testZone).fasting.single().id)
        instrumentation.runOnMainSync { model.editFast(row.planId!!) }
        withTimeout(10_000) { model.editor.first { it != null } }
        instrumentation.runOnMainSync { model.deleteFast() }
        withTimeout(10_000) { model.state.first { it.fasting.isEmpty() } }
        assertNull(repository.getFastingPlan(row.planId!!))
    }
}
