package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "mortality_records")
data class MortalityRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deathCount: Int,
    val symptoms: String,
    val suspectedCause: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Automated Environmental Snapshot
    val temperature: Float,
    val humidity: Float,
    val ammoniaLevel: Float,
    val soundLevel: Float
)
