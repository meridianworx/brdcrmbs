package com.ventures814.brdcrmbs.ui.fasting

import com.ventures814.brdcrmbs.domain.*
import java.io.Serializable
import java.time.*
import java.util.UUID

enum class FastPreset(val label: String, val hours: Long? = null) {
    H12("12:12", 12), H14("14:10", 14), H16("16:8", 16), H18("18:6", 18), H20("20:4", 20),
    OMAD("OMAD"), H24("24h", 24), H36("36h", 36), H48("48h", 48), H72("72h", 72),
    CUSTOM("Custom"), NO_FAST("No fast")
}

/** Small saved form draft; no durable planning writes occur until Save. */
data class FastingDraft(
    val id: String,
    val isNew: Boolean,
    val preset: FastPreset,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val zone: ZoneId,
    val notes: String? = null,
    val startOffset: ZoneOffset? = null,
    val endOffset: ZoneOffset? = null,
    val editable: Boolean = true,
    val originalPreset: String? = null,
    val presetEdited: Boolean = true
) : Serializable {
    fun startInstant(): Instant = resolve(start, startOffset)
    fun endInstant(): Instant = resolve(end, endOffset)
    fun duration(): Duration = Duration.between(startInstant(), endInstant()).also {
        require(!it.isZero && !it.isNegative) { "End must be after start." }
    }

    private fun resolve(local: LocalDateTime, preferred: ZoneOffset?): Instant {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "This time does not exist in ${zone.id} because the clock changes. Choose another time." }
        // Existing repeated-hour offsets are retained. New ambiguous input uses the earlier offset.
        val offset = preferred?.takeIf { it in offsets } ?: offsets.first()
        return local.toInstant(offset)
    }

    fun selectPreset(value: FastPreset): FastingDraft {
        val changed = copy(preset = value, presetEdited = true)
        val hours = value.hours
        return if (hours != null) runCatching { changed.withDuration(hours) }.getOrDefault(changed) else changed
    }

    private fun withDuration(hours: Long): FastingDraft {
        val resolved = startInstant().plus(Duration.ofHours(hours)).atZone(zone)
        return copy(end = resolved.toLocalDateTime(), endOffset = resolved.offset)
    }

    fun changeStart(value: LocalDateTime): FastingDraft {
        val changed = copy(start = value)
        val hours = preset.hours
        return if (hours != null) runCatching { changed.withDuration(hours) }.getOrDefault(changed) else changed
    }

    fun changeEnd(value: LocalDateTime): FastingDraft = copy(
        end = value, preset = if (preset.hours != null) FastPreset.CUSTOM else preset,
        presetEdited = presetEdited || preset.hours != null
    )

    fun toPlan(): FastingPlan {
        check(editable) { "Recorded outcomes cannot be edited." }
        if (preset == FastPreset.NO_FAST) return NoFastPlan(id, start.toLocalDate(), zone, notes)
        duration()
        return FastingEvent(id, startInstant(), endInstant(), zone, if (presetEdited) preset.label else originalPreset, notes)
    }

    companion object {
        fun new(date: LocalDate, zone: ZoneId): FastingDraft = FastingDraft(
            UUID.randomUUID().toString(), true, FastPreset.H16, date.atTime(19, 0),
            date.plusDays(1).atTime(11, 0), zone
        ).selectPreset(FastPreset.H16)

        fun from(plan: FastingPlan): FastingDraft = when (plan) {
            is FastingEvent -> {
                val start = plan.start.atZone(plan.zone)
                val end = plan.end.atZone(plan.zone)
                FastingDraft(plan.id, false, FastPreset.entries.firstOrNull { it.label == plan.preset } ?: FastPreset.CUSTOM,
                    start.toLocalDateTime(), end.toLocalDateTime(), plan.zone, plan.notes,
                    start.offset, end.offset, plan.outcome == FastingOutcome.PLANNED,
                    originalPreset = plan.preset, presetEdited = false)
            }
            is NoFastPlan -> new(plan.date, plan.zone).copy(
                id = plan.id, isNew = false, preset = FastPreset.NO_FAST, notes = plan.notes
            )
        }
    }
}
