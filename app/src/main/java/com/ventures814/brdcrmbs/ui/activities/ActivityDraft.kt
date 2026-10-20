package com.ventures814.brdcrmbs.ui.activities

import com.ventures814.brdcrmbs.domain.*
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class ActivitySource { BAKAL_GYM, SAVED_ACTIVITY, NEW_CUSTOM }

data class ActivityDraft(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val date: LocalDate,
    val time: LocalTime? = null,
    val name: String = "",
    val type: ActivityType = ActivityType.ACTIVITY,
    val notes: String = "",
    val saveForReuse: Boolean = false,
    val editable: Boolean = true,
    val rescheduledFromId: String? = null,
    val source: ActivitySource = ActivitySource.NEW_CUSTOM
) : Serializable {
    fun toPlan(): ActivityPlan {
        check(editable) { "Recorded activities cannot be edited." }
        require(name.isNotBlank()) { "Name is required." }
        return ActivityPlan(id, date, time, name, type, notes.takeUnless { it.isEmpty() }, rescheduledFromId = rescheduledFromId)
    }

    // Sources are entry methods; the durable reference is the user's name/type/notes.
    fun bakalGym() = copy(source = ActivitySource.BAKAL_GYM, type = ActivityType.WORKOUT)
    fun select(saved: SavedActivity) = copy(name = saved.name, type = saved.type, notes = saved.notes.orEmpty(), source = ActivitySource.SAVED_ACTIVITY)

    companion object {
        fun from(plan: ActivityPlan) = ActivityDraft(
            plan.id, false, plan.date, plan.time, plan.name, plan.type, plan.notes.orEmpty(),
            editable = plan.outcome == ActivityOutcome.PLANNED, rescheduledFromId = plan.rescheduledFromId
        )
    }
}
