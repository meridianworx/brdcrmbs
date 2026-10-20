package com.ventures814.brdcrmbs.ui.week

import com.ventures814.brdcrmbs.domain.*
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeekDay(val label: String, val number: String, val description: String)
data class WeekRow(val title: String, val subtitle: String, val time: String? = null)
data class WeekUiState(
    val weekLabel: String,
    val days: List<WeekDay>,
    val selectedDay: Int,
    val fasting: List<WeekRow> = emptyList(),
    val meals: List<WeekRow> = emptyList(),
    val activities: List<WeekRow> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false
)

/** Date/time display conversions live outside Compose and outside database storage. */
fun presentWeek(
    range: DateRange,
    selectedDay: Int,
    plans: PlanningData,
    zone: ZoneId,
    locale: Locale = Locale.getDefault()
): WeekUiState {
    val date = range.start.plusDays(selectedDay.toLong())
    val dayPlans = plans.forDay(date, zone)
    val time = DateTimeFormatter.ofPattern("h:mm a", locale)
    val monthDay = DateTimeFormatter.ofPattern("MMM d", locale)
    val fullDate = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    val dayName = DateTimeFormatter.ofPattern("EEE", locale)
    val last = range.endExclusive.minusDays(1)
    val weekLabel = if (range.start.month == last.month && range.start.year == last.year) {
        "${range.start.format(DateTimeFormatter.ofPattern("MMMM d", locale))}–${last.dayOfMonth}, ${last.year}"
    } else "${range.start.format(fullDate)} – ${last.format(fullDate)}"
    return WeekUiState(
        weekLabel,
        (0..6).map { offset ->
            val day = range.start.plusDays(offset.toLong())
            WeekDay(day.format(dayName), day.dayOfMonth.toString(), "${day.format(dayName)} ${day.dayOfMonth}, ${day.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}")
        },
        selectedDay,
        dayPlans.fasting.map { event ->
            val start = event.start.atZone(event.zone)
            val end = event.end.atZone(event.zone)
            val acrossDates = start.toLocalDate() != end.toLocalDate()
            val startLabel = if (acrossDates) "${start.format(monthDay)}, ${start.format(time)}" else start.format(time)
            val endLabel = if (acrossDates) "${end.format(monthDay)}, ${end.format(time)}" else end.format(time)
            val minutes = Duration.between(event.start, event.end).toMinutes()
            val duration = if (minutes % 60 == 0L) "${minutes / 60} hour fast" else "${minutes / 60}h ${minutes % 60}m fast"
            WeekRow("$startLabel → $endLabel", listOfNotNull(event.preset, duration).joinToString(" · "))
        },
        dayPlans.meals.map { WeekRow(it.text, it.label.orEmpty(), it.time?.format(time)) },
        dayPlans.activities.map {
            WeekRow(it.name, if (it.type == ActivityType.WORKOUT) "Workout" else "Activity", it.time?.format(time))
        }
    )
}
