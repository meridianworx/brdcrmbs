package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.preferences.*
import com.ventures814.brdcrmbs.ui.fasting.*
import com.ventures814.brdcrmbs.ui.today.*
import com.ventures814.brdcrmbs.ui.week.presentWeek
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.time.*
import java.util.Locale
import org.junit.Test
import org.junit.Assert.*

class AppPreferencesTest {
    @Test fun defaultsChoicesAndValidation() {
        assertEquals(AppPreferences(TimeFormat.SYSTEM, null, 3), AppPreferences())
        assertEquals(FastPreset.entries.filter { it.hours != null || it == FastPreset.OMAD }, defaultFastPresets)
        (1..6).forEach { assertEquals(it, AppPreferences(mealCount = it).mealCount) }
        listOf(0, 7).forEach { assertTrue(runCatching { AppPreferences(mealCount = it) }.isFailure) }
        assertTrue(runCatching { AppPreferences(fastingPreset = FastPreset.CUSTOM) }.isFailure)
        assertTrue(runCatching { AppPreferences(fastingPreset = FastPreset.NO_FAST) }.isFailure)
    }
    @Test fun clockPresentationAndSystemResolutionDoNotChangeValues() {
        val value = LocalTime.of(19, 0, 12, 123)
        assertEquals("7:00 PM", value.format(ClockFormat().formatter(locale = Locale.US)))
        assertEquals("19:00", value.format(ClockFormat(true).formatter(locale = Locale.US)))
        assertEquals("08:30", LocalTime.of(8, 30).format(ClockFormat(true).formatter()))
        assertFalse(TimeFormat.SYSTEM.is24Hour(false)); assertTrue(TimeFormat.SYSTEM.is24Hour(true))
        assertFalse(TimeFormat.TWELVE.is24Hour(true)); assertTrue(TimeFormat.TWENTY_FOUR.is24Hour(false))
        assertEquals(12, value.second); assertEquals(123, value.nano)
    }
    @Test fun weekSchedulesEndLabelsAndNotificationsKeepInstantsAcrossDst() {
        val zone = ZoneId.of("America/Denver")
        val date = LocalDate.of(2026, 10, 31)
        val draft = FastingDraft.new(date, zone).selectPreset(FastPreset.H48)
        val event = draft.toPlan() as FastingEvent
        val data = PlanningData(fasting = listOf(event), meals = listOf(MealPlan(date = date, time = LocalTime.of(8, 30), text = "Meal")),
            activities = listOf(ActivityPlan(date = date, time = LocalTime.of(19, 0), name = "Walk")))
        val range = DateRange.weekContaining(date)
        val index = (date.toEpochDay() - range.start.toEpochDay()).toInt()
        val twelve = presentWeek(range, index, data, zone, Locale.US, ClockFormat())
        val twentyFour = presentWeek(range, index, data, zone, Locale.US, ClockFormat(true))
        assertTrue(twelve.fasting.single().title.contains("7:00 PM"))
        assertTrue(twentyFour.fasting.single().title.contains("19:00"))
        assertEquals("08:30", twentyFour.meals.single().time); assertEquals("19:00", twentyFour.activities.single().time)
        assertTrue(event.endTimeLabel(event.start, ClockFormat(true)).endsWith("18:00"))
        assertEquals(Duration.ofHours(48), Duration.between(event.start, event.end))
        val prefs = ReminderPreferences(setOf(ReminderType.FAST_START, ReminderType.FAST_END))
        val a = ReminderPolicy.eligible(data, prefs, zone)
        val b = ReminderPolicy.eligible(data, prefs, zone, ClockFormat(true))
        assertEquals(a.map { it.at }, b.map { it.at }); assertTrue(b.first().body.contains("18:00"))
        assertEquals(event, data.fasting.single())
    }
}
