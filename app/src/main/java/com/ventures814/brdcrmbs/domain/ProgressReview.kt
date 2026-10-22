package com.ventures814.brdcrmbs.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

enum class ReviewStatus(val symbol: String, val label: String) {
    SUCCESS("✓", "Success"), CHANGED("◐", "Changed"), MISSED("✕", "Missed"),
    PLANNED("○", "Planned"), NONE("—", "Nothing planned")
}

data class AdherenceSummary(val done: Int, val resolved: Int) {
    val percent: Int? get() = if (resolved == 0) null else (100.0 * done / resolved).roundToInt()
}

data class ReviewDay(
    val date: LocalDate, val plans: PlanningData,
    val fasting: ReviewStatus, val meals: ReviewStatus, val activity: ReviewStatus,
    val mealsDone: Int, val mealsTotal: Int,
    val fastContinuesBefore: Boolean, val fastContinuesAfter: Boolean
)

data class MonthReview(
    val month: YearMonth, val days: List<ReviewDay>,
    val fasting: AdherenceSummary, val meals: AdherenceSummary, val activity: AdherenceSummary
) {
    // Monday-first, matching the established Week calendar.
    val leadingCells: Int get() = month.atDay(1).dayOfWeek.value - 1
}

/** Selected-day resolved-item score; existing monthly aggregation remains unchanged. */
val ReviewDay.adherence: AdherenceSummary get() = AdherenceSummary(
    plans.fasting.count { it.outcome == FastingOutcome.DONE } + plans.meals.count { it.outcome == MealOutcome.DONE } + plans.activities.count { it.outcome == ActivityOutcome.DONE },
    plans.fasting.count { it.outcome != FastingOutcome.PLANNED } + plans.meals.count { it.outcome != MealOutcome.PLANNED } + plans.activities.count { it.outcome != ActivityOutcome.PLANNED }
)

private fun aggregate(outcomes: List<String>): ReviewStatus = when {
    outcomes.isEmpty() -> ReviewStatus.NONE
    "MISSED" in outcomes -> ReviewStatus.MISSED
    "CHANGED" in outcomes -> ReviewStatus.CHANGED
    outcomes.all { it == "DONE" } -> ReviewStatus.SUCCESS
    else -> ReviewStatus.PLANNED
}

/** Read-only projection. Each overlapping fast counts once per month, never once per calendar cell.
 * No fast is intentional/neutral and excluded from the resolved fasting denominator.
 * Changed meals are resolved but not successful; all Planned items are excluded from percentages.
 */
fun PlanningData.reviewMonth(month: YearMonth, zone: ZoneId): MonthReview {
    val range = DateRange(month.atDay(1), month.plusMonths(1).atDay(1))
    val applicable = PlanningData(
        fasting.filter { it.start < range.endInstant(zone) && it.end > range.startInstant(zone) },
        meals.filter { YearMonth.from(it.date) == month },
        activities.filter { YearMonth.from(it.date) == month },
        noFast.filter { YearMonth.from(it.date) == month }
    )
    val days = (1..month.lengthOfMonth()).map { number ->
        val date = month.atDay(number)
        val plans = applicable.forDay(date, zone)
        val day = DateRange.day(date)
        ReviewDay(date, plans, aggregate(plans.fasting.map { it.outcome.name }),
            aggregate(plans.meals.map { it.outcome.name }), aggregate(plans.activities.map { it.outcome.name }),
            plans.meals.count { it.outcome == MealOutcome.DONE }, plans.meals.size,
            plans.fasting.any { it.start < day.startInstant(zone) },
            plans.fasting.any { it.end > day.endInstant(zone) })
    }
    return MonthReview(month, days,
        AdherenceSummary(applicable.fasting.count { it.outcome == FastingOutcome.DONE }, applicable.fasting.count { it.outcome != FastingOutcome.PLANNED }),
        AdherenceSummary(applicable.meals.count { it.outcome == MealOutcome.DONE }, applicable.meals.count { it.outcome != MealOutcome.PLANNED }),
        AdherenceSummary(applicable.activities.count { it.outcome == ActivityOutcome.DONE }, applicable.activities.count { it.outcome != ActivityOutcome.PLANNED }))
}
