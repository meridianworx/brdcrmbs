package com.ventures814.brdcrmbs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FastingEntity::class, MealEntity::class, ActivityEntity::class], version = 1, exportSchema = true)
abstract class PlanningDatabase : RoomDatabase() {
    abstract fun planningDao(): PlanningDao

    companion object {
        fun open(context: Context): PlanningDatabase = Room.databaseBuilder(
            context.applicationContext, PlanningDatabase::class.java, "brdcrmbs-planning.db"
        ).build() // No destructive fallback: future versions require an explicit migration.
    }
}
