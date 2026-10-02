package com.mhss.app.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String? = null,
    val start: Long,
    val end: Long,
    val location: String? = null,
    val allDay: Boolean = false,
    val color: Int = 0,
    val calendarId: Long,
    val recurring: Boolean = false,
    val frequency: CalendarEventFrequency = CalendarEventFrequency.NEVER,
    val interval: Int = 1,
    val weekDays: Set<DayOfWeek> = emptySet(),
    val instanceDay: Long? = null,
    /** Last moment the event repeats (RRULE UNTIL), null = forever. */
    val until: Long? = null,
    /** Explicit colour to write when saving: null = keep as is, 0 = clear (use calendar colour). */
    val eventColor: Int? = null,
)

enum class CalendarEventFrequency {
    NEVER,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
