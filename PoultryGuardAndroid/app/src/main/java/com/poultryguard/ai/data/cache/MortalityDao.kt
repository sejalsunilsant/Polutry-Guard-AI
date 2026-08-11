package com.poultryguard.ai.data.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poultryguard.ai.data.model.MortalityRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MortalityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MortalityRecord)

    @Query("SELECT * FROM mortality_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<MortalityRecord>>

    @Query("SELECT * FROM mortality_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<MortalityRecord>

    @Query("SELECT * FROM mortality_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<MortalityRecord>

    @Query("UPDATE mortality_records SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("SELECT SUM(deathCount) FROM mortality_records WHERE batchId = :batchId AND isSynced = 0")
    suspend fun getUnsyncedMortalityCountForBatch(batchId: String): Int?

    @Delete
    suspend fun delete(record: MortalityRecord)

    @Query("DELETE FROM mortality_records")
    suspend fun clearAll()
}
