package com.ventures814.brdcrmbs.preferences

import com.ventures814.brdcrmbs.ui.fasting.FastPreset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TimeFormat(val label: String) {
    SYSTEM("System default"), TWELVE("12-hour"), TWENTY_FOUR("24-hour");
    fun is24Hour(system: Boolean) = when (this) { SYSTEM -> system; TWELVE -> false; TWENTY_FOUR -> true }
}

/** Convenience defaults only: no plans, history, zones or reminder triggers are stored here. */
data class AppPreferences(
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val fastingPreset: FastPreset? = null,
    val mealCount: Int = 3
) {
    init { require(mealCount in 1..6); require(fastingPreset == null || fastingPreset in defaultFastPresets) }
}

val defaultFastPresets: List<FastPreset> = FastPreset.entries.filter { it != FastPreset.CUSTOM && it != FastPreset.NO_FAST }

/** Pure display conversion. Serialization and scheduling never use these patterns. */
data class ClockFormat(val is24Hour: Boolean = false) {
    val pattern: String get() = if (is24Hour) "HH:mm" else "h:mm a"
    fun formatter(prefix: String = "", locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(prefix + pattern, locale)
}
