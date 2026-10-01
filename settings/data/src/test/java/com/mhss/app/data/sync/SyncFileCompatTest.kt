package com.mhss.app.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Makes sure a sync file written by the Penguin Brain website can be read by the app. */
class SyncFileCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = true
    }

    @Test
    fun decodesWebSyncFile() {
        val text = javaClass.classLoader!!.getResource("web-sync-sample.json")!!.readText()
        val file = json.decodeFromString(SyncFile.serializer(), text)
        assertEquals(SyncFile.FORMAT, file.format)
        assertEquals("web", file.updatedBy)
        assertTrue(file.tasks.isNotEmpty())
        val legacy = file.tasks.first { it.id == "legacy1" }
        assertEquals(2, legacy.subTasks.size)
        assertTrue(file.notes.any { it.title == "筆記" && it.pinned })
        assertTrue(file.diary.isNotEmpty())
        assertTrue(file.deleted.isNotEmpty())
        assertEquals(2, file.events.size)
        val lecture = file.events.first { it.rrule.isNotBlank() }
        assertEquals("FREQ=WEEKLY;BYDAY=MO", lecture.rrule)
        assertEquals(listOf(10), lecture.reminders)
        assertEquals("#6f4cad", lecture.color)
        assertEquals("cat-class", lecture.category)
        assertEquals(5, file.categories.size)
        assertEquals(3_600_000L, lecture.end - lecture.start)
        assertTrue(file.events.any { it.allDay && it.title == "Holiday moved" })
        // round trip
        val again = json.decodeFromString(SyncFile.serializer(), json.encodeToString(SyncFile.serializer(), file))
        assertEquals(file, again)
    }

    @Test
    fun parsesDurations() {
        assertEquals(3_600_000L, PenguinCalendar.parseDuration("P3600S"))
        assertEquals(5_400_000L, PenguinCalendar.parseDuration("PT1H30M"))
        assertEquals(86_400_000L, PenguinCalendar.parseDuration("P1D"))
        assertEquals(0L, PenguinCalendar.parseDuration(null))
    }
}
