package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_stats")
data class SystemStats(
    @PrimaryKey val id: Int = 1,
    val totalFarmers: Int = 0,
    val activeFarmers: Int = 0,
    val totalFarms: Int = 0,
    val activeFarms: Int = 0,
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val registeredVets: Int = 0,
    val activeVets: Int = 0,
    val openDiseaseAlerts: Int = 0,
    val pendingSupportRequests: Int = 0
)
