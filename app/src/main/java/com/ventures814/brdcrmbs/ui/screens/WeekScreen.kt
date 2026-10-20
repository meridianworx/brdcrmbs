package com.ventures814.brdcrmbs.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme

/** Presentation-only sample. Dates and rows are not connected to a planning model. */
@Composable
fun WeekScreen(modifier: Modifier = Modifier) {
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.my_week),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                stringResource(R.string.week_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SampleWeekSelector()
        WeekDaySelector(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
        PlanningSectionCard(stringResource(R.string.week_fasting), R.drawable.ic_fast) {
            PlanningRow(stringResource(R.string.sample_fast_time), stringResource(R.string.sample_fast_detail))
        }
        PlanningSectionCard(stringResource(R.string.week_meals), R.drawable.ic_meal) {
            PlanningRow(stringResource(R.string.sample_eggs), stringResource(R.string.sample_first_meal), stringResource(R.string.sample_meal_time_1))
            PlanningDivider()
            PlanningRow(stringResource(R.string.sample_chicken), stringResource(R.string.sample_lunch), stringResource(R.string.sample_meal_time_2))
            PlanningDivider()
            PlanningRow(stringResource(R.string.sample_salmon), stringResource(R.string.sample_dinner), stringResource(R.string.sample_meal_time_3))
        }
        PlanningSectionCard(stringResource(R.string.week_activity), R.drawable.ic_activity) {
            PlanningRow(stringResource(R.string.sample_workout), stringResource(R.string.sample_workout_type), stringResource(R.string.sample_workout_time))
            PlanningDivider()
            PlanningRow(stringResource(R.string.sample_walk), stringResource(R.string.sample_activity_type), stringResource(R.string.sample_walk_time))
        }
    }
}

@Composable
private fun SampleWeekSelector() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Intentionally inert for the static UI milestone.
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.previous_week), Modifier.rotate(180f))
            }
            Text(
                stringResource(R.string.sample_week_range),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = {}) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.next_week))
            }
        }
    }
}

@Composable
private fun WeekDaySelector(selectedDay: Int, onDaySelected: (Int) -> Unit) {
    val days = stringArrayResource(R.array.sample_week_days)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fit seven days when possible; allow horizontal scrolling on narrow/large-text layouts.
        val dayWidth = ((maxWidth - 24.dp) / 7).coerceAtLeast(48.dp * fontScale)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            days.forEachIndexed { index, day ->
                val selected = index == selectedDay
                val date = (19 + index).toString()
                val dayDescription = stringResource(R.string.sample_day_description, day, date)
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.width(dayWidth).heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(selected = selected, role = Role.Tab, onClick = { onDaySelected(index) })
                            .semantics { contentDescription = dayDescription }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Text(day, style = MaterialTheme.typography.labelMedium)
                        Text(date, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningSectionCard(title: String, @DrawableRes icon: Int, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {}) { Text(stringResource(R.string.week_add)) }
        }
        content()
    }
}

@Composable
private fun PlanningRow(title: String, subtitle: String, time: String? = null) {
    // Edit affordance only: no navigation or data changes are implemented yet.
    Surface(onClick = {}, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (time != null) Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanningDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun WeekScreenPreview() {
    BrdcrmbsTheme { Surface { WeekScreen() } }
}
