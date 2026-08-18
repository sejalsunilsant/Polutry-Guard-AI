package com.poultryguard.ai.data.model

data class HardwareKit(
    val farmerName: String,
    val farmName: String,
    val gatewayId: String,
    val tempSensorId: String = "",
    val humidSensorId: String = "",
    val ammoniaSensorId: String = "",
    val soundSensorId: String = "",
    val ssid: String = "",
    val isProvisioned: Boolean = false,
    val isActive: Boolean = false,
    val provisionedAt: String = "",

    // Admin Device Kit Management fields
    val kitId: String = "",
    val serialNumber: String = "",
    val firmwareVersion: String = "",
    val lifecycleStatus: String = "Available", // Manufactured, Available, Assigned to Farmer, Installed, Active, Maintenance, Retired
    val hasTempSensor: Boolean = true,
    val hasHumidSensor: Boolean = true,
    val hasAmmoniaSensor: Boolean = true,
    val hasSoundSensor: Boolean = true,
    val hasCameraSensor: Boolean = false,
    val cameraSensorId: String = "",
    val lastCommunication: String = "Never",
    val farmerId: String = "",

    // ThingSpeak cloud telemetry credentials (read-only; write key is server-side only)
    val thingspeakChannelId: String = "",
    val thingspeakReadApiKey: String = ""
)


