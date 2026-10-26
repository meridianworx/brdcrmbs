package com.ventures814.brdcrmbs.ui.meals

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
import com.ventures814.brdcrmbs.domain.SavedMeal
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(
    draft: MealDraft, savedMeals: List<SavedMeal>, busy: Boolean, error: String?,
    onChange: ((MealDraft) -> MealDraft) -> Unit,
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
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.meal_back), Modifier.rotate(180f))
            }
            Text(stringResource(if (draft.isNew) R.string.meal_add_title else R.string.meal_edit_title), style = MaterialTheme.typography.headlineMedium)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (savedMeals.isNotEmpty() && draft.editable) OutlinedButton(onClick = { chooseSaved = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.meal_choose_saved))
                }
                OutlinedTextField(draft.text, { value -> onChange { it.copy(text = value) } },
                    label = { Text(stringResource(R.string.meal_name)) }, enabled = enabled,
                    isError = draft.text.isBlank(), modifier = Modifier.fillMaxWidth().testTag("mealText"))
                OutlinedButton(onClick = { picker = "date" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("mealDate")) {
                    Text(stringResource(R.string.meal_date_value, draft.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))))
                }
                OutlinedButton(onClick = { picker = "time" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("mealTime")) {
                    Text(draft.time?.let { stringResource(R.string.meal_time_value, it.format(com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter())) } ?: stringResource(R.string.meal_no_time))
                }
                if (draft.time != null) TextButton(onClick = { onChange { it.copy(time = null) } }, enabled = enabled) { Text(stringResource(R.string.meal_clear_time)) }
                OutlinedTextField(draft.label, { value -> onChange { it.copy(label = value) } }, label = { Text(stringResource(R.string.meal_label)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("mealLabel"))
                OutlinedTextField(draft.notes, { value -> onChange { it.copy(notes = value) } }, label = { Text(stringResource(R.string.meal_notes)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("mealNotes"))
                if (draft.editable) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.saveForReuse, { value -> onChange { it.copy(saveForReuse = value) } }, enabled = enabled, modifier = Modifier.testTag("mealSaveForReuse"))
                    Text(stringResource(R.string.meal_save_reuse))
                }
            }
        }
        if (!draft.editable) Text(stringResource(R.string.meal_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.text.isBlank() && draft.editable) Text(stringResource(R.string.meal_required), color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("mealError")) }
        if (draft.editable) {
            Button(onClick = onSave, enabled = enabled && draft.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.meal_saving else R.string.meal_save)) }
            if (!draft.isNew) OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.meal_delete)) }
        }
    }
    if (chooseSaved) AlertDialog(onDismissRequest = { chooseSaved = false }, title = { Text(stringResource(R.string.meal_saved_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedMeals.forEach { saved ->
                    OutlinedButton(onClick = { onChange { it.select(saved) }; chooseSaved = false }, modifier = Modifier.fillMaxWidth().testTag("savedMeal-${saved.id}")) {
                        Column { Text(saved.text); saved.label?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { chooseSaved = false }) { Text(stringResource(R.string.meal_cancel)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.meal_delete_title)) },
        text = { Text(stringResource(R.string.meal_delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy, modifier = Modifier.testTag("confirmDeleteMeal")) { Text(stringResource(R.string.meal_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.meal_cancel)) } })
    if (picker == "date") {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> onChange { it.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) } }; picker = null
            }) { Text(stringResource(R.string.meal_ok)) }
        }, dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.meal_cancel)) } }) { DatePicker(state) }
    }
    if (picker == "time") {
        val state = rememberTimePickerState(draft.time?.hour ?: 12, draft.time?.minute ?: 0, com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.is24Hour)
        AlertDialog(onDismissRequest = { picker = null }, title = { Text(stringResource(R.string.meal_time_title)) }, text = { TimeInput(state) },
            confirmButton = { TextButton(onClick = { onChange { it.copy(time = LocalTime.of(state.hour, state.minute)) }; picker = null }) { Text(stringResource(R.string.meal_ok)) } },
            dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.meal_cancel)) } })
    }
}
