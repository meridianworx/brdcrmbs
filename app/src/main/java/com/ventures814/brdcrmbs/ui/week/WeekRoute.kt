package com.ventures814.brdcrmbs.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.ui.screens.WeekScreen

@Composable
fun WeekRoute(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as BrdcrmbsApplication
    val model: WeekViewModel = viewModel(factory = viewModelFactory {
        initializer { WeekViewModel(application.planningRepository, createSavedStateHandle()) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    WeekScreen(
        state = state,
        onDaySelected = model::selectDay,
        modifier = modifier,
        onPreviousWeek = { model.moveWeek(-1) },
        onNextWeek = { model.moveWeek(1) }
    )
}
