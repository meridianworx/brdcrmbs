package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.today.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class FastingProgressTest {
    private val zone = ZoneId.of("America/Denver")
    private val start = LocalDate.of(2026, 10, 23).atTime(19, 0).atZone(zone).toInstant()
    private fun fast(hours: Long = 16) = FastingEvent("fast", start, start.plus(Duration.ofHours(hours)), zone, "16:8")

    @Test fun boundariesAndClampingKeepTimeSeparateFromAdherence() {
        val event = fast()
        for (offset in listOf(-100L, -1, 0, 8, 16, 17, 100)) {
            val result = event.progressAt(start.plus(Duration.ofHours(offset)))
            assertEquals(when { offset < 0 -> FastingTimeState.UPCOMING; offset < 16 -> FastingTimeState.ACTIVE; else -> FastingTimeState.ENDED }, result.state)
            assertEquals((offset / 16f).coerceIn(0f, 1f), result.fraction, 0.00001f)
            assertTrue(result.percent in 0..100)
            assertFalse(result.elapsed.isNegative); assertFalse(result.remaining.isNegative)
            assertEquals(FastingOutcome.PLANNED, event.outcome)
        }
        assertEquals(0, event.progressAt(start).percent)
        assertEquals(100, event.progressAt(event.end).percent)
        assertEquals(Duration.ZERO, event.progressAt(event.end).remaining)
    }

    @Test fun crossMidnightAndAllDurationsUseRealElapsedInstants() {
        for (hours in listOf(16L, 24, 36, 48, 72, 211, 504)) {
            val event = fast(hours)
            val halfway = event.progressAt(start.plus(Duration.ofMinutes(hours * 30)))
            assertEquals(FastingTimeState.ACTIVE, halfway.state)
            assertEquals(0.5f, halfway.fraction, 0.00001f)
            assertEquals(50, halfway.percent)
            assertEquals(Duration.ofMinutes(hours * 30), halfway.remaining)
        }
    }

    @Test fun nearEndRetainsCorrectMinutesAndNeverRoundsToEarlyCompletion() {
        val event = fast()
        val result = event.progressAt(event.end.minus(Duration.ofHours(5)).minus(Duration.ofMinutes(55)))
        assertEquals("5h 55m", remainingFastingTime(result.remaining))
        assertEquals(63, result.percent)
        val almost = event.progressAt(event.end.minusMillis(1))
        assertEquals(FastingTimeState.ACTIVE, almost.state)
        assertEquals(99, almost.percent)
        assertEquals("<1m", remainingFastingTime(almost.remaining))
    }

    @Test fun remainingFormattingIsConciseWithoutSeconds() {
        for ((minutes, expected) in listOf(45L to "45m", 60L to "1h", 355L to "5h 55m", 1800L to "1d 6h", 3060L to "2d 3h", 1505L to "1d 1h 5m")) {
            assertEquals(expected, remainingFastingTime(Duration.ofMinutes(minutes)))
        }
    }

    @Test fun springAndFallDstUsePersistedDurationRatherThanWallHours() {
        for (date in listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 10, 31))) {
            val begin = date.atTime(19, 0).atZone(zone).toInstant()
            val event = fast().copy(start = begin, end = begin.plus(Duration.ofHours(16)))
            assertEquals(50, event.progressAt(begin.plus(Duration.ofHours(8))).percent)
            assertEquals(Duration.ofHours(8), event.progressAt(begin.plus(Duration.ofHours(8))).remaining)
            assertNotEquals(16L, Duration.between(begin.atZone(zone).toLocalDateTime(), event.end.atZone(zone).toLocalDateTime()).toHours())
        }
    }

    @Test fun endLabelsUseEventZoneAcrossDatesAndYears() {
        val event = fast()
        assertEquals("Ends tomorrow at 11:00 AM", event.endTimeLabel(start))
        assertEquals("Ends at 11:00 AM", event.endTimeLabel(event.end.minusSeconds(60)))
        assertEquals("Ends Oct 26 at 7:00 PM", fast(72).endTimeLabel(start))
        val yearStart = LocalDate.of(2026, 12, 31).atTime(19, 0).atZone(zone).toInstant()
        val yearFast = event.copy(start = yearStart, end = yearStart.plus(Duration.ofHours(72)))
        assertEquals("Ends Jan 3, 2027 at 7:00 PM", yearFast.endTimeLabel(yearStart))
    }

    @Test fun onlyOneUnresolvedCurrentlyActiveEventGetsEmphasis() {
        val current = start.plus(Duration.ofHours(8))
        val event = fast()
        val later = event.copy(id = "later", start = start.plusSeconds(1))
        val records = listOf(event, later, event.copy(id = "future", start = current.plusSeconds(1)),
            event.copy(id = "ended", end = current), event.copy(id = "done", outcome = FastingOutcome.DONE),
            event.copy(id = "missed", outcome = FastingOutcome.MISSED))
        assertEquals("later", activeFastingId(records, current))
        assertEquals("later", activeFastingId(records.reversed(), current))
        assertNull(activeFastingId(records.filter { it.outcome != FastingOutcome.PLANNED }, current))
        assertNull(activeFastingId(emptyList(), current))
    }
}
