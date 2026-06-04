package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "veterinarians")
data class Veterinarian(
    @PrimaryKey val id: String,
    val name: String,
    val specialty: String,
    val phone: String,
    val email: String,
    val location: String,
    val photoUrl: String,
    val availability: String // "Available", "Busy", "Unavailable"
)
