package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class FastingIntegrityTest {
    private val zone = ZoneId.of("America/Denver")
    private val start = Instant.parse("2026-10-23T01:00:00Z")
    private fun event(from: Instant, to: Instant, id: String = "candidate") = FastingEvent(id, from, to, zone)
    private val existing = event(start, start.plus(Duration.ofHours(16)), "existing")

    @Test fun separateAndBothExactAdjacenciesAreAllowed() {
        for ((from, to) in listOf(start.minusSeconds(7200) to start.minusSeconds(3600),
            start.minusSeconds(3600) to start, existing.end to existing.end.plusSeconds(3600))) {
            assertNull(fastingConflict(event(from, to), listOf(existing), emptyList()))
        }
    }
    @Test fun partialContainedSurroundingIdenticalAndCrossMidnightIntervalsConflict() {
        for ((from, to) in listOf(existing.end.minusSeconds(60) to existing.end.plusSeconds(3600),
            start.minusSeconds(3600) to start.plusSeconds(60), start.plusSeconds(60) to existing.end.minusSeconds(60),
            start.minusSeconds(60) to existing.end.plusSeconds(60), start to existing.end)) {
            assertEquals("This fast overlaps an existing fast.", fastingConflict(event(from, to), listOf(existing), emptyList()))
        }
    }
    @Test fun allLongDurationsStayOneContinuousConflictInterval() {
        for (hours in listOf(24L, 36L, 48L, 72L, 211L)) {
            val long = event(start, start.plus(Duration.ofHours(hours)), "long")
            assertNotNull(fastingConflict(event(long.end.minusSeconds(60), long.end.plusSeconds(60)), listOf(long), emptyList()))
        }
    }
    @Test fun actualInstantsControlBothDstTransitionsIncludingRepeatedHour() {
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
            val from = date.atStartOfDay(zone).toInstant()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant()
            val day = event(from, to, "dst")
            assertNotNull(fastingConflict(event(to.minusSeconds(60), to.plusSeconds(60)), listOf(day), emptyList()))
            assertNull(fastingConflict(event(to, to.plusSeconds(60)), listOf(day), emptyList()))
        }
        val first = ZonedDateTime.of(LocalDateTime.of(2026, 11, 1, 1, 30), zone).withEarlierOffsetAtOverlap().toInstant()
        val second = first.plusSeconds(3600)
        assertFalse(event(first, first.plusSeconds(1800)).overlaps(event(second, second.plusSeconds(1800))))
    }
    @Test fun editExcludesOnlyItsOwnId() {
        assertNull(fastingConflict(existing, listOf(existing), emptyList(), existing.id))
        assertNotNull(fastingConflict(existing, listOf(existing, existing.copy(id = "other")), emptyList(), existing.id))
    }
    @Test fun noFastUsesEventsStoredZoneAndHalfOpenLocalDays() {
        val day = LocalDate.of(2026, 12, 31)
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val fast = event(day.atTime(19, 0).atZone(zone).toInstant(), midnight)
        val none = NoFastPlan(date = day, zone = ZoneId.of("Pacific/Kiritimati"))
        assertNotNull(fastingConflict(none, listOf(fast), emptyList()))
        assertNotNull(fastingConflict(fast, emptyList(), listOf(none)))
        assertNull(fastingConflict(none.copy(date = day.plusDays(1)), listOf(fast), emptyList()))
        val next = event(midnight, midnight.plusSeconds(3600))
        assertNotNull(fastingConflict(none.copy(date = day.plusDays(1)), listOf(next), emptyList()))
        val multi = fast.copy(end = midnight.plus(Duration.ofDays(3)))
        assertNotNull(fastingConflict(multi, emptyList(), listOf(none.copy(date = day.plusDays(2)))))
        assertNull(fastingConflict(none, emptyList(), emptyList()))
    }
    @Test fun feedbackDistinguishesConflictsAndExactDuplicates() {
        assertEquals("Added 4 planned items; skipped 2 identical Planned items. Skipped 1 fasting conflict.", CopyResult(4, 2, 1).feedback())
        assertTrue(CopyResult(0, 0, 2).feedback().contains("2 fasting conflicts"))
    }
}
