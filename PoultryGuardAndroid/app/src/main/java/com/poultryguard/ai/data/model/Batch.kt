package com.poultryguard.ai.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BatchStatus {
    ACTIVE,
    SOLD,
    CLOSED
}

data class Batch(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("farm_id")
    val farmId: String,
    
    @SerializedName("start_date")
    val startDate: String,
    
    @SerializedName("end_date")
    val endDate: String?,
    
    @SerializedName("initial_count")
    val initialCount: Int,
    
    @SerializedName("current_count")
    val currentCount: Int,
    
    @SerializedName("breed")
    val breed: String,
    
    @SerializedName("status")
    val status: BatchStatus
)

/**
 * Extension property to calculate the age of the batch dynamically.
 * Age is calculated as (today - startDate) for active batches,
 * and (endDate - startDate) for completed/closed batches.
 */
val Batch.ageDays: Int
    get() {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val start = format.parse(startDate) ?: return 0
            val end = if (status == BatchStatus.ACTIVE) {
                Date()
            } else {
                endDate?.let { format.parse(it) } ?: Date()
            }
            val diffInMillis = end.time - start.time
            val days = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
            days.coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }
