package com.poultryguard.ai.data.model

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.FARMER,
    val farmName: String = "Greenfield Broilers",
    val joinDate: String = ""
)

enum class UserRole {
    FARMER,       // Oversees sensors, fans, feeders
    VETERINARIAN, // Monitors sound anomalies, mortality logs, vaccines
    ADMIN         // Approves user roles, configures IoT devices, shed structures
}
