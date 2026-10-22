package com.ventures814.brdcrmbs.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.domain.*
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TodayState(val day: AdherenceDay, val plans: PlanningData = PlanningData(),
    val loading: Boolean = false, val failed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repository: PlanningRepository, private val saved: SavedStateHandle,
    private val clock: Clock? = null) : ViewModel() {
    private fun currentDay(): AdherenceDay {
        val current = clock ?: Clock.systemDefaultZone()
        return AdherenceDay(LocalDate.now(current), current.zone)
    }
    private val day = MutableStateFlow(currentDay())
    private val refreshVersion = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val draft = saved.getStateFlow<ActualMealDraft?>("actualMealDraft", null)
    val original = MutableStateFlow<MealPlan?>(null)
    val editorLoading = MutableStateFlow(false)
    private var editorJob: Job? = null
    val state = combine(day, refreshVersion) { value, _ -> value }.flatMapLatest { context ->
        repository.observe(DateRange.day(context.date), context.zone)
            .map { TodayState(context, it) }
            .onStart { emit(TodayState(context, loading = true)) }
            .catch { emit(TodayState(context, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState(day.value, loading = true))

    init {
        draft.value?.let {
            if (it.date == day.value.date) loadActual(it.id, retain = true)
            else saved["actualMealDraft"] = null
        }
    }

    fun refresh() {
        val current = currentDay()
        if (current != day.value) {
            day.value = current
            if (!busy.value) closeActual()
        }
        refreshVersion.value++
    }

    private fun mutate(action: suspend (AdherenceDay) -> Unit, onSaved: () -> Unit = {}) {
        if (busy.value) return
        val context = day.value
        if (context != currentDay()) { refresh(); error.value = "The local day changed. Review today's plan and try again."; return }
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { action(context); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Could not record the outcome. Please try again." }
            catch (_: Exception) { error.value = "Could not record the outcome. Your entries are retained; please try again." }
            finally { busy.value = false }
        }
    }
    fun fasting(id: String, outcome: FastingOutcome) = mutate({ repository.recordTodayFasting(id, outcome, it) })
    fun meal(id: String, outcome: MealOutcome) = mutate({ repository.recordTodayMeal(id, outcome, it) })
    fun activity(id: String, outcome: ActivityOutcome) = mutate({ repository.recordTodayActivity(id, outcome, it) })

    fun openActual(id: String) { if (!busy.value) loadActual(id, retain = false) }
    private fun loadActual(id: String, retain: Boolean) {
        editorJob?.cancel(); editorLoading.value = true; error.value = null; original.value = null
        editorJob = viewModelScope.launch {
            try {
                val meal = checkNotNull(repository.getMeal(id)) { "Meal no longer exists" }
                require(meal.date == day.value.date && day.value == currentDay()) { "Meal is no longer in today's context" }
                original.value = meal
                if (!retain) saved["actualMealDraft"] = ActualMealDraft.from(meal)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { error.value = "Could not load this meal. Return to Today and try again."
            } finally { editorLoading.value = false }
        }
    }
    fun changeActual(change: (ActualMealDraft) -> ActualMealDraft) {
        if (!busy.value) draft.value?.let { saved["actualMealDraft"] = change(it) }
    }
    fun closeActual() {
        if (busy.value) return
        editorJob?.cancel(); editorLoading.value = false; original.value = null
        saved["actualMealDraft"] = null; error.value = null
    }
    fun saveActual() {
        val entry = draft.value ?: return
        val actual = try { entry.actual() } catch (_: IllegalArgumentException) { error.value = "Actual meal is required."; return }
        mutate({ context ->
            require(entry.date == context.date) { "The local day changed. Return to Today." }
            repository.recordTodayMeal(entry.id, MealOutcome.CHANGED, context, actual)
        }, { saved["actualMealDraft"] = null; original.value = null })
    }
}
