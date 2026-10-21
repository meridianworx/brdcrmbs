package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.week.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlanningCopiesTest {
    private val zone = ZoneId.of("America/Denver")
    @Test fun originalFieldsBecomeIndependentPlannedRecordsWithoutActualsOrLinks() {
        val day = LocalDate.of(2026, 12, 31)
        val meal = MealPlan(date = day, time = LocalTime.of(11, 0, 30, 123), text = "Original", label = "Lunch", notes = "Keep",
            outcome = MealOutcome.CHANGED, actual = ActualMeal("Actual"))
        val activity = ActivityPlan(date = day, name = "Gym", type = ActivityType.WORKOUT, notes = "Keep", outcome = ActivityOutcome.MISSED, rescheduledFromId = "old")
        val source = PlanningData(meals = listOf(meal), activities = listOf(activity), noFast = listOf(NoFastPlan(date = day, zone = zone)))
        val shifted = source.shiftedPlans(1)
        assertNotEquals(meal.id, shifted.meals.single().id)
        assertEquals(meal.copy(id = shifted.meals.single().id, date = day.plusDays(1), outcome = MealOutcome.PLANNED, actual = null), shifted.meals.single())
        assertEquals(ActivityOutcome.PLANNED, shifted.activities.single().outcome)
        assertNull(shifted.activities.single().rescheduledFromId)
        assertEquals(ActivityType.WORKOUT, shifted.activities.single().type)
        assertEquals(day.plusDays(1), shifted.noFast.single().date)
        assertNotEquals(source.noFast.single().id, shifted.noFast.single().id)
        assertEquals(MealOutcome.CHANGED, meal.outcome); assertEquals("Actual", meal.actual!!.text)
    }
    @Test fun crossMidnightAndLongFastsKeepOneRecordAndElapsedDuration() {
        val day = LocalDate.of(2026, 1, 30)
        for (hours in listOf(2L, 16L, 24L, 36L, 48L, 72L, 211L)) {
            val start = day.atTime(19, 0).atZone(zone).toInstant()
            val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(hours)), zone = zone, preset = "Custom", outcome = FastingOutcome.DONE)
            val copy = PlanningData(fasting = listOf(fast)).shiftedPlans(3).fasting.single()
            assertEquals(day.plusDays(3).atTime(19, 0), copy.start.atZone(zone).toLocalDateTime())
            assertEquals(Duration.ofHours(hours), Duration.between(copy.start, copy.end))
            assertEquals(FastingOutcome.PLANNED, copy.outcome); assertNotEquals(fast.id, copy.id)
        }
    }
    @Test fun dstKeepsLocalStartAndElapsedHoursEvenWhenEndClockChanges() {
        val start = LocalDate.of(2026, 3, 6).atTime(19, 0).atZone(zone).toInstant()
        val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(16)), zone = zone)
        val copy = PlanningData(fasting = listOf(fast)).shiftedPlans(1).fasting.single()
        assertEquals(LocalDateTime.of(2026, 3, 7, 19, 0), copy.start.atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 3, 8, 12, 0), copy.end.atZone(zone).toLocalDateTime())
        assertEquals(Duration.ofHours(16), Duration.between(copy.start, copy.end))
    }
    @Test fun nonexistentDstStartIsRejectedRatherThanShifted() {
        val start = LocalDate.of(2026, 3, 7).atTime(2, 30).atZone(zone).toInstant()
        val fast = FastingEvent(start = start, end = start.plusSeconds(3600), zone = zone)
        assertThrows(IllegalArgumentException::class.java) { PlanningData(fasting = listOf(fast)).shiftedPlans(1) }
    }
    @Test fun repeatedHourUsesOriginalOffsetWhenValidOtherwiseEarlierOffset() {
        val local = LocalDateTime.of(2026, 11, 1, 1, 30)
        for (offset in zone.rules.getValidOffsets(local)) {
            val fast = FastingEvent(start = local.toInstant(offset), end = local.toInstant(offset).plusSeconds(3600), zone = zone)
            assertEquals(fast.start, PlanningData(fasting = listOf(fast)).shiftedPlans(0).fasting.single().start)
        }
        val before = local.minusDays(1).atZone(zone).toInstant()
        val fast = FastingEvent(start = before, end = before.plusSeconds(3600), zone = zone)
        assertEquals(ZoneOffset.ofHours(-6), PlanningData(fasting = listOf(fast)).shiftedPlans(1).fasting.single().start.atZone(zone).offset)
    }
    @Test fun translationKeepsTheFullMultiDayInterval() {
        val start = LocalDate.of(2026, 10, 19).atTime(19, 0).atZone(zone).toInstant()
        val fast = FastingEvent(start = start, end = start.plus(Duration.ofHours(48)), zone = zone)
        val copy = PlanningData(fasting = listOf(fast)).shiftedPlans(3).fasting.single()
        assertEquals(LocalDate.of(2026, 10, 22), copy.start.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 24), copy.end.atZone(zone).toLocalDate())
        assertEquals(Duration.ofHours(48), Duration.between(copy.start, copy.end))
    }
    @Test fun draftPreventsSelfSelectionAndRetainsSingleOrMultipleChoices() {
        val day = LocalDate.of(2026, 12, 31)
        val draft = ShortcutDraft(Shortcut.COPY_DAY, day, DateRange.weekContaining(day).start)
        assertTrue(draft.toggle(day).destinations.isEmpty())
        assertEquals(setOf(day.plusDays(2)), draft.toggle(day.plusDays(1)).toggle(day.plusDays(2)).destinations)
        val multiple = draft.copy(mode = Shortcut.COPY_DAYS).toggle(day.plusDays(1)).toggle(day.plusDays(2))
        assertEquals(2, multiple.destinations.size)
        assertEquals(setOf(day.plusDays(2)), multiple.toggle(day.plusDays(1)).destinations)
    }
    @Test fun emptyStructureStaysEmptyAndOptionalTimesRemainAbsent() {
        assertEquals(PlanningData(), PlanningData().shiftedPlans(7))
        val day = LocalDate.of(2026, 2, 28)
        val copied = PlanningData(meals = listOf(MealPlan(date = day, text = "Soup")),
            activities = listOf(ActivityPlan(date = day, name = "Walk"))).shiftedPlans(1)
        assertEquals(LocalDate.of(2026, 3, 1), copied.meals.single().date)
        assertNull(copied.meals.single().time); assertNull(copied.activities.single().time)
    }
}
