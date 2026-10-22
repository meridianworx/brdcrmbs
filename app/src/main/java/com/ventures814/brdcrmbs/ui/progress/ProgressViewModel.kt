package com.ventures814.brdcrmbs.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.domain.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ProgressContext(val month: YearMonth, val selected: LocalDate, val today: LocalDate, val zone: ZoneId)
data class ProgressState(val context: ProgressContext, val review: MonthReview? = null,
    val loading: Boolean = true, val failed: Boolean = false) {
    val selectedDay: ReviewDay? get() = review?.days?.firstOrNull { it.date == context.selected }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val repository: PlanningRepository, private val saved: SavedStateHandle,
    private val clock: Clock? = null) : ViewModel() {
    private fun now(): Pair<LocalDate, ZoneId> {
        val source = clock ?: Clock.systemDefaultZone()
        return LocalDate.now(source) to source.zone
    }
    private fun initial(): ProgressContext {
        val (today, zone) = now()
        val month = saved.get<String>("progressMonth")?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(today)
        val selected = saved.get<Long>("progressSelected")?.let(LocalDate::ofEpochDay)?.takeIf { YearMonth.from(it) == month } ?: month.atDay(1)
        return ProgressContext(month, if (saved.contains("progressMonth")) selected else today, today, zone)
    }
    private val context = MutableStateFlow(initial())
    private val retry = MutableStateFlow(0)
    // Selecting a day reuses this month's snapshot; it does not resubscribe/query per cell.
    private val monthData = combine(context.map { it.month to it.zone }.distinctUntilChanged(), retry) { range, _ -> range }
        .flatMapLatest { (month, zone) ->
            val requested = context.value.copy(month = month, zone = zone)
            repository.observe(DateRange(month.atDay(1), month.plusMonths(1).atDay(1)), zone)
                .map { ProgressState(requested, it.reviewMonth(month, zone), loading = false) }
                .onStart { emit(ProgressState(requested)) }
                .catch { emit(ProgressState(requested, loading = false, failed = true)) }
        }.flowOn(Dispatchers.Default)
    val state = combine(context, monthData) { current, loaded ->
        if (loaded.context.month != current.month || loaded.context.zone != current.zone) ProgressState(current)
        else loaded.copy(context = current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState(context.value))

    private fun set(value: ProgressContext) {
        saved["progressMonth"] = value.month.toString(); saved["progressSelected"] = value.selected.toEpochDay()
        context.value = value
    }
    fun resetToToday() { val (today, zone) = now(); set(ProgressContext(YearMonth.from(today), today, today, zone)); retry.value++ }
    fun moveMonth(offset: Long) { val next = context.value.month.plusMonths(offset); set(context.value.copy(month = next, selected = next.atDay(1))) }
    fun selectDate(date: LocalDate) { if (YearMonth.from(date) == context.value.month) set(context.value.copy(selected = date)) }
    fun refresh() { val (today, zone) = now(); set(context.value.copy(today = today, zone = zone)); retry.value++ }
}
