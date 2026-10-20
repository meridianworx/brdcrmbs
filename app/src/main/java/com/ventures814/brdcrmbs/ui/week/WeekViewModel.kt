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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.ventures814.brdcrmbs.ui.fasting.FastingDraft

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val repository: PlanningRepository,
    private val savedState: SavedStateHandle,
    clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    private val zone = clock.zone
    private val initialStart = DateRange.weekContaining(LocalDate.now(clock)).start.toEpochDay()
    private val weekStart = savedState.getStateFlow("weekStart", initialStart)
    private val selectedDay = savedState.getStateFlow("selectedDay", 0)
    val editor = savedState.getStateFlow<FastingDraft?>("fastDraft", null)
    val editorLoading = MutableStateFlow(false)
    val editorBusy = MutableStateFlow(false)
    val editorError = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    fun addFast() {
        loadJob?.cancel()
        editorLoading.value = false
        editorError.value = null
        savedState["fastDraft"] = FastingDraft.new(
            LocalDate.ofEpochDay(weekStart.value).plusDays(selectedDay.value.toLong()), zone
        )
    }

    fun editFast(id: String) {
        loadJob?.cancel()
        editorLoading.value = true
        editorError.value = null
        loadJob = viewModelScope.launch {
            try {
                val plan = checkNotNull(repository.getFastingPlan(id)) { "This plan no longer exists." }
                savedState["fastDraft"] = FastingDraft.from(plan)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { editorError.value = "Could not open the fasting plan. Return to Week and try again."
            } finally { editorLoading.value = false }
        }
    }

    fun closeEditor() {
        if (editorBusy.value) return
        loadJob?.cancel()
        editorLoading.value = false
        editorError.value = null
        savedState["fastDraft"] = null
    }

    fun updateDraft(change: (FastingDraft) -> FastingDraft) {
        if (editorBusy.value) return
        val draft = editor.value ?: return
        try {
            val changed = change(draft)
            savedState["fastDraft"] = changed
            editorError.value = null
        } catch (e: IllegalArgumentException) {
            // Keep the entered schedule for correction, without writing to persistence.
            editorError.value = e.message
        }
    }

    fun saveFast() {
        val draft = editor.value ?: return
        if (editorBusy.value) return
        val plan = try { draft.toPlan() } catch (e: IllegalArgumentException) {
            editorError.value = e.message; return
        } catch (e: IllegalStateException) { editorError.value = e.message; return }
        editorBusy.value = true
        viewModelScope.launch {
            try {
                if (draft.isNew) repository.addFastingPlan(plan) else repository.updateFastingPlan(plan)
                savedState["fastDraft"] = null
                editorError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { editorError.value = "Could not save the plan. Your entries are retained; please try again."
            } finally { editorBusy.value = false }
        }
    }

    fun deleteFast() {
        val draft = editor.value ?: return
        if (draft.isNew || !draft.editable || editorBusy.value) return
        editorBusy.value = true
        viewModelScope.launch {
            try {
                repository.deleteFastingPlan(draft.id)
                savedState["fastDraft"] = null
                editorError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { editorError.value = "Could not delete the plan. Your saved plan has been retained."
            } finally { editorBusy.value = false }
        }
    }

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
