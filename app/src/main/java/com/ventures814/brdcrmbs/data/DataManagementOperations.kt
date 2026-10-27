package com.ventures814.brdcrmbs.data

enum class DataAction { CLEAR_PLANNING, RESET_APP }

data class DataManagementResult(val remindersCleaned: Boolean)

interface DataManagementOperations {
    suspend fun manageData(action: DataAction): DataManagementResult
}
