package com.ventures814.brdcrmbs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.today.TodayState
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun LocalTime.display() = format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
fun TodayScreen(state: TodayState, busy: Boolean, error: String?,
    onFasting: (String, FastingOutcome) -> Unit, onMeal: (String, MealOutcome) -> Unit,
    onActivity: (String, ActivityOutcome) -> Unit, onActual: (String) -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("brdcrmbs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Today", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() }.testTag("todayHeading"))
        Text(state.day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("todayError")) }
        if (state.loading) { Text("Loading today's plan…"); return@Column }
        if (state.failed) {
            Text("Could not load today's plan.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text("Retry") }; return@Column
        }
        TodaySection("Fasting", R.drawable.ic_fast, state.plans.fasting.isEmpty() && state.plans.noFast.isEmpty()) {
            state.plans.fasting.forEach { fast -> key(fast.id) {
                val format = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
                val minutes = Duration.between(fast.start, fast.end).toMinutes()
                Text(fast.preset ?: "Fast", style = MaterialTheme.typography.titleMedium)
                Text("${fast.start.atZone(fast.zone).format(format)} →\n${fast.end.atZone(fast.zone).format(format)}")
                Secondary("${minutes / 60}h ${minutes % 60}m · ${fast.zone.id}")
                fast.notes?.let { Secondary(it) }
                OutcomeControls("fast-${fast.id}", fast.outcome.name, false, busy,
                    { onFasting(fast.id, FastingOutcome.DONE) }, { onFasting(fast.id, FastingOutcome.MISSED) })
                HorizontalDivider()
            } }
            state.plans.noFast.forEach { plan ->
                Text("— No fast", style = MaterialTheme.typography.titleMedium)
                Secondary("Intentionally planned as No fast")
                plan.notes?.let { Secondary(it) }
            }
        }
        TodaySection("Meals", R.drawable.ic_meal, state.plans.meals.isEmpty()) {
            state.plans.meals.forEach { meal -> key(meal.id) {
                Text(meal.text, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(meal.label, meal.time?.display()).joinToString(" · ")
                if (details.isNotEmpty()) Secondary(details)
                meal.notes?.let { Secondary(it) }
                if (meal.outcome == MealOutcome.CHANGED) meal.actual?.let { actual ->
                    Text("Actual: ${actual.text}", modifier = Modifier.testTag("actual-${meal.id}"))
                    val actualDetails = listOfNotNull(actual.label, actual.time?.display(), actual.notes).joinToString(" · ")
                    if (actualDetails.isNotEmpty()) Secondary(actualDetails)
                }
                OutcomeControls("meal-${meal.id}", meal.outcome.name, true, busy,
                    { onMeal(meal.id, MealOutcome.DONE) }, { onMeal(meal.id, MealOutcome.MISSED) }, { onActual(meal.id) })
                HorizontalDivider()
            } }
        }
        TodaySection("Workout / Activity", R.drawable.ic_activity, state.plans.activities.isEmpty()) {
            state.plans.activities.forEach { activity -> key(activity.id) {
                Text(activity.name, style = MaterialTheme.typography.titleMedium)
                Secondary(listOfNotNull(if (activity.type == ActivityType.WORKOUT) "Workout" else "Activity", activity.time?.display()).joinToString(" · "))
                activity.notes?.let { Secondary(it) }
                OutcomeControls("activity-${activity.id}", activity.outcome.name, false, busy,
                    { onActivity(activity.id, ActivityOutcome.DONE) }, { onActivity(activity.id, ActivityOutcome.MISSED) })
                HorizontalDivider()
            } }
        }
    }
}

@Composable private fun Secondary(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable private fun TodaySection(title: String, icon: Int, empty: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (empty) Secondary("Nothing planned") else content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun OutcomeControls(tag: String, outcome: String, meal: Boolean, busy: Boolean,
    done: () -> Unit, missed: () -> Unit, changed: () -> Unit = {}) {
    val label = when (outcome) { "DONE" -> "✓ Done"; "MISSED" -> "✕ Missed"; "CHANGED" -> "◐ Changed"; else -> "○ Planned" }
    val color = when (outcome) { "DONE" -> MaterialTheme.colorScheme.primary; "MISSED" -> MaterialTheme.colorScheme.error; "CHANGED" -> Color(0xFFFFD166); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(label, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag("status-$tag"))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = outcome == "DONE", onClick = done, enabled = !busy, label = { Text("Done") }, modifier = Modifier.testTag("done-$tag"))
        if (meal) FilterChip(selected = outcome == "CHANGED", onClick = changed, enabled = !busy,
            label = { Text(if (outcome == "CHANGED") "Edit actual" else "Changed") }, modifier = Modifier.testTag("changed-$tag"))
        FilterChip(selected = outcome == "MISSED", onClick = missed, enabled = !busy, label = { Text("Missed") }, modifier = Modifier.testTag("missed-$tag"))
    }
}
