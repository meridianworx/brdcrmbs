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
        database.invalidationTracker.createFlow("fasting_events", "meal_plans", "activity_plans")
            .map { load(range, zone) }.distinctUntilChanged()

    override suspend fun load(range: DateRange, zone: ZoneId): PlanningData = database.withTransaction {
        PlanningData(
            dao.fasting(range.startInstant(zone).toEpochMilli(), range.endInstant(zone).toEpochMilli()).map { it.model() },
            dao.meals(range.start.toEpochDay(), range.endExclusive.toEpochDay()).map { it.model() },
            dao.activities(range.start.toEpochDay(), range.endExclusive.toEpochDay()).map { it.model() }
        )
    }

    override suspend fun addFasting(event: FastingEvent) = dao.insert(event.entity())
    override suspend fun addMeal(meal: MealPlan) = dao.insert(meal.entity())
    override suspend fun addActivity(activity: ActivityPlan) = dao.insert(activity.entity())

    override suspend fun recordChangedMeal(id: String, actual: ActualMeal) {
        check(dao.recordChangedMeal(id, actual.text, actual.time?.toNanoOfDay(), actual.label, actual.notes) == 1) {
            "Meal does not exist: $id"
        }
    }
}
