package com.ventures814.brdcrmbs.ui.week

import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R

enum class Shortcut { COPY_DAY, COPY_DAYS, REPEAT_WEEK }
data class ShortcutDraft(
    val mode: Shortcut,
    val source: LocalDate,
    val weekStart: LocalDate,
    val destinationWeek: LocalDate = weekStart,
    val destinations: Set<LocalDate> = emptySet()
) : Serializable {
    fun toggle(date: LocalDate): ShortcutDraft {
        if (date == source) return this
        return copy(destinations = if (date in destinations) destinations - date
            else if (mode == Shortcut.COPY_DAY) setOf(date) else destinations + date)
    }
}

@Composable
fun PlanningActions(enabled: Boolean, onOpen: (Shortcut) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text(stringResource(R.string.planning_actions)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Shortcut.entries.forEach { shortcut ->
                DropdownMenuItem(text = { Text(stringResource(shortcut.label())) }, onClick = { expanded = false; onOpen(shortcut) })
            }
        }
    }
}

private fun Shortcut.label(): Int = when (this) {
    Shortcut.COPY_DAY -> R.string.copy_day
    Shortcut.COPY_DAYS -> R.string.copy_days
    Shortcut.REPEAT_WEEK -> R.string.repeat_week
}

@Composable
fun PlanningShortcutDialog(
    draft: ShortcutDraft, busy: Boolean, error: String?,
    onChange: ((ShortcutDraft) -> ShortcutDraft) -> Unit, onCopy: () -> Unit, onCancel: () -> Unit
) {
    val format = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val repeat = draft.mode == Shortcut.REPEAT_WEEK
    AlertDialog(onDismissRequest = onCancel,
        title = { Text(stringResource(draft.mode.label())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (repeat) {
                    Text(stringResource(R.string.repeat_message,
                        draft.weekStart.minusWeeks(1).format(format), draft.weekStart.minusDays(1).format(format),
                        draft.weekStart.format(format), draft.weekStart.plusDays(6).format(format)))
                } else {
                    Text(stringResource(R.string.copy_source, draft.source.format(format)), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onChange { it.copy(destinationWeek = it.destinationWeek.minusWeeks(1)) } }, enabled = !busy,
                            modifier = Modifier.testTag("copyPreviousWeek")) { Text("‹") }
                        Text(draft.destinationWeek.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), Modifier.weight(1f))
                        TextButton(onClick = { onChange { it.copy(destinationWeek = it.destinationWeek.plusWeeks(1)) } }, enabled = !busy,
                            modifier = Modifier.testTag("copyNextWeek")) { Text("›") }
                    }
                    (0..6).forEach { offset ->
                        val date = draft.destinationWeek.plusDays(offset.toLong())
                        val selected = date in draft.destinations
                        Surface(color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().toggleable(selected, enabled = !busy && date != draft.source,
                                role = if (draft.mode == Shortcut.COPY_DAY) Role.RadioButton else Role.Checkbox,
                                onValueChange = { onChange { it.toggle(date) } }).padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("copyDestination-$date"), verticalAlignment = Alignment.CenterVertically) {
                                Text(date.format(format), Modifier.weight(1f))
                                if (selected) Text("✓")
                            }
                        }
                    }
                    Text(stringResource(R.string.copy_selected_count, draft.destinations.size))
                }
                Text(stringResource(R.string.copy_rules), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("copyError")) }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy, enabled = !busy && (repeat || draft.destinations.isNotEmpty()), modifier = Modifier.testTag("confirmPlanningCopy")) {
                Text(stringResource(if (busy) R.string.copy_busy else if (repeat) R.string.repeat_week else R.string.copy_action))
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.copy_cancel)) } }
    )
}
