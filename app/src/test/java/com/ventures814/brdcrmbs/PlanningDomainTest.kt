package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlanningDomainTest {
    @Test fun weekAndMonthBoundariesRemainCalendarBased() {
        val range = DateRange.weekContaining(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), range.start)
        assertEquals(LocalDate.of(2027, 1, 4), range.endExclusive)
        assertEquals(LocalDate.of(2026, 8, 31), DateRange.weekContaining(LocalDate.of(2026, 9, 1)).start)
    }
    @Test(expected = IllegalArgumentException::class)
    fun changedWithoutActualIsRejected() {
        MealPlan(date = LocalDate.of(2026, 1, 1), text = "Original", outcome = MealOutcome.CHANGED)
    }
    @Test(expected = IllegalArgumentException::class)
    fun reversedFastIsRejected() {
        FastingEvent(start = Instant.ofEpochSecond(100), end = Instant.EPOCH, zone = ZoneOffset.UTC)
    }
    @Test fun futureAndHistoricalDaysAndOverlapUseSameRules() {
        for (date in listOf(LocalDate.of(2000, 12, 31), LocalDate.of(2040, 1, 1))) {
            val event = FastingEvent(start = date.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), end = date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(), zone = ZoneOffset.UTC)
            assertEquals(listOf(event), PlanningData(fasting = listOf(event)).forDay(date, ZoneOffset.UTC).fasting)
            assertTrue(PlanningData(fasting = listOf(event)).forDay(date.plusDays(2), ZoneOffset.UTC).fasting.isEmpty())
        }
    }
}
