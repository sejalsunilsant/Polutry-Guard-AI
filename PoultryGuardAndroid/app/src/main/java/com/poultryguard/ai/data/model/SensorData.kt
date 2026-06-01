package com.poultryguard.ai.data.model

data class SensorReading(
    val id: String,
    val name: String,
    val value: Float,
    val unit: String,
    val status: SensorStatus,
    val timestamp: String,
    val description: String,
    val rangeMin: Float,
    val rangeMax: Float,
    val idealMin: Float,
    val idealMax: Float
)

enum class SensorStatus {
    IDEAL,      // Perfect agricultural condition
    WARNING,    // Needs attention soon (e.g., slight temp variance)
    CRITICAL    // Immediate danger (e.g., extremely high ammonia or loud panic sound)
}
