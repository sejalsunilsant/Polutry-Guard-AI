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
    val totalSheds: Int,
    val floorSpaceSqFt: Int,
    
    // Linked Device Information
    val deviceId: String, // e.g. "ESP32-001"
    val deviceSerial: String,
    val firmwareVersion: String,
    val signalStrengthRssi: String = "-58 dBm",
    val batteryPercentage: String = "94%",
    
    // Active Batch Information
    val activeBatchId: String,
    val activeBatchStartDate: String,
    val chickAgeDays: Int,
    val feedConsumedKg: Float,
    val mortalitiesCount: Int,
    val isBatchActive: Boolean = true,
    
    // Sensor status details
    val tempSensorStatus: String = "Normal (24.2°C)",
    val humidSensorStatus: String = "Normal (61.5%)",
    val ammoniaSensorStatus: String = "Optimal (12 ppm)",
    val soundSensorStatus: String = "Healthy (54 dB)",
    
    // Disease Alerts
    val openDiseaseAlertsCount: Int,
    val latestAlertText: String = "No critical alerts",
    
    // Veterinarian Consultations
    val assignedVetName: String = "Dr. Sarah Jenkins",
    val lastConsultationDate: String = "2026-08-01",
    val consultationNotes: String = "Flock showing excellent weight gain. No respiratory snicks reported.",
    
    // Activity History
    val lastLoginTime: String = "Today, 10:45 AM",
    val lastActionDesc: String = "Configured automatic ventilation trigger for Shed 4"
)
