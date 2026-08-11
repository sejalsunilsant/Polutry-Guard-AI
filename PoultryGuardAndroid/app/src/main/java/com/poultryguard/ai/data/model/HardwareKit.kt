package com.poultryguard.ai.data.model

data class HardwareKit(
    val farmerName: String,
    val farmName: String,
    val gatewayId: String,
    val tempSensorId: String = "TMP-001",
    val humidSensorId: String = "HUM-001",
    val ammoniaSensorId: String = "NH3-001",
    val soundSensorId: String = "MIC-001",
    val ssid: String = "",
    val isProvisioned: Boolean = false,
    val isActive: Boolean = false,
    val provisionedAt: String = "",
    
    // Admin Device Kit Management fields
    val kitId: String = "PG-KIT-00000",
    val serialNumber: String = "PGESP00000",
    val firmwareVersion: String = "1.0.0",
    val lifecycleStatus: String = "Available", // Manufactured, Available, Assigned to Farmer, Installed, Active, Maintenance, Retired
    val hasTempSensor: Boolean = true,
    val hasHumidSensor: Boolean = true,
    val hasAmmoniaSensor: Boolean = true,
    val hasSoundSensor: Boolean = true,
    val hasCameraSensor: Boolean = false,
    val cameraSensorId: String = "CAM-001",
    val lastCommunication: String = "Never"
)

