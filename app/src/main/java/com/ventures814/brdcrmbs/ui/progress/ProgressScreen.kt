package com.ventures814.brdcrmbs.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.domain.*
import java.time.LocalDate
import java.time.Duration
import java.time.format.DateTimeFormatter

// Progress-only colors follow approved Screen 3; the shared theme remains untouched.
private val ReviewGreen = Color(0xFF43E5A5)
private val ReviewAmber = Color(0xFFFFC94D)
private val ReviewRed = Color(0xFFFF455D)
private val ReviewCardColor = Color(0xFF142025)
private val ReviewOutline = Color(0xFF344146)
private fun statusColor(status: ReviewStatus) = when (status) {
    ReviewStatus.SUCCESS -> ReviewGreen
    ReviewStatus.CHANGED -> ReviewAmber
    ReviewStatus.MISSED -> ReviewRed
    ReviewStatus.PLANNED -> Color(0xFFC0CAD0)
    ReviewStatus.NONE -> Color(0xFF526066)
}

@Composable
fun ProgressScreen(state: ProgressState, previous: () -> Unit, next: () -> Unit,
    select: (LocalDate) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("brdcrmbs", style = MaterialTheme.typography.titleMedium, color = ReviewGreen)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow("Previous month", "‹", previous)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PROGRESS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() })
                Text(state.context.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.testTag("progressMonth"), style = MaterialTheme.typography.titleLarge)
            }
            MonthArrow("Next month", "›", next)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Summary("Fasting", state.review?.fasting, Modifier.weight(1f))
            Summary("Meals", state.review?.meals, Modifier.weight(1f))
            Summary("Activity", state.review?.activity, Modifier.weight(1f))
        }
        if (state.loading) Text("Loading month…", style = MaterialTheme.typography.bodySmall)
        else if (state.failed) {
            Text("Could not load this month.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(retry) { Text("Retry") }
        } else state.review?.let { review ->
            // Screen 3 uses Sunday-first presentation; the shared month read model is unchanged.
            val leadingCells = (review.leadingCells + 1) % 7
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val calendarWidth = maxOf(maxWidth, 308.dp)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(calendarWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        val count = ((leadingCells + review.days.size + 6) / 7) * 7
                        (0 until count).toList().chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                week.forEach { index ->
                                    val day = review.days.getOrNull(index - leadingCells)
                                    if (day == null) {
                                        val date = review.month.atDay(1).plusDays((index - leadingCells).toLong())
                                        Box(Modifier.weight(1f).heightIn(min = 48.dp).background(ReviewCardColor.copy(alpha = .35f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.TopCenter) {
                                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), modifier = Modifier.padding(top = 5.dp))
                                        }
                                    } else CalendarDay(day, day.date == state.context.selected, day.date == state.context.today, { select(day.date) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().testTag("progressLegend"), horizontalArrangement = Arrangement.SpaceBetween) {
            ReviewStatus.entries.filter { it != ReviewStatus.NONE }.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.testTag("legend-${status.name}")) {
                    StatusMark(status, Modifier.size(10.dp)); Text(status.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Fasting · Meals · Activity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.loading && !state.failed) state.selectedDay?.let { DayDetail(it) }
    }
}

@Composable private fun MonthArrow(description: String, glyph: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description }) {
        Surface(shape = CircleShape, color = ReviewCardColor, border = BorderStroke(.5.dp, ReviewOutline)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Summary(label: String, summary: AdherenceSummary?, modifier: Modifier) {
    Surface(modifier.testTag("summaryCard-$label"), shape = RoundedCornerShape(10.dp), color = ReviewCardColor, border = BorderStroke(.5.dp, ReviewOutline)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(progress = { if (summary == null || summary.resolved == 0) 0f else summary.done.toFloat() / summary.resolved },
                    modifier = Modifier.size(32.dp).testTag("ring-$label").semantics { contentDescription = "$label ${summary?.percent?.let { "$it percent" } ?: "no resolved records"}" },
                    color = ReviewGreen, trackColor = ReviewOutline, strokeWidth = 5.dp, gapSize = 0.dp)
                Column {
                    Text(summary?.percent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("summary-$label"))
                    Text("${summary?.done ?: 0} / ${summary?.resolved ?: 0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Shape as well as color distinguishes outcomes at compact calendar size. */
@Composable private fun StatusMark(status: ReviewStatus, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val color = statusColor(status)
        when (status) {
            ReviewStatus.SUCCESS -> { drawCircle(color); drawLine(ReviewCardColor, Offset(radius * .5f, radius), Offset(radius * .9f, radius * 1.35f), 1.dp.toPx()); drawLine(ReviewCardColor, Offset(radius * .9f, radius * 1.35f), Offset(radius * 1.5f, radius * .65f), 1.dp.toPx()) }
            ReviewStatus.CHANGED -> { drawCircle(color, style = Stroke(1.dp.toPx())); drawArc(color, 90f, 180f, useCenter = true) }
            ReviewStatus.MISSED -> { drawCircle(color); drawLine(ReviewCardColor, Offset(radius * .6f, radius * .6f), Offset(radius * 1.4f, radius * 1.4f), 1.dp.toPx()); drawLine(ReviewCardColor, Offset(radius * 1.4f, radius * .6f), Offset(radius * .6f, radius * 1.4f), 1.dp.toPx()) }
            ReviewStatus.PLANNED -> drawCircle(color, style = Stroke(1.dp.toPx()))
            ReviewStatus.NONE -> drawCircle(color.copy(alpha = .5f))
        }
    }
}

@Composable private fun CalendarDay(day: ReviewDay, selected: Boolean, today: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.testTag("progressDate-${day.date}").semantics {
        this.selected = selected
        contentDescription = "${day.date}${if (today) ", Today" else ""}. Fasting ${day.fasting.label}; Meals ${day.meals.label}, ${day.mealsDone}/${day.mealsTotal} Done; Activity ${day.activity.label}"
    }, shape = RoundedCornerShape(5.dp), color = if (selected) ReviewGreen.copy(alpha = .12f) else ReviewCardColor,
        border = BorderStroke(if (selected) 1.dp else .5.dp, if (selected) ReviewGreen else ReviewOutline)) {
        Column(Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.date.dayOfMonth.toString(), color = if (today && !selected) ReviewGreen else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("F" to day.fasting, "M" to day.meals, "A" to day.activity).forEach { (category, status) ->
                    StatusMark(status, Modifier.size(7.dp).testTag("indicator-$category-${day.date}").semantics { contentDescription = "$category ${status.label}" })
                }
            }
            if (day.fastContinuesBefore || day.fastContinuesAfter) Canvas(Modifier.fillMaxWidth().height(2.dp).testTag("fastSpan-${day.date}").semantics {
                contentDescription = "Continuous fast${if (day.fastContinuesBefore) ", from previous date" else ""}${if (day.fastContinuesAfter) ", into next date" else ""}"
            }) {
                drawLine(statusColor(day.fasting).copy(alpha = .7f), Offset(if (day.fastContinuesBefore) 0f else size.width / 2, 1f), Offset(if (day.fastContinuesAfter) size.width else size.width / 2, 1f), 2.dp.toPx())
            }
        }
    }
}

@Composable private fun DayDetail(day: ReviewDay) {
    var expanded by rememberSaveable(day.date) { mutableStateOf(false) }
    val plans = day.plans
    val time = com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter()
    Surface(Modifier.fillMaxWidth().testTag("dayDetailCard"), shape = RoundedCornerShape(12.dp), color = ReviewCardColor, border = BorderStroke(.5.dp, ReviewOutline)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAY DETAIL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() })
                    Text(day.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("detailDate"))
                }
                Text(day.adherence.percent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("dailyAdherence"))
            }
            HorizontalDivider(color = ReviewOutline)
            val fast = plans.fasting.singleOrNull()
            val fastSchedule = fast?.let {
                val format = com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter("MMM d, ")
                val start = it.start.atZone(it.zone)
                val end = it.end.atZone(it.zone)
                if (start.toLocalDate() == day.date && Duration.between(it.start, it.end) < Duration.ofHours(24))
                    "${start.format(time)} → ${end.format(time)}${if (end.toLocalDate() != start.toLocalDate()) " (+1 day)" else ""}"
                else "${start.format(format)} → ${end.format(format)}"
            }
            DetailRow(R.drawable.ic_fast, if (fast != null) "Fasting · ${fast.preset ?: "Fast"}" else "Fasting",
                fastSchedule ?: if (plans.fasting.isNotEmpty()) "${plans.fasting.size} fasting plans" else if (plans.noFast.isNotEmpty()) "No fast · Intentional plan" else "Nothing planned", day.fasting)
            HorizontalDivider(color = ReviewOutline)
            val changed = plans.meals.filter { it.outcome == MealOutcome.CHANGED }
            DetailRow(R.drawable.ic_meal, "Meals · ${day.mealsDone} / ${day.mealsTotal} followed",
                if (changed.isNotEmpty()) changed.joinToString("; ") { "Planned: ${it.text} → Actual: ${it.actual!!.text}" }
                else plans.meals.joinToString(" · ") { it.text }.ifEmpty { "Nothing planned" }, day.meals)
            HorizontalDivider(color = ReviewOutline)
            val activity = plans.activities.singleOrNull()
            DetailRow(R.drawable.ic_activity, activity?.name ?: "Workout / Activity",
                activity?.let { "${if (it.type == ActivityType.WORKOUT) "Workout" else "Activity"} · ${it.outcome.name.lowercase().replaceFirstChar(Char::titlecase)}${it.time?.let { value -> " · ${value.format(time)}" }.orEmpty()}" }
                    ?: if (plans.activities.isEmpty()) "Nothing planned" else "${plans.activities.size} workouts / activities", day.activity)
            if (plans.fasting.isNotEmpty() || plans.noFast.isNotEmpty() || plans.meals.isNotEmpty() || plans.activities.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 32.dp).testTag("reviewDetails")) {
                    Text(if (expanded) "Hide details ▴" else "Review all details ▾", style = MaterialTheme.typography.labelMedium, color = ReviewGreen)
                }
            }
            if (expanded) FullDetails(plans)
        }
    }
}

@Composable private fun DetailRow(icon: Int, title: String, subtitle: String, status: ReviewStatus) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = ReviewGreen)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (status == ReviewStatus.NONE) "" else status.symbol, color = statusColor(status), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = status.label })
    }
}

@Composable private fun FullDetails(plans: PlanningData) {
    val time = com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter()
    val schedule = com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter("MMM d, yyyy · ")
    HorizontalDivider(color = ReviewOutline)
    plans.fasting.forEach { fast ->
        Text(fast.preset ?: "Fast", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail-fast-${fast.id}"))
        DetailText("${fast.start.atZone(fast.zone).format(schedule)} → ${fast.end.atZone(fast.zone).format(schedule)} · ${fast.zone.id}")
        DetailText(fast.outcome.name.lowercase().replaceFirstChar(Char::titlecase)); fast.notes?.let { DetailText(it) }
    }
    plans.noFast.forEach { DetailText("No fast · Intentional plan"); it.notes?.let { note -> DetailText(note) } }
    plans.meals.forEach { meal ->
        Text("Planned: ${meal.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail-planned-${meal.id}"))
        listOfNotNull(meal.label, meal.time?.format(time), meal.notes).takeIf { it.isNotEmpty() }?.let { DetailText(it.joinToString(" · ")) }
        DetailText(meal.outcome.name.lowercase().replaceFirstChar(Char::titlecase))
        if (meal.outcome == MealOutcome.CHANGED) meal.actual?.let { actual ->
            Text("Actual: ${actual.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail-actual-${meal.id}"))
            listOfNotNull(actual.label, actual.time?.format(time), actual.notes).takeIf { it.isNotEmpty() }?.let { DetailText(it.joinToString(" · ")) }
        }
    }
    plans.activities.forEach { activity ->
        Text(activity.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail-activity-${activity.id}"))
        DetailText(listOfNotNull(if (activity.type == ActivityType.WORKOUT) "Workout" else "Activity", activity.time?.format(time), activity.notes, activity.outcome.name.lowercase().replaceFirstChar(Char::titlecase)).joinToString(" · "))
    }
}

@Composable private fun DetailText(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
