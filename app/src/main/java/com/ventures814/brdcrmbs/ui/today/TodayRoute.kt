package com.ventures814.brdcrmbs.ui.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.ui.screens.TodayScreen

@Composable
fun rememberTodayModel(): TodayViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { TodayViewModel(app.planningRepository, createSavedStateHandle()) } })
}

@Composable
fun TodayRoute(modifier: Modifier = Modifier, model: TodayViewModel = rememberTodayModel(), onEditorVisible: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val original by model.original.collectAsStateWithLifecycle()
    val loading by model.editorLoading.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val editing = draft != null || original != null || loading
    val context = LocalContext.current
    LaunchedEffect(model) { model.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    DisposableEffect(context, model) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { model.refresh() } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(editing) { onEditorVisible(editing) }
    DisposableEffect(Unit) { onDispose { onEditorVisible(false) } }
    BackHandler(editing) { model.closeActual() }
    if (editing) ActualMealEditor(draft, original, busy, error, model::changeActual, model::saveActual, model::closeActual, modifier)
    else {
        val now by model.fastingTime.collectAsStateWithLifecycle()
        TodayScreen(state, busy, error, model::fasting, model::meal, model::activity, model::openActual, model::refresh, now, modifier)
    }
}
