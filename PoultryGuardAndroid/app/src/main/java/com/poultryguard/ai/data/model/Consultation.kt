package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "consultations")
data class Consultation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val veterinarianId: String,
    val farmerId: String,
    val farmerName: String,
    val dateTime: Long,
    val notes: String = "",
    val followUpDate: Long = 0L,
    val status: String = "SCHEDULED" // SCHEDULED, COMPLETED, CANCELLED
)
