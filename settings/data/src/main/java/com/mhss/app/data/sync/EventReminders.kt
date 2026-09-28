package com.mhss.app.data.sync

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract.Instances
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mhss.app.ui.R
import com.mhss.app.util.Constants
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Notifications for events in the "Penguin Brain" calendar, scheduled for the next 14 days. */
object EventReminders {
    private const val PREFS = "penguin_event_reminders"
    private const val KEY_CODES = "codes"
    private const val WINDOW = 14L * 24 * 60 * 60 * 1000
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_CODE = "code"

    fun reschedule(context: Context) {
        val calendar = PenguinCalendar(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        prefs.getStringSet(KEY_CODES, emptySet())!!.forEach { code ->
            pending(context, code.toInt(), null)?.let { alarm.cancel(it) }
        }
        val codes = HashSet<String>()
        val calId = try { calendar.calendarId() } catch (_: Exception) { null }
        if (calId != null) {
            val reminders = calendar.remindersByRow()
            val now = System.currentTimeMillis()
            val uri = Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, now - 24 * 60 * 60 * 1000)
                android.content.ContentUris.appendId(it, now + WINDOW)
            }.build()
            context.contentResolver.query(
                uri,
                arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.TITLE, Instances.ALL_DAY, Instances.EVENT_LOCATION),
                "${Instances.CALENDAR_ID} = ?",
                arrayOf(calId.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val mins = reminders[c.getLong(0)] ?: continue
                    val allDay = c.getInt(3) == 1
                    val begin = if (allDay) utcMidnightToLocal(c.getLong(1)) else c.getLong(1)
                    val title = c.getString(2).orEmpty().ifBlank { "Event" }
                    val time = if (allDay) "All day" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(begin))
                    val text = listOf(time, c.getString(4).orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                    mins.forEach { m ->
                        val at = begin - m * 60_000L
                        if (at > now) {
                            val code = "${c.getLong(0)}@${c.getLong(1)}@$m".hashCode()
                            val pi = pending(context, code, title to text) ?: return@forEach
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                            } else {
                                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                            }
                            codes += code.toString()
                        }
                    }
                }
            }
        }
        prefs.edit().putStringSet(KEY_CODES, codes).apply()
    }

    private fun utcMidnightToLocal(t: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = t }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    private fun pending(context: Context, code: Int, content: Pair<String, String>?): PendingIntent? {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = "com.mhss.app.EVENT_REMINDER"
            putExtra(EXTRA_CODE, code)
            content?.let { putExtra(EXTRA_TITLE, it.first); putExtra(EXTRA_TEXT, it.second) }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
                if (content == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val result = goAsync()
            Thread {
                try { EventReminders.reschedule(context) } catch (_: Exception) { } finally { result.finish() }
            }.start()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, Constants.REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(intent.getStringExtra(EventReminders.EXTRA_TITLE) ?: "Event")
            .setContentText(intent.getStringExtra(EventReminders.EXTRA_TEXT).orEmpty())
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(intent.getIntExtra(EventReminders.EXTRA_CODE, 0), notification)
    }
}
