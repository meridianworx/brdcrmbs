package com.ventures814.brdcrmbs.ui.today

import com.ventures814.brdcrmbs.domain.FastingEvent
import com.ventures814.brdcrmbs.domain.FastingOutcome
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.floor

enum class FastingTimeState { UPCOMING, ACTIVE, ENDED }

/** Presentation derived from persisted instants; time completion never changes adherence. */
data class FastingProgress(
    val state: FastingTimeState,
    val fraction: Float,
    val percent: Int,
    val elapsed: Duration,
    val remaining: Duration,
)

fun FastingEvent.progressAt(now: Instant): FastingProgress {
    val duration = Duration.between(start, end)
    val state = when {
        now < start -> FastingTimeState.UPCOMING
        now < end -> FastingTimeState.ACTIVE
        else -> FastingTimeState.ENDED
    }
    val elapsed = Duration.between(start, now).coerceIn(Duration.ZERO, duration)
    val remaining = Duration.between(now, end).coerceIn(Duration.ZERO, duration)
    fun Duration.secondsValue() = seconds.toDouble() + nano / 1_000_000_000.0
    val ratio = (elapsed.secondsValue() / duration.secondsValue()).coerceIn(0.0, 1.0)
    return FastingProgress(state, ratio.toFloat(), floor(ratio * 100).toInt(), elapsed, remaining)
}

/** Overlapping plans remain visible, with one deterministic live emphasis. */
fun activeFastingId(events: List<FastingEvent>, now: Instant): String? = events
    .filter { it.outcome == FastingOutcome.PLANNED && now >= it.start && now < it.end }
    .maxWithOrNull(compareBy<FastingEvent> { it.start }.thenBy { it.id })?.id

fun remainingFastingTime(duration: Duration): String {
    if (duration < Duration.ofMinutes(1)) return "<1m"
    val minutes = duration.toMinutes()
    return listOf(minutes / 1440 to "d", minutes % 1440 / 60 to "h", minutes % 60 to "m")
        .filter { it.first > 0 }.joinToString(" ") { "${it.first}${it.second}" }
}

fun FastingEvent.endTimeLabel(now: Instant, clockFormat: com.ventures814.brdcrmbs.preferences.ClockFormat = com.ventures814.brdcrmbs.preferences.ClockFormat()): String {
    val endLocal = end.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val date = endLocal.toLocalDate()
    val time = endLocal.format(clockFormat.formatter())
    return when (date) {
        today -> "Ends at $time"
        today.plusDays(1) -> "Ends tomorrow at $time"
        else -> "Ends ${endLocal.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy"))} at $time"
    }
}
