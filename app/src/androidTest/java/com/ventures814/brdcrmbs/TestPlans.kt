package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.screens.WeekScreen
import com.ventures814.brdcrmbs.ui.week.presentWeek
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.*
import java.util.Locale

val testDate: LocalDate = LocalDate.of(2026, 10, 19)
val testZone: ZoneId = ZoneId.of("America/Denver")
val testRange = DateRange.weekContaining(testDate)
fun samplePlans() = PlanningData(
    fasting = listOf(FastingEvent(id = "fast", start = testDate.atTime(19, 0).atZone(testZone).toInstant(),
        end = testDate.plusDays(1).atTime(11, 0).atZone(testZone).toInstant(), zone = testZone, preset = "16:8")),
    meals = listOf(
        MealPlan(id = "eggs", date = testDate, time = LocalTime.of(11, 0), text = "Eggs + avocado", label = "First meal"),
        MealPlan(id = "chicken", date = testDate, time = LocalTime.of(14, 30), text = "Chicken + vegetables", label = "Lunch"),
        MealPlan(id = "salmon", date = testDate, time = LocalTime.of(18, 0), text = "Salmon + salad", label = "Dinner")
    ),
    activities = listOf(
        ActivityPlan(id = "gym", date = testDate, time = LocalTime.of(17, 0), name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT),
        ActivityPlan(id = "walk", date = testDate, time = LocalTime.of(20, 0), name = "20-minute walk")
    )
)

@Composable
fun SampleWeekScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    WeekScreen(presentWeek(testRange, selected, samplePlans(), testZone, Locale.US), { selected = it })
}
