package com.ventures814.brdcrmbs.ui.today

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.ventures814.brdcrmbs.domain.MealPlan
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActualMealEditor(draft: ActualMealDraft?, original: MealPlan?, busy: Boolean, error: String?,
    onChange: ((ActualMealDraft) -> ActualMealDraft) -> Unit, onSave: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var picker by rememberSaveable(draft?.id) { mutableStateOf(false) }
    Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to Today") }
        Text("Change Meal / Log Actual", style = MaterialTheme.typography.headlineMedium)
        original?.let { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original planned meal", color = MaterialTheme.colorScheme.primary)
                    Text(plan.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("originalMeal"))
                    Text(listOfNotNull(plan.label, plan.time?.format(DateTimeFormatter.ofPattern("h:mm a")), plan.date.toString(), plan.notes).joinToString(" · "))
                }
            }
        }
        if (draft != null && original != null) {
            Text("What did you actually eat?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(draft.text, { value -> onChange { it.copy(text = value) } }, label = { Text("Actual meal (required)") },
                isError = draft.text.isBlank(), enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("actualText"))
            OutlinedTextField(draft.label, { value -> onChange { it.copy(label = value) } }, label = { Text("Type / label (optional)") },
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("actualLabel"))
            OutlinedButton(onClick = { picker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("actualTime")) {
                Text(draft.time?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Add time (optional)")
            }
            if (draft.time != null) TextButton(onClick = { onChange { it.copy(time = null) } }, enabled = !busy) { Text("Remove time") }
            OutlinedTextField(draft.notes, { value -> onChange { it.copy(notes = value) } }, label = { Text("Notes (optional)") },
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("actualNotes"))
            Button(onClick = onSave, enabled = !busy && draft.text.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("saveActual")) {
                Text(if (busy) "Saving…" else "Save")
            }
        } else Text("Loading original meal…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("actualError")) }
    }
    if (picker && draft != null) {
        val time = rememberTimePickerState(draft.time?.hour ?: 12, draft.time?.minute ?: 0, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(onDismissRequest = { picker = false }, title = { Text("Actual meal time") }, text = { TimeInput(time) },
            confirmButton = { TextButton(onClick = { onChange { it.copy(time = LocalTime.of(time.hour, time.minute)) }; picker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } })
    }
}
