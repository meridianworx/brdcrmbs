package com.ventures814.brdcrmbs.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ventures814.brdcrmbs.MainActivity
import com.ventures814.brdcrmbs.R
import java.time.Instant
import org.json.JSONObject

// KTX edit returns Unit; these commits must expose failure to the coordinator.
@SuppressLint("UseKtx")
class AndroidReminderBackend(private val context: Context, runtimeName: String = "reminder_runtime") : ReminderBackend {
    private val ledger = context.getSharedPreferences(runtimeName, Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    init { createChannel() }
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Plan reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for fasting, meals and activities you planned"
            })
    }
    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)

    override fun pending(): Map<String, PlanReminder> = ledger.all.filterKeys { it.startsWith("pending:") }.map { (key, value) ->
        val json = JSONObject(value as String)
        val id = key.removePrefix("pending:")
        id to PlanReminder(id, ReminderType.valueOf(json.getString("type")), Instant.ofEpochMilli(json.getLong("at")), json.getString("title"), json.getString("body"))
    }.toMap()
    override fun delivered(): Map<String, Instant> = ledger.all.filterKeys { it.startsWith("delivered:") }
        .mapKeys { it.key.removePrefix("delivered:") }.mapValues { Instant.ofEpochMilli(it.value as Long) }

    fun alarmIntent(key: String, at: Instant): Intent = Intent(context, ReminderReceiver::class.java)
        .setAction(ACTION_FIRE).setData(Uri.Builder().scheme("brdcrmbs-reminder").authority("plan").appendPath(key).build())
        .putExtra("key", key).putExtra("at", at.toEpochMilli())
    private fun alarm(reminder: PlanReminder) = PendingIntent.getBroadcast(context, 0, alarmIntent(reminder.key, reminder.at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun schedule(reminder: PlanReminder) {
        val json = JSONObject().put("type", reminder.type.name).put("at", reminder.at.toEpochMilli()).put("title", reminder.title).put("body", reminder.body)
        // Journal before the OS call so recovery can cancel/reinstall after interrupted scheduling.
        check(ledger.edit().putString("pending:${reminder.key}", json.toString()).commit())
        try { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at.toEpochMilli(), alarm(reminder)) }
        catch (failure: Exception) { check(ledger.edit().remove("pending:${reminder.key}").commit()); throw failure }
    }
    override fun cancel(key: String) {
        val intent = PendingIntent.getBroadcast(context, 0, alarmIntent(key, Instant.EPOCH), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (intent != null) { alarms.cancel(intent); intent.cancel() }
        check(ledger.edit().remove("pending:$key").commit())
    }
    override fun markDelivered(reminder: PlanReminder) {
        check(ledger.edit().putLong("delivered:${reminder.key}", reminder.at.toEpochMilli()).commit())
    }
    fun tapIntent(): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_TODAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    fun notification(reminder: PlanReminder): Notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(reminder.title).setContentText(reminder.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body)).setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(PendingIntent.getActivity(context, 0, tapIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()
    override fun post(reminder: PlanReminder) {
        if (allowed()) notifications.notify(reminder.key, 0, notification(reminder))
    }
    companion object {
        const val CHANNEL = "plan_reminders"
        const val ACTION_FIRE = "com.ventures814.brdcrmbs.REMINDER"
        const val ACTION_OPEN_TODAY = "com.ventures814.brdcrmbs.OPEN_TODAY"
    }
}
