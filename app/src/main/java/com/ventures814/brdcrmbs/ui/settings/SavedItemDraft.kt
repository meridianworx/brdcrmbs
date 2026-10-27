package com.ventures814.brdcrmbs.ui.settings

import com.ventures814.brdcrmbs.domain.*
import java.io.Serializable
import java.util.UUID

enum class SettingsPage { HOME, MEALS, ACTIVITIES, REMINDERS, BACKUP, PREFERENCES, DATA_MANAGEMENT }

/** Reusable library details only; no schedule, outcome or connection to a planned item. */
data class SavedItemDraft(
    val page: SettingsPage,
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val name: String = "",
    val label: String = "",
    val notes: String = "",
    val type: ActivityType = ActivityType.ACTIVITY
) : Serializable {
    init { require(page == SettingsPage.MEALS || page == SettingsPage.ACTIVITIES) }
    fun meal(): SavedMeal {
        require(page == SettingsPage.MEALS)
        return SavedMeal(id, name, label.takeUnless(String::isEmpty), notes.takeUnless(String::isEmpty))
    }
    fun activity(): SavedActivity {
        require(page == SettingsPage.ACTIVITIES)
        return SavedActivity(id, name, type, notes.takeUnless(String::isEmpty))
    }
    companion object {
        fun from(item: SavedMeal) = SavedItemDraft(SettingsPage.MEALS, item.id, false, item.text, item.label.orEmpty(), item.notes.orEmpty())
        fun from(item: SavedActivity) = SavedItemDraft(SettingsPage.ACTIVITIES, item.id, false, item.name, notes = item.notes.orEmpty(), type = item.type)
    }
}
