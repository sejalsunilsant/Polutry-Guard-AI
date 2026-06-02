package com.poultryguard.ai.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.cache.MortalityDao
import com.poultryguard.ai.data.model.MortalityRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MortalityRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao: MortalityDao = db.mortalityDao()

    private var firestore: FirebaseFirestore? = null
    private var firestoreEnabled = false

    init {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                firestoreEnabled = true
                Log.d("PoultryGuardMortality", "Firestore successfully initialized for Mortality sync.")
            } else {
                Log.w("PoultryGuardMortality", "Firebase is uninitialized. Operating in Room-only Offline Mode.")
            }
        } catch (e: Exception) {
            Log.w("PoultryGuardMortality", "Firestore initialization skipped: ${e.localizedMessage}")
        }
    }

    fun getAllRecordsFlow(): Flow<List<MortalityRecord>> = dao.getAllRecordsFlow()

    suspend fun getAllRecords(): List<MortalityRecord> = dao.getAllRecords()

    suspend fun insertRecord(record: MortalityRecord) {
        // 1. Save to local Room DB first
        dao.insert(record)
        Log.d("PoultryGuardMortality", "Saved mortality record locally to Room: ${record.id}")

        // 2. Try to sync to remote Firestore
        if (firestoreEnabled && firestore != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore!!.collection("mortality_records")
                        .document(record.id)
                        .set(record)
                        .addOnSuccessListener {
                            Log.d("PoultryGuardMortality", "Firestore remote sync success: ${record.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("PoultryGuardMortality", "Firestore remote sync failure: ${e.localizedMessage}")
                        }
                } catch (e: Exception) {
                    Log.e("PoultryGuardMortality", "Error during Firestore sync operation: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun deleteRecord(record: MortalityRecord) {
        dao.delete(record)
        if (firestoreEnabled && firestore != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore!!.collection("mortality_records")
                        .document(record.id)
                        .delete()
                        .addOnSuccessListener {
                            Log.d("PoultryGuardMortality", "Firestore delete success for ID: ${record.id}")
                        }
                } catch (e: Exception) {
                    Log.e("PoultryGuardMortality", "Error during Firestore delete: ${e.localizedMessage}")
                }
            }
        }
    }
}
