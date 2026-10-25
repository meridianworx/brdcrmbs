package com.ventures814.brdcrmbs.debugqa

import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ProgressQaScenarioTest {
    @Test fun canonicalAndAdjacentWeeksHaveNoFastingOrNoFastConflictsIncludingDst() {
        for (monday in listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 10, 26))) {
            val current = QaWeek(monday, ZoneId.of("America/Denver"))
            val first = progressQaScenario(current)
            val next = progressQaScenario(current.copy(monday = monday.plusWeeks(1)))
            val events = first.fasting + next.fasting
            val none = first.noFast + next.noFast
            events.forEach { assertNull(fastingConflict(it, events, none, it.id)) }
            none.forEach { assertNull(fastingConflict(it, events, none, it.id)) }
            assertTrue(first.fasting.any { it.outcome == FastingOutcome.DONE })
            assertTrue(first.fasting.any { it.outcome == FastingOutcome.MISSED })
            assertTrue(first.fasting.any { it.outcome == FastingOutcome.PLANNED })
            assertEquals(1, first.fasting.count { Duration.between(it.start, it.end).toHours() == 48L })
        }
    }
    private val week = QaWeek(LocalDate.of(2026, 10, 19), ZoneId.of("America/Denver"))
    @Test fun canonicalScenarioPreservesOutcomesAndActualSnapshot() {
        val data = progressQaScenario(week)
        assertEquals(5, data.fasting.size); assertEquals(1, data.noFast.size)
        assertEquals(9, data.meals.size); assertEquals(6, data.activities.size)
        assertEquals(21, (data.fasting.map { it.id } + data.noFast.map { it.id } + data.meals.map { it.id } + data.activities.map { it.id }).toSet().size)
        assertEquals(listOf(FastingOutcome.DONE, FastingOutcome.DONE, FastingOutcome.PLANNED, FastingOutcome.MISSED, FastingOutcome.PLANNED), data.fasting.map { it.outcome })
        assertEquals(week.monday.plusDays(1), data.noFast.single().date)
        assertEquals(3, data.meals.count { it.date == week.monday && it.outcome == MealOutcome.DONE })
        val changed = data.meals.single { it.outcome == MealOutcome.CHANGED }
        assertEquals("Pork + cabbage", changed.text)
        assertEquals(LocalTime.of(18, 0), changed.time)
        assertEquals(ActualMeal("Chicken + rice", LocalTime.of(18, 30), "Dinner", "Progress test data — changed meal"), changed.actual)
        assertEquals(listOf(MealOutcome.DONE, MealOutcome.DONE, MealOutcome.MISSED), data.meals.filter { it.date == week.monday.plusDays(2) }.map { it.outcome })
        assertEquals(ActivityOutcome.MISSED, data.activities[2].outcome)
        assertTrue(data.meals.none { it.date >= week.monday.plusDays(4) })
        assertTrue(data.activities.none { it.date == week.endInclusive })
        assertEquals(data, progressQaScenario(week))
    }
    @Test fun ownershipIsExactAndWeekSpecific() {
        val id = QaOwnership.id(week, "meal-mon-1")
        assertEquals(week.monday, QaOwnership.weekOf(id))
        for (invalid in listOf("$id-extra", "x$id", "${QaOwnership.PREFIX}2026-10-20|meal-mon-1", "${QaOwnership.PREFIX}2026-10-19|unknown", "${QaOwnership.PREFIX}bad|fast-mon")) assertNull(QaOwnership.weekOf(invalid))
        assertNotEquals(id, QaOwnership.id(week.copy(monday = week.monday.plusWeeks(1)), "meal-mon-1"))
    }
    @Test fun aggregationUsesExistingOverlapAndResolvedRules() {
        val review = progressQaScenario(week).reviewMonth(YearMonth.from(week.monday), week.zone)
        assertEquals(AdherenceSummary(2, 3), review.fasting)
        assertEquals(AdherenceSummary(6, 8), review.meals)
        assertEquals(AdherenceSummary(4, 5), review.activity)
        fun day(offset: Long) = review.days.single { it.date == week.monday.plusDays(offset) }
        assertEquals(ReviewStatus.NONE, day(1).fasting) // Intentional No fast has no contradictory event.
        assertEquals(ReviewStatus.CHANGED, day(1).meals)
        assertEquals(ReviewStatus.MISSED, day(5).fasting) // Adjacent Friday miss ends before Saturday fast starts.
        assertEquals(ReviewStatus.PLANNED, day(6).fasting)
        assertEquals(ReviewStatus.NONE, day(6).meals)
        assertEquals(ReviewStatus.NONE, day(6).activity)
        assertEquals(100, day(0).adherence.percent)
    }
    @Test fun currentWeekHandlesYearBoundariesAndDeviceZones() {
        for (zone in listOf("America/Denver", "Pacific/Kiritimati", "Pacific/Honolulu")) {
            val clock = Clock.fixed(Instant.parse("2027-01-01T02:00:00Z"), ZoneId.of(zone))
            val current = QaWeek.current(clock)
            assertEquals(DayOfWeek.MONDAY, current.monday.dayOfWeek)
            assertFalse(LocalDate.now(clock).isBefore(current.monday))
            assertFalse(LocalDate.now(clock).isAfter(current.endInclusive))
            assertEquals(clock.zone, current.zone)
        }
    }
    @Test fun continuousFastIs48ElapsedHoursIncludingDst() {
        for (monday in listOf(week.monday, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 10, 26))) {
            val scenario = progressQaScenario(week.copy(monday = monday))
            val fast = scenario.fasting.single { it.preset == "48h" }
            assertEquals(48, Duration.between(fast.start, fast.end).toHours().toInt())
            assertEquals(monday.plusDays(7), fast.end.atZone(week.zone).toLocalDate())
            assertEquals(7, fast.start.atZone(week.zone).hour)
        }
    }
}
