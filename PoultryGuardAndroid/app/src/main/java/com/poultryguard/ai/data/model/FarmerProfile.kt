package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farmer_profiles")
data class FarmerProfile(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val accountStatus: String, // "Active", "Disabled"
    val lastActive: String, // e.g. "2 min ago", "3 hrs ago"
    val isOnline: Boolean,
    
    // Farm Information
    val farmName: String,
    val farmLocation: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val totalSheds: Int,
    val floorSpaceSqFt: Int,
    
    // Linked Device Information
    val deviceId: String, // e.g. "ESP32-001"
    val deviceSerial: String,
    val firmwareVersion: String,
    val signalStrengthRssi: String = "",
    val batteryPercentage: String = "",
    
    // Active Batch Information
    val activeBatchId: String,
    val activeBatchStartDate: String,
    val chickAgeDays: Int,
    val feedConsumedKg: Float,
    val mortalitiesCount: Int,
    val isBatchActive: Boolean = true,
    
    // Sensor status details
    val tempSensorStatus: String = "",
    val humidSensorStatus: String = "",
    val ammoniaSensorStatus: String = "",
    val soundSensorStatus: String = "",
    
    // Disease Alerts
    val openDiseaseAlertsCount: Int,
    val latestAlertText: String = "",
    
    // Veterinarian Consultations
    val assignedVetName: String = "",
    val lastConsultationDate: String = "",
    val consultationNotes: String = "",
    
    // Activity History
    val lastLoginTime: String = "",
    val lastActionDesc: String = ""
)
