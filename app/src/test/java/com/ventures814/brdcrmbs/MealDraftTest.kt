package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.meals.MealDraft
import java.io.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class MealDraftTest {
    @Test fun requiredNameAndOptionalFieldsCreatePlannedMeal() {
        val date = LocalDate.of(2026, 12, 31)
        assertThrows(IllegalArgumentException::class.java) { MealDraft(date = date, text = " ").toPlan() }
        val draft = MealDraft(date = date, text = "Soup")
        val plan = draft.toPlan()
        assertEquals(date, plan.date); assertEquals(MealOutcome.PLANNED, plan.outcome)
        assertNull(plan.time); assertNull(plan.label); assertNull(plan.notes); assertNull(plan.actual)
    }

    @Test fun unchangedEditRetainsIdentityAllValuesAndTimePrecision() {
        val plan = MealPlan(date = LocalDate.of(2027, 1, 1), time = LocalTime.of(13, 2, 3, 123), text = "Salad", label = "First meal", notes = "Keep")
        assertEquals(plan, MealDraft.from(plan).toPlan())
        assertFalse(MealDraft.from(plan).isNew)
    }

    @Test fun savedSelectionPopulatesOnlyReusableDetailsAndDraftSurvivesSerialization() {
        val draft = MealDraft(date = LocalDate.of(2026, 2, 28), time = LocalTime.NOON)
        val selected = draft.select(SavedMeal(text = "Eggs", label = "Lunch", notes = "With greens"))
        assertEquals(draft.id, selected.id); assertEquals(draft.date, selected.date); assertEquals(draft.time, selected.time)
        assertEquals("Eggs", selected.text); assertEquals("Lunch", selected.label); assertEquals("With greens", selected.notes)
        assertFalse(selected.saveForReuse)
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(selected) } }.toByteArray()
        assertEquals(selected, ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() })
    }

    @Test fun everyRecordedOutcomeIsReadOnlyAndRetainsOriginalAndActualModel() {
        MealOutcome.entries.filter { it != MealOutcome.PLANNED }.forEach { outcome ->
            val plan = MealPlan(date = LocalDate.now(), text = "Original", outcome = outcome,
                actual = if (outcome == MealOutcome.CHANGED) ActualMeal("Actual") else null)
            assertFalse(MealDraft.from(plan).editable)
            assertThrows(IllegalStateException::class.java) { MealDraft.from(plan).toPlan() }
            assertEquals("Original", plan.text)
            if (outcome == MealOutcome.CHANGED) assertEquals("Actual", plan.actual!!.text)
        }
    }
}
