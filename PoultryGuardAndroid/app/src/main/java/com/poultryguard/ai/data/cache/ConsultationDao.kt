package com.poultryguard.ai.data.cache

import androidx.room.*
import com.poultryguard.ai.data.model.Consultation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations WHERE veterinarianId = :vetId ORDER BY dateTime ASC")
    fun getConsultationsForVetFlow(vetId: String): Flow<List<Consultation>>

    @Query("SELECT * FROM consultations WHERE id = :id LIMIT 1")
    suspend fun getConsultationById(id: String): Consultation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(consultation: Consultation)

    @Update
    suspend fun update(consultation: Consultation)

    @Query("DELETE FROM consultations")
    suspend fun deleteAll()
}
