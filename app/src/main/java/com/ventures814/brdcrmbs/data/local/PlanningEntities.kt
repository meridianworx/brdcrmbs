package com.ventures814.brdcrmbs.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ventures814.brdcrmbs.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Entity(tableName = "no_fast_plans", indices = [Index("dateEpochDay")])
data class NoFastEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val zoneId: String,
    val notes: String?
)

internal fun NoFastPlan.entity() = NoFastEntity(id, date.toEpochDay(), zone.id, notes)
internal fun NoFastEntity.model() = NoFastPlan(id, LocalDate.ofEpochDay(dateEpochDay), ZoneId.of(zoneId), notes)

@Entity(tableName = "fasting_events", indices = [Index("startMillis"), Index("endMillis")])
data class FastingEntity(
    @PrimaryKey val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val zoneId: String,
    val preset: String?,
    val notes: String?,
    val outcome: String
)

@Entity(tableName = "meal_plans", indices = [Index("dateEpochDay")])
data class MealEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val timeNanoOfDay: Long?,
    val plannedText: String,
    val label: String?,
    val notes: String?,
    val outcome: String,
    val actualText: String?,
    val actualTimeNanoOfDay: Long?,
    val actualLabel: String?,
    val actualNotes: String?
)

@Entity(
    tableName = "activity_plans",
    indices = [Index("dateEpochDay"), Index("rescheduledFromId")],
    foreignKeys = [ForeignKey(
        entity = ActivityEntity::class, parentColumns = ["id"],
        childColumns = ["rescheduledFromId"], onDelete = ForeignKey.NO_ACTION
    )]
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val timeNanoOfDay: Long?,
    val name: String,
    val type: String,
    val notes: String?,
    val outcome: String,
    val rescheduledFromId: String?
)

internal fun FastingEvent.entity() = FastingEntity(
    id, start.toEpochMilli(), end.toEpochMilli(), zone.id, preset, notes, outcome.name
)
internal fun FastingEntity.model() = FastingEvent(
    id, Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis),
    ZoneId.of(zoneId), preset, notes, FastingOutcome.valueOf(outcome)
)
internal fun MealPlan.entity() = MealEntity(
    id, date.toEpochDay(), time?.toNanoOfDay(), text, label, notes, outcome.name,
    actual?.text, actual?.time?.toNanoOfDay(), actual?.label, actual?.notes
)
internal fun MealEntity.model() = MealPlan(
    id, LocalDate.ofEpochDay(dateEpochDay), timeNanoOfDay?.let(LocalTime::ofNanoOfDay),
    plannedText, label, notes, MealOutcome.valueOf(outcome),
    actualText?.let { ActualMeal(it, actualTimeNanoOfDay?.let(LocalTime::ofNanoOfDay), actualLabel, actualNotes) }
)
internal fun ActivityPlan.entity() = ActivityEntity(
    id, date.toEpochDay(), time?.toNanoOfDay(), name, type.name, notes, outcome.name, rescheduledFromId
)
internal fun ActivityEntity.model() = ActivityPlan(
    id, LocalDate.ofEpochDay(dateEpochDay), timeNanoOfDay?.let(LocalTime::ofNanoOfDay),
    name, ActivityType.valueOf(type), notes, ActivityOutcome.valueOf(outcome), rescheduledFromId
)
