package com.ventures814.brdcrmbs.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.domain.ActivityType
import com.ventures814.brdcrmbs.ui.settings.*

@Composable
private fun rememberSettingsModel(): SettingsViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.planningRepository, createSavedStateHandle()) } })
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, model: SettingsViewModel = rememberSettingsModel(), onEditorVisible: (Boolean) -> Unit = {}, onResetComplete: (String) -> Unit = {}) {
    val page by model.page.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val library by model.library.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val child = page != SettingsPage.HOME || draft != null
    LaunchedEffect(child) { onEditorVisible(child) }
    DisposableEffect(Unit) { onDispose { onEditorVisible(false) } }
    BackHandler(enabled = child) { model.back() }
    when {
        page == SettingsPage.DATA_MANAGEMENT -> DataManagementScreen(modifier, model::back, { message -> model.back(); onResetComplete(message) })
        page == SettingsPage.PREFERENCES -> AppPreferencesScreen(modifier, model::back)
        page == SettingsPage.BACKUP -> BackupScreen(modifier, model::back)
        page == SettingsPage.REMINDERS -> RemindersScreen(modifier, model::back)
        draft != null -> SavedItemEditor(draft!!, busy, error, model::change, model::save, model::delete, model::back, modifier)
        page != SettingsPage.HOME -> SavedLibrary(page, library, model, modifier)
        else -> SettingsHome(modifier, model::open)
    }
}

@Composable
private fun SettingsHome(modifier: Modifier, open: (SettingsPage) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        SettingsGroup(R.string.settings_planning) {
            SettingsRow(R.string.settings_saved_meals, R.string.settings_meals_help, R.drawable.ic_meal) { open(SettingsPage.MEALS) }
            HorizontalDivider()
            SettingsRow(R.string.settings_saved_activities, R.string.settings_activities_help, R.drawable.ic_activity) { open(SettingsPage.ACTIVITIES) }
        }
        SettingsGroup(R.string.settings_reminders_group) {
            SettingsRow(R.string.settings_reminders, R.string.settings_reminders_help, R.drawable.ic_fast) { open(SettingsPage.REMINDERS) }
        }
        SettingsGroup(R.string.settings_data) {
            SettingsRow(R.string.settings_backup, R.string.settings_backup_help, R.drawable.ic_week) { open(SettingsPage.BACKUP) }
            HorizontalDivider()
            SettingsRow(R.string.settings_management, R.string.settings_management_help, R.drawable.ic_settings) { open(SettingsPage.DATA_MANAGEMENT) }
        }
        SettingsGroup(R.string.settings_app) {
            SettingsRow(R.string.settings_preferences, R.string.settings_preferences_help, R.drawable.ic_settings) { open(SettingsPage.PREFERENCES) }
            HorizontalDivider()
            SettingsRow(R.string.settings_about, R.string.settings_about_help, R.drawable.ic_today)
        }
        Text(stringResource(R.string.product_philosophy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Developer tooling remains in src/debug, intentionally detached from normal Settings.
    }
}

@Composable
private fun SettingsGroup(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(content = content) }
    }
}

@Composable
private fun SettingsRow(@StringRes title: Int, @StringRes subtitle: Int, @DrawableRes icon: Int, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Column {
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall)
            if (onClick == null) Text(stringResource(R.string.settings_later), style = MaterialTheme.typography.labelSmall)
        } },
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { if (onClick != null) Icon(painterResource(R.drawable.ic_chevron), null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun ChildHeader(title: String, back: () -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.settings_back), Modifier.rotate(180f))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun SavedLibrary(page: SettingsPage, library: SavedLibraryState, model: SettingsViewModel, modifier: Modifier) {
    val meals = page == SettingsPage.MEALS
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ChildHeader(stringResource(if (meals) R.string.settings_saved_meals else R.string.settings_saved_activities), model::back) }
        item { Button(onClick = model::add, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (meals) R.string.settings_add_meal else R.string.settings_add_activity)) } }
        when {
            library.loading -> item { Text(stringResource(R.string.settings_loading)) }
            library.failed -> item { Column {
                Text(stringResource(R.string.settings_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::retry) { Text(stringResource(R.string.settings_retry)) }
            } }
            meals -> {
                if (library.meals.isEmpty()) item { EmptyLibrary(true) }
                items(library.meals, key = { it.id }) { item ->
                    SavedRow(item.text, listOfNotNull(item.label, item.notes), "savedMeal-${item.id}") { model.edit(item) }
                }
            }
            else -> {
                if (library.activities.isEmpty()) item { EmptyLibrary(false) }
                items(library.activities, key = { it.id }) { item ->
                    SavedRow(item.name, listOf(stringResource(if (item.type == ActivityType.WORKOUT) R.string.settings_workout else R.string.settings_activity)) + listOfNotNull(item.notes), "savedActivity-${item.id}") { model.edit(item) }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(meals: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (meals) R.string.settings_no_meals else R.string.settings_no_activities))
        Text(stringResource(if (meals) R.string.settings_empty_meals_help else R.string.settings_empty_activities_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedRow(title: String, details: List<String>, tag: String, edit: () -> Unit) {
    Card(onClick = edit, modifier = Modifier.fillMaxWidth().testTag(tag), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                details.filter(String::isNotEmpty).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(painterResource(R.drawable.ic_chevron), null)
        }
    }
}

@Composable
private fun SavedItemEditor(draft: SavedItemDraft, busy: Boolean, error: String?, change: ((SavedItemDraft) -> SavedItemDraft) -> Unit,
    save: () -> Unit, delete: () -> Unit, back: () -> Unit, modifier: Modifier) {
    val meals = draft.page == SettingsPage.MEALS
    var confirm by rememberSaveable(draft.id) { mutableStateOf(false) }
    Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChildHeader(stringResource(if (meals) { if (draft.isNew) R.string.settings_add_meal else R.string.settings_edit_meal }
            else { if (draft.isNew) R.string.settings_add_activity else R.string.settings_edit_activity }), back, !busy)
        Text(stringResource(R.string.settings_template_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(draft.name, { value -> change { it.copy(name = value) } }, enabled = !busy,
                    label = { Text(stringResource(R.string.settings_name)) }, isError = draft.name.isBlank(), modifier = Modifier.fillMaxWidth().testTag("savedName"))
                if (meals) OutlinedTextField(draft.label, { value -> change { it.copy(label = value) } }, enabled = !busy,
                    label = { Text(stringResource(R.string.settings_label)) }, modifier = Modifier.fillMaxWidth().testTag("savedLabel"))
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityType.entries.forEach { type ->
                        FilterChip(selected = draft.type == type, enabled = !busy, onClick = { change { it.copy(type = type) } },
                            label = { Text(stringResource(if (type == ActivityType.WORKOUT) R.string.settings_workout else R.string.settings_activity)) })
                    }
                }
                OutlinedTextField(draft.notes, { value -> change { it.copy(notes = value) } }, enabled = !busy,
                    label = { Text(stringResource(R.string.settings_notes)) }, modifier = Modifier.fillMaxWidth().testTag("savedNotes"))
            }
        }
        if (draft.name.isBlank()) Text(stringResource(R.string.settings_required), color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("savedError")) }
        Button(onClick = save, enabled = !busy && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (busy) R.string.settings_saving else R.string.settings_save))
        }
        if (!draft.isNew) OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_delete)) }
    }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) confirm = false },
        title = { Text(stringResource(if (meals) R.string.settings_delete_meal_title else R.string.settings_delete_activity_title)) },
        text = { Text(stringResource(if (meals) R.string.settings_delete_meal_help else R.string.settings_delete_activity_help)) },
        confirmButton = { TextButton(onClick = { confirm = false; delete() }, enabled = !busy, modifier = Modifier.testTag("confirmDeleteSavedItem")) { Text(stringResource(R.string.settings_delete)) } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !busy) { Text(stringResource(R.string.settings_cancel)) } })
}
