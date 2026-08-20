package com.poultryguard.ai.data.repository

import android.content.Context
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.model.Veterinarian
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VetRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val vetDao = db.vetDao()

    fun getVeterinariansFlow(): Flow<List<Veterinarian>> {
        return vetDao.getAllVeterinarians()
    }

    suspend fun populateInitialVetsIfNeeded() {
        // Mock seeding removed. Vets are synced directly from the backend.
    }

    suspend fun insert(vet: Veterinarian) {
        vetDao.insert(vet)
    }

    suspend fun updateVerificationStatus(id: String, status: String) {
        vetDao.updateVerificationStatus(id, status)
    }

    suspend fun updateAvailability(id: String, status: String) {
        vetDao.updateAvailability(id, status)
    }

    suspend fun updateProfile(
        id: String,
        phone: String,
        specialty: String,
        location: String,
        qualification: String,
        licenseNumber: String,
        experience: Int
    ) {
        vetDao.updateProfile(id, phone, specialty, location, qualification, licenseNumber, experience)
    }

    private val cacheManager = com.poultryguard.ai.data.cache.LocalCacheManager(context)
    private var currentUrl: String = ""
    private var cachedApi: com.poultryguard.ai.data.api.AuthApi? = null

    private fun getApi(): com.poultryguard.ai.data.api.AuthApi? {
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
                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                    .build()
                    .create(com.poultryguard.ai.data.api.AuthApi::class.java)
            } catch (e: Exception) {
                null
            }
        }
        return cachedApi
    }

    suspend fun syncVeterinarians(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val api = getApi() ?: throw Exception("API not initialized.")
            val response = api.getAllVeterinarians()
            if (response.status == "success" && response.data != null) {
                val vets = response.data.map { dto ->
                    Veterinarian(
                        id = dto.id,
                        name = dto.name,
                        specialty = dto.specialty.ifBlank { "Avian Medicine" },
                        phone = dto.phone ?: "",
                        email = dto.email,
                        location = dto.location ?: "",
                        photoUrl = dto.photoUrl ?: "",
                        availability = dto.availability,
                        verificationStatus = dto.verificationStatus,
                        licenseNumber = dto.licenseNumber ?: "",
                        qualification = dto.qualification ?: "",
                        experience = dto.experience ?: 0
                    )
                }
                vetDao.deleteAll()
                vetDao.insertAll(vets)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch veterinarians from backend."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
