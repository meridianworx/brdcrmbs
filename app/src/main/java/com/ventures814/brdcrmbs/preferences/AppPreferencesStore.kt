package com.ventures814.brdcrmbs.preferences

import android.annotation.SuppressLint
import android.content.Context
import com.ventures814.brdcrmbs.ui.fasting.FastPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Same checked SharedPreferences mechanism as Reminders, with one atomic configuration write. */
@SuppressLint("UseKtx") // Preserve commit's durable-write result.
class AppPreferencesStore(context: Context, name: String = "app_preferences") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private fun read() = AppPreferences(
        runCatching { TimeFormat.valueOf(prefs.getString("timeFormat", null)!!) }.getOrDefault(TimeFormat.SYSTEM),
        runCatching { FastPreset.valueOf(prefs.getString("fastingPreset", null)!!) }.getOrNull()?.takeIf { it in defaultFastPresets },
        prefs.getInt("mealCount", 3).takeIf { it in 1..6 } ?: 3
    )
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    suspend fun replace(value: AppPreferences) = withContext(Dispatchers.IO) { mutex.withLock {
        check(prefs.edit().putString("timeFormat", value.timeFormat.name)
            .putString("fastingPreset", value.fastingPreset?.name).putInt("mealCount", value.mealCount).commit()) {
            "Could not save app preferences"
        }
        mutable.value = value
    } }
}
