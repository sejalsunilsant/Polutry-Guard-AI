package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val batchId: String,
    val deviceId: String,
    val predictionId: Long,
    val title: String,
    val description: String,
    val severity: String = "MEDIUM", // 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    val status: String = "UNRESOLVED", // 'UNRESOLVED', 'RESOLVED'
    val createdAt: Long = System.currentTimeMillis()
)
