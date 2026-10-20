package com.ventures814.brdcrmbs.data

import androidx.room.withTransaction
import com.ventures814.brdcrmbs.data.local.*
import com.ventures814.brdcrmbs.domain.*
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomPlanningRepository(private val database: PlanningDatabase) : PlanningRepository {
    private val dao = database.planningDao()

    override fun observe(range: DateRange, zone: ZoneId): Flow<PlanningData> =
        database.invalidationTracker.createFlow("fasting_events", "meal_plans", "activity_plans", "no_fast_plans")
            .map { load(range, zone) }.distinctUntilChanged()

    override suspend fun load(range: DateRange, zone: ZoneId): PlanningData = database.withTransaction {
        PlanningData(
            dao.fasting(range.startInstant(zone).toEpochMilli(), range.endInstant(zone).toEpochMilli()).map { it.model() },
            dao.meals(range.start.toEpochDay(), range.endExclusive.toEpochDay()).map { it.model() },
            dao.activities(range.start.toEpochDay(), range.endExclusive.toEpochDay()).map { it.model() },
            dao.noFast(range.start.toEpochDay(), range.endExclusive.toEpochDay()).map { it.model() }
        )
    }

    override suspend fun addFasting(event: FastingEvent) = addFastingPlan(event)

    override suspend fun getFastingPlan(id: String): FastingPlan? = database.withTransaction {
        dao.fastingById(id)?.model() ?: dao.noFastById(id)?.model()
    }

    override suspend fun addFastingPlan(plan: FastingPlan) = database.withTransaction {
        when (plan) {
            is FastingEvent -> { check(dao.noFastById(plan.id) == null); dao.insert(plan.entity()) }
            is NoFastPlan -> { check(dao.fastingById(plan.id) == null); dao.insert(plan.entity()) }
        }
    }

    override suspend fun updateFastingPlan(plan: FastingPlan) = database.withTransaction {
        val existing = checkNotNull(getFastingPlan(plan.id)) { "Plan no longer exists" }
        check(existing !is FastingEvent || existing.outcome == FastingOutcome.PLANNED) { "Recorded outcomes cannot be edited" }
        check(plan !is FastingEvent || plan.outcome == FastingOutcome.PLANNED)
        when {
            existing is FastingEvent && plan is FastingEvent -> check(dao.updateFasting(plan.id, plan.start.toEpochMilli(), plan.end.toEpochMilli(), plan.zone.id, plan.preset, plan.notes) == 1)
            existing is NoFastPlan && plan is NoFastPlan -> check(dao.update(plan.entity()) == 1)
            else -> {
                deleteFastingPlan(existing.id)
                addFastingPlan(plan) // Atomic kind conversion, retaining identity and rolling back on failure.
            }
        }
    }

    override suspend fun deleteFastingPlan(id: String) = database.withTransaction {
        val existing = checkNotNull(getFastingPlan(id)) { "Plan no longer exists" }
        when (existing) {
            is FastingEvent -> check(dao.deletePlannedFasting(id) == 1) { "Recorded outcomes cannot be deleted" }
            is NoFastPlan -> check(dao.deleteNoFast(id) == 1)
        }
    }
    override suspend fun addMeal(meal: MealPlan) = dao.insert(meal.entity())
    override suspend fun getMeal(id: String): MealPlan? = dao.mealById(id)?.model()

    override suspend fun savedMeals(): List<SavedMeal> = dao.savedMeals().map { it.model() }

    override suspend fun savePlannedMeal(meal: MealPlan, isNew: Boolean, saveForReuse: Boolean) = database.withTransaction {
        check(meal.outcome == MealOutcome.PLANNED && meal.actual == null) { "Recorded meals cannot be edited" }
        if (isNew) dao.insert(meal.entity()) else {
            check(dao.updatePlannedMeal(meal.id, meal.date.toEpochDay(), meal.time?.toNanoOfDay(), meal.text, meal.label, meal.notes) == 1) {
                "Meal no longer exists or has a recorded outcome"
            }
        }
        if (saveForReuse) {
            // Exact reusable details are deduplicated inside the same transaction as the plan.
            val existing = dao.savedMeals().any { it.text == meal.text && it.label == meal.label && it.notes == meal.notes }
            if (!existing) dao.insert(SavedMeal(text = meal.text, label = meal.label, notes = meal.notes).entity())
        }
    }

    override suspend fun deletePlannedMeal(id: String) {
        check(dao.deletePlannedMeal(id) == 1) { "Meal no longer exists or has a recorded outcome" }
    }
    override suspend fun addActivity(activity: ActivityPlan) = dao.insert(activity.entity())

    override suspend fun recordChangedMeal(id: String, actual: ActualMeal) {
        check(dao.recordChangedMeal(id, actual.text, actual.time?.toNanoOfDay(), actual.label, actual.notes) == 1) {
            "Meal does not exist: $id"
        }
    }
}
