package com.ventures814.brdcrmbs.ui.meals

import com.ventures814.brdcrmbs.domain.*
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class MealDraft(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val date: LocalDate,
    val time: LocalTime? = null,
    val text: String = "",
    val label: String = "",
    val notes: String = "",
    val saveForReuse: Boolean = false,
    val editable: Boolean = true
) : Serializable {
    fun toPlan(): MealPlan {
        check(editable) { "Recorded meals cannot be edited." }
        require(text.isNotBlank()) { "Meal is required." }
        return MealPlan(id, date, time, text, label.takeUnless { it.isEmpty() }, notes.takeUnless { it.isEmpty() })
    }

    fun select(saved: SavedMeal) = copy(text = saved.text, label = saved.label.orEmpty(), notes = saved.notes.orEmpty())

    companion object {
        fun from(meal: MealPlan) = MealDraft(
            meal.id, false, meal.date, meal.time, meal.text, meal.label.orEmpty(), meal.notes.orEmpty(),
            editable = meal.outcome == MealOutcome.PLANNED && meal.actual == null
        )
    }
}
