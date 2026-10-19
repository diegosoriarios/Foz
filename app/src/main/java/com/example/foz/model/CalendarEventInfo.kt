package com.example.foz.model

data class CalendarEventInfo(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?
)
