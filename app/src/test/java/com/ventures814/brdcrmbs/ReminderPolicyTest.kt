package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ReminderPolicyTest {
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2026, 12, 31)
    private val now = date.atTime(10, 0).atZone(zone).toInstant()
    private val all = ReminderPreferences(ReminderType.entries.toSet())
    private fun meal(id: String = "meal", time: LocalTime? = LocalTime.NOON) = MealPlan(id, date, time, "Soup")
    private fun activity(time: LocalTime? = LocalTime.NOON) = ActivityPlan("activity", date, time, "Walk")
    private fun fast() = FastingEvent("fast", now.plusSeconds(3600), now.plus(Duration.ofHours(49)), zone, "48h")
    private fun eligible(data: PlanningData, prefs: ReminderPreferences = all) = ReminderPolicy.eligible(data, prefs, zone)
    private val backend = Fake()
    private val sync = ReminderReconciler(backend)

    @Test fun defaultsOffAndEachCategoryIsIndependent() {
        val data = PlanningData(listOf(fast()), listOf(meal()), listOf(activity()))
        assertTrue(eligible(data, ReminderPreferences()).isEmpty())
        for (type in ReminderType.entries) {
            val prefs = ReminderPreferences().with(type, true)
            assertEquals(setOf(type), eligible(data, prefs).map { it.type }.toSet())
            assertTrue(eligible(data, prefs.with(type, false)).isEmpty())
        }
    }
    @Test fun timedFuturePlansOnlyAndDisablingCancelsCategory() {
        val data = PlanningData(meals = listOf(meal(), meal("untimed", null), meal("past", LocalTime.of(9, 0))),
            activities = listOf(activity(), activity(null).copy(id = "untimed-activity")))
        sync.sync(eligible(data), now, true)
        assertEquals(setOf("MEAL:meal", "ACTIVITY:activity"), backend.pending().keys)
        sync.sync(eligible(data, all.with(ReminderType.MEAL, false)), now, true)
        assertEquals(setOf("ACTIVITY:activity"), backend.pending().keys)
        sync.sync(eligible(data, ReminderPreferences()), now, true)
        assertTrue(backend.pending().isEmpty())
    }
    @Test fun continuousFastHasOneStartEndAndNoFastHasNeither() {
        val event = fast()
        val data = PlanningData(fasting = listOf(event), noFast = listOf(NoFastPlan("none", date, zone)))
        val reminders = eligible(data)
        assertEquals(2, reminders.size)
        assertEquals(listOf(event.start, event.end), reminders.map { it.at })
        assertTrue(eligible(PlanningData(noFast = data.noFast)).isEmpty())
        sync.sync(reminders, event.start.plusSeconds(1), true)
        assertEquals(setOf("FAST_END:fast"), backend.pending().keys)
    }
    @Test fun editsReplaceSameStableIdentityAndDeletesCancelAllTypes() {
        val original = PlanningData(listOf(fast()), listOf(meal()), listOf(activity()))
        sync.sync(eligible(original), now, true)
        val edited = PlanningData(listOf(fast().copy(start = now.plusSeconds(7200), end = now.plusSeconds(190000))),
            listOf(meal().copy(time = LocalTime.of(13, 0))), listOf(activity().copy(time = LocalTime.of(14, 0))))
        sync.sync(eligible(edited), now, true)
        assertEquals(4, backend.pending().size)
        assertEquals(8, backend.schedules)
        assertEquals(eligible(edited).associateBy { it.key }, backend.pending())
        sync.sync(emptyList(), now, true)
        assertTrue(backend.pending().isEmpty())
    }
    @Test fun resolvedOutcomesCancelWithoutChangingRawHistory() {
        for (outcome in listOf(MealOutcome.DONE, MealOutcome.MISSED, MealOutcome.CHANGED)) {
            val original = PlanningData(listOf(fast()), listOf(meal()), listOf(activity()))
            sync.sync(eligible(original), now, true)
            val changed = original.copy(fasting = listOf(fast().copy(outcome = FastingOutcome.DONE)),
                meals = listOf(meal().copy(outcome = outcome, actual = if (outcome == MealOutcome.CHANGED) ActualMeal("Actual") else null)),
                activities = listOf(activity().copy(outcome = ActivityOutcome.MISSED)))
            sync.sync(eligible(changed), now, true)
            assertTrue(backend.pending().isEmpty())
            assertEquals(outcome, changed.meals.single().outcome)
        }
    }
    @Test fun repeatedSyncAndRebootAreIdempotentAndIgnorePastBacklog() {
        val reminders = eligible(PlanningData(meals = listOf(meal())))
        repeat(3) { sync.sync(reminders, now, true) }
        assertEquals(1, backend.schedules)
        repeat(3) { sync.sync(reminders, now, true, recovery = true) }
        assertEquals(1, backend.pending().size)
        sync.sync(reminders, reminders.single().at.plusSeconds(1), true, recovery = true)
        assertTrue(backend.pending().isEmpty())
    }
    @Test fun permissionLossCancelsAndRestorationSchedulesOnlyFuture() {
        val reminders = eligible(PlanningData(meals = listOf(meal())))
        sync.sync(reminders, now, true)
        sync.sync(reminders, now, false)
        assertTrue(backend.pending().isEmpty())
        sync.sync(reminders, now, true)
        assertEquals(1, backend.pending().size)
    }
    @Test fun inexactDeliveryIsRevalidatedAndConsumedOnce() {
        val reminders = eligible(PlanningData(meals = listOf(meal())))
        val item = reminders.single()
        sync.sync(reminders, now, true)
        sync.sync(reminders, item.at.plusSeconds(60), true) // Normal sync must not erase an already-due inexact alarm.
        assertEquals(1, backend.pending().size)
        assertTrue(sync.deliver(item.key, item.at, reminders, item.at.plusSeconds(60), true))
        assertFalse(sync.deliver(item.key, item.at, reminders, item.at.plusSeconds(60), true))
        sync.sync(reminders, now, true, recovery = true) // Clock moved back; still not replayed.
        assertTrue(backend.pending().isEmpty()); assertEquals(1, backend.posts)
    }
    @Test fun staleResolvedDeletedDeniedAndVeryLateAlarmsDoNotPost() {
        val reminders = eligible(PlanningData(meals = listOf(meal())))
        val item = reminders.single()
        sync.sync(reminders, now, true)
        assertFalse(sync.deliver(item.key, item.at.minusSeconds(1), reminders, item.at, true))
        assertEquals(item, backend.pending()[item.key])
        assertFalse(sync.deliver(item.key, item.at, emptyList(), item.at, true))
        sync.sync(reminders, now, true)
        assertFalse(sync.deliver(item.key, item.at, reminders, item.at, false))
        sync.sync(reminders, now, true)
        assertFalse(sync.deliver(item.key, item.at, reminders, item.at.plusSeconds(3601), true))
        assertEquals(0, backend.posts)
    }
    @Test fun dstGapIsSkippedOverlapUsesEarlierOffsetAndFastingInstantsStayExact() {
        val spring = MealPlan("spring", LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), "Gap")
        val fall = spring.copy(id = "fall", date = LocalDate.of(2026, 11, 1), time = LocalTime.of(1, 30))
        val result = eligible(PlanningData(meals = listOf(spring, fall), activities = listOf(activity().copy(date = spring.date, time = spring.time))))
        assertEquals(1, result.size)
        assertEquals(Instant.parse("2026-11-01T07:30:00Z"), result.single().at)
        val event = fast().copy(start = Instant.parse("2026-03-08T08:00:00Z"), end = Instant.parse("2026-03-10T08:00:00Z"))
        val changedZone = ReminderPolicy.eligible(PlanningData(fasting = listOf(event)), all, ZoneId.of("Asia/Tokyo"))
        assertEquals(listOf(event.start, event.end), changedZone.map { it.at })
    }
    @Test fun localPlansFollowCurrentZoneAcrossYearBoundaryWithNoInventedTimes() {
        val data = PlanningData(meals = listOf(meal().copy(date = date.plusDays(1))))
        val denver = eligible(data).single()
        val tokyo = ReminderPolicy.eligible(data, all, ZoneId.of("Asia/Tokyo")).single()
        assertEquals("2027-01-01T19:00:00Z", denver.at.toString())
        assertEquals("2027-01-01T03:00:00Z", tokyo.at.toString())
        assertEquals(denver.key, tokyo.key)
    }
    private class Fake : ReminderBackend {
        val items = mutableMapOf<String, PlanReminder>(); val sent = mutableMapOf<String, Instant>()
        var schedules = 0; var posts = 0
        override fun pending() = items.toMap()
        override fun delivered() = sent.toMap()
        override fun schedule(reminder: PlanReminder) { items[reminder.key] = reminder; schedules++ }
        override fun cancel(key: String) { items.remove(key) }
        override fun post(reminder: PlanReminder) { posts++ }
        override fun markDelivered(reminder: PlanReminder) { sent[reminder.key] = reminder.at }
    }
    @Test fun largeFuturePlanSetUsesBoundedRollingNativeQueueWithoutLosingLaterPlans() {
        val reminders = (0..130).map { PlanReminder("MEAL:$it", ReminderType.MEAL, now.plusSeconds(60L * (it + 1)), "Planned meal", "Soup $it") }
        sync.sync(reminders, now, true)
        assertEquals(ReminderReconciler.CAPACITY, backend.pending().size)
        assertFalse("MEAL:130" in backend.pending())
        for (item in reminders) {
            assertTrue(item.key in backend.pending())
            assertTrue(sync.deliver(item.key, item.at, reminders, item.at, true))
            sync.sync(reminders, item.at, true)
        }
        assertEquals(131, backend.posts)
        assertTrue(backend.pending().isEmpty())
    }
}
