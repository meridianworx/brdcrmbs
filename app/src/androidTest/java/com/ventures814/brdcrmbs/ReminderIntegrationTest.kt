package com.ventures814.brdcrmbs

import android.app.PendingIntent
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class ReminderIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2026, 12, 28)
    private val clock = Clock.fixed(date.atTime(8, 0).atZone(zone).toInstant(), zone)
    private val name = "reminder-test-${UUID.randomUUID()}"
    private lateinit var db: PlanningDatabase
    private lateinit var repo: RoomPlanningRepository
    private lateinit var settings: ReminderSettingsStore
    private lateinit var engine: ReminderCoordinator
    private val backend = Fake()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PlanningDatabase::class.java).build()
        repo = RoomPlanningRepository(db, clock)
        settings = ReminderSettingsStore(context, name)
        engine = ReminderCoordinator(repo, settings, backend, { true }, { clock })
    }
    @After fun cleanup() { engine.close(); db.close(); context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit(); context.getSharedPreferences("${name}_permission", Context.MODE_PRIVATE).edit().clear().commit() }
    private suspend fun await(count: Int) { withTimeout(30_000) { while (engine.pending.value.size != count) delay(30) } }
    private suspend fun enableAll() { ReminderType.entries.forEach { settings.set(it, true) } }

    @Test fun allFourPreferencesDefaultOffAndPersistIndependently() = runBlocking {
        assertTrue(settings.state.value.enabled.isEmpty()); assertFalse(settings.asked)
        for (type in ReminderType.entries) {
            settings.set(type, true)
            assertTrue(type in ReminderSettingsStore(context, name).state.value.enabled)
            settings.set(type, false)
            assertFalse(type in ReminderSettingsStore(context, name).state.value.enabled)
        }
        settings.markAsked(); assertTrue(ReminderSettingsStore(context, name).asked)
    }
    @Test fun repositoryObservationReplacesEditsCancelsDeletesAndResolvedOutcomes() = runBlocking {
        enableAll(); engine.start()
        val fast = FastingEvent("fast", clock.instant().plusSeconds(3600), clock.instant().plusSeconds(176400), zone)
        val meal = MealPlan("meal", date, LocalTime.NOON, "Planned")
        val activity = ActivityPlan("activity", date, LocalTime.of(13, 0), "Walk")
        repo.addFasting(fast); repo.addMeal(meal); repo.addActivity(activity)
        repo.addFastingPlan(NoFastPlan("none", date, zone))
        repo.addMeal(meal.copy(id = "untimed", time = null)); repo.addMeal(meal.copy(id = "past", time = LocalTime.of(7, 0)))
        await(4)
        repo.savePlannedMeal(meal.copy(time = LocalTime.of(14, 0)), false)
        repo.savePlannedActivity(activity.copy(time = LocalTime.of(15, 0)), false)
        repo.updateFastingPlan(fast.copy(start = fast.start.plusSeconds(60), end = fast.end.plusSeconds(60)))
        withTimeout(30_000) { while (engine.pending.value.first { it.key == "MEAL:meal" }.at != date.atTime(14, 0).atZone(zone).toInstant() ||
            engine.pending.value.first { it.key == "FAST_END:fast" }.at != fast.end.plusSeconds(60)) delay(30) }
        for (outcome in listOf(MealOutcome.DONE, MealOutcome.MISSED, MealOutcome.CHANGED)) {
            repo.recordTodayMeal(meal.id, outcome, AdherenceDay(date, zone), if (outcome == MealOutcome.CHANGED) ActualMeal("Actual") else null)
            engine.reconcile(); assertFalse(engine.pending.value.any { it.type == ReminderType.MEAL })
        }
        val history = repo.getMeal(meal.id)!!
        assertEquals("Planned", history.text); assertEquals("Actual", history.actual!!.text)
        repo.recordTodayFasting(fast.id, FastingOutcome.DONE, AdherenceDay(date, zone))
        repo.recordTodayActivity(activity.id, ActivityOutcome.MISSED, AdherenceDay(date, zone))
        await(0)
        val another = meal.copy(id = "delete-meal"); val otherActivity = activity.copy(id = "delete-activity"); val otherFast = fast.copy(id = "delete-fast")
        repo.addMeal(another); repo.addActivity(otherActivity); repo.addFasting(otherFast); await(4)
        repo.deletePlannedMeal(another.id); repo.deletePlannedActivity(otherActivity.id); repo.deleteFastingPlan(otherFast.id); await(0)
        assertEquals(4, db.openHelper.writableDatabase.version)
    }
    @Test fun copySelectedDaysRepeatWeekAndPreferenceChangesReconcileWithoutCopyChanges() = runBlocking {
        enableAll(); engine.start()
        val source = date.minusWeeks(1)
        repo.addMeal(MealPlan("source", source, LocalTime.NOON, "Lunch"))
        repo.addActivity(ActivityPlan("source-activity", source, LocalTime.of(13, 0), "Walk"))
        repo.addFasting(FastingEvent("source-fast", source.atTime(19, 0).atZone(zone).toInstant(), source.plusDays(1).atTime(11, 0).atZone(zone).toInstant(), zone))
        repo.copyDay(source, setOf(date), zone); await(4)
        repo.copyDay(source, setOf(date.plusDays(1), date.plusDays(2)), zone); await(12)
        repo.repeatLastWeek(date.plusWeeks(1), zone); await(24)
        val original = repo.load(DateRange(date, date.plusWeeks(2)), zone)
        engine.reconcile(); engine.reconcile(recovery = true); await(24)
        settings.set(ReminderType.MEAL, false); await(18)
        settings.set(ReminderType.ACTIVITY, false); await(12)
        settings.set(ReminderType.FAST_START, false); await(6)
        settings.set(ReminderType.FAST_END, false); await(0)
        assertEquals(original, repo.load(DateRange(date, date.plusWeeks(2)), zone))
    }
    @Test fun androidAlarmIdentityRuntimeJournalAndNotificationTapSurviveBackendRecreation() {
        val runtime = "$name-runtime"
        val first = AndroidReminderBackend(context, runtime)
        val item = PlanReminder("MEAL:identity|with spaces", ReminderType.MEAL, Instant.now().plusSeconds(86400), "Planned meal", "Soup")
        try {
            first.schedule(item); first.schedule(item)
            val reopened = AndroidReminderBackend(context, runtime)
            assertEquals(mapOf(item.key to item), reopened.pending())
            val originalIntent = first.alarmIntent(item.key, item.at)
            val changedIntent = first.alarmIntent(item.key, item.at.plusSeconds(60))
            assertTrue(originalIntent.filterEquals(changedIntent))
            assertFalse(originalIntent.filterEquals(first.alarmIntent("MEAL:another", item.at)))
            val alarm = PendingIntent.getBroadcast(context, 0, originalIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            assertNotNull(alarm)
            assertEquals(AndroidReminderBackend.ACTION_OPEN_TODAY, first.tapIntent().action)
            assertEquals(MainActivity::class.java.name, first.tapIntent().component!!.className)
            val notification = first.notification(item)
            assertNotNull(notification.contentIntent)
            reopened.markDelivered(item); assertEquals(item.at, first.delivered()[item.key])
            reopened.cancel(item.key); assertTrue(first.pending().isEmpty())
            assertNull(PendingIntent.getBroadcast(context, 0, originalIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally { first.cancel(item.key); context.getSharedPreferences(runtime, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @Test fun nativeNotificationUsesPlanContentAndItsTapOpensToday() {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val runtime = "$name-notification"
        val platform = AndroidReminderBackend(context, runtime)
        val item = PlanReminder("MEAL:$name", ReminderType.MEAL, clock.instant(), "Planned meal", "User-created soup")
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
        val previousMode = appOps.unsafeCheckOpNoThrow("android:post_notification", context.applicationInfo.uid, context.packageName)
        fun shell(command: String) { automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        } }
        shell("appops set ${context.packageName} POST_NOTIFICATION allow")
        automation.adoptShellPermissionIdentity(android.Manifest.permission.POST_NOTIFICATIONS)
        try {
            assertTrue(platform.allowed())
            platform.post(item)
            runBlocking { withTimeout(30_000) { while (manager.activeNotifications.none { it.tag == item.key }) delay(100) } }
            val posted = manager.activeNotifications.single { it.tag == item.key }.notification
            assertEquals(item.body, posted.extras.getString(android.app.Notification.EXTRA_TEXT))
            // Invoke the real posted token; wait for asynchronous Activity startup, not System UI animations.
            posted.contentIntent.send()
            runBlocking {
                withTimeout(30_000) {
                    var opened = false
                    while (!opened) {
                        InstrumentationRegistry.getInstrumentation().runOnMainSync {
                            opened = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { it is MainActivity && it.intent.action == AndroidReminderBackend.ACTION_OPEN_TODAY }
                        }
                        if (!opened) delay(100)
                    }
                }
            }
        } finally {
            manager.cancel(item.key, 0)
            automation.dropShellPermissionIdentity()
            val restore = when (previousMode) { android.app.AppOpsManager.MODE_ALLOWED -> "allow"; android.app.AppOpsManager.MODE_IGNORED -> "ignore"; android.app.AppOpsManager.MODE_ERRORED -> "deny"; else -> "default" }
            shell("appops set ${context.packageName} POST_NOTIFICATION $restore")
            context.getSharedPreferences(runtime, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private class Fake : ReminderBackend {
        private val items = java.util.concurrent.ConcurrentHashMap<String, PlanReminder>()
        private val sent = java.util.concurrent.ConcurrentHashMap<String, Instant>()
        override fun pending() = items.toMap()
        override fun delivered() = sent.toMap()
        override fun schedule(reminder: PlanReminder) { items[reminder.key] = reminder }
        override fun cancel(key: String) { items.remove(key) }
        override fun post(reminder: PlanReminder) = Unit
        override fun markDelivered(reminder: PlanReminder) { sent[reminder.key] = reminder.at }
    }
}
