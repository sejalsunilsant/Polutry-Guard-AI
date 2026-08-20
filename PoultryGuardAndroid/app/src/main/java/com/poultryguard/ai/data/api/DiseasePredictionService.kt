package com.poultryguard.ai.data.api

import android.content.Context
import com.poultryguard.ai.data.cache.LocalCacheManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Multipart
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

// Request payload for REST AI Disease prediction model
data class DiseasePredictionRequest(
    val deviceId: String,
    val farmId: String
)

// Response layout returned by AI REST engine
data class DiseasePredictionResponse(
    val riskLevel: DiseaseRiskLevel,
    val confidence: Float,
    val recommendation: String
)

// Response layout returned by AI Sound classification engine
data class SoundPredictionResponse(
    val prediction: String,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val status: String,
    val message: String? = null
)

// Response layout returned by Multi-modal Guardian Prediction engine
data class GuardianPredictionResponse(
    val status: String,
    val condition: String,
    val riskLevel: DiseaseRiskLevel,
    val confidence: Float,
    val recommendation: String,
    val imageUrl: String?,
    val soundUrl: String?,
    val timestamp: String
)

enum class DiseaseRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

interface DiseasePredictionApi {
    @POST("api/v1/predict-disease")
    suspend fun predictDisease(@Body request: DiseasePredictionRequest): DiseasePredictionResponse

    @GET("api/v1/predictions/latest")
    suspend fun getLatestPrediction(@Query("deviceId") deviceId: String): DiseasePredictionResponse

    @Multipart
    @POST("api/v1/predict-sound")
    suspend fun predictSound(@Part file: MultipartBody.Part): SoundPredictionResponse

    @Multipart
    @POST("api/v1/guardian/predict")
    suspend fun predictGuardian(
        @Part("deviceId") deviceId: RequestBody,
        @Part("farmId") farmId: RequestBody,
        @Part image: MultipartBody.Part?,
        @Part sound: MultipartBody.Part?
    ): GuardianPredictionResponse
}

class DiseasePredictionRepository(private val context: Context) {
    private val cacheManager = LocalCacheManager(context)
    private var currentUrl: String = ""
    private var cachedApi: DiseasePredictionApi? = null

    private fun getApi(): DiseasePredictionApi? {
        val url = cacheManager.getApiBaseUrl()
        if (url != currentUrl || cachedApi == null) {
            currentUrl = url
            cachedApi = try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                Retrofit.Builder()
                    .baseUrl(currentUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(DiseasePredictionApi::class.java)
            } catch (e: Exception) {
                null // Graceful local fallback if URL is misconfigured
            }
        }
        return cachedApi
    }

    suspend fun predictSoundFile(
        filePart: MultipartBody.Part
    ): Result<SoundPredictionResponse> {
        return try {
            val api = getApi() ?: throw Exception("Retrofit API not initialized.")
            val response = api.predictSound(filePart)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestPrediction(deviceId: String): Result<DiseasePredictionResponse> {
        return try {
            val api = getApi() ?: throw Exception("Retrofit API not initialized.")
            val response = api.getLatestPrediction(deviceId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun predictDiseaseRisk(
        deviceId: String,
        farmId: String,
        temp: Float = 0.0f,
        humid: Float = 0.0f,
        ammonia: Float = 0.0f,
        sound: Float = 0.0f
    ): Result<DiseasePredictionResponse> {
        return try {
            val api = getApi() ?: throw Exception("Retrofit API not initialized.")
            
            val request = DiseasePredictionRequest(deviceId, farmId)
            val response = api.predictDisease(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun predictGuardian(
        deviceId: String,
        farmId: String,
        imagePart: MultipartBody.Part?,
        soundPart: MultipartBody.Part?
    ): Result<GuardianPredictionResponse> {
        return try {
            val api = getApi() ?: throw Exception("Retrofit API not initialized.")
            val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
            val farmIdBody = farmId.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val response = api.predictGuardian(
                deviceId = deviceIdBody,
                farmId = farmIdBody,
                image = imagePart,
                sound = soundPart
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

