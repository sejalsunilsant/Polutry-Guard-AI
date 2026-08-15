package com.poultryguard.ai.data.repository

import android.content.Context
import android.util.Log
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.cache.MortalityDao
import com.poultryguard.ai.data.model.MortalityRecord
import kotlinx.coroutines.flow.Flow

class MortalityRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao: MortalityDao = db.mortalityDao()

    fun getAllRecordsFlow(): Flow<List<MortalityRecord>> = dao.getAllRecordsFlow()

    suspend fun getAllRecords(): List<MortalityRecord> = dao.getAllRecords()

    suspend fun insertRecord(record: MortalityRecord) {
        // Save to local Room DB
        dao.insert(record)
        Log.d("PoultryGuardMortality", "Saved mortality record locally to Room: ${record.id}")
    }

    suspend fun deleteRecord(record: MortalityRecord) {
        dao.delete(record)
        Log.d("PoultryGuardMortality", "Deleted mortality record: ${record.id}")
    }
}
