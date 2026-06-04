package com.poultryguard.ai.data.model

import java.util.UUID

enum class FarmEventType {
    VACCINE,
    MEDICINE,
    FEEDING,
    CLEANING,
    VENTILATION,
    OTHER,
    DEATH
}

enum class RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

data class FarmEvent(
    val id: String = UUID.randomUUID().toString(),
    val dateStr: String, // format: "yyyy-MM-dd"
    val timeStr: String? = null, // format: "HH:mm"
    val type: FarmEventType,
    val title: String,
    val count: Int? = null, // specifically for deaths
    val cause: String? = null, // specifically for deaths
    val symptoms: String? = null, // specifically for deaths
    val notes: String? = null,
    val isScheduled: Boolean = false,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val isReminderSent: Boolean = false
)
