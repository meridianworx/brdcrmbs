package com.ventures814.brdcrmbs.debugqa

import com.ventures814.brdcrmbs.domain.*
import java.time.*

data class QaWeek(val monday: LocalDate, val zone: ZoneId) {
    val endInclusive: LocalDate get() = monday.plusDays(6)
    companion object {
        fun current(clock: Clock = Clock.systemDefaultZone()) = QaWeek(DateRange.weekContaining(LocalDate.now(clock)).start, clock.zone)
    }
}

/** Reserved, finite ID grammar. Prefix lookalikes and genuine UUID records are never owned. */
object QaOwnership {
    const val PREFIX = "brdcrmbs-debug-progress-v1|"
    val slots = setOf("fast-mon", "fast-wed", "fast-thu", "fast-fri", "fast-sat", "no-fast-tue",
        "meal-mon-1", "meal-mon-2", "meal-mon-3", "meal-tue-1", "meal-tue-2",
        "meal-wed-1", "meal-wed-2", "meal-wed-3", "meal-thu-1",
        "activity-mon", "activity-tue", "activity-wed", "activity-thu", "activity-fri", "activity-sat")
    fun id(week: QaWeek, slot: String): String { require(slot in slots); return "$PREFIX${week.monday}|$slot" }
    fun weekOf(id: String): LocalDate? {
        val parts = id.split('|')
        if (parts.size != 3 || parts[0] + "|" != PREFIX || parts[2] !in slots) return null
        return runCatching { LocalDate.parse(parts[1]) }.getOrNull()?.takeIf {
            it.dayOfWeek == DayOfWeek.MONDAY && it.toString() == parts[1]
        }
    }
}

fun progressQaScenario(week: QaWeek): PlanningData {
    require(week.monday.dayOfWeek == DayOfWeek.MONDAY)
    fun date(offset: Long) = week.monday.plusDays(offset)
    fun id(slot: String) = QaOwnership.id(week, slot)
    fun fast(slot: String, offset: Long, hour: Int, hours: Long, preset: String, outcome: FastingOutcome): FastingEvent {
        val start = date(offset).atTime(hour, 0).atZone(week.zone).toInstant()
        return FastingEvent(id(slot), start, start.plus(Duration.ofHours(hours)), week.zone, preset, "Progress QA · $slot", outcome)
    }
    fun meal(slot: String, offset: Long, hour: Int, minute: Int, text: String, label: String, outcome: MealOutcome, actual: ActualMeal? = null) =
        MealPlan(id(slot), date(offset), LocalTime.of(hour, minute), text, label, "Progress QA · $slot", outcome, actual)
    fun activity(slot: String, offset: Long, hour: Int, text: String, type: ActivityType, outcome: ActivityOutcome) =
        ActivityPlan(id(slot), date(offset), LocalTime.of(hour, 0), text, type, "Progress QA · $slot", outcome)
    return PlanningData(
        fasting = listOf(
            fast("fast-mon", 0, 19, 16, "16:8", FastingOutcome.DONE),
            fast("fast-wed", 2, 18, 18, "18:6", FastingOutcome.DONE),
            fast("fast-thu", 3, 19, 16, "16:8", FastingOutcome.PLANNED),
            fast("fast-fri", 4, 19, 24, "24h", FastingOutcome.MISSED),
            fast("fast-sat", 5, 19, 48, "48h", FastingOutcome.PLANNED)),
        noFast = listOf(NoFastPlan(id("no-fast-tue"), date(1), week.zone, "Progress QA · intentional No fast")),
        meals = listOf(
            meal("meal-mon-1", 0, 8, 0, "Eggs + avocado", "Breakfast", MealOutcome.DONE),
            meal("meal-mon-2", 0, 12, 30, "Chicken + vegetables", "Lunch", MealOutcome.DONE),
            meal("meal-mon-3", 0, 18, 0, "Salmon + salad", "Dinner", MealOutcome.DONE),
            meal("meal-tue-1", 1, 8, 0, "Oatmeal", "Breakfast", MealOutcome.DONE),
            meal("meal-tue-2", 1, 18, 0, "Pork + cabbage", "Dinner", MealOutcome.CHANGED,
                ActualMeal("Chicken + rice", LocalTime.of(18, 30), "Dinner", "Progress test data — changed meal")),
            meal("meal-wed-1", 2, 8, 0, "Eggs", "Breakfast", MealOutcome.DONE),
            meal("meal-wed-2", 2, 12, 0, "Chicken salad", "Lunch", MealOutcome.DONE),
            meal("meal-wed-3", 2, 18, 0, "Pork + vegetables", "Dinner", MealOutcome.MISSED),
            meal("meal-thu-1", 3, 9, 0, "Oat meal shake", "Breakfast", MealOutcome.PLANNED)),
        activities = listOf(
            activity("activity-mon", 0, 17, "Bakal Gym — Upper Body", ActivityType.WORKOUT, ActivityOutcome.DONE),
            activity("activity-tue", 1, 19, "30-minute walk", ActivityType.ACTIVITY, ActivityOutcome.DONE),
            activity("activity-wed", 2, 17, "Bakal Gym — Lower Body", ActivityType.WORKOUT, ActivityOutcome.MISSED),
            activity("activity-thu", 3, 17, "Mobility session", ActivityType.ACTIVITY, ActivityOutcome.PLANNED),
            activity("activity-fri", 4, 18, "45-minute walk", ActivityType.ACTIVITY, ActivityOutcome.DONE),
            activity("activity-sat", 5, 10, "Bakal Gym — Full Body", ActivityType.WORKOUT, ActivityOutcome.DONE)))
}
