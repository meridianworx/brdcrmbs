package com.ventures814.brdcrmbs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.debugqa.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class QaToolsModel(private val seeder: ProgressQaSeeder) : ViewModel() {
    var pending by mutableStateOf<String?>(null); private set
    var week by mutableStateOf(seeder.currentWeek()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()
    fun refreshWeek() { if (!busy && pending == null) week = seeder.currentWeek() }
    fun request(action: String) { if (!busy) { week = seeder.currentWeek(); error = null; pending = action } }
    fun cancel() { if (!busy) pending = null }
    fun confirm() {
        val action = pending ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (action == "Seed") seeder.seed(week) else seeder.reset()
                messages.send(if (action == "Seed") "Test week seeded." else "Test data removed.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                error = "No changes saved. Check the current week and any linked activity history, then try again."
            } finally { busy = false; pending = null; week = seeder.currentWeek() }
        }
    }
}

@Composable
internal fun SettingsDevelopmentContent() {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    val model: QaToolsModel = viewModel(factory = viewModelFactory {
        initializer { QaToolsModel(ProgressQaSeeder(app.planningDatabase)) }
    })
    ProgressQaTools(model)
}

@Composable
internal fun ProgressQaTools(model: QaToolsModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshWeek() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.feedback.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) } }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEVELOPER / TESTING", color = MaterialTheme.colorScheme.primary)
            Text("Progress Test Data", style = MaterialTheme.typography.titleMedium)
            Text("Current local Monday–Sunday QA scenario. Genuine plans are preserved and may coexist.")
            Text("Test week: ${model.week.monday} – ${model.week.endInclusive}")
            Button(onClick = { model.request("Seed") }, enabled = !model.busy) { Text("Seed Test Week") }
            OutlinedButton(onClick = { model.request("Reset") }, enabled = !model.busy) { Text("Reset Test Data") }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SnackbarHost(snackbar)
        }
    }
    model.pending?.let { action ->
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text(if (action == "Seed") "Seed Progress test data?" else "Remove seeded test data?") },
            text = { Text(if (action == "Seed")
                "Create the standard QA scenario for ${model.week.monday} – ${model.week.endInclusive}. Replace only this seeder's records for that week."
                else "Only records created by this debug seeder will be removed, across all seeded weeks. Genuine plans and saved items are preserved.") },
            confirmButton = { TextButton(onClick = model::confirm, enabled = !model.busy) { Text(if (model.busy) "Working…" else action) } },
            dismissButton = { TextButton(onClick = model::cancel, enabled = !model.busy) { Text("Cancel") } })
    }
}
