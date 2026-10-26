package com.ventures814.brdcrmbs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import com.ventures814.brdcrmbs.ui.fasting.*
import com.ventures814.brdcrmbs.ui.week.*
import java.io.File
import java.time.*
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.*
import org.junit.Assert.*

class AppPreferencesPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "app-pref-test-${UUID.randomUUID()}"
    private lateinit var db: PlanningDatabase
    private lateinit var prefs: AppPreferencesStore
    private lateinit var reminders: ReminderSettingsStore
    private lateinit var store: RoomBackupStore
    private lateinit var service: BackupService
    private val models = ViewModelStore()
    private val directory by lazy { File(context.cacheDir, name).apply { mkdirs() } }
    private val journal get() = File(directory, "journal.json")
    private val choice = AppPreferences(TimeFormat.TWENTY_FOUR, FastPreset.H48, 6)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        prefs = AppPreferencesStore(context, name)
        reminders = ReminderSettingsStore(context, "$name-reminders")
        store = RoomBackupStore(db, reminders, prefs)
        service = BackupService(store, reminders, context.contentResolver, journal, {})
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { models.clear() }; db.close()
        listOf(name, "$name-reminders", "$name-reminders_permission").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
    }
    @Test fun preferencesPersistWithoutChangingPlansAndNewDraftOnlyUsesDefault() = runBlocking {
        val repo = RoomPlanningRepository(db)
        val original = richBackupSnapshot()
        store.replace(original)
        assertEquals(AppPreferences(), prefs.state.value)
        prefs.replace(choice)
        assertEquals(choice, AppPreferencesStore(context, name).state.value)
        assertEquals(BackupCodec.encode(original.copy(appPreferences = choice)), BackupCodec.encode(store.snapshot(original.createdAt)))
        val clock = Clock.fixed(Instant.parse("2030-01-07T20:00:00Z"), ZoneId.of("America/Denver"))
        val model = WeekViewModel(repo, SavedStateHandle(), clock, { prefs.state.value.fastingPreset })
        models.put("week", model)
        instrumentation.runOnMainSync { model.addFast() }
        assertEquals(FastPreset.H48, model.editor.value!!.preset)
        assertEquals(48, model.editor.value!!.duration().toHours().toInt())
        assertEquals(original.plans.fasting.size, store.snapshot(original.createdAt).plans.fasting.size)
        val existing = original.plans.fasting.first()
        instrumentation.runOnMainSync { model.editFast(existing.id) }
        withTimeout(30_000) { model.editorLoading.first { !it } }
        assertEquals(existing.start, model.editor.value!!.startInstant())
        assertEquals(existing.end, model.editor.value!!.endInstant())
        assertEquals(FastingDraft.from(existing), model.editor.value)
        // Changing defaults does not bypass repository authority.
        assertTrue(runCatching { repo.addFasting(existing.copy(id = "conflict", outcome = FastingOutcome.PLANNED)) }.isFailure)
        val occupied = existing.start.atZone(existing.zone).toLocalDate()
        assertTrue(runCatching { repo.addFastingPlan(NoFastPlan("no-fast-conflict", occupied, existing.zone)) }.isFailure)
        assertEquals(BackupCodec.encode(original.copy(appPreferences = choice)), BackupCodec.encode(store.snapshot(original.createdAt)))
    }
    @Test fun optionalFormatOnePreferencesAndOldBackupsKeepReminderChoices() = runBlocking {
        val original = richBackupSnapshot().copy(appPreferences = choice)
        val encoded = BackupCodec.encode(original)
        val decoded = BackupCodec.decode(encoded)
        assertEquals(choice, decoded.appPreferences); assertEquals(original.reminders, decoded.reminders)
        assertEquals(1, JSONObject(encoded).getInt("formatVersion"))
        val old = JSONObject(encoded).apply { remove("appPreferences") }.toString()
        val legacy = BackupCodec.decode(old)
        assertEquals(AppPreferences(), legacy.appPreferences); assertEquals(original.reminders, legacy.reminders)
        service.restore(decoded)
        assertEquals(choice, AppPreferencesStore(context, name).state.value)
        service.restore(legacy)
        assertEquals(AppPreferences(), prefs.state.value); assertEquals(original.reminders, reminders.state.value)
        assertEquals(4, db.openHelper.writableDatabase.version)
    }
    @Test fun invalidPreferenceFieldsAreRejectedBeforeAnyReplacement() = runBlocking {
        prefs.replace(choice)
        val encoded = BackupCodec.encode(BackupSnapshot(Instant.EPOCH, appPreferences = choice))
        listOf("mealCount" to 7, "mealCount" to "3", "timeFormat" to "UNKNOWN", "fastingPreset" to "CUSTOM", "fastingPreset" to "NO_FAST").forEach { (key, value) ->
            val invalid = JSONObject(encoded).apply { getJSONObject("appPreferences").put(key, value) }
            assertTrue(runCatching { BackupCodec.decode(invalid.toString()) }.isFailure)
        }
        assertEquals(choice, prefs.state.value)
    }
    @Test fun postPreferenceDeferredSqlFailureCompensatesBothPreferenceStores() = runBlocking {
        val original = richBackupSnapshot().copy(appPreferences = choice)
        store.replace(original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_commit AFTER INSERT ON saved_meals BEGIN INSERT INTO activity_plans (id,dateEpochDay,timeNanoOfDay,name,type,notes,outcome,rescheduledFromId) VALUES ('injected',0,NULL,'Injected','WORKOUT',NULL,'PLANNED','missing-parent'); END")
        db.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
        val target = BackupSnapshot(original.createdAt, savedMeals = listOf(SavedMeal("new", "New")))
        assertTrue(runCatching { service.restore(target) }.isFailure)
        assertEquals(choice, AppPreferencesStore(context, name).state.value)
        assertEquals(original.reminders, ReminderSettingsStore(context, "$name-reminders").state.value)
        assertEquals(BackupCodec.encode(original), BackupCodec.encode(store.snapshot(original.createdAt)))
        assertFalse(journal.exists())
    }
    @Test fun recoveryFinishesOrRollsBackBothConfigurationStoresIncludingOldJournal() = runBlocking {
        val original = richBackupSnapshot().copy(appPreferences = choice)
        val target = BackupSnapshot(original.createdAt)
        fun writeJournal() {
            val canonical = BackupCodec.encode(target.copy(createdAt = Instant.EPOCH, reminders = ReminderPreferences(), appPreferences = AppPreferences()))
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
            journal.writeText(JSONObject().put("target", digest)
                .put("before", JSONArray(original.reminders.enabled.map { it.name })).put("after", JSONArray())
                .put("beforeApp", BackupCodec.encodePreferences(choice)).put("afterApp", BackupCodec.encodePreferences(AppPreferences())).toString())
        }
        store.replace(original); writeJournal(); prefs.replace(AppPreferences()); service.recover()
        assertEquals(choice, prefs.state.value); assertEquals(original.reminders, reminders.state.value)
        writeJournal(); store.replace(target); prefs.replace(choice); service.recover(); service.recover()
        assertEquals(AppPreferences(), prefs.state.value); assertTrue(reminders.state.value.enabled.isEmpty())
        // Existing pre-preferences journals remain readable and recover with documented defaults.
        writeJournal(); val old = JSONObject(journal.readText()).apply { remove("beforeApp"); remove("afterApp") }
        journal.writeText(old.toString()); service.recover(); assertEquals(AppPreferences(), prefs.state.value)
    }

    @Test fun notificationFormattingCannotCancelAnotherDelayedEligibleReminder() = runBlocking {
        val zone = ZoneId.of("America/Denver")
        val start = LocalDate.of(2030, 1, 7).atTime(19, 0).atZone(zone).toInstant()
        val first = FastingEvent("first", start, start.plusSeconds(60), zone, "Custom")
        val second = FastingEvent("second", start.plusSeconds(120), start.plusSeconds(120 + 16 * 3600), zone, "16:8")
        val repo = RoomPlanningRepository(db)
        repo.addFasting(first); repo.addFasting(second)
        reminders.replaceForRestore(ReminderPreferences(setOf(ReminderType.FAST_START)))
        prefs.replace(choice)
        val registrations = mutableMapOf<String, PlanReminder>()
        val consumed = mutableMapOf<String, Instant>()
        val posted = mutableListOf<PlanReminder>()
        val backend = object : ReminderBackend {
            override fun pending() = registrations.toMap()
            override fun delivered() = consumed.toMap()
            override fun schedule(reminder: PlanReminder) { registrations[reminder.key] = reminder }
            override fun cancel(key: String) { registrations.remove(key) }
            override fun post(reminder: PlanReminder) { posted.add(reminder) }
            override fun markDelivered(reminder: PlanReminder) { consumed[reminder.key] = reminder.at }
        }
        val data = PlanningData(fasting = listOf(first, second))
        val eligible = ReminderPolicy.eligible(data, reminders.state.value, zone)
        // Both were registered earlier and now arrive within the existing one-hour grace period.
        eligible.forEach(backend::schedule)
        val engine = ReminderCoordinator(repo, reminders, backend, { true },
            { Clock.fixed(start.plusSeconds(300), zone) }, { ClockFormat(true) })
        try {
            engine.deliver("FAST_START:first", first.start)
            assertEquals(1, posted.size); assertTrue(posted.single().body.contains("19:01"))
            assertTrue("Display formatting must not remove another eligible due alarm", "FAST_START:second" in backend.pending())
            engine.deliver("FAST_START:second", second.start)
            assertEquals(2, posted.size); assertTrue(posted.last().body.contains("11:02"))
            assertFalse(posted.last().body.contains("AM")); assertTrue(backend.pending().isEmpty())
            assertEquals(data.fasting.toSet(), repo.load(DateRange.day(LocalDate.of(2030, 1, 7)), zone).fasting.toSet())
        } finally { engine.close() }
    }
}
