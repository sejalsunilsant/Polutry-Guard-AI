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
    @SerializedName("floorSpaceSqFt") val floorSpaceSqFt: Int
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
}
