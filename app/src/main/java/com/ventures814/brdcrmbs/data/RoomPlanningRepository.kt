package com.ventures814.brdcrmbs.data

import androidx.room.withTransaction
import com.ventures814.brdcrmbs.data.local.*
import com.ventures814.brdcrmbs.domain.*
import java.time.ZoneId
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomPlanningRepository(private val database: PlanningDatabase) : PlanningRepository {
    private val dao = database.planningDao()

    override suspend fun copyDay(source: LocalDate, destinations: Set<LocalDate>, zone: ZoneId): CopyResult = database.withTransaction {
        require(destinations.isNotEmpty() && source !in destinations) { "Choose a destination other than the source day." }
        val loaded = load(DateRange.day(source), zone)
        val snapshot = loaded.copy(fasting = loaded.fasting.filter { it.start.atZone(zone).toLocalDate() == source })
        // Materialize all candidates before inserting. A DST error cancels the entire request.
        val copies = destinations.sorted().map { snapshot.shiftedPlans(ChronoUnit.DAYS.between(source, it)) }
        insertCopies(copies)
    }

    override suspend fun repeatLastWeek(weekContaining: LocalDate, zone: ZoneId): CopyResult = database.withTransaction {
        val week = DateRange.weekContaining(weekContaining)
        val previous = DateRange(week.start.minusWeeks(1), week.start)
        // Own an event by its start day, rather than duplicating ongoing events from earlier weeks.
        val loaded = load(previous, zone)
        val snapshot = loaded.copy(fasting = loaded.fasting.filter {
            val date = it.start.atZone(zone).toLocalDate()
            date >= previous.start && date < previous.endExclusive
        })
        insertCopies(listOf(snapshot.shiftedPlans(7)))
    }

    private suspend fun insertCopies(batches: List<PlanningData>): CopyResult {
        var added = 0
        val fastingKeys = mutableSetOf<FastingEvent>()
        val mealKeys = mutableSetOf<MealPlan>()
        val activityKeys = mutableSetOf<ActivityPlan>()
        val noFastKeys = mutableSetOf<NoFastPlan>()
        for (batch in batches) {
            for (event in batch.fasting) {
                // Query its exact instant range, including events starting outside destination dates.
                fastingKeys.addAll(dao.fasting(event.start.toEpochMilli(), event.end.toEpochMilli())
                    .map { it.model() }.filter { it.outcome == FastingOutcome.PLANNED }.map { it.copyKey() })
                if (fastingKeys.add(event.copyKey())) { dao.insert(event.entity()); added++ }
            }
            for (meal in batch.meals) {
                mealKeys.addAll(dao.meals(meal.date.toEpochDay(), meal.date.plusDays(1).toEpochDay())
                    .map { it.model() }.filter { it.outcome == MealOutcome.PLANNED }.map { it.copyKey() })
                if (mealKeys.add(meal.copyKey())) { dao.insert(meal.entity()); added++ }
            }
            for (activity in batch.activities) {
                activityKeys.addAll(dao.activities(activity.date.toEpochDay(), activity.date.plusDays(1).toEpochDay())
                    .map { it.model() }.filter { it.outcome == ActivityOutcome.PLANNED }.map { it.copyKey() })
                if (activityKeys.add(activity.copyKey())) { dao.insert(activity.entity()); added++ }
            }
            for (plan in batch.noFast) {
                noFastKeys.addAll(dao.noFast(plan.date.toEpochDay(), plan.date.plusDays(1).toEpochDay()).map { it.model().copyKey() })
                if (noFastKeys.add(plan.copyKey())) { dao.insert(plan.entity()); added++ }
            }
        }
        return CopyResult(added, batches.sumOf { it.recordCount } - added)
    }

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
    override suspend fun getActivity(id: String): ActivityPlan? = dao.activityById(id)?.model()
    override suspend fun savedActivities(): List<SavedActivity> = dao.savedActivities().map { it.model() }

    override suspend fun savePlannedActivity(activity: ActivityPlan, isNew: Boolean, saveForReuse: Boolean) = database.withTransaction {
        check(activity.outcome == ActivityOutcome.PLANNED) { "Recorded activities cannot be edited" }
        if (isNew) {
            check(activity.rescheduledFromId == null) { "Rescheduling is not a planning create operation" }
            dao.insert(activity.entity())
        } else {
            val existing = checkNotNull(dao.activityById(activity.id))
            check(existing.rescheduledFromId == activity.rescheduledFromId) { "Planning cannot change historical linkage" }
            check(dao.updatePlannedActivity(activity.id, activity.date.toEpochDay(), activity.time?.toNanoOfDay(), activity.name, activity.type.name, activity.notes) == 1) {
                "Activity no longer exists or has a recorded outcome"
            }
        }
        if (saveForReuse && dao.savedActivities().none { it.name == activity.name && it.type == activity.type.name && it.notes == activity.notes }) {
            dao.insert(SavedActivity(name = activity.name, type = activity.type, notes = activity.notes).entity())
        }
    }

    override suspend fun deletePlannedActivity(id: String) {
        check(dao.deletePlannedActivity(id) == 1) { "Activity no longer exists or has a recorded outcome" }
    }

    override suspend fun recordChangedMeal(id: String, actual: ActualMeal) {
        check(dao.recordChangedMeal(id, actual.text, actual.time?.toNanoOfDay(), actual.label, actual.notes) == 1) {
            "Meal does not exist: $id"
        }
    }
}
