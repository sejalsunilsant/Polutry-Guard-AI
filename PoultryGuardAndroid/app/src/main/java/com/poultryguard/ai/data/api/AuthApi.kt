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
    @SerializedName("experience") val experience: Int? = null
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
    @SerializedName("name") val name: String
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
    @SerializedName("experience") val experience: Int? = null
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
    @SerializedName("experience") val experience: Int? = null
)

// ── Kit Management DTOs ─────────────────────────────────────────────────────

/**
 * Request body for POST /api/v1/admin/kits.
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
 * Request body for POST /api/v1/admin/kits/assign.
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

/** List-kits response envelope (GET /api/v1/admin/kits). */
data class KitListApiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<KitDto>?
)

// ────────────────────────────────────────────────────────────────────────────

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): AuthApiResponse

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): AuthApiResponse

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(
        @Body body: ForgotPasswordRequest
    ): AuthApiResponse

    @retrofit2.http.GET("api/v1/admin/pending-farmers")
    suspend fun getPendingFarmers(): PendingFarmersResponse

    @POST("api/v1/admin/review-farmer")
    suspend fun reviewFarmer(
        @Body body: ReviewFarmerRequest
    ): ReviewFarmerResponse

    @retrofit2.http.GET("api/v1/admin/farmers")
    suspend fun getAllFarmers(): FarmersResponse

    @retrofit2.http.GET("api/v1/veterinarians")
    suspend fun getAllVeterinarians(): VeterinariansResponse

    @retrofit2.http.GET("api/v1/users/{profile_id}/context")
    suspend fun getUserContext(
        @retrofit2.http.Path("profile_id") profileId: String
    ): UserContextResponse

    /** Register a new IoT device kit (farm_id intentionally NULL at creation). */
    @POST("api/v1/admin/kits")
    suspend fun createKit(
        @Body body: CreateKitRequest
    ): KitApiResponse

    /** Assign an existing kit to a farmer's farm. Backend resolves farm_id. */
    @POST("api/v1/admin/kits/assign")
    suspend fun assignKit(
        @Body body: AssignKitRequest
    ): KitApiResponse

    /** Fetch all registered device kits (excludes write API key). */
    @retrofit2.http.GET("api/v1/admin/kits")
    suspend fun listKits(): KitListApiResponse

    /** Configure Wi-Fi credentials for a device kit. */
    @POST("api/v1/device/configure-wifi")
    suspend fun configureWifi(
        @retrofit2.http.Header("Authorization") token: String,
        @Body body: ConfigureWifiRequest
    ): ConfigureWifiResponse
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


