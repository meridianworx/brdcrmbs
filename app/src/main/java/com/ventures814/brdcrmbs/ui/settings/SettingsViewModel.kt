package com.ventures814.brdcrmbs.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SavedLibraryState(val meals: List<SavedMeal> = emptyList(), val activities: List<SavedActivity> = emptyList(),
    val loading: Boolean = true, val failed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val repository: PlanningRepository, private val saved: SavedStateHandle) : ViewModel() {
    val page = saved.getStateFlow("settingsPage", SettingsPage.HOME)
    val draft = saved.getStateFlow<SavedItemDraft?>("savedItemDraft", null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private val retry = MutableStateFlow(0)
    val library = retry.flatMapLatest {
        combine(repository.observeSavedMeals(), repository.observeSavedActivities()) { meals, activities ->
            SavedLibraryState(meals, activities, loading = false)
        }.onStart { emit(SavedLibraryState()) }.catch { emit(SavedLibraryState(loading = false, failed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedLibraryState())

    fun retry() { retry.value++ }
    fun open(target: SettingsPage) { if (!busy.value) { saved["settingsPage"] = target; saved["savedItemDraft"] = null; error.value = null } }
    fun add() { if (!busy.value && page.value != SettingsPage.HOME) { saved["savedItemDraft"] = SavedItemDraft(page.value); error.value = null } }
    fun edit(item: SavedMeal) { if (!busy.value) { saved["savedItemDraft"] = SavedItemDraft.from(item); error.value = null } }
    fun edit(item: SavedActivity) { if (!busy.value) { saved["savedItemDraft"] = SavedItemDraft.from(item); error.value = null } }
    fun change(transform: (SavedItemDraft) -> SavedItemDraft) { if (!busy.value) draft.value?.let { saved["savedItemDraft"] = transform(it) } }
    fun back() {
        if (busy.value) return
        error.value = null
        if (draft.value != null) saved["savedItemDraft"] = null else saved["settingsPage"] = SettingsPage.HOME
    }
    private fun mutate(action: suspend (SavedItemDraft) -> Unit) {
        val item = draft.value ?: return
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { action(item); saved["savedItemDraft"] = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Could not update this saved item. Your entries are retained; please try again or return to the list." }
            finally { busy.value = false }
        }
    }
    fun save() {
        if (draft.value?.name.isNullOrBlank()) return
        mutate { item ->
            if (item.page == SettingsPage.MEALS) repository.saveSavedMeal(item.meal(), item.isNew)
            else repository.saveSavedActivity(item.activity(), item.isNew)
        }
    }
    fun delete() {
        if (draft.value?.isNew != false) return
        mutate { item ->
            if (item.page == SettingsPage.MEALS) repository.deleteSavedMeal(item.id) else repository.deleteSavedActivity(item.id)
        }
    }
}
