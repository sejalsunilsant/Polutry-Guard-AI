package com.poultryguard.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_stats")
data class SystemStats(
    @PrimaryKey val id: Int = 1,
    val totalFarmers: Int = 124,
    val activeFarmers: Int = 98,
    val totalFarms: Int = 112,
    val activeFarms: Int = 88,
    val totalDevices: Int = 117,
    val onlineDevices: Int = 89,
    val offlineDevices: Int = 12,
    val registeredVets: Int = 15,
    val activeVets: Int = 11,
    val openDiseaseAlerts: Int = 7,
    val pendingSupportRequests: Int = 3
)
