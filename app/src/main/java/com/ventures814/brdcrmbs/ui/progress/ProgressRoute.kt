package com.ventures814.brdcrmbs.ui.progress

import android.content.*
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

@Composable
fun rememberProgressModel(): ProgressViewModel {
    val app = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory { initializer { ProgressViewModel(app.planningRepository, createSavedStateHandle()) } })
}

@Composable
fun ProgressRoute(modifier: Modifier = Modifier, model: ProgressViewModel = rememberProgressModel(),
    resetToTodayRequested: Boolean = false, onResetHandled: () -> Unit = {}) {
    LaunchedEffect(resetToTodayRequested) { if (resetToTodayRequested) { model.resetToToday(); onResetHandled() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    val context = LocalContext.current
    DisposableEffect(context, model) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { model.refresh() } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    ProgressScreen(state, { model.moveMonth(-1) }, { model.moveMonth(1) }, model::selectDate, model::refresh, modifier)
}
