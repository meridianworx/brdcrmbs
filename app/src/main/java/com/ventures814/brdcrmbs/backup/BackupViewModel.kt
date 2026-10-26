package com.ventures814.brdcrmbs.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupDocumentRequest { CREATE, OPEN }
data class BackupPreview(val createdAt: java.time.Instant, val records: Int)
data class BackupUiState(val busy: Boolean = false, val request: BackupDocumentRequest? = null,
    val filename: String = "", val preview: BackupPreview? = null, val message: String? = null, val failed: Boolean = false)

class BackupViewModel(private val operations: BackupOperations) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    private var prepared: PreparedBackup? = null
    private var candidate: BackupSnapshot? = null

    private fun launch(action: suspend () -> Unit, failure: String) {
        mutable.value = mutable.value.copy(busy = true, message = null, failed = false)
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { prepared = null; candidate = null; mutable.value = BackupUiState(message = failure, failed = true) }
        }
    }
    fun create() {
        if (state.value.busy) return
        launch({
            prepared = operations.create()
            mutable.value = BackupUiState(busy = true, request = BackupDocumentRequest.CREATE, filename = prepared!!.filename)
        }, "Could not prepare a complete backup. Your data is unchanged. Please try again.")
    }
    fun open() { if (!state.value.busy) mutable.value = BackupUiState(busy = true, request = BackupDocumentRequest.OPEN) }
    fun requestLaunched() { mutable.value = state.value.copy(request = null) }
    fun pickerFailed() { prepared = null; mutable.value = BackupUiState(message = "Could not open the document picker. Try again.", failed = true) }
    fun created(uri: Uri?) {
        if (uri == null) { prepared = null; mutable.value = BackupUiState(message = "Backup cancelled."); return }
        val payload = prepared
        if (payload == null) { mutable.value = BackupUiState(message = "Backup preparation was interrupted. Choose Create Backup again.", failed = true); return }
        launch({ operations.write(uri, payload); prepared = null; mutable.value = BackupUiState(message = "Backup saved.") },
            "Could not write the backup. The selected file may be incomplete; create a new backup. Your app data is unchanged.")
    }
    fun opened(uri: Uri?) {
        if (uri == null) { mutable.value = BackupUiState(message = "Restore cancelled."); return }
        launch({
            candidate = operations.read(uri)
            val snapshot = checkNotNull(candidate)
            mutable.value = BackupUiState(preview = BackupPreview(snapshot.createdAt, snapshot.count))
        }, "Could not read a supported, complete brdcrmbs backup. Your current data is unchanged.")
    }
    fun cancelRestore() { if (!state.value.busy) { candidate = null; mutable.value = BackupUiState(message = "Restore cancelled.") } }
    fun restore() {
        val snapshot = candidate ?: return
        if (state.value.busy) return
        launch({
            val result = operations.restore(snapshot)
            candidate = null
            mutable.value = BackupUiState(message = if (result.remindersReconciled) "Backup restored." else "Backup restored. Open Reminders to retry reminder synchronization.")
        }, "Could not restore the backup. Your previous data has been retained; please try again.")
    }
}
