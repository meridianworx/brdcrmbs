package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.backup.BackupSnapshot
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import java.time.*

internal fun richBackupSnapshot(): BackupSnapshot {
    val zone = ZoneId.of("America/Denver")
    val day = LocalDate.of(2026, 11, 1)
    val start = day.minusDays(1).atTime(19, 0).atZone(zone).toInstant().plusMillis(123)
    val fast = FastingEvent("history-long", start, start.plus(Duration.ofHours(72)), zone, "72h", "Continuous · historical", FastingOutcome.DONE)
    val original = ActivityPlan("z-original", day.minusDays(1), LocalTime.NOON, "Original missed", ActivityType.WORKOUT, "Keep original", ActivityOutcome.MISSED)
    val child = ActivityPlan("a-rescheduled", day, LocalTime.of(12, 34, 56, 123456789), "Later plan", rescheduledFromId = original.id)
    return BackupSnapshot(Instant.parse("2026-10-25T14:00:00.123Z"), PlanningData(
        fasting = listOf(fast, fast.copy(id = "historical-overlap", outcome = FastingOutcome.MISSED)),
        noFast = listOf(NoFastPlan("intentional", day, zone, "Legacy intentional day")),
        meals = MealOutcome.entries.map { outcome -> MealPlan("meal-${outcome.name}", day, LocalTime.of(18, 30, 12, 987654321), "Original planned $outcome", "Dinner", "Plan notes", outcome,
            if (outcome in setOf(MealOutcome.CHANGED, MealOutcome.DONE)) ActualMeal("Actual fish ☺", LocalTime.of(19, 1, 2, 123456789), "Actual label", "Actual notes\nKeep history") else null) },
        activities = listOf(child, original, ActivityPlan("done-activity", day, name = "Completed walk", outcome = ActivityOutcome.DONE))
    ), listOf(SavedMeal("saved-meal", "Reusable soup", "Lunch", "Independent")),
        listOf(SavedActivity("saved-activity", "Reusable workout", ActivityType.WORKOUT, "Independent")),
        ReminderPreferences(setOf(ReminderType.FAST_END, ReminderType.MEAL)))
}
