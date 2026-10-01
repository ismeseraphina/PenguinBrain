package com.mhss.app.data.clock

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import com.mhss.app.data.sync.EventReminderReceiver
import com.mhss.app.data.sync.EventReminders
import com.mhss.app.database.MyBrainDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Clock (lite) for the Android app, same rules as the website:
 * events, task deadlines and routines only notify and offer START, the timer never starts by itself.
 */
data class ClockTimer(
    val label: String,
    val kind: String,
    val duration: Long,
    val startedAt: Long,
    val elapsed: Long,
    val finished: Boolean
) {
    fun elapsedAt(now: Long) = elapsed + if (startedAt > 0) now - startedAt else 0L
    fun remainingAt(now: Long) = (duration - elapsedAt(now)).coerceAtLeast(0L)
    val running get() = startedAt > 0 && !finished
}

data class ClockCard(
    val key: String,
    val kind: String, // event-live, event-next, task, routine
    val title: String,
    val detail: String,
    val minutes: Int,
    val at: Long,
    val end: Long = 0L,
    val color: Int = 0
)

data class Routine(val id: String, val name: String, val hour: Int, val minute: Int, val minutes: Int, val days: Set<Int>)

object PenguinClock {
    private const val PREFS = "penguin_clock"
    private const val MIN = 60_000L
    private const val TIMER_CODE = 7_310_001
    const val FOCUS_LEAD_MIN = 180
    const val FOCUS_MINUTES = 50

    // Calendar.DAY_OF_WEEK values: 1 = Sunday … 7 = Saturday
    private val everyDay = (1..7).toSet()
    private val weekdays = (2..6).toSet()
    val routines = listOf(
        Routine("r-lunch", "Lunch", 12, 30, 50, everyDay),
        Routine("r-eyes", "Eye break & walk", 16, 0, 10, weekdays),
        Routine("r-dinner", "Dinner", 19, 0, 50, everyDay),
        Routine("r-shutdown", "Evening shutdown", 22, 30, 10, everyDay),
    )

    private val _timer = MutableStateFlow<ClockTimer?>(null)
    val timer: StateFlow<ClockTimer?> = _timer.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.contains("duration")) {
            _timer.value = ClockTimer(
                p.getString("label", "").orEmpty(), p.getString("kind", "manual").orEmpty(),
                p.getLong("duration", 0), p.getLong("startedAt", 0), p.getLong("elapsed", 0), p.getBoolean("finished", false)
            )
        }
    }

    private fun save(context: Context, t: ClockTimer?) {
        _timer.value = t
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        if (t != null) {
            e.putString("label", t.label).putString("kind", t.kind).putLong("duration", t.duration)
                .putLong("startedAt", t.startedAt).putLong("elapsed", t.elapsed).putBoolean("finished", t.finished)
        }
        e.apply()
        scheduleEnd(context, t)
    }

    fun start(context: Context, label: String, minutes: Int, kind: String = "manual") =
        save(context, ClockTimer(label, kind, minutes.coerceAtLeast(1) * MIN, System.currentTimeMillis(), 0L, false))

    fun pause(context: Context) {
        val t = _timer.value ?: return
        if (t.running) save(context, t.copy(elapsed = t.elapsedAt(System.currentTimeMillis()), startedAt = 0L))
    }

    fun resume(context: Context) {
        val t = _timer.value ?: return
        if (!t.running && !t.finished) save(context, t.copy(startedAt = System.currentTimeMillis()))
    }

    fun addMinutes(context: Context, minutes: Int) {
        val t = _timer.value ?: return
        val now = System.currentTimeMillis()
        save(
            context,
            if (t.finished) t.copy(duration = t.duration + minutes * MIN, elapsed = t.elapsedAt(now), startedAt = now, finished = false)
            else t.copy(duration = t.duration + minutes * MIN)
        )
    }

    fun stop(context: Context) = save(context, null)

    /** Called by the UI tick: marks the timer finished once time is up (the alarm shows the notification). */
    fun tick(context: Context, now: Long) {
        val t = _timer.value ?: return
        if (t.running && t.remainingAt(now) <= 0L) save(context, t.copy(elapsed = t.duration, startedAt = 0L, finished = true))
    }

    private fun scheduleEnd(context: Context, t: ClockTimer?) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = notifyIntent(context, TIMER_CODE, "Done: ${t?.label.orEmpty()}", "Time is up. Take a breath, then pick the next card.")
        alarm.cancel(pi)
        if (t != null && t.running) setAlarm(alarm, System.currentTimeMillis() + t.remainingAt(System.currentTimeMillis()), pi)
    }

    private fun setAlarm(alarm: AlarmManager, at: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun notifyIntent(context: Context, code: Int, title: String, text: String): PendingIntent {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = "com.mhss.app.EVENT_REMINDER"
            putExtra(EventReminders.EXTRA_CODE, code)
            putExtra(EventReminders.EXTRA_TITLE, title)
            putExtra(EventReminders.EXTRA_TEXT, text)
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // ---------------- cards ----------------
    private fun hm(t: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
    fun dur(ms: Long): String {
        val m = (ms / MIN).coerceAtLeast(0)
        return if (m < 60) "$m min" else if (m % 60 == 0L) "${m / 60} h" else "${m / 60} h ${m % 60} min"
    }

    private fun hasCalendar(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private data class Inst(val id: Long, val title: String, val begin: Long, val end: Long, val location: String, val color: Int)

    private fun instances(context: Context, from: Long, to: Long): List<Inst> {
        if (!hasCalendar(context)) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val out = ArrayList<Inst>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.EVENT_LOCATION, Instances.ALL_DAY, Instances.DISPLAY_COLOR),
                "${Instances.VISIBLE} = 1",
                null,
                "${Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == 1) continue
                    out += Inst(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Event" }, c.getLong(2), c.getLong(3), c.getString(4).orEmpty(), c.getInt(6))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun routineAt(r: Routine, now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, r.hour)
        set(Calendar.MINUTE, r.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    suspend fun cards(context: Context, database: MyBrainDatabase, now: Long = System.currentTimeMillis()): List<ClockCard> {
        val cards = ArrayList<ClockCard>()
        instances(context, now - 36 * 60 * MIN, now + 12 * 60 * MIN).forEach { e ->
            val key = "ev:${e.id}@${e.begin}"
            if (e.begin <= now && e.end > now) {
                cards += ClockCard(key, "event-live", e.title, "In progress · ${dur(e.end - now)} left · until ${hm(e.end)}", ((e.end - now + MIN - 1) / MIN).toInt().coerceAtLeast(1), e.begin, e.end, e.color)
            } else if (e.begin > now) {
                val loc = if (e.location.isNotBlank()) " · ${e.location}" else ""
                cards += ClockCard(key, "event-next", e.title, "${hm(e.begin)}–${hm(e.end)}$loc", ((e.end - e.begin) / MIN).toInt().coerceAtLeast(1), e.begin, e.end, e.color)
            }
        }
        database.taskDao().getAllFullTasks().forEach { t ->
            if (t.isCompleted || t.dueDate <= 0L) return@forEach
            val opens = t.dueDate - FOCUS_LEAD_MIN * MIN
            if (now < opens || now > t.dueDate + 24 * 60 * MIN) return@forEach
            val left = t.dueDate - now
            val minutes = if (left > 0) (left / MIN).toInt().coerceIn(5, FOCUS_MINUTES) else FOCUS_MINUTES
            val detail = if (left > 0) "Due in ${dur(left)} · do it now" else "Overdue by ${dur(-left)}"
            cards += ClockCard("task:${t.id}@${t.dueDate}", "task", t.title.ifBlank { "Task" }, detail, minutes, t.dueDate)
        }
        val weekday = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        routines.filter { weekday in it.days }.forEach { r ->
            val at = routineAt(r, now)
            if (now < at - 30 * MIN || now > at + 60 * MIN) return@forEach
            val detail = if (now < at) "At %02d:%02d · in %s".format(r.hour, r.minute, dur(at - now)) else "Now · ${r.minutes} min"
            cards += ClockCard("rt:${r.id}@$at", "routine", r.name, detail, r.minutes, at)
        }
        val rank = mapOf("event-live" to 0, "routine" to 1, "task" to 2, "event-next" to 3)
        return cards.sortedWith(compareBy({ rank[it.kind] ?: 9 }, { it.at }))
    }

    // ---------------- notifications for the next 24 hours ----------------
    suspend fun scheduleNotifications(context: Context, database: MyBrainDatabase) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences("$PREFS-alarms", Context.MODE_PRIVATE)
        prefs.getStringSet("codes", emptySet())!!.forEach { code ->
            val intent = Intent(context, EventReminderReceiver::class.java).setAction("com.mhss.app.EVENT_REMINDER")
            PendingIntent.getBroadcast(context, code.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                ?.let { alarm.cancel(it) }
        }
        val now = System.currentTimeMillis()
        val until = now + 24 * 60 * MIN
        val codes = HashSet<String>()
        fun add(key: String, at: Long, title: String) {
            if (at <= now || at > until) return
            val code = ("clock:$key").hashCode()
            setAlarm(alarm, at, notifyIntent(context, code, title, "Open Penguin Brain and press START when you are ready."))
            codes += code.toString()
        }
        instances(context, now, until).forEach { add("ev:${it.id}@${it.begin}", it.begin, "${it.title} is starting") }
        database.taskDao().getAllFullTasks().forEach { t ->
            if (!t.isCompleted && t.dueDate > 0) add("task:${t.id}@${t.dueDate}", t.dueDate - FOCUS_LEAD_MIN * MIN, "Time to start: ${t.title}")
        }
        for (dayOffset in 0..1) {
            val base = now + dayOffset * 24 * 60 * MIN
            val weekday = Calendar.getInstance().apply { timeInMillis = base }.get(Calendar.DAY_OF_WEEK)
            routines.filter { weekday in it.days }.forEach { r ->
                val at = routineAt(r, base)
                add("rt:${r.id}@$at", at, "${r.name} · ${r.minutes} min")
            }
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }
}
