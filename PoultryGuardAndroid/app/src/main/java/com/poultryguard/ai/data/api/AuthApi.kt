package com.poultryguard.ai.data.api

import com.google.gson.annotations.SerializedName
import com.poultryguard.ai.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String,
    @SerializedName("farmName") val farmName: String,
    @SerializedName("farmLocation") val farmLocation: String,
    @SerializedName("totalSheds") val totalSheds: Int,
    @SerializedName("floorSpaceSqFt") val floorSpaceSqFt: Int,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("specialty") val specialty: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("licenseNumber") val licenseNumber: String? = null,
    @SerializedName("qualification") val qualification: String? = null,
    @SerializedName("experience") val experience: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String
)

data class AuthApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: UserProfile?,
    @SerializedName("code") val code: String? = null
)

data class PendingFarmersResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<PendingFarmerDto>?
)

data class PendingFarmerDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("join_date") val joinDate: String,
    @SerializedName("approval_status") val approvalStatus: String,
    @SerializedName("farm_members") val farmMembers: List<FarmMemberDto>?
)

data class FarmMemberDto(
    @SerializedName("farm_id") val farmId: String,
    @SerializedName("farms") val farms: FarmDto?
)

data class FarmDto(
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("batches") val batches: List<BatchDto>? = null
)

data class BatchDto(
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String
)


data class ReviewFarmerRequest(
    @SerializedName("profileId") val profileId: String,
    @SerializedName("action") val action: String,
    @SerializedName("rejectionReason") val rejectionReason: String?
)

data class ReviewFarmerResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?
)

data class FarmersResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<PendingFarmerDto>?
)

data class VeterinariansResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<VeterinarianDto>?
)

data class VeterinarianDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("specialty") val specialty: String,
    @SerializedName("phone") val phone: String?,
    @SerializedName("email") val email: String,
    @SerializedName("location") val location: String?,
    @SerializedName("verification_status") val verificationStatus: String,
    @SerializedName("availability") val availability: String,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("license_number") val licenseNumber: String? = null,
    @SerializedName("qualification") val qualification: String? = null,
    @SerializedName("experience") val experience: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class UserContextResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: UserContextDto?
)

data class UserContextDto(
    @SerializedName("profileId") val profileId: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("farmId") val farmId: String?,
    @SerializedName("farmName") val farmName: String?,
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("deviceName") val deviceName: String?,
    @SerializedName("thingspeakChannelId") val thingspeakChannelId: String?,
    @SerializedName("thingspeakReadApiKey") val thingspeakReadApiKey: String?,
    @SerializedName("wifiSsid") val wifiSsid: String?,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("specialty") val specialty: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("licenseNumber") val licenseNumber: String? = null,
    @SerializedName("qualification") val qualification: String? = null,
    @SerializedName("experience") val experience: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

// ── Kit Management DTOs ─────────────────────────────────────────────────────

/**
 * Request body for POST /admin/kits.
 * The thingspeakWriteApiKey is sent to the backend but NEVER stored in any
 * client-side model — it stays server-side only.
 */
data class CreateKitRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("name") val name: String,
    @SerializedName("kitId") val kitId: String = "",
    @SerializedName("serialNumber") val serialNumber: String = "",
    @SerializedName("firmwareVersion") val firmwareVersion: String = "",
    @SerializedName("thingspeakChannelId") val thingspeakChannelId: String? = null,
    @SerializedName("thingspeakReadApiKey") val thingspeakReadApiKey: String? = null,
    @SerializedName("thingspeakWriteApiKey") val thingspeakWriteApiKey: String? = null
)

/**
 * Request body for POST /admin/kits/assign.
 * The backend resolves farm_id from farm_members — Android must never pass farm_id directly.
 */
data class AssignKitRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("farmerProfileId") val farmerProfileId: String
)

/** DTO returned in the `data` field of kit API responses. */
data class KitDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("kit_id") val kitId: String?,
    @SerializedName("farm_id") val farmId: String?,
    @SerializedName("thingspeak_channel_id") val thingspeakChannelId: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?,
    @SerializedName("created_at") val createdAt: String?
)

/** Generic single-kit response envelope. */
data class KitApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: KitDto?
)

/** List-kits response envelope (GET /admin/kits). */
data class KitListApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<KitDto>?
)

// ────────────────────────────────────────────────────────────────────────────

interface AuthApi {
    @POST("auth/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): AuthApiResponse

    @POST("auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): AuthApiResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body body: ForgotPasswordRequest
    ): AuthApiResponse

    @retrofit2.http.GET("admin/pending-farmers")
    suspend fun getPendingFarmers(): PendingFarmersResponse

    @POST("admin/review-farmer")
    suspend fun reviewFarmer(
        @Body body: ReviewFarmerRequest
    ): ReviewFarmerResponse

    @retrofit2.http.GET("admin/farmers")
    suspend fun getAllFarmers(): FarmersResponse

    @retrofit2.http.GET("veterinarians")
    suspend fun getAllVeterinarians(): VeterinariansResponse

    @retrofit2.http.GET("users/{profile_id}/context")
    suspend fun getUserContext(
        @retrofit2.http.Path("profile_id") profileId: String
    ): UserContextResponse

    /** Register a new IoT device kit (farm_id intentionally NULL at creation). */
    @POST("admin/kits")
    suspend fun createKit(
        @Body body: CreateKitRequest
    ): KitApiResponse

    /** Assign an existing kit to a farmer's farm. Backend resolves farm_id. */
    @POST("admin/kits/assign")
    suspend fun assignKit(
        @Body body: AssignKitRequest
    ): KitApiResponse

    /** Fetch all registered device kits (excludes write API key). */
    @retrofit2.http.GET("admin/kits")
    suspend fun listKits(): KitListApiResponse

    /** Configure Wi-Fi credentials for a device kit. */
    @POST("device/configure-wifi")
    suspend fun configureWifi(
        @retrofit2.http.Header("Authorization") token: String,
        @Body body: ConfigureWifiRequest
    ): ConfigureWifiResponse

    @retrofit2.http.GET("health-alerts")
    suspend fun getAlerts(
        @retrofit2.http.Query("batch_id") batchId: String? = null
    ): AlertsApiResponse

    @POST("health-alerts")
    suspend fun createAlert(
        @Body body: CreateAlertRequest
    ): AlertApiResponse

    @retrofit2.http.PUT("health-alerts/{alert_id}")
    suspend fun updateAlertStatus(
        @retrofit2.http.Path("alert_id") alertId: String,
        @Body body: UpdateAlertStatusRequest
    ): SimpleApiResponse

    @retrofit2.http.GET("veterinary-consultations")
    suspend fun getCases(
        @retrofit2.http.Query("vet_id") vetId: String? = null,
        @retrofit2.http.Query("batch_id") batchId: String? = null
    ): CasesApiResponse

    @POST("veterinary-consultations")
    suspend fun createCase(
        @Body body: CreateCaseRequest
    ): CaseApiResponse

    @retrofit2.http.PUT("veterinary-consultations/{case_id}")
    suspend fun updateCase(
        @retrofit2.http.Path("case_id") caseId: String,
        @Body body: UpdateCaseRequest
    ): SimpleApiResponse
}

data class ConfigureWifiRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("ssid") val ssid: String,
    @SerializedName("password") val password: String
)

data class ConfigureWifiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class CreateAlertRequest(
    @SerializedName("batchId") val batchId: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("predictionId") val predictionId: Long,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("severity") val severity: String
)

data class UpdateAlertStatusRequest(
    @SerializedName("status") val status: String
)

data class AlertsApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<AlertDto>?
)

data class AlertApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: AlertDto?
)

data class AlertDto(
    @SerializedName("id") val id: String,
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("prediction_id") val predictionId: Long,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateCaseRequest(
    @SerializedName("alertId") val alertId: String?,
    @SerializedName("batchId") val batchId: String,
    @SerializedName("veterinarianId") val veterinarianId: String?,
    @SerializedName("status") val status: String
)

data class UpdateCaseRequest(
    @SerializedName("veterinarianId") val veterinarianId: String?,
    @SerializedName("status") val status: String,
    @SerializedName("diagnosis") val diagnosis: String?,
    @SerializedName("recommendation") val recommendation: String?,
    @SerializedName("treatment") val treatment: String?,
    @SerializedName("followUpInstructions") val followUpInstructions: String?
)

data class CasesApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<CaseDto>?
)

data class CaseApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: CaseDto?
)

data class CaseDto(
    @SerializedName("id") val id: String,
    @SerializedName("alert_id") val alertId: String?,
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("veterinarian_id") val veterinarianId: String?,
    @SerializedName("status") val status: String,
    @SerializedName("diagnosis") val diagnosis: String?,
    @SerializedName("recommendation") val recommendation: String?,
    @SerializedName("treatment") val treatment: String?,
    @SerializedName("followUpInstructions") val followUpInstructions: String?,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class SimpleApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?
)





