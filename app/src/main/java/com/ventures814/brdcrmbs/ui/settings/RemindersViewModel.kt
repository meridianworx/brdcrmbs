package com.ventures814.brdcrmbs.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class RemindersViewModel(private val settings: ReminderSettingsStore) : ViewModel() {
    val preferences = settings.state
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val asked get() = settings.asked
    private var failed: Pair<ReminderType, Boolean>? = null
    fun retry() { failed?.let { set(it.first, it.second) } }
    fun set(type: ReminderType, on: Boolean) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { settings.set(type, on); error.value = null; failed = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = type to on; error.value = "Could not save reminder preference. Please try again." }
            finally { busy.value = false }
        }
    }
    fun markAsked(then: () -> Unit) {
        viewModelScope.launch {
            try { settings.markAsked(); then() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Could not save permission request state. Please try again." }
        }
    }
}
