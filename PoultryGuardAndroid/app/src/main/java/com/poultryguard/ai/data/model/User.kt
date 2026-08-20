package com.poultryguard.ai.data.model

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.FARMER,
    val farmName: String = "",
    val joinDate: String = "",
    val approvalStatus: String = "APPROVED",
    val rejectionReason: String? = null,
    val farmId: String? = null,
    val token: String? = null
)

enum class UserRole {
    FARMER,
    VETERINARIAN,
    ADMIN,
    SUPER_ADMIN
}

enum class AppPermission {
    VIEW_OWN_FARM,
    VIEW_ASSIGNED_FARMS,
    VIEW_TELEMETRY,
    VIEW_AI_PREDICTIONS,
    MANAGE_BATCHES,
    VIEW_BATCHES,
    RECORD_MORTALITY,
    VIEW_VET_CASES,
    DIAGNOSE_DISEASE,
    MANAGE_VETS,
    ADD_DEVICE_KITS,
    ASSIGN_DEVICES,
    VIEW_ALL_FARMERS,
    MANAGE_FARMS,
    MANAGE_SYSTEM_SETTINGS
}

/**
 * Maps UserRole to concrete permissions based on the Role-Based Access Control grid.
 */
fun UserRole.getPermissions(): List<AppPermission> {
    return when (this) {
        UserRole.FARMER -> listOf(
            AppPermission.VIEW_OWN_FARM,
            AppPermission.VIEW_TELEMETRY,
            AppPermission.VIEW_AI_PREDICTIONS,
            AppPermission.MANAGE_BATCHES,
            AppPermission.RECORD_MORTALITY,
            AppPermission.VIEW_VET_CASES
        )
        UserRole.VETERINARIAN -> listOf(
            AppPermission.VIEW_ASSIGNED_FARMS,
            AppPermission.VIEW_TELEMETRY,
            AppPermission.VIEW_AI_PREDICTIONS,
            AppPermission.VIEW_BATCHES,
            AppPermission.RECORD_MORTALITY,
            AppPermission.VIEW_VET_CASES,
            AppPermission.DIAGNOSE_DISEASE
        )
        UserRole.ADMIN, UserRole.SUPER_ADMIN -> listOf(
            AppPermission.VIEW_OWN_FARM,
            AppPermission.VIEW_ASSIGNED_FARMS,
            AppPermission.VIEW_TELEMETRY,
            AppPermission.VIEW_AI_PREDICTIONS,
            AppPermission.MANAGE_BATCHES,
            AppPermission.RECORD_MORTALITY,
            AppPermission.VIEW_VET_CASES,
            AppPermission.MANAGE_VETS,
            AppPermission.ADD_DEVICE_KITS,
            AppPermission.ASSIGN_DEVICES,
            AppPermission.VIEW_ALL_FARMERS,
            AppPermission.MANAGE_FARMS,
            AppPermission.MANAGE_SYSTEM_SETTINGS
        )
    }
}

/**
 * Validates if a user profile is authorized for a specific application action.
 */
fun UserProfile.hasPermission(permission: AppPermission): Boolean {
    return this.role.getPermissions().contains(permission)
}
