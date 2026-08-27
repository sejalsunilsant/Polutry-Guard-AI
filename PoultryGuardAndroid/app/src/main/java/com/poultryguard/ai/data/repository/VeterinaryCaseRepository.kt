package com.poultryguard.ai.data.repository

import android.content.Context
import android.util.Log
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.Alert
import com.poultryguard.ai.data.model.VeterinaryCase
import com.poultryguard.ai.data.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class VeterinaryCaseRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val alertDao = db.alertDao()
    private val veterinaryCaseDao = db.veterinaryCaseDao()
    private val cacheManager = LocalCacheManager(context)
    private var currentUrl: String = ""
    private var cachedApi: AuthApi? = null

    private fun getApi(): AuthApi? {
        val url = cacheManager.getApiBaseUrl()
        if (url != currentUrl || cachedApi == null) {
            currentUrl = url
            cachedApi = try {
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                retrofit2.Retrofit.Builder()
                    .baseUrl(currentUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(AuthApi::class.java)
            } catch (e: Exception) {
                null
            }
        }
        return cachedApi
    }

    fun getAllAlertsFlow(): Flow<List<Alert>> = alertDao.getAllAlertsFlow()

    fun getAllCasesFlow(): Flow<List<VeterinaryCase>> = veterinaryCaseDao.getAllCasesFlow()

    fun getCasesForVetFlow(vetId: String): Flow<List<VeterinaryCase>> =
        veterinaryCaseDao.getCasesForVetFlow(vetId)

    suspend fun getAlertById(id: String): Alert? = alertDao.getAlertById(id)

    suspend fun getCaseById(id: String): VeterinaryCase? = veterinaryCaseDao.getCaseById(id)

    suspend fun createAlert(batchId: String, deviceId: String, predictionId: Long, title: String, description: String, severity: String): Alert {
        val api = getApi()
        if (api != null) {
            try {
                val response = api.createAlert(CreateAlertRequest(batchId, deviceId, predictionId, title, description, severity))
                if (response.status == "success" && response.data != null) {
                    val dto = response.data
                    val alert = Alert(
                        id = dto.id,
                        batchId = dto.batchId,
                        deviceId = dto.deviceId,
                        predictionId = dto.predictionId,
                        title = dto.title,
                        description = dto.description,
                        severity = dto.severity,
                        status = dto.status
                    )
                    alertDao.insert(alert)
                    return alert
                }
            } catch (e: Exception) {
                Log.w("VetCaseRepo", "createAlert API failed, using local fallback: ${e.localizedMessage}")
            }
        }
        val alert = Alert(
            id = UUID.randomUUID().toString(),
            batchId = batchId,
            deviceId = deviceId,
            predictionId = predictionId,
            title = title,
            description = description,
            severity = severity,
            status = "UNRESOLVED"
        )
        alertDao.insert(alert)
        return alert
    }

    suspend fun requestVeterinaryReview(alertId: String): Result<VeterinaryCase> {
        return try {
            val alert = alertDao.getAlertById(alertId) ?: throw Exception("Alert not found.")
            val api = getApi()
            if (api != null) {
                try {
                    val response = api.createCase(CreateCaseRequest(alertId, alert.batchId, null, "PENDING"))
                    if (response.status == "success" && response.data != null) {
                        val dto = response.data
                        val vetCase = VeterinaryCase(
                            id = dto.id,
                            alertId = dto.alertId,
                            batchId = dto.batchId,
                            veterinarianId = dto.veterinarianId,
                            status = dto.status,
                            diagnosis = dto.diagnosis,
                            recommendation = dto.recommendation,
                            treatment = dto.treatment,
                            followUpInstructions = dto.followUpInstructions
                        )
                        veterinaryCaseDao.insert(vetCase)
                        return Result.success(vetCase)
                    }
                } catch (e: Exception) {
                    Log.w("VetCaseRepo", "createCase API failed, using local fallback: ${e.localizedMessage}")
                }
            }
            val vetCase = VeterinaryCase(
                id = UUID.randomUUID().toString(),
                alertId = alertId,
                batchId = alert.batchId,
                veterinarianId = null,
                status = "PENDING",
                diagnosis = null,
                recommendation = null
            )
            veterinaryCaseDao.insert(vetCase)
            Result.success(vetCase)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignVeterinarian(caseId: String, vetId: String): Result<Unit> {
        return try {
            val vetCase = veterinaryCaseDao.getCaseById(caseId) ?: throw Exception("Case not found.")
            val api = getApi()
            if (api != null) {
                api.updateCase(caseId, UpdateCaseRequest(vetId, "ASSIGNED", vetCase.diagnosis, vetCase.recommendation, vetCase.treatment, vetCase.followUpInstructions))
            }
            val updated = vetCase.copy(
                veterinarianId = vetId,
                status = "ASSIGNED",
                updatedAt = System.currentTimeMillis()
            )
            veterinaryCaseDao.insert(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val consultationDao = db.consultationDao()
    private val mortalityDao = db.mortalityDao()

    fun getConsultationsForVetFlow(vetId: String): Flow<List<com.poultryguard.ai.data.model.Consultation>> =
        consultationDao.getConsultationsForVetFlow(vetId)

    fun getConsultationsForFarmerFlow(farmerId: String): Flow<List<com.poultryguard.ai.data.model.Consultation>> =
        consultationDao.getConsultationsForFarmerFlow(farmerId)

    fun getAllMortalityRecordsFlow(): Flow<List<com.poultryguard.ai.data.model.MortalityRecord>> =
        mortalityDao.getAllRecordsFlow()

    suspend fun scheduleConsultation(
        vetId: String,
        farmerId: String,
        farmerName: String,
        dateTime: Long,
        notes: String,
        followUpDate: Long
    ): Result<Unit> {
        return try {
            val consultation = com.poultryguard.ai.data.model.Consultation(
                id = UUID.randomUUID().toString(),
                veterinarianId = vetId,
                farmerId = farmerId,
                farmerName = farmerName,
                dateTime = dateTime,
                notes = notes,
                followUpDate = followUpDate,
                status = "SCHEDULED"
            )
            consultationDao.insert(consultation)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateConsultationStatus(id: String, status: String): Result<Unit> {
        return try {
            val consultation = consultationDao.getConsultationById(id) ?: throw Exception("Consultation not found.")
            val updated = consultation.copy(status = status)
            consultationDao.insert(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitDiagnosis(
        caseId: String,
        diagnosis: String,
        recommendation: String,
        treatment: String,
        followUpInstructions: String
    ): Result<Unit> {
        return try {
            val vetCase = veterinaryCaseDao.getCaseById(caseId) ?: throw Exception("Case not found.")
            val api = getApi()
            if (api != null) {
                api.updateCase(caseId, UpdateCaseRequest(vetCase.veterinarianId, "DIAGNOSED", diagnosis, recommendation, treatment, followUpInstructions))
            }
            val updated = vetCase.copy(
                status = "DIAGNOSED",
                diagnosis = diagnosis,
                recommendation = recommendation,
                treatment = treatment,
                followUpInstructions = followUpInstructions,
                updatedAt = System.currentTimeMillis()
            )
            veterinaryCaseDao.insert(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveCase(caseId: String): Result<Unit> {
        return try {
            val vetCase = veterinaryCaseDao.getCaseById(caseId) ?: throw Exception("Case not found.")
            val api = getApi()
            if (api != null) {
                api.updateCase(caseId, UpdateCaseRequest(vetCase.veterinarianId, "RESOLVED", vetCase.diagnosis, vetCase.recommendation, vetCase.treatment, vetCase.followUpInstructions))
                vetCase.alertId?.let { alertId ->
                    api.updateAlertStatus(alertId, UpdateAlertStatusRequest("RESOLVED"))
                }
            }
            val updated = vetCase.copy(
                status = "RESOLVED",
                updatedAt = System.currentTimeMillis()
            )
            veterinaryCaseDao.insert(updated)
            
            // Also resolve the associated alert if there is one
            vetCase.alertId?.let { alertId ->
                alertDao.updateStatus(alertId, "RESOLVED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAlerts(batchId: String? = null): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val api = getApi() ?: throw Exception("API not initialized.")
            val response = api.getAlerts(batchId)
            if (response.status == "success" && response.data != null) {
                val list = response.data.map { dto ->
                    Alert(
                        id = dto.id,
                        batchId = dto.batchId,
                        deviceId = dto.deviceId,
                        predictionId = dto.predictionId,
                        title = dto.title,
                        description = dto.description,
                        severity = dto.severity,
                        status = dto.status
                    )
                }
                list.forEach { alertDao.insert(it) }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch alerts"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCases(vetId: String? = null, batchId: String? = null): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val api = getApi() ?: throw Exception("API not initialized.")
            val response = api.getCases(vetId, batchId)
            if (response.status == "success" && response.data != null) {
                val list = response.data.map { dto ->
                    VeterinaryCase(
                        id = dto.id,
                        alertId = dto.alertId,
                        batchId = dto.batchId,
                        veterinarianId = dto.veterinarianId,
                        status = dto.status,
                        diagnosis = dto.diagnosis,
                        recommendation = dto.recommendation,
                        treatment = dto.treatment,
                        followUpInstructions = dto.followUpInstructions
                    )
                }
                list.forEach { veterinaryCaseDao.insert(it) }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch cases"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createManualVetRequest(
        batchId: String,
        vetId: String,
        diseaseAlertId: String? = null
    ): Result<VeterinaryCase> {
        return try {
            val existing = veterinaryCaseDao.getAllCasesFlow().first()
            val duplicate = existing.any { 
                it.batchId == batchId && 
                it.veterinarianId == vetId && 
                it.status != "RESOLVED"
            }
            if (duplicate) {
                throw Exception("An active request for this batch and veterinarian is open.")
            }
            
            val api = getApi()
            if (api != null) {
                val response = api.createCase(CreateCaseRequest(
                    alertId = diseaseAlertId,
                    batchId = batchId,
                    veterinarianId = vetId,
                    status = "ASSIGNED"
                ))
                if (response.status == "success" && response.data != null) {
                    val dto = response.data
                    val vetCase = VeterinaryCase(
                        id = dto.id,
                        alertId = dto.alertId,
                        batchId = dto.batchId,
                        veterinarianId = dto.veterinarianId,
                        status = dto.status,
                        diagnosis = dto.diagnosis,
                        recommendation = dto.recommendation,
                        treatment = dto.treatment,
                        followUpInstructions = dto.followUpInstructions
                    )
                    veterinaryCaseDao.insert(vetCase)
                    return Result.success(vetCase)
                } else {
                    throw Exception("Server rejected case creation.")
                }
            }
            
            val vetCase = VeterinaryCase(
                id = UUID.randomUUID().toString(),
                alertId = diseaseAlertId,
                batchId = batchId,
                veterinarianId = vetId,
                status = "ASSIGNED"
            )
            veterinaryCaseDao.insert(vetCase)
            Result.success(vetCase)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAndTriggerAutoVetAlert(batchId: String) {
        try {
            val batch = cacheManager.getCachedActiveBatch() ?: return
            if (batch.id != batchId) return
            
            val totalDeaths = db.mortalityDao().getAllRecords().filter { it.batchId == batchId }.sumOf { it.deathCount }
            val thresholdPercentage = 1.0f
            val mortalityRate = if (batch.initialCount > 0) (totalDeaths.toFloat() / batch.initialCount * 100) else 0f
            
            if (mortalityRate >= thresholdPercentage) {
                val activeAlerts = alertDao.getAllAlertsFlow().first().filter { 
                    it.batchId == batchId && 
                    it.status == "UNRESOLVED" && 
                    !it.title.contains("Healthy", ignoreCase = true) 
                }
                if (activeAlerts.isNotEmpty()) {
                    val diseaseAlert = activeAlerts.first()
                    
                    val existingCases = veterinaryCaseDao.getAllCasesFlow().first()
                    val duplicateExists = existingCases.any { 
                        it.batchId == batchId && 
                        it.alertId == diseaseAlert.id && 
                        it.status != "RESOLVED" 
                    }
                    if (duplicateExists) {
                        return
                    }
                    
                    val cachedUser = cacheManager.getCachedUserProfile()
                    val farmerLat = cachedUser?.let { db.farmerProfileDao().getFarmerByEmail(it.email)?.latitude }
                    val farmerLng = cachedUser?.let { db.farmerProfileDao().getFarmerByEmail(it.email)?.longitude }
                    
                    val vets = db.vetDao().getAllVeterinarians().first().filter { it.verificationStatus == "VERIFIED" }
                    val chosenVet = if (vets.isNotEmpty() && farmerLat != null && farmerLng != null) {
                        vets.minByOrNull { vet ->
                            val vetLat = vet.latitude
                            val vetLng = vet.longitude
                            if (vetLat != null && vetLng != null) {
                                Math.pow(vetLat - farmerLat, 2.0) + Math.pow(vetLng - farmerLng, 2.0)
                            } else {
                                Double.MAX_VALUE
                            }
                        } ?: vets.first()
                    } else if (vets.isNotEmpty()) {
                        vets.first()
                    } else {
                        null
                    }
                    
                    if (chosenVet != null) {
                        val api = getApi()
                        if (api != null) {
                            try {
                                val response = api.createCase(CreateCaseRequest(
                                    alertId = diseaseAlert.id,
                                    batchId = batchId,
                                    veterinarianId = chosenVet.id,
                                    status = "ASSIGNED"
                                ))
                                if (response.status == "success" && response.data != null) {
                                    val dto = response.data
                                    val vetCase = VeterinaryCase(
                                        id = dto.id,
                                        alertId = dto.alertId,
                                        batchId = dto.batchId,
                                        veterinarianId = dto.veterinarianId,
                                        status = dto.status,
                                        diagnosis = dto.diagnosis,
                                        recommendation = dto.recommendation,
                                        treatment = dto.treatment,
                                        followUpInstructions = dto.followUpInstructions
                                    )
                                    veterinaryCaseDao.insert(vetCase)
                                }
                            } catch (ae: Exception) {
                                Log.e("AutoVetAlert", "API creation failed, using local: ${ae.localizedMessage}")
                                val vetCase = VeterinaryCase(
                                    id = UUID.randomUUID().toString(),
                                    alertId = diseaseAlert.id,
                                    batchId = batchId,
                                    veterinarianId = chosenVet.id,
                                    status = "ASSIGNED"
                                )
                                veterinaryCaseDao.insert(vetCase)
                            }
                        } else {
                            val vetCase = VeterinaryCase(
                                id = UUID.randomUUID().toString(),
                                alertId = diseaseAlert.id,
                                batchId = batchId,
                                veterinarianId = chosenVet.id,
                                status = "ASSIGNED"
                            )
                            veterinaryCaseDao.insert(vetCase)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutoVetAlert", "Error triggering alert: ${e.localizedMessage}")
        }
    }
}
