package com.ventures814.brdcrmbs.backup

import android.util.JsonReader
import android.util.JsonToken
import com.ventures814.brdcrmbs.domain.*
import com.ventures814.brdcrmbs.reminders.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.time.*

/** Format 1 uses ISO dates/times/instants, explicit nullable fields and stable record ordering. */
object BackupCodec {
    const val TYPE = "com.ventures814.brdcrmbs.backup"
    const val VERSION = 1
    const val MAX_BYTES = 32 * 1024 * 1024
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun <T> rows(values: List<T>, id: (T) -> String, encode: (T) -> JSONObject) = JSONArray().apply { values.sortedBy(id).forEach { put(encode(it)) } }
    private fun actual(a: ActualMeal) = obj("text" to a.text, "time" to a.time?.toString(), "label" to a.label, "notes" to a.notes)

    fun encode(snapshot: BackupSnapshot): String {
        val s = snapshot.validate()
        val data = obj(
            "fasting" to rows(s.plans.fasting, { it.id }) { obj("id" to it.id, "start" to it.start.toString(), "end" to it.end.toString(), "zone" to it.zone.id, "preset" to it.preset, "notes" to it.notes, "outcome" to it.outcome.name) },
            "noFast" to rows(s.plans.noFast, { it.id }) { obj("id" to it.id, "date" to it.date.toString(), "zone" to it.zone.id, "notes" to it.notes) },
            "meals" to rows(s.plans.meals, { it.id }) { obj("id" to it.id, "date" to it.date.toString(), "time" to it.time?.toString(), "text" to it.text, "label" to it.label, "notes" to it.notes, "outcome" to it.outcome.name, "actual" to it.actual?.let(::actual)) },
            "activities" to rows(s.plans.activities, { it.id }) { obj("id" to it.id, "date" to it.date.toString(), "time" to it.time?.toString(), "name" to it.name, "type" to it.type.name, "notes" to it.notes, "outcome" to it.outcome.name, "rescheduledFromId" to it.rescheduledFromId) },
            "savedMeals" to rows(s.savedMeals, { it.id }) { obj("id" to it.id, "text" to it.text, "label" to it.label, "notes" to it.notes) },
            "savedActivities" to rows(s.savedActivities, { it.id }) { obj("id" to it.id, "name" to it.name, "type" to it.type.name, "notes" to it.notes) }
        )
        return obj("type" to TYPE, "formatVersion" to VERSION, "createdAt" to s.createdAt.toString(),
            "databaseVersion" to 4, "appVersion" to s.appVersion, "data" to data,
            "reminderPreferences" to JSONArray(ReminderType.entries.filter { it in s.reminders.enabled }.map { it.name })).toString().also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the supported 32 MiB size." }
        }
    }

    private fun JSONObject.fields(vararg names: String): JSONObject = apply {
        require(keys().asSequence().toSet() == names.toSet()) { "Missing or unsupported backup fields." }
    }
    private fun JSONObject.text(name: String): String = get(name).let { require(it is String) { "Invalid text field: $name" }; it }
    private fun JSONObject.optional(name: String): String? = if (get(name) == JSONObject.NULL) null else text(name)
    private fun JSONObject.integer(name: String): Int = get(name).let {
        require(it is Long && it in Int.MIN_VALUE..Int.MAX_VALUE) { "Invalid numeric metadata." }; it.toInt()
    }
    private fun <T> JSONObject.list(name: String, read: (JSONObject) -> T): List<T> = getJSONArray(name).let { a ->
        require(a.length() <= 100_000) { "Too many backup records." }
        (0 until a.length()).map { read(a.getJSONObject(it)) }
    }
    private fun time(value: String?) = value?.let(LocalTime::parse)
    private fun readActual(a: JSONObject): ActualMeal {
        a.fields("text", "time", "label", "notes")
        return ActualMeal(a.text("text"), time(a.optional("time")), a.optional("label"), a.optional("notes"))
    }

    fun decode(text: String): BackupSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the supported 32 MiB size." }
        val root = JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val parsed = readValue(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT && parsed is JSONObject) { "Invalid backup document." }
            parsed
        }.fields("type", "formatVersion", "createdAt", "databaseVersion", "appVersion", "data", "reminderPreferences")
        require(root.text("type") == TYPE) { "This is not a brdcrmbs backup." }
        require(root.integer("formatVersion") == VERSION && root.integer("databaseVersion") == 4) { "Unsupported backup version." }
        val d = root.getJSONObject("data").fields("fasting", "noFast", "meals", "activities", "savedMeals", "savedActivities")
        val plans = PlanningData(
            fasting = d.list("fasting") { r ->
                r.fields("id", "start", "end", "zone", "preset", "notes", "outcome")
                FastingEvent(r.text("id"), Instant.parse(r.text("start")), Instant.parse(r.text("end")), ZoneId.of(r.text("zone")), r.optional("preset"), r.optional("notes"), FastingOutcome.valueOf(r.text("outcome")))
            },
            noFast = d.list("noFast") { r ->
                r.fields("id", "date", "zone", "notes")
                NoFastPlan(r.text("id"), LocalDate.parse(r.text("date")), ZoneId.of(r.text("zone")), r.optional("notes"))
            },
            meals = d.list("meals") { r ->
                r.fields("id", "date", "time", "text", "label", "notes", "outcome", "actual")
                MealPlan(r.text("id"), LocalDate.parse(r.text("date")), time(r.optional("time")), r.text("text"), r.optional("label"), r.optional("notes"), MealOutcome.valueOf(r.text("outcome")),
                    if (r.get("actual") == JSONObject.NULL) null else readActual(r.getJSONObject("actual")))
            },
            activities = d.list("activities") { r ->
                r.fields("id", "date", "time", "name", "type", "notes", "outcome", "rescheduledFromId")
                ActivityPlan(r.text("id"), LocalDate.parse(r.text("date")), time(r.optional("time")), r.text("name"), ActivityType.valueOf(r.text("type")), r.optional("notes"), ActivityOutcome.valueOf(r.text("outcome")), r.optional("rescheduledFromId"))
            }
        )
        val meals = d.list("savedMeals") { r -> r.fields("id", "text", "label", "notes"); SavedMeal(r.text("id"), r.text("text"), r.optional("label"), r.optional("notes")) }
        val activities = d.list("savedActivities") { r -> r.fields("id", "name", "type", "notes"); SavedActivity(r.text("id"), r.text("name"), ActivityType.valueOf(r.text("type")), r.optional("notes")) }
        val preferences = root.getJSONArray("reminderPreferences").let { a ->
            require(a.length() <= ReminderType.entries.size)
            val names = (0 until a.length()).map { a.get(it).let { value -> require(value is String); ReminderType.valueOf(value) } }
            require(names.toSet().size == names.size)
            ReminderPreferences(names.toSet())
        }
        return BackupSnapshot(Instant.parse(root.text("createdAt")), plans, meals, activities, preferences, root.integer("appVersion")).validate()
    }

    /** Strict syntax, duplicate-name rejection, bounded nesting and no trailing content. */
    private fun readValue(reader: JsonReader, depth: Int): Any {
        require(depth <= 16) { "Backup is nested too deeply." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) { val key = reader.nextName(); require(!has(key)) { "Duplicate backup field." }; put(key, readValue(reader, depth + 1)) }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) { require(length() < 100_000); put(readValue(reader, depth + 1)) }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().toLong() // Format 1 has integer metadata only.
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> throw IllegalArgumentException("Invalid backup value.")
        }
    }
}
