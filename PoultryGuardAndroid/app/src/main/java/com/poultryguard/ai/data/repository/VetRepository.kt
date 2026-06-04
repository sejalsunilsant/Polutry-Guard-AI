package com.poultryguard.ai.data.repository

import android.content.Context
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.model.Veterinarian
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VetRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val vetDao = db.vetDao()

    fun getVeterinariansFlow(): Flow<List<Veterinarian>> {
        return vetDao.getAllVeterinarians()
    }

    suspend fun populateInitialVetsIfNeeded() {
        val dbList = vetDao.getAllVeterinarians().first()
        if (dbList.isEmpty()) {
            val initialVets = listOf(
                Veterinarian(
                    id = "vet_1",
                    name = "Dr. Sarah Jenkins",
                    specialty = "Avian Pathology & Biosecurity",
                    phone = "+15553827492",
                    email = "sarah.jenkins@poultryguard.ai",
                    location = "Midwest Broiler Belt, Sect-4",
                    photoUrl = "sarah_jenkins",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_2",
                    name = "Dr. Robert Chen",
                    specialty = "Poultry Nutrition & Wellness",
                    phone = "+15559812734",
                    email = "robert.chen@poultryguard.ai",
                    location = "East Valley Barns",
                    photoUrl = "robert_chen",
                    availability = "Busy"
                ),
                Veterinarian(
                    id = "vet_3",
                    name = "Dr. Elena Rostova",
                    specialty = "Epidemiology & Viral Control",
                    phone = "+15557342918",
                    email = "elena.rostova@poultryguard.ai",
                    location = "Northern Free-Range Zone",
                    photoUrl = "elena_rostova",
                    availability = "Unavailable"
                )
            )
            vetDao.insertAll(initialVets)
        }
    }

    suspend fun updateAvailability(id: String, status: String) {
        vetDao.updateAvailability(id, status)
    }
}
