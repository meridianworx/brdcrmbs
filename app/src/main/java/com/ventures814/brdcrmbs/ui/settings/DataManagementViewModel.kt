package com.ventures814.brdcrmbs.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventures814.brdcrmbs.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DataManagementState(
    val confirmation: DataAction? = null,
    val finalReset: Boolean = false,
    val busy: Boolean = false,
    val completed: DataAction? = null,
    val message: String? = null,
    val failed: Boolean = false
)

class DataManagementViewModel(private val operations: DataManagementOperations) : ViewModel() {
    private val mutable = MutableStateFlow(DataManagementState())
    val state = mutable.asStateFlow()
    fun request(action: DataAction) { if (!state.value.busy) mutable.value = DataManagementState(confirmation = action) }
    fun cancel() { if (!state.value.busy) mutable.value = DataManagementState() }
    fun feedbackShown() { mutable.value = state.value.copy(completed = null, message = null) }
    fun confirm() {
        val action = state.value.confirmation ?: return
        if (state.value.busy) return
        if (action == DataAction.RESET_APP && !state.value.finalReset) {
            mutable.value = state.value.copy(finalReset = true)
            return
        }
        mutable.value = DataManagementState(busy = true)
        viewModelScope.launch {
            try {
                val result = operations.manageData(action)
                val message = if (action == DataAction.RESET_APP) "App data reset." else "Planning and history cleared."
                mutable.value = DataManagementState(completed = action, message = message +
                    if (result.remindersCleaned) "" else " Reminder cleanup is incomplete; reopen the app to retry.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.value = DataManagementState(failed = true,
                    message = "Could not complete the operation. No successful deletion was confirmed. Please try again.")
            }
        }
    }
}
