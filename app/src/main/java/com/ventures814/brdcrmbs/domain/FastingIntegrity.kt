package com.ventures814.brdcrmbs.domain

/** A rejected schedule write; existing history is never repaired or hidden. */
class FastingConflictException(message: String) : IllegalArgumentException(message)

fun FastingEvent.overlaps(other: FastingEvent): Boolean = start < other.end && end > other.start

/** Calendar ownership follows the continuous event's recorded zone, with an exclusive end. */
fun FastingEvent.appliesTo(date: java.time.LocalDate): Boolean {
    val day = DateRange.day(date)
    return start < day.endInstant(zone) && end > day.startInstant(zone)
}

fun fastingConflict(candidate: FastingPlan, events: List<FastingEvent>, noFast: List<NoFastPlan>, excludeId: String? = null): String? =
    when (candidate) {
        is FastingEvent -> when {
            events.any { it.id != excludeId && candidate.overlaps(it) } -> "This fast overlaps an existing fast."
            noFast.any { it.id != excludeId && candidate.appliesTo(it.date) } -> "Remove the No Fast plan before adding a fast on this day."
            else -> null
        }
        is NoFastPlan -> if (events.any { it.id != excludeId && it.appliesTo(candidate.date) })
            "A fasting plan already applies to this day." else null
    }
