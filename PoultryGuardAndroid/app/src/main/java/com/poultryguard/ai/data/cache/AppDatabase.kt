package com.poultryguard.ai.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.model.Veterinarian
import com.poultryguard.ai.data.model.SystemStats
import com.poultryguard.ai.data.model.FarmerProfile
import com.poultryguard.ai.data.model.SupportTicket
import com.poultryguard.ai.data.model.Alert
import com.poultryguard.ai.data.model.VeterinaryCase

@Database(entities = [MortalityRecord::class, Veterinarian::class, SystemStats::class, FarmerProfile::class, SupportTicket::class, Alert::class, VeterinaryCase::class, com.poultryguard.ai.data.model.Consultation::class], version = 15, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mortalityDao(): MortalityDao
    abstract fun vetDao(): VetDao
    abstract fun systemStatsDao(): SystemStatsDao
    abstract fun farmerProfileDao(): FarmerProfileDao
    abstract fun supportTicketDao(): SupportTicketDao
    abstract fun alertDao(): AlertDao
    abstract fun veterinaryCaseDao(): VeterinaryCaseDao
    abstract fun consultationDao(): ConsultationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poultry_guard_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
