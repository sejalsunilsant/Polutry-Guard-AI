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
                    farmName = farmName.ifBlank { "Greenfield Broilers" },
                    farmLocation = farmLocation.ifBlank { "Unspecified Sector" },
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
}
