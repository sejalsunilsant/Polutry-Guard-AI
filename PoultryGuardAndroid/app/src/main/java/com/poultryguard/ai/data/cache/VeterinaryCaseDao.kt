package com.poultryguard.ai.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poultryguard.ai.data.model.VeterinaryCase
import kotlinx.coroutines.flow.Flow

@Dao
interface VeterinaryCaseDao {
    @Query("SELECT * FROM veterinary_cases ORDER BY createdAt DESC")
    fun getAllCasesFlow(): Flow<List<VeterinaryCase>>

    @Query("SELECT * FROM veterinary_cases WHERE id = :id LIMIT 1")
    suspend fun getCaseById(id: String): VeterinaryCase?

    @Query("SELECT * FROM veterinary_cases WHERE veterinarianId = :vetId ORDER BY createdAt DESC")
    fun getCasesForVetFlow(vetId: String): Flow<List<VeterinaryCase>>

    @Query("SELECT * FROM veterinary_cases WHERE batchId = :batchId ORDER BY createdAt DESC")
    fun getCasesForBatchFlow(batchId: String): Flow<List<VeterinaryCase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vetCase: VeterinaryCase)

    @Update
    suspend fun update(vetCase: VeterinaryCase)

    @Query("DELETE FROM veterinary_cases WHERE batchId = :batchId")
    suspend fun deleteCasesForBatch(batchId: String)

    @Query("DELETE FROM veterinary_cases")
    suspend fun deleteAll()
}
