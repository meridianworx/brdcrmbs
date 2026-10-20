package com.ventures814.brdcrmbs.domain

import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface PlanningRepository {
    fun observe(range: DateRange, zone: ZoneId): Flow<PlanningData>
    suspend fun load(range: DateRange, zone: ZoneId): PlanningData
    // Insert-only APIs deliberately reject duplicate IDs rather than overwriting history.
    suspend fun addFasting(event: FastingEvent)
    suspend fun addMeal(meal: MealPlan)
    suspend fun addActivity(activity: ActivityPlan)
    suspend fun recordChangedMeal(id: String, actual: ActualMeal)
}
