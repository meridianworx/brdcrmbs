package com.ventures814.brdcrmbs.ui.fasting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FastingEditorScreen(
    draft: FastingDraft,
    busy: Boolean,
    error: String?,
    onChange: ((FastingDraft) -> FastingDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var picker by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(draft.id) { mutableStateOf(false) }
    val duration = if (draft.preset == FastPreset.NO_FAST) null else runCatching { draft.duration() }
    val validation = duration?.exceptionOrNull()?.message
    val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val timeFormat = com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.fast_back), Modifier.rotate(180f))
            }
            Text(stringResource(if (draft.isNew) R.string.fast_add_title else R.string.fast_edit_title), style = MaterialTheme.typography.headlineMedium)
        }
        EditorCard {
            Text(stringResource(R.string.fast_preset), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FastPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = draft.preset == preset, enabled = !busy && draft.editable,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = { onChange { it.selectPreset(preset) } }, label = { Text(preset.label) }
                    )
                }
            }
        }
        EditorCard {
            Text(stringResource(R.string.fast_schedule), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.fast_zone, draft.zone.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ScheduleControl(
                stringResource(if (draft.preset == FastPreset.NO_FAST) R.string.fast_day else R.string.fast_start_date),
                draft.start.format(dateFormat), "fastStartDate", !busy && draft.editable
            ) { picker = "startDate" }
            if (draft.preset != FastPreset.NO_FAST) {
                ScheduleControl(stringResource(R.string.fast_start_time), draft.start.format(timeFormat), "fastStartTime", !busy && draft.editable) { picker = "startTime" }
                ScheduleControl(stringResource(R.string.fast_end_date), draft.end.format(dateFormat), "fastEndDate", !busy && draft.editable) { picker = "endDate" }
                ScheduleControl(stringResource(R.string.fast_end_time), draft.end.format(timeFormat), "fastEndTime", !busy && draft.editable) { picker = "endTime" }
                duration?.getOrNull()?.let {
                    val minutes = it.toMinutes()
                    Text(stringResource(R.string.fast_duration, minutes / 60, minutes % 60), color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.fast_offsets, draft.startInstant().atZone(draft.zone).offset.id, draft.endInstant().atZone(draft.zone).offset.id),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!draft.editable) Text(stringResource(R.string.fast_recorded_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        (error ?: validation)?.let { Text(it, modifier = Modifier.testTag("fastError"), color = MaterialTheme.colorScheme.error) }
        if (draft.editable) {
            Button(onClick = onSave, enabled = !busy && validation == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.fast_saving else R.string.fast_save))
            }
            if (!draft.isNew) OutlinedButton(
                onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.fast_delete)) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.fast_delete_title)) },
        text = { Text(stringResource(R.string.fast_delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy, modifier = Modifier.testTag("confirmDeleteFast")) { Text(stringResource(R.string.fast_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.fast_cancel)) } }
    )
    picker?.let { field ->
        val start = field.startsWith("start")
        val value = if (start) draft.start else draft.end
        if (field.endsWith("Date")) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = 1..9999
            )
            DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange { if (start) it.changeStart(LocalDateTime.of(date, it.start.toLocalTime())) else it.changeEnd(LocalDateTime.of(date, it.end.toLocalTime())) }
                    }
                    picker = null
                }) { Text(stringResource(R.string.fast_ok)) }
            }, dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.fast_cancel)) } }) {
                DatePicker(state)
            }
        } else {
            val state = rememberTimePickerState(value.hour, value.minute, com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.is24Hour)
            AlertDialog(onDismissRequest = { picker = null }, title = { Text(stringResource(if (start) R.string.fast_start_time else R.string.fast_end_time)) },
                text = { TimeInput(state) },
                confirmButton = { TextButton(onClick = {
                    onChange {
                        val time = LocalTime.of(state.hour, state.minute)
                        if (start) it.changeStart(LocalDateTime.of(it.start.toLocalDate(), time)) else it.changeEnd(LocalDateTime.of(it.end.toLocalDate(), time))
                    }
                    picker = null
                }) { Text(stringResource(R.string.fast_ok)) } },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.fast_cancel)) } }
            )
        }
    }
}

@Composable
private fun EditorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ScheduleControl(label: String, value: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(value) }
    }
}
