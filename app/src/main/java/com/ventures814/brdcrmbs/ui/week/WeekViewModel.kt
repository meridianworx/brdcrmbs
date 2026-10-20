package com.ventures814.brdcrmbs.ui.week

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.domain.DateRange
import com.ventures814.brdcrmbs.domain.PlanningData
import com.ventures814.brdcrmbs.domain.PlanningRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    repository: PlanningRepository,
    private val savedState: SavedStateHandle,
    clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    private val zone = clock.zone
    private val initialStart = DateRange.weekContaining(LocalDate.now(clock)).start.toEpochDay()
    private val weekStart = savedState.getStateFlow("weekStart", initialStart)
    private val selectedDay = savedState.getStateFlow("selectedDay", 0)

    private data class LoadedWeek(val range: DateRange, val data: PlanningData, val loading: Boolean = false, val failed: Boolean = false)

    private val loadedWeek = weekStart.flatMapLatest { epochDay ->
        val start = LocalDate.ofEpochDay(epochDay)
        val range = DateRange(start, start.plusWeeks(1))
        repository.observe(range, zone)
            .map { LoadedWeek(range, it) }
            .onStart { emit(LoadedWeek(range, PlanningData(), loading = true)) }
            .catch { emit(LoadedWeek(range, PlanningData(), failed = true)) }
    }

    val state = combine(loadedWeek, selectedDay) { loaded, selected ->
        presentWeek(loaded.range, selected, loaded.data, zone)
            .copy(loading = loaded.loading, failed = loaded.failed)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        presentWeek(DateRange.weekContaining(LocalDate.now(clock)), 0, PlanningData(), zone).copy(loading = true)
    )

    fun selectDay(index: Int) {
        require(index in 0..6)
        savedState["selectedDay"] = index
    }

    fun moveWeek(offset: Long) {
        savedState["weekStart"] = LocalDate.ofEpochDay(weekStart.value).plusWeeks(offset).toEpochDay()
    }
}
