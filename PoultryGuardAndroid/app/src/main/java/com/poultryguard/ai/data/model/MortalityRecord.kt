package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "mortality_records")
data class MortalityRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deathCount: Int,
    val reason: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val recordedBy: String = "Farmer",
    
    // Legacy support fields
    val symptoms: String = "Unspecified",
    val suspectedCause: String = "Unspecified",
    
    // Automated Environmental Snapshot
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val ammoniaLevel: Float = 0f,
    val soundLevel: Float = 0f,
    
    val batchId: String? = null,
    val isSynced: Boolean = false
)
