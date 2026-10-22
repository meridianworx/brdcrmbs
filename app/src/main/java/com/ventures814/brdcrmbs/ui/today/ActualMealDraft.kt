package com.ventures814.brdcrmbs.ui.today

import com.ventures814.brdcrmbs.domain.*
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime

data class ActualMealDraft(
    val id: String, val date: LocalDate, val text: String = "",
    val label: String = "", val time: LocalTime? = null, val notes: String = ""
) : Serializable {
    fun actual() = ActualMeal(text, time, label.takeUnless { it.isEmpty() }, notes.takeUnless { it.isEmpty() })
    companion object {
        fun from(meal: MealPlan) = ActualMealDraft(meal.id, meal.date, meal.actual?.text.orEmpty(),
            meal.actual?.label.orEmpty(), meal.actual?.time, meal.actual?.notes.orEmpty())
    }
}
