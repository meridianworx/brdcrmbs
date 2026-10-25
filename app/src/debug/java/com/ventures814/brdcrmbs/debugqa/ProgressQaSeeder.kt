package com.ventures814.brdcrmbs.debugqa

import androidx.room.withTransaction
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.Duration
import com.ventures814.brdcrmbs.domain.*

/** Exact reserved identity, separate from the canonical Progress week scenario. */
object ActiveFastQaOwnership {
    const val ID = "brdcrmbs-debug-active-fast-v1"
}

/** Debug-only transactional owner of QA records. Production CRUD/history guards stay unchanged. */
class ProgressQaSeeder(private val database: PlanningDatabase, private val clock: Clock? = null) {
    private val repository = RoomPlanningRepository(database)
    fun currentWeek() = QaWeek.current(clock ?: Clock.systemDefaultZone())

    suspend fun seed(confirmedWeek: QaWeek) = database.withTransaction {
        require(confirmedWeek == currentWeek()) { "The local test week changed. Review the current week and confirm again." }
        val plans = progressQaScenario(confirmedWeek)
        removeOwned(confirmedWeek.monday)
        plans.fasting.forEach { repository.addFasting(it) }
        plans.noFast.forEach { repository.addFastingPlan(it) }
        plans.meals.forEach { repository.addMeal(it) }
        plans.activities.forEach { repository.addActivity(it) }
    }

    /** All weeks owned by this seeder, never a date-range delete or whole-database reset. */
    suspend fun reset() = database.withTransaction { removeOwned(null); removeActive() }

    suspend fun seedActiveFast() = database.withTransaction {
        val current = clock ?: Clock.systemDefaultZone()
        val now = java.time.Instant.ofEpochMilli(current.millis())
        removeActive()
        repository.addFasting(FastingEvent(id = ActiveFastQaOwnership.ID,
            start = now.minus(Duration.ofHours(10)), end = now.plus(Duration.ofHours(6)),
            zone = current.zone, preset = "16:8", notes = "Active Fast QA"))
    }

    private fun removeActive() {
        database.openHelper.writableDatabase.execSQL("DELETE FROM fasting_events WHERE id = ?", arrayOf(ActiveFastQaOwnership.ID))
        database.openHelper.writableDatabase.execSQL("DELETE FROM no_fast_plans WHERE id = ?", arrayOf(ActiveFastQaOwnership.ID))
    }

    private fun removeOwned(week: LocalDate?) {
        val sqlite = database.openHelper.writableDatabase
        // Exact grammar validation follows the candidate prefix query. IDs also survive kind conversions.
        for (table in listOf("activity_plans", "meal_plans", "fasting_events", "no_fast_plans")) {
            val ids = sqlite.query("SELECT id FROM $table WHERE id LIKE ?", arrayOf(QaOwnership.PREFIX + "%")).use { rows ->
                buildList { while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    val ownedWeek = QaOwnership.weekOf(id)
                    if (ownedWeek != null && (week == null || ownedWeek == week)) add(id)
                } }
            }
            // SQLite foreign keys deliberately abort/roll back if genuine history references a QA activity.
            ids.forEach { sqlite.execSQL("DELETE FROM $table WHERE id = ?", arrayOf(it)) }
        }
    }
}
