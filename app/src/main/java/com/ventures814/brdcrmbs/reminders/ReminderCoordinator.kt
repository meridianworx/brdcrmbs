package com.ventures814.brdcrmbs.reminders

import com.ventures814.brdcrmbs.domain.PlanningRepository
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderCoordinator(
    private val repository: PlanningRepository,
    val settings: ReminderSettingsStore,
    private val backend: ReminderBackend,
    private val allowed: () -> Boolean,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
    private val displayClock: () -> com.ventures814.brdcrmbs.preferences.ClockFormat = { com.ventures814.brdcrmbs.preferences.ClockFormat() }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val reconciler = ReminderReconciler(backend)
    val error = MutableStateFlow<String?>(null)
    val pending = MutableStateFlow<List<PlanReminder>>(emptyList())
    /** Backup replacement shares delivery/reconciliation serialization without changing timing policy. */
    suspend fun withRestoreBoundary(action: suspend () -> Unit) = mutex.withLock { action() }
    /** No delivery/observer can interleave replacement and its future-only reconciliation. */
    suspend fun restoreAndReconcile(action: suspend () -> Unit): Boolean = mutex.withLock {
        action()
        runCatching { reconcileLocked(recovery = true) }
            .onFailure { error.value = "Could not synchronize reminders. Open Reminders to retry." }.isSuccess
    }
    fun start() {
        scope.launch {
            combine(repository.observeReminderPlans(), settings.state) { _, _ -> Unit }
                .onEach { reconcile() }
                .retryWhen { _, _ -> error.value = "Could not synchronize reminders. Open Reminders to retry."; delay(5_000); true }
                .collect()
        }
    }
    suspend fun reconcile(recovery: Boolean = false) = mutex.withLock {
        reconcileLocked(recovery)
    }
    private suspend fun reconcileLocked(recovery: Boolean) {
        val current = clock()
        val eligible = ReminderPolicy.eligible(repository.observeReminderPlans().first(), settings.state.value, current.zone)
        reconciler.sync(eligible, current.instant(), allowed(), recovery)
        pending.value = backend.pending().values.sortedBy { it.at }
        error.value = null
    }
    suspend fun deliver(key: String, at: java.time.Instant) = mutex.withLock {
        val current = clock()
        val data = repository.observeReminderPlans().first()
        val preferences = settings.state.value
        // Text must not change equality used by the locked pending-alarm/grace-period policy.
        val eligible = ReminderPolicy.eligible(data, preferences, current.zone)
        val displayed = ReminderPolicy.eligible(data, preferences, current.zone, displayClock()).associateBy { it.key }
        reconciler.deliver(key, at, eligible, current.instant(), allowed()) { displayed.getValue(it.key) }
        reconciler.sync(eligible, current.instant(), allowed())
        pending.value = backend.pending().values.sortedBy { it.at }
    }
    fun refresh() { scope.launch { try { reconcile() } catch (e: CancellationException) { throw e } catch (_: Exception) { error.value = "Could not synchronize reminders. Try again." } } }
    fun close() { scope.cancel() }
}
