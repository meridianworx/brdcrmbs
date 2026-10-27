package com.ventures814.brdcrmbs.data

import androidx.room.withTransaction
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.data.local.*
import com.ventures814.brdcrmbs.domain.PlanningData
import com.ventures814.brdcrmbs.reminders.ReminderSettingsStore
import java.time.Instant

/** Dedicated whole-history boundary; ordinary planning writes retain their locked validation. */
class RoomBackupStore(private val database: PlanningDatabase, private val settings: ReminderSettingsStore,
    val appPreferences: com.ventures814.brdcrmbs.preferences.AppPreferencesStore? = null) {
    private val dao = database.planningDao()
    /** Configuration/library-only target; clearing must not depend on exporting old history. */
    suspend fun withoutPlanning(at: Instant): BackupSnapshot = database.withTransaction {
        BackupSnapshot(at, savedMeals = dao.savedMeals().map { it.model() },
            savedActivities = dao.savedActivities().map { it.model() }, reminders = settings.state.value,
            appPreferences = appPreferences?.state?.value ?: com.ventures814.brdcrmbs.preferences.AppPreferences())
    }

    suspend fun clearPlanning() = database.withTransaction {
        // One DELETE statement removes the entire self-referencing activity set, including children.
        dao.restoreClearActivities(); dao.restoreClearMeals(); dao.restoreClearFasting(); dao.restoreClearNoFast()
    }
    suspend fun snapshot(at: Instant, excludeQa: Boolean = true): BackupSnapshot = database.withTransaction {
        fun keep(id: String) = !excludeQa || !isBackupQaOwned(id)
        BackupSnapshot(at, PlanningData(
            dao.allFasting().map { it.model() }.filter { keep(it.id) },
            dao.backupMeals().filter { keep(it.id) }.map {
                require(it.actualText != null || (it.actualTimeNanoOfDay == null && it.actualLabel == null && it.actualNotes == null)) {
                    "Incomplete stored actual-meal details cannot be exported safely."
                }
                it.model()
            },
            dao.backupActivities().map { it.model() }.filter { keep(it.id) },
            dao.backupNoFast().map { it.model() }.filter { keep(it.id) }
        ), dao.savedMeals().map { it.model() }, dao.savedActivities().map { it.model() }, settings.state.value, appPreferences = appPreferences?.state?.value ?: com.ventures814.brdcrmbs.preferences.AppPreferences()).validate()
        // A genuine activity referencing an excluded QA parent rejects export, never loses its history.
    }

    suspend fun replace(snapshot: BackupSnapshot) = database.withTransaction {
        snapshot.validate()
        dao.restoreClearActivities(); dao.restoreClearMeals(); dao.restoreClearFasting(); dao.restoreClearNoFast()
        dao.restoreClearSavedMeals(); dao.restoreClearSavedActivities()
        snapshot.plans.fasting.forEach { dao.insert(it.entity()) }
        snapshot.plans.noFast.forEach { dao.insert(it.entity()) }
        snapshot.plans.meals.forEach { dao.insert(it.entity()) }
        snapshot.activitiesInInsertionOrder().forEach { dao.insert(it.entity()) }
        snapshot.savedMeals.forEach { dao.insert(it.entity()) }
        snapshot.savedActivities.forEach { dao.insert(it.entity()) }
        settings.replaceForRestore(snapshot.reminders)
        appPreferences?.replace(snapshot.appPreferences)
        // Detect deferred constraints while Room can still roll back normally. Android SQLite can
        // leave a native transaction open when a deferred FK fails during endTransaction/COMMIT.
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "Restored activity history contains an invalid relationship." }
        }
    }
}
