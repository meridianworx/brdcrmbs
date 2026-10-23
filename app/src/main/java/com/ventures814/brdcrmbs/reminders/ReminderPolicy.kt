package com.ventures814.brdcrmbs.reminders

import com.ventures814.brdcrmbs.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

enum class ReminderType { FAST_START, FAST_END, MEAL, ACTIVITY }
data class ReminderPreferences(val enabled: Set<ReminderType> = emptySet()) {
    fun with(type: ReminderType, on: Boolean) = copy(enabled = if (on) enabled + type else enabled - type)
}
data class PlanReminder(val key: String, val type: ReminderType, val at: Instant, val title: String, val body: String)

object ReminderPolicy {
    // Inexact alarms may arrive late, but never replay stale day-old plans.
    val maxDelay: Duration = Duration.ofHours(1)
    fun eligible(data: PlanningData, preferences: ReminderPreferences, zone: ZoneId): List<PlanReminder> = buildList {
        fun addIf(type: ReminderType, id: String, at: Instant, title: String, body: String) {
            if (type in preferences.enabled) add(PlanReminder("${type.name}:$id", type, at, title, body))
        }
        data.fasting.filter { it.outcome == FastingOutcome.PLANNED }.forEach {
            val label = it.preset ?: "Custom"
            val end = it.end.atZone(it.zone).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
            addIf(ReminderType.FAST_START, it.id, it.start, "Fasting starts now", "$label · Ends $end · ${it.zone.id}")
            addIf(ReminderType.FAST_END, it.id, it.end, "Fasting complete", "Your planned $label fast ends now.")
        }
        data.meals.filter { it.outcome == MealOutcome.PLANNED }.forEach {
            localInstant(it.date, it.time, zone)?.let { at -> addIf(ReminderType.MEAL, it.id, at, "Planned meal", it.text) }
        }
        data.activities.filter { it.outcome == ActivityOutcome.PLANNED }.forEach {
            localInstant(it.date, it.time, zone)?.let { at -> addIf(ReminderType.ACTIVITY, it.id, at,
                if (it.type == ActivityType.WORKOUT) "Planned workout" else "Planned activity", it.name) }
        }
    }.sortedWith(compareBy({ it.at }, { it.key }))

    private fun localInstant(date: LocalDate, time: LocalTime?, zone: ZoneId): Instant? {
        val local = date.atTime(time ?: return null)
        // Match fasting input: gaps are invalid, repeated times use the earlier offset.
        val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
        return local.toInstant(offset).let { Instant.ofEpochMilli(it.toEpochMilli()) }
    }
}

interface ReminderBackend {
    fun pending(): Map<String, PlanReminder>
    fun delivered(): Map<String, Instant>
    fun schedule(reminder: PlanReminder)
    fun cancel(key: String)
    fun post(reminder: PlanReminder)
    fun markDelivered(reminder: PlanReminder)
}

/** Serialized by the coordinator; deterministic policy also works with an in-memory backend. */
class ReminderReconciler(private val backend: ReminderBackend) {
    // Bound native registrations; remaining persisted plans fill the queue as alarms are consumed.
    // This leaves ample room below Android's per-app alarm cap, with no calendar horizon.
    companion object { const val CAPACITY = 128 }
    fun sync(eligible: List<PlanReminder>, now: Instant, allowed: Boolean, recovery: Boolean = false) {
        val old = backend.pending()
        val delivered = backend.delivered()
        val desired = if (!allowed) emptyMap() else eligible.filter {
            delivered[it.key] != it.at && (it.at > now || (!recovery && old[it.key] == it && now <= it.at.plus(ReminderPolicy.maxDelay)))
        }.sortedWith(compareBy({ it.at }, { it.key })).take(CAPACITY).associateBy { it.key }
        old.keys.filter { it !in desired }.forEach(backend::cancel)
        desired.values.forEach { if (it.at > now && (recovery || old[it.key] != it)) backend.schedule(it) }
    }

    fun deliver(key: String, trigger: Instant, eligible: List<PlanReminder>, now: Instant, allowed: Boolean): Boolean {
        val pending = backend.pending()[key] ?: return false
        if (pending.at != trigger) return false // A replaced alarm must not cancel its replacement.
        val current = eligible.firstOrNull { it.key == key && it.at == trigger }
        if (!allowed || current == null || now < trigger || now > trigger.plus(ReminderPolicy.maxDelay) || backend.delivered()[key] == trigger) {
            backend.cancel(key); return false
        }
        // Record consumption first: process death cannot cause a duplicate notification.
        backend.markDelivered(current)
        backend.post(current)
        backend.cancel(key)
        return true
    }
}
