package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.today.ActualMealDraft
import java.io.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ActualMealDraftTest {
    @Test fun requiredTextAndAbsentOptionalFields() {
        val draft = ActualMealDraft("meal", LocalDate.of(2026, 12, 31))
        assertThrows(IllegalArgumentException::class.java) { draft.actual() }
        assertEquals(ActualMeal("Soup"), draft.copy(text = "Soup").actual())
    }
    @Test fun originalPlanRemainsSeparateAndActualPrecisionSurvivesEditing() {
        val actual = ActualMeal("Actual", LocalTime.of(18, 30, 15, 123), "Dinner", "Notes")
        val meal = MealPlan(date = LocalDate.of(2027, 1, 1), text = "Original", outcome = MealOutcome.CHANGED, actual = actual)
        assertEquals(actual, ActualMealDraft.from(meal).actual())
        assertEquals("Original", meal.text)
        assertEquals(actual.copy(text = "Corrected"), ActualMealDraft.from(meal).copy(text = "Corrected").actual())
        assertEquals(actual, meal.actual)
    }
    @Test fun draftCanBeSerializedForRestoration() {
        val draft = ActualMealDraft("id", LocalDate.of(2026, 3, 8), "Lunch", "Label", LocalTime.NOON, "Notes")
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(draft) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(draft, restored)
    }
}
