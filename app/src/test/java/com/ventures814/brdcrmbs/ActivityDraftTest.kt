package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.activities.*
import java.time.*
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class ActivityDraftTest {
    @Test fun requiredNameOptionalTimeAndBakalWorkoutAreExplicit() {
        val draft = ActivityDraft(date = LocalDate.of(2026, 12, 31))
        assertThrows(IllegalArgumentException::class.java) { draft.toPlan() }
        val custom = draft.copy(name = "Walk").toPlan()
        assertNull(custom.time); assertNull(custom.notes)
        assertEquals(ActivityType.ACTIVITY, custom.type); assertEquals(ActivityOutcome.PLANNED, custom.outcome)
        val gym = draft.bakalGym().copy(name = "Bakal Gym — Upper Body", notes = "Evening").toPlan()
        assertEquals(ActivityType.WORKOUT, gym.type); assertEquals("Bakal Gym — Upper Body", gym.name)
    }
    @Test fun selectionKeepsScheduleIdentityAndSerializedDraft() {
        val draft = ActivityDraft(date = LocalDate.of(2027, 1, 1), time = LocalTime.of(20, 0))
        val selected = draft.select(SavedActivity(name = "Basketball", type = ActivityType.ACTIVITY, notes = "Court"))
        assertEquals(draft.id, selected.id); assertEquals(draft.date, selected.date); assertEquals(draft.time, selected.time)
        assertEquals("Basketball", selected.name); assertEquals("Court", selected.notes)
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(selected) } }.toByteArray()
        assertEquals(selected, ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() })
    }
    @Test fun unchangedEditKeepsPrecisionAndHistoricalLink() {
        val plan = ActivityPlan(date = LocalDate.of(2026, 3, 1), time = LocalTime.of(17, 15, 30, 123), name = "Walk", notes = "Keep", rescheduledFromId = "missed")
        assertEquals(plan, ActivityDraft.from(plan).toPlan())
        assertFalse(ActivityDraft.from(plan).isNew)
        for (outcome in listOf(ActivityOutcome.DONE, ActivityOutcome.MISSED)) {
            val draft = ActivityDraft.from(plan.copy(outcome = outcome))
            assertFalse(draft.editable)
            assertThrows(IllegalStateException::class.java) { draft.toPlan() }
        }
    }
}
