package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.fasting.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class FastingDraftTest {
    private val zone = ZoneId.of("America/Denver")
    private val date = LocalDate.of(2026, 10, 19)

    @Test fun presetsUseExplicitElapsedHoursIncludingMidnightAndDst() {
        for (day in listOf(date, LocalDate.of(2026, 3, 7), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 12, 31))) {
            for (preset in FastPreset.entries.filter { it.hours != null }) {
                val draft = FastingDraft.new(day, zone).selectPreset(preset)
                val plan = draft.toPlan() as FastingEvent
                assertEquals(preset.hours, Duration.between(plan.start, plan.end).toHours())
                assertEquals(day, plan.start.atZone(zone).toLocalDate())
                assertEquals(preset.label, plan.preset)
            }
        }
    }

    @Test fun customHasNoPresetDurationCapAndManualEndSwitchesToCustom() {
        val draft = FastingDraft.new(date, zone).changeEnd(date.plusDays(20).atTime(3, 15))
        assertEquals(FastPreset.CUSTOM, draft.preset)
        assertTrue(draft.duration() > Duration.ofHours(72))
        assertEquals(draft.end, (draft.toPlan() as FastingEvent).end.atZone(zone).toLocalDateTime())
    }

    @Test fun omadRetainsScheduleAndNoFastIsNotAnEvent() {
        val draft = FastingDraft.new(date, zone)
        assertEquals(draft.end, draft.selectPreset(FastPreset.OMAD).end)
        assertEquals("OMAD", (draft.selectPreset(FastPreset.OMAD).toPlan() as FastingEvent).preset)
        assertEquals(date, (draft.selectPreset(FastPreset.NO_FAST).toPlan() as NoFastPlan).date)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartCannotBecomeAPlan() {
        FastingDraft.new(date, zone).changeEnd(date.atTime(18, 0)).toPlan()
    }

    @Test(expected = IllegalArgumentException::class)
    fun equalEndpointsCannotBecomeAPlan() {
        val draft = FastingDraft.new(date, zone)
        draft.changeEnd(draft.start).toPlan()
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonexistentDstTimeIsRejectedWithoutSilentClockShift() {
        FastingDraft.new(LocalDate.of(2026, 3, 8), zone).selectPreset(FastPreset.CUSTOM)
            .changeStart(LocalDateTime.of(2026, 3, 8, 2, 30)).toPlan()
    }

    @Test fun repeatedHourRetainsOriginalOffsetAndIdentity() {
        val local = LocalDateTime.of(2026, 11, 1, 1, 30)
        val later = local.toInstant(ZoneOffset.ofHours(-7))
        val original = FastingEvent(id = "same", start = later, end = later.plusSeconds(7200), zone = zone, preset = "Custom", notes = "Keep")
        assertEquals(original, FastingDraft.from(original).toPlan())
    }

    @Test fun unchangedEditorRetainsExistingOptionalLabelNotesAndSubMinutePrecision() {
        val event = FastingEvent(start = Instant.ofEpochMilli(1001), end = Instant.ofEpochMilli(2005), zone = zone, notes = "Keep")
        assertEquals(event, FastingDraft.from(event).toPlan())
        assertEquals(event.copy(preset = "Existing label"), FastingDraft.from(event.copy(preset = "Existing label")).toPlan())
        val bytes = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(bytes).use { it.writeObject(FastingDraft.from(event)) }
        val restored = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as FastingDraft }
        assertEquals(event, restored.toPlan())
    }
}
