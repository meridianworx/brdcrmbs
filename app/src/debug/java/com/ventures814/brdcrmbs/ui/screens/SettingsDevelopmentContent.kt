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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                when (action) { "Seed" -> seeder.seed(week); "Seed Active Fast" -> seeder.seedActiveFast(); else -> seeder.reset() }
                messages.send(when (action) { "Seed" -> "Test week seeded."; "Seed Active Fast" -> "Active test fast seeded."; else -> "Test data removed." })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: com.ventures814.brdcrmbs.domain.FastingConflictException) {
                error = "No changes saved. ${e.message} Remove the conflicting plan or reset its owned test scenario, then retry."
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
    ReminderDiagnostics(app)
}

@Composable
private fun ReminderDiagnostics(app: BrdcrmbsApplication) {
    val pending by app.reminders.pending.collectAsStateWithLifecycle()
    val error by app.reminders.error.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reminder diagnostics", style = MaterialTheme.typography.titleMedium)
            Text("Pending reminders: ${pending.size}")
            OutlinedButton(onClick = app.reminders::refresh) { Text("Reconcile reminders now") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            pending.take(20).forEach { Text("${it.type} · ${it.at.atZone(java.time.ZoneId.systemDefault())}", style = MaterialTheme.typography.bodySmall) }
            if (pending.size > 20) Text("Showing the next 20 reminders.")
        }
    }
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
            Text("Current local Monday–Sunday QA scenario. Genuine plans are preserved. Conflicting fasting plans prevent seeding.")
            Text("Test week: ${model.week.monday} – ${model.week.endInclusive}")
            Button(onClick = { model.request("Seed") }, enabled = !model.busy) { Text("Seed Test Week") }
            OutlinedButton(onClick = { model.request("Seed Active Fast") }, enabled = !model.busy) { Text("Seed Active Fast") }
            OutlinedButton(onClick = { model.request("Reset") }, enabled = !model.busy) { Text("Reset Test Data") }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SnackbarHost(snackbar)
        }
    }
    model.pending?.let { action ->
        AlertDialog(
            onDismissRequest = model::cancel,
            title = { Text(when (action) { "Seed" -> "Seed Progress test data?"; "Seed Active Fast" -> "Seed active test fast?"; else -> "Remove seeded test data?" }) },
            text = { Text(if (action == "Seed Active Fast") "Create a 16-hour Planned fast from 10 hours ago until 6 hours from now. Replace only the reserved active-fast QA record. Other plans are preserved; fasting conflicts reject the operation."
                else if (action == "Seed")
                "Create the standard QA scenario for ${model.week.monday} – ${model.week.endInclusive}. Replace only this seeder's records for that week."
                else "Only records created by this debug seeder will be removed, across all seeded weeks. Genuine plans and saved items are preserved.") },
            confirmButton = { TextButton(onClick = model::confirm, enabled = !model.busy) { Text(if (model.busy) "Working…" else action) } },
            dismissButton = { TextButton(onClick = model::cancel, enabled = !model.busy) { Text("Cancel") } })
    }
}
