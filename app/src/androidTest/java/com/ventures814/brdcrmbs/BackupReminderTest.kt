package com.ventures814.brdcrmbs

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.io.File
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BackupReminderTest {
    @Test fun restoreReconcilesNewPreferencesAndFuturePlansWithoutReplayingPastAlarmsOrChangingPermission() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        val name = "backup-reminders-${java.util.UUID.randomUUID()}"
        val settings = ReminderSettingsStore(context, name)
        val clock = Clock.fixed(Instant.parse("2026-10-25T14:00:00Z"), ZoneId.of("America/Denver"))
        val pending = mutableMapOf<String, PlanReminder>()
        val backend = object : ReminderBackend {
            override fun pending() = pending.toMap()
            override fun delivered() = emptyMap<String, Instant>()
            override fun schedule(reminder: PlanReminder) { pending[reminder.key] = reminder }
            override fun cancel(key: String) { pending.remove(key) }
            override fun post(reminder: PlanReminder) { fail("Restore must not post notifications") }
            override fun markDelivered(reminder: PlanReminder) { fail("Restore must not fabricate delivery") }
        }
        var permission = true
        val repo = RoomPlanningRepository(db)
        val coordinator = ReminderCoordinator(repo, settings, backend, { permission }, { clock })
        val file = File(context.noBackupFilesDir, "$name.json")
        try {
            val day = LocalDate.now(clock)
            val store = RoomBackupStore(db, settings)
            val service = BackupService(store, settings, context.contentResolver, file,
                { coordinator.reconcile(recovery = true) }, { coordinator.withRestoreBoundary(it) }, clock,
                restoreBoundary = coordinator::restoreAndReconcile)
            val original = BackupSnapshot(clock.instant(), PlanningData(meals = listOf(MealPlan("old", day, LocalTime.NOON, "Old"))), reminders = ReminderPreferences(setOf(ReminderType.MEAL)))
            store.replace(original); coordinator.reconcile(); assertTrue("MEAL:old" in pending)
            val start = clock.instant().plusSeconds(3600)
            val restored = BackupSnapshot(clock.instant(), PlanningData(
                fasting = listOf(FastingEvent("future-fast", start, start.plusSeconds(57600), clock.zone)),
                meals = listOf(MealPlan("future", day, LocalTime.NOON, "Restored"), MealPlan("past", day, LocalTime.of(7, 0), "Past"))),
                reminders = ReminderPreferences(setOf(ReminderType.FAST_END, ReminderType.MEAL)))
            assertTrue(service.restore(restored).remindersReconciled)
            assertEquals(setOf("FAST_END:future-fast", "MEAL:future"), pending.keys)
            assertTrue(pending.values.all { it.at > clock.instant() })
            assertEquals(restored.reminders, settings.state.value); assertFalse(settings.asked)
            assertEquals(2, repo.load(DateRange.day(day), clock.zone).meals.size)
            permission = false
            service.restore(restored)
            assertTrue(pending.isEmpty()); assertEquals(restored.reminders, settings.state.value); assertFalse(permission)
        } finally {
            coordinator.close(); db.close(); file.delete()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
