package com.ventures814.brdcrmbs

import android.app.Application
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.PlanningRepository

class BrdcrmbsApplication : Application() {
    val reminderSettings by lazy { com.ventures814.brdcrmbs.reminders.ReminderSettingsStore(this) }
    val reminderBackend by lazy { com.ventures814.brdcrmbs.reminders.AndroidReminderBackend(this) }
    val reminders by lazy { com.ventures814.brdcrmbs.reminders.ReminderCoordinator(planningRepository, reminderSettings, reminderBackend, reminderBackend::allowed) }
    override fun onCreate() {
        super.onCreate()
        reminders.start()
        com.ventures814.brdcrmbs.reminders.ReminderRecoveryReceiver.enqueue(this)
    }
    internal val planningDatabase: PlanningDatabase by lazy { PlanningDatabase.open(this) }
    val planningRepository: PlanningRepository by lazy {
        RoomPlanningRepository(planningDatabase)
    }
}
