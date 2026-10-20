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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import com.ventures814.brdcrmbs.ui.week.WeekUiState
import com.ventures814.brdcrmbs.ui.week.WeekRow
import com.ventures814.brdcrmbs.ui.week.presentWeek
import com.ventures814.brdcrmbs.domain.DateRange
import com.ventures814.brdcrmbs.domain.PlanningData
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WeekScreen(
    state: WeekUiState,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {}
) {
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
        WeekSelector(state.weekLabel, onPreviousWeek, onNextWeek)
        WeekDaySelector(state, onDaySelected)
        if (state.loading || state.failed) {
            Text(
                stringResource(if (state.failed) R.string.week_load_failed else R.string.week_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PlanningSectionCard(stringResource(R.string.week_fasting), R.drawable.ic_fast) {
            PlanningRows(state.fasting)
        }
        PlanningSectionCard(stringResource(R.string.week_meals, state.meals.size), R.drawable.ic_meal) {
            PlanningRows(state.meals)
        }
        PlanningSectionCard(stringResource(R.string.week_activity, state.activities.size), R.drawable.ic_activity) {
            PlanningRows(state.activities)
        }
    }
}

@Composable
private fun WeekSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.previous_week), Modifier.rotate(180f))
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = onNext) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.next_week))
            }
        }
    }
}

@Composable
private fun WeekDaySelector(state: WeekUiState, onDaySelected: (Int) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fit seven days when possible; allow horizontal scrolling on narrow/large-text layouts.
        val dayWidth = ((maxWidth - 24.dp) / 7).coerceAtLeast(48.dp * fontScale)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.days.forEachIndexed { index, day ->
                val selected = index == state.selectedDay
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.width(dayWidth).heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(selected = selected, role = Role.Tab, onClick = { onDaySelected(index) })
                            .semantics { contentDescription = day.description }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Text(day.label, style = MaterialTheme.typography.labelMedium)
                        Text(day.number, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningRows(rows: List<WeekRow>) {
    if (rows.isEmpty()) {
        Text(
            stringResource(R.string.week_empty), modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else rows.forEachIndexed { index, row ->
        if (index > 0) PlanningDivider()
        PlanningRow(row.title, row.subtitle, row.time)
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
    val state = presentWeek(DateRange.weekContaining(LocalDate.of(2026, 10, 19)), 0, PlanningData(), ZoneId.of("America/Denver"))
    BrdcrmbsTheme { Surface { WeekScreen(state, {}) } }
}
