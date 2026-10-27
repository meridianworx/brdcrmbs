package com.ventures814.brdcrmbs

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.*
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.debugqa.ProgressQaSeeder
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.reminders.*
import com.ventures814.brdcrmbs.ui.fasting.FastPreset
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class DataManagementPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "data-management-${UUID.randomUUID()}"
    private val zone = ZoneId.of("America/Denver")
    private val clock = Clock.fixed(Instant.parse("2026-10-25T14:00:00.123Z"), zone)
    private val original = richBackupSnapshot().copy(appPreferences = AppPreferences(TimeFormat.TWENTY_FOUR, FastPreset.H48, 6),
        reminders = ReminderPreferences(ReminderType.entries.toSet()))
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private lateinit var prefs: AppPreferencesStore
    private lateinit var settings: ReminderSettingsStore
    private lateinit var store: RoomBackupStore
    private lateinit var backend: AndroidReminderBackend
    private lateinit var engine: ReminderCoordinator
    private lateinit var service: BackupService
    private val directory by lazy { File(context.cacheDir, name).apply { mkdirs() } }
    private val journal get() = File(directory, "journal.json")
    private var failCleanup = false
    private var cleanupCalls = 0

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db)
        prefs = AppPreferencesStore(context, name)
        settings = ReminderSettingsStore(context, "$name-reminders")
        store = RoomBackupStore(db, settings, prefs)
        backend = AndroidReminderBackend(context, "$name-runtime")
        engine = ReminderCoordinator(repo, settings, backend, { true }, { clock })
        service = BackupService(store, settings, context.contentResolver, journal, { engine.reconcile(true) },
            boundary = engine::withRestoreBoundary, clock = clock, restoreBoundary = engine::restoreAndReconcile,
            cleanupDataRuntime = { reset ->
                cleanupCalls++
                check(!failCleanup) { "Injected interrupted cleanup" }
                backend.clearDataRuntime()
                if (reset) settings.resetPermissionHistory()
            })
        store.replace(original); settings.markAsked()
    }
    @After fun cleanup() {
        backend.clearDataRuntime(); engine.close(); db.close()
        listOf(name, "$name-reminders", "$name-reminders_permission", "$name-runtime").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
    }
    private suspend fun current() = store.snapshot(original.createdAt, excludeQa = false)
    private suspend fun equal(expected: BackupSnapshot) = assertEquals(BackupCodec.encode(expected), BackupCodec.encode(current()))
    private fun register(): PlanReminder {
        val request = PlanReminder("MEAL:meal-PLANNED", ReminderType.MEAL, clock.instant().plusSeconds(86_400), "Meal", "Plan")
        backend.schedule(request); backend.markDelivered(request.copy(key = "MEAL:old"))
        return request
    }

    @Test fun clearDeletesEveryPlanningTableIncludingQaAndPreservesLibrariesAndConfiguration() = runBlocking {
        val qa = ProgressQaSeeder(db, Clock.fixed(Instant.parse("2030-01-07T18:00:00Z"), zone))
        qa.seed(qa.currentWeek())
        val alarm = register()
        assertTrue(service.manageData(DataAction.CLEAR_PLANNING).remindersCleaned)
        equal(original.copy(plans = PlanningData()))
        assertTrue(settings.asked)
        assertTrue(backend.pending().isEmpty()); assertTrue(backend.delivered().isEmpty())
        assertNull(PendingIntent.getBroadcast(context, 0, backend.alarmIntent(alarm.key, alarm.at), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        assertFalse(journal.exists())
        service.manageData(DataAction.CLEAR_PLANNING); equal(original.copy(plans = PlanningData()))
        // QA tooling still works after clearing its stored records.
        qa.seed(qa.currentWeek()); assertTrue(current().plans.fasting.isNotEmpty())
        service.manageData(DataAction.CLEAR_PLANNING)
        assertEquals(4, db.openHelper.writableDatabase.version)
    }

    @Test fun resetDefaultsAllSixTablesAndRuntimeWithoutChangingOsSettingsAndCanRepeat() = runBlocking {
        val qa = ProgressQaSeeder(db, Clock.fixed(Instant.parse("2030-01-07T18:00:00Z"), zone))
        qa.seed(qa.currentWeek())
        val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val systemClock = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
        register()
        assertTrue(service.manageData(DataAction.RESET_APP).remindersCleaned)
        equal(BackupSnapshot(original.createdAt))
        assertEquals(AppPreferences(), AppPreferencesStore(context, name).state.value)
        assertEquals(ReminderPreferences(), ReminderSettingsStore(context, "$name-reminders").state.value)
        assertFalse(settings.asked); assertTrue(backend.pending().isEmpty()); assertTrue(backend.delivered().isEmpty())
        assertEquals(permission, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(systemClock, Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24))
        service.manageData(DataAction.RESET_APP); equal(BackupSnapshot(original.createdAt))
        repo.addMeal(MealPlan(date = LocalDate.now(clock), text = "Immediately usable"))
        assertEquals(1, current().plans.meals.size)
    }

    @Test fun exportedBackupSurvivesClearAndResetAndRestoresEverySupportedHistoricalField() = runBlocking {
        val backup = service.create()
        val document = File(directory, "user-selected-backup.json")
        val uri = Uri.fromFile(document)
        service.write(uri, backup)
        val bytes = document.readBytes()
        service.manageData(DataAction.CLEAR_PLANNING); assertArrayEquals(bytes, document.readBytes())
        service.manageData(DataAction.RESET_APP); equal(BackupSnapshot(original.createdAt))
        assertArrayEquals(bytes, document.readBytes())
        assertTrue(service.restore(service.read(uri)).remindersReconciled)
        equal(original)
        assertEquals("Actual fish ☺", current().plans.meals.first { it.outcome == MealOutcome.CHANGED }.actual!!.text)
        assertEquals("Original planned CHANGED", current().plans.meals.first { it.outcome == MealOutcome.CHANGED }.text)
        assertEquals("z-original", current().plans.activities.first { it.id == "a-rescheduled" }.rescheduledFromId)
        assertArrayEquals(bytes, document.readBytes())
    }

    @Test fun deletionFailureRollsBackClearWithoutTouchingPreferencesLibrariesOrReminderRuntime() = runBlocking {
        val request = register()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clear BEFORE DELETE ON meal_plans BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { service.manageData(DataAction.CLEAR_PLANNING) }.isFailure)
        equal(original); assertEquals(request, backend.pending()[request.key]); assertTrue(settings.asked)
        assertEquals(0, cleanupCalls); assertFalse(journal.exists())
    }

    @Test fun resetLateFailureAfterPreferenceWritesCompensatesBothStoresAndAllRoomRows() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reset AFTER DELETE ON saved_activities BEGIN INSERT INTO activity_plans (id,dateEpochDay,timeNanoOfDay,name,type,notes,outcome,rescheduledFromId) VALUES ('injected',0,NULL,'Injected','WORKOUT',NULL,'PLANNED','missing-parent'); END")
        db.openHelper.writableDatabase.execSQL("PRAGMA defer_foreign_keys = ON")
        assertTrue(runCatching { service.manageData(DataAction.RESET_APP) }.isFailure)
        equal(original); assertTrue(settings.asked); assertFalse(journal.exists()); assertEquals(0, cleanupCalls)
    }

    @Test fun committedCleanupFailureRetainsJournalAndRecoversIdempotentlyForBothActions() = runBlocking {
        for (action in DataAction.entries) {
            store.replace(original); settings.markAsked(); register(); failCleanup = true
            assertFalse(service.manageData(action).remindersCleaned)
            assertTrue(journal.exists())
            assertEquals(PlanningData(), current().plans)
            failCleanup = false
            service.recover(); service.recover()
            equal(if (action == DataAction.RESET_APP) BackupSnapshot(original.createdAt) else original.copy(plans = PlanningData()))
            assertFalse(journal.exists()); assertTrue(backend.pending().isEmpty()); assertTrue(backend.delivered().isEmpty())
            assertEquals(action != DataAction.RESET_APP, settings.asked)
        }
    }

    @Test fun repositoryAndLibraryObserversReceiveRealEmptyStatesAfterCommit() = runBlocking {
        val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1))
        val plans = async { repo.observe(range, zone).first { it == PlanningData() } }
        val meals = async { repo.observeSavedMeals().first { it.isEmpty() } }
        val activities = async { repo.observeSavedActivities().first { it.isEmpty() } }
        service.manageData(DataAction.RESET_APP)
        withTimeout(30_000) { plans.await(); meals.await(); activities.await() }
        equal(BackupSnapshot(original.createdAt))
    }

    @Test fun interruptedUncommittedResetRecoversPreviousConfigurationAndKeepsSchedules() = runBlocking {
        val alarm = register()
        val target = BackupCodec.encode(BackupSnapshot(Instant.EPOCH))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(target.toByteArray())
            .joinToString("") { "%02x".format(it) }
        journal.writeText(org.json.JSONObject().put("target", digest)
            .put("before", org.json.JSONArray(original.reminders.enabled.map { it.name })).put("after", org.json.JSONArray())
            .put("beforeApp", BackupCodec.encodePreferences(original.appPreferences))
            .put("afterApp", BackupCodec.encodePreferences(AppPreferences())).put("dataAction", DataAction.RESET_APP.name).toString())
        // Process died during the cross-store transaction: SQLite rolled back; one config write persisted.
        prefs.replace(AppPreferences()); settings.replaceForRestore(ReminderPreferences())
        service.recover(); service.recover()
        equal(original); assertEquals(alarm, backend.pending()[alarm.key]); assertEquals(0, cleanupCalls)
        assertTrue(settings.asked); assertFalse(journal.exists())
    }

    @Suppress("DEPRECATION")
    @Test fun clearCancelsAlreadyPostedNativeNotificationWithoutChangingPermission() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow("android:post_notification", context.applicationInfo.uid, context.packageName)
        fun shell(command: String) { automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        } }
        shell("appops set ${context.packageName} POST_NOTIFICATION allow")
        automation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val posted = PlanReminder("MEAL:$name", ReminderType.MEAL, clock.instant(), "Review meal", "Plan")
        try {
            val permission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            assertTrue(backend.allowed()); backend.post(posted)
            withTimeout(30_000) { while (manager.activeNotifications.none { it.tag == posted.key }) delay(100) }
            service.manageData(DataAction.CLEAR_PLANNING)
            withTimeout(30_000) { while (manager.activeNotifications.any { it.tag == posted.key }) delay(100) }
            assertEquals(permission, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        } finally {
            manager.cancel(posted.key, 0); automation.dropShellPermissionIdentity()
            val previous = when (mode) { android.app.AppOpsManager.MODE_ALLOWED -> "allow"; android.app.AppOpsManager.MODE_IGNORED -> "ignore"; android.app.AppOpsManager.MODE_ERRORED -> "deny"; else -> "default" }
            shell("appops set ${context.packageName} POST_NOTIFICATION $previous")
        }
    }
}
