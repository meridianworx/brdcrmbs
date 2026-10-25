package com.ventures814.brdcrmbs

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.io.File
import java.time.*
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class BackupPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "backup-test-${UUID.randomUUID()}"
    private lateinit var db: PlanningDatabase
    private lateinit var settings: ReminderSettingsStore
    private lateinit var store: RoomBackupStore
    private lateinit var service: BackupService
    private lateinit var directory: File
    private val original = richBackupSnapshot()
    private var reconciled = 0
    private val journal get() = File(directory, "journal.json")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        settings = ReminderSettingsStore(context, name); store = RoomBackupStore(db, settings)
        directory = File(context.cacheDir, name).apply { mkdirs() }
        service = BackupService(store, settings, context.contentResolver, journal, { reconciled++ }, clock = Clock.fixed(original.createdAt, ZoneId.of("America/Denver")))
    }
    @After fun cleanup() {
        db.close()
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("${name}_permission", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private suspend fun current() = store.snapshot(original.createdAt, excludeQa = false)
    private suspend fun equal(expected: BackupSnapshot) { assertEquals(BackupCodec.encode(expected), BackupCodec.encode(current())) }

    @Test fun completeSafCompatibleFileRoundTripReplacesInsteadOfMergingAndPreservesPreferences() = runBlocking {
        store.replace(original); settings.markAsked()
        val prepared = service.create()
        assertEquals("brdcrmbs-backup-2026-10-25.json", prepared.filename)
        val file = File(directory, prepared.filename); val uri = Uri.fromFile(file)
        service.write(uri, prepared)
        val selected = service.read(uri)
        store.replace(BackupSnapshot(original.createdAt, PlanningData(meals = listOf(MealPlan("not-in-backup", LocalDate.now(), text = "Replace me")))))
        assertTrue(service.restore(selected).remindersReconciled)
        equal(original); assertEquals(1, reconciled)
        assertEquals(original.reminders, ReminderSettingsStore(context, name).state.value)
        assertTrue(settings.asked) // Device permission request history is neither imported nor cleared.
        assertFalse(journal.exists()); assertEquals(4, db.openHelper.writableDatabase.version)
    }
    @Test fun emptyBackupReplacesAllSixTablesAndUserPreferenceChoices() = runBlocking {
        store.replace(original)
        val empty = BackupSnapshot(original.createdAt)
        service.restore(BackupCodec.decode(BackupCodec.encode(empty)))
        equal(empty); assertTrue(settings.state.value.enabled.isEmpty())
    }
    @Test fun validationUnreadableFileAndBadUtf8NeverModifyCurrentData() = runBlocking {
        store.replace(original)
        val bad = File(directory, "bad.json").apply { writeText("{malformed") }
        assertTrue(runCatching { service.read(Uri.fromFile(bad)) }.isFailure); equal(original)
        bad.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        assertTrue(runCatching { service.read(Uri.fromFile(bad)) }.isFailure); equal(original)
        assertTrue(runCatching { service.read(Uri.parse("content://missing.backup.provider/file")) }.isFailure); equal(original)
        val invalid = original.copy(plans = original.plans.copy(activities = listOf(ActivityPlan("broken", LocalDate.now(), name = "Broken", rescheduledFromId = "missing"))))
        assertTrue(runCatching { service.restore(invalid) }.isFailure); equal(original)
        assertEquals(0, reconciled)
    }
    @Test fun lateSqlFailureRollsBackEveryTableAndRetainsPreferences() = runBlocking {
        store.replace(original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON meal_plans WHEN NEW.id = 'late-failure' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val replacement = BackupSnapshot(original.createdAt, PlanningData(meals = listOf(MealPlan("late-failure", LocalDate.now(), text = "Fail"))), reminders = ReminderPreferences(setOf(ReminderType.ACTIVITY)))
        assertTrue(runCatching { service.restore(replacement) }.isFailure)
        equal(original); assertEquals(original.reminders, settings.state.value)
        assertFalse(journal.exists()); assertEquals(0, reconciled)
    }
    @Test fun journalCreationFailureOccursBeforeDestructiveWrite() = runBlocking {
        store.replace(original)
        val blocked = File(directory, "not-a-directory").apply { writeText("occupied") }
        val unavailable = BackupService(store, settings, context.contentResolver, File(blocked, "journal"), { fail("Must not reconcile") })
        assertTrue(runCatching { unavailable.restore(BackupSnapshot(original.createdAt)) }.isFailure)
        equal(original)
    }
    @Test fun deferredConstraintFailureCompensatesPreferencesAfterTheyWereWritten() = runBlocking {
        store.replace(original)
        // Inject a deferred constraint that must be caught before COMMIT, after preferences are written.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_commit AFTER INSERT ON saved_meals BEGIN INSERT INTO activity_plans (id,dateEpochDay,timeNanoOfDay,name,type,notes,outcome,rescheduledFromId) VALUES ('injected',0,NULL,'Injected','WORKOUT',NULL,'PLANNED','missing-parent'); END")
        db.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
        val replacement = BackupSnapshot(original.createdAt, savedMeals = listOf(com.ventures814.brdcrmbs.domain.SavedMeal("new", "New")))
        assertTrue(runCatching { service.restore(replacement) }.isFailure)
        equal(original); assertEquals(original.reminders, ReminderSettingsStore(context, name).state.value)
        assertFalse(journal.exists()); assertEquals(0, reconciled)
    }
    private fun writeRecovery(target: BackupSnapshot, before: ReminderPreferences, after: ReminderPreferences) {
        val canonical = BackupCodec.encode(target.copy(createdAt = Instant.EPOCH, reminders = ReminderPreferences(), appVersion = 1))
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        journal.writeText(JSONObject().put("target", digest).put("before", JSONArray(before.enabled.map { it.name })).put("after", JSONArray(after.enabled.map { it.name })).toString())
    }
    @Test fun interruptedUncommittedRoomWriteRecoversPreviousPreferencesBeforeNormalUse() = runBlocking {
        store.replace(original)
        val target = BackupSnapshot(original.createdAt)
        writeRecovery(target, original.reminders, target.reminders)
        settings.replaceForRestore(target.reminders) // SQLite has already rolled the interrupted write back.
        service.recover(); equal(original); assertFalse(journal.exists())
    }
    @Test fun interruptedCommittedRoomWriteFinishesRestoredPreferencesIdempotently() = runBlocking {
        store.replace(original)
        val target = BackupSnapshot(original.createdAt)
        writeRecovery(target, original.reminders, target.reminders)
        store.replace(target)
        settings.replaceForRestore(original.reminders)
        service.recover(); service.recover(); equal(target); assertFalse(journal.exists())
    }
    @Test fun reminderReconciliationFailureReportsCommittedRestoreSeparately() = runBlocking {
        store.replace(original)
        val delayed = BackupService(store, settings, context.contentResolver, journal, { error("OS unavailable") })
        val empty = BackupSnapshot(original.createdAt)
        assertFalse(delayed.restore(empty).remindersReconciled)
        equal(empty); assertFalse(journal.exists())
    }
}
