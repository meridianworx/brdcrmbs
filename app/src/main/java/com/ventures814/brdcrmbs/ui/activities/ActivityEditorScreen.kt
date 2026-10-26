package com.ventures814.brdcrmbs.ui.activities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.domain.SavedActivity
import com.ventures814.brdcrmbs.domain.ActivityType
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityEditorScreen(
    draft: ActivityDraft, savedActivities: List<SavedActivity>, busy: Boolean, error: String?,
    onChange: ((ActivityDraft) -> ActivityDraft) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var picker by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(draft.id) { mutableStateOf(false) }
    var chooseSaved by rememberSaveable(draft.id) { mutableStateOf(false) }
    val enabled = !busy && draft.editable
    Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.activity_back), Modifier.rotate(180f))
            }
            Text(stringResource(if (draft.isNew) R.string.activity_add_title else R.string.activity_edit_title), style = MaterialTheme.typography.headlineMedium)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.activity_source), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.source == ActivitySource.BAKAL_GYM, enabled = enabled,
                        onClick = { onChange { it.bakalGym() } }, label = { Text(stringResource(R.string.activity_bakal)) })
                    FilterChip(selected = draft.source == ActivitySource.SAVED_ACTIVITY, enabled = enabled,
                        onClick = { chooseSaved = true }, label = { Text(stringResource(R.string.activity_saved_source)) })
                    FilterChip(selected = draft.source == ActivitySource.NEW_CUSTOM, enabled = enabled,
                        onClick = { onChange { it.copy(source = ActivitySource.NEW_CUSTOM) } }, label = { Text(stringResource(R.string.activity_custom_source)) })
                }
                if (draft.source == ActivitySource.BAKAL_GYM) Text(stringResource(R.string.activity_bakal_help),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(draft.name, { value -> onChange { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.activity_name)) }, enabled = enabled,
                    isError = draft.name.isBlank(), modifier = Modifier.fillMaxWidth().testTag("activityText"))
                OutlinedButton(onClick = { picker = "date" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("activityDate")) {
                    Text(stringResource(R.string.activity_date_value, draft.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))))
                }
                OutlinedButton(onClick = { picker = "time" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("activityTime")) {
                    Text(draft.time?.let { stringResource(R.string.activity_time_value, it.format(com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter())) } ?: stringResource(R.string.activity_no_time))
                }
                if (draft.time != null) TextButton(onClick = { onChange { it.copy(time = null) } }, enabled = enabled) { Text(stringResource(R.string.activity_clear_time)) }
                Text(stringResource(R.string.activity_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityType.entries.forEach { type ->
                        FilterChip(selected = draft.type == type, enabled = enabled,
                            onClick = { onChange { it.copy(type = type) } },
                            label = { Text(stringResource(if (type == ActivityType.WORKOUT) R.string.activity_workout else R.string.activity_activity)) })
                    }
                }
                OutlinedTextField(draft.notes, { value -> onChange { it.copy(notes = value) } }, label = { Text(stringResource(R.string.activity_notes)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("activityNotes"))
                if (draft.editable) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.saveForReuse, { value -> onChange { it.copy(saveForReuse = value) } }, enabled = enabled, modifier = Modifier.testTag("activitySaveForReuse"))
                    Text(stringResource(R.string.activity_save_reuse))
                }
            }
        }
        if (!draft.editable) Text(stringResource(R.string.activity_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.name.isBlank() && draft.editable) Text(stringResource(R.string.activity_required), color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("activityError")) }
        if (draft.editable) {
            Button(onClick = onSave, enabled = enabled && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.activity_saving else R.string.activity_save)) }
            if (!draft.isNew) OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_delete)) }
        }
    }
    if (chooseSaved) AlertDialog(onDismissRequest = { chooseSaved = false }, title = { Text(stringResource(R.string.activity_saved_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (savedActivities.isEmpty()) Text(stringResource(R.string.activity_saved_empty))
                savedActivities.forEach { saved ->
                    OutlinedButton(onClick = { onChange { it.select(saved) }; chooseSaved = false }, modifier = Modifier.fillMaxWidth().testTag("savedActivity-${saved.id}")) {
                        Column { Text(saved.name); Text(stringResource(if (saved.type == ActivityType.WORKOUT) R.string.activity_workout else R.string.activity_activity), style = MaterialTheme.typography.bodySmall); saved.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { chooseSaved = false }) { Text(stringResource(R.string.activity_cancel)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.activity_delete_title)) },
        text = { Text(stringResource(R.string.activity_delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy, modifier = Modifier.testTag("confirmDeleteActivity")) { Text(stringResource(R.string.activity_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.activity_cancel)) } })
    if (picker == "date") {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> onChange { it.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) } }; picker = null
            }) { Text(stringResource(R.string.activity_ok)) }
        }, dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.activity_cancel)) } }) { DatePicker(state) }
    }
    if (picker == "time") {
        val state = rememberTimePickerState(draft.time?.hour ?: 12, draft.time?.minute ?: 0, com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.is24Hour)
        AlertDialog(onDismissRequest = { picker = null }, title = { Text(stringResource(R.string.activity_time_title)) }, text = { TimeInput(state) },
            confirmButton = { TextButton(onClick = { onChange { it.copy(time = LocalTime.of(state.hour, state.minute)) }; picker = null }) { Text(stringResource(R.string.activity_ok)) } },
            dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.activity_cancel)) } })
    }
}
