package com.ventures814.brdcrmbs

import android.app.Application
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.PlanningRepository

class BrdcrmbsApplication : Application() {
    val appPreferences by lazy { com.ventures814.brdcrmbs.preferences.AppPreferencesStore(this) }
    val reminderSettings by lazy { com.ventures814.brdcrmbs.reminders.ReminderSettingsStore(this) }
    val reminderBackend by lazy { com.ventures814.brdcrmbs.reminders.AndroidReminderBackend(this) }
    val reminders by lazy { com.ventures814.brdcrmbs.reminders.ReminderCoordinator(planningRepository, reminderSettings, reminderBackend, reminderBackend::allowed, displayClock = {
        com.ventures814.brdcrmbs.preferences.ClockFormat(appPreferences.state.value.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(this)))
    }) }
    val backups by lazy {
        com.ventures814.brdcrmbs.backup.BackupService(
            com.ventures814.brdcrmbs.data.RoomBackupStore(planningDatabase, reminderSettings, appPreferences), reminderSettings,
            contentResolver, java.io.File(noBackupFilesDir, "restore-preferences-journal.json"),
            { reminders.reconcile(recovery = true) }, { action -> reminders.withRestoreBoundary(action) },
            restoreBoundary = reminders::restoreAndReconcile
        )
    }
    override fun onCreate() {
        super.onCreate()
        // Normally a no-op. Recover an interrupted cross-store restore before reminders can read it.
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { backups.recover() }
        reminders.start()
        com.ventures814.brdcrmbs.reminders.ReminderRecoveryReceiver.enqueue(this)
    }
    internal val planningDatabase: PlanningDatabase by lazy { PlanningDatabase.open(this) }
    val planningRepository: PlanningRepository by lazy {
        RoomPlanningRepository(planningDatabase)
    }
}
