package com.ventures814.brdcrmbs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlanningDao {
    @Insert suspend fun insert(event: FastingEntity)
    @Insert suspend fun insert(meal: MealEntity)
    @Insert suspend fun insert(activity: ActivityEntity)

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
