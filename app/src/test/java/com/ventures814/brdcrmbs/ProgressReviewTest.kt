package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ProgressReviewTest {
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2026, 10, 22)
    private val month = YearMonth.from(date)
    private fun meal(outcome: MealOutcome) = MealPlan(date = date, text = "Original", outcome = outcome,
        actual = if (outcome == MealOutcome.CHANGED) ActualMeal("Actual", label = "Lunch", notes = "Keep") else null)
    private fun activity(outcome: ActivityOutcome) = ActivityPlan(date = date, name = "Walk", outcome = outcome)
    private fun fast(outcome: FastingOutcome, hours: Long = 48) = date.atTime(19, 0).atZone(zone).toInstant().let {
        FastingEvent(start = it, end = it.plus(Duration.ofHours(hours)), zone = zone, outcome = outcome)
    }
    private fun PlanningData.day() = reviewMonth(month, zone).days[date.dayOfMonth - 1]

    @Test fun calendarLengthsOffsetsAndLeapYearsForEveryStartingWeekday() {
        val starts = mutableSetOf<Int>()
        for (year in 2024..2027) for (number in 1..12) {
            val value = YearMonth.of(year, number)
            val review = PlanningData().reviewMonth(value, zone)
            assertEquals(value.lengthOfMonth(), review.days.size)
            assertEquals(value.atDay(1).dayOfWeek.value - 1, review.leadingCells)
            assertEquals(value.atEndOfMonth(), review.days.last().date)
            starts += review.leadingCells
        }
        assertEquals((0..6).toSet(), starts)
        assertEquals(29, PlanningData().reviewMonth(YearMonth.of(2024, 2), zone).days.size)
        assertEquals(28, PlanningData().reviewMonth(YearMonth.of(2025, 2), zone).days.size)
    }
    @Test fun mealsHierarchyFractionsAndActualSeparation() {
        val cases = listOf(
            listOf(MealOutcome.DONE, MealOutcome.DONE) to ReviewStatus.SUCCESS,
            listOf(MealOutcome.DONE, MealOutcome.CHANGED, MealOutcome.PLANNED) to ReviewStatus.CHANGED,
            listOf(MealOutcome.CHANGED, MealOutcome.MISSED) to ReviewStatus.MISSED,
            listOf(MealOutcome.DONE, MealOutcome.PLANNED) to ReviewStatus.PLANNED,
            emptyList<MealOutcome>() to ReviewStatus.NONE)
        cases.forEach { (outcomes, expected) ->
            val plans = outcomes.map(::meal)
            val day = PlanningData(meals = plans).day()
            assertEquals(expected, day.meals); assertEquals(outcomes.count { it == MealOutcome.DONE }, day.mealsDone)
            assertEquals(outcomes.size, day.mealsTotal); assertEquals(plans, day.plans.meals)
            day.plans.meals.filter { it.outcome == MealOutcome.CHANGED }.forEach {
                assertEquals("Original", it.text); assertEquals("Actual", it.actual!!.text)
            }
        }
    }
    @Test fun activityHierarchyAndIndependentRecords() {
        listOf(listOf(ActivityOutcome.DONE, ActivityOutcome.DONE) to ReviewStatus.SUCCESS,
            listOf(ActivityOutcome.DONE, ActivityOutcome.PLANNED) to ReviewStatus.PLANNED,
            listOf(ActivityOutcome.PLANNED, ActivityOutcome.MISSED) to ReviewStatus.MISSED,
            emptyList<ActivityOutcome>() to ReviewStatus.NONE).forEach { (outcomes, status) ->
            val plans = outcomes.map(::activity)
            val day = PlanningData(activities = plans).day()
            assertEquals(status, day.activity); assertEquals(plans, day.plans.activities)
        }
    }
    @Test fun fastingOutcomesNoFastAndOverlappingEventHierarchy() {
        for ((outcome, expected) in listOf(FastingOutcome.DONE to ReviewStatus.SUCCESS, FastingOutcome.MISSED to ReviewStatus.MISSED, FastingOutcome.PLANNED to ReviewStatus.PLANNED)) {
            assertEquals(expected, PlanningData(fasting = listOf(fast(outcome))).day().fasting)
        }
        assertEquals(ReviewStatus.MISSED, PlanningData(fasting = listOf(fast(FastingOutcome.DONE), fast(FastingOutcome.MISSED))).day().fasting)
        assertEquals(ReviewStatus.PLANNED, PlanningData(fasting = listOf(fast(FastingOutcome.DONE), fast(FastingOutcome.PLANNED))).day().fasting)
        val noFast = NoFastPlan(date = date, zone = zone)
        val review = PlanningData(noFast = listOf(noFast)).reviewMonth(month, zone)
        assertEquals(ReviewStatus.NONE, review.days[21].fasting); assertEquals(listOf(noFast), review.days[21].plans.noFast)
        assertNull(review.fasting.percent)
    }
    @Test fun continuousFastsSpanDatesWithoutMultiplyingSummaryAndUseHalfOpenMidnights() {
        for (hours in listOf(16L, 48L, 72L, 211L)) {
            val event = fast(FastingOutcome.DONE, hours)
            val review = PlanningData(fasting = listOf(event)).reviewMonth(month, zone)
            val occupied = review.days.filter { it.plans.fasting.isNotEmpty() }
            assertTrue(occupied.size >= 2)
            assertEquals(AdherenceSummary(1, 1), review.fasting)
            occupied.forEach { assertEquals(listOf(event), it.plans.fasting) }
            assertFalse(occupied.first().fastContinuesBefore); assertTrue(occupied.first().fastContinuesAfter)
        }
        val midnight = date.plusDays(1).atStartOfDay(zone).toInstant()
        val event = FastingEvent(start = midnight.minusSeconds(3600), end = midnight, zone = zone)
        val review = PlanningData(fasting = listOf(event)).reviewMonth(month, zone)
        assertEquals(1, review.days.count { it.plans.fasting.isNotEmpty() })
        assertFalse(review.days[21].fastContinuesAfter)
    }
    @Test fun summaryUsesOnlyResolvedMonthlyRecordsAndChangedIsNotDone() {
        val plans = PlanningData(
            fasting = FastingOutcome.entries.map { fast(it) },
            meals = MealOutcome.entries.map(::meal) + meal(MealOutcome.DONE).copy(date = date.plusMonths(1)) + meal(MealOutcome.PLANNED).copy(date = month.atEndOfMonth()),
            activities = ActivityOutcome.entries.map(::activity),
            noFast = listOf(NoFastPlan(date = date, zone = zone)))
        val review = plans.reviewMonth(month, zone)
        assertEquals(AdherenceSummary(1, 2), review.fasting); assertEquals(50, review.fasting.percent)
        assertEquals(AdherenceSummary(1, 3), review.meals); assertEquals(33, review.meals.percent)
        assertEquals(AdherenceSummary(1, 2), review.activity); assertEquals(50, review.activity.percent)
        val empty = PlanningData().reviewMonth(month, zone)
        assertNull(empty.fasting.percent); assertNull(empty.meals.percent); assertNull(empty.activity.percent)
        assertEquals(0, AdherenceSummary(0, 1).percent)
    }
    @Test fun monthYearAndDstOverlapIncludesEarlierStartsWithoutMutatingSource() {
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 1))) {
            val start = date.minusDays(1).atTime(19, 0).atZone(zone).toInstant()
            val event = FastingEvent(start = start, end = start.plus(Duration.ofHours(72)), zone = zone, outcome = FastingOutcome.DONE)
            val original = PlanningData(fasting = listOf(event))
            val review = original.reviewMonth(YearMonth.from(date), zone)
            val day = review.days[date.dayOfMonth - 1]
            assertEquals(listOf(event), day.plans.fasting); assertTrue(day.fastContinuesBefore)
            assertEquals(AdherenceSummary(1, 1), review.fasting); assertEquals(listOf(event), original.fasting)
        }
    }
    @Test fun dailyAdherenceUsesResolvedItemsAcrossCategoriesAndKeepsChangedDistinct() {
        val plans = PlanningData(fasting = listOf(fast(FastingOutcome.DONE)),
            meals = listOf(meal(MealOutcome.DONE)), activities = listOf(activity(ActivityOutcome.MISSED)))
        assertEquals(AdherenceSummary(2, 3), plans.day().adherence)
        assertEquals(67, plans.day().adherence.percent)
        val changed = plans.copy(meals = plans.meals + meal(MealOutcome.CHANGED) + meal(MealOutcome.PLANNED),
            noFast = listOf(NoFastPlan(date = date, zone = zone)))
        assertEquals(AdherenceSummary(2, 4), changed.day().adherence)
        assertEquals(50, changed.day().adherence.percent)
        assertNull(PlanningData(meals = listOf(meal(MealOutcome.PLANNED)), noFast = changed.noFast).day().adherence.percent)
        assertEquals(0, PlanningData(meals = listOf(meal(MealOutcome.MISSED))).day().adherence.percent)
        assertEquals(AdherenceSummary(1, 1), PlanningData(fasting = listOf(fast(FastingOutcome.DONE, 72))).day().adherence)
    }
}
