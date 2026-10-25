package com.ventures814.brdcrmbs.domain

import java.time.Duration
import java.util.UUID

data class CopyResult(val added: Int, val skipped: Int, val fastingConflicts: Int = 0) {
    fun feedback(): String = if (added == 0 && skipped == 0 && fastingConflicts == 0) "Nothing to copy. No plans were added."
        else "Added $added planned items; skipped $skipped identical Planned items." +
            if (fastingConflicts > 0) " Skipped $fastingConflicts fasting conflict${if (fastingConflicts == 1) "" else "s"}." else ""
}

/** Shift original planning fields only; historical outcomes, actuals and linkage stay at the source. */
fun PlanningData.shiftedPlans(days: Long): PlanningData = PlanningData(
    fasting = fasting.map { event ->
        val original = event.start.atZone(event.zone)
        val local = original.toLocalDateTime().plusDays(days)
        val offsets = event.zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) {
            "Cannot copy a fast to $local in ${event.zone.id}: that time does not exist because the clock changes. Choose another destination."
        }
        val offset = original.offset.takeIf { it in offsets } ?: offsets.first()
        val start = local.toInstant(offset)
        event.copy(id = UUID.randomUUID().toString(), start = start,
            end = start.plus(Duration.between(event.start, event.end)), outcome = FastingOutcome.PLANNED)
    },
    meals = meals.map { it.copy(id = UUID.randomUUID().toString(), date = it.date.plusDays(days), outcome = MealOutcome.PLANNED, actual = null) },
    activities = activities.map { it.copy(id = UUID.randomUUID().toString(), date = it.date.plusDays(days), outcome = ActivityOutcome.PLANNED, rescheduledFromId = null) },
    noFast = noFast.map { it.copy(id = UUID.randomUUID().toString(), date = it.date.plusDays(days)) }
)

// Exact planned-field equality. IDs, outcomes, actual meals, and historical linkage are not keys.
internal fun FastingEvent.copyKey() = copy(id = "copy-key", outcome = FastingOutcome.PLANNED)
internal fun MealPlan.copyKey() = copy(id = "copy-key", outcome = MealOutcome.PLANNED, actual = null)
internal fun ActivityPlan.copyKey() = copy(id = "copy-key", outcome = ActivityOutcome.PLANNED, rescheduledFromId = null)
internal fun NoFastPlan.copyKey() = copy(id = "copy-key")

internal val PlanningData.recordCount: Int get() = fasting.size + meals.size + activities.size + noFast.size
