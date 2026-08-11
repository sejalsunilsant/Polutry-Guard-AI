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
        val count = vetDao.getCount()
        if (count == 0) {
            val initialVets = listOf(
                Veterinarian(
                    id = "vet_priya",
                    name = "Dr. Priya Sharma",
                    specialty = "Poultry Veterinary",
                    phone = "+919876543210",
                    email = "priya.sharma@poultryguard.ai",
                    location = "Kolhapur",
                    photoUrl = "priya_sharma",
                    availability = "Available",
                    verificationStatus = "PENDING",
                    assignedFarmsCount = 12,
                    openCasesCount = 3,
                    credentialsDetails = "State Veterinary Council License #MH-10294, Master's in Avian Pathology from Bombay Vet College.",
                    consultationHistory = "1. Consultation: Resolved viral infection on Farm A (10 days ago)\n2. Consultation: Advised feed adjustments on Farm B (3 weeks ago)"
                ),
                Veterinarian(
                    id = "vet_1",
                    name = "Dr. Sarah Jenkins",
                    specialty = "Avian Pathology & Biosecurity",
                    phone = "+15553827492",
                    email = "sarah.jenkins@poultryguard.ai",
                    location = "Midwest Broiler Belt, Sect-4",
                    photoUrl = "sarah_jenkins",
                    availability = "Available",
                    verificationStatus = "VERIFIED",
                    assignedFarmsCount = 8,
                    openCasesCount = 0,
                    credentialsDetails = "License Cert #48291-AV, Board Certified in Avian Medicine.",
                    consultationHistory = "1. Session: Regular diagnostic checkup on Shed 4 (5 days ago)"
                ),
                Veterinarian(
                    id = "vet_2",
                    name = "Dr. Robert Chen",
                    specialty = "Poultry Nutrition & Wellness",
                    phone = "+15559812734",
                    email = "robert.chen@poultryguard.ai",
                    location = "East Valley Barns",
                    photoUrl = "robert_chen",
                    availability = "Busy",
                    verificationStatus = "VERIFIED",
                    assignedFarmsCount = 14,
                    openCasesCount = 2,
                    credentialsDetails = "Lic #29402-N, Ph.D. in Poultry Nutritional Sciences.",
                    consultationHistory = "1. Session: Feed rationing consultation (2 days ago)"
                ),
                Veterinarian(
                    id = "vet_3",
                    name = "Dr. Elena Rostova",
                    specialty = "Epidemiology & Viral Control",
                    phone = "+15557342918",
                    email = "elena.rostova@poultryguard.ai",
                    location = "Northern Free-Range Zone",
                    photoUrl = "elena_rostova",
                    availability = "Unavailable",
                    verificationStatus = "SUSPENDED",
                    assignedFarmsCount = 3,
                    openCasesCount = 0,
                    credentialsDetails = "Lic #98102-E, Board Certified Avian Epidemiologist.",
                    consultationHistory = "Account suspended pending biosecurity compliance audit."
                ),
                Veterinarian(
                    id = "vet_4",
                    name = "Dr. Marcus Vance",
                    specialty = "Avian Influenza Management",
                    phone = "+15553827004",
                    email = "marcus.vance@poultryguard.ai",
                    location = "Southern Broiler Fields",
                    photoUrl = "marcus_vance",
                    availability = "Available",
                    verificationStatus = "VERIFIED",
                    assignedFarmsCount = 6,
                    openCasesCount = 1,
                    credentialsDetails = "Lic #87102-AI, Emergency Influenza Task Force Certification.",
                    consultationHistory = "1. Session: Rapid PCR test setup for Shed A (1 week ago)"
                )
            )
            vetDao.insertAll(initialVets)
        }
    }

    suspend fun insert(vet: Veterinarian) {
        vetDao.insert(vet)
    }

    suspend fun updateVerificationStatus(id: String, status: String) {
        vetDao.updateVerificationStatus(id, status)
    }

    suspend fun updateAvailability(id: String, status: String) {
        vetDao.updateAvailability(id, status)
    }
}
