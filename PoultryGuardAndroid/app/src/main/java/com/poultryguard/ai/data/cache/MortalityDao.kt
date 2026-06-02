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

    @Delete
    suspend fun delete(record: MortalityRecord)

    @Query("DELETE FROM mortality_records")
    suspend fun clearAll()
}
