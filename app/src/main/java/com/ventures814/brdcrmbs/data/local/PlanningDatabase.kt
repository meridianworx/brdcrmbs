package com.ventures814.brdcrmbs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FastingEntity::class, MealEntity::class, ActivityEntity::class, NoFastEntity::class], version = 2, exportSchema = true)
abstract class PlanningDatabase : RoomDatabase() {
    abstract fun planningDao(): PlanningDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS no_fast_plans (id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, zoneId TEXT NOT NULL, notes TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_no_fast_plans_dateEpochDay ON no_fast_plans(dateEpochDay)")
            }
        }
        fun open(context: Context): PlanningDatabase = Room.databaseBuilder(
            context.applicationContext, PlanningDatabase::class.java, "brdcrmbs-planning.db"
        ).addMigrations(MIGRATION_1_2).build() // No destructive migration fallback.
    }
}
