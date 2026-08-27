package com.poultryguard.ai.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poultryguard.ai.data.api.ChatRepository
import com.poultryguard.ai.data.api.ChatMessage
import com.poultryguard.ai.data.api.FarmContext
import com.poultryguard.ai.data.api.DiseasePredictionRepository
import com.poultryguard.ai.data.api.DiseasePredictionResponse
import com.poultryguard.ai.data.api.DiseaseRiskLevel
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.ConnectionState
import com.poultryguard.ai.data.model.SensorReading
import com.poultryguard.ai.data.model.SensorStatus
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.model.Batch
import com.poultryguard.ai.data.model.BatchStatus
import com.poultryguard.ai.data.model.ageDays
import com.poultryguard.ai.data.repository.MortalityRepository
import com.poultryguard.ai.data.repository.BatchRepository
import com.poultryguard.ai.data.mqtt.MqttManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val sensorReadings: List<SensorReading>,
        val connectionState: ConnectionState,
        val shedName: String,
        val birdCount: Int,
        val loggedMortalities: Int = 0,
        val alertCount: Int = 0,
        val activeBatch: Batch? = null,
        val diseasePrediction: DiseasePredictionResponse = DiseasePredictionResponse(
            riskLevel = DiseaseRiskLevel.LOW,
            confidence = 0.0f,
            recommendation = "No prediction available"
        ),
        val isMqttConnected: Boolean = false
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // AI Chat states
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val diseaseRepository = DiseasePredictionRepository(application.applicationContext)
    private val chatRepository = ChatRepository(application.applicationContext)
    private val cacheManager = LocalCacheManager(application.applicationContext)
    private val mortalityRepository = MortalityRepository(application.applicationContext)
    private val batchRepository = BatchRepository(application.applicationContext)
    private var mqttManager: MqttManager? = null

    private var currentTemp: Float? = null
    private var currentHumid: Float? = null
    private var currentAmmonia: Float? = null
    private var currentSound: Float? = null
    
    private val _isMqttConnected = MutableStateFlow(false) // just placeholder for matches
    private var activeBatch: Batch? = null
    private var unsyncedMortalities = 0
    private var farmerId: String = ""
    private var farmId: String = ""
    private var deviceId: String = ""

    private var activeMqttConnection = false
    private var currentPrediction = DiseasePredictionResponse(
        riskLevel = DiseaseRiskLevel.LOW,
        confidence = 0.0f,
        recommendation = "No prediction available"
    )

    init {
        loadCachedData()
        loadInitialData()
    }

    private fun loadCachedData() {
        val cachedTelemetry = cacheManager.getCachedTelemetry()
        currentTemp = cachedTelemetry["temp"]
        currentHumid = cachedTelemetry["humid"]
        currentAmmonia = cachedTelemetry["ammonia"]
        currentSound = cachedTelemetry["sound"]
        
        activeBatch = cacheManager.getCachedActiveBatch()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            
            val user = cacheManager.getCachedUserProfile()
            val db = com.poultryguard.ai.data.cache.AppDatabase.getDatabase(getApplication())
            val farmerProfile = user?.email?.let { db.farmerProfileDao().getFarmerByEmail(it) }
            
            if (farmerProfile != null) {
                farmerId = farmerProfile.id
                deviceId = farmerProfile.deviceId
            } else {
                farmerId = user?.uid ?: ""
                deviceId = ""
            }
            farmId = user?.farmId ?: farmerId

            // Fetch authoritative farm/device context from backend (mirrors refreshData logic)
            if (user != null && farmerId.isNotBlank()) {
                try {
                    val authRepo = com.poultryguard.ai.data.repository.SupabaseAuthRepository(getApplication())
                    val contextResult = authRepo.fetchUserContext(farmerId)
                    contextResult.onSuccess { dto ->
                        farmId = dto.farmId ?: farmId
                        deviceId = dto.deviceId ?: deviceId
                    }
                } catch (e: Exception) {
                    // Network unavailable — fall through to local cache lookup below
                }
            }

            // Fallback device ID lookup from SharedPreferences if empty (e.g. offline setup)
            if (deviceId.isBlank() && farmerId.isNotEmpty()) {
                val localKits = cacheManager.getHardwareKits()
                val assignedKit = localKits.find { 
                    it.farmerId == farmerId || 
                    (farmerProfile != null && it.farmerId.trim().lowercase() == farmerProfile.id.trim().lowercase()) ||
                    (it.farmerId.isEmpty() && it.farmerName.trim().lowercase() == (farmerProfile?.name ?: user?.name ?: "").trim().lowercase())
                }
                if (assignedKit != null) {
                    deviceId = assignedKit.gatewayId
                }
            }
 
            val farmerName = farmerProfile?.name ?: user?.name ?: "Farmer"
            _chatMessages.value = listOf(
                ChatMessage(
                    sender = "AI",
                    text = "Hi $farmerName! 🐔 I am ChickBot, your AI Farm Assistant. I am monitoring your farm in real-time. Ask me any biosecurity or temperature safety questions!"
                )
            )
             
            // Sync outbox
            batchRepository.syncUnsyncedMortalities()
             
            // Get active batch from repository
            val batchResult = batchRepository.getActiveBatch(farmId)
            batchResult.onSuccess { batch ->
                activeBatch = batch
                unsyncedMortalities = batch?.let { batchRepository.getUnsyncedMortalityCount(it.id) } ?: 0
            }
            
            delay(1000)
            setupMqtt()
            updateDashboardState()
            loadLatestPrediction()
        }
    }


    fun loadLatestPrediction() {
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            val result = diseaseRepository.getLatestPrediction(
                deviceId = deviceId
            )
            result.onSuccess { prediction ->
                currentPrediction = prediction
                updateDashboardState()
            }
        }
    }

    fun triggerPrediction() {
        if (deviceId.isBlank()) return
        viewModelScope.launch {
            val result = diseaseRepository.predictDiseaseRisk(
                deviceId = deviceId,
                farmId = farmId,
                temp = currentTemp ?: 0f,
                humid = currentHumid ?: 0f,
                ammonia = currentAmmonia ?: 0f,
                sound = currentSound ?: 0f
            )
            result.onSuccess { prediction ->
                currentPrediction = prediction
                updateDashboardState()
            }
        }
    }

    private fun setupMqtt() {
        mqttManager?.disconnect()
        mqttManager = MqttManager(
            context = getApplication<Application>().applicationContext,
            onReadingReceived = { topic, value ->
                handleIncomingTelemetry(topic, value)
            },
            onConnectionStateChanged = { connected ->
                activeMqttConnection = connected
                updateDashboardState()
            }
        )
    }

    private fun handleIncomingTelemetry(topic: String, value: Float) {
        when (topic) {
            MqttManager.TOPIC_TEMP -> currentTemp = value
            MqttManager.TOPIC_HUMID -> currentHumid = value
            MqttManager.TOPIC_AMMONIA -> currentAmmonia = value
            MqttManager.TOPIC_SOUND -> currentSound = value
        }
        
        cacheManager.cacheTelemetry(currentTemp, currentHumid, currentAmmonia, currentSound)
        updateDashboardState()
    }

    fun submitMortalityLog(deathCount: Int, symptoms: List<String>) {
        if (deathCount <= 0) return
        
        viewModelScope.launch {
            val currentBatch = activeBatch ?: return@launch
            if (deathCount > currentBatch.currentCount) return@launch
            
            val result = batchRepository.recordMortality(
                batchId = currentBatch.id,
                deathCount = deathCount,
                reason = "Sudden Death Syndrome",
                notes = if (symptoms.isEmpty()) "Unspecified" else symptoms.joinToString(", "),
                recordedBy = "Farmer"
            )
            
            result.onSuccess { updated ->
                activeBatch = updated
                unsyncedMortalities = batchRepository.getUnsyncedMortalityCount(updated.id)
                
                // Simulated prediction generation based on symptoms removed.
                updateDashboardState()
            }
        }
    }

    // Conversational Chat logic with active Farm Context
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(sender = "USER", text = text)
        _chatMessages.value = _chatMessages.value + userMsg
        
        viewModelScope.launch {
            _isTyping.value = true
            
            // Build real-time context
            val currentBatch = activeBatch
            val dynamicBirdCount = if (currentBatch != null) {
                (currentBatch.currentCount - unsyncedMortalities).coerceAtLeast(0)
            } else {
                0
            }
            val context = FarmContext(
                currentTemperature = currentTemp ?: 0f,
                currentHumidity = currentHumid ?: 0f,
                currentAmmonia = currentAmmonia ?: 0f,
                currentSoundLevel = currentSound ?: 0f,
                birdCount = dynamicBirdCount,
                loggedMortalities = unsyncedMortalities,
                activeShed = currentBatch?.let { "${it.id} (${it.breed})" } ?: "No Active Batch",
                farmerId = farmerId.ifBlank { null },
                farmId = farmId.ifBlank { null },
                batchId = currentBatch?.id
            )

            // Dynamic background completion request
            val replyText = chatRepository.getAiResponse(
                message = text,
                history = _chatMessages.value.dropLast(1),
                context = context
            )

            val aiMsg = ChatMessage(sender = "AI", text = replyText)
            _chatMessages.value = _chatMessages.value + aiMsg
            _isTyping.value = false
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            setupMqtt()
            
            val user = cacheManager.getCachedUserProfile()
            if (user != null) {
                try {
                    val authRepo = com.poultryguard.ai.data.repository.SupabaseAuthRepository(getApplication())
                    val contextResult = authRepo.fetchUserContext(user.uid)
                    contextResult.onSuccess { dto ->
                        farmerId = dto.profileId
                        farmId = dto.farmId ?: ""
                        deviceId = dto.deviceId ?: ""
                    }
                } catch (e: Exception) {
                    // Ignore context sync failure on refresh
                }
            }

            // Fallback device ID lookup from SharedPreferences if empty (e.g. offline setup or sync override)
            if (deviceId.isBlank() && farmerId.isNotEmpty()) {
                val db = com.poultryguard.ai.data.cache.AppDatabase.getDatabase(getApplication())
                val farmerProfile = db.farmerProfileDao().getFarmerById(farmerId)
                val localKits = cacheManager.getHardwareKits()
                val assignedKit = localKits.find { 
                    it.farmerId == farmerId || 
                    (farmerProfile != null && it.farmerId.trim().lowercase() == farmerProfile.id.trim().lowercase()) ||
                    (it.farmerId.isEmpty() && it.farmerName.trim().lowercase() == (farmerProfile?.name ?: user?.name ?: "").trim().lowercase())
                }
                if (assignedKit != null) {
                    deviceId = assignedKit.gatewayId
                }
            }

            // Sync outbox
            batchRepository.syncUnsyncedMortalities()
             
            val batchResult = batchRepository.getActiveBatch(farmId)
            batchResult.onSuccess { batch ->
                activeBatch = batch
                unsyncedMortalities = batch?.let { batchRepository.getUnsyncedMortalityCount(it.id) } ?: 0
            }
            
            delay(800)
            _isRefreshing.value = false
            updateDashboardState()
            loadLatestPrediction()
        }
    }

    fun refreshMortalityCount() {
        viewModelScope.launch {
            val batchResult = batchRepository.getActiveBatch(farmId)
            batchResult.onSuccess { batch ->
                activeBatch = batch
                unsyncedMortalities = batch?.let { batchRepository.getUnsyncedMortalityCount(it.id) } ?: 0
            }
            updateDashboardState()
        }
    }

    fun startNewBatch(startDate: String, initialCount: Int, breed: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = batchRepository.startBatch(farmId, startDate, initialCount, breed)
            result.onSuccess { newBatch ->
                activeBatch = newBatch
                unsyncedMortalities = 0
                updateDashboardState()
                onSuccess()
            }
        }
    }

    fun closeActiveBatch(status: BatchStatus, endDate: String, currentCount: Int, onSuccess: () -> Unit) {
        val currentBatch = activeBatch ?: return
        viewModelScope.launch {
            val result = batchRepository.closeBatch(currentBatch.id, status, endDate, currentCount)
            result.onSuccess {
                activeBatch = null
                unsyncedMortalities = 0
                updateDashboardState()
                onSuccess()
            }
        }
    }

    private fun updateDashboardState() {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = dateFormat.format(Date())

        val readings = listOf(
            SensorReading(
                id = "temperature",
                name = "Temperature",
                value = currentTemp,
                unit = "°C",
                status = getTempStatus(currentTemp),
                timestamp = timeStr,
                description = getTempDescription(currentTemp),
                rangeMin = 10f, rangeMax = 45f,
                idealMin = 21f, idealMax = 27f
            ),
            SensorReading(
                id = "humidity",
                name = "Humidity",
                value = currentHumid,
                unit = "%",
                status = getHumidStatus(currentHumid),
                timestamp = timeStr,
                description = getHumidDescription(currentHumid),
                rangeMin = 20f, rangeMax = 95f,
                idealMin = 50f, idealMax = 70f
            ),
            SensorReading(
                id = "ammonia",
                name = "Ammonia Gas",
                value = currentAmmonia,
                unit = "ppm",
                status = getAmmoniaStatus(currentAmmonia),
                timestamp = timeStr,
                description = getAmmoniaDescription(currentAmmonia),
                rangeMin = 0f, rangeMax = 60f,
                idealMin = 0f, idealMax = 20f
            ),
            SensorReading(
                id = "sound",
                name = "Acoustic Panic",
                value = currentSound,
                unit = "dB",
                status = getSoundStatus(currentSound),
                timestamp = timeStr,
                description = getSoundDescription(currentSound),
                rangeMin = 30f, rangeMax = 110f,
                idealMin = 40f, idealMax = 65f
            )
        )

        val alertCount = readings.count { it.status != SensorStatus.IDEAL }

        val currentBatch = activeBatch
        val dynamicBirdCount = if (currentBatch != null) {
            (currentBatch.currentCount - unsyncedMortalities).coerceAtLeast(0)
        } else {
            0
        }
        
        val user = cacheManager.getCachedUserProfile()
        val dynamicShedName = if (currentBatch != null) {
            "${user?.farmName?.ifBlank { "Farm" } ?: "Farm"} (${currentBatch.id} - ${currentBatch.breed} - Day ${currentBatch.ageDays})"
        } else {
            "No Active Batch"
        }

        _uiState.value = DashboardUiState.Success(
            sensorReadings = readings,
            connectionState = if (activeMqttConnection) ConnectionState.CONNECTED else ConnectionState.CONNECTING,
            alertCount = alertCount,
            birdCount = dynamicBirdCount,
            loggedMortalities = unsyncedMortalities,
            shedName = dynamicShedName,
            activeBatch = currentBatch,
            diseasePrediction = currentPrediction,
            isMqttConnected = activeMqttConnection
        )
    }

    private fun getTempStatus(valFloat: Float?): SensorStatus {
        if (valFloat == null) return SensorStatus.IDEAL
        return when {
            valFloat < 19f || valFloat > 30f -> SensorStatus.CRITICAL
            valFloat < 21f || valFloat > 27f -> SensorStatus.WARNING
            else -> SensorStatus.IDEAL
        }
    }

    private fun getTempDescription(valFloat: Float?): String {
        if (valFloat == null) return "Waiting for temperature telemetry..."
        return when (getTempStatus(valFloat)) {
            SensorStatus.IDEAL -> "Shed heat is in standard cozy broiler comfort range."
            SensorStatus.WARNING -> "Mild thermal variance. Keep monitoring ventilation."
            SensorStatus.CRITICAL -> if (valFloat > 30f) "HEAT STRESS! Trigger misting pumps immediately!" else "CHILL HAZARD! Turn on brooder heaters."
        }
    }

    private fun getHumidStatus(valFloat: Float?): SensorStatus {
        if (valFloat == null) return SensorStatus.IDEAL
        return when {
            valFloat < 45f || valFloat > 80f -> SensorStatus.CRITICAL
            valFloat < 50f || valFloat > 70f -> SensorStatus.WARNING
            else -> SensorStatus.IDEAL
        }
    }

    private fun getHumidDescription(valFloat: Float?): String {
        if (valFloat == null) return "Waiting for humidity telemetry..."
        return when (getHumidStatus(valFloat)) {
            SensorStatus.IDEAL -> "Cohesive humidity level. Dampness check OK."
            SensorStatus.WARNING -> "Moderate dampness. Check air replacement cycles."
            SensorStatus.CRITICAL -> "Heavy dampness risk! Fans must increase throughput."
        }
    }

    private fun getAmmoniaStatus(valFloat: Float?): SensorStatus {
        if (valFloat == null) return SensorStatus.IDEAL
        return when {
            valFloat >= 25f -> SensorStatus.CRITICAL
            valFloat >= 16f -> SensorStatus.WARNING
            else -> SensorStatus.IDEAL
        }
    }

    private fun getAmmoniaDescription(valFloat: Float?): String {
        if (valFloat == null) return "Waiting for ammonia telemetry..."
        return when (getAmmoniaStatus(valFloat)) {
            SensorStatus.IDEAL -> "Ammonia is safe. Air quality is pristine."
            SensorStatus.WARNING -> "Slight gas buildup detected. Cycle exhaust fans."
            SensorStatus.CRITICAL -> "CRITICAL EXPOSURE! Litter needs treating/venting."
        }
    }

    private fun getSoundStatus(valFloat: Float?): SensorStatus {
        if (valFloat == null) return SensorStatus.IDEAL
        return when {
            valFloat >= 75f -> SensorStatus.CRITICAL
            valFloat >= 66f -> SensorStatus.WARNING
            else -> SensorStatus.IDEAL
        }
    }

    private fun getSoundDescription(valFloat: Float?): String {
        if (valFloat == null) return "Waiting for acoustic telemetry..."
        return when (getSoundStatus(valFloat)) {
            SensorStatus.IDEAL -> "Steady, natural chirping levels. Flock is calm."
            SensorStatus.WARNING -> "Elevated noise. Disturbance or feeding rush active."
            SensorStatus.CRITICAL -> "CRITICAL SCREAM! Predator, sound shock or power outage!"
        }
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager?.disconnect()
    }
}
