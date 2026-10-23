package com.ventures814.brdcrmbs.reminders

import android.app.job.*
import android.content.*
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import java.time.Instant
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderBackend.ACTION_FIRE) return
        val key = intent.getStringExtra("key") ?: return
        if (!intent.hasExtra("at")) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { (context.applicationContext as BrdcrmbsApplication).reminders.deliver(key, Instant.ofEpochMilli(intent.getLongExtra("at", 0))) }
            } catch (_: Exception) { ReminderRecoveryReceiver.enqueue(context) }
            finally { result.finish() }
        }
    }
}

class ReminderRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) enqueue(context)
    }
    companion object {
        fun enqueue(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(7401, ComponentName(context, ReminderRecoveryJob::class.java))
                .setOverrideDeadline(0).build())
        }
    }
}

class ReminderRecoveryJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO).launch {
            var retry = false
            try { (application as BrdcrmbsApplication).reminders.reconcile(recovery = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { retry = true }
            finally { if (isActive) jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
}
