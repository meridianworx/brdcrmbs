package com.ventures814.brdcrmbs.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.reminders.*

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
fun RemindersScreen(modifier: Modifier, back: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BrdcrmbsApplication
    val model: RemindersViewModel = viewModel(factory = viewModelFactory { initializer { RemindersViewModel(app.reminderSettings) } })
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val syncError by app.reminders.error.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(app.reminderBackend.allowed()) }
    var pending by rememberSaveable { mutableStateOf<ReminderType?>(null) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = app.reminderBackend.allowed()
        if (granted && allowed) pending?.let { model.set(it, true) }
        pending = null
        app.reminders.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = app.reminderBackend.allowed(); app.reminders.refresh() }
    fun systemSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        context.startActivity(intent)
    }
    fun request() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && (!model.asked || context.activity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true)) explain = true else systemSettings()
    }
    RemindersContent(modifier, preferences, allowed, busy, error ?: syncError, back,
        toggle = { type, on ->
            if (!on || allowed) model.set(type, on)
            else { pending = type; if (!model.asked) request() else systemSettings() }
        }, permission = { pending = null; request() }, retry = { if (error != null) model.retry() else app.reminders.refresh() })
    if (explain) AlertDialog(onDismissRequest = { explain = false; pending = null },
        title = { Text("Allow plan reminders?") },
        text = { Text("Notifications let brdcrmbs remind you about plans you create. Categories stay off if permission is not granted.") },
        confirmButton = { TextButton(onClick = {
            explain = false
            model.markAsked { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explain = false; pending = null }) { Text("Not now") } })
}

@Composable
fun RemindersContent(modifier: Modifier = Modifier, preferences: ReminderPreferences, allowed: Boolean, busy: Boolean,
    error: String?, back: () -> Unit, toggle: (ReminderType, Boolean) -> Unit, permission: () -> Unit, retry: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { TextButton(onClick = back) { Text("Back") }; Text("Reminders", style = MaterialTheme.typography.headlineMedium) }
        Text("You make the plan. The app helps you follow it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((heading, types) in listOf("FASTING" to listOf(ReminderType.FAST_START, ReminderType.FAST_END), "PLANNING" to listOf(ReminderType.MEAL, ReminderType.ACTIVITY))) {
            Text(heading, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                types.forEachIndexed { index, type ->
                    if (index > 0) HorizontalDivider()
                    val title = when(type) { ReminderType.FAST_START -> "Fasting start"; ReminderType.FAST_END -> "Fasting end"; ReminderType.MEAL -> "Meals"; ReminderType.ACTIVITY -> "Workout / Activity" }
                    val help = when(type) { ReminderType.FAST_START -> "Notify me when a planned fast begins"; ReminderType.FAST_END -> "Notify me when a planned fast ends"; ReminderType.MEAL -> "Notify me about planned meals with a time"; ReminderType.ACTIVITY -> "Notify me about timed workouts and activities" }
                    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(help) },
                        trailingContent = { Switch(checked = type in preferences.enabled, enabled = !busy,
                            onCheckedChange = { toggle(type, it) }, modifier = Modifier.testTag("reminder-${type.name}").semantics { contentDescription = title }) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
        Text("NOTIFICATIONS", color = MaterialTheme.colorScheme.primary)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notification permission", style = MaterialTheme.typography.titleMedium)
                Text(if (allowed) "Allowed" else "Blocked — reminders cannot be delivered. Existing category choices are kept.")
                if (!allowed) OutlinedButton(onClick = permission) { Text("Enable notifications") }
                Text("Reminders are requested at plan time. Android may delay delivery to save battery. Reminders more than an hour late are skipped.", style = MaterialTheme.typography.bodySmall)
                Text("Meals and activities use your current time zone. Nonexistent clock-change times are skipped; repeated times use the earlier occurrence.", style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Retry") } }
    }
}
