package com.poultryguard.ai.data.cache

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.poultryguard.ai.data.model.FarmEvent
import com.poultryguard.ai.data.model.RecurrenceType
import com.poultryguard.ai.data.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CalendarReminderManager {

    fun scheduleReminder(context: Context, event: FarmEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val dateTimeStr = "${event.dateStr} ${event.timeStr ?: "08:00"}"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = try {
            sdf.parse(dateTimeStr)
        } catch (e: Exception) {
            null
        } ?: return

        var triggerTime = date.time

        // If trigger time is in the past, adjust it forward if it's recurring
        val currentTime = System.currentTimeMillis()
        if (triggerTime <= currentTime) {
            val calendar = Calendar.getInstance().apply { time = date }
            while (calendar.timeInMillis <= currentTime) {
                when (event.recurrence) {
                    RecurrenceType.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                    RecurrenceType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    RecurrenceType.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                    else -> break
                }
            }
            triggerTime = calendar.timeInMillis
            // If it is a one-off and in the past, don't schedule
            if (event.recurrence == RecurrenceType.NONE && triggerTime <= currentTime) {
                Log.d("ReminderManager", "Skipping alarm in past for event: ${event.id}")
                return
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", event.title)
            val notesWithRecurrence = if (event.recurrence != RecurrenceType.NONE) {
                "${event.notes ?: ""} (${event.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }} reminder)"
            } else {
                event.notes ?: ""
            }
            putExtra("notes", notesWithRecurrence)
            putExtra("eventId", event.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val interval = when (event.recurrence) {
                RecurrenceType.DAILY -> AlarmManager.INTERVAL_DAY
                RecurrenceType.WEEKLY -> AlarmManager.INTERVAL_DAY * 7
                RecurrenceType.MONTHLY -> AlarmManager.INTERVAL_DAY * 30
                else -> 0L
            }

            if (interval > 0L) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    interval,
                    pendingIntent
                )
                Log.d("ReminderManager", "Scheduled repeating alarm for event: ${event.id} at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(triggerTime)}")
            } else {
                // Use standard alarm
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d("ReminderManager", "Scheduled one-off alarm for event: ${event.id} at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(triggerTime)}")
            }
        } catch (e: SecurityException) {
            Log.e("ReminderManager", "Failed to schedule alarm due to security exception: ${e.localizedMessage}")
        }
    }

    fun cancelReminder(context: Context, event: FarmEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("ReminderManager", "Cancelled alarm for event: ${event.id}")
        }
    }
}
