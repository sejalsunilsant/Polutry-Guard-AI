package com.poultryguard.ai.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.poultryguard.ai.data.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun getAllAlertsFlow(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: String): Alert?

    @Query("SELECT * FROM alerts WHERE batchId = :batchId ORDER BY createdAt DESC")
    suspend fun getAlertsForBatch(batchId: String): List<Alert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: Alert)

    @Update
    suspend fun update(alert: Alert)

    @Query("UPDATE alerts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
