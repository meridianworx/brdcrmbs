package com.ventures814.brdcrmbs.backup

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.ReminderPreferences
import java.time.Instant

/** Portable history snapshot. Restore is deliberately distinct from new-plan creation validation. */
data class BackupSnapshot(
    val createdAt: Instant,
    val plans: PlanningData = PlanningData(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val savedActivities: List<SavedActivity> = emptyList(),
    val reminders: ReminderPreferences = ReminderPreferences(),
    val appVersion: Int = 1
) {
    val count: Int get() = plans.fasting.size + plans.noFast.size + plans.meals.size + plans.activities.size + savedMeals.size + savedActivities.size

    fun validate(): BackupSnapshot {
        require(appVersion > 0 && count <= 100_000) { "Unsupported backup size or app metadata." }
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "Backup contains duplicate or empty record IDs." } }
        unique(plans.fasting.map { it.id } + plans.noFast.map { it.id })
        unique(plans.meals.map { it.id }); unique(plans.activities.map { it.id })
        unique(savedMeals.map { it.id }); unique(savedActivities.map { it.id })
        activitiesInInsertionOrder()
        // Historical overlapping fasts and retained actual snapshots are valid history, never repaired.
        return this
    }

    fun activitiesInInsertionOrder(): List<ActivityPlan> {
        val remaining = plans.activities.associateBy { it.id }.toMutableMap()
        val known = remaining.keys.toSet()
        require(remaining.values.all { it.rescheduledFromId == null || it.rescheduledFromId in known }) { "Backup contains an activity link without its original record." }
        val children = remaining.values.groupBy { it.rescheduledFromId }
        val ready = java.util.ArrayDeque<ActivityPlan>()
        remaining.values.filter { it.rescheduledFromId == null }.sortedBy { it.id }.forEach(ready::add)
        val ordered = mutableListOf<ActivityPlan>()
        while (ready.isNotEmpty()) {
            val next = ready.removeFirst()
            ordered.add(next); remaining.remove(next.id)
            children[next.id].orEmpty().sortedBy { it.id }.forEach(ready::add)
        }
        require(remaining.isEmpty()) { "Backup contains cyclic activity links." }
        return ordered
    }
}
