package com.poultryguard.ai.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

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

interface ChatApi {
    @POST("api/v1/chat")
    suspend fun sendChatMessage(@Body request: ChatRequest): ChatResponse
}

class ChatRepository(
    private val hostUrl: String = "http://10.0.2.2:5000/" // standard emulator loopback port
) {
    private var chatApi: ChatApi? = null

    init {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(hostUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            chatApi = retrofit.create(ChatApi::class.java)
        } catch (e: Exception) {
            chatApi = null
        }
    }

    suspend fun getAiResponse(
        message: String,
        history: List<ChatMessage>,
        context: FarmContext
    ): String {
        return try {
            if (chatApi == null) throw Exception("Retrofit API not initialized.")
            val request = ChatRequest(message, history, context)
            val response = chatApi!!.sendChatMessage(request)
            response.reply
        } catch (e: Exception) {
            // Highly contextual Local Rule AI fallback to keep operations offline-safe (zero cost)
            generateLocalResponse(message, context)
        }
    }

    private fun generateLocalResponse(message: String, context: FarmContext): String {
        val msg = message.lowercase()
        return when {
            "risk" in msg || "disease" in msg || "health" in msg -> {
                if (context.currentAmmonia > 20f || context.currentTemperature > 29f) {
                    "🚨 **Local AI Warning**: Ammonia level is elevated (${context.currentAmmonia} ppm). High risk of respiratory Snick infection. Make sure exhaust fans are at full capacity to ventilate."
                } else {
                    "✅ **Local AI Audit**: Barn environment is safe. Telemetry shows standard comfort indicators."
                }
            }
            "ammonia" in msg || "gas" in msg -> {
                "💨 **Local Expert Tip**: Ammonia gas should be kept below 20 ppm. Dry the broiler litter to keep levels within safe parameters."
            }
            "temp" in msg || "heat" in msg -> {
                "🌡️ **Local Expert Tip**: Ambient temperature is ${context.currentTemperature}°C. Keep it stable between 21-27°C."
            }
            "death" in msg || "mortality" in msg -> {
                "📊 **Local Expert Tip**: Mortality rate is at %.2f%%. Stable, healthy limits.".format(
                    (context.loggedMortalities.toFloat() / (context.birdCount + context.loggedMortalities)) * 100
                )
            }
            else -> {
                "Hi Joe! I am your AI Copilot. Ask me about air quality, bird health, temperature management, or mortality rates."
            }
        }
    }
}
