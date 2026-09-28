package com.mhss.app.data.sync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import java.util.TimeZone
import java.util.UUID

/**
 * The "Penguin Brain" calendar on the phone. It is a local calendar owned by this app, and it is the one
 * that syncs with the website. Events created in it from the calendar screen are uploaded on the next sync,
 * events from the website are written into it.
 *
 * _SYNC_ID holds the shared event id, SYNC_DATA1 the updatedDate of the last synced version and
 * SYNC_DATA2 the reminder minutes (comma separated). Android sets DIRTY / DELETED when the user edits or deletes.
 */
class PenguinCalendar(private val context: Context) {

    data class Row(val rowId: Long, val syncId: String?, val dirty: Boolean, val event: SyncEvent)
    data class Local(val calendarId: Long, val rows: List<Row>, val deletedRows: List<Row>) {
        val events get() = rows.map { it.event }
    }

    private val cr get() = context.contentResolver

    fun hasPermission() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun Uri.asAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    /** @return the calendar id, creating the calendar if needed. Null without calendar permission. */
    fun calendarId(): Long? {
        if (!hasPermission()) return null
        cr.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, ACCOUNT_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, ACCOUNT_NAME)
            put(Calendars.CALENDAR_COLOR, 0xFFD6336C.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return cr.insert(Calendars.CONTENT_URI.asAdapter(), values)?.let { ContentUris.parseId(it) }
    }

    fun read(now: Long): Local? {
        val calId = calendarId() ?: return null
        val rows = ArrayList<Row>()
        val deleted = ArrayList<Row>()
        cr.query(
            Events.CONTENT_URI.asAdapter(),
            arrayOf(
                Events._ID, Events._SYNC_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION,
                Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.RRULE,
                Events.DIRTY, Events.DELETED, Events.SYNC_DATA1, Events.SYNC_DATA2
            ),
            "${Events.CALENDAR_ID} = ?",
            arrayOf(calId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val syncId = c.getString(1)
                val dirty = c.getInt(10) == 1
                val start = c.getLong(5)
                val dtEnd = if (c.isNull(6)) 0L else c.getLong(6)
                val end = if (dtEnd > 0) dtEnd else start + parseDuration(c.getString(7))
                val isNew = syncId.isNullOrBlank()
                val reminders = c.getString(13)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                    ?: if (isNew) listOf(DEFAULT_REMINDER) else emptyList()
                val event = SyncEvent(
                    title = c.getString(2).orEmpty(),
                    description = c.getString(3).orEmpty(),
                    location = c.getString(4).orEmpty(),
                    start = start,
                    end = maxOf(end, start),
                    allDay = c.getInt(8) == 1,
                    rrule = c.getString(9).orEmpty(),
                    reminders = reminders,
                    updatedDate = if (dirty || isNew) now else c.getString(12)?.toLongOrNull() ?: now,
                    id = if (isNew) UUID.randomUUID().toString() else syncId!!
                )
                val row = Row(c.getLong(0), syncId, dirty, event)
                if (c.getInt(11) == 1) deleted += row else rows += row
            }
        }
        return Local(calId, rows, deleted)
    }

    /** Make the phone calendar match [merged]. @return number of changed events. */
    fun apply(local: Local, merged: List<SyncEvent>): Int {
        var changes = 0
        val byId = local.rows.associateBy { it.event.id }
        val keep = merged.map { it.id }.toSet()
        merged.forEach { e ->
            val row = byId[e.id]
            val values = values(local.calendarId, e)
            if (row == null) {
                cr.insert(Events.CONTENT_URI.asAdapter(), values)
                changes++
            } else if (row.dirty || row.syncId != e.id || row.event != e) {
                cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, row.rowId).asAdapter(), values, null, null)
                if (row.event.copy(updatedDate = 0) != e.copy(updatedDate = 0)) changes++
            }
        }
        (local.rows.filter { it.event.id !in keep } + local.deletedRows).forEach {
            cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it.rowId).asAdapter(), null, null)
            if (it in local.rows) changes++
        }
        return changes
    }

    private fun values(calendarId: Long, e: SyncEvent) = ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, e.title)
        put(Events.DESCRIPTION, e.description)
        put(Events.EVENT_LOCATION, e.location)
        put(Events.DTSTART, e.start)
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        // all-day events are stored at UTC midnight, timed events are absolute instants
        put(Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else TimeZone.getDefault().id)
        if (e.rrule.isNotBlank()) {
            put(Events.RRULE, e.rrule)
            put(Events.DURATION, "P${maxOf(0L, (e.end - e.start) / 1000)}S")
            putNull(Events.DTEND)
        } else {
            putNull(Events.RRULE)
            putNull(Events.DURATION)
            put(Events.DTEND, e.end)
        }
        put(Events._SYNC_ID, e.id)
        put(Events.SYNC_DATA1, e.updatedDate.toString())
        put(Events.SYNC_DATA2, e.reminders.joinToString(","))
        put(Events.DIRTY, 0)
    }

    /** Reminder minutes of each synced event, keyed by the phone's event row id. */
    fun remindersByRow(): Map<Long, List<Int>> {
        val calId = calendarId() ?: return emptyMap()
        val out = HashMap<Long, List<Int>>()
        cr.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.SYNC_DATA2, Events._SYNC_ID),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
            arrayOf(calId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val list = c.getString(1)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                    ?: if (c.getString(2).isNullOrBlank()) listOf(DEFAULT_REMINDER) else emptyList()
                if (list.isNotEmpty()) out[c.getLong(0)] = list
            }
        }
        return out
    }

    companion object {
        const val ACCOUNT_NAME = "Penguin Brain"
        const val DEFAULT_REMINDER = 10

        /** RFC 5545 durations like P3600S, PT1H30M, P1D, P1W. */
        fun parseDuration(d: String?): Long {
            if (d.isNullOrBlank()) return 0L
            val m = Regex("""^[+-]?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?(?:(\d+)S)?$""")
                .find(d.trim()) ?: return 0L
            val g = m.groupValues.map { it.toLongOrNull() ?: 0L }
            return ((g[1] * 7 + g[2]) * 86_400L + g[3] * 3_600L + g[4] * 60L + g[5] + g[6]) * 1000L
        }
    }
}
