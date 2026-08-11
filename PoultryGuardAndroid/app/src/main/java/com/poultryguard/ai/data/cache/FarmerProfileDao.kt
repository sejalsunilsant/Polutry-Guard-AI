package com.poultryguard.ai.data.cache

import androidx.room.*
import com.poultryguard.ai.data.model.FarmerProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerProfileDao {
    @Query("SELECT * FROM farmer_profiles")
    fun getAllFarmersFlow(): Flow<List<FarmerProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(farmers: List<FarmerProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(farmer: FarmerProfile)

    @Query("UPDATE farmer_profiles SET accountStatus = :status WHERE id = :id")
    suspend fun updateAccountStatus(id: String, status: String)

    @Query("UPDATE farmer_profiles SET lastActive = 'Just now', lastLoginTime = 'Just now' WHERE id = :id")
    suspend fun resetAccess(id: String)

    @Query("SELECT COUNT(*) FROM farmer_profiles")
    suspend fun getCount(): Int
}
