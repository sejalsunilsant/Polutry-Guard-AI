package com.poultryguard.ai.data.repository

import android.content.Context
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.model.Alert
import com.poultryguard.ai.data.model.VeterinaryCase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class VeterinaryCaseRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val alertDao = db.alertDao()
    private val veterinaryCaseDao = db.veterinaryCaseDao()

    fun getAllAlertsFlow(): Flow<List<Alert>> = alertDao.getAllAlertsFlow()

    fun getAllCasesFlow(): Flow<List<VeterinaryCase>> = veterinaryCaseDao.getAllCasesFlow()

    fun getCasesForVetFlow(vetId: String): Flow<List<VeterinaryCase>> =
        veterinaryCaseDao.getCasesForVetFlow(vetId)

    suspend fun getAlertById(id: String): Alert? = alertDao.getAlertById(id)

    suspend fun getCaseById(id: String): VeterinaryCase? = veterinaryCaseDao.getCaseById(id)

    suspend fun createAlert(batchId: String, deviceId: String, predictionId: Long, title: String, description: String, severity: String): Alert {
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

    suspend fun submitDiagnosis(caseId: String, diagnosis: String, recommendation: String): Result<Unit> {
        return try {
            val vetCase = veterinaryCaseDao.getCaseById(caseId) ?: throw Exception("Case not found.")
            val updated = vetCase.copy(
                status = "DIAGNOSED",
                diagnosis = diagnosis,
                recommendation = recommendation,
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
}
