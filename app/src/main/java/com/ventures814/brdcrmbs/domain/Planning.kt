package com.ventures814.brdcrmbs.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class FastingOutcome { PLANNED, DONE, MISSED }
enum class MealOutcome { PLANNED, DONE, CHANGED, MISSED }
enum class ActivityOutcome { PLANNED, DONE, MISSED }
enum class ActivityType { WORKOUT, ACTIVITY }

data class FastingEvent(
    val id: String = UUID.randomUUID().toString(),
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val preset: String? = null,
    val notes: String? = null,
    val outcome: FastingOutcome = FastingOutcome.PLANNED
) {
    init {
        require(id.isNotBlank())
        require(end > start) { "A fast must end after it starts" }
        require(start.nano % 1_000_000 == 0 && end.nano % 1_000_000 == 0) {
            "Fasting timestamps use millisecond precision"
        }
    }
}

data class ActualMeal(
    val text: String,
    val time: LocalTime? = null,
    val label: String? = null,
    val notes: String? = null
) {
    init { require(text.isNotBlank()) }
}

data class MealPlan(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val time: LocalTime? = null,
    val text: String,
    val label: String? = null,
    val notes: String? = null,
    val outcome: MealOutcome = MealOutcome.PLANNED,
    val actual: ActualMeal? = null
) {
    init {
        require(id.isNotBlank() && text.isNotBlank())
        require(outcome != MealOutcome.CHANGED || actual != null) {
            "Changed meals must retain actual meal information"
        }
    }
}

data class ActivityPlan(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val time: LocalTime? = null,
    val name: String,
    val type: ActivityType = ActivityType.ACTIVITY,
    val notes: String? = null,
    val outcome: ActivityOutcome = ActivityOutcome.PLANNED,
    val rescheduledFromId: String? = null
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(rescheduledFromId != id)
    }
}

/** Half-open calendar range: start inclusive, endExclusive excluded. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    init { require(start < endExclusive) }
    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()
    fun endInstant(zone: ZoneId): Instant = endExclusive.atStartOfDay(zone).toInstant()

    companion object {
        fun day(date: LocalDate) = DateRange(date, date.plusDays(1))
        fun weekContaining(date: LocalDate): DateRange {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DateRange(monday, monday.plusWeeks(1))
        }
    }
}

data class PlanningData(
    val fasting: List<FastingEvent> = emptyList(),
    val meals: List<MealPlan> = emptyList(),
    val activities: List<ActivityPlan> = emptyList()
) {
    fun forDay(date: LocalDate, zone: ZoneId): PlanningData {
        val range = DateRange.day(date)
        return PlanningData(
            fasting.filter { it.start < range.endInstant(zone) && it.end > range.startInstant(zone) },
            meals.filter { it.date == date },
            activities.filter { it.date == date }
        )
    }
}
