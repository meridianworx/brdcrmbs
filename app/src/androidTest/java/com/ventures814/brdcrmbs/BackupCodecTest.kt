package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    @Test fun deterministicVersionedCompleteRoundTripPreservesEveryHistoricalField() {
        val original = richBackupSnapshot()
        val encoded = BackupCodec.encode(original)
        assertEquals(encoded, BackupCodec.encode(original))
        val metadata = JSONObject(encoded)
        assertEquals(BackupCodec.TYPE, metadata.getString("type")); assertEquals(1, metadata.getInt("formatVersion"))
        assertEquals(4, metadata.getInt("databaseVersion")); assertEquals(original.createdAt.toString(), metadata.getString("createdAt"))
        val restored = BackupCodec.decode(encoded)
        assertEquals(encoded, BackupCodec.encode(restored))
        assertEquals(original.plans.fasting.toSet(), restored.plans.fasting.toSet())
        assertEquals(original.plans.noFast, restored.plans.noFast)
        assertEquals(original.plans.meals.toSet(), restored.plans.meals.toSet())
        assertEquals(original.plans.activities.toSet(), restored.plans.activities.toSet())
        assertEquals(original.savedMeals, restored.savedMeals); assertEquals(original.savedActivities, restored.savedActivities)
        assertEquals(original.reminders, restored.reminders)
        val changed = restored.plans.meals.single { it.outcome == MealOutcome.CHANGED }
        assertEquals("Original planned CHANGED", changed.text); assertEquals("Actual fish ☺", changed.actual!!.text)
        assertEquals(123456789, changed.actual!!.time!!.nano)
        assertNotNull(restored.plans.meals.single { it.outcome == MealOutcome.DONE }.actual)
        assertEquals(72, Duration.between(restored.plans.fasting.first().start, restored.plans.fasting.first().end).toHours().toInt())
    }
    @Test fun emptyBackupRoundTrips() {
        val empty = BackupSnapshot(Instant.EPOCH)
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }
    @Test fun malformedWrongIdentityUnsupportedMetadataAndTrailingOrDuplicateFieldsAreRejected() {
        val valid = BackupCodec.encode(richBackupSnapshot())
        val cases = listOf("", "not json", "[]", "{", valid + " {}", valid.replaceFirst("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"),
            JSONObject(valid).put("type", "other").toString(), JSONObject(valid).put("formatVersion", 2).toString(),
            JSONObject(valid).put("databaseVersion", 5).toString(), JSONObject(valid).put("formatVersion", "1").toString(),
            JSONObject(valid).put("createdAt", "bad").toString(), JSONObject(valid).put("appVersion", -1).toString(),
            JSONObject(valid).put("unknown", "field").toString())
        cases.forEach { assertTrue("Invalid file accepted", runCatching { BackupCodec.decode(it) }.isFailure) }
    }
    @Test fun inconsistentRecordsEnumsPrecisionAndHistoricalLinksAreRejected() {
        val valid = BackupCodec.encode(richBackupSnapshot())
        fun changed(edit: (JSONObject) -> Unit) = JSONObject(valid).also { edit(it.getJSONObject("data")) }.toString()
        val invalid = listOf(
            changed { val rows = it.getJSONArray("fasting"); rows.put(rows.getJSONObject(0)) },
            changed { it.getJSONArray("fasting").getJSONObject(0).put("end", "1900-01-01T00:00:00Z") },
            changed { it.getJSONArray("fasting").getJSONObject(0).put("start", "2026-10-31T19:00:00.000000001Z") },
            changed { it.getJSONArray("fasting").getJSONObject(0).put("zone", "No/SuchZone") },
            changed { it.getJSONArray("meals").getJSONObject(0).put("outcome", "UNKNOWN") },
            changed { it.getJSONArray("activities").getJSONObject(0).put("rescheduledFromId", "missing") },
            changed { it.getJSONArray("savedMeals").getJSONObject(0).put("text", " ") },
            changed { it.getJSONArray("meals").getJSONObject(0).put("time", "25:00") })
        invalid.forEach { assertTrue("Inconsistent backup accepted", runCatching { BackupCodec.decode(it) }.isFailure) }
    }
}
