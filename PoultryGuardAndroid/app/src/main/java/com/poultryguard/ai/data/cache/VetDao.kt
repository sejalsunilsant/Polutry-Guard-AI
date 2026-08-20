package com.poultryguard.ai.data.cache

import androidx.room.*
import com.poultryguard.ai.data.model.Veterinarian
import kotlinx.coroutines.flow.Flow

@Dao
interface VetDao {
    @Query("SELECT * FROM veterinarians")
    fun getAllVeterinarians(): Flow<List<Veterinarian>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vets: List<Veterinarian>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vet: Veterinarian)

    @Query("UPDATE veterinarians SET availability = :status WHERE id = :id")
    suspend fun updateAvailability(id: String, status: String)

    @Query("UPDATE veterinarians SET verificationStatus = :status WHERE id = :id")
    suspend fun updateVerificationStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM veterinarians")
    suspend fun getCount(): Int

    @Query("SELECT * FROM veterinarians WHERE email = :email LIMIT 1")
    suspend fun getVetByEmail(email: String): Veterinarian?

    @Query("UPDATE veterinarians SET phone = :phone, specialty = :specialty, location = :location, qualification = :qualification, licenseNumber = :licenseNumber, experience = :experience WHERE id = :id")
    suspend fun updateProfile(
        id: String,
        phone: String,
        specialty: String,
        location: String,
        qualification: String,
        licenseNumber: String,
        experience: Int
    )

    @Query("DELETE FROM veterinarians")
    suspend fun deleteAll()
}
