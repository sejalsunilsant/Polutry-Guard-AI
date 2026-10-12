package com.poultryguard.ai.data.api

import android.content.Context
import android.util.Log
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.model.FarmEvent
import com.poultryguard.ai.data.repository.MortalityRepository
import com.poultryguard.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// Dedicated models for the Groq Chat Completions API
data class GroqChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

data class GroqRequest(
    val model: String = "qwen/qwen3.8-27b",
    val messages: List<GroqChatMessage>
)

data class GroqChoice(
    val message: GroqChatMessage
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

// Internal message format used by UI and database (to avoid breaking the application compile)
data class ChatMessage(
    val sender: String, // "USER" | "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FarmContext(
    val currentTemperature: Float? = null,
    val currentHumidity: Float? = null,
    val currentAmmonia: Float? = null,
    val currentSoundLevel: Float? = null,
    val birdCount: Int = 0,
    val loggedMortalities: Int = 0,
    val activeShed: String = "Shed #4",
    val farmerId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
)

data class ChatRequest(
    val message: String,
    val history: List<ChatMessage>,
    val farmContext: FarmContext
)

data class ChatResponse(
    val reply: String,
    val newAccessToken: String? = null,
    val newRefreshToken: String? = null
)


interface FlaskChatApi {
    @POST("chat")
    suspend fun getChatCompletion(
        @Header("Authorization") authHeader: String?,
        @Header("X-Refresh-Token") refreshToken: String?,
        @Body request: ChatRequest
    ): ChatResponse
}

class ChatService(private val apiKey: String) {

    private val api: FlaskChatApi by lazy {
        val baseUrl = "https://polutry-guard-ai.onrender.com/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlaskChatApi::class.java)
    }

    suspend fun getResponse(messages: List<GroqChatMessage>): String {
        return "Legacy ChatService is deprecated."
    }
}

class ChatRepository(private val context: Context) {
    private val cacheManager = LocalCacheManager(context)
    private val mortalityRepository = MortalityRepository(context)

    private var currentUrl: String = ""
    private var cachedApi: FlaskChatApi? = null

    private fun getApi(): FlaskChatApi? {
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
                    .create(FlaskChatApi::class.java)
            } catch (e: Exception) {
                null
            }
        }
        return cachedApi
    }

    suspend fun getAiResponse(
        message: String,
        history: List<ChatMessage>,
        context: FarmContext
    ): String {
        return try {
            val api = getApi() ?: throw Exception("Retrofit API not initialized.")
            val userProfile = cacheManager.getCachedUserProfile()
            val authHeader = userProfile?.token?.let { "Bearer $it" }
                ?: userProfile?.uid?.let { "Bearer mock_token_for_$it" }
            val refreshToken = userProfile?.refreshToken
            
            val request = ChatRequest(
                message = message,
                history = history,
                farmContext = context
            )
            val response = api.getChatCompletion(authHeader, refreshToken, request)

            // Cache automatically refreshed credentials if returned by backend
            if (!response.newAccessToken.isNullOrBlank() && userProfile != null) {
                cacheManager.cacheUserProfile(userProfile.copy(
                    token = response.newAccessToken,
                    refreshToken = response.newRefreshToken ?: userProfile.refreshToken
                ))
            }

            response.reply
        } catch (e: Exception) {
            Log.e("PoultryGuardChat", "Error contacting LLM API: ${e.localizedMessage}", e)
            // Premium Local Rule AI fallback to keep operations offline-safe (zero cost)
            generateLocalResponse(message, context)
        }
    }

    private suspend fun buildSystemPrompt(farmContext: FarmContext): String {
        val thirtyDaysAgoMs = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        val recentEvents = try {
            cacheManager.getCachedFarmEvents()
        } catch (e: Exception) {
            emptyList()
        }

        val recentMortalities = try {
            mortalityRepository.getAllRecords()
        } catch (e: Exception) {
            emptyList()
        }

        // Filter events and mortalities to the last 30 days
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val filteredEvents = recentEvents.filter { event ->
            try {
                val eventDate = dateFormat.parse(event.dateStr)
                eventDate != null && eventDate.time >= thirtyDaysAgoMs
            } catch (e: Exception) {
                true // Keep it if date parsing fails, to avoid losing valuable records
            }
        }

        val filteredMortalities = recentMortalities.filter { it.timestamp >= thirtyDaysAgoMs }

        val eventsStr = if (filteredEvents.isEmpty()) {
            "No historical events recorded in the last 30 days."
        } else {
            filteredEvents.joinToString("\n") { event ->
                "- [${event.dateStr} ${event.timeStr ?: "00:00"}] ${event.title}: ${event.notes ?: "N/A"} (${event.type})"
            }
        }

        val mortalitiesStr = if (filteredMortalities.isEmpty()) {
            "No historical mortality logs recorded in the last 30 days."
        } else {
            filteredMortalities.joinToString("\n") { record ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                "- [$dateStr] Deaths: ${record.deathCount}, Symptoms: ${record.symptoms}, Suspected Cause: ${record.suspectedCause} (Env snapshot: Temp=${record.temperature}°C, Humid=${record.humidity}%, Ammonia=${record.ammoniaLevel} ppm, Sound=${record.soundLevel} dB)"
            }
        }

        return """
            You are PoultryGuard AI, an expert agricultural AI copilot for a modern smart poultry farm.
            You are helping the farmer, manage his poultry house .
            
            Use the current telemetry and historical records below to provide high-quality, practical, biosecurity-compliant advice. 
            If telemetry indicates anomalies (e.g. high temperature, high ammonia, sound panics), alert the user and suggest specific remedies.
            
            CURRENT FARM ENVIRONMENT:
            - Temperature: ${farmContext.currentTemperature}°C (Ideal: 21-27°C)
            - Humidity: ${farmContext.currentHumidity}% (Ideal: 50-70%)
            - Ammonia Level: ${farmContext.currentAmmonia} ppm (Safe: <20 ppm)
            - Acoustic Panic/Sound Level: ${farmContext.currentSoundLevel} dB (Safe: 40-65 dB)
            - Bird Count: ${farmContext.birdCount}
            - Logged Mortalities (Total Current Batch): ${farmContext.loggedMortalities}
            - Active Shed: ${farmContext.activeShed}
            
            HISTORICAL FARM EVENTS (LAST 30 DAYS):
            $eventsStr
            
            HISTORICAL MORTALITY RECORDS (LAST 30 DAYS):
            $mortalitiesStr
            
            Be concise, clear, and actionable. Keep your tone professional, supportive, and agricultural-expert focused. Use bullet points and bold formatting where appropriate to make information easily scannable in a mobile interface.Use simple langugage that farmer can understand.Dont give too lengthy suggections.
        """.trimIndent()
    }

    private fun generateLocalResponse(message: String, context: FarmContext): String {
        val msg = message.lowercase()
        val temp = context.currentTemperature
        val amm = context.currentAmmonia
        val hasLiveTelemetry = temp != null && temp > 0f && amm != null && amm > 0f

        val telemetryDisplay = if (hasLiveTelemetry) {
            "Live Telemetry: Temp=${temp}°C, Ammonia=${amm} ppm"
        } else {
            "Telemetry previous: Live sensor telemetry unavailable"
        }

        return when {
            "risk" in msg || "disease" in msg || "health" in msg -> {
                if (hasLiveTelemetry) {
                    if ((amm ?: 0f) > 20f || (temp ?: 0f) > 29f) {
                        "🐔 **Local AI Warning**: Ammonia level is elevated (${amm} ppm). High risk of respiratory Snick infection. Make sure exhaust fans are at full capacity to ventilate."
                    } else {
                        "🐔 **Local AI Audit**: Barn environment is safe. Telemetry shows standard comfort indicators."
                    }
                } else {
                    "🐔 **Local AI Audit**: Note: $telemetryDisplay. Standard target comfort range is 21–27°C and ammonia must remain under 20 ppm."
                }
            }
            "ammonia" in msg || "gas" in msg -> {
                if (hasLiveTelemetry) {
                    "💨 **Local Expert Tip**: Ammonia gas is at ${amm} ppm. Safe threshold is below 20 ppm. Dry the broiler litter to keep levels low."
                } else {
                    "💨 **Local Expert Tip**: $telemetryDisplay. Ammonia gas must strictly remain below 20 ppm to prevent respiratory damage."
                }
            }
            "temp" in msg || "heat" in msg -> {
                if (hasLiveTelemetry) {
                    "🌡️ **Local Expert Tip**: Ambient temperature is ${temp}°C. Keep it stable between 21-27°C."
                } else {
                    "🌡️ **Local Expert Tip**: $telemetryDisplay. Maintain optimal ambient temperature between 21–27°C."
                }
            }
            "death" in msg || "mortality" in msg -> {
                val totalFlock = context.birdCount + context.loggedMortalities
                if (totalFlock > 0) {
                    val rate = (context.loggedMortalities.toFloat() / totalFlock) * 100
                    "📊 **Local Expert Tip**: Mortality rate is at %.2f%%. Total deaths: ${context.loggedMortalities}.".format(rate)
                } else {
                    "📊 **Local Expert Tip**: Total flock count is zero. No mortalities recorded."
                }
            }
            else -> {
                "Hello Farmer! I am ChickBot. $telemetryDisplay. Ask me any biosecurity, batch, or flock safety questions!"
            }
        }
    }
}
