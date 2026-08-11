package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "veterinary_cases")
data class VeterinaryCase(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val alertId: String?,
    val batchId: String,
    val veterinarianId: String?,
    val status: String = "PENDING", // 'PENDING', 'ASSIGNED', 'DIAGNOSED', 'RESOLVED'
    val diagnosis: String? = null,
    val recommendation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
