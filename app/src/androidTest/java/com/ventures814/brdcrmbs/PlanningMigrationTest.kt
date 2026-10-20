package com.ventures814.brdcrmbs

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.data.RoomPlanningRepository
import com.ventures814.brdcrmbs.data.local.PlanningDatabase
import com.ventures814.brdcrmbs.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class PlanningMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun schema(version: Int): JSONObject = instrumentation.context.assets.open(
        "com.ventures814.brdcrmbs.data.local.PlanningDatabase/$version.json"
    ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

    /** Recreate actual exported schemas; no duplicated hand-maintained table definitions. */
    private fun createOldVersion(name: String, version: Int) {
        val exported = schema(version)
        instrumentation.targetContext.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null).use { db ->
            val entities = exported.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (item in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = exported.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.execSQL("INSERT INTO fasting_events VALUES ('fast', 0, 86400000, 'UTC', '24h', 'Keep', 'MISSED')")
            db.execSQL("INSERT INTO meal_plans VALUES ('meal', 0, NULL, 'Original', 'Lunch', 'Plan notes', 'CHANGED', 'Actual', NULL, 'Actual label', 'Actual notes')")
            db.execSQL("INSERT INTO activity_plans VALUES ('activity', 0, NULL, 'Walk', 'ACTIVITY', 'Keep', 'MISSED', NULL)")
            if (version == 2) db.execSQL("INSERT INTO no_fast_plans VALUES ('no-fast', 0, 'UTC', 'Keep no fast')")
            db.version = version
        }
    }

    @Test fun migrationPreservesVersionOneHistoryAndAddsEmptyNoFastTable() = verifyMigration(1)
    @Test fun migrationPreservesVersionTwoFastingAndHistoryAndAddsSavedMeals() = verifyMigration(2)

    private fun verifyMigration(version: Int) = runBlocking {
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        createOldVersion(name, version)
        val database = Room.databaseBuilder(instrumentation.targetContext, PlanningDatabase::class.java, name)
            .addMigrations(PlanningDatabase.MIGRATION_1_2, PlanningDatabase.MIGRATION_2_3).build()
        try {
            // Opening through Room executes the production migration chain and full schema validator.
            val migrated = database.openHelper.writableDatabase
            assertEquals(3, migrated.version)
            migrated.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(schema(3).getString("identityHash"), cursor.getString(0))
            }
            val tables = mutableSetOf<String>()
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            assertEquals(setOf("fasting_events", "meal_plans", "activity_plans", "no_fast_plans", "saved_meals"),
                tables - setOf("android_metadata", "room_master_table"))
            migrated.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("ok", cursor.getString(0))
            }
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val repo = RoomPlanningRepository(database)
            val data = repo.load(DateRange.day(java.time.LocalDate.ofEpochDay(0)), java.time.ZoneOffset.UTC)
            assertEquals(FastingOutcome.MISSED, data.fasting.single().outcome)
            assertEquals("Keep", data.fasting.single().notes)
            assertEquals(FastingEvent("fast", java.time.Instant.EPOCH, java.time.Instant.ofEpochMilli(86400000),
                java.time.ZoneId.of("UTC"), "24h", "Keep", FastingOutcome.MISSED), data.fasting.single())
            assertEquals("Original", data.meals.single().text)
            assertEquals("Actual", data.meals.single().actual!!.text)
            assertEquals(MealOutcome.CHANGED, data.meals.single().outcome)
            assertEquals(ActivityOutcome.MISSED, data.activities.single().outcome)
            assertEquals(MealPlan("meal", java.time.LocalDate.ofEpochDay(0), text = "Original", label = "Lunch",
                notes = "Plan notes", outcome = MealOutcome.CHANGED,
                actual = ActualMeal("Actual", label = "Actual label", notes = "Actual notes")), data.meals.single())
            if (version == 1) assertTrue(data.noFast.isEmpty()) else {
                assertEquals(NoFastPlan("no-fast", java.time.LocalDate.ofEpochDay(0), java.time.ZoneId.of("UTC"), "Keep no fast"), data.noFast.single())
            }
            assertTrue(repo.savedMeals().isEmpty())
            val planned = MealPlan(date = java.time.LocalDate.ofEpochDay(0), text = "Reusable")
            repo.savePlannedMeal(planned, isNew = true, saveForReuse = true)
            assertEquals("Reusable", repo.savedMeals().single().text)
            repo.addFastingPlan(NoFastPlan(date = java.time.LocalDate.ofEpochDay(0), zone = java.time.ZoneOffset.UTC))
            assertEquals(if (version == 1) 1 else 2, repo.load(DateRange.day(java.time.LocalDate.ofEpochDay(0)), java.time.ZoneOffset.UTC).noFast.size)
        } finally { database.close(); instrumentation.targetContext.deleteDatabase(name) }
    }
}
