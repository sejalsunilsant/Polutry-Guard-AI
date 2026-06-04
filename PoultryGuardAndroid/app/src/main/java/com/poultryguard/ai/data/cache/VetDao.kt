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

    @Query("UPDATE veterinarians SET availability = :status WHERE id = :id")
    suspend fun updateAvailability(id: String, status: String)
}
