package com.ventures814.brdcrmbs.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.data.DataAction

@Composable private fun rememberDataManagementModel(): DataManagementViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { DataManagementViewModel(app.backups) } })
}

@Composable
fun DataManagementScreen(modifier: Modifier = Modifier, back: () -> Unit, resetComplete: (String) -> Unit,
    model: DataManagementViewModel = rememberDataManagementModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    BackHandler { if (!state.busy) { model.cancel(); back() } }
    LaunchedEffect(state.completed) {
        state.completed?.let { action ->
            val message = checkNotNull(state.message)
            if (action == DataAction.RESET_APP) {
                model.feedbackShown()
                resetComplete(message)
            } else {
                snackbars.showSnackbar(message)
                model.feedbackShown()
            }
        }
    }
    Scaffold(modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { model.cancel(); back() }, enabled = !state.busy) {
                    Icon(painterResource(R.drawable.ic_chevron), "Back to Settings", Modifier.rotate(180f))
                }
                Text("Data Management", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            Text("These actions permanently delete local data. Create a backup first if you want to keep it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Clear Planning & History", style = MaterialTheme.typography.titleMedium)
                    Text("Delete all scheduled plans and adherence history. Saved Meals, Saved Activities, preferences, and exported backups are not affected.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { model.request(DataAction.CLEAR_PLANNING) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("clearPlanning")) { Text("Clear Planning & History", color = MaterialTheme.colorScheme.error) }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reset App Data", style = MaterialTheme.typography.titleMedium)
                    Text("Delete all brdcrmbs planning, history and saved libraries, and reset preferences. Exported backups will remain.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { model.request(DataAction.RESET_APP) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("resetAppData")) { Text("Reset App Data", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("dataBusy"))
            if (state.failed) Text(checkNotNull(state.message), color = MaterialTheme.colorScheme.error)
        }
    }
    state.confirmation?.let { action ->
        val reset = action == DataAction.RESET_APP
        AlertDialog(onDismissRequest = model::cancel, containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(if (reset) { if (state.finalReset) "Permanently reset app data?" else "Reset App Data?" } else "Clear Planning & History?") },
            text = { key(action, state.finalReset) { Column(Modifier.verticalScroll(rememberScrollState())) { Text(if (reset) {
                if (state.finalReset) "This cannot be undone in the app. All planning, history, Saved Meals and Saved Activities will be deleted. App Preferences return to System default / No default / 3. All four Reminder choices turn OFF. Exported backups will NOT be deleted."
                else "This deletes all planning and adherence history, Saved Meals and Saved Activities, and resets App Preferences and Reminder choices. Your exported backup files will NOT be deleted. Android permissions and system settings will not change."
            } else "This permanently deletes your fasting, meal, and activity plans and adherence history from this device. Saved Meals, Saved Activities, preferences, and exported backups will remain.") } } },
            confirmButton = { TextButton(onClick = model::confirm, modifier = Modifier.testTag("confirmDataAction")) {
                Text(if (reset) { if (state.finalReset) "Reset App Data" else "Continue" } else "Clear", color = MaterialTheme.colorScheme.error)
            } }, dismissButton = { TextButton(onClick = model::cancel) { Text("Cancel") } })
    }
}
