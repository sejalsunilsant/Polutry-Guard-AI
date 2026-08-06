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
    val provisionedAt: String = ""
)
