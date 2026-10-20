package com.ventures814.brdcrmbs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FastingEntity::class, MealEntity::class, ActivityEntity::class, NoFastEntity::class, SavedMealEntity::class, SavedActivityEntity::class], version = 4, exportSchema = true)
abstract class PlanningDatabase : RoomDatabase() {
    abstract fun planningDao(): PlanningDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_activities (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, notes TEXT, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_meals (id TEXT NOT NULL, text TEXT NOT NULL, label TEXT, notes TEXT, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS no_fast_plans (id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, zoneId TEXT NOT NULL, notes TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_no_fast_plans_dateEpochDay ON no_fast_plans(dateEpochDay)")
            }
        }
        fun open(context: Context): PlanningDatabase = Room.databaseBuilder(
            context.applicationContext, PlanningDatabase::class.java, "brdcrmbs-planning.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build() // No destructive migration fallback.
    }
}
