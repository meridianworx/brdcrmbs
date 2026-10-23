package com.ventures814.brdcrmbs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlanningDao {
    @Query("SELECT * FROM saved_meals ORDER BY text COLLATE NOCASE, id")
    fun observeSavedMeals(): kotlinx.coroutines.flow.Flow<List<SavedMealEntity>>
    @Query("SELECT * FROM saved_activities ORDER BY name COLLATE NOCASE, id")
    fun observeSavedActivities(): kotlinx.coroutines.flow.Flow<List<SavedActivityEntity>>
    @Update suspend fun updateSavedMeal(meal: SavedMealEntity): Int
    @Update suspend fun updateSavedActivity(activity: SavedActivityEntity): Int
    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteSavedMeal(id: String): Int
    @Query("DELETE FROM saved_activities WHERE id = :id")
    suspend fun deleteSavedActivity(id: String): Int
    @Query("UPDATE fasting_events SET outcome = :outcome WHERE id = :id")
    suspend fun recordFastingOutcome(id: String, outcome: String): Int
    // Retain the last actual snapshot when correcting Changed to Done/Missed.
    @Query("UPDATE meal_plans SET outcome = :outcome WHERE id = :id")
    suspend fun recordMealOutcome(id: String, outcome: String): Int
    @Query("UPDATE activity_plans SET outcome = :outcome WHERE id = :id")
    suspend fun recordActivityOutcome(id: String, outcome: String): Int
    @Insert suspend fun insert(activity: SavedActivityEntity)
    @Query("SELECT * FROM saved_activities ORDER BY name COLLATE NOCASE, id")
    suspend fun savedActivities(): List<SavedActivityEntity>
    @Query("SELECT * FROM activity_plans WHERE id = :id")
    suspend fun activityById(id: String): ActivityEntity?
    // Neither outcome nor rescheduledFromId may be rewritten by planning edits.
    @Query("""UPDATE activity_plans SET dateEpochDay = :date, timeNanoOfDay = :time,
        name = :name, type = :type, notes = :notes WHERE id = :id AND outcome = 'PLANNED'""")
    suspend fun updatePlannedActivity(id: String, date: Long, time: Long?, name: String, type: String, notes: String?): Int
    @Query("DELETE FROM activity_plans WHERE id = :id AND outcome = 'PLANNED'")
    suspend fun deletePlannedActivity(id: String): Int
    @Insert suspend fun insert(event: FastingEntity)
    @Insert suspend fun insert(meal: MealEntity)
    @Insert suspend fun insert(activity: ActivityEntity)
    @Insert suspend fun insert(plan: NoFastEntity)
    @Update suspend fun update(plan: NoFastEntity): Int
    @Insert suspend fun insert(meal: SavedMealEntity)

    @Query("SELECT * FROM saved_meals ORDER BY text COLLATE NOCASE, id")
    suspend fun savedMeals(): List<SavedMealEntity>
    @Query("SELECT * FROM meal_plans WHERE id = :id")
    suspend fun mealById(id: String): MealEntity?
    // Planning mutations must never overwrite outcome or actual-meal columns.
    @Query("""UPDATE meal_plans SET dateEpochDay = :date, timeNanoOfDay = :time,
        plannedText = :text, label = :label, notes = :notes
        WHERE id = :id AND outcome = 'PLANNED' AND actualText IS NULL""")
    suspend fun updatePlannedMeal(id: String, date: Long, time: Long?, text: String, label: String?, notes: String?): Int
    @Query("DELETE FROM meal_plans WHERE id = :id AND outcome = 'PLANNED' AND actualText IS NULL")
    suspend fun deletePlannedMeal(id: String): Int

    @Query("SELECT * FROM fasting_events WHERE id = :id")
    suspend fun fastingById(id: String): FastingEntity?
    @Query("SELECT * FROM no_fast_plans WHERE id = :id")
    suspend fun noFastById(id: String): NoFastEntity?
    @Query("SELECT * FROM no_fast_plans WHERE dateEpochDay >= :startDay AND dateEpochDay < :endDay ORDER BY dateEpochDay, id")
    suspend fun noFast(startDay: Long, endDay: Long): List<NoFastEntity>
    @Query("""UPDATE fasting_events SET startMillis = :start, endMillis = :end,
        zoneId = :zone, preset = :preset, notes = :notes WHERE id = :id AND outcome = 'PLANNED'""")
    suspend fun updateFasting(id: String, start: Long, end: Long, zone: String, preset: String?, notes: String?): Int
    @Query("DELETE FROM fasting_events WHERE id = :id AND outcome = 'PLANNED'")
    suspend fun deletePlannedFasting(id: String): Int
    @Query("DELETE FROM no_fast_plans WHERE id = :id")
    suspend fun deleteNoFast(id: String): Int

    @Query("SELECT * FROM fasting_events WHERE startMillis < :endMillis AND endMillis > :startMillis ORDER BY startMillis, id")
    suspend fun fasting(startMillis: Long, endMillis: Long): List<FastingEntity>

    @Query("SELECT * FROM meal_plans WHERE dateEpochDay >= :startDay AND dateEpochDay < :endDay ORDER BY dateEpochDay, timeNanoOfDay, id")
    suspend fun meals(startDay: Long, endDay: Long): List<MealEntity>

    @Query("SELECT * FROM activity_plans WHERE dateEpochDay >= :startDay AND dateEpochDay < :endDay ORDER BY dateEpochDay, timeNanoOfDay, id")
    suspend fun activities(startDay: Long, endDay: Long): List<ActivityEntity>

    // Preserve all original plan columns. A correction may update actual details only.
    @Query("""UPDATE meal_plans SET outcome = 'CHANGED', actualText = :text,
        actualTimeNanoOfDay = :time, actualLabel = :label, actualNotes = :notes WHERE id = :id""")
    suspend fun recordChangedMeal(id: String, text: String, time: Long?, label: String?, notes: String?): Int
}
