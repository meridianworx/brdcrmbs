package com.ventures814.brdcrmbs.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.preferences.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppPreferencesViewModel(private val store: AppPreferencesStore,
    private val boundary: suspend (suspend () -> Unit) -> Unit = { it() }) : ViewModel() {
    val state = store.state
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    fun change(transform: (AppPreferences) -> AppPreferences) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { boundary { store.replace(transform(store.state.value)) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Could not save this preference. Please try again." }
            finally { busy.value = false }
        }
    }
}

@Composable private fun rememberAppPreferencesModel(): AppPreferencesViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { AppPreferencesViewModel(app.appPreferences, app.reminders::withRestoreBoundary) } })
}

@Composable
fun AppPreferencesScreen(modifier: Modifier = Modifier, back: () -> Unit, model: AppPreferencesViewModel = rememberAppPreferencesModel()) {
    val value by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron), "Back to Settings", Modifier.rotate(180f)) }
            Text("App Preferences", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            listOf("Time format" to value.timeFormat.label, "Default fasting preset" to (value.fastingPreset?.label ?: "No default"),
                "Default meal count" to value.mealCount.toString()).forEachIndexed { index, (title, detail) ->
                if (index > 0) HorizontalDivider()
                ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron), null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.clickable(enabled = !busy) { selection = title }.testTag("preference-$index"))
            }
        }
        Text("Defaults never create or change plans. Meal count is saved; meals are currently added individually in Week.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    selection?.let { title ->
        val choices = when (title) {
            "Time format" -> TimeFormat.entries.map { it.label }
            "Default fasting preset" -> listOf("No default") + defaultFastPresets.map { it.label }
            else -> (1..6).map(Int::toString)
        }
        val current = when (title) { "Time format" -> value.timeFormat.label; "Default fasting preset" -> value.fastingPreset?.label ?: "No default"; else -> value.mealCount.toString() }
        AlertDialog(onDismissRequest = { selection = null }, containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(title) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !busy) {
                        model.change { old -> when (title) {
                            "Time format" -> old.copy(timeFormat = TimeFormat.entries[index])
                            "Default fasting preset" -> old.copy(fastingPreset = if (index == 0) null else defaultFastPresets[index - 1])
                            else -> old.copy(mealCount = index + 1)
                        } }; selection = null
                    }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == label, onClick = null); Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            } }, confirmButton = { TextButton(onClick = { selection = null }) { Text("Cancel") } })
    }
}
