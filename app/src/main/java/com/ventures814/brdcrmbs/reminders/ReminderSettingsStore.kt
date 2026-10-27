package com.ventures814.brdcrmbs.reminders

import android.content.Context
import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Four opt-in preferences; no planning records or additional persistence dependency. */
@SuppressLint("UseKtx") // Keep commit's success result so failed durable writes are reported.
class ReminderSettingsStore(context: Context, name: String = "reminder_preferences") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val permission = context.getSharedPreferences("${name}_permission", Context.MODE_PRIVATE)
    private fun read() = ReminderPreferences(ReminderType.entries.filter { prefs.getBoolean(it.name, false) }.toSet())
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val asked: Boolean get() = permission.getBoolean("permissionAsked", false)
    /** Restore the four user choices only; permission and runtime state stay device-local. */
    suspend fun replaceForRestore(value: ReminderPreferences) = withContext(Dispatchers.IO) {
        val edit = prefs.edit()
        ReminderType.entries.forEach { edit.putBoolean(it.name, it in value.enabled) }
        check(edit.commit()) { "Could not restore reminder preferences" }
        mutable.value = read()
    }
    suspend fun markAsked() = withContext(Dispatchers.IO) { check(permission.edit().putBoolean("permissionAsked", true).commit()) }
    /** App-owned prompt history only; never changes Android permission/channel settings. */
    suspend fun resetPermissionHistory() = withContext(Dispatchers.IO) { check(permission.edit().clear().commit()) }
    suspend fun set(type: ReminderType, on: Boolean) = withContext(Dispatchers.IO) {
        check(prefs.edit().putBoolean(type.name, on).commit()) { "Could not save reminder preference" }
        mutable.value = read()
    }
}
