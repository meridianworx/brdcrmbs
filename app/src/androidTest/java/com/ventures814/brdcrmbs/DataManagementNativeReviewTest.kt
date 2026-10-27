package com.ventures814.brdcrmbs

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.RoomBackupStore
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.reminders.*
import com.ventures814.brdcrmbs.ui.fasting.FastPreset
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

/** Real app/task/SAF review. No production fixture hooks or dependency on UI Automator. */
@SdkSuppress(minSdkVersion = 29)
class DataManagementNativeReviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val app get() = context.applicationContext as BrdcrmbsApplication
    private val directory get() = File(context.getExternalFilesDir(null), "data-management-review").apply { mkdirs() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::walk)
        }
        automation.rootInActiveWindow?.let(::walk)
        return result
    }
    private fun find(label: String) = nodes().filter { it.text?.toString() == label || it.contentDescription?.toString() == label }
    private fun waitFor(label: String) = runBlocking {
        android.util.Log.i("DataManagementReview", "Waiting for: $label")
        withTimeout(90_000) { while (find(label).isEmpty()) delay(200) }
    }
    private fun click(label: String) = runBlocking {
        android.util.Log.i("DataManagementReview", "Clicking: $label")
        var forward = true
        withTimeout(90_000) {
            while (true) {
                val found = find(label).lastOrNull { it.isVisibleToUser && it.isEnabled }
                if (found != null) {
                    if (found.packageName?.toString() != context.packageName) {
                        var target: AccessibilityNodeInfo? = found
                        while (target != null && !target.isClickable) target = target.parent
                        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) break
                    }
                    val bounds = Rect().also(found::getBoundsInScreen)
                    if (!bounds.isEmpty) {
                        val now = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                                bounds.exactCenterX(), bounds.exactCenterY(), 0)
                            event.source = InputDevice.SOURCE_TOUCHSCREEN
                            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                            if (action == MotionEvent.ACTION_DOWN) delay(80)
                        }
                        break
                    }
                }
                val scroll = nodes().firstOrNull { it.isScrollable }
                if (scroll != null && !scroll.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) forward = !forward
                delay(300)
                runCatching { automation.waitForIdle(300, 3_000) }
            }
        }
        delay(300)
        runCatching { automation.waitForIdle(300, 5_000) }
        if (label == "Settings") waitFor("Saved Meals")
    }
    private fun back() = runBlocking {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        // Every Back in this review returns from a focused child to Settings main.
        withTimeout(90_000) { while (find("Today").isEmpty() || find("Week").isEmpty()) delay(100) }
        runCatching { automation.waitForIdle(300, 5_000) }
    }
    private fun capture(name: String) {
        runCatching { automation.waitForIdle(300, 5_000) }
        // Shell-owned review output survives Gradle uninstalling the target APK after a run.
        automation.executeShellCommand("screencap -p /sdcard/brdcrmbs-data-review-$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        val text = nodes().joinToString("\n") { "${it.text ?: it.contentDescription ?: ""} ${it.viewIdResourceName ?: ""}" }
        File(directory, "$name.txt").writeText(text)
        android.util.Log.i("DataManagementReview", "Capture $name:\n$text")
    }

    @Test fun nativeClearResetAndSafRestorePreserveBackupAndRefreshTheRealApp() = runBlocking {
        val store = RoomBackupStore(app.planningDatabase, app.reminderSettings, app.appPreferences)
        val before = store.snapshot(Instant.EPOCH, excludeQa = false)
        val asked = app.reminderSettings.asked
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val base = richBackupSnapshot()
        val shift = date.toEpochDay() - LocalDate.of(2026, 11, 1).toEpochDay()
        val fixture = base.copy(plans = base.plans.copy(
            fasting = base.plans.fasting.map { fast ->
                val start = fast.start.atZone(fast.zone).plusDays(shift).toInstant()
                fast.copy(start = start, end = start.plus(Duration.between(fast.start, fast.end)))
            }, noFast = base.plans.noFast.map { it.copy(date = it.date.plusDays(shift)) },
            meals = base.plans.meals.map { it.copy(date = it.date.plusDays(shift)) },
            activities = base.plans.activities.map { it.copy(date = it.date.plusDays(shift)) }),
            appPreferences = AppPreferences(TimeFormat.TWENTY_FOUR, FastPreset.H48, 6),
            reminders = ReminderPreferences(ReminderType.entries.toSet()))
        val filename = "brdcrmbs-data-review-${UUID.randomUUID()}.json"
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename); put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }))
        fun bytes() = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        suspend fun current() = store.snapshot(fixture.createdAt, excludeQa = false)
        val pid = android.os.Process.myPid()
        try {
            app.backups.restore(fixture)
            app.backups.write(uri, app.backups.create())
            val exported = bytes()
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitFor("Today"); click("Settings"); click("Data Management"); waitFor("Clear Planning & History")
            capture("01-data-management")
            click("Clear Planning & History"); waitFor("Clear Planning & History?"); capture("02-clear-confirmation")
            click("Cancel"); assertEquals(BackupCodec.encode(fixture), BackupCodec.encode(current()))
            click("Clear Planning & History"); click("Clear")
            withTimeout(90_000) { while (current().plans != PlanningData()) delay(200) }
            waitFor("Planning and history cleared."); capture("03-cleared")
            assertEquals(fixture.copy(plans = PlanningData()), current()); assertArrayEquals(exported, bytes())
            back(); click("Saved Meals"); waitFor("Reusable soup"); capture("04-preserved-meals")
            back(); click("Saved Activities"); waitFor("Reusable workout"); capture("05-preserved-activities")
            back(); click("App Preferences"); waitFor("24-hour"); waitFor("48h"); waitFor("6"); capture("06-preserved-preferences")
            back(); click("Data Management"); click("Reset App Data"); waitFor("Reset App Data?")
            capture("07-reset-review"); click("Cancel"); assertEquals(fixture.copy(plans = PlanningData()), current())
            click("Reset App Data"); click("Continue"); waitFor("Permanently reset app data?")
            capture("08-reset-final"); click("Cancel"); assertEquals(fixture.copy(plans = PlanningData()), current())
            click("Reset App Data"); click("Continue"); waitFor("Permanently reset app data?"); click("Reset App Data")
            withTimeout(90_000) { while (current() != BackupSnapshot(fixture.createdAt)) delay(200) }
            waitFor("Today"); waitFor("Nothing planned"); capture("09-reset-today")
            assertEquals(pid, android.os.Process.myPid()); assertArrayEquals(exported, bytes())
            click("Week"); waitFor("My Week"); waitFor("Meals (0)"); capture("10-reset-week")
            click("Progress"); waitFor("PROGRESS"); waitFor("Nothing planned"); capture("11-reset-progress")
            click("Settings"); click("App Preferences"); waitFor("System default"); waitFor("No default"); waitFor("3"); capture("12-default-preferences")
            back(); click("Saved Meals"); waitFor("No saved meals yet."); capture("13-empty-meals")
            back(); click("Saved Activities"); waitFor("No saved activities yet."); capture("14-empty-activities")
            back(); click("Backup & Restore"); click("Restore Backup")
            // Android's actual document picker, using the exported Downloads document.
            runCatching {
                withTimeout(8_000) { while (find(filename).isEmpty()) delay(200) }
            }.onFailure {
                click("Show roots"); click("Downloads")
            }
            click(filename); waitFor("Replace current data?"); capture("15-restore-confirmation")
            click("Replace & Restore")
            withTimeout(90_000) { while (BackupCodec.encode(current()) != BackupCodec.encode(fixture)) delay(200) }
            waitFor("Backup restored.")
            capture("16-restored"); assertArrayEquals(exported, bytes())
            back(); click("Today"); waitFor("72h"); capture("17-restored-today")
        } catch (failure: Throwable) {
            runCatching { capture("failure") }
            throw failure
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.finish() }
            }
            app.backups.restore(before)
            if (asked) app.reminderSettings.markAsked() else app.reminderSettings.resetPermissionHistory()
            context.contentResolver.delete(uri, null, null)
        }
    }
}
