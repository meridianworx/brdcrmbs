package com.ventures814.brdcrmbs.domain

import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface PlanningRepository {
    fun observe(range: DateRange, zone: ZoneId): Flow<PlanningData>
    suspend fun load(range: DateRange, zone: ZoneId): PlanningData
    // Insert-only APIs deliberately reject duplicate IDs rather than overwriting history.
    suspend fun addFasting(event: FastingEvent)
    suspend fun getFastingPlan(id: String): FastingPlan?
    suspend fun addFastingPlan(plan: FastingPlan)
    suspend fun updateFastingPlan(plan: FastingPlan)
    suspend fun deleteFastingPlan(id: String)
    suspend fun addMeal(meal: MealPlan)
    suspend fun getMeal(id: String): MealPlan?
    suspend fun savePlannedMeal(meal: MealPlan, isNew: Boolean, saveForReuse: Boolean = false)
    suspend fun deletePlannedMeal(id: String)
    suspend fun savedMeals(): List<SavedMeal>
    suspend fun addActivity(activity: ActivityPlan)
    suspend fun getActivity(id: String): ActivityPlan?
    suspend fun savePlannedActivity(activity: ActivityPlan, isNew: Boolean, saveForReuse: Boolean = false)
    suspend fun deletePlannedActivity(id: String)
    suspend fun savedActivities(): List<SavedActivity>
    suspend fun recordChangedMeal(id: String, actual: ActualMeal)
}
