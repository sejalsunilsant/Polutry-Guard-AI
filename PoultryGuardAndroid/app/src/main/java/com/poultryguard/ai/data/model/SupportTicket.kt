package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "support_tickets")
data class SupportTicket(
    @PrimaryKey val ticketId: String,
    val farmerName: String,
    val issue: String,
    val deviceId: String,
    val priority: String, // "LOW", "MEDIUM", "HIGH"
    val status: String, // "Unresolved", "Resolved"
    val category: String // "Farmer Complaint", "Device Problem", "Installation Request", "Veterinarian Request", "Technical Support"
)
