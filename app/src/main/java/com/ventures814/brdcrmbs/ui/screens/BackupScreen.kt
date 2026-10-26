package com.ventures814.brdcrmbs.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ventures814.brdcrmbs.backup.*

@Composable
private fun rememberBackupModel(): BackupViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { BackupViewModel(app.backups) } })
}

@Composable
fun BackupScreen(modifier: Modifier = Modifier, back: () -> Unit, model: BackupViewModel = rememberBackupModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), model::created)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::opened)
    LaunchedEffect(state.request) {
        val request = state.request ?: return@LaunchedEffect
        model.requestLaunched()
        try {
            if (request == BackupDocumentRequest.CREATE) create.launch(state.filename)
            else open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: Exception) { model.pickerFailed() }
    }
    BackHandler { if (!state.busy) { if (state.preview != null) model.cancelRestore() else back() } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, enabled = !state.busy, modifier = Modifier.testTag("backupBack")) {
                Icon(painterResource(R.drawable.ic_chevron), "Back to Settings", Modifier.rotate(180f))
            }
            Text("Backup & Restore", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        Text("Keep a portable copy of your plans, history, saved meals, saved activities, reminder choices and app preferences.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Create a backup", style = MaterialTheme.typography.titleMedium)
                Text("Choose where Android saves the file. No brdcrmbs account or internet connection is required.")
                Button(onClick = model::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("createBackup")) { Text("Create Backup") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Restore a backup", style = MaterialTheme.typography.titleMedium)
                Text("Select a backup to validate and review. Restoring replaces current plans, history, saved items, reminder choices and app preferences; it does not merge them.")
                OutlinedButton(onClick = model::open, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("openBackup")) { Text("Restore Backup") }
            }
        }
        Text("Backup files contain personal planning history and are not encrypted. Store and share them only in a location you trust. Notification permission and scheduled alarms are not included.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp)); Text("Working…")
        }
        state.message?.let { Text(it, color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("backupFeedback")) }
    }
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = { if (!state.busy) model.cancelRestore() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Replace current data?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup created ${com.ventures814.brdcrmbs.ui.settings.LocalClockFormat.current.formatter("MMM d, yyyy · ").withZone(java.time.ZoneId.systemDefault()).format(preview.createdAt)}")
                Text("${preview.records} records. This replaces all current brdcrmbs plans, history, saved items, reminder choices and app preferences. Current data will not be merged or kept. Create a backup first if you want to keep it.")
            } },
            confirmButton = { TextButton(onClick = model::restore, enabled = !state.busy, modifier = Modifier.testTag("confirmRestoreBackup")) { Text(if (state.busy) "Restoring…" else "Replace & Restore") } },
            dismissButton = { TextButton(onClick = model::cancelRestore, enabled = !state.busy) { Text("Cancel") } })
    }
}
