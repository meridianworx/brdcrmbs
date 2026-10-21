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
import com.ventures814.brdcrmbs.ui.meals.MealDraft
import com.ventures814.brdcrmbs.domain.SavedMeal
import com.ventures814.brdcrmbs.domain.SavedActivity
import com.ventures814.brdcrmbs.ui.activities.ActivityDraft

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val repository: PlanningRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    private val zone = clock.zone
    private val initialStart = DateRange.weekContaining(LocalDate.now(clock)).start.toEpochDay()
    private val weekStart = savedState.getStateFlow("weekStart", initialStart)
    private val selectedDay = savedState.getStateFlow("selectedDay", 0)
    val shortcut = savedState.getStateFlow<ShortcutDraft?>("planningShortcut", null)
    val copyBusy = MutableStateFlow(false)
    val copyError = MutableStateFlow<String?>(null)
    val copyMessage = MutableStateFlow<String?>(null)
    private var feedbackContext = 0L

    fun clearCopyFeedback() {
        feedbackContext++
        copyMessage.value = null
    }

    fun resetToToday() {
        val today = LocalDate.now(clock)
        val start = DateRange.weekContaining(today).start
        clearCopyFeedback()
        savedState["weekStart"] = start.toEpochDay()
        savedState["selectedDay"] = (today.toEpochDay() - start.toEpochDay()).toInt()
    }

    fun openShortcut(mode: Shortcut) {
        if (copyBusy.value) return
        val start = LocalDate.ofEpochDay(weekStart.value)
        savedState["planningShortcut"] = ShortcutDraft(mode, start.plusDays(selectedDay.value.toLong()), start)
        copyError.value = null
        clearCopyFeedback()
    }

    fun changeShortcut(change: (ShortcutDraft) -> ShortcutDraft) {
        if (!copyBusy.value) shortcut.value?.let { savedState["planningShortcut"] = change(it) }
    }

    fun closeShortcut() {
        if (copyBusy.value) return
        savedState["planningShortcut"] = null
        copyError.value = null
    }

    fun copyPlans() {
        val request = shortcut.value ?: return
        if (copyBusy.value) return
        if (request.mode != Shortcut.REPEAT_WEEK && request.destinations.isEmpty()) return
        copyBusy.value = true
        copyError.value = null
        val resultContext = feedbackContext
        viewModelScope.launch {
            try {
                val result = if (request.mode == Shortcut.REPEAT_WEEK) repository.repeatLastWeek(request.weekStart, zone)
                    else repository.copyDay(request.source, request.destinations, zone)
                if (resultContext == feedbackContext) {
                    copyMessage.value = if (result.added == 0 && result.skipped == 0) "Nothing to copy. No plans were added."
                        else "Added ${result.added} planned items; skipped ${result.skipped} identical Planned items."
                }
                savedState["planningShortcut"] = null
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) { copyError.value = "Nothing was copied. ${e.message}"
            } catch (_: Exception) { copyError.value = "Could not copy plans. Nothing was copied; please try again."
            } finally { copyBusy.value = false }
        }
    }
    val editor = savedState.getStateFlow<FastingDraft?>("fastDraft", null)
    val editorLoading = MutableStateFlow(false)
    val editorBusy = MutableStateFlow(false)
    val editorError = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    val activityEditor = savedState.getStateFlow<ActivityDraft?>("activityDraft", null)
    val activityLoading = MutableStateFlow(false)
    val activityBusy = MutableStateFlow(false)
    val activityError = MutableStateFlow<String?>(null)
    val savedActivities = MutableStateFlow<List<SavedActivity>>(emptyList())
    private var activityLoadJob: Job? = null

    init {
        if (activityEditor.value != null) loadActivityEditor(null)
    }

    fun addActivity() {
        savedState["activityDraft"] = ActivityDraft(date = LocalDate.ofEpochDay(weekStart.value).plusDays(selectedDay.value.toLong()))
        loadActivityEditor(null)
    }

    fun editActivity(id: String) = loadActivityEditor(id)

    private fun loadActivityEditor(id: String?) {
        activityLoadJob?.cancel()
        activityLoading.value = true
        activityError.value = null
        savedActivities.value = emptyList()
        activityLoadJob = viewModelScope.launch {
            try {
                if (id != null) savedState["activityDraft"] = ActivityDraft.from(checkNotNull(repository.getActivity(id)))
                savedActivities.value = repository.savedActivities()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { activityError.value = "Could not load activity details or Saved Activities. Return to Week and try again."
            } finally { activityLoading.value = false }
        }
    }

    fun closeActivityEditor() {
        if (activityBusy.value) return
        activityLoadJob?.cancel()
        activityLoading.value = false
        activityError.value = null
        savedState["activityDraft"] = null
    }

    fun updateActivityDraft(change: (ActivityDraft) -> ActivityDraft) {
        if (activityBusy.value || activityLoading.value) return
        val draft = activityEditor.value ?: return
        if (!draft.editable) return
        savedState["activityDraft"] = change(draft)
    }

    fun saveActivity() {
        val draft = activityEditor.value ?: return
        if (activityBusy.value || activityLoading.value) return
        val activity = try { draft.toPlan() } catch (e: IllegalArgumentException) {
            activityError.value = e.message; return
        } catch (e: IllegalStateException) { activityError.value = e.message; return }
        activityBusy.value = true
        viewModelScope.launch {
            try {
                repository.savePlannedActivity(activity, draft.isNew, draft.saveForReuse)
                savedState["activityDraft"] = null
                activityError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { activityError.value = "Could not save the activity. Your entries are retained; please try again."
            } finally { activityBusy.value = false }
        }
    }

    fun deleteActivity() {
        val draft = activityEditor.value ?: return
        if (draft.isNew || !draft.editable || activityBusy.value || activityLoading.value) return
        activityBusy.value = true
        viewModelScope.launch {
            try {
                repository.deletePlannedActivity(draft.id)
                savedState["activityDraft"] = null
                activityError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { activityError.value = "Could not delete the activity. Your saved activity has been retained."
            } finally { activityBusy.value = false }
        }
    }

    val mealEditor = savedState.getStateFlow<MealDraft?>("mealDraft", null)
    val mealLoading = MutableStateFlow(false)
    val mealBusy = MutableStateFlow(false)
    val mealError = MutableStateFlow<String?>(null)
    val savedMeals = MutableStateFlow<List<SavedMeal>>(emptyList())
    private var mealLoadJob: Job? = null

    init {
        if (mealEditor.value != null) loadMealEditor(null)
    }

    fun addMeal() {
        savedState["mealDraft"] = MealDraft(date = LocalDate.ofEpochDay(weekStart.value).plusDays(selectedDay.value.toLong()))
        loadMealEditor(null)
    }

    fun editMeal(id: String) = loadMealEditor(id)

    private fun loadMealEditor(id: String?) {
        mealLoadJob?.cancel()
        mealLoading.value = true
        mealError.value = null
        savedMeals.value = emptyList()
        mealLoadJob = viewModelScope.launch {
            try {
                if (id != null) savedState["mealDraft"] = MealDraft.from(checkNotNull(repository.getMeal(id)))
                savedMeals.value = repository.savedMeals()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mealError.value = "Could not load meal details or Saved Meals. Return to Week and try again."
            } finally { mealLoading.value = false }
        }
    }

    fun closeMealEditor() {
        if (mealBusy.value) return
        mealLoadJob?.cancel()
        mealLoading.value = false
        mealError.value = null
        savedState["mealDraft"] = null
    }

    fun updateMealDraft(change: (MealDraft) -> MealDraft) {
        if (mealBusy.value || mealLoading.value) return
        val draft = mealEditor.value ?: return
        if (!draft.editable) return
        savedState["mealDraft"] = change(draft)
    }

    fun saveMeal() {
        val draft = mealEditor.value ?: return
        if (mealBusy.value || mealLoading.value) return
        val meal = try { draft.toPlan() } catch (e: IllegalArgumentException) {
            mealError.value = e.message; return
        } catch (e: IllegalStateException) { mealError.value = e.message; return }
        mealBusy.value = true
        viewModelScope.launch {
            try {
                repository.savePlannedMeal(meal, draft.isNew, draft.saveForReuse)
                savedState["mealDraft"] = null
                mealError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mealError.value = "Could not save the meal. Your entries are retained; please try again."
            } finally { mealBusy.value = false }
        }
    }

    fun deleteMeal() {
        val draft = mealEditor.value ?: return
        if (draft.isNew || !draft.editable || mealBusy.value || mealLoading.value) return
        mealBusy.value = true
        viewModelScope.launch {
            try {
                repository.deletePlannedMeal(draft.id)
                savedState["mealDraft"] = null
                mealError.value = null
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mealError.value = "Could not delete the meal. Your saved meal has been retained."
            } finally { mealBusy.value = false }
        }
    }

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
        clearCopyFeedback()
        savedState["weekStart"] = LocalDate.ofEpochDay(weekStart.value).plusWeeks(offset).toEpochDay()
    }
}
