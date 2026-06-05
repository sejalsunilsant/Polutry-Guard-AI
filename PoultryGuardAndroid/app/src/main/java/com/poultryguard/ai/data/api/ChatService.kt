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

// Dedicated models for the Groq Chat Completions API
data class GroqChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

data class GroqRequest(
    val model: String = "llama-3.1-8b-instant",
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
    val currentTemperature: Float,
    val currentHumidity: Float,
    val currentAmmonia: Float,
    val currentSoundLevel: Float,
    val birdCount: Int,
    val loggedMortalities: Int,
    val activeShed: String = "Shed #4"
)

data class ChatRequest(
    val message: String,
    val history: List<ChatMessage>,
    val farmContext: FarmContext
)

data class ChatResponse(
    val reply: String
)

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): GroqResponse
}

class ChatService(private val apiKey: String) {

    private val api: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    suspend fun getResponse(messages: List<GroqChatMessage>): String {
        return try {
            val request = GroqRequest(messages = messages)
            val response = api.getChatCompletion("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "No response from AI"
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }
}

class ChatRepository(
    private val context: Context,
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    private val cacheManager = LocalCacheManager(context)
    private val mortalityRepository = MortalityRepository(context)

    private val api: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    suspend fun getAiResponse(
        message: String,
        history: List<ChatMessage>,
        context: FarmContext
    ): String {
        return try {
            if (apiKey.isBlank() || apiKey == "YOUR_GROQ_API_KEY_HERE" || apiKey == "\"\"") {
                throw Exception("Groq API Key not configured. Using offline fallback.")
            }

            // 1. Build system prompt with all current and 30-day historical data
            val systemPrompt = buildSystemPrompt(context)

            // 2. Prepare Groq chat messages list
            val groqMessages = mutableListOf<GroqChatMessage>()
            groqMessages.add(GroqChatMessage(role = "system", content = systemPrompt))

            // 3. Map conversation history
            for (chatMsg in history) {
                val role = if (chatMsg.sender == "USER") "user" else "assistant"
                groqMessages.add(GroqChatMessage(role = role, content = chatMsg.text))
            }

            // 4. Add the current user query
            groqMessages.add(GroqChatMessage(role = "user", content = message))

            // 5. Send completion request
            val request = GroqRequest(messages = groqMessages)
            val response = api.getChatCompletion("Bearer $apiKey", request)

            response.choices.firstOrNull()?.message?.content ?: throw Exception("Received empty response from Groq API")
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
            
            Be concise, clear, and actionable. Keep your tone professional, supportive, and agricultural-expert focused. Use bullet points and bold formatting where appropriate to make information easily scannable in a mobile interface.Use simple langugage that farmer can understand.
        """.trimIndent()
    }

    private fun generateLocalResponse(message: String, context: FarmContext): String {
        val msg = message.lowercase()
        return when {
            "risk" in msg || "disease" in msg || "health" in msg -> {
                if (context.currentAmmonia > 20f || context.currentTemperature > 29f) {
                    " **Local AI Warning**: Ammonia level is elevated (${context.currentAmmonia} ppm). High risk of respiratory Snick infection. Make sure exhaust fans are at full capacity to ventilate."
                } else {
                    " **Local AI Audit**: Barn environment is safe. Telemetry shows standard comfort indicators."
                }
            }
            "ammonia" in msg || "gas" in msg -> {
                " **Local Expert Tip**: Ammonia gas should be kept below 20 ppm. Dry the broiler litter to keep levels within safe parameters."
            }
            "temp" in msg || "heat" in msg -> {
                "🌡️ **Local Expert Tip**: Ambient temperature is ${context.currentTemperature}°C. Keep it stable between 21-27°C."
            }
            "death" in msg || "mortality" in msg -> {
                val totalFlock = context.birdCount + context.loggedMortalities
                if (totalFlock > 0) {
                    val rate = (context.loggedMortalities.toFloat() / totalFlock) * 100
                    "**Local Expert Tip**: Mortality rate is at %.2f%%. Stable, healthy limits.".format(rate)
                } else {
                    "**Local Expert Tip**: No birds or mortality recorded yet. Total flock count is zero."
                }
            }
            else -> {
                "Hii ! I am your AI Copilot. (Operating in Offline Mode). Ask me about air quality, bird health, temperature management, or mortality rates."
            }
        }
    }
}
