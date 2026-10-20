package com.ventures814.brdcrmbs.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.ui.screens.WeekScreen
import com.ventures814.brdcrmbs.ui.fasting.FastingEditorScreen

@Composable
fun rememberWeekModel(): WeekViewModel {
    val application = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory {
        initializer { WeekViewModel(application.planningRepository, createSavedStateHandle()) }
    })
}

@Composable
fun WeekRoute(
    modifier: Modifier = Modifier,
    model: WeekViewModel = rememberWeekModel(),
    onEditorVisible: (Boolean) -> Unit = {}
) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft by model.editor.collectAsStateWithLifecycle()
    val loading by model.editorLoading.collectAsStateWithLifecycle()
    val busy by model.editorBusy.collectAsStateWithLifecycle()
    val error by model.editorError.collectAsStateWithLifecycle()
    val editing = draft != null || loading
    LaunchedEffect(editing) { onEditorVisible(editing) }
    DisposableEffect(Unit) { onDispose { onEditorVisible(false) } }
    BackHandler(editing) { model.closeEditor() }
    if (draft != null) {
        FastingEditorScreen(draft!!, busy, error, model::updateDraft, model::saveFast, model::deleteFast, model::closeEditor, modifier)
        return
    }
    if (loading) {
        Column(modifier) {
            TextButton(onClick = model::closeEditor) { Text("Back to Week") }
            Text("Loading plan…")
        }
        return
    }
    if (error != null) {
        Column(modifier) {
            Text(error!!)
            TextButton(onClick = model::closeEditor) { Text("Back to Week") }
        }
        return
    }
    WeekScreen(
        state = state,
        onDaySelected = model::selectDay,
        modifier = modifier,
        onPreviousWeek = { model.moveWeek(-1) },
        onNextWeek = { model.moveWeek(1) },
        onAddFast = model::addFast,
        onEditFast = model::editFast
    )
}
