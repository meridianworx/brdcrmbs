package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.ui.settings.*
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class SavedItemDraftTest {
    @Test fun requiredNamesAndOptionalFieldsUseLibraryModelsOnly() {
        assertThrows(IllegalArgumentException::class.java) { SavedItemDraft(SettingsPage.MEALS, name = " ").meal() }
        assertThrows(IllegalArgumentException::class.java) { SavedItemDraft(SettingsPage.ACTIVITIES).activity() }
        val meal = SavedItemDraft(SettingsPage.MEALS, name = "Soup").meal()
        assertNull(meal.label); assertNull(meal.notes)
        val activity = SavedItemDraft(SettingsPage.ACTIVITIES, name = "Walk").activity()
        assertNull(activity.notes); assertEquals(ActivityType.ACTIVITY, activity.type)
    }
    @Test fun editingRetainsIdentityAndOriginalOptionalFields() {
        val meal = SavedMeal(text = "Chicken + vegetables", label = "Lunch", notes = "Original notes")
        val activity = SavedActivity(name = "Bakal Gym — Upper Body", type = ActivityType.WORKOUT, notes = "Text reference")
        assertEquals(meal, SavedItemDraft.from(meal).meal())
        assertEquals(activity, SavedItemDraft.from(activity).activity())
        assertFalse(SavedItemDraft.from(meal).isNew)
        assertEquals("Chicken + vegetables", meal.text)
        assertEquals("Chicken + broccoli", SavedItemDraft.from(meal).copy(name = "Chicken + broccoli").meal().text)
    }
    @Test fun unsavedDraftSurvivesSerializationWithoutSchedulingFields() {
        for (page in listOf(SettingsPage.MEALS, SettingsPage.ACTIVITIES)) {
            val draft = SavedItemDraft(page, name = "Unsaved", label = "Lunch", notes = "Keep", type = ActivityType.WORKOUT)
            val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(draft) } }.toByteArray()
            assertEquals(draft, ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() })
        }
    }
}
