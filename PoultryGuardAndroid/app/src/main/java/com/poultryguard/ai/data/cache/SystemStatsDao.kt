package com.poultryguard.ai.data.cache

import androidx.room.*
import com.poultryguard.ai.data.model.SystemStats
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemStatsDao {
    @Query("SELECT * FROM system_stats WHERE id = 1")
    fun getSystemStats(): Flow<SystemStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: SystemStats)

    @Query("SELECT COUNT(*) FROM system_stats")
    suspend fun getCount(): Int
}
