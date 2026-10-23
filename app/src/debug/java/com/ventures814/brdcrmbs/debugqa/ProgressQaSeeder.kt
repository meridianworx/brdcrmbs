package com.ventures814.brdcrmbs.debugqa

import androidx.room.withTransaction
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import java.time.Clock
import java.time.LocalDate

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
    suspend fun reset() = database.withTransaction { removeOwned(null) }

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
