package com.poultryguard.ai.data.repository

import android.content.Context
import com.poultryguard.ai.data.api.AuthApi
import com.poultryguard.ai.data.api.LoginRequest
import com.poultryguard.ai.data.api.RegisterRequest
import com.poultryguard.ai.data.api.ForgotPasswordRequest
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole,
        farmName: String = "",
        farmLocation: String = "",
        totalSheds: Int = 4,
        floorSpaceSqFt: Int = 24000
    ): Result<UserProfile>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): UserProfile?
    fun isSimulatedMode(): Boolean
    suspend fun getPendingFarmers(): Result<List<com.poultryguard.ai.data.api.PendingFarmerDto>>
    suspend fun reviewFarmer(profileId: String, action: String, rejectionReason: String? = null): Result<Unit>
    suspend fun syncAllFarmers(): Result<Unit>
    suspend fun fetchUserContext(profileId: String): Result<com.poultryguard.ai.data.api.UserContextDto>
}

class SupabaseAuthRepository(private val context: Context) : AuthRepository {

    private val cacheManager = LocalCacheManager(context)
    private var currentUrl: String = ""
    private var cachedApi: AuthApi? = null

    private fun getApi(): AuthApi {
        val url = cacheManager.getApiBaseUrl()
        if (url != currentUrl || cachedApi == null) {
            currentUrl = url
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            cachedApi = Retrofit.Builder()
                .baseUrl(currentUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi::class.java)
        }
        return cachedApi!!
    }

    /**
     * Public accessor for the Retrofit AuthApi client, used by screens that need
     * to call kit management endpoints (createKit, assignKit, listKits).
     * Returns null if the API base URL is not configured (offline / fallback mode).
     */
    fun getAuthApi(): AuthApi? {
        return try {
            getApi()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.login(LoginRequest(email.trim().lowercase(), password))
            if (response.status == "success" && response.data != null) {
                if (response.data.approvalStatus == "APPROVED") {
                    cacheManager.cacheUserProfile(response.data)
                }
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "Failed logging in."))
            }
        } catch (e: retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                val gson = com.google.gson.Gson()
                val errorResponse = gson.fromJson(errorBody, com.poultryguard.ai.data.api.AuthApiResponse::class.java)
                if (errorResponse != null && errorResponse.message != null) {
                    val code = errorResponse.code ?: ""
                    Result.failure(Exception(errorResponse.message + if (code.isNotEmpty()) "||$code" else ""))
                } else {
                    Result.failure(e)
                }
            } catch (jsonEx: Exception) {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole,
        farmName: String,
        farmLocation: String,
        totalSheds: Int,
        floorSpaceSqFt: Int
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.register(
                RegisterRequest(
                    name = name,
                    email = email.trim().lowercase(),
                    password = password,
                    role = role.name,
                    farmName = farmName,
                    farmLocation = farmLocation,
                    totalSheds = totalSheds,
                    floorSpaceSqFt = floorSpaceSqFt
                )
            )
            if (response.status == "success" && response.data != null) {
                if (response.data.approvalStatus == "APPROVED") {
                    cacheManager.cacheUserProfile(response.data)
                }
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "Registration failed."))
            }
        } catch (e: retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                val gson = com.google.gson.Gson()
                val errorResponse = gson.fromJson(errorBody, com.poultryguard.ai.data.api.AuthApiResponse::class.java)
                if (errorResponse != null && errorResponse.message != null) {
                    val code = errorResponse.code ?: ""
                    Result.failure(Exception(errorResponse.message + if (code.isNotEmpty()) "||$code" else ""))
                } else {
                    Result.failure(e)
                }
            } catch (jsonEx: Exception) {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
            if (response.status == "success") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Failed sending reset link."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cacheManager.clearCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): UserProfile? {
        return cacheManager.getCachedUserProfile()
    }

    override fun isSimulatedMode(): Boolean {
        return false
    }

    override suspend fun getPendingFarmers(): Result<List<com.poultryguard.ai.data.api.PendingFarmerDto>> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.getPendingFarmers()
            if (response.status == "success" && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "Failed to fetch pending farmers."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reviewFarmer(
        profileId: String,
        action: String,
        rejectionReason: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.reviewFarmer(
                com.poultryguard.ai.data.api.ReviewFarmerRequest(profileId, action, rejectionReason)
            )
            if (response.status == "success") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Failed to submit review action."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncAllFarmers(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.getAllFarmers()
            if (response.status == "success" && response.data != null) {
                val db = com.poultryguard.ai.data.cache.AppDatabase.getDatabase(context)
                val farmers = response.data.map { dto ->
                    val farmMember = dto.farmMembers?.firstOrNull()
                    com.poultryguard.ai.data.model.FarmerProfile(
                        id = dto.id,
                        name = dto.name,
                        email = dto.email,
                        phone = "",
                        accountStatus = if (dto.approvalStatus == "APPROVED") "Active" else "Pending",
                        lastActive = "Just now",
                        isOnline = false,
                        farmName = farmMember?.farms?.name ?: "",
                        farmLocation = "",
                        totalSheds = 4,
                        floorSpaceSqFt = 24000,
                        deviceId = "",
                        deviceSerial = "",
                        firmwareVersion = "",
                        activeBatchId = "",
                        activeBatchStartDate = "",
                        chickAgeDays = 0,
                        feedConsumedKg = 0.0f,
                        mortalitiesCount = 0,
                        openDiseaseAlertsCount = 0
                    )
                }
                db.farmerProfileDao().deleteAll()
                db.farmerProfileDao().insertAll(farmers)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch farmers from backend."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchUserContext(profileId: String): Result<com.poultryguard.ai.data.api.UserContextDto> = withContext(Dispatchers.IO) {
        try {
            val api = getApi()
            val response = api.getUserContext(profileId)
            if (response.status == "success" && response.data != null) {
                val dto = response.data
                val db = com.poultryguard.ai.data.cache.AppDatabase.getDatabase(context)
                if (dto.role == "FARMER") {
                    val existing = db.farmerProfileDao().getFarmerById(dto.profileId)
                    val newFarmer = com.poultryguard.ai.data.model.FarmerProfile(
                        id = dto.profileId,
                        name = dto.name,
                        email = dto.email,
                        phone = existing?.phone ?: "",
                        accountStatus = "Active",
                        lastActive = "Just now",
                        isOnline = true,
                        farmName = dto.farmName ?: "",
                        farmLocation = existing?.farmLocation ?: "",
                        totalSheds = existing?.totalSheds ?: 4,
                        floorSpaceSqFt = existing?.floorSpaceSqFt ?: 24000,
                        deviceId = dto.deviceId ?: "",
                        deviceSerial = dto.deviceId ?: "",
                        firmwareVersion = existing?.firmwareVersion ?: "",
                        activeBatchId = existing?.activeBatchId ?: "",
                        activeBatchStartDate = existing?.activeBatchStartDate ?: "",
                        chickAgeDays = existing?.chickAgeDays ?: 0,
                        feedConsumedKg = existing?.feedConsumedKg ?: 0.0f,
                        mortalitiesCount = existing?.mortalitiesCount ?: 0,
                        openDiseaseAlertsCount = existing?.openDiseaseAlertsCount ?: 0
                    )
                    db.farmerProfileDao().insert(newFarmer)

                    if (!dto.deviceId.isNullOrBlank()) {
                        val currentKits = cacheManager.getHardwareKits().toMutableList()
                        val matchedKit = currentKits.find { it.gatewayId == dto.deviceId }
                        val updatedKit = matchedKit?.copy(
                            farmerId = dto.profileId,
                            farmerName = dto.name,
                            farmName = dto.farmName ?: "",
                            gatewayId = dto.deviceId,
                            kitId = if (matchedKit.kitId.isNotBlank()) matchedKit.kitId else (dto.deviceName ?: dto.deviceId),
                            isProvisioned = true,
                            isActive = true,
                            lifecycleStatus = "Active",
                            thingspeakChannelId = dto.thingspeakChannelId ?: matchedKit.thingspeakChannelId,
                            thingspeakReadApiKey = dto.thingspeakReadApiKey ?: matchedKit.thingspeakReadApiKey
                        ) ?: com.poultryguard.ai.data.model.HardwareKit(
                            farmerId = dto.profileId,
                            farmerName = dto.name,
                            farmName = dto.farmName ?: "",
                            gatewayId = dto.deviceId,
                            kitId = dto.deviceName ?: dto.deviceId,
                            isProvisioned = true,
                            isActive = true,
                            lifecycleStatus = "Active",
                            thingspeakChannelId = dto.thingspeakChannelId ?: "",
                            thingspeakReadApiKey = dto.thingspeakReadApiKey ?: ""
                        )
                        for (i in currentKits.indices) {
                            if (currentKits[i].gatewayId != dto.deviceId) {
                                currentKits[i] = currentKits[i].copy(isActive = false)
                            }
                        }
                        currentKits.removeAll { it.gatewayId == dto.deviceId }
                        currentKits.add(updatedKit)
                        cacheManager.saveHardwareKits(currentKits)
                    }

                    val cachedProfile = cacheManager.getCachedUserProfile()
                    if (cachedProfile != null) {
                        cacheManager.cacheUserProfile(cachedProfile.copy(
                            farmName = dto.farmName ?: "",
                            farmId = dto.farmId
                        ))
                    }
                } else if (dto.role == "VETERINARIAN") {
                    val existing = db.vetDao().getVetByEmail(dto.email)
                    val newVet = com.poultryguard.ai.data.model.Veterinarian(
                        id = dto.profileId,
                        name = dto.name,
                        specialty = existing?.specialty ?: "Avian Medicine",
                        phone = dto.thingspeakChannelId ?: existing?.phone ?: "",
                        email = dto.email,
                        location = existing?.location ?: "",
                        photoUrl = existing?.photoUrl ?: "",
                        availability = existing?.availability ?: "Available",
                        verificationStatus = existing?.verificationStatus ?: "VERIFIED"
                    )
                    db.vetDao().insert(newVet)
                }
                Result.success(dto)
            } else {
                Result.failure(Exception("Failed to fetch context from backend."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
