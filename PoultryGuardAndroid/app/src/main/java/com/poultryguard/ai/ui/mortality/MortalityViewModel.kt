package com.poultryguard.ai.ui.mortality

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.repository.MortalityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MortalityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MortalityRepository(application.applicationContext)
    private val cacheManager = LocalCacheManager(application.applicationContext)

    // Reactive StateFlow of past records directly from Room database
    val historicalRecords: StateFlow<List<MortalityRecord>> = repository.getAllRecordsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submissionSuccess = MutableStateFlow(false)
    val submissionSuccess: StateFlow<Boolean> = _submissionSuccess.asStateFlow()

    fun resetSubmissionStatus() {
        _submissionSuccess.value = false
    }

    /**
     * Submission logic: fetch current environment readings, bundle them, and save
     */
    fun submitRecord(
        deathCount: Int,
        selectedSymptoms: List<String>,
        customSymptoms: String,
        suspectedCause: String,
        customCause: String
    ) {
        if (deathCount <= 0) return

        viewModelScope.launch {
            _isSubmitting.value = true

            // 1. Automatically fetch the most recent real-time environmental snapshot from cache
            val telemetry = cacheManager.getCachedTelemetry()
            val temp = telemetry["temp"] ?: 24.2f
            val humid = telemetry["humid"] ?: 61.5f
            val ammonia = telemetry["ammonia"] ?: 12.0f
            val sound = telemetry["sound"] ?: 54.0f

            // 2. Assemble symptoms list (chips + typed)
            val combinedSymptoms = mutableListOf<String>()
            combinedSymptoms.addAll(selectedSymptoms)
            if (customSymptoms.isNotBlank()) {
                combinedSymptoms.add(customSymptoms.trim())
            }
            val symptomsStr = if (combinedSymptoms.isEmpty()) "Unspecified" else combinedSymptoms.joinToString(", ")

            // 3. Assemble suspected cause (standard drop-down selection or custom typed)
            val actualCause = if (suspectedCause.equals("Other", ignoreCase = true) && customCause.isNotBlank()) {
                customCause.trim()
            } else {
                suspectedCause
            }

            // 4. Create and save record
            val record = MortalityRecord(
                id = UUID.randomUUID().toString(),
                deathCount = deathCount,
                symptoms = symptomsStr,
                suspectedCause = actualCause,
                timestamp = System.currentTimeMillis(),
                temperature = temp,
                humidity = humid,
                ammoniaLevel = ammonia,
                soundLevel = sound
            )

            repository.insertRecord(record)
            
            // 5. Update overall cached mortalities for dashboard context syncing
            cacheManager.cacheLoggedMortalities(deathCount)

            _isSubmitting.value = false
            _submissionSuccess.value = true
        }
    }

    /**
     * Delete an existing record
     */
    fun deleteRecord(record: MortalityRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }
}
