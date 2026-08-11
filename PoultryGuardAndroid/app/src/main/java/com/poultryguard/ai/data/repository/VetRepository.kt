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
                ),
                Veterinarian(
                    id = "vet_4",
                    name = "Dr. Marcus Vance",
                    specialty = "Avian Influenza Management",
                    phone = "+15553827004",
                    email = "marcus.vance@poultryguard.ai",
                    location = "Southern Broiler Fields",
                    photoUrl = "marcus_vance",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_5",
                    name = "Dr. Chloe Patel",
                    specialty = "Flock Health Diagnostic Specialist",
                    phone = "+15553827005",
                    email = "chloe.patel@poultryguard.ai",
                    location = "Western Hatcheries",
                    photoUrl = "chloe_patel",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_6",
                    name = "Dr. Amara Okafor",
                    specialty = "Poultry Immunology",
                    phone = "+15553827006",
                    email = "amara.okafor@poultryguard.ai",
                    location = "Central Layer Sheds",
                    photoUrl = "amara_okafor",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_7",
                    name = "Dr. David Miller",
                    specialty = "Biosecurity Protocols",
                    phone = "+15553827007",
                    email = "david.miller@poultryguard.ai",
                    location = "Eastern Breeder Hub",
                    photoUrl = "david_miller",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_8",
                    name = "Dr. Sophie Dubois",
                    specialty = "Poultry Parasitology",
                    phone = "+15553827008",
                    email = "sophie.dubois@poultryguard.ai",
                    location = "Northwest Broiler Farm",
                    photoUrl = "sophie_dubois",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_9",
                    name = "Dr. Kenji Sato",
                    specialty = "Avian Toxicology",
                    phone = "+15553827009",
                    email = "kenji.sato@poultryguard.ai",
                    location = "Southeastern Aviaries",
                    photoUrl = "kenji_satoh",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_10",
                    name = "Dr. Hans Weber",
                    specialty = "Poultry Farm Sanitization",
                    phone = "+15553827010",
                    email = "hans.weber@poultryguard.ai",
                    location = "Central Plains Barns",
                    photoUrl = "hans_weber",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_11",
                    name = "Dr. Carlos Gomez",
                    specialty = "Avian Pathology",
                    phone = "+15553827011",
                    email = "carlos.gomez@poultryguard.ai",
                    location = "Southwest Laying Houses",
                    photoUrl = "carlos_gomez",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_12",
                    name = "Dr. Anita Desai",
                    specialty = "Flock Medication Programs",
                    phone = "+15553827012",
                    email = "anita.desai@poultryguard.ai",
                    location = "North East Layer Belt",
                    photoUrl = "anita_desai",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_13",
                    name = "Dr. Liam O'Connor",
                    specialty = "Water System Sanitization",
                    phone = "+15553827013",
                    email = "liam.oconnor@poultryguard.ai",
                    location = "Southern Broiler Valley",
                    photoUrl = "liam_oconnor",
                    availability = "Available"
                ),
                Veterinarian(
                    id = "vet_14",
                    name = "Dr. Emily Taylor",
                    specialty = "Viral Prevention",
                    phone = "+15553827014",
                    email = "emily.taylor@poultryguard.ai",
                    location = "Mountain View Layers",
                    photoUrl = "emily_taylor",
                    availability = "Busy"
                ),
                Veterinarian(
                    id = "vet_15",
                    name = "Dr. Tariq Al-Fayed",
                    specialty = "Poultry Genetics Health",
                    phone = "+15553827015",
                    email = "tariq.alfayed@poultryguard.ai",
                    location = "Valley Broiler Ranch",
                    photoUrl = "tariq_alfayed",
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
