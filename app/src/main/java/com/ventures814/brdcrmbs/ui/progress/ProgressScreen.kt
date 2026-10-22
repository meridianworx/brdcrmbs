package com.ventures814.brdcrmbs.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
private fun statusColor(status: ReviewStatus): Color = when (status) {
    ReviewStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    ReviewStatus.CHANGED -> Color(0xFFFFD166)
    ReviewStatus.MISSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(state: ProgressState, previous: () -> Unit, next: () -> Unit,
    select: (LocalDate) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("brdcrmbs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Progress", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Summary("Fasting", state.review?.fasting, Modifier.weight(1f))
            Summary("Meals", state.review?.meals, Modifier.weight(1f))
            Summary("Activity", state.review?.activity, Modifier.weight(1f))
        }
        ReviewCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(previous, Modifier.semantics { contentDescription = "Previous month" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                Text(state.context.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.testTag("progressMonth"), style = MaterialTheme.typography.titleMedium)
                TextButton(next, Modifier.semantics { contentDescription = "Next month" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
            }
            if (state.loading) Text("Loading month…")
            else if (state.failed) {
                Text("Could not load this month.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(retry) { Text("Retry") }
            } else state.review?.let { review ->
                // Preserve readable F/M/A columns on narrow or enlarged-text displays.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val calendarWidth = maxOf(maxWidth, 336.dp)
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(calendarWidth)) {
                        Row {
                            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        val cells = List<ReviewDay?>(review.leadingCells) { null } + review.days
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                for (index in 0..6) {
                                    val day = week.getOrNull(index)
                                    if (day == null) Spacer(Modifier.weight(1f))
                                    else CalendarDay(day, day.date == state.context.selected, day.date == state.context.today,
                                        { select(day.date) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReviewStatus.entries.filter { it != ReviewStatus.NONE }.forEach { Status(it, Modifier.testTag("legend-${it.name}")) }
        }
        Text("F = Fasting   M = Meals   A = Activity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("— Nothing planned / intentional No fast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Day Detail", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(state.context.selected.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), modifier = Modifier.testTag("detailDate"))
        if (!state.loading && !state.failed) state.selectedDay?.let { DayDetail(it) }
    }
}

@Composable private fun Summary(label: String, summary: AdherenceSummary?, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(summary?.percent?.let { "$it%" } ?: "—", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("summary-$label"))
            Text("${summary?.done ?: 0}/${summary?.resolved ?: 0} resolved", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun CalendarDay(day: ReviewDay, selected: Boolean, today: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.padding(vertical = 3.dp, horizontal = 1.dp).testTag("progressDate-${day.date}")
        .semantics {
            this.selected = selected
            contentDescription = "${day.date}${if (today) ", Today" else ""}. Fasting ${day.fasting.label}; Meals ${day.meals.label}, ${day.mealsDone}/${day.mealsTotal} Done; Activity ${day.activity.label}"
        }, shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(vertical = 6.dp).heightIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.date.dayOfMonth.toString(), color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("F" to day.fasting, "M" to day.meals, "A" to day.activity).forEach { (label, status) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("indicator-$label-${day.date}")) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                        Text(status.symbol, color = statusColor(status), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // The same event's continuation across local midnights; detail retains its full endpoints.
            if (day.fastContinuesBefore || day.fastContinuesAfter) Text(
                "${if (day.fastContinuesBefore) "←" else "·"} F ${if (day.fastContinuesAfter) "→" else "·"}",
                color = statusColor(day.fasting), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("fastSpan-${day.date}").semantics { contentDescription = "Continuous fast${if (day.fastContinuesBefore) ", continues from previous date" else ""}${if (day.fastContinuesAfter) ", continues into next date" else ""}" })
        }
    }
}

@Composable private fun Status(status: ReviewStatus, modifier: Modifier = Modifier) {
    Text("${status.symbol} ${status.label}", color = statusColor(status), style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable private fun ReviewCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun DayDetail(day: ReviewDay) {
    val plans = day.plans
    val time = DateTimeFormatter.ofPattern("h:mm a")
    ReviewCard {
        Text("Fasting", style = MaterialTheme.typography.titleMedium)
        if (plans.fasting.isEmpty() && plans.noFast.isEmpty()) Text("Nothing planned")
        plans.fasting.forEach { fast ->
            val format = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
            Text(fast.preset ?: "Fast", modifier = Modifier.testTag("detail-fast-${fast.id}"))
            Text("${fast.start.atZone(fast.zone).format(format)} →\n${fast.end.atZone(fast.zone).format(format)} · ${fast.zone.id}")
            Status(when (fast.outcome) { FastingOutcome.DONE -> ReviewStatus.SUCCESS; FastingOutcome.MISSED -> ReviewStatus.MISSED; else -> ReviewStatus.PLANNED })
            fast.notes?.let { Text(it) }; HorizontalDivider()
        }
        plans.noFast.forEach { Text("— No fast · Intentional plan"); it.notes?.let { note -> Text(note) } }
    }
    ReviewCard {
        Text("Meals · ${day.mealsDone}/${day.mealsTotal} Done", style = MaterialTheme.typography.titleMedium)
        if (plans.meals.isEmpty()) Text("Nothing planned")
        plans.meals.forEach { meal ->
            Text("Planned: ${meal.text}", modifier = Modifier.testTag("detail-planned-${meal.id}"))
            listOfNotNull(meal.label, meal.time?.format(time), meal.notes).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
            Status(when (meal.outcome) { MealOutcome.DONE -> ReviewStatus.SUCCESS; MealOutcome.CHANGED -> ReviewStatus.CHANGED; MealOutcome.MISSED -> ReviewStatus.MISSED; else -> ReviewStatus.PLANNED })
            if (meal.outcome == MealOutcome.CHANGED) meal.actual?.let { actual ->
                Text("Actual: ${actual.text}", modifier = Modifier.testTag("detail-actual-${meal.id}"))
                listOfNotNull(actual.label, actual.time?.format(time), actual.notes).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
            }
            HorizontalDivider()
        }
    }
    ReviewCard {
        Text("Workout / Activity", style = MaterialTheme.typography.titleMedium)
        if (plans.activities.isEmpty()) Text("Nothing planned")
        plans.activities.forEach { activity ->
            Text(activity.name, modifier = Modifier.testTag("detail-activity-${activity.id}"))
            Text(listOfNotNull(if (activity.type == ActivityType.WORKOUT) "Workout" else "Activity", activity.time?.format(time), activity.notes).joinToString(" · "))
            Status(when (activity.outcome) { ActivityOutcome.DONE -> ReviewStatus.SUCCESS; ActivityOutcome.MISSED -> ReviewStatus.MISSED; else -> ReviewStatus.PLANNED })
            HorizontalDivider()
        }
    }
}
