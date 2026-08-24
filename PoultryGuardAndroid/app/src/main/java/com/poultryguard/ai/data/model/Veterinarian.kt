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
    val availability: String, // "Available", "Busy", "Unavailable"
    val verificationStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED, SUSPENDED
    val assignedFarmsCount: Int = 0,
    val openCasesCount: Int = 0,
    val credentialsDetails: String = "",
    val consultationHistory: String = "",
    
    // Additional requested fields
    val licenseNumber: String = "",
    val qualification: String = "",
    val experience: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null
)
