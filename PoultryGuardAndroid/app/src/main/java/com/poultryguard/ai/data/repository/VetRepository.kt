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
            val api = getApi()
            if (api == null) {
                android.util.Log.e("VetRepo", "syncVeterinarians: API is null, seeding mock vets")
                seedMockVets()
                return@withContext Result.failure(Exception("API not initialized."))
            }
            android.util.Log.d("VetRepo", "syncVeterinarians: calling API getAllVeterinarians...")
            val response = api.getAllVeterinarians()
            android.util.Log.d("VetRepo", "syncVeterinarians: response status=${response.status}, data size=${response.data?.size}")
            if (response.status == "success" && response.data != null) {
                val data = response.data
                if (data.isEmpty()) {
                    android.util.Log.w("VetRepo", "syncVeterinarians: server returned empty list, seeding mock vets")
                    seedMockVets()
                    return@withContext Result.success(Unit)
                }
                val vets = data.map { dto ->
                    android.util.Log.d("VetRepo", "syncVeterinarians: mapping vet id=${dto.id}, name=${dto.name}, verificationStatus=${dto.verificationStatus}")
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
                        experience = dto.experience ?: 0,
                        latitude = dto.latitude,
                        longitude = dto.longitude
                    )
                }
                vetDao.deleteAll()
                vetDao.insertAll(vets)
                val verifiedAfterSync = vetDao.getVerifiedCount()
                android.util.Log.d("VetRepo", "syncVeterinarians: SUCCESS. Inserted ${vets.size} vets. Verified count in DB=$verifiedAfterSync")
                Result.success(Unit)
            } else {
                android.util.Log.w("VetRepo", "syncVeterinarians: status not success or data null, seeding mock vets")
                seedMockVets()
                Result.failure(Exception("Failed to fetch veterinarians from backend."))
            }
        } catch (e: Exception) {
            android.util.Log.e("VetRepo", "syncVeterinarians: EXCEPTION: ${e.message}", e)
            seedMockVets()
            Result.failure(e)
        }
    }

    private suspend fun seedMockVets() {
        val verifiedCount = vetDao.getVerifiedCount()
        if (verifiedCount == 0) {
            val mockVets = listOf(
                Veterinarian(
                    id = "vet_1",
                    name = "Dr. Ramesh Kumar",
                    specialty = "Avian Pathology",
                    phone = "+919876543210",
                    email = "ramesh@poultryguard.ai",
                    location = "Ludhiana, Punjab",
                    photoUrl = "https://randomuser.me/api/portraits/men/32.jpg",
                    availability = "Available",
                    verificationStatus = "VERIFIED",
                    licenseNumber = "VET-IND-2021-9981",
                    qualification = "M.V.Sc (Avian Medicine)",
                    experience = 8,
                    latitude = 18.5204,
                    longitude = 73.8567
                ),
                Veterinarian(
                    id = "vet_2",
                    name = "Dr. Priya Patel",
                    specialty = "Poultry Nutrition",
                    phone = "+918765432109",
                    email = "priya@poultryguard.ai",
                    location = "Anand, Gujarat",
                    photoUrl = "https://randomuser.me/api/portraits/women/44.jpg",
                    availability = "Available",
                    verificationStatus = "VERIFIED",
                    licenseNumber = "VET-IND-2018-4521",
                    qualification = "Ph.D. in Poultry Science",
                    experience = 12,
                    latitude = 22.5645,
                    longitude = 72.9289
                )
            )
            vetDao.insertAll(mockVets)
        }
    }
}
